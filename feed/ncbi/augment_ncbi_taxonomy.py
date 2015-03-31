#!/usr/bin/python

# usage python augment_ncbi_taxonomy [taxonomy [accession [augmented_taxonomy [logfile]]]]

import logging
import sys

DEFAULT_TAXONOMY_FILE = 'taxonomy.tsv'
DEFAULT_ACCESSIONS_FILE = 'accessionid_to_taxonid.tsv'
DEFAULT_AUGMENTED_TAXONOMY_FILE = 'augmented_taxonomy.tsv'
DEFAULT_LOG_FILE = 'augment_ncbi_taxonomy.log'


def process_args():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('taxonomy', nargs='?', default=DEFAULT_TAXONOMY_FILE)
    parser.add_argument('accession_map',
                        nargs='?',
                        default=DEFAULT_ACCESSIONS_FILE)
    parser.add_argument('augmented_taxonomy',
                        nargs='?',
                        default=DEFAULT_AUGMENTED_TAXONOMY_FILE)
    parser.add_argument('log_file', nargs='?', default=DEFAULT_LOG_FILE)
    pargs = parser.parse_args()
    return pargs


def load_taxonomy(taxonomy_path):
    result = {}
    try:
        taxonomy_file = open(taxonomy_path, 'rU')
        taxonomy_file.readline()  # read and toss headers
        line = taxonomy_file.readline()
        while line:
            fields = line.split('\t|\t')
            taxon = {}
            taxon['id'] = fields[0]
            taxon['parent_uid'] = fields[1]
            taxon['name'] = fields[2]
            if len(fields[3].strip()) > 0:
                taxon['rank'] = fields[3].strip()
            result[fields[0]] = taxon
            line = taxonomy_file.readline()
    except IOError as e:
        msg = "processing {} as taxonomy file".format(taxonomy_path)
        print "Error: " + msg
        logging.error(msg)
        sys.exit(1)
    taxonomy_file.close()
    return result


def process_accessions(accession_path):
    result = {}
    try:
        accession_file = open(accession_path, 'rU')
        line = accession_file.readline()[:-2]
        while line:
            fields = line.split('\t')
            accession = {}
            accession['id'] = fields[0].strip()
            tname = fields[1].strip()
            accession['taxon'] = tname
            if len(fields) > 2:
                if len(fields[2].strip('\n')) > 0:
                    strain = fields[2].strip('\n')
                    accession['strain'] = strain
            result[fields[1]] = accession
            line = accession_file.readline()
    except IOError as e:
        msg = "processing {} as accession mapping file".format(accession_path)
        print "Error: " + msg
        logging.error(msg)
        sys.exit(1)
    accession_file.close()
    return result


STRAIN_WARNING = ("strain name %s matched nonsuffix/nonembed " +
                  "substring of parent name %s")


def augment_taxonomy(base_taxonomy, accessions):
    result = base_taxonomy
    for item in accessions.keys():
        acc = accessions[item]
        acc_taxon = acc['taxon']
        if (acc_taxon in base_taxonomy) and ('strain' in acc):
            parent_name = base_taxonomy[acc_taxon]['name']
            strain_name = acc['strain']
            if (parent_name.find(strain_name) == -1):
                parent_id = base_taxonomy[acc_taxon]['id']
                subtaxname = parent_name + ' ' + acc['strain']
                subtaxid = parent_id + '.' + acc['strain']
                subtax = {'id': subtaxid,
                          'name': subtaxname,
                          'parent_uid': parent_id}
                if subtaxid not in result:
                    result[subtaxid] = subtax
            else:
                if not parent_name.endswith(strain_name):
                    embed_name = strain_name + '('
                    embed_name2 = '(' + strain_name + ')'
                    if ((parent_name.find(embed_name) == -1) and
                        (parent_name.find(embed_name2) == -1)):
                        print STRAIN_WARNING % (acc['strain'], parent_name)
    return result

TAXON_HEADER = "uid\t|\tparent_uid\t|\tname\t|\trank\t|\t\n"
TAXON_TEMPLATE = "%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n"


def save_taxonomy(augmented_taxonomy, augmented_path):
    try:
        ofile = open(augmented_path, "w")
        ofile.write(TAXON_HEADER)
        for id in augmented_taxonomy.keys():
            item = augmented_taxonomy[id]
            uid = item['id']
            name = item['name']
            parent = item['parent_uid']
            if 'rank' in item:
                rank = item['rank']
            else:
                rank = 'no rank'
            outstr = TAXON_TEMPLATE % (uid, parent, name, rank)
            ofile.write(outstr)
    except IOError as e:
        msg = "writing {} as augmented taxonomy file".format(augmented_path)
        print "Error: " + msg
        logging.error(msg)
        sys.exit(1)
    ofile.close()


def startup():
    pargs = vars(process_args())
    logging.basicConfig(filename=pargs['log_file'],
                        filemode='w',
                        level=logging.INFO,
                        format='%(levelname)-8s: %(message)s')
    taxonomy_path = pargs['taxonomy']
    accession_path = pargs['accession_map']
    augmented_path = pargs['augmented_taxonomy']
    base_taxonomy = load_taxonomy(taxonomy_path)
    print len(base_taxonomy)
    possible_children = process_accessions(accession_path)
    print len(possible_children)
    augmented_taxonomy = augment_taxonomy(base_taxonomy, possible_children)
    save_taxonomy(augmented_taxonomy, augmented_path)


if __name__ == '__main__':
    startup()
