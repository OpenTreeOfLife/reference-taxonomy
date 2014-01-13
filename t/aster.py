# Jython script to build the model village taxonomy.

from org.opentreeoflife.smasher import Taxonomy

ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/');
ncbi.analyzeOTUs()

gbif = Taxonomy.getTaxonomy('t/tax/gbif_aster/');
gbif.analyzeMajorRankConflicts()

tax = Taxonomy.unite([ncbi, gbif]);
tax.edit('t/edits/');
tax.assignIds(Taxonomy.getTaxonomy('t/tax/prev_aster/'));
tax.dump("t/tax/aster/");
