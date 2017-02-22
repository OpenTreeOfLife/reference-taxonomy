from org.opentreeoflife.taxa import Taxonomy

import os, csv

home = '../..'

ott = Taxonomy.getRawTaxonomy(os.path.join(home, 'tax/ott/'), 'ott')
# for faster testing:
#ott = Taxonomy.getTaxonomy(os.path.join(home, 't/tax/aster/'), 'ott')

def doit():

    prefix_to_count = {}
    otu_count = 0
    ott_count = 0

    with open(os.path.join(home, 'ids_that_are_otus.tsv'), 'r') as infile:
        reader = csv.reader(infile, delimiter='\t')
        for row in reader:
            otu_count += 1
            id = row[0]
            if otu_count % 50000 == 0: print otu_count, id
            node = ott.lookupId(id)
            if node != None:
                ott_count += 1
                for qid in node.sourceIds:
                    prefix = qid.prefix
                    count = prefix_to_count.get(prefix, 0)
                    prefix_to_count[prefix] = count + 1

    for prefix in prefix_to_count:
        print prefix, prefix_to_count[prefix]
    print 'ott', ott_count

doit()
