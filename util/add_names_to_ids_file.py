# Jython script to add names as 3rd column to otu OTT ids file

from org.opentreeoflife.smasher import Taxonomy
import csv

ottdirname = 'tax/prev_ott/'
infilename = 'ids-that-are-otus.tsv'
outfilename = 'ids-that-are-otus.tsv.new'

# Load OTT

ids = Taxonomy.getTaxonomy(ottdirname)

# Load ids file and write ids file

def doit():
    win = 0
    lose = 0
    infile = open(infilename, 'r')
    outfile = open(outfilename, 'w')
    for line in infile:
        row = line.strip().split('\t')
        id = row[0]
        studies = row[1]
        taxon = ids.lookupId(id)
        name = ""
        if taxon != None:
            name = taxon.name
            win += 1
        else:
            lose += 1
        outfile.write("%s\t%s\t%s\n" % (id, studies, name))
    print win, lose
    infile.close()
    outfile.close()

doit()
