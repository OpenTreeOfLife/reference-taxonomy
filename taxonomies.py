# coding=utf-8

from org.opentreeoflife.taxa import Taxonomy
from claim import *
#from claim import Has_child, Whether_same, With_ancestor, With_descendant, \
#                  Whether_extant, make_claim, make_claims

this_source = 'https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/taxonomies.py'

def load_silva():
    silva = Taxonomy.getTaxonomy('tax/silva/', 'silva')

    # Used in studies pg_2448,pg_2783,pg_2753, seen deprecated on 2015-07-20
    silva.taxon('AF364847').rename('Pantoea ananatis LMG 20103')    # ncbi:706191
    silva.taxon('EF690403').rename('Pantoea ananatis B1-9')  # ncbi:1048262

    patch_silva(silva)

    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms this synonymy.  Dail L. prefers -phyta to -phyceae
    # but says -phytina would be more correct per code.
    # Skeleton taxonomy has -phyta (on Dail's advice).
    silva.taxon('Rhodophyceae').synonym('Rhodophyta')    # moot now?

    silva.taxon('Florideophycidae', 'Rhodophyceae').synonym('Florideophyceae')
    silva.taxon('Stramenopiles', 'SAR').synonym('Heterokonta') # needed by WoRMS

    return silva

def bad_name(taxonomy, name, anc):
    tax = taxonomy.maybeTaxon(name, anc)
    if tax != None:
        tax.clobberName('Not ' + name)

def patch_silva(silva):

    # Sample contamination 
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/201
    bad_name(silva, 'Trichoderma harzianum', 'life')
    bad_name(silva, 'Sclerotinia homoeocarpa', 'life')
    bad_name(silva, 'Puccinia triticina', 'life')
    bad_name(silva, 'Daphnia pulex', 'life')
    bad_name(silva, 'Nematostella vectensis', 'life')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/104
    bad_name(silva, 'Caenorhabditis elegans', 'life')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/100
    bad_name(silva, 'Solanum lycopersicum', 'life')

    # - Deal with parent/child homonyms in SILVA -
    # Arbitrary choices here to eliminate ambiguities down the road when NCBI gets merged.
    # (If the homonym is retained, then the merge algorithm will have no
    # way to choose between them, and refuse to match either.  It will
    # then create a third homonym.)
    # Note order dependence between the following two
    silva.taxon('Intramacronucleata','Intramacronucleata').clobberName('Intramacronucleata inf.')
    silva.taxon('Spirotrichea','Intramacronucleata inf.').clobberName('Spirotrichea inf.')
    silva.taxon('Cyanobacteria','Bacteria').clobberName('Cyanobacteria sup.')
    silva.taxon('Actinobacteria','Bacteria').clobberName('Actinobacteria sup.')
    silva.taxon('Acidobacteria','Bacteria').clobberName('Acidobacteria sup.')
    silva.taxon('Ochromonas','Ochromonadales').clobberName('Ochromonas sup.')
    silva.taxon('Tetrasphaera','Tetrasphaera').clobberName('Tetrasphaera inf.')

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

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/79
    Ml = silva.maybeTaxon('Melampsora lini')
    if Ml != None: Ml.prune(this_source)
    Ps = silva.maybeTaxon('Polyangium sorediatum')
    if Ps != None: Ps.prune(this_source)
    up = silva.maybeTaxon('unidentified plasmid')
    if up != None: up.prune(this_source)

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    # Not Choanoflagellida
    # Current NCBI name = 'Choanoflagellate-like sp. ribosomal RNA small subunit (16S rRNA-like)'
    silva.taxon('L29455').prune(this_source)

    # JAR noticed failed inclusion test - this is fixed in silva 117
    silva.taxon('Bacteria').take(silva.maybeTaxon('Verrucomicrobia group'))

    # EU930344	|	D78004/#6	|	Photorhabdus luminescens	|	samples	|	ncbi:1004165	|	
    # EU930342	|	D78004/#6	|	Photorhabdus luminescens	|	samples	|	ncbi:1004166	|	
    # 1004165	|	29488	|	Photorhabdus luminescens subsp. caribbeanensis	|	subspecies	|	
    # 1004166	|	29488	|	Photorhabdus luminescens subsp. hainanensis	|	subspecies	|	
    silva.taxon('EU930344').clobberName('Photorhabdus luminescens subsp. caribbeanensis')
    silva.taxon('EU930342').clobberName('Photorhabdus luminescens subsp. hainanensis')

    # JAR noticed ambiguity in build transcript
    cal = silva.maybeTaxon('AB074504/#6')
    if cal != None and cal.name == 'Calothrix':
        cal.clobberName('Calothrix (homonym)')

