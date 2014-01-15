# Jython script to build the model village taxonomy.

from org.opentreeoflife.smasher import Taxonomy

ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/')
ncbi.analyzeOTUs()

gbif = Taxonomy.getTaxonomy('t/tax/gbif_aster/')
gbif.analyzeMajorRankConflicts()

tax = Taxonomy.unite([ncbi, gbif])
tax.edit('t/edits/')

fam = tax.taxon("Phellinaceae")
gen = tax.newTaxon("Opentreeia", "genus", "data:testing")
fam.add(gen)
gen.add(tax.newTaxon("Opentreeia sp. A", "species", "data:testing"))
gen.add(tax.newTaxon("Opentreeia sp. B", "species", "data:testing"))

sp = tax.newTaxon("Opentreeia sp. C", "species", "data:testing")
gen.add(sp)
sp.prune()

tax.assignIds(Taxonomy.getTaxonomy('t/tax/prev_aster/'))
tax.dump("t/tax/aster/")
