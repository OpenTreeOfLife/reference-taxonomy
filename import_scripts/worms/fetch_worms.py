#!/usr/bin/python

# WoRMS had about 330,000 records in 2014
# Square root of 360,000 is 600.
# If harvested over 24 hours, that would be 229 records per minute
# or 4 per second.
# Maybe: harvest 229, then wait to fill out one minute?
#        harvest 4, then wait to fill out one second?
#        harvest 8, then wait one second?

# Starts with:
#   queue.txt - list of aphia ids for subtress that remain to be harvested.
#   records/*.csv - file of digested worms records already harvested.

# Every member of the queue is an aphia id for a record that has been 
# processed, but whose children and synonyms have not.

RECORDS_PER_CHUNK = 20   # eventually maybe 800
REQUESTS_PER_SLEEP = 3
SECONDS_PER_SLEEP = 1
DEFAULT_CHUNK_SIZE = 100

DEFAULT_PROXY = 'http://www.marinespecies.org/aphia.php?p=soap&wsdl=1'

DEFAULT_ROOT_ID = 1  # Biota

# Need:
#  https://sourceforge.net/projects/pywebsvcs/files/SOAP.py/

import os, sys, re, csv
import codecs  # maybe not
import argparse
from collections import deque
from time import sleep
from SOAPpy import WSDL

WORMSPROXY = WSDL.Proxy(DEFAULT_PROXY)

seen = {}

def fetch_worms(root, queuepath, outpath, chunk_count):
    queue = load_queue(queuepath, root)
    j = 0
    while len(queue) > 0 and j < chunk_count:
        chunk = get_chunk(queue)
        save_chunk(chunk, outpath)
        save_queue(queue, queuepath)
        j += 1

def save_chunk(chunk, outpath):
    (aphias, links) = chunk
    if len(links) > 0:
        if not os.path.isdir(outpath):
            os.makedirs(outpath)
        id = links[0][0]
        print 'chunk', id
        path = os.path.join(outpath, 'l%s.csv' % id)
        print 'writing', path, len(links)
        with open(path, 'w') as outfile:
            writer = csv.writer(outfile)
            writer.writerow(links_header)
            for row in links:
                writer.writerow(row)

        if len(aphias) > 0:
            path = os.path.join(outpath, 'a%s.csv' % id)
            print 'writing', path, len(aphias)
            with open(path, 'w') as outfile:
                writer = csv.writer(outfile)
                writer.writerow(digest_header)
                for row in aphias:
                    writer.writerow([fieldify(x) for x in row])

def fieldify(x):
    if isinstance(x, unicode):
        return x.encode('utf-8')
    elif x is True:
        return 'true'
    elif x is False:
        return 'false'
    else:
        return x

def load_queue(queuepath, root):
    if os.path.exists(queuepath):
        with open(queuepath, 'r') as infile:
            line = infile.next()
            queued = line.strip().split(' ')
            print '%s ids queued' % len(queued)
            ids = [int(id) for id in queued]
    else:
        ids = [root]
    for id in ids:
        seen[id] = ('queued', 'q')
    return deque(ids)

def save_queue(queue, queuepath):
    print 'queued:', len(queue)
    with open(queuepath, 'w') as outfile:
        outfile.write(' '.join([str(x) for x in queue]))
        outfile.write('\n')

def get_chunk(queue):
    record_count = [0]
    request_count = [0]
    aphias = []
    links = []
    def emit(digested_record):
        aphias.append(digested_record)
        record_count[0] += 1
    def throttle():
        request_count[0] += 1
        if request_count[0] % REQUESTS_PER_SLEEP == 0:
            sleep(SECONDS_PER_SLEEP)
    def see(child, parent_id, rel):
        if child.AphiaID in seen:
            if child.status != 'unaccepted':
                (parent_id2, rel2) = seen[child.AphiaID]
                print ('** REPEAT ENCOUNTER: %s = %s %s + %s %s (%s) **' %
                       (child.AphiaID,
                        rel2, parent_id2,
                        rel, parent_id,
                        child.status))
            s = False
        else:
            # First time encountering this id
            seen[child.AphiaID] = (parent_id, rel)
            emit(digest(child))
            s = True
        links.append((child.AphiaID, parent_id, rel))
        return s
    while len(queue) > 0 and record_count[0] < RECORDS_PER_CHUNK:
        parent_id = queue.pop()  # id
        linked_ids = {}

        print 'requesting synonyms of', parent_id
        for syn in sort_aphia(get_one_taxon_synonyms(parent_id)):
            if syn.AphiaID in linked_ids:
                # Silently ignore duplicate synonyms
                continue
            linked_ids[syn.AphiaID] = True
            see(syn, parent_id, 's')
        throttle()

        print 'requesting children of', parent_id
        children = sort_aphia(get_one_taxon_children(parent_id))
        to_q = []
        for child in children:
            if child.AphiaID in linked_ids:
                # Ignore duplicate children, and children that
                # duplicate synonyms
                continue
            linked_ids[child.AphiaID] = True
            if see(child, parent_id, 'c'):
                # Open tree policy for WoRMS: no subspecies.
                # Huge performance win on download.
                if child.rank != 'Species':
                    to_q.append(child.AphiaID)
        # try to do lowest numbered subtrees first
        queue.extend(reversed(to_q))
        throttle()
    return (aphias, links)

def sort_aphia(aphia_list):
    return sorted(aphia_list, key=(lambda aphia: aphia.AphiaID))

def digest(aphia_record):
    return (aphia_record.AphiaID,
            aphia_record.scientificname,
            aphia_record.authority,
            aphia_record.rank,
            aphia_record.status,  #e.g. 'accepted'
            aphia_record.unacceptreason,
            aphia_record.valid_AphiaID,
            aphia_record.isExtinct)

digest_header = [
    'id', 'name', 'authority', 'rank', 'status',
    'unacceptreason', 'valid', 'extinct']

links_header = [
    'id', 'parent', 'rel']

"""
Keep these aphia fields:

    AphiaID: unique and persistent identifier within WoRMS. Primary key in the database.
    scientificname: the full scientific name without authorship
    authority: the authorship information for the scientificname formatted 
      according to the conventions of the applicable nomenclaturalCode
    rank: the taxonomic rank of the most specific name in the scientificname
    status: the status of the use of the scientificname (usually a taxonomic 
      opinion). Additional technical statuses are (1) quarantined: hidden from 
      public interface after decision from an editor and (2) deleted: AphiaID 
      should NOT be used anymore, please replace it by the valid_AphiaID
      E.g. 'accepted'
    unacceptreason: the reason why a scientificname is unaccepted
    valid_AphiaID: the AphiaID (for the scientificname) of the currently accepted taxon
    isExtinct: a flag indicating an extinct organism. Possible values: 0/1/NUL
"""


MAXLENGTH = 50


def get_one_taxon_children(taxon_id):
    result = []
    offset = 0
    try:
        wsdlChildren = WORMSPROXY.getAphiaChildrenByID(taxon_id,
                                                       offset=offset)
        print 'gotcha', len(wsdlChildren)
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--queue', dest='queuefile')
    parser.add_argument('--out', dest='outdir')
    parser.add_argument('--chunks', dest='chunk_count', type=int)
    parser.add_argument('--root', dest='root', type=int, default=DEFAULT_ROOT_ID)
    parser.add_argument('--chunksize', dest='chunksize', type=int,
                        default=DEFAULT_CHUNK_SIZE)
    args = parser.parse_args()
    fetch_worms(args.root, args.queuefile, args.outdir, args.chunk_count)