def load_h2007():
    h2007 = Taxonomy.getTaxonomy('feed/h2007/tree.tre', 'h2007')

    # 2014-04-08 Misspelling
    if h2007.maybeTaxon('Chaetothryriomycetidae') != None:
        h2007.taxon('Chaetothryriomycetidae').rename('Chaetothyriomycetidae')

    if h2007.maybeTaxon('Asteriniales') != None:
        h2007.taxon('Asteriniales').rename('Asterinales')
    else:
        h2007.taxon('Asterinales').synonym('Asteriniales')

    # h2007/if synonym https://github.com/OpenTreeOfLife/reference-taxonomy/issues/40
    h2007.taxon('Urocystales').synonym('Urocystidales')

    return h2007

# Index Fungorum

def load_fung():
    fung = Taxonomy.getTaxonomy('tax/fung/', 'if')

    fung.analyzeMajorRankConflicts()

    # 2014-04-14 Bad Fungi homonyms in new version of IF.  90156 is the good one.
    # 90154 has no descendants
    if fung.maybeTaxon('90154') != None:
        print 'Removing Fungi 90154'
        fung.taxon('90154').prune(this_source)
    # 90155 is "Nom. inval." and has no descendants
    if fung.maybeTaxon('90155') != None:
        print 'Removing Fungi 90155'
        fung.taxon('90155').prune(this_source)

    fix_basal(fung)

    # smush folds sibling taxa that have the same name.
    # fung.smush()

    if True:
        patch_fung(fung)
    else:
        try:
            patch_fung(fung)
        except:
            print '**** Exception in patch_fung'

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
    fung.taxon('Byssus phosphorea').prune(this_source)

    # IF Thraustochytriidae = SILVA Thraustochytriaceae ?  (Stramenopiles)
    # IF T. 90638 contains Sicyoidochytrium, Schizochytrium, Ulkenia, Thraustochytrium
    #  Parietichytrium, Elina, Botryochytrium, Althornia
    # SILVA T. contains Ulkenia and a few others of these... I say yes.
    thraust = fung.maybeTaxon('90377') # Seems to have gone away
    if thraust != None:
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

    fung.taxon('Asterinales').synonym('Asteriniales')  #backward compatibility

    # ** No taxon found with this name: Nowakowskiellaceae
    # ** No taxon found with this name: Septochytriaceae
    # ** No taxon found with this name: Jaapiaceae
    # ** (null=if:81865 Rhizocarpaceae) is already a child of (null=h2007:212 Rhizocarpales)
    # ** No taxon found with this name: Hyaloraphidiaceae

    # Yan Wong https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116
    # fung.taxon('Mycosphaeroides').extinct()  - gone
    fung.taxon('Majasphaeridium').extinct()

    fung.taxon('Urosporidium').prune(this_source) # parasitic protist, SILVA looks correct
    fung.taxon('Bonamia').prune(this_source)      # parasitic protist, SILVA looks correct
    fung.taxon('Zonaria').prune(this_source)
    fung.taxon('Ellobiopsis').prune(this_source)

    # Our IF dump puts genus Saccharomycetes (21291) directly under Fungi
    # (incertae sedis).  The corresponding IF record on the site says
    # "Position in classification: Fossil Fungi". Only two species, too
    # easily confused with class Saccharamycetes (90791). Discovered while
    # tracking down
    # ** Ranks out of order: (if:21291 Saccharomycetes?) genus has child (if:90341 Saccharomycetidae?) subclass
    # in GBIF.
    # Neither SILVA nor h2007 has the class.  GBIF used to have the genus,
    # not sure if it still does.
    cetes = fung.taxon('21291')
    if cetes != None:
        cetes.extinct()
        cetes.prune("if:21291")
        #cetes.rank = None
        #tina = fung.taxon('Saccharomycetaceae', 'Fungi')
        #if tina != None:
        #    tina.take(fung.taxon('Saccharomycetes', 'Fungi'))
        #cetes.extant()

    # JAR 2015-07-20 - largest unattached subtree was Microeccrina, which is dubious.
    fung.taxon('Microeccrina').prune('http://www.nhm.ku.edu/~fungi/Monograph/Text/chapter12.htm')

    # JAR 2015-07-20 - homonym, Nom. illegit.
    fung.taxon('Acantharia').prune('http://www.indexfungorum.org/Names/NamesRecord.asp?RecordID=8')

    # JAR 2015-09-10 on perusing a long list of equivocal homonyms
    # (weaklog.csv).  Hibbett 2007 and NCBI put Microsporidia in Fungi.
    fung.taxon('Fungi').take(fung.taxon('Microsporidia'))

    # This is helpful if SAR is a division
    # fung.taxon('SAR').take(fung.taxon('Oomycota'))

    # 2015-09-15 Pezizomycotina has no parent pointer; without Fungi as a barrier,
    # the placement of genus Onychophora is screwed up.
    # Found while investigating https://github.com/OpenTreeOfLife/feedback/issues/88
    fung.taxon('Ascomycota').take(fung.taxon('Pezizomycotina'))

    # 2015-10-06 JAR noticed while debugging deprecated taxa list:
    # This should cover Basidiomycota, Zygomycota, Glomeromycota, and Ascomycota
    for taxon in fung.taxa():
        if taxon.rank == 'phylum' and taxon.isRoot():
            fung.taxon('Fungi').take(taxon)

    # 2015-10-06 https://en.wikipedia.org/wiki/Taphrinomycotina
    if fung.taxon('Taphrinomycotina').isRoot():
        fung.taxon('Ascomycota').take(fung.taxon('Taphrinomycotina'))
    if fung.taxon('Saccharomycotina').isRoot():
        fung.taxon('Ascomycota').take(fung.taxon('Saccharomycotina'))

    print "Fungi in Index Fungorum has %s nodes"%fung.taxon('Fungi').count()

