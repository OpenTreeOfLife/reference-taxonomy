# Go through inclusion tests in germinator repo
# smash --jython util/check_inclusions.py

from org.opentreeoflife.taxa import Taxonomy
import csv, sys

def check(inclusionspath, ott):
    infile = open(inclusionspath, 'rb')
    reader = csv.reader(infile)
    for row in reader:
        small = row[0]
        big = row[1]
        small_id = row[2]

        small_taxa = ott.filterByAncestor(small, big)
        if small_taxa == None:
            small_taxa = ott.lookup(small)
            if small_taxa == None:
                print '** There is no taxon named %s' % (small,)
            elif len(small_taxa) == 1:
                small_tax = small_taxa[0]
                if small_tax.id != small_id:
                    print '** The id of %s (which is not in %s) is %s (expected %s)' % (small, big, small_tax.id, small_id)
                    show_interloper(small_id, ott)
                else:
                    print '** Taxon %s (%s) is not in %s' % (small, small_id, big)
            else:
                print '** %s is polysemous, and no choice is in %s' % (small, big)
            
        elif len(small_taxa) == 1:
            small_tax = small_taxa[0]
            if small_tax.id != small_id:
                print '** The id of %s in %s is %s (expected %s)' % (small, big, small_tax.id, small_id)
                show_interloper(small_id, ott)

        else:
            print '** More than one taxon named %s is in %s' % (small, big)
            print '  ', small_taxa

    infile.close()

def show_interloper(small_id, ott):
    probe = ott.lookupId(small_id)
    if probe != None:
        print '   Id %s belongs to %s' % (small_id, probe)
    else:
        print '   (There is no taxon with id %s)' % small_id

if __name__ == '__main__':
    if len(sys.argv) == 3:
        inclusions = sys.argv[1]
        taxname = sys.argv[2]
    else:
        print 'ignoring supplied args', sys.argv
        inclusions = '../germinator/taxa/inclusions.csv'
        taxname = 'tax/ott/'
    check(inclusionspath, Taxonomy.getTaxonomy(taxname, 'ott'))
