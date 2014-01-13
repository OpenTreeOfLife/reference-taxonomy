# Jython script to build the Open Tree reference taxonomy

from org.opentreeoflife.smasher import Taxonomy

silva = Taxonomy.getTaxonomy('tax/silva/');
s713  = Taxonomy.getTaxonomy('tax/713/');
if    = Taxonomy.getTaxonomy('tax/if/');
if.analyzeMajorRankConflicts()

ncbi  = Taxonomy.getTaxonomy('tax/ncbi/');
ncbi.analyzeOTUs()

gbif  = Taxonomy.getTaxonomy('tax/gbif/');
gbif.analyzeMajorRankConflicts()

tax = Taxonomy.newTaxonomy()
tax.absorb(silva, "silva")
tax.absorb(s713, "study713")
tax.absorb(if, "if")
tax.absorb(ncbi, "ncbi")
tax.absorb(gbif, "gbif")

tax.edit('feed/ott/edits/')
tax.deforestate()

tax.assignIds(Taxonomy.getTaxonomy('tax/prev_ott/'))
tax.dump("tax/ott/")

