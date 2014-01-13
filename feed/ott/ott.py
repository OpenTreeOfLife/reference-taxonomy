# Jython script to build the Open Tree reference taxonomy

from org.opentreeoflife.smasher import Taxonomy

tax = Taxonomy.newTaxonomy()

silva = Taxonomy.getTaxonomy('tax/silva/')
tax.absorb(silva, "silva")

s713  = Taxonomy.getTaxonomy('tax/713/')
tax.absorb(s713, "study713")

fung  = Taxonomy.getTaxonomy('tax/if/')
# fung.smush()
fung.analyzeMajorRankConflicts()
tax.absorb(fung, "if")

ncbi  = Taxonomy.getTaxonomy('tax/ncbi/')
ncbi.analyzeOTUs()
tax.absorb(ncbi, "ncbi")

gbif  = Taxonomy.getTaxonomy('tax/gbif/')
# gbif.smush()
gbif.analyzeMajorRankConflicts()
tax.absorb(gbif, "gbif")

tax.edit('feed/ott/edits/')
tax.deforestate()
tax.assignIds(Taxonomy.getTaxonomy('tax/prev_ott/'))
tax.dump("tax/ott/")

