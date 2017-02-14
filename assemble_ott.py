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
from chromista_spreadsheet import fixChromista
import adjustments
import check_inclusions
from establish import establish
from claim import *
#import reason_report

this_source = 'https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/make-ott.py'
inclusions_path = 'inclusions.csv'
additions_clone_path = 'feed/amendments/amendments-1'
new_taxa_path = 'new_taxa'

def create_ott():

    ott = UnionTaxonomy.newTaxonomy('ott')

    # Would be nice if there were tests for all of these...
    for name in names_of_interest:
        ott.eventLogger.namesOfInterest.add(name)

    # idspace string 'skel' is magical, see Taxon.addSource
    ott.setSkeleton(Taxonomy.getTaxonomy('tax/skel/', 'skel'))

    # These are particularly hard cases; create alignment targets up front
    adjustments.deal_with_polysemies(ott)

    merge_sources(ott)

    # consider try: ... except: print '**** Exception in patch_ott'
    patch_ott(ott)

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
    # higher priority to Worms for Malacostraca, Cnidaria so we split out
    # those clades from worms and absorb them before NCBI
    worms = adjustments.load_worms()
    # Malacostraca instead of Decapoda because M. is in the skeleton
    (malacostraca, worms_sans_malacostraca) = split_taxonomy(worms, 'Malacostraca')
    align_and_merge(ott.alignment(malacostraca))
    (cnidaria, low_priority_worms) = split_taxonomy(worms_sans_malacostraca, 'Cnidaria')
    align_and_merge(ott.alignment(cnidaria))

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
    low_priority_worms.taxon('Glaucophyta'). \
        absorb(low_priority_worms.taxon('Glaucophyceae'))
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
                if taxon.sourceIds[0].prefix == 'gbif':
                    # See https://github.com/OpenTreeOfLife/feedback/issues/43
                    # It's OK if it's also in IRMNG
                    flagged += 1
                    taxon.extinct()
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

# ----- Final patches -----

