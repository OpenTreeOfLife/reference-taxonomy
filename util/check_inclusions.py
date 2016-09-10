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
        small_id_tax = ott.lookupId(small_id)

        small_nodes = ott.filterByAncestor(small, big)
        if small_nodes == None:
            small_nodes = ott.lookup(small)
            if small_nodes == None:
                print '** There is no taxon named %s' % (small,)
            else:
                print '** There is no taxon named %s (id %s) in %s' % (small, small_id, big)
                for small_node in small_nodes:
                    small_tax = small_node.taxon()
                    div = small_tax.getDivision().name
                    if small_tax != small_id_tax:
                        print ('   There is a %s (division %s) not in %s; its id is %s'
                               % (small, div, big, small_tax.id))
                    else:
                        print ('   There is a %s (division %s) not in %s; its id happens to be %s' 
                               % (small, div, big, small_id))
                    show_interloper(small_node, small_id, ott)
            
        else:
            smaller_nodes = [x for x in small_nodes if x.name == small]
            if len(smaller_nodes) == 1:
                small_nodes = smaller_nodes

            if len(small_nodes) == 1:
                small_node = small_nodes[0]
                small_tax = small_node.taxon()
                if small_id != '' and small_tax != small_id_tax:
                    print '** The id of %s in %s is %s (expected %s)' % (small, big, small_tax.id, small_id)
                    show_interloper(small_node, small_id, ott)

            else:
                print '** More than one taxon named %s is in %s' % (small, big)
                print '  ', small_nodes

    infile.close()

def show_interloper(small_node, small_id, ott):
    if small_node != small_node.taxon():
        print '   %s is a synonym for %s' % (small_node.name, small_node.taxon().name)
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
        inclusions = 'inclusions.csv'
        taxname = 'tax/ott/'
    check(inclusions, Taxonomy.getTaxonomy(taxname, 'ott'))
