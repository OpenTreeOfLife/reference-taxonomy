
from org.opentreeoflife.taxa import Taxonomy, Taxon, Rank
from org.opentreeoflife.conflict import ConflictAnalysis, Disposition
from org.opentreeoflife.smasher import AlignmentByName

import os, sys, csv

taxpath = sys.argv[1]

ott = Taxonomy.getRawTaxonomy(taxpath, 'ott')
ott.inferFlags()

# Taxomachine suppresses NOT_OTU, ENVIRONMENTAL[_INHERITED], 
# VIRAL, HIDDEN[INHERITED], and WAS_CONTAINER

exclude_from_analysis = (Taxonomy.FORMER_CONTAINER |
                        Taxonomy.NOT_OTU)  

excluded = 0
syn_count = 0
tip_count = 0
internals = 0
binomials = 0
species = 0
suppressed = 0
extinct = 0
incertae = 0
unplaced = 0
infra = 0
barren = 0
merged = 0
names = {}
for taxon in ott.taxa():
    all_flags = taxon.properFlags | taxon.inferredFlags
    if (all_flags & exclude_from_analysis) != 0:
        excluded += 1
        continue
    syn_count += len(taxon.synonyms)
    if taxon.hasChildren():
        internals += 1
    else:
        tip_count += 1

    if (Taxon.isBinomial(taxon.name) and
        (taxon.rank == Rank.NO_RANK or taxon.rank.level >= Rank.SPECIES_RANK.level)):
        binomials += 1
    if taxon.isHidden(): suppressed += 1
    if taxon.isExtinct(): extinct += 1
    if taxon.properFlags & Taxonomy.INCERTAE_SEDIS_ANY != 0:
        incertae += 1
        if taxon.properFlags & Taxonomy.UNPLACED != 0:
            unplaced += 1
    if all_flags & Taxonomy.INFRASPECIFIC != 0:
        infra += 1
    if all_flags & Taxonomy.BARREN != 0:
        barren += 1

    if taxon.rank == Rank.SPECIES_RANK:
        species += 1

    if taxon.name != None:
        names[taxon.name] = names.get(taxon.name, 0) + 1

rug = Taxonomy.getRawTaxonomy('ruggiero/ruggiero/', 'rug')
# Prepare for conflict analysis
# oh no, we really need a skeleton.
rug_alignment = AlignmentByName(rug, ott)
rug_alignment.align()
rug_conflict = ConflictAnalysis(rug, ott, rug_alignment, True)

# Polysemies
# Classify them somehow?

poly = 0
poly_species = 0
poly_genera = 0
poly_list = []
for name in names:
    semies = names[name]
    if semies > 1:
        poly += 1
        speciesp = False
        genusp = False
        for node in ott.lookup(name):
            if node.taxon() == node:
                all_flags = node.properFlags | node.inferredFlags
                if (all_flags & exclude_from_analysis) != 0:
                    continue
                if node.rank == Rank.SPECIES_RANK:
                    speciesp = True
                elif node.rank == Rank.GENUS_RANK:
                    genusp = True
        if speciesp: poly_species += 1
        if genusp: poly_genera += 1
        # get taxa = ott.lookup(name), get mrca, look at size of mrca
        if semies > 4:
            poly_list.append((semies, name))


# Report

print
print '(Excluding %s non-taxa from analysis)' % excluded
print
print 'General metrics on OTT:'
print ' * Number of taxon records:', ott.count()-excluded
print ' * Number of synonym records:', syn_count
print ' * Number of internal nodes:', internals
print ' * Number of tips:', tip_count
print " * Number of records with rank 'species':", species
print ' * Number of taxa with binomial name-strings:', binomials
print ' * Number of polysemous name-strings: %s  ' % poly
print '     of which species %s, genera %s' % (poly_species, poly_genera)

print
print 'Annotations:'
print " * Number of taxa marked incertae sedis or equivalent: %s  " % incertae
print '     of which leftover children of inconsistent source taxa: %s' % unplaced
print ' * Number of extinct taxa: %s' % extinct
# hidden at curator request ?
print ' * Number of infraspecific taxa (below the rank of species): %s' % infra
print ' * Number of species-less higher taxa (rank above species but containing no species): %s' % barren

def fix_prefix(qid):
    prefix = qid.prefix
    if prefix.startswith('http'): prefix = 'curated'
    elif prefix.startswith('additions'): prefix = 'curated'
    return prefix

