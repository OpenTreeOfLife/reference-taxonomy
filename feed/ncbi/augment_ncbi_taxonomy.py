#!/usr/bin/python

# Tool to add genbank strains as terminal taxa to an NCBI taxonomy formated for smasher
# (lines like id\t|\tparent_uid\t|\tname\t|\trank\t|)
# strains and their accession identifiers are in a file formated as:
# accessionid\tNCBI taxon id\tstrain identifier

# output is like taxonomy input, but additional terminal taxa, with identifiers like
# taxonid.

# Requirements based on this:

# Now that we have strain names from Genbank we can add them as taxon-like
# entities to create an augmented NCBI Taxonomy (ANT).

# For each NCBI taxon there will be some number (0, 1, more) of strains listed
# in Genbank. That is, we will have some number of Genbank records annotated as
# belonging to that taxon, and among these there will be multiple strains.
# Each distinct strain for a taxon should become a new quasi-taxon that is a
# child of the NCBI taxon. An exception is # when the strain id is a suffix of
# the NCBI taxon name.

# The name for a new strain-taxon has been implemented as the name of the taxon
# concatenated with the strain name, with a space in between. Ids don’t have
# to be numbers for smasher, so were implemented as NNN.strainname


# usage python augment_ncbi_taxonomy.py [taxonomy [accession [augmented_taxonomy]]]

import sys

DEFAULT_TAXONOMY_FILE = 'taxonomy.tsv'
DEFAULT_ACCESSIONS_FILE = 'accessionid_to_taxonid.tsv'
DEFAULT_AUGMENTED_TAXONOMY_FILE = 'augmented_taxonomy.tsv'


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
    pargs = parser.parse_args()
    return pargs


def load_taxonomy(taxonomy_path):
    result = {}
    with  open(taxonomy_path, 'r') as taxonomy_file:
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
        taxonomy_file.close()
    return result


def process_accessions(accession_path):
    result = {}
    with open(accession_path, 'r') as accession_file:
        for raw_line in accession_file:
            line = raw_line[:-2]
            fields = line.split('\t')
            accession = {}
            accession['id'] = fields[0].strip()
            tname = fields[1].strip()
            accession['taxon'] = tname
            if len(fields) > 2:
                if len(fields[2].strip('\n')) > 0:
                    strain = fields[2].strip('\n')
                    accession['strain'] = strain
            result[tname] = accession
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
    with open(augmented_path, "w") as ofile:
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


def startup():
    pargs = vars(process_args())
    taxonomy_path = pargs['taxonomy']
    accession_path = pargs['accession_map']
    augmented_path = pargs['augmented_taxonomy']
    base_taxonomy = load_taxonomy(taxonomy_path)
    base_size = len(base_taxonomy)
    possible_children = process_accessions(accession_path)
    augmented_taxonomy = augment_taxonomy(base_taxonomy, possible_children)
    added_taxa = len(augmented_taxonomy)-base_size
    print "added {} terminals to base taxonomy of size {}".format(added_taxa,
                                                                  base_size)
    save_taxonomy(augmented_taxonomy, augmented_path)


if __name__ == '__main__':
    startup()
