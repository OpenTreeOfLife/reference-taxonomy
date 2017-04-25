# Adds a fourth 'name' column to the accessionid_to_taxonid.tsv table.

import argparse, csv

def get_accession_to_taxon(accessionfilename):
    accession_to_taxon = {}
    with open(accessionfilename, 'r') as afile:
        i = 0
        for line in afile:
            fields = line.split('\t')
            ncbi_id = fields[1].strip()
            if ncbi_id != '*':
                genbank_id = fields[0].strip()
                if len(fields) >= 3 and fields[2] != '':
                    strain = fields[2].strip()
                else:
                    strain = None
                accession_to_taxon[genbank_id] = (ncbi_id, strain)
                i += 1
                if i % 100000 == 0: print genbank_id, ncbi_id, strain
        print i, 'accessions'
    return accession_to_taxon

def get_ncbi_to_name(taxonomyfilename):
    ncbi_to_name = {}
    i = 0
    with open(taxonomyfilename, 'r') as nfile:
        reader = csv.reader(nfile, delimiter='\t')
        header = reader.next()
        uidx = header.index('uid')
        namex = header.index('name')
        for row in reader:
            # id, parentid, name, rank
            id = row[uidx]
            if id != 'uid':
                name = row[namex]   # ??
                if name != None:
                    ncbi_to_name[id] = name
                    i += 1
                    if i % 200000 == 0: print i, id, name, row
    return ncbi_to_name

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='NCBI ids and names')
    parser.add_argument('ncbi', help='taxonomy.tsv for NCBI taxonomy in open tree format')
    parser.add_argument('mappings', help='genbank accession id to NCBI taxon mapping')
    parser.add_argument('out', help='where to write the output file')
    args = parser.parse_args()

    print 'reading', args.mappings
    accession_to_taxon = get_accession_to_taxon(args.mappings)

    print 'reading', args.ncbi
    ncbi_to_name = get_ncbi_to_name(args.ncbi)

    print 'writing', args.out
    with open(args.out, 'w') as outfile:
        i = 0
        writer = csv.writer(outfile, delimiter='\t')
        for gid in accession_to_taxon:
            (ncbi_id, strain) = accession_to_taxon[gid]
            name = ncbi_to_name.get(ncbi_id)
            row = writer.writerow((gid, ncbi_id, strain, name))
            i += 1
            if i % 500000 == 0:
                print gid, ncbi_id, strain, name
