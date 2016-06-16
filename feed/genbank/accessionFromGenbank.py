# Obtain NCBI ids and strain names for SILVA cluster from Genbank.
# This script access the .seq files directly, instead of using eutils.

import argparse
import time
import os
import pickle

# These maximum values need to be updated manually from the directory listing 
# at ftp://ftp.ncbi.nlm.nih.gov/genbank/
# This should be automated

RANGES = {"gbbct": 159,  # bacteria, now 238
          "gbinv": 132,  # invertebrates, now 136
          "gbmam": 9,   # other mammals, now 37
          "gbpln": 95,  # plants, now 125
          "gbpri": 48,  # primates, now 53
          "gbrod": 31,  # rodents
          "gbvrt": 44   # other vertebrates, now 60
          # "gbvrl": 33,  # virus
          # "gbphg": 2,  # phage
}

FTP_SERVER = 'ftp://ftp.ncbi.nlm.nih.gov/genbank/'
DEFAULT_PICKLE_FILE = 'Genbank.pickle'
SILVA_FILE = 'feed/silva/in/silva_no_sequences.fasta'

parser = argparse.ArgumentParser(description='Text genbank flatfile parsing')

def tokenize(line):
    result = []
    for item in line[:-1].split(' '):
        if item != '':
            result.append(item)
    return result

# Process one genbank flat file, extracting taxon ids and strain names.

def process_file(flatfilespec, accessions, conflicts, interesting_ids):
    with open(flatfilespec) as flatfile:
        locus_id = None
        accession_id = None
        found_features = False
        found_source = False
        taxon_id = None
        strain_id = None
        line = flatfile.readline()
        i = 0
        while line:
            tokens = tokenize(line)
            if len(tokens) > 0:
                if tokens[0] == 'LOCUS':
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
                        elif accession_id in interesting_ids:
                            accessions[accession_id] = accession_pair
                            i += 1
                            if i % 20000 == 0: print accession_id, taxon_id
                    accession_id = None
                    taxon_id = None
                    strain_id = None
                    found_features = False
                    found_source = False
                    locus_id = tokens[1]
                elif tokens[0] == 'ACCESSION' and len(tokens) > 1:
                    accession_id = tokens[1]
                elif (tokens[0] == 'FEATURES') and accession_id:
                    # print('found features')
                    found_features = True
                elif (tokens[0] == 'source') and found_features:
                    # print('found source')
                    found_features = False
                    found_source = True
                elif tokens[0] in ['gene', 'CDS', 'exon', 'intron']:
                    found_source = False
                elif found_source and (tokens[0].startswith('/db_xref')):
                    feature = extract_feature(line, '/db_xref=')
                    taxon_id = feature[len('taxon:'):]   
                elif found_source and (tokens[0].startswith('/strain')):
                    feature = extract_feature(line, '/strain=')
                    strain_id = feature
            line = flatfile.readline()
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
                elif accession_id in interesting_ids:
                    accessions[accession_id] = accession_pair
        return (accessions, conflicts)


def extract_feature(line, tag):
    stripped = line.strip()
    detagged = stripped[len(tag):]
    return detagged.strip('"')

# Read the .fasta file (or sequence-stripped .fasta file) to find 
# out which genbank ids we care about.
# Has about .5 million rows.
def read_silva(filename):
    ids = {}
    with open(filename, 'r') as infile:
        for line in infile:
            stuff = line.lstrip('>').split('.', 1)
            id = stuff[0]
            ids[id] = True
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
        with open(DEFAULT_PICKLE_FILE,"w") as picklefile:
            pickle.dump(to_save, picklefile, -1)
        command = ("rm " + local_file)
        os.system(command)
        segment = d.next()



if __name__ == '__main__':
    main(parser.parse_args())

