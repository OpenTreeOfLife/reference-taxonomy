#!/usr/bin/python

import sys
import re
import logging
import codecs
import pickle
from SOAPpy import WSDL


# usage:
# python worms.py [taxonomy synonyms [logfile]]
# defaults as follows:

DEFAULT_TAXONOMY_NAME = 'taxonomy.tsv'
DEFAULT_SYNONYMS_NAME = 'synonyms.tsv'
DEFAULT_LOG_NAME = 'worms.log'
DEFAULT_CHECKPOINT_NAME = 'checkpointfile'
DEFAULT_PROXY = 'http://www.marinespecies.org/aphia.php?p=soap&wsdl=1'

WORMSPROXY = WSDL.Proxy(DEFAULT_PROXY)

VISITED = {}

ROOT_ID = 1


def startup(args):
    """ """
    pargs = vars(process_args())
    log_fname = pargs["log_file"]
    results_fname = pargs["taxonomy_file"]  # pargs.taxonomy_file ?
    synonyms_fname = pargs["synonyms_file"]

    logging.basicConfig(filename=log_fname,
                        filemode='w',
                        level=logging.INFO,
                        format='%(levelname)-8s: %(message)s')

    try:
        checkpointfile = open(DEFAULT_CHECKPOINT_NAME, 'rb')
    except IOError:
        checkpointfile = None
    if checkpointfile:
        queue = pickle.load(checkpointfile)
        taxa = pickle.load(checkpointfile)
        synonyms = pickle.load(checkpointfile)
        checkpointfile.close()
        (taxa, synonyms) = traverse_taxonomy(0, (queue, taxa, synonyms))
    else:
        (taxa, synonyms) = traverse_taxonomy(ROOT_ID, None)
    write_taxonomy(results_fname, taxa)
    write_synonyms(synonyms_fname, synonyms)


def process_args():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("taxonomy_file",
                        nargs='?',
                        default=DEFAULT_TAXONOMY_NAME)
    parser.add_argument("synonyms_file",
                        nargs='?',
                        default=DEFAULT_SYNONYMS_NAME)
    parser.add_argument("log_file", nargs='?', default=DEFAULT_LOG_NAME)
    pargs = parser.parse_args()
    return pargs


MAXLENGTH = 50


def get_one_taxon_children(taxon_id):
    result = []
    offset = 0
    try:
        wsdlChildren = WORMSPROXY.getAphiaChildrenByID(taxon_id,
                                                       offset=offset)
        if wsdlChildren:
            result.extend(wsdlChildren)
            while len(wsdlChildren) == MAXLENGTH:
                offset += MAXLENGTH
                wsdlChildren = WORMSPROXY.getAphiaChildrenByID(taxon_id,
                                                               offset=offset)
                if wsdlChildren:
                    result.extend(wsdlChildren)
    except socket.error as e:
        print e
        raise RuntimeException("dummy")
    finally:
        return result


def get_one_taxon_synonyms(taxon_id):
    result = []
    offset = 0
    wsdlSyns = WORMSPROXY.getAphiaSynonymsByID(taxon_id)
    if wsdlSyns:
        result.extend(wsdlSyns)    # return wsdlSyns ?
    return result

TESTPAUSE = 20


def traverse_taxonomy(root_id, pickled):
    from collections import deque
    from time import sleep
    if pickled:
        (queue, taxa, synonyms) = pickled
        last_sleep = len(taxa)
    else:
        queue = deque()
        queue.append(root_id)
        taxa = {}
        synonyms = []
        last_sleep = 0
    while ((len(queue) > 0) and (len(taxa) < 500000)):
        print "Taxon count = %d" % len(taxa)
        if (len(taxa) - last_sleep) > TESTPAUSE:
            print "sleeping", TESTPAUSE
            sleep(20)
            picklefile = open(DEFAULT_CHECKPOINT_NAME, 'wb')
            pickle.dump(queue, picklefile)
            pickle.dump(taxa, picklefile)
            pickle.dump(synonyms, picklefile)
            picklefile.close()
            last_sleep = len(taxa)
        current_parent = queue.pop()
        children = get_one_taxon_children(current_parent)
        for child in children:
            if child.AphiaID not in taxa and child.status == 'accepted':
                if child.AphiaID == ROOT_ID:
                    taxon = make_root_taxon()
                elif child.rank is not None:
                    taxon = {"id": child.AphiaID,
                             "parent": current_parent,
                             "name": child.valid_name,
                             "rank": child.rank.lower()}
                else:
                    taxon = {"id": child.AphiaID,
                             "parent": current_parent,
                             "name": child.valid_name}
                if child.isExtinct:
                    print "Extinct = %s" % str(child.isExtinct)
                    taxon['flags'] = 'extinct'
                else:
                    taxon['flags'] = ''
                taxa[child.AphiaID] = taxon
                queue.append(child.AphiaID)
        syns = get_one_taxon_synonyms(current_parent)
        for syn in syns:
            s = {'AphiaID': current_parent,
                 'synonym': syn.scientificname}
            synonyms.append(s)
    return (taxa, synonyms)


