# Go through inclusion tests in germinator repo

from org.opentreeoflife.smasher import Taxonomy
import csv

def check(ott):
    infile = open('../germinator/taxa/inclusions.csv', 'rb')
    reader = csv.reader(infile)
    for row in reader:
        small = row[0]
        big = row[1]
        if ott.maybeTaxon(small, big) == None:
            tax = ott.maybeTaxon(small)
            if tax == None:
                print '** No unique taxon named %s' % small
            else:
                print '** %s not under %s' % (small, big)
                tax.show()
    infile.close()

if __name__ == '__main__':
    ott = Taxonomy.getTaxonomy('tax/ott/')
    check(ott)
