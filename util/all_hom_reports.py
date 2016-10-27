from org.opentreeoflife.taxa import Taxonomy, HomonymReport

import os

report_dir = 'report'

def report(dir, idspace):
    tax = Taxonomy.getRawTaxonomy(os.path.join('tax', dir, ''), idspace)
    # tax.smush() 
    # HomonymReport.homonymDensityReport(tax, dir + '-density-report.csv')
    # HomonymReport.homonymUncertaintyReport(tax, 'reports/' + dir + '-uncertainty-report.csv')
    if not os.path.isdir(report_dir):
        os.makedirs(report_dir)
    HomonymReport.homonymReport(tax, os.path.join(report_dir, dir + '-homonym-report.csv'))
    # a = tax.alignTo(tax)

report('ott', 'ott')
report('silva', 'silva')
report('fung', 'if')
report('worms', 'worms')
report('ncbi', 'ncbi')
report('gbif', 'gbif')
report('irmng', 'irmng')
report('prev_ott', 'ott')