def link_to_h2007(tax):
    print '-- Putting families in Hibbett 2007 orders --'
    # 2014-04-13 Romina #40, #60
    for (order_name, family_names) in \
        [('Neozygitales', ['Neozygitaceae']),
         ('Asterinales', ['Asterinaceae']),
         ('Savoryellales', ['Savoryella', 'Ascotaiwania', 'Ascothailandia']), 
         ('Cladochytriales', ['Cladochytriaceae', 'Nowakowskiellaceae', 'Septochytriaceae', 'Endochytriaceae']),
         ('Jaapiales', ['Jaapiaceae']),
         ('Coniocybales', ['Coniocybaceae']),
         ('Hyaloraphidiales', ['Hyaloraphidiaceae']), # no such family
         ('Mytilinidiales', ['Mytilinidiaceae', 'Gloniaceae']),
        ]:
        order = tax.maybeTaxon(order_name)
        if order != None:
            for family in family_names:
                order.take(tax.taxon(family))
        else:
            print '*** Missing fungal order', foo[0]

    # Stereopsidaceae = Stereopsis + Clavulicium
    if tax.maybeTaxon('Stereopsidaceae') == None:
        s = tax.newTaxon('Stereopsidaceae', 'family', 'https://en.wikipedia.org/wiki/Stereopsidales')
        s.take(tax.taxon('Stereopsis', 'Agaricomycetes'))
        s.take(tax.taxon('Clavulicium', 'Agaricomycetes'))
        # Dangling node at this point, but will be attached below

    # 2015-07-13 Romina
    h2007_fam = 'http://figshare.com/articles/Fungal_Classification_2015/1465038'
    some_claims = []
    for (order, families) in \
        [('Talbotiomycetales',['Talbotiomyces calosporus']),
         ('Moniliellales',['Moniliellaceae']),   
         ('Malasseziales',['Malasseziaceae', 'Malassezia']),
         ('Trichotheliales',['Trichotheliaceae', 'Myeloconidiaceae']),
         ('Trichosporonales',['Sporobolomyces ruberrimus']),
         ('Holtermanniales',['Holtermanniella']),
         ('Lepidostromatales',['Lepidostromataceae']),
         ('Atheliales',['Atheliaceae']),
         ('Stereopsidales',['Stereopsidaceae']),
         ('Septobasidiales',['Septobasidiaceae']),
         ('Symbiotaphrinales',['Symbiotaphrina']),
         ('Caliciales',['Sphaerophoraceae']),
         ('Sarrameanales',['Sarrameanaceae']),
         ('Trapeliales',['Trapeliaceae']),
         ('Halosphaeriales',['Halosphaeriaceae']),
         ('Abrothallales',['Abrothallus']),
         ('Arctomiales',['Arctomiaceae']),
         ('Hymeneliales',['Hymeneliaceae']),
         ('Leprocaulales',['Leprocaulaceae']),
         ('Loxosporales',['Loxospora']),  #Hodkinson and Lendemer 2011
     ]:
        for family in families:
            some_claims.append(Has_child(order, family, h2007_fam))
    if not make_claims(tax, some_claims):
        print '** one or more claims failed'
    test_claims(tax, some_claims, windy=True)

