
from org.opentreeoflife.smasher import Taxonomy, Alignment

def report(tag):
	tax = Taxonomy.getTaxonomy('tax/' + tag + '/', tag)
	# Alignment.homonymDensityReport(tax, tag + '-density-report.tsv')
	Alignment.homonymUncertaintyReport(tax, tag + '-uncertainty-report.tsv')
	# a = tax.alignTo(tax)

report('silva')
report('if')
report('ncbi')
report('gbif')
report('irmng')
report('ott')
