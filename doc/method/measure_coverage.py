# Command line arguments: tax ids
#   tax: path to taxonomy, e.g. ../../tax/ott/
#   ids: path to ids file, e.g. ../../ids_that_are_otus.tsv

# To test:
#   ../../bin/jython measure_coverage.py ../../t/tax/aster/ ../../ids_in_synthesis.tsv


from org.opentreeoflife.taxa import Taxonomy

import os, csv, sys

home = '../..'

def doit(tax_path, ids_path):

    ott = Taxonomy.getRawTaxonomy(tax_path, 'ott')

    all_nodes = {}

    with open(ids_path, 'r') as infile:
        reader = csv.reader(infile, delimiter='\t')
        otu_count = 0
        for row in reader:
            id = row[0]
            if otu_count % 50000 == 0: print otu_count, id
            otu_count += 1
            node = ott.lookupId(id)
            if node != None:
                all_nodes[node.id] = node

    print 'OTT taxa assigned to OTUs:', len(all_nodes)

    prefix_to_count = {}
    ott_count = 0

    for id in all_nodes:
        node = all_nodes[id]
        ott_count += 1
        for qid in node.sourceIds:
            prefix = qid.prefix
            count = prefix_to_count.get(prefix, 0)
            prefix_to_count[prefix] = count + 1

    print 'OTT ids assigned to OTUs:', otu_count
    for prefix in prefix_to_count:
        print prefix, prefix_to_count[prefix]

doit(sys.argv[1], sys.argv[2])

