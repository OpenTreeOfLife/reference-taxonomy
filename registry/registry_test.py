"""
Start with an empty registry.
Register a taxonomy.
Resolve a second taxonomy and see how different it is from the first.
Register the second taxonomy.
Resolve second taxonomy.
At this point we should have 'best' registry ids for every node in the second taxonomy.  If not, there is something wrong.
TBD: Test the first taxonomy against the augmented registry and see if we get the same answer.
"""


from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.registry import Registry

import sys

# export PYTHONPATH=`pwd`:$PYTHONPATH

def run_test(t1, t2):

    # A.
    # - start with a registry.
    # - find taxonomy T nodes matching the registry.
    # - register all new nodes.
    # B.
    # - find taxonomy T nodes matching the registry.
    # - report
    # C.
    # - find taxonomy T2 nodes matching the registry.
    # - report

    def analyze(r, tax, corr, comment):
        # corr is a registration <-> taxa correspondence
        print ('%s %s registrations, %s applied registrations, %s taxa covered' %
               (comment, r.size(), corr.size(), corr.cosize()))
        check(tax, r, corr)

    def check(t, r, corr):
        h = 0
        i = 0
        j = 0
        for taxon in t:
            probe = r.chooseRegistration(taxon, corr)
            if probe == None:
                probes = corr.coget(taxon)
                if probes == None:
                    h += 1
                elif len(probes) == 1:
                    i += 1
                else:
                    j += 1
        print ' no reg chosen: %s (0), %s (1), %s (>1)' % (h, i, j)

    def show_unmapped(tax, r, corr):
        i = 0
        for taxon in tax:
            probe = r.chooseRegistration(taxon, corr)
            if probe == None:
                if i < 5:
                    print 'missing registration:', taxon, corr.coget(taxon)
                i = i + 1
        print i, 'unmapped'

    def compare_correspondences(corr, newcorr):
        for taxon in corr.goats():
            probe = r.chooseRegistration(taxon, newcorr)
            if probe == None:
                regs = newcorr.coget(taxon)
                if regs == None:
                    oldprobe = r.chooseRegistration(taxon, corr)
                    if oldprobe == None:
                        print 'no registration(s) in either correspondence:', taxon
                    else:
                        print 'registration(s) disappeared:', taxon, oldprobe, r.explain(taxon, oldprobe, newcorr)
                else:
                    for oldreg in corr.coget(taxon):
                        if not (oldreg in regs):
                            print 'registration %s not in %s for %s' % (oldreg, regs, taxon)

    def do_taxonomy(tax1, r):
        print '---'
        print tax1.getTag(), 'taxa:', tax1.count()

        corr = r.resolve(tax1)
        analyze(r, tax1, corr, 'before register:')

        # this should create new registrations for all taxa
        r.register(tax1, corr)
        analyze(r, tax1, corr, 'after register:')
        show_unmapped(tax1, r, corr)

        # see whether lookup is repeatable.
        # this should match most, if not, all, taxa with registrations in r
        newcorr = r.resolve(tax1)
        analyze(r, tax1, newcorr, 'after remap:')
        compare_correspondences(corr, newcorr)

        return newcorr

    # WORK IN PROGRESS
    def compare_taxonomies(tax1, tax2, corr1, corr2):
        for node1 in tax1:
            if node1.id != None:
                node2 = tax2.lookupId(node1)
                if corr1.coget(node1) != corr2.coget(node2):
                    print "mismatch", node1, node2

    r = Registry.newRegistry()

    tax1 = Taxonomy.getTaxonomy(t1, t1.split('/')[-2])
    corr1 = do_taxonomy(tax1, r)

    tax2 = Taxonomy.getTaxonomy(t2, t2.split('/')[-2])
    corr2 = do_taxonomy(tax2, r)

    # compare_taxonomies(tax1, tax2, corr1 corr2)

    r.dump('registry.csv')


# You can run the test on any pair of taxonomies.
# It will make more sense if the second is derived from the first,
# either a successor taxonomy version of a new synthetic tree.

# Asterales

# Two different versions of the asterales taxonomy (on JAR's disk)
# run_test('../t/tax/aster.2/', '../t/tax/aster/')

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

# run_test('asterales-ott28/', 'asterales-synth/')

# Fungi

#   ott28.select(ott28.taxon('Fungi')).dump('fungi-ott28/')
#   synth3.select(synth3.taxon('Fungi')).dump('fungi-synth3/')
# run_test('fungi-ott28/', 'fungi-synth3/')

# Plants

#   ott28.select(ott28.taxon('Chloroplastida')).dump('plants-ott28/')
#   synth3.select(synth3.taxon('Chloroplastida')).dump('plants-synth3/')

if len(sys.argv) > 1:
    run_test(sys.argv[1], sys.argv[2])
else:
    run_test('plants-ott28/', 'plants-synth3/')
