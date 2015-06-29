# coding=utf-8

from org.opentreeoflife.smasher import Taxonomy

def load_silva():
    silva = Taxonomy.getTaxonomy('tax/silva/', 'silva')

    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms this synonymy.  Dail L. prefers -phyta to -phyceae
    # but says -phytina would be more correct per code.
    silva.taxon('Rhodophyceae').rename('Rhodophyta')

    patch_silva(silva)

    return silva

def patch_silva(silva):

    # Sample contamination
    silva.taxon('Trichoderma harzianum').prune()
    silva.taxon('Sclerotinia homoeocarpa').prune()
    silva.taxon('Puccinia triticina').prune()

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/104
    silva.taxon('Caenorhabditis elegans').prune()

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/100
    silva.taxon('Solanum lycopersicum').prune()

    # - Deal with parent/child homonyms in SILVA -
    # Arbitrary choices here to eliminate ambiguities down the road when NCBI gets merged.
    # (If the homonym is retained, then the merge algorithm will have no
    # way to choose between them, and refuse to match either.  It will
    # then create a third homonym.)
    # Note order dependence between the following two
    silva.taxon('Intramacronucleata','Intramacronucleata').rename('Intramacronucleata inf.')
    silva.taxon('Spirotrichea','Intramacronucleata inf.').rename('Spirotrichea inf.')
    silva.taxon('Cyanobacteria','Bacteria').rename('Cyanobacteria sup.')
    silva.taxon('Actinobacteria','Bacteria').rename('Actinobacteria sup.')
    silva.taxon('Acidobacteria','Bacteria').rename('Acidobacteria sup.')
    silva.taxon('Ochromonas','Ochromonadales').rename('Ochromonas sup.')
    silva.taxon('Tetrasphaera','Tetrasphaera').rename('Tetrasphaera inf.')

    # SILVA's placement of Rozella as a sibling of Fungi is contradicted
    # by Hibbett 2007, which puts it under Fungi.  Hibbett gets priority.
    # We make the change to SILVA to prevent Nucletmycea from being
    # labeled 'tattered'.
    silva.taxon('Fungi').take(silva.taxon('Rozella'))

    # 2014-04-12 Rick Ree #58 and #48 - make them match NCBI
    silva.taxon('Arthrobacter Sp. PF2M5').rename('Arthrobacter sp. PF2M5')
    silva.taxon('Halolamina sp. wsy15-h1').rename('Halolamina sp. WSY15-H1')
    # RR #55 - this is a silva/ncbi homonym
    silva.taxon('vesicomya').rename('Vesicomya')

    # From Laura and Dail on 5 Feb 2014
    # https://groups.google.com/forum/#!topic/opentreeoflife/a69fdC-N6pY
    silva.taxon('Diatomea').rename('Bacillariophyta')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/30
    # https://github.com/OpenTreeOfLife/feedback/issues/5
    for name in ['GAL08', 'GOUTA4', 'JL-ETNP-Z39', 'Kazan-3B-28',
                 'LD1-PA38', 'MVP-21', 'NPL-UPA2', 'OC31', 'RsaHF231',
                 'S2R-29', 'SBYG-2791', 'SM2F11', 'WCHB1-60', 'T58',
                 'LKM74', 'LEMD255', 'CV1-B1-93', 'H1-10', 'H26-1',
                 'M1-18D08', 'D4P07G08', 'DH147-EKD10', 'LG25-05',
                 'NAMAKO-1', 'RT5iin25', 'SA1-3C06', 'DH147-EKD23']:
            silva.taxon(name).elide()  #maybe just hide instead ?

    # - Deal with division alignment issues -
    # In SILVA, Ctenophora is a genus inside of SAR, not a metazoan phylum
    if False:
        # *** The following seems to not work. ***
        ott.notSame(silva.taxon('Ctenophora', 'Coscinodiscophytina'),
                    skel.taxon('Ctenophora'))
    else:
        silva.taxon('Ctenophora', 'Coscinodiscophytina').prune()

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/79
    Ml = silva.maybeTaxon('Melampsora lini')
    if Ml != None: Ml.prune()
    Ps = silva.maybeTaxon('Polyangium sorediatum')
    if Ps != None: Ps.prune()
    up = silva.maybeTaxon('unidentified plasmid')
    if up != None: up.prune()

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    silva.taxon('Choanoflagellida', 'Ichthyosporea').rename('Choanoflagellida NOT')