def load_713():
    study713 = Taxonomy.getTaxonomy('tax/713/', 'study713')
    return study713

def load_ncbi():
    ncbi = Taxonomy.getTaxonomy('tax/ncbi/', 'ncbi')
    fix_SAR(ncbi)

    ncbi.taxon('Viridiplantae').rename('Chloroplastida')
    patch_ncbi(ncbi)

    # analyzeOTUs sets flags on questionable taxa ("unclassified",
    #  hybrids, and so on) to allow the option of suppression downstream
    ncbi.analyzeOTUs()

    return ncbi

def patch_ncbi(ncbi):

    # New NCBI top level taxa introduced circa July 2014
    for toplevel in ["Viroids", "other sequences", "unclassified sequences"]:
        if ncbi.maybeTaxon(toplevel) != None:
            ncbi.taxon(toplevel).prune(this_source)

    # - Canonicalize division names (cf. skeleton) -
    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms these synonymies.
    ncbi.taxon('Glaucocystophyceae').rename('Glaucophyta')
    ncbi.taxon('Haptophyceae').rename('Haptophyta')

    ncbi.taxon('Viruses').hide()

    # - Touch-up -

    # RR 2014-04-12 #49
    ncbi.taxon('leotiomyceta').rename('Leotiomyceta')

    # RR #53
    ncbi.taxon('White-sloanea').synonym('White-Sloanea')

    # RR #56
    ncbi.taxon('sordariomyceta').rename('Sordariomyceta')

    # RR #52
    if ncbi.maybeTaxon('spinocalanus spinosus') != None:
        ncbi.taxon('spinocalanus spinosus').rename('Spinocalanus spinosus')
    if ncbi.maybeTaxon('spinocalanus angusticeps') != None:
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

    # Mark Holder https://github.com/OpenTreeOfLife/reference-taxonomy/issues/120
    ncbi.taxon('Cetartiodactyla').synonym('Artiodactyla')

    # Cody Howard https://github.com/OpenTreeOfLife/feedback/issues/57
    # http://dx.doi.org/10.1002/fedr.19971080106
    ncbi.taxon('Massonieae').take(ncbi.taxon('Resnova'))

    # From examining the deprecated OTU list.  This one occurs in study pg_188
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

    # Try to prevent confusion with GBIF genus Eucarya in Magnoliopsida
    ncbi.taxon('Eukaryota').notCalled('Eucarya')

    # JAR 2015-07-25 Prevent extinction of Myxogastria
    ncbi.taxon('Myxogastria').notCalled('Myxomycetes')

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/83
    # https://dx.doi.org/10.1007/s10764-010-9443-1 paywalled
    ncbi.taxon('Tarsius syrichta').rename('Carlito syrichta')

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/84
    ncbi.taxon('Tarsius bancanus').rename('Cephalopachus bancanus')

    # Yan Wong 2015-10-10 http://www.iucnredlist.org/details/22732/0
    uncia = ncbi.taxon('Uncia uncia')
    uncia.rename('Panthera uncia')
    ncbi.taxon('Panthera', 'Felidae').take(uncia)

    # Marc Jones 2015-10-10 https://groups.google.com/d/msg/opentreeoflife/wCP7ervK8YE/DbbonQtkBQAJ
    ncbi.taxon('Sphenodontia', 'Sauropsida').rename('Rhynchocephalia')

    # Chris Owen patches 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/88
    # NCBI puts Chaetognatha in Deuterostomia.
    ncbi.taxon('Protostomia').take(ncbi.taxonThatContains('Chaetognatha','Sagittoidea'))

