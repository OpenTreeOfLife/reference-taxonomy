from org.opentreeoflife.smasher import Taxonomy
from org.opentreeoflife.smasher import Reportx
import taxonomies

ott = Taxonomy.newTaxonomy()
skel = Taxonomy.getTaxonomy('tax/skel/', 'skel')
ott.setSkeleton(skel)

def report(tax, tag):
	ott.markDivisions(tax)
#	Reportx.bogotypes(tax)
	taxonomies.checkDivisions(tax)
	Reportx.report(tax, tag + '-mrca-report.tsv')

if True:
	report(taxonomies.loadIrmng(), 'irmng')
else:
	silva = taxonomies.loadSilva()
	ott.notSame(silva.taxon('Ctenophora', 'Coscinodiscophytina'),
				skel.taxon('Ctenophora'))
	report(silva, 'silva')
	report(taxonomies.loadH2007(), 'h2007')
	report(taxonomies.loadFung(), 'if')
	report(taxonomies.loadNcbi(), 'ncbi')
	report(taxonomies.loadGbif(), 'gbif')
	report(taxonomies.loadIrmng(), 'irmng')
	report(taxonomies.loadOtt(), 'ott')
