from org.opentreeoflife.smasher import Taxonomy
from org.opentreeoflife.smasher import Reportx
import taxonomies

union = Taxonomy.newTaxonomy()
skel = Taxonomy.getTaxonomy('tax/skel/', 'skel')
union.setSkeleton(skel)

def report(tax, tag):
	tax.markDivisions(union);
	Reportx.report(tax, tag + '-mrca-report.tsv')

if True:
	report(taxonomies.loadOtt(), 'ott')
else:
	report(taxonomies.loadSilva(), 'silva')
	report(taxonomies.loadH2007(), 'h2007')
	report(taxonomies.loadFung(), 'if')
	report(taxonomies.loadNcbi(), 'ncbi')
	report(taxonomies.loadGbif(), 'gbif')
	report(taxonomies.loadIrmng(), 'irmng')
