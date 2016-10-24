
from org.opentreeoflife.taxa import Taxonomy, Taxon, Rank

import os, sys, csv

taxpath = sys.argv[1]

ott = Taxonomy.getRawTaxonomy(taxpath, 'ott')

print 'Number of nodes:', ott.count()

syn_count = 0
for taxon in ott.taxa():
    syn_count += len(taxon.synonyms)
print 'Number of synonyms:', syn_count

print 'Number of tips:', ott.tipCount()

#ott.elideContainers()
#ott.inferFlags()

binomials = 0
suppressed = 0
extinct = 0
for taxon in ott.taxa():
    if (Taxon.isBinomial(taxon.name) and
        (taxon.rank == Rank.NO_RANK or taxon.rank.level >= Rank.SPECIES_RANK.level)):
        binomials += 1
    if taxon.isHidden(): suppressed += 1
    if taxon.isExtinct(): extinct += 1
print 'Number of binomials:', binomials
print 'Number taxa suppressed / not: %s / %s' % (suppressed, ott.count() - suppressed)
print 'Number extinct: %s' % extinct

print 'Polysemies:'
names = {}
for taxon in ott.taxa():
    if taxon.name != None:
        count = names.get(taxon.name, 0)
        names[taxon.name] = count + 1

hist = [0] * 1000
for name in names:
    hist[names[name]] += 1

for i in range(2, len(hist)):
    if hist[i] != 0:
        print '%s-way: %s' % (i, hist[i])
