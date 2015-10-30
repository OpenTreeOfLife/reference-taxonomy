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
            probe = corr.assignedRegistration(taxon)
            if probe == None:
                probes = corr.coget(taxon)
                if probes == None:
                    h += 1
                elif len(probes) == 1:
                    i += 1
                else:
                    j += 1
        print ' no reg assigned: %s (0 compatible regs), %s (1), %s (>1)' % (h, i, j)

    def show_unmapped(tax, r, corr):
        i = 0
        for taxon in tax:
            probe = corr.assignedRegistration(taxon)
            if probe == None:
                if i < 5:
                    print 'missing registration:', taxon, corr.coget(taxon)
                i = i + 1
        print i, 'unmapped'

    def compare_correspondences(corr, newcorr):
        for taxon in corr.taxa():
            probe = newcorr.assignedRegistration(taxon)
            if probe == None:
                regs = newcorr.coget(taxon)
                if regs == None:
                    oldprobe = corr.assignedRegistration(taxon)
                    if oldprobe == None:
                        print 'for %s, no compatible registration(s) in either correspondence' % (taxon,)
                    else:
                        print 'for %s, registration(s) %s disappeared' % (taxon, oldprobe)
                        print '| %s' % (newcorr.explain(taxon, oldprobe),)
                else:
                    for oldreg in corr.coget(taxon):
                        if not (oldreg in regs):
                            print 'for %s, registration %s not among compatible registrations %s' % (taxon, oldreg, regs)

    def do_taxonomy(tax1, r):
        print '---'
        print tax1.getTag(), 'taxa:', tax1.count()

        # 
        corr = Correspondence(r, tax1)
        print 'Assigning registrations to nodes in', tax1
        corr.assign()
        analyze(r, tax1, corr, 'before extend:')

        # this should create new registrations for all taxa
        print 'Extending registry for', tax1
        corr.extend()
        analyze(r, tax1, corr, 'after extend:')
        show_unmapped(tax1, r, corr)

        # see whether lookup is repeatable.
        # this should match most, if not, all, taxa with registrations in r
        print 'Re-assigning registrations to nodes in', tax1
        newcorr = Correspondence(r, tax1)
        newcorr.assign()
        analyze(r, tax1, newcorr, 'after remap:')
        compare_correspondences(corr, newcorr)

        return newcorr

    # WORK IN PROGRESS
    def compare_how_taxonomies_map(tax1, tax2, corr1, corr2):
        for node1 in tax1:
            if (not node1.isHidden()) and node1.id != None:
                node2 = tax2.lookupId(node1.id)
                if node2 != None and (not node2.isHidden()):
                    reg1 = corr1.assignedRegistration(node1)
                    reg2 = corr2.assignedRegistration(node2)
                    if reg1 != reg2:
                        print "registration for node id differs between the two taxonomies", node1, reg1, node2, reg2
                        print corr2.explain(node2, reg1, corr2)

    r = Registry()

    tax1 = Taxonomy.getTaxonomy(t1, t1.split('/')[-2])
    corr1 = do_taxonomy(tax1, r)

    tax2 = Taxonomy.getTaxonomy(t2, t2.split('/')[-2])
    corr2 = do_taxonomy(tax2, r)

    compare_how_taxonomies_map(tax1, tax2, corr1, corr2)

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
