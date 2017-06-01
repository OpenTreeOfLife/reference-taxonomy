# How many species in GBIF?
# grep species ../../feed/gbif/in/taxon.txt | grep -v synonym | wc

# To test:
# ../../bin/jython taxonomy_metrics.py ../../t/tax/aster/ ../../tax/skel/ tmp_test_metrics.json tmp_test_contributions.csv

import os, sys, csv, json

from org.opentreeoflife.taxa import Taxonomy, Taxon, Rank
from org.opentreeoflife.conflict import ConflictAnalysis, Disposition
from org.opentreeoflife.smasher import AlignmentByName

# outpath is for the general stats JSON, and comes from the command line

def doit(ott, sep, outpath, conpath):

    do_rug = False  #os.path.isdir('out/ruggiero')

    if do_rug:
        rug = Taxonomy.getRawTaxonomy('out/ruggiero/', 'rug')
        # Prepare for conflict analysis
        # oh no, we really need a separation taxonomy to do that.
        rug_alignment = AlignmentByName(rug, ott)
        rug_alignment.align()
        rug_conflict = ConflictAnalysis(rug, ott, rug_alignment, True)

    overall_table(ott, outpath)
    source_breakdown_table(ott, conpath)


# Taxomachine suppresses NOT_OTU, ENVIRONMENTAL[_INHERITED], 
# VIRAL, HIDDEN[INHERITED], and WAS_CONTAINER

exclude_from_analysis = (Taxonomy.FORMER_CONTAINER |
                        Taxonomy.NOT_OTU)  

def overall_table(ott, outpath):

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
    widest = None
    names = {}    # Number of nodes having a given name as primary name.
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

        if widest == None or len(taxon.getChildren()) > len(widest.getChildren()):
            widest = taxon

    # Homonyms
    # Classify them somehow?

    poly = 0
    poly_species = 0
    poly_genera = 0
    poly_list = []
    for name in names:
        semies = names[name]    # How many nodes with this name as primary?
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


    report = {}
    report['node_count'] = ott.count()
    report['absorbed'] = excluded
    report['synonym_count'] = syn_count
    report['internal_node_count'] = internals
    report['tip_count'] = tip_count
    report['species'] = species
    report['binomials'] = binomials
    report['homonym_count'] = poly
    report['species_homonym_count'] = poly_species
    report['genus_homonym_count'] = poly_genera
    report['max_depth'] = (max_depth(ott.forest) - 1)
    report['max_children'] = len(widest.getChildren())

    report['incertae_sedis_count'] = incertae
    report['extinct_count'] = extinct
    report['infraspecific_count'] = infra
    report['barren'] = barren

    print 'Writing', outpath
    with open(outpath, 'w') as outfile:
        json.dump(report, outfile, indent=2)

def dashify(x): return x if x != 0 else '-'

def fix_prefix(qid):
    prefix = qid.prefix
    if prefix.startswith('http'): prefix = 'curated'
    elif prefix.startswith('additions'): prefix = 'curated'
    return prefix


def source_breakdown_table(ott, conpath):

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

            if sep.lookupId(taxon.id) != None:
                prefix = 'separation'
            else:
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


    source_order = {'separation': -1,
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

    total_first = 0
    total_merged = 0
    total_inconsistent = 0
    total_aligned = 0
    total_total = 0
    table = []
    table.append(['source', 'total', 'copied', 'aligned', 'absorbed', 'conflict'])
    for source in sources:
        con = contributed.get(source, 0)
        al = aligned.get(source, 0)
        mer = merged.get(source, 0)
        inc = inconsistent.get(source, 0)
        tot = con + al + mer + inc
        table.append([source, tot, con, al, mer, inc])
        total_first += con
        total_aligned += al
        total_merged += mer
        total_inconsistent += inc
        total_total += tot
    table.append(['total', total_total, total_first, total_aligned, dashify(total_merged), dashify(total_inconsistent)])

    print 'Writing', conpath
    with open(conpath, 'w') as outfile:
        dump_table_as_csv(table, outfile)



# Unused

def show_contributions():

    # Show table in human-readable form
    print
    print '```'
    format_string = '%10s %9s %9s %9s %9s %9s'
    for row in table:
        print format_string % tuple(row)
    print '```'
    print """
Key:
    * source = name of source taxonomy
    * total = total number of nodes in source
    * copied = total number of nodes originating from this source (copied)
    * aligned = number of source nodes aligned and copied
    * absorbed = number of source nodes absorbed (not copied)
    * conflict = number of inconsistent source nodes (not copied)
    """

def dump_table_as_csv(table, outfile):
    # Provide CSV form for Pensoft
    writer = csv.writer(outfile)
    for row in table:
        writer.writerow(row)

def max_depth(node):
    m = 0
    for child in node.getChildren():
        d = max_depth(child) + 1
        if d > m: m = d
    return m

if __name__ == '__main__':

    taxpath = sys.argv[1]
    seppath = sys.argv[2]
    outpath = sys.argv[3]  # general report, JSON
    conpath = sys.argv[4]  # contributions, CSV
    sep = Taxonomy.getRawTaxonomy(seppath, 'ott')
    ott = Taxonomy.getRawTaxonomy(taxpath, 'ott')
    ott.inferFlags()

    doit(ott, sep, outpath, conpath)
