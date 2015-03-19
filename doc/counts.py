# Smasher script, for demonstration purposes.

# This was written as a one-off and isn't meant to show off good
# coding style.  The treatment of ambiguous names is definitely a
# kludge.  It would be better to use the taxonThatContains method to
# refer to a taxon without using an id.

from org.opentreeoflife.smasher import Taxonomy

ott = Taxonomy.getTaxonomy('tax/2.8/')
ncbi = Taxonomy.getTaxonomy('tax/ncbi/')

def do_counts(tax, bac, cil):
	for x in ["Bacteria",
				"Cyanobacteria",
				"Ciliophora",
				"Nematoda",
				"Chlorophyta",
				"Rhodophyceae",
				"Fungi",
				"Insecta",
				"Chordata",
				"Embryophyta"]:
		key = x
		if x == "Bacteria": key = bac
		if x == "Ciliophora": key = cil
		print x, tax.taxon(key).tipCount()

print "OTT 2.8"
do_counts(ott, "844192", "302424")

print "NCBI 11 June 2014"
do_counts(ncbi, "2", "5878")

"""

alias smash='java -classpath .:lib/* -Xmx20g org.opentreeoflife.smasher.Smasher'
smash

>>> do_counts(ott, "844192", "302424")
do_counts(ott, "844192", "302424")
Bacteria 348973
Cyanobacteria 17870
Ciliophora 11209
Nematoda 32856
Chlorophyta 16018
Rhodophyceae 15433
Fungi 348406
Insecta 1105044
Chordata 129530
Embryophyta 315138
>>> do_counts(ncbi, "2", "5878")
do_counts(ncbi, "2", "5878")
Bacteria 335887
Cyanobacteria 11508
Ciliophora 1721
Nematoda 5993
Chlorophyta 5550
Rhodophyceae 5472
Fungi 98072
Insecta 187511
Chordata 55968
Embryophyta 124076
>>> 

"""