def load_h2007():
    h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')

    # 2014-04-08 Misspelling
    if h2007.maybeTaxon('Chaetothryriomycetidae') != None:
        h2007.taxon('Chaetothryriomycetidae').rename('Chaetothyriomycetidae')

    if h2007.maybeTaxon('Asteriniales') != None:
        h2007.taxon('Asteriniales').rename('Asterinales')
    else:
        h2007.taxon('Asterinales').synonym('Asteriniales')

    return h2007

def load_fung():
    fung = Taxonomy.getTaxonomy('tax/fung/', 'if')

    # 2014-04-14 Bad Fungi homonyms in new version of IF.  90156 is the good one.
    # 90154 has no descendants
    if fung.maybeTaxon('90154') != None:
        print 'Removing Fungi 90154'
        fung.taxon('90154').prune()
    # 90155 is "Nom. inval." and has no descendants
    if fung.maybeTaxon('90155') != None:
        print 'Removing Fungi 90155'
        fung.taxon('90155').prune()

    fixProtists(fung)

    # smush folds sibling taxa that have the same name.
    # fung.smush()

    patch_fung(fung)
    fung.smush()

    return fung

def patch_fung(fung):
    # analyzeMajorRankConflicts sets the "major_rank_conflict" flag when
    # intermediate ranks are missing (e.g. a family that's a child of a
    # class)
    fung.analyzeMajorRankConflicts()

    # JAR 2014-04-27 JAR found while investigating 'hidden' status of
    # Thelohania butleri.  Move out of Protozoa to prevent their being hidden
    # --- this seems to have been fixed in the 2014-04 version of IF
    # fung.taxon('Fungi').take(fung.taxon('Microsporidia'))

    # *** Non-Fungi processing

    # JAR 2014-05-13 Chlorophyte or fungus?  This one is very confused.
    # Pick it up from GBIF if at all
    # Mycobank and EOL (via Mycobank) put in in Algae
    # IF says it's a chlorophyte, not a fungus
    # First Nature says it's a synonym for a fungus (Terana caerulea) 'Cobalt crust fungus'
    # GBIF puts it in Basidiomycota (Fungi), synonym for Terana caerulea, in Phanerochaetaceae
    # Study pg_391 puts it sister to Phlebiopsis gigantea, in Basidiomycota
    # Study pg_1744 puts it sister to genus Phanerochaete, which is in Basidiomycota
    # Study pg_1160 puts is close to Phanerochaete and Hyphodermella
    # I'm thinking of putting it in Phanerochaetaceae. - GBIF does this for us.
    fung.taxon('Byssus phosphorea').prune()

    if False:  # see taxonomies.load_fung
        # Work in progress.  By promoting to root we've lost the fact that
        # protozoa are eukaryotes, which is unfortunate.  Not important in this
        # case, but suggestive of deeper problems in the framework.
        # Test case: Oomycota should end up in Stramenopiles.
        fung_Protozoa = fung.maybeTaxon('Protozoa')
        if fung_Protozoa != None:
            fung_Protozoa.hide()   # recursive
            fung_Protozoa.detach()
            fung_Protozoa.elide()
        fung_Chromista = fung.maybeTaxon('Chromista')
        if fung_Chromista != None:
            fung_Chromista.hide()  # recursive
            fung_Chromista.detach()
            fung_Chromista.elide()

    # IF Thraustochytriidae = SILVA Thraustochytriaceae ?  (Stramenopiles)
    # IF T. 90638 contains Sicyoidochytrium, Schizochytrium, Ulkenia, Thraustochytrium
    #  Parietichytrium, Elina, Botryochytrium, Althornia
    # SILVA T. contains Ulkenia and a few others of these... I say yes.
    thraust = fung.taxon('90377')
    thraust.synonym('Thraustochytriaceae')
    thraust.synonym('Thraustochytriidae')
    thraust.synonym('Thraustochytridae')

    # IF Labyrinthulaceae = SILVA Labyrinthulomycetes ?  NO.
    # IF L. contains only Labyrinthomyxa, Labyrinthula
    # SILVA L. contains a lot more than that.

    # IF Hyphochytriaceae = SILVA Hyphochytriales ?
    # SILVA Hyphochytriales = AB622284/#4 contains only
    # Hypochitrium, Rhizidiomycetaceae

    # There are two Bacillaria.
    # 1. NCBI 3002, in Stramenopiles, contains Bacillaria paxillifer.
    #    No synonyms in NCBI.
    #    IF has Bacillaria as a synonym for Camillea (if:777).
    #    Bacillaria is not otherwise in IF.
    #    Cammillea in IF is in Stramenopiles.
    # 2. NCBI 109369, in Pezizomycotina
    #    No synonyms in NCBI.
    # NCBI 13677 = Camillea, a fish.

    # There are two Polyangium, a bacterium (NCBI) and a flatworm (IF).

    # smush folds sibling taxa that have the same name.
    # (repeats - see load_fung()  ???)
    # fung.smush()

    # *** Fungi processing

    # JAR 2014-04-11 Missing in earlier IF, mistake in later IF -
    # extraneous authority string.  See Romina's issue #42
    # This is a fungus.
    cyph = fung.maybeTaxon('Cyphellopsis')
    if cyph == None:
        cyph = fung.maybeTaxon('Cyphellopsis Donk 1931')
        if cyph != None:
            cyph.rename('Cyphellopsis')
        else:
            cyph = fung.newTaxon('Cyphellopsis', 'genus', 'if:17439')
        fung.taxon('Niaceae').take(cyph)

    # Romina 2014-04-09
    # IF has both Hypocrea and Trichoderma.  Hypocrea is the right name.
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/86
    fung.taxon('Trichoderma viride').rename('Hypocrea rufa')  # Type
    fung.taxon('Hypocrea').absorb(fung.taxonThatContains('Trichoderma', 'Hypocrea rufa'))

    # Romina https://github.com/OpenTreeOfLife/reference-taxonomy/issues/42
    fung.taxon('Trichoderma deliquescens').rename('Hypocrea lutea')

    # 2014-04-13 Romina #40, #60
    for foo in [('Neozygitales', ['Neozygitaceae']),
                ('Asterinales', ['Asterinaceae']),
                ('Savoryellales', ['Savoryella', 'Ascotaiwania', 'Ascothailandia']), 
                ('Cladochytriales', ['Cladochytriaceae', 'Nowakowskiellaceae', 'Septochytriaceae', 'Endochytriaceae']),
                ('Jaapiales', ['Jaapiaceae']),
                ('Coniocybales', ['Coniocybaceae']),
                ('Hyaloraphidiales', ['Hyaloraphidiaceae']),
                ('Mytilinidiales', ['Mytilinidiaceae', 'Gloniaceae'])]:
        order = fung.maybeTaxon(foo[0])
        if order != None:
            for family in foo[1]:
                order.take(fung.taxon(family))
        else:
            print '*** Missing fungal order', foo[0]

    fung.taxon('Asterinales').synonym('Asteriniales')  #backward compatibility

    # ** No taxon found with this name: Nowakowskiellaceae
    # ** No taxon found with this name: Septochytriaceae
    # ** No taxon found with this name: Jaapiaceae
    # ** (null=if:81865 Rhizocarpaceae) is already a child of (null=h2007:212 Rhizocarpales)
    # ** No taxon found with this name: Hyaloraphidiaceae

    # Yan Wong https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116
    fung.taxon('Mycosphaeroides').extinct()
    fung.taxon('Majasphaeridium').extinct()

    print "Fungi in Index Fungorum has %s nodes"%fung.taxon('Fungi').count()


