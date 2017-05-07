# Jython script to build the Open Tree reference taxonomy
# coding=utf-8

# Unless specified otherwise issues are in the reference-taxonomy repo:
# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/...

import sys, os, csv

from org.opentreeoflife.taxa import Taxonomy, SourceTaxonomy, TsvEdits, Addition, Taxon
from org.opentreeoflife.smasher import UnionTaxonomy

# for id list
from org.opentreeoflife.taxa import CSVReader, QualifiedId
from java.io import FileReader

import ncbi_ott_assignments
sys.path.append("feed/misc/")
import adjustments, amendments
import check_inclusions
from claim import *
#import reason_report

this_source = 'https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/make-ott.py'
inclusions_path = 'inclusions.csv'
additions_clone_path = 'feed/amendments/amendments-1'
new_taxa_path = 'new_taxa'

def create_ott(version):

    ott = UnionTaxonomy.newTaxonomy('ott')
    ott.version = version;

    # Would be nice if there were tests for all of these...
    for name in names_of_interest:
        ott.eventLogger.namesOfInterest.add(name)

    ott.setSkeleton(Taxonomy.getTaxonomy('tax/separation/', 'separation'))

    # These are particularly hard cases; create alignment targets up front
    adjustments.deal_with_polysemies(ott)

    # Align and merge each source in sequence
    merge_sources(ott)

    # consider try: ... except: print '**** Exception in patch_ott'
    amendments.patch_ott(ott)

    # Remove all trees but the largest (or make them life incertae sedis)
    ott.deforestate()

    # End of topology changes.  Now assign ids.
    ids_and_additions(ott)

    # data structure integrity checks
    ott.check()

    # For deprecated id report (dump)
    ott.loadPreferredIds('ids_that_are_otus.tsv', False)
    ott.loadPreferredIds('ids_in_synthesis.tsv', True)

    return ott

def merge_sources(ott):

    # SILVA
    silva = adjustments.load_silva()
    silva_to_ott = adjustments.align_silva(silva, ott)
    align_and_merge(silva_to_ott)

    # Hibbett 2007
    h2007 = adjustments.load_h2007()
    h2007_to_ott = ott.alignment(h2007)
    align_and_merge(h2007_to_ott)

    # Index Fungorum
    fungorum = adjustments.load_fung()
    (fungi, fungorum_sans_fungi) = split_taxonomy(fungorum, 'Fungi')
    align_and_merge(adjustments.align_fungi(fungi, ott))

    # the non-Fungi from Index Fungorum get absorbed below

    lamiales = adjustments.load_lamiales()
    align_and_merge(adjustments.align_lamiales(lamiales, ott))

    # WoRMS
    # higher priority to Worms for Malacostraca, Cnidaria, Mollusca
    #  so we split out
    # those clades from worms and absorb them before NCBI
    worms = adjustments.load_worms()
    # Malacostraca instead of Decapoda because M. is in the separation taxonomy
    (malacostraca, worms_sans_malacostraca) = split_taxonomy(worms, 'Malacostraca')
    align_and_merge(ott.alignment(malacostraca))
    (cnidaria, worms_sans_cnidaria) = split_taxonomy(worms_sans_malacostraca, 'Cnidaria')
    align_and_merge(ott.alignment(cnidaria))
    (mollusca, low_priority_worms) = split_taxonomy(worms_sans_cnidaria, 'Mollusca')
    align_and_merge(ott.alignment(mollusca))

    # NCBI
    ncbi = adjustments.load_ncbi()

    # analyzeOTUs sets flags on questionable taxa (hybrid, metagenomes,
    #  etc) to allow the option of suppression downstream
    ncbi.analyzeOTUs()

    ncbi_to_ott = adjustments.align_ncbi(ncbi, silva, ott)
    align_and_merge(ncbi_to_ott)

    # Reporting
    # Get mapping from NCBI to OTT, derived via SILVA and Genbank.
    mappings = load_ncbi_to_silva(ncbi, silva, silva_to_ott)
    compare_ncbi_to_silva(mappings, silva_to_ott)

    debug_divisions('Reticularia splendens', ncbi, ott)

    # Low-priority WoRMS
    # This is suboptimal, but the names are confusing the division logic
    a = adjustments.align_worms(low_priority_worms, ott)
    align_and_merge(a)

    # The rest of Index Fungorum.  (Maybe not a good idea to use this.
    # These taxa are all in GBIF.)
    # align_and_merge(adjustments.align_fungorum_sans_fungi(fungorum_sans_fungi, ott))

    # GBIF
    gbif = adjustments.load_gbif()
    gbif_to_ott = adjustments.align_gbif(gbif, ott)
    align_and_merge(gbif_to_ott)

    # http://dx.doi.org/10.1016/j.ympev.2004.12.019 "Eccrinales
    # (Trichomycetes) are not fungi, but a clade of protists at the
    # early divergence of animals and fungi"
    debug_divisions('Enterobryus cingaloboli', gbif, ott)

    # Cylindrocarpon is now Neonectria
    cyl = gbif_to_ott.image(gbif.taxon('Cylindrocarpon', 'Ascomycota'))
    if cyl != None:
        cyl.setId('51754')

    # IRMNG
    irmng = adjustments.load_irmng()
    a = adjustments.align_irmng(irmng, ott)
    hide_irmng(irmng)
    align_and_merge(a)

    # Misc fixups
    adjustments.link_to_h2007(ott)
    report_on_h2007(h2007, h2007_to_ott)

    get_default_extinct_info_from_gbif(gbif, gbif_to_ott)