def load_worms():
    worms = Taxonomy.getTaxonomy('tax/worms/', 'worms')
    worms.smush()

    worms.taxon('Viruses').prune("taxonomies.py")

    worms.taxon('Biota').rename('life')
    worms.taxon('Animalia').synonym('Metazoa')

    fix_basal(worms)

    # 2015-02-17 According to WoRMS web site.  Occurs in pg_1229
    if worms.maybeTaxon('Scenedesmus communis') != None:
        worms.taxon('Scenedesmus communis').synonym('Scenedesmus caudata')

    # See NCBI
    worms.taxon('Millericrinida').extant()

    # Species fungorum puts this species in Candida
    worms.taxon('Trichosporon diddensiae').rename('Candida diddensiae')

    # Help to match up with IRMNG
    worms.taxon('Ochrophyta').synonym('Heterokontophyta')

    worms.smush()  # Gracilimesus gorbunovi, pg_1783

    return worms

def load_gbif():
    gbif = Taxonomy.getTaxonomy('tax/gbif/', 'gbif')
    gbif.smush()

    # In GBIF, if a rank is skipped for some children but not others, that
    # means the rank-skipped children are incertae sedis.  Mark them so.
    gbif.analyzeMajorRankConflicts()

    fix_basal(gbif)  # creates a Eukaryota node
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
    claims = [
        Whether_same('Chryso-hypnum', 'Chryso-Hypnum', True),
        Whether_same('Drepano-Hypnum', 'Drepano-hypnum', True),
        Whether_same('Complanato-Hypnum', 'Complanato-hypnum', True),
        Whether_same('Leptorrhyncho-Hypnum', 'Leptorrhyncho-hypnum', True),
        
        # Doug Soltis 2015-02-17 https://github.com/OpenTreeOfLife/feedback/issues/59 
        # http://dx.doi.org/10.1016/0034-6667(95)00105-0
        Whether_extant('Timothyia', False, 'https://github.com/OpenTreeOfLife/feedback/issues/59'),

    ]
    make_claims(gbif, claims)
    # See new versions above
    # gbif.taxon('Chryso-hypnum').absorb(gbif.taxon('Chryso-Hypnum'))
    # gbif.taxon('Complanato-Hypnum').rename('Complanato-hypnum')
    # gbif.taxon('Leptorrhyncho-Hypnum').rename('Leptorrhyncho-hypnum')

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
        claim = Whether_same('Cyrto-Hypnum ' + epithet, 'Cyrto-hypnum ' + epithet, True,
                             'https://github.com/OpenTreeOfLife/reference-taxonomy/issues/45')
        claim.make_true(gbif)
        # was gbif.taxon('Cyrto-hypnum ' + epithet).absorb(gbif.taxon('Cyrto-Hypnum ' + epithet))

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
                 # 'Mycosphaeroides'  - gone
             ]:
        claim = Whether_extant(name, False, 'https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116')
        claim.make_true(gbif)

    # JAR 2014-07-18  - get rid of Helophorus duplication
    # GBIF 3263442 = Helophorus Fabricius, 1775, from CoL
    #    in 6985 = Helophoridae Leach, 1815
    # GBIF 6757656 = Helophorus Leach, from IRMNG homonym list
    #    in 7829 = Hydraenidae Mulsant, 1844
    #  ('Helophorus', 'Helophoridae') ('Helophorus', 'Hydraenidae')
    gbif.taxon('3263442').absorb(gbif.taxon('6757656'))

    # JAR 2015-06-27  there are two Myospalax myospalax
    # The one in Spalacidae is the right one, Myospalacinae is the wrong one
    # (according to NCBI)
    # Probably should clean up the whole genus
    # 2439121 = Myospalax myospalax (Laxmann, 1773) from CoL
    gbif.taxon('2439121').absorb(gbif.taxon('6075534'))

    # 4010070	pg_1378	Gelidiellaceae	gbif:8998  -- ok, paraphyletic

    # https://github.com/OpenTreeOfLife/feedback/issues/64
    gbif.taxon('Plagiomene').extinct()

    # https://github.com/OpenTreeOfLife/feedback/issues/65
    gbif.taxon('Worlandia').extinct()

    tip = gbif.maybeTaxon('6101461') # ('Tipuloidea', 'Hemiptera') gbif:6101461 - extinct
    if tip != None:
        tip.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    oph = gbif.taxon('4872240')  # ('Ophiurina', 'Ophiurinidae') gbif:4872240 - extinct
    if oph != None:
        oph.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    # 2015-07-25 Extra Dipteras are confusing new division logic.  Barren genus
    gbif.taxon('3230674').prune(this_source)

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/72
    gbif.taxon('Myeladaphus').extinct()

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/78
    gbif.taxon('Oxyprinichthys').extinct()

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/82
    gbif.taxon('Tarsius thailandica').extinct()

    return gbif