def load_713():
    study713 = Taxonomy.getTaxonomy('tax/713/', 'study713')
    return study713

def load_ncbi():
    ncbi = Taxonomy.getTaxonomy('tax/ncbi/', 'ncbi')

    ncbi.taxon('Viridiplantae').rename('Chloroplastida')

    # New NCBI top level taxa introduced circa July 2014
    for toplevel in ["Viroids", "other sequences", "unclassified sequences"]:
        if ncbi.maybeTaxon(toplevel) != None:
            ncbi.taxon(toplevel).prune()

    # - Canonicalize division names (cf. skeleton) -
    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms these synonymies.
    ncbi.taxon('Glaucocystophyceae').rename('Glaucophyta')
    ncbi.taxon('Haptophyceae').rename('Haptophyta')

    ncbi.taxon('Viruses').hide()

    patch_ncbi(ncbi)

    # analyzeOTUs sets flags on questionable taxa ("unclassified",
    #  hybrids, and so on) to allow the option of suppression downstream
    ncbi.analyzeOTUs()
    ncbi.analyzeContainers()

    return ncbi

def patch_ncbi(ncbi):
    # - Touch-up -

    # RR 2014-04-12 #49
    ncbi.taxon('leotiomyceta').rename('Leotiomyceta')

    # RR #53
    ncbi.taxon('White-sloanea').synonym('White-Sloanea')

    # RR #56
    ncbi.taxon('sordariomyceta').rename('Sordariomyceta')

    # RR #52
    ncbi.taxon('spinocalanus spinosus').rename('Spinocalanus spinosus')
    ncbi.taxon('spinocalanus angusticeps').rename('Spinocalanus angusticeps')

    # RR #59
    ncbi.taxon('candidate division SR1').rename('Candidate division SR1')
    ncbi.taxon('candidate division WS6').rename('Candidate division WS6')
    ncbi.taxon('candidate division BRC1').rename('Candidate division BRC1')
    ncbi.taxon('candidate division OP9').rename('Candidate division OP9')
    ncbi.taxon('candidate division JS1').rename('Candidate division JS1')

    # RR #51
    ncbi.taxon('Dendro-hypnum').synonym('Dendro-Hypnum')
    # RR #45
    ncbi.taxon('Cyrto-hypnum').synonym('Cyrto-Hypnum')
    # RR #54
    ncbi.taxon('Sciuro-hypnum').synonym('Sciuro-Hypnum')

    # RR 2014-04-12 #46
    ncbi.taxon('Pechuel-loeschea').synonym('Pechuel-Loeschea')

    # RR #50
    ncbi.taxon('Saxofridericia').synonym('Saxo-Fridericia')
    ncbi.taxon('Saxofridericia').synonym('Saxo-fridericia')

    # RR #57
    ncbi.taxon('Solms-laubachia').synonym('Solms-Laubachia')

    # Romina 2014-04-09
    # NCBI has both Hypocrea and Trichoderma.
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/86
    ncbi.taxon('Trichoderma viride').rename('Hypocrea rufa')  # Type
    ncbi.taxon('Hypocrea').absorb(ncbi.taxonThatContains('Trichoderma', 'Hypocrea rufa'))

    # Mark Holder https://github.com/OpenTreeOfLife/reference-taxonomy/issues/120
    ncbi.taxon('Cetartiodactyla').synonym('Artiodactyla')

    # Cody Howard https://github.com/OpenTreeOfLife/feedback/issues/57
    # http://dx.doi.org/10.1002/fedr.19971080106
    ncbi.taxon('Massonieae').take(ncbi.taxon('Resnova'))

    # From exmaming the deprecated OTU list.  This one occurs in study pg_188
    # Name got better, old name lost  JAR 2015-06-27
    # This could probably be automated, just by looking up the NCBI id
    # in the right table.
    for (oldname, ncbiid) in [
            ('Bifidobacterium pseudocatenulatum DSM 20438 = JCM 1200 = LMG 10505', '547043'),
            ('Bifidobacterium catenulatum DSM 16992 = JCM 1194 = LMG 11043', '566552'),
            ('Escherichia coli DSM 30083 = JCM 1649 = ATCC 11775', '866789'),
            ('Borrelia sp. SV1', 498741),  # Borrelia finlandensis
            ('Planchonella sp. Meyer 3013', 371649), #was 419398
            ('Planchonella sp. Takeuchi & al 17902', 419399),
            ('Sersalisia sp. Bartish and Ford 33', 346601), #was 346602
            ('Borrelia sp. SV1', 498741),
            ('Planchonella sp. Meyer 3013', 419398),
            ('Planchonella sp. Takeuchi & al 17902', 419399),
            ('Pycnandra sp. Munzinger 2618', 550765),
            ('Pycnandra sp. Munzinger 2615', 550764),
            ('Pycnandra sp. Munzinger 3135', 550768),
            ('Pycnandra sp. Lowry et al. 5786', 280721), #was 280735
            ('Osmolindsaea sp. SL02013b', 1393958),
            ('Osmolindsaea sp. SL-2013a', 1388881),
            ('Docosaccus sp. GW5429', 1503681), #was 582882
            ('Lellingeria sp. MAS-2010', 741642),
            ('Terpsichore sp. MAS-2010d', 741647),
            ('Terpsichore sp. MAS-2010c', 741646),
            ('Terpsichore sp. MAS-2010b', 741644),
            # ('Narrabeena sp. 0CDN6739-K', 1342637),  left no forwarding address
            ('Iochroma sp. Smith 337', 1545467), #was 362368
            ('Iochroma sp. Smith 370', 1545468), #was 362367
            ('cyanobacterium endosymbiont of Epithemia turgida isolate ETSB Lake Yunoko', 1228987),
            ('Pycnandra sp. McPherson and Munzinger 18106', 278656),
            ('Lellingeria sp. PHL-2010c', 861204),
            ('Lellingeria sp. PHL-2010d', 861206),
            ('Lellingeria sp. PHL-2010a', 861202),
            ('Pycnandra sp. Munzinger 2885', 550767),
            ('Pycnandra sp. Munzinger 2624', 550766),
            ('Caenorhabditis briggsae AF16', 6238), #was 473542
            ('Tachigali sp. Clarke 7212', 162921),
            ('Euretidae sp. HBFH 16-XI-02-1-001', 396898),
            ('Cedrela sp. 4 ANM-2010', 582833), #was 934059
            ('Schismatoglottis sp. SYW-2010f', 743908),
            ('Schismatoglottis sp. SYW-2010c', 743905),
            ('Pycnandra sp. Swenson 597', 282185),
            ('Planchonella sp. Meyer 3013', 73570),
            ('Pycnandra sp. Lowry et al. 5786', 9539),
            ('Docosaccus sp. GW5429', 7764),
            ('Narrabeena sp. 0CDN6739-K', 39542),
            ('Iochroma sp. Smith 337', 8960),
            ('Iochroma sp. Smith 370', 8953),
            ('Caenorhabditis briggsae AF16', 1949),
            ('Cedrela sp. 4 ANM-2010', 624),
    ]:
        tax = ncbi.maybeTaxon(str(ncbiid))
        if tax != None:
            tax.synonym(oldname)

