import sys
from org.opentreeoflife.taxa import Taxonomy, Newick

source = sys.argv[1]    # Name of directory containing original taxonomy (must end in /)

ott = Taxonomy.getRawTaxonomy(source, 'ott')

count = 0
grafts = 0
non_tip_grafts = 0

# Seen = seen idspaces among ancestors.
# Returns set of seen idspaces.

def recur(taxon, seen):
    global count, grafts, non_tip_grafts
    count += 1

    # idspace (source) of taxon
    space = taxon.sourceIds.get(0).prefix

    all = empty()
    seen_child = adjoin(space, seen)
    for child in taxon.getChildren():
        under = recur(child, seen_child)
        child_space = child.sourceIds.get(0).prefix
        if child_space != space:
            # A graft or resolution.
            if intersectp(under, seen):
                # A resolution.
                print 'resolve', child, taxon, child.rank
            else:
                grafts += 1
                if not child.hasChildren():
                    True      # tip, not interesting
                else:
                    non_tip_grafts += 1
                    if child.count() < 1000:
                        True        # small, not interesting
                    else:
                        print 'graft', child, taxon, child.rank.name, child.count()
        all = union(all, under)
    return all

bit_positions = {}
next_position = 1

def bit_position(space):
    global next_position
    pos = bit_positions.get(space)
    if pos == None:
        pos = next_position
        if next_position < 2147483648:
            next_position = next_position * 2
        bit_positions[space] = pos
    return pos

def adjoin(x, s):
    return bit_position(x) | s

def union(s, t): return s | t

def intersectp(s, t): return (s & t) != 0

def empty(): return 0

def isin(x, s):
    return (bit_position(x) & s) != 0

for root in ott.roots():
    recur(root, empty())

print >>sys.stderr, grafts, 'grafts'
print >>sys.stderr, non_tip_grafts, 'non-tip grafts'

print >>sys.stderr, count, 'taxon records'
