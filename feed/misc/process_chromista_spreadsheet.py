# The 'project' with the emphasis on the second syllable, as in a
# relational algebra projection.

import csv, string, sys

with open(sys.argv[1], 'rb') as csvfile:
    csvreader = csv.reader(csvfile)
    csvreader.next()
    print '# coding=utf-8'
    print
    print 'def fixonetaxon(tax, taxon, current, proposed):'
    print '    curr = tax.taxon(taxon, current)'
    print '    prop = tax.taxon(proposed)'
    print '    if (prop != None) and (curr != None) and (curr.getParent() != prop):'
    print '        if (curr.getParent() != tax.taxon(current)):'
    print '            print "** Parent altered by IRMNG:", curr, curr.getParent(), prop'
    print '        else:'
    print '            prop.take(curr)'
    print

    print "def fixChromista(tax):"
    for row in csvreader:
        taxon = row[0].strip()
        currentparent = row[1].strip()
        proposed = row[2].strip()
        if taxon != '' and proposed != '' and not ('incertae' in proposed):
            reference = row[3].strip()
            notes = row[4].strip()

            print '    # See %s'%(reference)
            if notes != '':
                print '    # %s'%(notes)
            print "    fixonetaxon(tax, '%s', '%s', '%s')"%(taxon, currentparent, proposed)
            if 'incertae sedis' in notes:
                print "    tax.taxon('%s').incertaeSedis()"%(taxon)
            print ''
