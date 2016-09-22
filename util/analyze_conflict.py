# Takes two trees, and does a conflict analysis of the second
# against the first.
# Typically the first ("reference") taxonomy would be OTT.

# Trees are specified using any of the following forms (those accepted
# by getTaxonomy):
# 1. literal Newick string e.g.  (a,(b,c))d
# 2. Newick string in a file, name ending in .tre
# 3. 'interim taxonomy format', directory name, with a / at the end

# Give a short 'idspace' tag for each - choice doesn't matter much.
# E.g. 'ott' for OTT.  These show up in the output.

# E.g.
#  bin/jython util/analyze_conflict.py tax/ott/ ott scratch/Ruggiero/ rugg

import sys

from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.smasher import AlignmentByName
from org.opentreeoflife.conflict import ConflictAnalysis

def conflict(spec1, space1, spec2, space2):

    # Reference tree
    ref = Taxonomy.getTaxonomy(spec1, space1)

    # Input tree
    input = Taxonomy.getTaxonomy(spec2, space2)

    a = AlignmentByName(input, ref)
    a.align();

    if False:
        for node in input.taxa():
            print node, a.getTaxon(node)

    print 'Conflict analysis'
    ca = ConflictAnalysis(input, ref, a, False)
    print '  input root:', ca.inputRoot
    print '  ref root:', ca.refRoot
    print '  induced root:', ca.inducedRoot
    print '  ingroup:', ca.ingroup
    print '  induced ingroup:', ca.inducedIngroup
    print '  map size:', ca.map.size()
    print '  comap size:', ca.comap.size()

    mapped_tip_count = 0
    unmapped_tip_count = 0
    none_count = 0

    rel_counts = {}

    if ca.inducedRoot != None:
        for node in ca.ingroup.descendants(True):
            if node.hasChildren():
                art = ca.articulation(node)
                if art != None:
                    n = art.disposition.name
                    print node, n, art.witness
                    rel_counts[n] = rel_counts.get(n, 0) + 1
                else:
                    print node, 'no articulation'
                    none_count += 1
            elif a.getTaxon(node) != None:
                mapped_tip_count += 1
            else:
                unmapped_tip_count += 1
                print node, 'unmapped'
    else:
        print 'no induced root!'

    print
    for n in rel_counts:
        print '%s: %s' % (n, rel_counts[n])
    print 'Mapped tips:', mapped_tip_count
    print 'Unmapped tips:', unmapped_tip_count
    print 'Other:', none_count

# conflict('((a,b)ab,c,d)ee', 'x', '(a,(b,c)bc,d)e', 'y')
# conflict('tax/silva/', 'silva', 'scratch/Ruggiero/', 'rug')

conflict(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4])

# bin/jython scratch/conflict_test.py old/ott2.10draft11/ ott scratch/Ruggiero/ rug