# utilities

def debug_divisions(name, ncbi, ott):
    print '##', name
    n = ncbi.taxon(name)
    if n != None:
        n.show()
        o = ott.taxon(name)
        if o != None:
            o.show()
            while o != None:
                print o, o.getDivision()
                o = o.parent
    print '##'

# Splits a taxonomy into two parts: 1. the subtree rooted at taxon_name
# and 2. everything else
def split_taxonomy(taxy, taxon_name):
    # get the taxon with name=taxon_name from the taxonomy
    t = taxy.taxon(taxon_name)
    # get the subtree rooted at this taxon
    subtree = taxy.select(t)
    # remove all of the descendants of this taxon
    t.trim()
    taxy_sans_subtree = taxy
    return (subtree, taxy_sans_subtree)


# Maps taxon in NCBI taxonomy to SILVA-derived OTT taxon

def load_ncbi_to_silva(ncbi, silva, silva_to_ott):
    mappings = {}
    flush = []
    with open('feed/silva/out/ncbi_to_silva.tsv', 'r') as infile:
        reader = csv.reader(infile, delimiter='\t')
        for (ncbi_id, silva_cluster_id) in reader:
            n = ncbi.maybeTaxon(ncbi_id)
            if n != None:
                s = silva.maybeTaxon(silva_cluster_id)
                if s != None:
                    so = silva_to_ott.image(s)
                    if so != None:
                        if n in mappings:
                            # 213 of these
                            # print '** NCBI id maps to multiple SILVA clusters', n
                            mappings[n] = True
                            flush.append(n)
                        else:
                            mappings[n] = so
                    else:
                        print '** no OTT taxon for cluster', silva_cluster_id
                else:
                    # Too many of these now, and not sure what to do about them.
                    # print '| no cluster %s for %s' % (silva_cluster_id, n)
                    True
    for n in flush:
        if n in mappings:
            del mappings[n]
    return mappings

# Report on differences between how NCBI and OTT map to SILVA
# 2016-11-03 This is a disturbingly large number: 67254

def compare_ncbi_to_silva(mappings, silva_to_ott):
    problems = 0
    for taxon in mappings:
        t1 = mappings[taxon]
        t2 = silva_to_ott.image(taxon)
        if t1 != t2:
            problems += 1
            if t2 != None and t1.name == t2.name:
                div = t1.divergence(t2)
                if div != None:
                    print '| %s -> (%s, %s) coalescing at (%s, %s)' % \
                        (taxon, t1, t2, div[0], div[1])
    print '* %s NCBI taxa map differently by cluster vs. by name' % problems


# The processed GBIF taxonomy contains a file listing GBIF taxon ids for all
# taxa that are listed as coming from PaleoDB.  This is processed after all
# taxonomies are processed but before patches are applied.  We use it to set
# extinct flags for taxa originating only from GBIF (i.e. if the taxon also
# comes from NCBI, WoRMS, etc. then we do not mark it as extinct).

def get_default_extinct_info_from_gbif(gbif, gbif_to_ott):
    infile = open('tax/gbif/paleo.tsv')
    paleos = 0
    flagged = 0
    for row in infile:
        paleos += 1
        id = row.strip()
        gtaxon = gbif.lookupId(id)
        if gtaxon != None:
            taxon = gbif_to_ott.image(gtaxon)
            if taxon != None:
                prefix = taxon.sourceIds[0].prefix
                if prefix == 'gbif':
                    # See https://github.com/OpenTreeOfLife/feedback/issues/43
                    # It's OK if it's also in IRMNG
                    flagged += 1
                    taxon.extinct()
                else:
                    print "| PaleoDB taxon %s may be extant; it's in %s" % (taxon, prefix)
    infile.close()
    print '| Flagged %s of %s taxa from paleodb\n' % (flagged, paleos)