contributed = {}
aligned = {}
merged = {}
inconsistent = {}
for taxon in ott.taxa():

    # Note as 'aligned' all but first qid
    if taxon.sourceIds != None:
        firstp = True
        for qid in taxon.sourceIds:
            if firstp == True:
                firstp = False
            else:
                prefix = fix_prefix(qid)
                aligned[prefix] = aligned.get(prefix, 0) + 1

        prefix = fix_prefix(taxon.sourceIds[0])
    else:
        prefix = 'curated'

    all_flags = taxon.properFlags | taxon.inferredFlags
    if (all_flags & exclude_from_analysis) != 0:
        if (taxon.properFlags & Taxonomy.MERGED) != 0:
            merged[prefix] = merged.get(prefix, 0) + 1
        elif (taxon.properFlags & Taxonomy.INCONSISTENT) != 0:
            inconsistent[prefix] = inconsistent.get(prefix, 0) + 1
    else:
        contributed[prefix] = contributed.get(prefix, 0) + 1

source_order = {'skeleton': -1,
                'silva': 0,
                'h2007': 1,
                'if': 2,
                'worms': 3,
                'study713': 4,
                'ncbi': 5,
                'gbif': 6,
                'irmng': 7,
                'curated': 8}
sources = sorted(contributed.keys(), key=lambda(src): source_order.get(src, 99))

print
print 'Assembly:'
print ' * Contributions from various sources'

def dashify(x): return x if x != 0 else '-'

total_first = 0
total_merged = 0
total_inconsistent = 0
total_aligned = 0
format_string = '%13s %9s %9s %9s %9s'
print format_string % ('Source', 'Contrib', 'Aligned', 'Merged', 'Conflict')
for source in sources:
    con = contributed.get(source, 0)
    al = aligned.get(source, 0)
    mer = merged.get(source, 0)
    inc = inconsistent.get(source, 0)
    print format_string % (source, con, al, dashify(mer), dashify(inc))
    total_first += con
    total_aligned += al
    total_merged += mer
    total_inconsistent += inc
print format_string % ('total', total_first, total_aligned, dashify(total_merged), dashify(total_inconsistent))


def max_depth(node):
    m = 0
    for child in node.getChildren():
        d = max_depth(child) + 1
        if d > m: m = d
    return m

print
print 'Topology:'
print ' * Maximum depth of any node in the tree: %s' % (max_depth(ott.forest) - 1)
print ' * Branching factor: average %.2f children per internal node' % ((ott.count() - 1.0) / internals)

print
print 'Comparison with Ruggiero et al. 2015 (goes to characterizing backbone):'
print ' * Number of taxa in Ruggiero: %s  of which orders/tips: %s' % (rug.count(), rug.tipCount())

order_match = 0
supported_by = 0
resolves = 0
partial_path_of = 0
conflicts_with = 0
other = 0
higher = 0
for taxon in rug.taxa():
    if taxon.isRoot(): continue
    if taxon.hasChildren():
        art = rug_conflict.articulation(taxon)
        if art != None:
            higher += 1
            d = art.disposition
            if d == Disposition.SUPPORTED_BY:
                supported_by += 1
            elif d == Disposition.RESOLVES:
                resolves += 1
            elif d == Disposition.PATH_SUPPORTED_BY:
                partial_path_of += 1
            elif d == Disposition.CONFLICTS_WITH:
                conflicts_with += 1
        else:
            other += 1
    elif rug_alignment.getTaxon(taxon) != None:
        order_match += 1

print ' * Ruggiero orders aligned by name to OTT: %s' % order_match
print ' * Disposition of Ruggiero taxa above rank of order:'
print '     * Taxon contains at least one order aligned by name to OTT: %s' % higher
print '     * Full topological consistency between Ruggiero and OTT: %s' % supported_by
print '     * Taxon resolves an OTT polytomy: %s' % resolves
print '     * Taxon supports more than one OTT taxon: %s' % partial_path_of
print '     * Taxon conflicts with one or more OTT taxa: %s' % conflicts_with
print '     * Taxon containing no aligned order: %s' % other

print 'For possible discussion:'
print ' * Number of taxa suppressed for supertree synthesis purposes: %s' % suppressed

print
print 'Appendix: Some extreme polysemies.'

for (semies, name) in sorted(poly_list, key=lambda(semies, name):semies):
    print ' * %s %s' % (semies, name)
