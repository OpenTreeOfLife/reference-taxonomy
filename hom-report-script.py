from org.opentreeoflife.smasher import Taxonomy
from org.opentreeoflife.smasher import HomonymReport 

union = Taxonomy.newTaxonomy()
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
