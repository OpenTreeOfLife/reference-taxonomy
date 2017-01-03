# What happened to the taxa in taxonomy 1, when taxonomy 1 was
# replaced by taxonomy 2?

import sys, os, json, argparse, csv
from org.opentreeoflife.taxa import Taxonomy, Nexson, Flag

def compare(t1, t2):
    print 'comparing', t1, 'to', t2
    retired = 0
    became_hidden = 0
    became_unhidden = 0
    became_extinct = 0
    became_unextinct = 0
    became_suppressed = 0
    became_unsuppressed = 0
    kept = 0
    novel = 0
    tax1 = Taxonomy.getTaxonomy(t1, 'x')
    tax1.inferFlags()
    tax2 = Taxonomy.getTaxonomy(t2, 'x')
    tax2.inferFlags()
    for taxon in tax1.taxa():
        probe = tax2.lookupId(taxon.id)
        if probe == None:
            retired += 1
        elif probe.isAnnotatedHidden() and not taxon.isAnnotatedHidden():
            became_hidden += 1
        elif not probe.isAnnotatedHidden() and taxon.isAnnotatedHidden():
            became_unhidden += 1
        elif probe.isExtinct() and not taxon.isExtinct():
            became_extinct += 1
        elif not probe.isExtinct() and taxon.isExtinct():
            became_unextinct += 1
        elif probe.isHidden() and not taxon.isHidden():
            became_suppressed += 1
        elif not probe.isHidden() and taxon.isHidden():
            became_unsuppressed += 1
        else:
            kept += 1
    for taxon in tax2.taxa():
        if tax1.lookupId(taxon.id) == None:
            novel += 1
    print
    print 'id retired:', retired
    print 'newly hidden:', became_hidden
    print 'no longer hidden:', became_unhidden
    print 'newly extinct:', became_extinct
    print 'no longer extinct:', became_unextinct
    print 'newly otherwise suppressed:', became_suppressed
    print 'no longer otherwise suppressed:', became_unsuppressed
    print 'new:', novel
    print 'no change in status:', kept

compare(sys.argv[1], sys.argv[2])
