# Jython script to build the "model village" taxonomy.

from org.opentreeoflife.smasher import Taxonomy

# Create model taxonomy
tax = Taxonomy.newTaxonomy()

# Add NCBI subset to the model taxonomy
ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/')
# analyzeOTUs sets flags on questionable taxa ("unclassified" and so on)
#  to allow the option of suppression downstream
ncbi.analyzeOTUs()
tax.absorb(ncbi)

# Add GBIF subset fo the model taxonomy
gbif = Taxonomy.getTaxonomy('t/tax/gbif_aster/')
# analyzeMajorRankConflicts sets the "major_rank_conflict" flag when
# intermediate ranks are missing (e.g. a family that's a child of a
# class)
gbif.analyzeMajorRankConflicts()
tax.absorb(gbif)

# "Old" patch system with tab-delimited files
tax.edit('t/edits/')

# Example of referring to a taxon
fam = tax.taxon("Phellinaceae")

# Example of how you might add a genus to the taxonomy
gen = tax.newTaxon("Opentreeia", "genus", "data:testing")
fam.add(gen)
gen.add(tax.newTaxon("Opentreeia sp. A", "species", "data:testing"))
gen.add(tax.newTaxon("Opentreeia sp. B", "species", "data:testing"))

# Test deletion feature
sp = tax.newTaxon("Opentreeia sp. C", "species", "data:testing")
gen.add(sp)
sp.prune()

# Assign identifiers to the taxa in the model taxonomy.  Identifiers
# assigned in the previous version are carried over to this version.
tax.assignIds(Taxonomy.getTaxonomy('t/tax/prev_aster/'))

# Write the model taxonomy out to a set of files
tax.dump("t/tax/aster/")