def hide_irmng(irmng):
    # Sigh...
    # https://github.com/OpenTreeOfLife/feedback/issues/302
    for root in irmng.roots():
        root.hide()

    # 2016-11-06 Laura Katz personal email to JAR:
    # "IRMNG great for microbial diversity, for example"
    irmng.taxon('Protista').unhide()

    with open('irmng_only_otus.csv', 'r') as infile:
        reader = csv.reader(infile)
        reader.next()           # header row
        for row in reader:
            if irmng.lookupId(row[0]) is not None:
                irmng.lookupId(row[0]).unhide()

# -----------------------------------------------------------------------------
# OTT id assignment

def ids_and_additions(ott):

    # ad hoc assignments specifically for NCBI taxa, basedon NCBI id

    for (ncbi_id, ott_id, name) in ncbi_ott_assignments.ncbi_assignments_list:
        im = ott.lookupQid(QualifiedId('ncbi', ncbi_id))
        if im == None:
            print '* ncbi:%s not found in OTT - %s' % (ncbi_id, name)
        else:
            if im.name != name:
                print '** ncbi:%s name is %s, but expected %s' % (ncbi_id, im.name, name)
            im.addId(ott_id)

    # Force some id assignments... will try to automate this in the future.
    # Most of these come from looking at the deprecated.tsv file after a
    # series of smasher runs.

    for (inf, sup, id) in [
            ('Tipuloidea', 'Diptera', '722875'),
            ('Saccharomycetes', 'Saccharomycotina', '989999'),
            ('Phaeosphaeria', 'Ascomycota', '5486272'),
            ('Synedra acus','Eukaryota','992764'),
            ('Hessea','Archaeplastida','600099'),
            ('Morganella','Arthropoda','6400'),
            ('Rhynchonelloidea','Rhynchonellidae','5316010'),
            ('Morganella', 'Fungi', '973932'),
            ('Parmeliaceae', 'Lecanorales', '305904'),
            ('Cordana', 'Ascomycota', '946160'),
            ('Pseudofusarium', 'Ascomycota', '655794'),
            ('Marssonina', 'Dermateaceae', '372158'), # ncbi:324777
            ('Marssonia', 'Lamiales', '5512668'), # gbif:7268388
            # ('Gloeosporium', 'Pezizomycotina', '75019'),  # synonym for Marssonina
            ('Escherichia coli', 'Enterobacteriaceae', '474506'), # ncbi:562
            # ('Dischloridium', 'Trichocomaceae', '895423'),
            ('Exaiptasia pallida', 'Cnidaria', '135923'),
            ('Choanoflagellida', 'Holozoa', '202765'),
            ('Billardiera', 'Lamiales', '798963'),
            ('Trachelomonas grandis', 'Bacteria', '58035'), # study ot_91 Tr46259
            ('Hypomyzostoma', 'Myzostomida', '552744'),   # was incorrectly in Annelida
            ('Gyromitus', 'SAR', '696946'),
            ('Pseudogymnoascus destructans', 'Pezizomycotina', '428163'),
            # ('Amycolicicoccus subflavus', 'Mycobacteriaceae', '541768'),  # ncbi:639313
            # ('Pohlia', 'Foraminifera', '5325989')  - NO
            ('Pohlia', 'Amphibia', '5325989'),  # irmng:1311321
            ('Phyllanthus', 'Pentapetalae', '452944'),  # pg_25 @josephwb = 5509975
    ]:
        tax = ott.maybeTaxon(inf, sup)
        if tax != None:
            tax.setId(id)

    ott.taxon('452944').addId('5509975')

    # ott.taxon('474506') ...

    ott.taxonThatContains('Rhynchonelloidea', 'Sphenarina').setId('795939') # NCBI

    # Trichosporon is a mess, because it occurs 3 times in NCBI.
    trich = ott.taxonThatContains('Trichosporon', 'Trichosporon cutaneum')
    if trich != None:
        trich.setId('364222')

    #ott.image(fungi.taxon('11060')).setId('4107132') #Cryptococcus - a total mess

    # --------------------
    # Assign OTT ids to taxa that don't have them, re-using old ids when possible
    ids = Taxonomy.getRawTaxonomy('tax/prev_ott/', 'ott')

    # Edit the id source taxonomy to optimize id coverage

    # Kludge to undo lossage in OTT 2.9
    for taxon in ids.taxa():
        if (len(taxon.sourceIds) >= 2 and
            taxon.sourceIds[0].prefix == "ncbi" and
            taxon.sourceIds[1].prefix == "silva"):
            taxon.sourceIds.remove(taxon.sourceIds[0])

    # OTT 2.9 has both Glaucophyta and Glaucophyceae...
    # this creates an ambiguity when aligning.
    # Need to review this; maybe they *should* be separate taxa.
    g1 = ids.maybeTaxon('Glaucophyta')
    g2 = ids.maybeTaxon('Glaucophyceae')
    if g1 != None and g2 != None and g1 != g2:
        g1.absorb(g2)

    # Assign old ids to nodes in the new version
    ott.carryOverIds(ids) # Align & copy ids

    # Apply the additions (which already have ids assigned)
    print '-- Processing additions --'
    Addition.processAdditions(additions_clone_path, ott)

    print '-- Checking id list'
    assign_ids_from_list(ott, 'feed/ott_id_list/by_qid.csv')

    # Mint ids for new nodes
    ott.assignNewIds(new_taxa_path)

