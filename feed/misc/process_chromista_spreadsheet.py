# The 'project' with the emphasis on the second syllable, as in a
# relational algebra projection.

import csv, string, sys

with open(sys.argv[1], 'rb') as csvfile:
    csvreader = csv.reader(csvfile)
    csvreader.next()
    print '# coding=utf-8'
    print
    print 'count = [0]'
    print
    print 'def fixonetaxon(tax, taxon, proposed):'
    print '    euk = tax.taxon("Eukaryota")'
    print '    curr = tax.taxon(taxon)'
    print '    prop = tax.taxon(proposed)'
    print '    if (prop != None) and (curr != None) and (curr.getParent() != prop):'
    print '        if (curr.getParent() != euk):'
    print '            print "** Parent was probably altered by IRMNG:", curr, curr.getParent(), prop'
    print '        else:'
    print '            prop.take(curr)'
    print '            count[0] += 1'
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
            print "    fixonetaxon(tax, '%s', '%s')"%(taxon, proposed)
            if 'incertae sedis' in notes:
                print "    tax.taxon('%s').incertaeSedis()"%(taxon)
            print ''

    print
    print 'print "Successes:", count[0]'