def make_root_taxon():
    return {"id": ROOT_ID,
            "parent": '',
            "name": 'Biota',
            "rank": ''}


TAXON_HEADER = "uid\t|\tparent_uid\t|\tname\t|\trank\t|\tflags\n"
TAXON_TEMPLATE = "%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\n"


def write_taxonomy(fname, taxon_table):
    """ Opens and writes lines in the taxonomy file corresponding to
    each valid name"""
    try:
        taxonomy_file = codecs.open(fname, "w", "utf-8")
        taxonomy_file.write(TAXON_HEADER)
        for rawtaxon in taxon_table.values():    # maybe sort this
            taxon = process_parens(rawtaxon)
            if 'rank' in taxon:
                outstr = TAXON_TEMPLATE % (taxon['id'],
                                           taxon['parent'],
                                           taxon['name'],
                                           taxon['rank'],
                                           taxon['flags'])
            else:
                outstr = TAXON_TEMPLATE % (taxon['id'],
                                           taxon['parent'],
                                           taxon['name'],
                                           '',
                                           taxon['flags'])
            taxonomy_file.write(outstr)
        taxonomy_file.close()
    except IOError as e:
        logging.error(
            "error %s opening/writing %s as taxonomy file file",
            str(e),
            taxonomy_fname)

def process_parens(taxon):
    """subgenera are parenthesized; if this occurs in the name of a
       species or lower ranked taxon, strip out the subgenus reference;
       if the taxon is the subgenus, assume the parenthesized portion is
       the subgenus name, so strip out the genus name that preceeds it and
       parens.  If it doesn't fit the pattern, fail."""
    if 'rank' not in taxon:  # unfortunate edge case
        return taxon
    name = taxon['name']
    if taxon['rank'] == 'subgenus':
        if name.find('(') > -1:
            open = name.find('(')
            close = name.find(')')
            if close > open:
                name = name[0:open -1]
                taxon['name'] = name
    elif name.find('(') > -1:
        if name.find('(') > -1:
            open = name.find('(')
            close = name.find(')')
            if close > open:
                name = name[0:open] + name[close+2:]
                taxon['name'] = name
    return taxon
       


SYNONYM_HEADER = "uid\t|\tname\t|\ttype\t|\tTBD\t|\n"
SYNONYM_TEMPLATE = "%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n"


def write_synonyms(synonyms_fname, synonyms):
    """
    Opens and writes lines in the synonyms file corresponding to
    each name identified as a synonym (IF id != Current Name id)
    If the name is a homonym and authority information is
    available, it will appear in the third column, prefix by
    'authority', rather than by 'synonym'
    """
    try:
        synonyms_file = codecs.open(synonyms_fname, "w", "utf-8")
        synonyms_file.write(SYNONYM_HEADER)
        for syn in synonyms:
            if 'AphiaID' in syn:  # if no id, then nothing worth writing
                if 'Author' in syn and 'Year' in syn:
                    namefield = "%s %s %s" % (
                        syn['synonym'],
                        syn['Author'],
                        syn['Year'])
                    typefield = 'authority'
                else:
                    namefield = syn['synonym']
                    typefield = 'synonym'
                outstr = SYNONYM_TEMPLATE % (
                    syn['AphiaID'],
                    namefield,
                    typefield,
                    '')
                synonyms_file.write(outstr)
        synonyms_file.close()
    except IOError as e:
        logging.error(
            "error %s opening/writing %s as name synonym file",
            str(e),
            synonyms_fname)


if __name__ == "__main__":
    startup(sys.argv)
