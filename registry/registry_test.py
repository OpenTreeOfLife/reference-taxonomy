"""
Start with an empty registry.
Register a taxonomy.
Assign ids for a second taxonomy and see how different it is from the first.
Register the second taxonomy.
Assign ids for second taxonomy.
At this point we should have 'best' registry ids for every node in the second taxonomy.  If not, there is something wrong.
TBD: Test the first taxonomy against the augmented registry and see if we get the same answer.
"""


from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.registry import Registry, Correspondence

import sys

# export PYTHONPATH=`pwd`:$PYTHONPATH

def show_unmapped(tax, r, corr):
    i = 0
    for taxon in tax:
        if not Registry.isTerminalTaxon(taxon):
            probe = corr.assignedRegistration(taxon)
            if probe == None:
                if i < 5:
                    print 'missing registration:', taxon
                i = i + 1
    print i, 'unmapped'

def compare_correspondences(corr1, corr2):
    for node1 in corr1.taxonomy:
        if (not node1.isHidden()) and (not Registry.isTerminalTaxon(node1)):
            reg1 = corr1.assignedRegistration(node1)
            if node1.name != None and node1.id != None:
                node2 = corr2.taxonomy.lookupId(node1.id)
                if node2 != None and (not node2.isHidden()):
                    reg2 = corr2.assignedRegistration(node2)
                    if reg2 == None:
                        if reg1 == None:
                            print 'for %s, no assigned registration in either correspondence' % (node1,)
                        else:
                            print 'for %s, registration %s no longer resolves' % (node1, reg1)
                            print '| %s' % (corr2.explain(node2, reg1),)
                    else:
                        if reg1 == None:
                            print 'for %s, assigned in second correspondence only %s' % (node2, reg2)
                            print '| %s' % (corr2.explain(node2, reg1),)
                        elif reg1 != reg2:
                            node3 = corr2.resolve(reg1)
                            if node3 == node2:
                                True
                                #print 'old registration %s resolves to new node %s' % (reg1, node2)
                                #print '| %s' % (corr2.explain(node2, reg1),)
                            elif node3 == None:
                                print 'for %s, assignment %s (dead) changed to %s' % (node1, reg1, reg2)
                                print '| %s' % (corr2.explain(node2, reg1),)
                            else:
                                print 'for %s, assignment %s changed to %s' % (node1, reg1, reg2)
                                print '| (old registration %s now resolves to %s)' % (reg1, node3)
                                print '| %s' % (corr2.explain(node2, reg1),)
            elif False:
                node2 = corr2.resolve(reg1)
                if node2 == None:
                    print 'cannot find new node corresponding to %s=%s', node1, reg1


def do_taxonomy(tax1, r):
    print tax1.getTag(), 'taxa:', tax1.count()
    tax1.cleanRanks()
    tax1.inferFlags()

    # 
    corr = Correspondence(r, tax1)
    print '--- Assigning registrations to nodes in', tax1
    corr.resolve()

    # this should create new registrations for all taxa
    print '--- Extending registry for', tax1
    corr.extend()
    show_unmapped(tax1, r, corr)

    # see whether lookup is repeatable.
    # this should match most, if not, all, taxa with registrations in r
    print '--- Re-assigning registrations to nodes in', tax1
    newcorr = Correspondence(r, tax1)
    newcorr.resolve()
    # compare_correspondences(corr, newcorr)

    return newcorr


def run_tests(treenames):

    r = Registry()

    first = None
    last = None

    for t1 in treenames:

        tree = Taxonomy.getTaxonomy(t1, t1.split('/')[-2])
        last = do_taxonomy(tree, r)
        if first == None:
            first = last

    # compare_correspondences(first, last)

    r.dump('registry.csv')


# You can run the test on any pair of taxonomies.
# It will make more sense if the second is derived from the first,
# either a successor taxonomy version of a new synthetic tree.

# Asterales

# Two different versions of the asterales taxonomy (on JAR's disk)
# run_test(['../t/tax/aster.2/', '../t/tax/aster/'])

# How to extract the Asterales subtree from OTT 2.8:
#   unpack http://files.opentreeoflife.org/ott/ott2.8/ott2.8.tgz to ../tax/prev_ott
#   smash
#   ott28 = Taxonomy.getTaxonomy('../tax/prev_ott/')
#   ott28.select(ott28.taxon('Asterales')).dump('asterales-ott28/')
# Similarly for any other taxon, e.g. Fungi, Chloroplastida, etc.
# Extract from synthetic tree v3.0:
#   unpack http://files.opentreeoflife.org/trees/draftversion3.tre.gz
#   smash
#   synth3 = Taxonomy.getNewick('draftversion3.tre', 'synth')
#   synth3.select(synth3.taxon('Asterales')).dump('asterales-synth3/')

# run_test(['aster-ott28/', 'aster-synth3/'])

# Fungi

#   ott28.select(ott28.taxon('Fungi')).dump('fungi-ott28/')
#   synth3.select(synth3.taxon('Fungi')).dump('fungi-synth3/')
# run_test(['fungi-ott28/', 'fungi-synth3/'])

# Plants

#   ott28.select(ott28.taxon('Chloroplastida')).dump('plants-ott28/')
#   synth3.select(synth3.taxon('Chloroplastida')).dump('plants-synth3/')

if len(sys.argv) > 1:
    run_tests(sys.argv[1:])
else:
    run_tests(['plants-ott28/', 'plants-synth3/'])