def patch_ott(ott):

    # troublemakers.  we don't use them
    print '| Flushing %s viruses' % ott.taxon('Viruses').count()
    ott.taxon('Viruses').prune()

    # Romina 2014-04-09: Hypocrea = Trichoderma.
    # IF and all the other taxonomies have both Hypocrea and Trichoderma.
    # Need to merge them.
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/86
    # Hypocrea rufa is the type species for genus Hypocrea, and it's the same as
    # Trichoderma viride.
    ott.taxon('Hypocrea').absorb(ott.taxonThatContains('Trichoderma', 'Trichoderma viride'))
    ott.taxon('Hypocrea rufa').absorb(ott.taxon('Trichoderma viride'))

    # Romina https://github.com/OpenTreeOfLife/reference-taxonomy/issues/42
    # this seems to have fixed itself
    # ott.taxon('Hypocrea lutea').absorb(ott.taxon('Trichoderma deliquescens'))

    # 2014-01-27 Joseph: Quiscalus is incorrectly in
    # Fringillidae instead of Icteridae.  NCBI is wrong, GBIF is correct.
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/87
    ott.taxon('Icteridae').take(ott.taxon('Quiscalus', 'Fringillidae'))

    # Misspelling in GBIF... seems to already be known
    # Stephen email to JAR 2014-01-26
    # ott.taxon("Torricelliaceae").synonym("Toricelliaceae")


    # Joseph 2014-01-27 https://code.google.com/p/gbif-ecat/issues/detail?id=104
    ott.taxon('Parulidae').take(ott.taxon('Myiothlypis', 'Passeriformes'))
    # I don't get why this one isn't a major_rank_conflict !? - bug. (so to speak.)
    ott.taxon('Blattodea').take(ott.taxon('Phyllodromiidae'))

    # See above (occurs in both IF and GBIF).  Also see issue #67
    chlam = ott.taxonThatContains('Chlamydotomus', 'Chlamydotomus beigelii')
    if chlam != None: chlam.incertaeSedis()

    # Joseph Brown 2014-01-27
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/87
    # http://www.worldbirdnames.org/BOW/antbirds/
    # Occurs as Sakesphorus bernardi in ncbi, gbif, irmng, as Thamnophilus bernardi in bgif
    for epithet in ['bernardi', 'melanonotus', 'melanothorax']:
        sname = 'Sakesphorus ' + epithet
        tname = 'Thamnophilus ' + epithet
        sak = ott.maybeTaxon(sname)
        tham = ott.maybeTaxon(tname)
        if sak != None and tham != None and sak != tham:
            tham.absorb(sak)
        elif sak != None:
            sak.rename(tname)
        elif tham != None:
            tham.synonym(sname)

    # Mammals - groups cluttering basal part of tree
    ott.taxon('Litopterna').extinct()
    ott.taxon('Notoungulata').extinct()
    # Artiodactyls
    ott.taxon('Boreameryx').extinct()
    ott.taxon('Thandaungia').extinct()
    ott.taxon('Limeryx').extinct()
    ott.taxon('Delahomeryx').extinct()
    ott.taxon('Krabitherium').extinct()
    ott.taxon('Discritochoerus').extinct()
    ott.taxon('Brachyhyops').extinct()

    # "No clear identity has emerged"
    #  http://forestis.rsvs.ulaval.ca/REFERENCES_X/phylogeny.arizona.edu/tree/eukaryotes/accessory/parasitic.html
    # Need to hide it because it clutters base of Fungi
    if ott.maybeTaxon('Amylophagus','Fungi') != None:
        ott.taxon('Amylophagus','Fungi').incertaeSedis()

    # Bad synonym - Tony Rees 2014-01-28
    # https://groups.google.com/d/msg/opentreeoflife/SrI7KpPgoPQ/ihooRUSayXkJ
    if ott.maybeTaxon('Lemania pluvialis') != None:
        ott.taxon('Lemania pluvialis').prune("make-ott.py")

    # Tony Rees 2014-01-29
    # https://groups.google.com/d/msg/opentreeoflife/SrI7KpPgoPQ/wTeD17GzOGoJ
    trigo = ott.maybeTaxon('Trigonocarpales')
    if trigo != None: trigo.extinct()

    #Pinophyta and daughters need to be deleted; - Bryan 2014-01-28
    #Lycopsida and daughters need to be deleted;
    #Pteridophyta and daughters need to be deleted;
    #Gymnospermophyta and daughters need to be deleted;
    # These had all disappeared from NCBI by Nov 2016, but the new GBIF
    # has synonymized Pteridophyta with Tracheophyta.
    for name in ['Pinophyta', 'Pteridophyta', 'Gymnospermophyta']:
        taxon = ott.maybeTaxon(name, 'Chloroplastida')
        if taxon != None and ott.maybeTaxon('Rosa', name) == None:
            taxon.incertaeSedis()

    # Patches from the Katz lab to give decent parents to taxa classified
    # as Chromista or Protozoa
    print '-- Chromista/Protozoa spreadsheet from Katz lab --'
    fixChromista(ott)
    # 2016-06-30 deleted from spreadsheet because ambiguous:
    #   Enigma,Protozoa,Polychaeta ,,,,, -
    #   Acantharia,Protozoa,Radiozoa,,,,,
    #   Lituolina,Chromista,Lituolida ,WORMS,,,,


    print '-- more patches --'

    # From Laura and Dail on 5 Feb 2014
    # https://groups.google.com/d/msg/opentreeoflife/a69fdC-N6pY/y9QLqdqACawJ
    tax = ott.maybeTaxon('Chlamydiae/Verrucomicrobia group')
    if tax != None and tax.name != 'Bacteria':
        tax.rename('Verrucomicrobia group')
    # The following is obviated by algorithm changes
    # ott.taxon('Heterolobosea','Discicristata').absorb(ott.taxon('Heterolobosea','Percolozoa'))
    tax = ott.taxonThatContains('Excavata', 'Euglena')
    if tax != None:
        tax.take(ott.taxon('Oxymonadida','Eukaryota'))

    # There is no Reptilia in OTT 2.9, so this can probably be deleted
    if ott.maybeTaxon('Reptilia') != None:
        ott.taxon('Reptilia').hide()

    # Chris Owen patches 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/88
    ott.taxon('Protostomia').take(ott.taxonThatContains('Chaetognatha','Sagittoidea'))
    ott.taxon('Lophotrochozoa').take(ott.taxon('Platyhelminthes'))
    ott.taxon('Lophotrochozoa').take(ott.taxon('Gnathostomulida'))
    ott.taxon('Bilateria').take(ott.taxon('Acoela'))
    ott.taxon('Bilateria').take(ott.taxon('Xenoturbella'))
    ott.taxon('Bilateria').take(ott.taxon('Nemertodermatida'))
    # Myzostomida no longer in Annelida
    # ott.taxon('Polychaeta','Annelida').take(ott.taxon('Myzostomida'))
    # https://dx.doi.org/10.1007/s13127-011-0044-4
    # Not in deuterostomes
    ott.taxon('Bilateria').take(ott.taxon('Xenacoelomorpha'))
    if ott.maybeTaxon('Staurozoa') != None:
        #  8) Stauromedusae should be a class (Staurozoa; Marques and Collins 2004) and should be removed from Scyphozoa
        ott.taxon('Cnidaria').take(ott.taxon('Stauromedusae'))
    ott.taxon('Copepoda').take(ott.taxon('Prionodiaptomus'))

    # Bryan Drew patches 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/89
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/90
    ott.taxon('Scapaniaceae').absorb(ott.taxon('Lophoziaceae'))
    ott.taxon('Salazaria mexicana').rename('Scutellaria mexicana')
    # One Scutellaria is in Lamiaceae; the other is a fungus.
    # IRMNG's Salazaria 1288740 is in Lamiales, and is a synonym of Scutellaria.

    ##### RECOVER THIS SOMEHOW --
    # ott.taxon('Scutellaria','Lamiaceae').absorb(ott.image(gbif.taxon('Salazaria')))
    # IRMNG 1288740 not in newer IRMNG

    if False:
        sal = irmng.maybeTaxon('1288740')
        if sal != None:
            ott.taxon('Scutellaria','Lamiaceae').absorb(ott.image(sal)) #Salazaria

    #  Make an order Boraginales that contains Boraginaceae + Hydrophyllaceae
    #  http://dx.doi.org/10.1111/cla.12061
    # Bryan Drew 2013-09-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/91
    bora1 = ott.taxonThatContains('Boraginaceae', 'Borago officinalis')
    bora1.absorb(ott.taxon('Hydrophyllaceae'))
    bora2 = ott.taxonThatContains('Boraginales', 'Borago officinalis')
    bora2.take(bora1)
    ott.taxon('lamiids').take(bora2)

    # Bryan Drew 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/90
    # Vahlia 26024 <- Vahliaceae 23372 <- lammids 596112 (was incertae sedis)
    ott.taxon('lamiids').take(ott.taxon('Vahliaceae'))

    # Bryan Drew 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/90
    # http://www.sciencedirect.com/science/article/pii/S0034666703000927
    # write this ?? ott.assert(as_said_by('https://github.com/OpenTreeOfLife/reference-taxonomy/issues/90', extinct('Araripia')))
    ara = ott.taxon('Araripia')
    if ara != None: ara.extinct()

    # Bryan Drew  2014-02-05
    # http://www.mobot.org/mobot/research/apweb/
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/92
    ott.taxon('Viscaceae').rename('Visceae')
    ott.taxon('Amphorogynaceae').rename('Amphorogyneae')
    ott.taxon('Thesiaceae').rename('Thesieae')
    sant = ott.taxonThatContains('Santalaceae', 'Santalum insulare')
    sant.take(ott.taxon('Visceae'))
    sant.take(ott.taxon('Amphorogyneae'))
    sant.take(ott.taxon('Thesieae'))
    sant.absorb(ott.taxon('Cervantesiaceae'))
    sant.absorb(ott.taxon('Comandraceae'))

    # Bryan Drew 2014-01-30
    # http://dx.doi.org/10.1126/science.282.5394.1692
    ott.taxon('Magnoliophyta').take(ott.taxon('Archaefructus'))

    # Bryan Drew 2014-01-30
    # http://deepblue.lib.umich.edu/bitstream/handle/2027.42/48219/ID058.pdf
    ott.taxon('eudicotyledons').take(ott.taxon('Phyllites'))

    # Bryan Drew 2014-02-13
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/93
    # http://dx.doi.org/10.1007/978-3-540-31051-8_2
    ott.taxon('Alseuosmiaceae').take(ott.taxon('Platyspermation'))

    # JAR 2014-02-24.  We are getting extinctness information for genus
    # and above from IRMNG, but not for species.
    # There's a similar problem in Equus.
    for child in ott.taxon('Homo', 'Primates').children:
        if child.name != 'Homo sapiens':
            child.extinct()
    for child in ott.taxon('Homo sapiens', 'Primates').children:
        if child.name != 'Homo sapiens sapiens':
            child.extinct()

    # JAR 2014-03-07 hack to prevent H.s. from being extinct due to all of
    # its subspecies being extinct.
    # I wish I knew what the authority for the H.s.s. name was.
    # (Linnaeus maybe?)
    hss = ott.newTaxon('Homo sapiens sapiens', 'subspecies', 'https://en.wikipedia.org/wiki/Homo_sapiens_sapiens')
    ott.taxon('Homo sapiens').take(hss)
    hss.hide()

    # Raised by Joseph Brown 2014-03-09, solution proposed by JAR
    # Tribolium is incertae sedis in NCBI but we want it to not be hidden,
    # since it's a model organism.
    # Placement in Tenebrioninae is according to http://bugguide.net/node/view/152 .
    # Is this cheating?
    ott.taxon('Tenebrioninae').take(ott.taxon('Tribolium','Coleoptera'))

    # Bryan Drew 2014-03-20 http://dx.doi.org/10.1186/1471-2148-14-23
    # This isn't quite right - we really want to create a new taxon 'eurosides'
    # equal to rosids minus Vitales, and either leave rosids alone or get rid of 
    # it
    ott.taxon('Pentapetalae').take(ott.taxon('Vitales'))

    # Bryan Drew 2014-03-14 http://dx.doi.org/10.1186/1471-2148-14-23
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/24
    ott.taxon('Streptophytina').elide()

    # Dail 2014-03-20
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/29
    # Note misspelling in SILVA
    ott.taxon('Freshwayer Opisthokonta').rename('Freshwater Microbial Opisthokonta')

    # JAR 2014-03-31 just poking around
    # Many of these would be handled by major_rank_conflict if it worked
    for name in [
            'Temnospondyli', # http://tolweb.org/tree?group=Temnospondyli
            'Eobatrachus', # https://en.wikipedia.org/wiki/Eobatrachus
            'Vulcanobatrachus', # https://en.wikipedia.org/wiki/Vulcanobatrachus
            'Beelzebufo', # https://en.wikipedia.org/wiki/Beelzebufo
            'Iridotriton', # https://en.wikipedia.org/wiki/Iridotriton
            'Baurubatrachus', # https://en.wikipedia.org/wiki/Baurubatrachus
            'Acritarcha', # # JAR 2014-04-26

    ]:
        tax = ott.maybeTaxon(name)
        if tax != None: tax.extinct()

    # Dail 2014-03-31 https://github.com/OpenTreeOfLife/feedback/issues/4
    # "The parent [of Lentisphaerae] should be Bacteria and not Verrucomicrobia"
    # no evidence given
    bact = ott.taxonThatContains('Bacteria', 'Lentisphaerae')
    if bact != None:
        bact.take(ott.taxon('Lentisphaerae'))

    # David Hibbett 2014-04-02 misspelling in h2007 file
    # (Dacrymecetales is 'no rank', Dacrymycetes is a class)
    if ott.maybeTaxon('Dacrymecetales') != None:
        ott.taxon('Dacrymecetales').rename('Dacrymycetes')

    # Dail https://github.com/OpenTreeOfLife/feedback/issues/6
    ott.taxon('Telonema').synonym('Teleonema')

    # Joseph https://github.com/OpenTreeOfLife/reference-taxonomy/issues/43
    ott.taxon('Lorisiformes').take(ott.taxon('Lorisidae'))

    # Romina https://github.com/OpenTreeOfLife/reference-taxonomy/issues/42
    # As of 2014-04-23 IF synonymizes Cyphellopsis to Merismodes
    cyph = ott.maybeTaxon('Cyphellopsis','Cyphellaceae')
    if cyph != None:
        cyph.unhide()
        if ott.maybeTaxon('Cyphellopsis','Niaceae') != None:
            cyph.absorb(ott.taxon('Cyphellopsis','Niaceae'))

    ott.taxon('Diaporthaceae').take(ott.taxon('Phomopsis'))
    ott.taxon('Valsaceae').take(ott.taxon('Valsa', 'Fungi'))
    ott.taxon('Agaricaceae').take(ott.taxon('Cystoderma','Fungi'))
    # Invert the synonym relationship
    ott.taxon('Hypocrea lutea').absorb(ott.taxon('Trichoderma deliquescens'))

    # Fold Norops into Anolis
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/31
    # TBD: Change species names from Norops X to Anolis X for all X
    ott.taxon('Anolis').absorb(ott.maybeTaxon('Norops', 'Iguanidae'))

    for (name, super) in [
        # JAR 2014-04-08 - these are in study OTUs - see IRMNG
        ('Inseliellum', None),
        ('Conus', 'Gastropoda'),
        ('Patelloida', None),
        ('Phyllanthus', 'Phyllanthaceae'),
        ('Stelis','Orchidaceae'),
        ('Chloris', 'Poaceae'),
        ('Acropora', 'Acroporidae'),
        ('Diadasia', None),

        # JAR 2014-04-24
        # grep "ncbi:.*extinct_inherited" tax/ott/taxonomy.tsv | head
        ('Tarsius', None),
        ('Odontesthes', None),
        ('Leiognathus', 'Chordata'),
        ('Oscheius', None),
        ('Cicindela', None),
        ('Leucothoe', 'Ericales'),
        ('Hydrornis', None),
        ('Bostrychia harveyi', None), #fungus
        ('Agaricia', None), #coral
        ('Dischidia', None), #eudicot

        # JAR 2014-05-13
        ('Saurischia', None),
        # there are two of these, maybe should be merged.
        # 'Myoxidae', 'Rodentia'),

        # JAR 2014-05-13 These are marked extinct by IRMNG but are all in NCBI
        # and have necleotide sequences
        ('Zemetallina', None),
        ('Nullibrotheas', None),
        ('Fissiphallius', None),
        ('Nullibrotheas', None),
        ('Sinelater', None),
        ('Phanerothecium', None),
        ('Cephalotaxaceae', None),
        ('Vittaria elongata', None),
        ('Neogymnocrinus', None),
    ]:
        if super == None:
            tax = ott.maybeTaxon(name)
        else:
            tax = ott.maybeTaxon(name, super)
        if tax != None: tax.extant()

    # JAR 2014-05-08 while looking at the deprecated ids file.
    # http://www.theplantlist.org/tpl/record/kew-2674785
    ott.taxon('Berendtiella rugosa').synonym('Berendtia rugosa')

    # JAR 2014-05-13 weird problem
    # NCBI incorrectly has both Cycadidae and Cycadophyta as children of Acrogymnospermae.
    # Cycadophyta (class, with daughter Cycadopsida) has no sequences.
    # The net effect is a bunch of extinct IRMNG genera showing up in
    # Cycadophyta, with Cycadophyta entirely extinct.
    #
    # NCBI has subclass Cycadidae =                     order Cycadales
    # GBIF has phylum Cycadophyta = class Cycadopsida = order Cycadales
    # IRMNG has                     class Cycadopsida = order Cycadales
    if ott.maybeTaxon('Cycadidae') != None:
        ott.taxon('Cycadidae').absorb(ott.taxon('Cycadopsida'))
        ott.taxon('Cycadidae').absorb(ott.taxon('Cycadophyta'))

    # Similar problem with Gnetidae and Ginkgoidae

    # Dail 2014-03-31
    # https://github.com/OpenTreeOfLife/feedback/issues/6
    ott.taxon('Telonema').synonym('Teleonema')

    # JAR noticed 2015-02-17  used in pg_2460
    # http://reptile-database.reptarium.cz/species?genus=Parasuta&species=spectabilis
    ott.taxon('Parasuta spectabilis').synonym('Rhinoplocephalus spectabilis')

    # Bryan Drew 2015-02-17 http://dx.doi.org/10.1016/j.ympev.2014.11.011
    sax = ott.taxon('Saxifragella bicuspidata')
    ott.taxon('Saxifraga').take(sax)
    sax.rename('Saxifraga bicuspidata')

    # JAR 2015-07-21 noticed, obviously wrong
    ott.taxonThatContains('Ophiurina', 'Acrocnida brachiata').extant()

    # straightening out an awful mess
    ott.taxon('Saccharomycetes', 'Saccharomycotina').extant()  # foo.  don't know who sets this

    ott.taxonThatContains('Rhynchonelloidea', 'Sphenarina').extant() # NCBI

    # https://github.com/OpenTreeOfLife/feedback/issues/133
    ott.taxon('Pipoidea', 'Amphibia').take(ott.taxon('Cordicephalus', 'Amphibia'))

    # This is a randomly chosen bivalve to force Bivalvia to not be extinct
    ott.taxon('Corculum cardissa', 'Bivalvia').extant()
    # Similarly for roaches
    ott.taxon('Periplaneta americana', 'Blattodea').extant()

    # https://github.com/OpenTreeOfLife/feedback/issues/159
    ott.taxon('Nesophontidae').extinct()

    # "Old" patch system
    TsvEdits.edit(ott, 'feed/ott/edits/')

    # JAR 2016-06-30 Fixing a warning from 'report_on_h2007'
    # There really ought to be a family (Hyaloraphidiaceae, homonym) in
    # between, but it's not really necessary, so I won't bother
    ott.taxon('Hyaloraphidiales', 'Fungi').take(ott.taxon('Hyaloraphidium', 'Fungi'))

    # 2016-07-01 JAR while studying rank inversions.  NCBI has wrong rank.
    if ott.taxon('Vezdaeaceae').getRank() == 'genus':
        ott.taxon('Vezdaeaceae').setRank('family')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/195
    ott.taxon('Opisthokonta').setRank('no rank')

    # https://github.com/OpenTreeOfLife/feedback/issues/177
    ott.taxon('Amia fasciata').prune('https://github.com/OpenTreeOfLife/feedback/issues/177')

    # https://github.com/OpenTreeOfLife/feedback/issues/127
    # single species, name not accepted
    ott.taxon('Cestracion', 'Sphyrnidae').prune('https://github.com/OpenTreeOfLife/feedback/issues/127')

    # Related to https://github.com/OpenTreeOfLife/feedback/issues/307
    pter = ott.maybeTaxon('Pteridophyta')
    if pter != None and pter.parent == ott.taxon('Archaeplastida'):
        for child in pter.getChildren():
            child.unplaced()
        ott.taxon('Tracheophyta').absorb(pter)

    # https://github.com/OpenTreeOfLife/feedback/issues/221
    for name in ['Elephas cypriotes',         # NCBI
                 'Elephas antiquus',          # NCBI
                 'Elephas sp. NHMC 20.2.2.1', # NCBI
                 'Mammuthus',                 # NCBI
                 'Parelephas',                # GBIF
                 'Numidotheriidae',           # GBIF
                 'Barytheriidae',             # GBIF
                 'Anthracobunidae',           # GBIF
                 ]:
        taxon = ott.maybeTaxon(name, 'Proboscidea')
        if taxon != None:
            taxon.extinct()

    # From 2.10 deprecated list
    for (name, anc) in [
                 # Sphaerulina	if:5128,ncbi:237179,worms:100120,worms:100117,gbif:2621555,gbif:2574294,gbif:7254927,gbif:2564487,irmng:1291796	newly-hidden[extinct]	Sphaerulina	=
                 # Cucurbita	ncbi:3660,gbif:2874506,irmng:1009179	newly-hidden[extinct]	Cucurbita	=	synthesis
                 # Tuber	if:5629,ncbi:36048,gbif:7257845,gbif:7257854,gbif:2593130,gbif:5237010,irmng:1120184,irmng:1029932	newly-hidden[extinct]	Tuber	=
                 # Blastocystis	silva:U26177/#4,ncbi:12967,if:20081,gbif:3269640,irmng:1031549	newly-hidden[extinct]	Blastocystis	=
                 # Clavulinopsis	if:17324,ncbi:104211,gbif:2521976,irmng:1340486	newly-hidden[extinct]	Clavulinopsis	=
                 # Nesophontidae	gbif:9467,irmng:104821	newly-hidden[extinct]	Nesophontidae	=
                 # Polystoma	ncbi:92216,gbif:2503819,irmng:1269690,irmng:1269737	newly-hidden[extinct]	Polystoma	=
                 # Rustia	ncbi:86991,gbif:2904559,irmng:1356264	newly-hidden[extinct]	Rustia	=
                 ('Thalassiosira guillardii', 'SAR'),    # mistake in WoRMS
            ]:
        taxon = ott.maybeTaxon(name, anc)
        if taxon != None:
            taxon.extant()

    # we were getting extinctness from IRMNG, but now it's suppressed
    din = ott.maybeTaxon('Dinaphis', 'Aphidoidea')
    if din != None: din.extinct()

    # Yan Wong https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116
    # fung.taxon('Mycosphaeroides').extinct()  - gone
    if ott.maybeTaxon('Majasphaeridium'):
        ott.taxon('Majasphaeridium').extinct() # From IF

    # https://github.com/OpenTreeOfLife/feedback/issues/64
    ott.taxon('Plagiomene').extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/65
    ott.taxon('Worlandia').extinct() # from GBIF

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/72
    ott.taxon('Myeladaphus').extinct() # from GBIF

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/78
    ott.taxon('Oxyprinichthys').extinct() # from GBIF

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/82
    tar = ott.maybeTaxon('Tarsius thailandica')
    if tar != None: tar.extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/86
    ott.taxon('Gillocystis').extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/186
    # https://en.wikipedia.org/wiki/Réunion_ibis
    thres = ott.taxon('Threskiornis solitarius') # from GBIF
    thres.absorb(ott.taxon('Raphus solitarius')) # from GBIF
    thres.extinct()

    # https://github.com/OpenTreeOfLife/feedback/issues/282
    ott.taxon('Chelomophrynus', 'Anura').extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/283
    ott.taxon('Shomronella', 'Anura').extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/165
    sphe = False
    for child in ott.taxon('Sphenodontidae').children: # from GBIF
        if child.name != 'Sphenodon':
            child.extinct()
        else:
            sphe = True
    if not sphe:
        print '** No extant member of Sphenodontidae'

    # https://github.com/OpenTreeOfLife/feedback/issues/159
    # sez: 'The order Soricomorpha ("shrew-form") is a taxon within the
    # class of mammals. In previous years it formed a significant group
    # within the former order Insectivora. However, that order was shown
    # to be polyphyletic ...'
    ott.taxon('Nesophontidae', 'Soricomorpha').extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/135
    cry = ott.maybeTaxon('Cryptobranchus matthewi', 'Amphibia')
    if cry != None: cry.extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/134
    ott.taxon('Hemitrypus', 'Amphibia').extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/133
    cord = ott.taxon('Cordicephalus', 'Amphibia')
    if cord != None:
        cord.extinct() # from GBIF

    # https://github.com/OpenTreeOfLife/feedback/issues/123
    gryph = ott.taxon('Gryphodobatis', 'Orectolobidae')
    if gryph != None:
        gryph.extinct() # from GBIF

    # Recover missing extinct flags.  I think these are problems in
    # the dump that I have, but have been fixed in the current IRMNG
    # (July 2016).
    for (name, super) in [
            ('Tvaerenellidae', 'Ostracoda'),
            ('Chrysocythere', 'Ostracoda'),
            ('Mutilus', 'Ostracoda'),
            ('Aurila', 'Ostracoda'),
            ('Loxostomum', 'Ostracoda'),
            ('Loxostomatidae', 'Ostracoda'),
    ]:
        if super == None:
            tax = ott.maybeTaxon(name) # IRMNG
        else:
            tax = ott.maybeTaxon(name, super)
        if tax != None: tax.extinct()

    # https://github.com/OpenTreeOfLife/feedback/issues/304
    ott.taxon('Notobalanus', 'Maxillopoda').extant() # IRMNG

    # https://github.com/OpenTreeOfLife/feedback/issues/303
    ott.taxon('Neolepas', 'Maxillopoda').extant() # IRMNG

    # See NCBI
    ott.taxon('Millericrinida').extant() # WoRMS

    # Doug Soltis 2015-02-17 https://github.com/OpenTreeOfLife/feedback/issues/59 
    # http://dx.doi.org/10.1016/0034-6667(95)00105-0
    # Seems to have gone away with 2016 GBIF.  On fossilworks site
    timo = ott.maybeTaxon('Timothyia', 'Laurales')
    if timo != None: timo.extinct()

    # Yan Wong 2014-12-16 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116
    for name in ['Griphopithecus', 'Asiadapis',
                 'Lomorupithecus', 'Marcgodinotius', 'Muangthanhinius',
                 'Plesiopithecus', 'Suratius', 'Killikaike blakei', 'Rissoina bonneti',
                 # 'Mycosphaeroides'  - gone
             ]:
        claim = Whether_extant(name, False, 'https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116')
        claim.make_true(ott)

    # MTH 2016-01-05 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/182
    h2 = ott.maybeTaxon('Homarus', 'Coleoptera')
    if h2 != None and not h2.hasChildren(): h2.prune()


# -----------------------------------------------------------------------------
# OTT id assignment

def ids_and_additions(ott):

    # ad hoc assignments specifically for NCBI taxa, basedon NCBI id

    for (ncbi_id, ott_id, name) in ncbi_ott_assignments.ncbi_assignments_list:
        im = ott.lookupQid(QualifiedId('ncbi', ncbi_id))
        if im == None:
            print '** ncbi:%s not found in OTT - %s' % (ncbi_id, name)
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
    assign_ids_from_list(ott, 'ott_id_list/by_qid.csv')

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
        [qid, ids] = row
        taxon = tax.lookupQid(QualifiedId(qid))
        if taxon != None:
            id_list = ids.split(';')

            # If every id maps either nowhere or to the taxon,
            # set every id to map to the taxon.
            win = True
            for id in id_list:
                z = tax.lookupId(id)
                if z != None and z != taxon: win = False
            if win:
                if taxon.id != id_list[0]:
                    if taxon.id == None:
                        count += 1
                    else:
                        change_count += 1
                        taxon.setId(id_list[0])
                        for id in id_list:
                            taxon.taxonomy.addId(taxon, id)
    infile.close()
    print '| Assigned %s, changed %s ids from %s' % (count, change_count, filename)

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
                     ]
