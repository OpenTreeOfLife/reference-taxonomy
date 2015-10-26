from org.opentreeoflife.smasher import Taxonomy, Flag
from org.opentreeoflife.smasher import Registry

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

        corr = r.mapRegistrationsToTaxa(tax1)
        analyze(r, tax1, corr, 'before register:')

        # this should create new registrations for all taxa
        r.register(tax1, corr)
        analyze(r, tax1, corr, 'after register:')
        show_unmapped(tax1, r, corr)

        # see whether lookup is repeatable.
        # this should match most, if not, all, taxa with registrations in r
        newcorr = r.mapRegistrationsToTaxa(tax1)
        analyze(r, tax1, newcorr, 'after remap:')
        compare_correspondences(corr, newcorr)

        loser = tax1.maybeTaxon('5507785')
        if loser != None:
            x = loser.parent.children.get(0)
            y = loser.parent.children.get(1)
            print 'siblings:', loser.parent.children
            print 'hidden:', x.isHidden(), y.isHidden()
            print 'flags:', Flag.flagsAsString(x), Flag.flagsAsString(y)
            print 'compare:', r.betterAsType.compare(x, y)

    r = Registry.newRegistry()

    do_taxonomy(Taxonomy.getTaxonomy(t1, t1.split('/')[-2]), r)
    do_taxonomy(Taxonomy.getTaxonomy(t2, t2.split('/')[-2]), r)

    r.dump('registry.csv')


# Asterales

# run_test('../t/tax/aster.2/', '../t/tax/aster/')

# ott28 = Taxonomy.getTaxonomy('../tax/prev_ott/')
# ott28.select(ott28.taxon('Asterales')).dump('asterales-ott28/')

# run_test('asterales-ott28/', 'asterales-synth/')

# Fungi

# ott28.select(ott28.taxon('Fungi')).dump('fungi-ott28/')
run_test('fungi-ott28/', 'fungi-synth3/')

# Plants

# ott28.select(ott28.taxon('Chloroplastida')).dump('plants-ott28/')
# run_test('plants-ott28/', 'plants-synth3/'')