# Use master OTT id list to assign some ids

def assign_ids_from_list(tax, filename):
    count = 0
    change_count = 0
    infile = FileReader(filename)
    r = CSVReader(infile)
    while True:
        row = r.readNext()
        if row == None: break
        [qid_string, ids] = row
        tracep = (qid_string == 'ncbi:33543' or qid_string == 'gbif:2433391'
                  or qid_string == 'gbif:2467506' or qid_string == 'ncbi:28376')
        if tracep:
            print '# Tracing %s %s' % (qid_string, ids)
        qid = QualifiedId(qid_string)
        taxon = tax.lookupQid(qid)
        if taxon != None:
            id_list = ids.split(';')
            qid_id = id_list[0]
            if tracep == False:
                tracep = (qid_id == '565578' or qid_id == '5541322')

            if tracep:
                print '# qid %s, id_list %s' % (qid, id_list)

            # Look for collision
            tenant = tax.lookupId(qid_id)
            if tenant != None:
                # qid_id is unavailable
                # Happens 7700 or so times; most cases ought to be homonymized,
                # but not all
                if tracep:
                    print '# %s (for %s) is in use by %s' % (qid_id, taxon, tenant)
                False

            # Qid from list is one of the taxon's qids.

            # Use the proposed id if the qid's node has no id
            elif taxon.id == None:
                # Happens 87854 for OTT 3.0
                if tracep: print '# Setting %s as id of %s' % (qid_id, taxon)
                taxon.setId(qid_id)
                # Deal with aliases
                for id in id_list[1:]:
                    if tax.lookupId(id) == None:
                        if tracep: print '# adding %s as id for %s' % (id, taxon)
                        tax.addId(taxon, id)
                    else:
                        if tracep:
                            print '# alias %s (for %s) is in use by %s' % (id, taxon, tax.lookupId(id))
                count += 1

            # If it has an id, but qid is not the primary qid, skip it
            elif taxon.sourceIds[0] != qid:
                if tracep: print '# %s is minor for %s' % (qid_id, taxon)
                False

            # Use the id in the id_list if it's smaller than the one in taxon
            elif int(qid_id) < int(taxon.id):
                if tracep: print '# %s is replacing %s as the id of %s' % (qid_id, taxon.id, taxon)
                taxon.setId(qid_id)
                for id in id_list[1:]:
                    if tax.lookupId(id) == None:
                        tax.addId(taxon, id)
                change_count += 1

            else:
                if tracep: print '# %s has id %s < %s' % (qid, qid_id, taxon)
        else:
            if tracep: print '# no taxon with qid %s; ids %s' % (qid, ids)


    infile.close()
    print '| Assigned %s ids, changed %s ids from %s' % (count, change_count, filename)

    # Could harvest merges from the id list, as well, and
    # maybe even restore lower-numbered OTT ids.

# -----------------------------------------------------------------------------
# Reports

def align_and_merge(alignment):
    ott = alignment.target
    ott.align(alignment)
    ott.merge(alignment)

def report_on_h2007(h2007, h2007_to_ott):
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/40
    print '-- Checking realization of h2007'
    for taxon in h2007.taxa():
        im = h2007_to_ott.image(taxon)
        if im != None:
            if im.children == None:
                print '** Barren taxon from h2007', taxon.name
        else:
            print '** Missing taxon from h2007', taxon.name

