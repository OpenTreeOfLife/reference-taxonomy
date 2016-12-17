import sys
from org.opentreeoflife.taxa import Taxonomy, Newick

source = sys.argv[1]    # Name of directory containing original taxonomy (must end in /)

ott = Taxonomy.getRawTaxonomy(source, 'ott')

count = 0

for taxon in ott.taxa():
    global count
    count += 1
    if (taxon.properFlags & Taxonomy.INCONSISTENT) != 0:
        mrca = taxon.parent
        if mrca.count() > 500000: continue
        space = taxon.sourceIds.get(0).prefix   # the inconsistent taxon

        children = []
        for child in mrca.getChildren():
            child_space = child.sourceIds.get(0).prefix
            if (child_space == space) and (child.properFlags & Taxonomy.UNPLACED) != 0:
                children.append(child)

        print 'inconsistent:', taxon, ' mrca:', mrca, len(children), mrca.count()

        if len(children) < 30:
            for child in children:
                print '  unplaced in mrca:', child

print count, 'taxon records'