def loadGbif():
    gbif = Taxonomy.getTaxonomy('tax/gbif/', 'gbif')
    gbif.smush()

    # In GBIF, if a rank is skipped for some children but not others, that
    # means the rank-skipped children are incertae sedis.  Mark them so.
    gbif.analyzeMajorRankConflicts()

    fixProtists(gbif)  # creates a Eukaryota node
    fixPlants(gbif)
    gbif.taxon('Animalia').synonym('Metazoa')

    patch_gbif(gbif)
    return gbif

def patch_gbif(gbif):
    # - Touch-up -

    # Rod Page blogged about this one
    # http://iphylo.blogspot.com/2014/03/gbif-liverwort-taxonomy-broken.html
    gbif.taxon('Jungermanniales','Marchantiophyta').absorb(gbif.taxon('Jungermanniales','Bryophyta'))

    # Joseph 2013-07-23 https://github.com/OpenTreeOfLife/opentree/issues/62
    # GBIF has two copies of Myospalax
    gbif.taxon('6006429').absorb(gbif.taxon('2439119'))

    # RR 2014-04-12 #47
    gbif.taxon('Drake-brockmania').absorb(gbif.taxon('Drake-Brockmania'))
    # RR #50 - this one is in NCBI, see above
    gbif.taxon('Saxofridericia').absorb(gbif.taxon('4930834')) #Saxo-Fridericia
    # RR #57 - the genus is in NCBI, see above
    gbif.taxon('Solms-laubachia').absorb(gbif.taxon('4908941')) #Solms-Laubachia
    gbif.taxon('Solms-laubachia pulcherrima').absorb(gbif.taxon('Solms-Laubachia pulcherrima'))

    # RR #45
    gbif.taxon('Cyrto-hypnum').absorb(gbif.taxon('4907605'))

    # 2014-04-13 JAR noticed while grepping
    gbif.taxon('Chryso-hypnum').absorb(gbif.taxon('Chryso-Hypnum'))
    gbif.taxon('Drepano-Hypnum').rename('Drepano-hypnum')
    gbif.taxon('Complanato-Hypnum').rename('Complanato-hypnum')
    gbif.taxon('Leptorrhyncho-Hypnum').rename('Leptorrhyncho-hypnum')

    # Romina 2014-04-09
    # GBIF has both Hypocrea and Trichoderma.  And it has four Trichoderma synonyms...
    # pick the one that contains bogo-type Hypocrea rufa
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/86
    gbif.taxon('Trichoderma viride').rename('Hypocrea rufa')  # Type
    gbif.taxon('Hypocrea').absorb(gbif.taxonThatContains('Trichoderma', 'Hypocrea rufa'))

    # 2014-04-21 RR
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/45
    for epithet in ['cylindraceum',
                    'lepidoziaceum',
                    'intermedium',
                    'espinosae',
                    'pseudoinvolvens',
                    'arzobispoae',
                    'sharpii',
                    'frontinoae',
                    'atlanticum',
                    'stevensii',
                    'brachythecium']:
        gbif.taxon('Cyrto-hypnum ' + epithet).absorb(gbif.taxon('Cyrto-Hypnum ' + epithet))

    # JAR 2014-04-23 Noticed while perusing silva/gbif conflicts
    gbif.taxon('Ebriaceae').synonym('Ebriacea')
    gbif.taxon('Acanthocystidae').absorb(gbif.taxon('Acanthocistidae'))
    gbif.taxon('Dinophyta').synonym('Dinoflagellata')

    # JAR 2014-06-29 stumbled on this while trying out new alignment
    # methods and examining troublesome homonym Bullacta exarata.
    # GBIF obviously puts it in the wrong place, see description at
    # http://www.gbif.org/species/4599744 (it's a snail, not a shrimp).
    bex = gbif.taxon('Bullacta exarata', 'Atyidae')
    bec = gbif.taxon('Bullacta ecarata', 'Atyidae')
    if bex != None and bec != None:
        bex.absorb(bec)
        bex.detach()

    # Yan Wong 2014-12-16 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116
    for name in ['Griphopithecus', 'Asiadapis',
                 'Lomorupithecus', 'Marcgodinotius', 'Muangthanhinius',
                 'Plesiopithecus', 'Suratius', 'Killikaike blakei', 'Rissoina bonneti',
                 'Mycosphaeroides']:
        gbif.taxon(name).extinct()

    # Doug Soltis 2015-02-17 https://github.com/OpenTreeOfLife/feedback/issues/59 
    # http://dx.doi.org/10.1016/0034-6667(95)00105-0
    gbif.taxon('Timothyia').extinct()

    # JAR 2014-07-18  - get rid of Helophorus duplication
    gbif.taxon('3263442').absorb(gbif.taxon('6757656'))

    # JAR 2015-06-27  there are two Myospalax myospalax
    # The one in Spalacidae is the right one, Myospalacinae is the wrong one
    # (according to NCBI)
    # Probably should clean up the whole genus
    gbif.taxon('2439121').absorb(gbif.taxon('6075534'))

    # 4010070	pg_1378	Gelidiellaceae	gbif:8998  -- ok, paraphyletic

    return gbif