def report(ott):

    if False:
        # This one is getting too big.  Should write it to a file.
        print '-- Parent/child homonyms'
        ott.parentChildHomonymReport()

    # Requires ../germinator
    print '-- Inclusion tests'
    check_inclusions.check(inclusions_path, ott)
    # tests deleted because taxon no longer present:
    #  Progenitohyus,Cetartiodactyla,3615889,"https://github.com/OpenTreeOfLife/feedback/issues/58"
    #  Protaspis,Opisthokonta,5345086,"not found"
    #  Coscinodiscus,Porifera,5344432,""
    #  Retaria,Opisthokonta,5297815,""
    #  Campanella,Holozoa,5343447,""
    #  Hessea,Holozoa,5295839,""
    #  Neoptera,Tachinidae,5340261,"test of genus"


# -----------------------------------------------------------------------------
# Things to keep an eye on

names_of_interest = ['Ciliophora',
                     'Phaeosphaeria',
                     'Morganella',
                     'Saccharomycetes',

                     # From the deprecated file
                     'Methanococcus maripaludis',
                     'Cyanidioschyzon',
                     'Pseudoalteromonas atlantica',
                     'Pantoea ananatis', # deprecated and gone
                     'Gibberella zeae', # was deprecated

                     # From notSame directives
                     'Acantharia', # in Venturiaceae < Fungi < Opisth. / Rhizaria < SAR
                     'Steinia', # in Lecideaceae < Fungi / Alveolata / insect < Holozoa in irmng
                     'Epiphloea', # in Pezizomycotina < Opisth. / Rhodophyta  should be OK, Rh. is a division
                     'Campanella', # in Agaricomycotina < Nuclet. / SAR / Holozoa  - check IF placement
                     'Lacrymaria', # in Agaricomycotina / ?
                     'Frankia',    # in Pezizomycotina / Bacteria
                     'Phialina',   # in Pezizomycotina
                     'Bogoriella',

                     'Bostrychia',
                     'Buchnera',
                     'Podocystis', # not found
                     'Crepidula',
                     'Hessea',
                     'Choanoflagellida',
                     'Choanozoa',
                     'Retaria',
                     'Labyrinthomorpha',
                     'Ophiurina',
                     'Rhynchonelloidea',
                     'Neoptera',
                     'Tipuloidea',
                     'Tetrasphaera',
                     'Protaspis',
                     'Coscinodiscus',
                     'Photorhabdus luminescens', # samples from deprecated list
                     'Xenorhabdus bovienii',
                     'Gibberella zeae',
                     'Ruwenzorornis johnstoni',
                     'Burkea',

                     'Blattodea',
                     'Eumetazoa',
                     'Bivalvia',
                     'Pelecypoda',
                     'Parmeliaceae',
                     'Heterolepa',
                     'Acanthokara',
                     'Epigrapsus notatus',  # occurs twice in worms, should be merged...
                     'Carduelis barbata',  # 'incompatible-use'
                     'Spinus barbatus',
                     'Abatia',
                     'Jungermanniaceae',
                     'Populus deltoides',
                     'Salicaceae',
                     'Salix sericea',
                     'Streptophytina',
                     'Loxosporales',
                     'Sarrameanales',
                     'Trichoderma',
                     'Hypocrea',
                     'Elaphocordyceps subsessilis', # incompatible-use - ok
                     'Bacillus selenitireducens',   # incompatible-use
                     'Nematostella vectensis',
                     'Aiptasia pallida',  # Cyanobacteria / cnidarian confusion
                     'Mahonia',  # merged
                     'Maddenia', # merged
                     'Crenarchaeota', # silva duplicate
                     'Dermabacter',
                     'Orzeliscidae', # should be 'rejected refinement'
                     'Sogonidae',
                     'Echinochalina',
                     'Callyspongia elegans',
                     'Callyspongia',
                     'Pseudostomum',
                     'Pseudostomidae',
                     'Parvibacter',
                     'Euxinia',
                     'Xiphonectes',
                     'Cylindrocarpon',
                     'Macrophoma',
                     'Tricellulortus peponiformis',
                     'Dischloridium',
                     'Gloeosporium',
                     'Exaiptasia pallida',
                     'Cladochytriaceae',
                     'Hyaloraphidium',
                     'Marssonina',
                     'Marssonia',
                     'Platypus',
                     'Dendrosporium',
                     'Diphylleia',
                     'Myzostomida',
                     'Endomyzostoma tenuispinum',
                     'Myzostoma cirriferum',
                     'Helotiales',
                     'Leotiales',
                     'Desertella',
                     'Cyclophora',
                     'Pohlia',
                     'Lonicera',
                     'Chromista',
                     'Protista',
                     'Protozoa',
                     'Zaglossus bruijni',
                     'Acropora',
                     ]
