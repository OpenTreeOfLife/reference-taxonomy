# Jython script to build the "model village" taxonomy.

import os
from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.taxa import TsvEdits, Addition
from org.opentreeoflife.smasher import UnionTaxonomy
from claim import Has_child

# Create model taxonomy
tax = UnionTaxonomy.newTaxonomy('ott')

for name in ['Pentaphragma ellipticum',
             'Lachnophyllum',
             'Sipolisia',
             'Cicerbita bourgaei',
             'Adenophora triphylla',
             'Artemisia vulgaris',
             'Carlina libanotica',
]:
    tax.watch(name)

# Establish homonym-resolution skeleton (not really used here)
# skel = Taxonomy.getTaxonomy('tax/skel/', 'skel')
# tax.setSkeleton(skel)


# Add NCBI subset to the model taxonomy
ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/', 'ncbi')
# analyzeOTUs sets flags on questionable taxa ("unclassified" and so on)
#  to allow the option of suppression downstream
ncbi.analyzeOTUs()
tax.absorb(ncbi)

# Add GBIF subset fo the model taxonomy
gbif = Taxonomy.getTaxonomy('t/tax/gbif_aster/', 'gbif')
gbif.smush()
# analyzeMajorRankConflicts sets the "major_rank_conflict" flag when
# intermediate ranks are missing (e.g. a family that's a child of a
# class)
gbif.analyzeMajorRankConflicts()
tax.absorb(gbif)

# "Old" patch system with tab-delimited files
TsvEdits.edit(tax, 't/edits/')

claims = [
    Has_child('Asterales', 'Phellinaceae')
]

for claim in claims:
    print claim.check(tax)

gen = tax.newTaxon("Opentreeia", "genus", "data:testing")
gen.take(tax.newTaxon("Opentreeia sp. A", "species", "data:testing"))
gen.take(tax.newTaxon("Opentreeia sp. B", "species", "data:testing"))

# Example of referring to a taxon
fam = tax.maybeTaxon("Phellinaceae")

if fam != None:
    # Example of how you might add a genus to the taxonomy
    fam.take(gen)

# Test deletion feature
sp = tax.newTaxon("Opentreeia sp. C", "species", "data:testing")
gen.take(sp)
sp.prune("aster.py")

# tax.loadPreferredIds('ids-that-are-otus.tsv')

additions_path = 't/amendments-0'

# Assign identifiers to the taxa in the model taxonomy.  Identifiers
# assigned in the previous version are carried over to this version.
ids = Taxonomy.getTaxonomy('t/tax/prev_aster/', 'ott')

tax.carryOverIds(ids)    # performs alignment
Addition.processAdditions(additions_path, tax)
tax.assignNewIds(additions_path)

# Write the model taxonomy out to a set of files
tax.dump('t/tax/aster/', '\t|\t')
