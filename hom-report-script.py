from org.opentreeoflife.smasher import Taxonomy
from org.opentreeoflife.smasher import Reportx

def report(tag):
	tax = Taxonomy.getTaxonomy('tax/' + tag + '/', tag)
	Reportx.report(tax, tag + '-mrca-report.tsv')

report('silva')
report('if')
report('ncbi')
report('gbif')
report('irmng')
report('ott')
