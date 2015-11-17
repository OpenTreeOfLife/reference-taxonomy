# Requires python.security.respectJavaAccessibility = false
# on java command line or in .jython

from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.smasher import UnionTaxonomy, HomonymReport 

union = UnionTaxonomy()
skel = Taxonomy.getTaxonomy('tax/skel/', 'skel')
union.setSkeleton(skel)

def report(tax, tag):
    union.markDivisionsFromSkeleton(tax, skel)
    HomonymReport.homonymReport(tax, 'reports/' + tag + '-homonym-report.tsv')

if True:
    ott = Taxonomy.getTaxonomy('tax/ott/', 'ott')
    report(ott, 'ott')
else:
    import taxonomies
    report(taxonomies.loadSilva(), 'silva')
    report(taxonomies.loadH2007(), 'h2007')
    report(taxonomies.loadFung(), 'worms')
    report(taxonomies.loadFung(), 'if')
    report(taxonomies.loadNcbi(), 'ncbi')
    report(taxonomies.loadGbif(), 'gbif')
    report(taxonomies.loadIrmng(), 'irmng')