def loadIrmng():
    irmng = Taxonomy.getTaxonomy('tax/irmng/', 'irmng')
    irmng.smush()
    irmng.analyzeMajorRankConflicts()

    fixProtists(irmng)
    fixPlants(irmng)
    irmng.taxon('Animalia').synonym('Metazoa')

    # JAR 2014-04-26 Flush all 'Unaccepted' taxa
    irmng.taxon('Unaccepted', 'life').prune()

    # Fixes

    # Neopithecus (extinct) occurs in two places.  Flush one, mark the other
    irmng.taxon('1413316').prune() #Neopithecus in Mammalia
    irmng.taxon('1413315').extinct() #Neopithecus in Primates (Pongidae)

    # RR #50
    # irmng.taxon('Saxo-Fridericia').rename('Saxofridericia')
    # irmng.taxon('Saxofridericia').absorb(irmng.taxon('Saxo-fridericia'))
    saxo = irmng.maybeTaxon('1063899')
    if saxo != None:
        saxo.absorb(irmng.taxon('1071613'))

    # Romina 2014-04-09
    # IRMNG has EIGHT different Trichodermas.  (Four are synonyms of other things.)
    # 1307461 = Trichoderma Persoon 1794, in Hypocreaceae
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/86
    irmng.taxon('Hypocrea').absorb(irmng.taxon('1307461'))

    # JAR 2015-06-28
    # The synonym Ochrothallus multipetalus -> Niemeyera multipetala
    # is no good; it interferes with correct processing of Ochrothallus 
    # multipetalus.  We could remove the synonym, but instead remove its 
    # target because no synonym-removal command is available.
    irmng.taxon('Niemeyera multipetala').prune()

    return irmng

