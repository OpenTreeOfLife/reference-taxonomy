# Go through inclusion tests in germinator repo
# smash --jython util/check_inclusions.py

from org.opentreeoflife.smasher import Taxonomy
import csv, sys

def check(ott):
    infile = open('../germinator/taxa/inclusions.csv', 'rb')
    reader = csv.reader(infile)
    for row in reader:
        small = row[0]
        big = row[1]
        small_id = row[2]

        small_tax = ott.maybeTaxon(small_id)
        if small_tax == None:
            small_tax = ott.maybeTaxon(small)
            if small_tax == None:
                print '** No unique taxon with id %s or name %s' % (small_id, small)
            else:
                print '** %s is %s, not %s' % (small, small_tax.id, small_id)
        else:
            look = ott.maybeTaxon(small, big)
            if look == None:
                print '** %s=%s not under %s' % (small, small_id, big)
                small_tax.show()
            elif look != small_tax:
                print '** The %s that descends from %s is %s, not %s' % (small, big, look.id, small_id)
            if small_tax.isHidden():
                print '%s (%s) is hidden' % (small, small_id)

    infile.close()

if __name__ == '__main__':
    taxname = 'tax/ott/'
    if len(sys.argv) > 1:
        taxname = sys.argv[1]
    else:
        print sys.argv
    check(Taxonomy.getTaxonomy(taxname))
