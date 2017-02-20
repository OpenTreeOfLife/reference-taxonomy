# How many phylesystem OTUs are in NCBI?

import codecs

in_ncbi = {}

with codecs.open('tax/ott/taxonomy.tsv', 'r', 'utf-8') as infile:
    i = 0
    infile.next()  # header
    for line in infile:
        [id, _] = line.split('\t', 1)
        if 'ncbi' in line:
            in_ncbi[id] = True
        if i % 500000 == 0: print i, id
        i += 1

count = 0

with open('ids_that_are_otus.tsv', 'r') as infile:
    i = 0
    for line in infile:
        [id, _] = line.split('\t', 1)
        if id in in_ncbi:
            count += 1
        if i % 500000 == 0: print i, id
        i += 1

print count, 'out of', i, 'OTUs are in NCBI'