def loadWorms():
    worms = Taxonomy.getTaxonomy('tax/worms/', 'worms')
    worms.smush()

    worms.taxon('Biota').synonym('life')
    worms.taxon('Animalia').synonym('Metazoa')
    fixProtists(worms)
    fixPlants(worms)
    return worms


# Common code for GBIF, IRMNG, Index Fungorum
def fixProtists(tax):
    euk = tax.maybeTaxon('Eukaryota')
    if euk == None:
        euk = tax.newTaxon('Eukaryota', 'domain', 'ncbi:2759')
    co = tax.maybeTaxon('cellular organisms')
    if co != None: co.take(euk)
    else:
        li = tax.maybeTaxon('life')
        if li != None: li.take(euk)
        else:
            print('No parent for Eukaryota')

    for name in ['Animalia', 'Fungi', 'Plantae']:
        node = tax.maybeTaxon(name)
        if node != None:
            euk.take(node)

    for name in ['Chromista', 'Protozoa', 'Protista']:
        bad = tax.maybeTaxon(name)
        if bad != None:
            euk.take(bad)
            bad.hide()   # recursive
            for child in bad.children:
                child.incertaeSedis()
            bad.elide()

# 'Fix' plants to match SILVA and NCBI...
def fixPlants(tax):
    # 1. co-opt GBIF taxon for a slightly different purpose
    tax.taxon('Plantae').rename('Chloroplastida')

    euk = tax.taxon('Eukaryota')

    # JAR 2014-04-25 GBIF puts rhodophytes under Plantae, but it's not
    # in Chloroplastida.
    # Need to fix this before alignment because of division calculations.
    # Plantae is a child of 'life' in GBIF, and Rhodophyta is one of many
    # phyla below that.  Move up to be a sibling of Plantae.
    # Discovered while looking at Bostrychia alignment problem.
    euk.take(tax.taxon('Rhodophyta'))

    # JAR 2014-05-13 similarly
    # Glaucophyta - there's a GBIF/IRMNG false homonym, should be merged
    euk.take(tax.taxon('Glaucophyta'))
    euk.take(tax.taxon('Haptophyta'))

