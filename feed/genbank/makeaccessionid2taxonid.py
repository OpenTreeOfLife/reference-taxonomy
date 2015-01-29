import argparse
import time
import os
import pickle

DEFAULT_PICKLE_FILE = 'Genbank.pickle'
DEFAULT_ACCESSION_MAP = 'accessionid_to_taxonid.tsv'
parser = argparse.ArgumentParser(description='Text genbank flatfile testing')



def main(args):
    with open(DEFAULT_PICKLE_FILE,'rb') as picklefile:
        to_load = pickle.load(picklefile)
    if to_load:
        accessions, lastfile, conflicts = to_load
        print("conflicts: {0:8d}".format(len(conflicts)))
        print("accessions: {0:8d}; last: {1}".format(len(accessions),lastfile))
        with open(DEFAULT_ACCESSION_MAP,'wt') as mapfile: 
            for acc in accessions.keys():
                tax_strain = accessions[acc]
                if tax_strain[1]:
                    mapfile.write("{0}\t{1}\t{2}\n".format(acc, 
                                                           tax_strain[0], 
                                                           tax_strain[1]))
                else:
                    mapfile.write("{0}\t{1}\t\n".format(acc, tax_strain[0]))



if __name__ == '__main__':
    main(parser.parse_args())

