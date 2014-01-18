# The 'project' with the emphasis on the second syllable, as in a
# relational algebra projection.

import csv, string

with open('feed/ott/chromista-spreadsheet.csv', 'rb') as csvfile:
    csvreader = csv.reader(csvfile)
    csvreader.next()
    print '# coding=utf-8'
    print
    print 'def fixonetaxon(proposed, taxon):'
    print '    prop = tax.taxon(proposed)'
    print '    if prop != None:'
    print '        prop.take(tax.taxon(taxon))'
    print
    print "def fixtaxonomy(tax):"
    for row in csvreader:
        taxon = row[0].strip()
        currentparent = row[1].strip()
        proposed = row[2].strip()
        if taxon != '' and proposed != '':
            reference = row[3].strip()
            notes = row[4].strip()

            print '    # See %s'%(reference)
            if notes != '':
                print '    # %s'%(notes)
            print "    fixonetaxon('%s', '%s')"%(proposed, taxon)
            if 'incertae sedis' in notes:
                print "    incertaeSedis(tax.taxon('%s'))"%(taxon)
            print ''