def loadOtt():
    ott = Taxonomy.getTaxonomy('tax/ott/', 'ott')
    return ott

# Manual 'bogotype' selection is because it can be hard to find a good
# non-homonym in SILVA, especially for suppressed groups...

# TBD: these tests live in a file in the germinator repo.  Change to
# using that list instead of this one.

def checkDivisions(tax):
    cases = [
        # ("Alveolata", "Dysteria marioni"), not in GBIF
        ("Archaea", "Aeropyrum camini"),
        ("Bacteria", "Bosea eneae"),

        # ("Eukaryota", "Synedra acus"),
        ("Chloroplastida", "Lippia alba"),
        ("Haptophyta", "Rebecca salina"),
        ("Rhodophyta", "Acrotylus australis"), #MANUALLY SELECTED
        ("Glaucophyta", "Cyanophora biloba"),

        ("Fungi", "Tuber indicum"),    # MANUAL

        ("Porifera", "Oscarella nicolae"), # MANUALLY SELECTED
        ("Cnidaria", "Malo kingi"),
        ("Ctenophora", "Pleurobrachia bachei"), # MANUALLY SELECTED
        ("Annelida", "Pista wui"),
        ("Chordata", "Ia io"),
        ("Arthropoda", "Rhysida nuda"),
        ("Arachnida", "Larca lata"),
        ("Diptera", "Osca lata"),
        ("Hymenoptera", "Odontomachus rixosus"), # MANUALLY SELECTED
        # ("Insecta", ...),
        ("Metazoa", "Loa loa"),
        ("Malacostraca", "Uca osa"),
        ("Coleoptera", "Car pini"),
        ("Lepidoptera", "Una usta"),
        ("Mollusca", "Lima lima")]
    for (div, bogo) in cases:
        probe = tax.maybeTaxon(bogo)
        if probe != None:
            d = probe.getDivision()
            if d == None:
                print "** No division for %s: want %s"%(bogo, div)
            elif d.name != div:
                print "** Wrong division for %s: have %s, want %s"%(bogo, d.name, div)
        elif False:
            probe = tax.maybeTaxon(div)
            if probe != None:
                print "** Division %s present but examplar %s isn't"%(div, bogo)
    return tax
