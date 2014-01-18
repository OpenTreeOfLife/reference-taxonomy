# Jython script to build the Open Tree reference taxonomy

from org.opentreeoflife.smasher import Taxonomy
import sys
sys.path.append("feed/ott/")
from chromista_spreadsheet import fixChromista

ott = Taxonomy.newTaxonomy()

silva = Taxonomy.getTaxonomy('tax/silva/')
ott.absorb(silva, 'silva')

study713  = Taxonomy.getTaxonomy('tax/713/')
ott.notSame(study713.taxon('Buchnera'), silva.taxon('Buchnera'))
ott.absorb(study713, 'study713')

fung  = Taxonomy.getTaxonomy('tax/if/')
fung.smush()
fung.analyzeMajorRankConflicts()
ott.absorb(fung, 'if')

ncbi  = Taxonomy.getTaxonomy('tax/ncbi/')
ncbi.taxon('Fungi').hideDescendants()
ott.same(ncbi.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
ott.notSame(ncbi.taxon('Burkea'), fung.taxon('Burkea'))
ncbi.analyzeOTUs()
ott.absorb(ncbi, 'ncbi')

# Misspelling in GBIF
# ott.taxon("Torricelliaceae").synonym("Toricelliaceae")

gbif  = Taxonomy.getTaxonomy('tax/gbif/')
gbif.smush()
gbif.taxon('Fungi').hideDescendants()
ott.same(gbif.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
ott.same(ncbi.taxon('5878'), gbif.taxon('10'))	  # Ciliophora
ott.same(ncbi.taxon('29178'), gbif.taxon('389'))  # Foraminifera
gbif.analyzeMajorRankConflicts()
ott.absorb(gbif, 'gbif')

fixChromista(ott)

ott.edit('feed/ott/edits/')
ott.deforestate()
ott.assignIds(Taxonomy.getTaxonomy('tax/prev_ott/'))
ott.dump('tax/ott/')

