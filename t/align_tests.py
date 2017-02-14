# Need these examples:
#   lineage
#   overlap    
#   proximity

import sys, codecs, subprocess
from org.opentreeoflife.taxa import Taxonomy, Newick
from org.opentreeoflife.smasher import UnionTaxonomy

blustery = 0

# Test alignment heuristics in simple cases.

def tst(noise, target, source):
    print noise
    sep = Taxonomy.getRawTaxonomy('tax/skel/', 'ott')
    t = Taxonomy.getRawTaxonomy(target, 'target')
    s = Taxonomy.getRawTaxonomy(source, 'source')
    u = combine(sep, t, s, blustery)
    u.dumpChoices('align_tests_choices.tsv')
    subprocess.call(['cat', 'align_tests_choices.tsv'])

# Copied from merge_tests.py

def combine(sep, t, s, bluster):
    u = UnionTaxonomy.newTaxonomy('union')
    u.blustery = 0
    u.setSkeleton(sep)

    ta = u.alignment(t)
    u.align(ta)
    u.merge(ta)
    print u.lookup('a')

    u.blustery = bluster
    print s.lookup('a')
    sa = u.alignment(s)

    for root in s.roots():
        sa.alignTaxon(root)

    u.align(sa)
    debug_alignment(sa)
    return u

def debug_alignment(a):
    for taxon in a.keySet():
        print taxon, a.getAnswer(taxon)

def write_newick(taxy):
    sys.stdout.write(newick(taxy))
    sys.stdout.write('\n')

def newick(taxy):
    return Newick.toNewick(taxy, Newick.USE_NAMES)


# Separation
if False:
    tst('separation', '((a)Metazoa,(a)Fungi)life', '((a)Metazoa)life')
# Lineage
tst('lineage', '((a)b,(a)c)life', '((a)b,((a2)c)life')
# Overlap
tst('overlap', '((a)b,(a2)b)life', '((a)b)life')
# Proximity
tst('proximity', '((a)Metazoa,a)life', '(a)life')
