# Go through inclusion tests in germinator repo

from org.opentreeoflife.smasher import Taxonomy
import csv

ott = Taxonomy.getTaxonomy('tax/ott/')

infile = open('../germinator/taxa/inclusions.csv', 'rb')
reader = csv.reader(infile)
for row in reader:
	small = row[0]
	big = row[1]
	if ott.taxon(small, big) == None:
		print '** Failed', small, big
infile.close()
