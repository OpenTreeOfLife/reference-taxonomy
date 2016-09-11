from org.opentreeoflife.taxa import Taxonomy, HomonymReport

import os

def report(tag):
    tax = Taxonomy.getTaxonomy('tax/' + tag + '/', tag)
    tax.smush() 
    # HomonymReport.homonymDensityReport(tax, tag + '-density-report.tsv')
    # HomonymReport.homonymUncertaintyReport(tax, 'reports/' + tag + '-uncertainty-report.tsv')
    HomonymReport.homonymReport(tax, 'reports/' + tag + '-homonym-report.tsv')
    # a = tax.alignTo(tax)

report('silva')
report('fung')
report('worms')
report('ncbi')
report('gbif')
report('irmng')
report('ott')
report('prev_ott')
