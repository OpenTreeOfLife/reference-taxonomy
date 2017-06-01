# Obtain NCBI ids and strain names for SILVA cluster from Genbank.
# This script access the .seq files directly, instead of using eutils.

# Command line arguments:
#   Input: SILVA unprocessed taxonomy path (from .fasta file)
#   Output: pickle file path

# Original by Peter Midford, 27 January 2015
# Former location in this repository was feed/genbank/

import argparse
import time
import sys, os
import pickle

# These maximum values need to be updated manually from the directory listing 
# at ftp://ftp.ncbi.nlm.nih.gov/genbank/
# This should be automated - get directory listing, parse file names, etc.
# alternatively: a try/except
# Numbers here are current as of 2017-04-20

RANGES = {"gbbct": 350, # bacteria, was 159
          "gbinv": 153, # invertebrates, was 132
          "gbmam": 39,   # other mammals, was 9
          "gbpln": 145,  # plants, was 95
          "gbpri": 56,  # primates, was 48
          "gbrod": 30,  # rodents, was 31
          "gbvrt": 64   # other vertebrates, was 44
          # "gbvrl": 33,  # virus
          # "gbphg": 2,  # phage
}

FTP_SERVER = 'ftp://ftp.ncbi.nlm.nih.gov/genbank/'
SILVA_FILE = sys.argv[1]  # 'feed/silva/work/silva_no_sequences.fasta'
DEFAULT_PICKLE_FILE = sys.argv[2]  #'Genbank.pickle'

# Process one genbank flat file, extracting taxon ids and strain names.

def process_file(flatfilespec, accessions, conflicts, interesting_ids):
    time.sleep(1)    # try to forestall throttling
    with open(flatfilespec) as flatfile:
        locus_id = None
        accession_id = None
        found_features = False
        found_source = False
        taxon_id = None
        strain_id = None
        i = 0

        def finish_accession():
            if (accession_id and taxon_id):
                if strain_id:
                    accession_pair = (taxon_id, strain_id)
                else:
                    accession_pair = (taxon_id, None)
                if accession_id in accessions:
                    accession_value = accessions[accession_id]
                    if not accession_pair == accession_value:
                        new_conflict = (accession_id, 
                                        accession_value, 
                                        accession_pair)
                        if new_conflict not in conflicts:
                            conflicts.add(new_conflict)
                            print("conflict at %s: s% and %s" % new_conflict)
                else:
                    i += 1
                    if i % 20000 == 0: print accession_id, taxon_id
                    if accession_id in interesting_ids:
                        accessions[accession_id] = accession_pair

        for line in flatfile:
            line = line.lstrip()
            first = first_token(line)
            if first != None:
                if first == 'LOCUS':
                    finish_accession()
                    accession_id = None
                    taxon_id = None
                    strain_id = None
                    found_features = False
                    found_source = False
                    tokens = tokenize(line)
                    locus_id = tokens[1]
                elif first == 'ACCESSION':
                    tokens = tokenize(line)
                    if len(tokens) > 1:
                        accession_id = tokens[1]
                elif (first == 'FEATURES') and accession_id:
                    # print('found features')
                    found_features = True
                elif (first == 'source') and found_features:
                    # print('found source')
                    found_features = False
                    found_source = True
                elif first in ['gene', 'CDS', 'exon', 'intron']:
                    found_source = False
                elif found_source and (first.startswith('/db_xref')):
                    feature = extract_feature(line, '/db_xref=')
                    taxon_id = feature[len('taxon:'):]   
                elif found_source and (first.startswith('/strain')):
                    feature = extract_feature(line, '/strain=')
                    strain_id = feature
        finish_accession()
        return (accessions, conflicts)

def tokenize(line):
    result = []
    for item in line[:-1].split(' '):
        if item != '':
            result.append(item)
    return result

def first_token(line):
    s = line.split(' ', 1)
    if len(s) >= 1 and s[0] != '':
        return s[0]
    else:
        return None

def extract_feature(line, tag):
    stripped = line.strip()
    detagged = stripped[len(tag):]
    return detagged.strip('"')

# Read the .fasta file (or sequence-stripped .fasta file) to find 
# out which genbank ids we care about.
# Has about .5 million rows.
def read_silva(filename):
    print 'reading', filename
    ids = {}
    with open(filename, 'r') as infile:
        for line in infile:
            stuff = line.lstrip('>').split('.', 1)
            id = stuff[0]
            ids[id] = True
    print '  %s accessions' % (len(ids))
    if not 'A45315' in ids:
        print '** Lost A45315 from SILVA'
    return ids

def driver():
    for dataset in RANGES.keys():
        for count in xrange(RANGES[dataset]):
            yield dataset+str(count+1)


def main(args):
    if os.path.exists(SILVA_FILE):
        interesting_ids = read_silva(SILVA_FILE)
    if os.path.exists(DEFAULT_PICKLE_FILE):
        with open(DEFAULT_PICKLE_FILE,'r') as picklefile:
            to_load = pickle.load(picklefile)
    else:
        to_load = None
    d = driver()
    if to_load:
        accessions, lastfile, conflicts = to_load
        print("accessions: {0:8d}; last: {1}".format(len(accessions),lastfile))
        segment = d.next()
        while lastfile[:-4] != segment:
            print("lastfile: {0}, segment: {1}".format(lastfile[:-4], segment))
            segment = d.next()
        segment = d.next() # advance to next sequence file
    else:
        accessions = {}
        conflicts = set()
        segment = d.next()
    while segment:
        remote_file = segment + '.seq.gz'
        local_file = segment + '.seq'
        command = ("curl -# '" + 
                   FTP_SERVER + 
                   remote_file + 
                   "' | gunzip > " + 
                   local_file)
        print command
        os.system(command)
        accessions, conflicts = process_file(local_file, accessions, conflicts, interesting_ids)
        print "accession count = {0:8d}".format(len(accessions))
        to_save = (accessions, local_file, conflicts)
        temp = DEFAULT_PICKLE_FILE + '.new'
        with open(temp, 'w') as picklefile:
            pickle.dump(to_save, picklefile, -1)
        os.rename(temp, DEFAULT_PICKLE_FILE)
        os.remove(local_file)
        segment = d.next()


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description='Text genbank flatfile parsing')

    main(parser.parse_args())