def load_irmng():
    irmng = Taxonomy.getTaxonomy('tax/irmng/', 'irmng')
    irmng.smush()
    irmng.analyzeMajorRankConflicts()

    irmng.taxon('Viruses').prune("taxonomies.py")

    fix_basal(irmng)
    irmng.taxon('Animalia').synonym('Metazoa')

    # JAR 2014-04-26 Flush all 'Unaccepted' taxa
    irmng.taxon('Unaccepted', 'life').prune(this_source)

    # Fixes

    # Neopithecus (extinct) occurs in two places.  Flush one, mark the other
    irmng.taxon('1413316').prune(this_source) #Neopithecus in Mammalia
    irmng.taxon('1413315').extinct() #Neopithecus in Primates (Pongidae)

    # RR #50
    # irmng.taxon('Saxo-Fridericia').rename('Saxofridericia')
    # irmng.taxon('Saxofridericia').absorb(irmng.taxon('Saxo-fridericia'))
    saxo = irmng.maybeTaxon('1063899')
    if saxo != None:
        saxo.absorb(irmng.taxon('1071613'))

    # JAR 2015-06-28
    # The synonym Ochrothallus multipetalus -> Niemeyera multipetala
    # is no good; it interferes with correct processing of Ochrothallus 
    # multipetalus.  We could remove the synonym, but instead remove its 
    # target because no synonym-removal command is available.
    irmng.taxon('Niemeyera multipetala').prune(this_source)

    tip = irmng.taxon('Tipuloidea', 'Hemiptera')  # irmng:1170022
    if tip != None:
        tip.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    oph = irmng.taxon('1346026') # irmng:1346026 'Ophiurina', 'Ophiurinidae'
    if oph != None:
        oph.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    # NCBI synonymizes Pelecypoda = Bivalvia
    irmng.taxon('Bivalvia').absorb(irmng.taxon('Pelecypoda')) # bogus order
    # hmm
    irmng.taxon('Bivalvia').extant()

    # This one was mapping to Blattodea, and making it extinct.
    # Caused me a couple of hours of grief.
    # My guess is it's because its unique child Sinogramma is in Blattodea in GBIF.
    # Wikipedia says it's paraphyletic.
    irmng.taxon('Blattoptera', 'Insecta').prune('https://en.wikipedia.org/wiki/Blattoptera')

    # 2015-07-25 Found while trying to figure out why Theraphosidae was marked extinct.
    # NCBI thinks that Theraphosidae and Aviculariidae are the same.
    irmng.taxon('Aviculariidae').extant()

    # 2015-07-25 Extra Dipteras are confusing new division logic.  Barren genus
    irmng.taxon('1323521').prune(this_source)

    # 2015-09-10 This one is unclassified (Diptera) and is leading to confusion with two other Steinias.
    irmng.taxon('1299622').prune(this_source)

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/74
    # Lymnea is a snail, not a shark
    irmng.taxon('1317416').prune(this_source)

    # 2015-10-12 JAR checked IRMNG online and this taxon (Ctenophora in Chelicerata) did not exist
    if irmng.maybeTaxon('1279363') != None:
        irmng.taxon('1279363').prune(this_source)

    return irmng

