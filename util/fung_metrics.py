from org.opentreeoflife.taxa import Taxonomy

"""
Usage:

from org.opentreeoflife.taxa import Taxonomy
ott = Taxonomy.getTaxonomy('tax/ott2.8/')
import fung_metrics
fung = ott.select('Fungi')
fung.analyze()
fung_metrics.doit(fung)
"""

def doit(fung):
    internal = 0
    tips = 0
    species = 0
    fungorum = 0
    ncbi = 0
    gbif = 0
    other = 0
    extinct = 0
    hidden = 0
    for node in fung:
        if node.children == None:
            tips += 1
        else:
            internal += 1
        if node.rank == 'species':
            species += 1
            if node.sourceIds != None:
                source = node.sourceIds[0].getPrefix()
                if source == "if":
                    fungorum += 1
                elif source == "ncbi":
                    ncbi += 1
                elif source == "gbif":
                    gbif += 1
                else:
                    other += 1
            if node.isHidden():
                hidden += 1
            elif node.isExtinct():
                extinct += 1

    print "Internal nodes: %s\nTips: %s\nSpecies: %s" % (internal, tips, species)
    print " (The following counts are for species only)"
    print "From IF: %s\nFrom NCBI but not IF: %s\nFrom GBIF but not NCBI or IF: %s\nFrom elsewhere: %s" % (fungorum, ncbi, gbif, other)
    print "Incertae sedis and similar: %s\nExtinct and not incertae sedis: %s" % (hidden, extinct)
