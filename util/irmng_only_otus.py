# Command line argument = file to write to
# Writes a row for every OTT id that
#  (a) occurs in tax/ott/,
#  (b) occurs as an OTU in phylesystem,
#  (c) is sourced only from in IRMNG.

import csv, sys

from org.opentreeoflife.taxa import Taxonomy, Rank
from org.opentreeoflife.smasher import UnionTaxonomy

union = UnionTaxonomy.newTaxonomy('ott')
union.loadPreferredIds('ids_that_are_otus.tsv', False)
union.loadPreferredIds('ids_in_synthesis.tsv', True)

ott = Taxonomy.getTaxonomy('tax/ott/', 'ott')
#ott = Taxonomy.getTaxonomy('t/tax/aster/', 'ott')

with open(sys.argv[1], 'w') as outfile:
    writer = csv.writer(outfile)
    writer.writerow(['irmng','ott','name','synthesis'])
    for taxon in ott.taxa():
        # if (taxon.rank == Rank.SPECIES_RANK and ...)
        if (len(taxon.sourceIds) == 1 and
            taxon.sourceIds[0].prefix == 'irmng'):
            probe = union.importantIds.lookupId(taxon.id)
            if probe != None:
                writer.writerow([taxon.sourceIds[0].id,
                                 taxon.id,
                                 taxon.name,
                                 'synthesis' if probe.inSynthesis else ''])