# Common code for the 'old fashioned' taxonomies IF, GBIF, IRMNG,
# WoRMS - important for getting divisions in the right place, which is
# important for homonym separation.

def fix_basal(tax):
    fix_protists(tax)
    fix_SAR(tax)
    fix_plants(tax)

# Common code for GBIF, IRMNG, Index Fungorum
def fix_protists(tax):
    euk = tax.maybeTaxon('Eukaryota')
    if euk == None:
        euk = tax.newTaxon('Eukaryota', 'domain', 'ncbi:2759')
    co = tax.maybeTaxon('cellular organisms')
    if co != None: co.take(euk)
    else:
        li = tax.maybeTaxon('life')
        if li != None: li.take(euk)
        else:
            print('* No parent "life" for Eukaryota, probably OK')

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

    fix_SAR(tax)

# Add SAR and populate it

def fix_SAR(tax):
    sar = tax.maybeTaxon('SAR')
    if sar == None:
        euk = tax.taxon('Eukaryota')
        s = tax.maybeTaxon('Stramenopiles')
        if s == None:
            s = tax.maybeTaxon('Heterokonta')
        a = tax.maybeTaxon('Alveolata')
        r = tax.maybeTaxon('Rhizaria')
        if s != None or a != None or r != None:
            sar = tax.newTaxon('SAR', None, 'silva:E17133/#2')
            euk.take(sar)
            if s != None: sar.take(s)
            else: print '** No Stramenopiles'
            if a != None: sar.take(a)
            else: print '** No Alveolata'
            if r != None: sar.take(r)
            else: print '** No Rhizaria'

# 'Fix' plants to match SILVA and NCBI...
def fix_plants(tax):
    euk = tax.taxon('Eukaryota')

    # 1. co-opt GBIF taxon for a slightly different purpose
    plants = tax.maybeTaxon('Plantae')
    if plants != None:
        plants.rename('Chloroplastida')
        # euk.take(plants)  ???

    if euk != None:
        # JAR 2014-04-25 GBIF puts rhodophytes under Plantae, but it's not
        # in Chloroplastida.
        # Need to fix this before alignment because of division calculations.
        # Plantae is a child of 'life' in GBIF, and Rhodophyta is one of many
        # phyla below that.  Move up to be a sibling of Plantae.
        # This redefines GBIF Plantae, sort of, which is not nice.
        # Discovered while looking at Bostrychia alignment problem.
        # JAR 2014-05-13 similarly
        # Glaucophyta - there's a GBIF/IRMNG false homonym, should be merged
        # This needs to match the skeleton!
        for name in ['Rhodophyta', 'Glaucophyta', 'Haptophyta', 'Fungi', 'Metazoa', 'Chloroplastida']:
            # should check to see if this is an improvement in specificity?
            rho = tax.maybeTaxon(name)
            if rho != None:
                euk.take(rho)
