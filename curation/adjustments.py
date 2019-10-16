# coding=utf-8

from proposition import *
from establish import establish

from org.opentreeoflife.taxa import Rank

this_source = 'https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/taxonomies.py'

# Don't change the otc() ids!

# ----- Difficult polysemies -----

def deal_with_polysemies(ott):
    # Ctenophora is seriously messing up the division logic.
    # ncbi 1003038	|	33856	|	Ctenophora	|	genus	|	= diatom        OTT 103964
    # ncbi 10197 	|	6072	|	Ctenophora	|	phylum	|	= comb jellies  OTT 641212
    # ncbi 516519	|	702682	|	Ctenophora	|	genus	|	= cranefly      OTT 1043126

    # The comb jellies are already in the taxonomy at this point (from
    # separation taxonomy).

    # Add the diatom to OTT so that SILVA has something to map its diatom to
    # that's not the comb jellies.

    # To do this without creating a sibling-could homonym, we have to create
    # a place to put it.  This will be rederived from SILVA soon enough.
    establish('Bacillariophyta', ott, division='SAR', ott_id='5342311')

    # Diatom.  Contains e.g. Ctenophora pulchella.
    establish('Ctenophora', ott, ancestor='Bacillariophyta', ott_id='103964')

    # The comb jellies should already be in separation, but include the code for symmetry.
    # Contains e.g. Leucothea multicornis
    establish('Ctenophora', ott, parent='Metazoa', ott_id='641212')

    # The fly will be added by NCBI; provide a node to map it to.
    # Contains e.g. Ctenophora dorsalis
    establish('Ctenophora', ott, division='Diptera', ott_id='1043126')

    establish('Podocystis', ott, division='Fungi', ott_id='809209')
    establish('Podocystis', ott, parent='Bacillariophyta', ott_id='357108')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/198
    establish('Euxinia', ott, division='Metazoa', source='ncbi:100781', ott_id='476941') #flatworm
    establish('Euxinia', ott, division='Metazoa', source='ncbi:225958', ott_id='329188') #amphipod

    # Discovered via failed inclusion test
    establish('Campanella', ott, division='SAR', source='ncbi:168241', ott_id='136738') #alveolata
    establish('Campanella', ott, division='Fungi', source='ncbi:71870', ott_id='5342392')    #basidiomycete

    # Discovered via failed inclusion test
    establish('Diphylleia', ott, division='Eukaryota',      source='ncbi:177250', ott_id='4738987') #apusozoan
    establish('Diphylleia', ott, division='Chloroplastida', source='ncbi:63346' , ott_id='570408') #eudicot

    # Discovered via failed inclusion test
    establish('Epiphloea', ott, division='Fungi',      source='if:1869', ott_id='5342482') #lichinales
    establish('Epiphloea', ott, division='Rhodophyta', source='ncbi:257604', ott_id='471770')  #florideophycidae

    # Discovered on scrutinizing the log.  There's a third one but it gets
    # separated automatically
    establish('Morganella', ott, division='Bacteria', source='ncbi:581', ott_id='524780') #also in silva
    establish('Morganella', ott, division='Fungi',    source='if:19222', ott_id='973932')

    # Discovered from inclusions test after tweaking alignment heuristics.
    establish('Cyclophora', ott, division='SAR',         source='ncbi:216819', ott_id='678569') #diatom
    establish('Cyclophora', ott, division='Lepidoptera', source='ncbi:190338', ott_id='1030079') #moth
    # there are two more Cyclophora/us but they take care of themselves

    # Discovered after update to GBIF 2016, which introduced an orthoptera genus by this name
    establish('Lutheria', ott, division='Platyhelminthes', source='worms:479527', ott_id='5131356')  # flatworm
    establish('Lutheria', ott, division='Insecta', source='gbif:7978890')  # orthopteran

    # not sure why this is needed.
    establish('Stereopsidaceae', ott, division='Fungi', source='gbif:7717211')

    # 3.0draft5 problem
    establish('Corymorpha', ott, division='Cnidaria', source='ncbi:264057', ott_id='183501')
    establish('Corymorpha', ott, division='Nematoda', source='ncbi:364543', ott_id='860265')

def adjust_silva(silva):
    patch_silva(silva)
    for name in ['Metazoa',
                 'Fungi',
                 'Chloroplast',
                 'mitochondria',
                 'Herdmania',
                 'Oryza',
                 'Chloroplastida']:
        print '| trimming', name, silva.taxon(name).count()
        silva.taxon(name).trim()
    return silva

def patch_silva(silva):

    # Used in studies pg_2448,pg_2783,pg_2753, seen deprecated on 2015-07-20
    # These are probably now obviated by improvements in the way silva is merged
    proclaim(silva,
             synonym_of(taxon('AF364847'),
                        taxon('Pantoea ananatis LMG 20103'),
                        'synonym', # relationship unknown
                        otc(1)))

    if silva.maybeTaxon('AF364847') != None:
        silva.taxon('AF364847').rename('Pantoea ananatis LMG 20103')    # ncbi:706191
    if silva.maybeTaxon('EF690403') != None:
        silva.taxon('EF690403').rename('Pantoea ananatis B1-9')  # ncbi:1048262

    for (anum, name) in silva_bad_names:
        tax = silva.maybeTaxon(anum)
        if tax != None:
            # was 'Not ' + name
            if tax.name == name:
                tax.clobberName('cluster ' + anum)
            else:
                print '** unexpected name %s for %s, expected %s' % (tax.name, anum, name)

    loser = silva.maybeTaxon('Crenarchaeota','Crenarchaeota')
    if loser != None:
        loser.prune(this_source)

    # - Deal with parent/child homonyms in SILVA -
    # Arbitrary choices here to eliminate ambiguities down the road when NCBI gets merged.
    # (If the homonym is retained, then the merge algorithm will have no
    # way to choose between them, and refuse to match either.  It will
    # then create a third homonym.)
    # Note order dependence between the following two
    silva.taxon('Intramacronucleata','Intramacronucleata').clobberName('Intramacronucleata inf.')
    silva.taxon('Spirotrichea','Intramacronucleata inf.').clobberName('Spirotrichea inf.')
    silva.taxonThatContains('Cyanobacteria','Chloroplast').clobberName('Cyanobacteria/Melainabacteria group')
    # this one is funny, NCBI really wants it to be a parent/child homonym
    silva.taxonThatContains('Actinobacteria','Acidimicrobiia').clobberName('Actinobacteraeota')
    # Change the inferior Acidobacteria to -iia.  Designation is fragile!
    silva.taxon('D26171/#3').clobberName('Acidobacteriia')
    # This is fixed in a later version of SILVA
    silva.taxonThatContains('Ochromonas','Dinobryon').clobberName('Ochromonas sup.')
    silva.taxon('Tetrasphaera','Tetrasphaera').clobberName('Tetrasphaera inf.')

    # SILVA's placement of Rozella as a sibling of Fungi is contradicted
    # by Hibbett 2007, which puts it under Fungi.  Hibbett gets priority.
    # We make the change to SILVA to prevent an inconsistency.
    silva.taxon('Fungi').take(silva.taxon('Rozella'))

    def fix_name(bad, good):
        b = silva.maybeTaxon(bad)
        if b != None: b.rename(good, 'spelling variant')
        else:
            g = silva.maybeTaxon(good)
            if g != None: g.synonym(bad, 'spelling variant')

    # 2014-04-12 Rick Ree #58 and #48 - make them match NCBI
    fix_name('Arthrobacter Sp. PF2M5', 'Arthrobacter sp. PF2M5')
    fix_name('Halolamina sp. wsy15-h1', 'Halolamina sp. WSY15-H1')
    # RR #55 - this is a silva/ncbi homonym
    fix_name('vesicomya', 'Vesicomya')

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

    # 2016-06-15 after updating silva taxon names to latest NCBI.
    # The Ex25 name disappeared form NCBI taxonomy.
    # Identity with Vibrio antiquarius made on the basis of genbank id.
    # Taxon is used in a study.
    silva.taxon('CP001805').synonym('Vibrio sp. Ex25')
    # similarly
    silva.taxon('CP002976').synonym('Phaeobacter gallaeciensis DSM 17395 = CIP 105210')

    # Nonspecific Pseudomonas, from a patent
    # https://github.com/OpenTreeOfLife/feedback/issues/309
    silva.taxon('HC510467').clobberName('cluster HC510467')
    silva.taxon('HC510467').hide()

    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms this synonymy.  Dail L. prefers -phyta to -phyceae
    # but says -phytina would be more correct per code.
    # Separation taxonomy has -phyta (on Dail's advice).
    silva.taxon('Rhodophyceae').synonym('Rhodophyta')    # moot now?

    silva.taxon('Florideophycidae', 'Rhodophyceae').synonym('Florideophyceae')

    # JAR 2016-07-29 I could find no argument supporting the name
    # 'Choanomonada' as a replacement for 'Choanoflagellida'.  I
    # suggested to the SILVA folks that they change the name.
    silva.taxon('Choanomonada', 'Opisthokonta').rename('Choanoflagellida')
    silva.taxon('Choanomonada', 'Opisthokonta').synonym('Choanoflagellatea') #wikipedia

# Sample contamination ?
# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/201
silva_bad_names = [
    ('JX888097', 'Trichoderma harzianum'),
    ('JU096348', 'Sclerotinia homoeocarpa'),
    ('JU096305', 'Sclerotinia homoeocarpa'),
    ('HP451821', 'Puccinia triticina'),
    ('HP459251', 'Puccinia triticina'),
    ('ACJG01014252', 'Daphnia pulex'),
    ('ABAV01034229', 'Nematostella vectensis'),  #Excavata should be Opisth.
    ('JT491765', 'Ictalurus punctatus'),

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/104
    ('JN975069', 'Caenorhabditis elegans'),

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/100
    ('AEKE02005637', 'Solanum lycopersicum'),
    ('BABP01087923', 'Solanum lycopersicum'),    # and 22 others...

    # These were formerly in process_silva.py
    ('ABEG02010941', 'Caenorhabditis brenneri'),
    ('ABRM01041397', 'Hydra magnipapillata'),
    ('ALWT01111512', 'Myotis davidii'),  # bat; SILVA puts this in SAR
    ('HP641760', 'Alasmidonta varicosa'), # bivalve; SILVA puts it in SAR
    ('JR876587', 'Embioptera sp. UVienna-2012'),

    # Via compare_ncbi_to_silva
    ('AEKZ01001951', 'Apis florea'),
    ('AMGZ01279395', 'Elephantulus edwardii'),
    ('CACX01001880', 'Strongyloides ratti'),
    ('EU221512', 'Trachelomonas grandis'),
    ('ABJB010370606', 'Ixodes scapularis'),
    ('JU112734', 'Agrostis stolonifera'),
    ('CU179746', 'Danio rerio'),
    ('ABKP02007643', 'Anopheles gambiae M'),
    ('AY833572', 'Bemisia tabaci'),
    ('ABRR02151433', 'Vicugna pacos'),
    ('CQ786497', 'Crenarchaeota'),
    ('ADMH01000290', 'Anopheles darlingi'),
    ('AAGD02009939', 'Caenorhabditis remanei'),
    ('JT042258', 'Stegodyphus tentoriicola'),
    ('JW107284', 'Capsicum annuum'),
    ('JL594206', 'Ophthalmotilapia ventralis'),
    ('JV125402', 'Aiptasia pallida'),

    # Phylesystem outlier check
    ('A16379', 'Trachelomonas grandis'),   # euglenida
]

# ----- SILVA -----

def align_silva(silva, ott):
    a = ott.alignment(silva)
    a.same(silva.taxonThatContains('Ctenophora', 'Ctenophora pulchella'),
           ott.taxon('103964'))
    #a.same(silva.taxonThatContains('Ctenophora', 'Beroe ovata'),
    #       ott.taxon('641212'))

    # 2016-11-20 Force matches with NCBI and WoRMS Protaspis
    # WoRMS says: "Protapsa Cavalier-Smith in Howe et al., 2011 was proposed
    # to replace Protaspis Skuja, 1939 under the ICZN"
    silva.taxon('Protaspa', 'Rhizaria').synonym('Protaspis')

    # From Laura and Dail on 5 Feb 2014
    # https://groups.google.com/forum/#!topic/opentreeoflife/a69fdC-N6pY
    a.same(silva.taxon('Diatomea'), ott.taxon('Bacillariophyta'))

    return a

# Hibbett 2007

def adjust_h2007(h2007):

    # h2007/if synonym https://github.com/OpenTreeOfLife/reference-taxonomy/issues/40
    h2007.taxon('Urocystales').synonym('Urocystidales')

    for t in h2007.taxa():
        if t.name != None and t.name.endswith('ales') and t.rank == Rank.NO_RANK:
            t.setRank('order')

    return h2007

# Index Fungorum

def adjust_fung(fung):
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
    # (repeats - see adjust_fung()  ???)
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

    fung.taxon('Asterinales').synonym('Asteriniales', 'spelling variant')  #backward compatibility

    # ** No taxon found with this name: Nowakowskiellaceae
    # ** No taxon found with this name: Septochytriaceae
    # ** No taxon found with this name: Jaapiaceae
    # ** (null=if:81865 Rhizocarpaceae) is already a child of (null=h2007:212 Rhizocarpales)
    # ** No taxon found with this name: Hyaloraphidiaceae

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
        #cetes.setRank(None)
        #tina = fung.taxon('Saccharomycetaceae', 'Fungi')
        #if tina != None:
        #    tina.take(fung.taxon('Saccharomycetes', 'Fungi'))
        #cetes.extant()

    # JAR 2015-07-20 - largest unattached subtree was Microeccrina, which is dubious.
    fung.taxon('Microeccrina').prune('http://www.nhm.ku.edu/~fungi/Monograph/Text/chapter12.htm')

    # JAR 2015-09-10 on perusing a long list of equivocal homonyms
    # (weaklog.csv).  Hibbett 2007 and NCBI put Microsporidia in Fungi.
    fung.taxon('Fungi').take(fung.taxon('Microsporidia'))

    # 2015-09-15 Pezizomycotina has no parent pointer; without Fungi as a barrier,
    # the placement of genus Onychophora is screwed up.
    # Found while investigating https://github.com/OpenTreeOfLife/feedback/issues/88
    if fung.taxon('Pezizomycotina').isRoot():
        fung.taxon('Ascomycota').take(fung.taxon('Pezizomycotina'))

    # 2015-10-06 JAR noticed while debugging deprecated taxa list:
    # This should cover Basidiomycota, Zygomycota, Glomeromycota, and Ascomycota
    # 2016-08-30 This should be no longer needed (but innocuous)
    for taxon in fung.taxa():
        if taxon.getRank() == 'phylum' and taxon.isRoot():
            fung.taxon('Fungi').take(taxon)

    # 2015-10-06 https://en.wikipedia.org/wiki/Taphrinomycotina
    if fung.taxon('Taphrinomycotina').isRoot():
        fung.taxon('Ascomycota').take(fung.taxon('Taphrinomycotina'))
        if fung.taxon('Saccharomycotina').isRoot():
            fung.taxon('Ascomycota').take(fung.taxon('Saccharomycotina'))

    # 2016-09-01 This is unplaced and keeps getting erroneously mapped to
    # Rhodophyta, creating duplicates (it's really a Chlorophyte)
    fung.taxonThatContains('Byssus', 'Byssus rufa').prune(this_source)

    # Nudge to alignment with IRMNG (unclassified fossil Fungi)
    # IF 585158 = IRMNG 1090915
    fung.taxon('Fungi').take(fung.taxon('Majasphaeridium'))

    # 2017-02-13 https://github.com/OpenTreeOfLife/opentree/issues/773
    fung.taxon('Dactylella ambrosia').notCalled('Fusarium ambrosium')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/301
    # An Ichthyosporean variously misclassified as a fungus or an oomyocote.
    # Fixed by NCBI.
    if fung.maybeTaxon('329819') != None:
        fung.taxon('329819').prune()  # Dermocystidium salmonis
    if fung.maybeTaxon('483212') != None:
        fung.taxon('483212').prune()  # Ostracoblabe salmonis

    # 2017-03-26 Index Fungorum has Chondromyces crocatus Berk. &
    # M.A. Curtis 1874 incertae sedis in Fungi, but this is wrong;
    # it's actually in Bacteria (according to GBIF).  Just flush the
    # IF record.
    chon = fung.maybeTaxon('Chondromyces', 'Fungi')
    if chon != None: chon.prune()

    print "Fungi in Index Fungorum has %s nodes"%fung.taxon('Fungi').count()

# tax = ott

def link_to_h2007(tax):
    print '-- Putting families in Hibbett 2007 orders --'
    # 2014-04-13 Romina #40, #60

    # Stereopsidaceae = Stereopsis + Clavulicium
    s = tax.taxon('Stereopsidaceae', 'Fungi')
    s.take(tax.taxon('Stereopsis', 'Agaricomycetes'))
    s.take(tax.taxon('Clavulicium', 'Agaricomycetes'))
    # Dangling node at this point, but will be attached below

    establish('Moniliellaceae', tax, parent='Moniliellales',
              division='Fungi', source='ncbi:1538067')
    establish('Lepidostromataceae', tax, parent='Lepidostromatales',
              division='Fungi', source='ncbi:579912')
    # In OTT 3.0 and Index Fungorum, Trichotheliaceae is listed as a
    # synonym for Porinaceae.  But it seems to be accepted in Mycobank,
    # and was given as accepted by Romina.
    # Not sure how the two relate.

    # To do next: put some genera in these families

    # e.g. according to mycobank, Trichotheliaceae contains
    # Actiniopsis, Actinopsis, Asteropeltis, Cryptopeltis,
    # Ophiodictyon, Ophthalmidium, Phragmopeltheca, Phyllophiale,
    # Polycornum, Sagedia, Segestrella, Sphaeromphale, Stephosia,
    # Stereochlamydomyces, Stereochlamys, Stereoclamydomyces,
    # Trichotheliomyces, Zamenhofia

    # 2015-07-13 Romina
    for (family, order, sid) in \
        [
         ('Neozygitaceae', 'Neozygitales', None),
         ('Asterinaceae', 'Asterinales', None),
         ('Savoryella', 'Savoryellales', None),
         ('Ascotaiwania', 'Savoryellales', None),
         ('Ascothailandia', 'Savoryellales', None),
         ('Cladochytriaceae', 'Cladochytriales', None),
         ('Nowakowskiellaceae', 'Cladochytriales', None),
         ('Septochytriaceae', 'Cladochytriales', None),
         ('Endochytriaceae', 'Cladochytriales', None),
         ('Jaapiaceae', 'Jaapiales', None),
         ('Coniocybaceae', 'Coniocybales', None),
         # was Hyaloraphidiaceae, no such family - Hyaloraphidium is genus
         ('Hyaloraphidium', 'Hyaloraphidiales', None),
         ('Mytilinidiaceae', 'Mytilinidiales', None),
         ('Gloniaceae', 'Mytilinidiales', None),

         ('Talbotiomyces calosporus', 'Talbotiomycetales', otc(3)),
         # Moniliellaceae = ncbi:1538067,gbif:8375337
         ('Moniliellaceae', 'Moniliellales', otc(4)),
         ('Malasseziaceae', 'Malasseziales', otc(5)),   # , 'Malassezia' - redundant
         # Trichotheliaceae missing, order barren  mb:81496
         # Romina had: ('Trichotheliaceae', 'Trichotheliales', otc(6)),
         ('Porinaceae', 'Trichotheliales', None),
         ('Myeloconidiaceae', 'Trichotheliales', otc(7)),
         ('Sporobolomyces ruberrimus', 'Trichosporonales', otc(8)),
         ('Holtermanniella', 'Holtermanniales', otc(9)),
         # Lepidostromataceae = ncbi:579912,gbif:8295976
         ('Lepidostromataceae', 'Lepidostromatales', otc(10)),
         ('Atheliaceae', 'Atheliales', otc(11)),
         # why is Stereopsidales childless?
         ('Stereopsidaceae', 'Stereopsidales', otc(12)),
         ('Septobasidiaceae', 'Septobasidiales', otc(13)),
         # why is Symbiotaphrinales barren?
         ('Symbiotaphrina', 'Symbiotaphrinales', otc(14)),
         # why is Caliciales barren?
         ('Sphaerophoraceae', 'Caliciales', otc(15)),
         ('Sarrameanaceae', 'Sarrameanales', otc(16)),
         ('Trapeliaceae', 'Trapeliales', otc(17)),
         ('Halosphaeriaceae', 'Halosphaeriales', otc(18)),
         ('Abrothallus', 'Abrothallales', otc(19)),
         ('Arctomiaceae', 'Arctomiales', otc(20)),
         # why is Hymeneliales barren?
         # Possible casualty of changes to merge method?
         ('Hymeneliaceae', 'Hymeneliales', otc(21)),
         ('Leprocaulaceae', 'Leprocaulales', otc(22)),
         # why is Loxosporales barren?
         ('Loxospora', 'Loxosporales', otc(23)),  #Hodkinson and Lendemer 2011
         ]:
        proclaim(tax, has_parent(taxon(family), taxon(order), sid))

# ----- Index Fungorum -----
# IF is pretty comprehensive for Fungi, but has an assortment of other
# things, mostly eukaryotic microbes.  We should treat the former as
# more authoritative than NCBI, and the latter as less authoritative
# than NCBI.

def align_fungi(fungi, ott):
    a = ott.alignment(fungi)

    # *** Alignment to SILVA

    # 2014-03-07 Prevent a false match between Phaeosphaerias
    # https://groups.google.com/d/msg/opentreeoflife/5SAPDerun70/fRjA2M6z8tIJ
    # One is a fungus in Pezizomycotina, the other a rhizarian
    # Now handled automatically.  Checked in inclusions.csv

    # 2014-04-08 Bad Morganella match was causing Agaricaceae to be paraphyletic
    # Checked in inclusions.csv

    # 2014-04-08 More IF/SILVA bad matches
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/63
    for (name, f, o) in [('Acantharia', 'Fungi', 'Rhizaria'),   # fungus if:8 is nom. illegit. but it's also in gbif
                         ('Lacrymaria', 'Fungi', 'Alveolata'),
                         ('Steinia', 'Fungi', 'Alveolata'),   # also insect < Holozoa in irmng
                         #'Epiphloea',      # in Pezizomycotina < Opisth. / Rhodophyta  should be OK, Rh. is a division
                         #'Campanella',     # in Agaricomycotina < Nuclet. / SAR / Cnidaria
                         #'Bogoriella',     # in Verrucariaceae < Pezizomycotina < Euk. / Bogoriellaceae < Bacteria  should be ok
    ]:
        tax1 = fungi.maybeTaxon(name, f)
        if tax1 == None:
            print '** no %s in IF' % name # 'no Acantharia in IF'
        else:
            a.same(tax1, establish(name, ott, ancestor=f))

    # 2014-04-25 JAR
    # There are three Bostrychias: a rhodophyte, a fungus, and a bird.
    # The fungus name is a synonym for Cytospora.
    # Now handled automatically, see inclusions.csv

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/20
    # Problem: Chlamydotomus is an incertae sedis child of Fungi.  Need to
    # find a good home for it.
    #
    # Mycobank says Chlamydotomus beigelii = Trichosporon beigelii:
    # http://www.mycobank.org/BioloMICS.aspx?Link=T&TableKey=14682616000000067&Rec=35058&Fields=All
    #
    # IF says the basionym is Pleurococcus beigelii, and P. beigelii's current name
    # is Geotrichum beigelii.  IF says the type for Trichosporon is Trichosporon beigelii,
    # and that T. beigelii's current name is Trichosporum beigelii... with no synonymies...
    # So IF does not corroborate Mycobank.
    #
    # So we could consider absorbing Chlamydotomus into Trichosoporon.  But...
    #
    # Not sure about this.  beigelii has a sister, cellaris, that should move along
    # with it, but the name Trichosporon cellaris has never been published.
    # Cb = ott.taxon('Chlamydotomus beigelii')
    # Cb.rename('Trichosporon beigelii')
    # ott.taxon('Trichosporon').take(Cb)
    #
    # Just make it incertae sedis and put off dealing with it until someone cares...

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/79
    # As of 2016-06-05, the fungus has changed its name to Melampsora, so there is no longer a problem.
    a.same(fungi.taxon('Podocystis', 'Fungi'), ott.taxon('Podocystis', 'Fungi'))

    # Create a homonym (the one in Fungi, not the same as the one in Alveolata)
    # so that the IF Ciliophora can map to it
    establish('Ciliophora', ott, ancestor='Fungi', rank='genus', source='if:7660', ott_id='5343665')
    a.same(fungi.taxon('Ciliophora', 'Fungi'), ott.taxon('Ciliophora', 'Fungi'))

    # Create a homonym (the one in Fungi, not the same as the one in Rhizaria)
    # so that the IF Phaeosphaeria can map to it
    establish('Phaeosphaeria', ott, ancestor='Fungi', rank='genus', source='if:3951', ott_id='5486272')
    a.same(fungi.taxon('Phaeosphaeria', 'Fungi'), ott.taxon('Phaeosphaeria', 'Fungi'))

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    # Unfortunately Choanoflagellida is currently showing up as
    # inconsistent.
    if False:
        a.same(fungorum.maybeTaxon('Choanoflagellida'),
               ott.maybeTaxon('Choanoflagellida', 'Opisthokonta'))

    # 2017-03-26 http://dx.doi.org/10.1080/01916122.2002.9989566
    red = fungi.maybeTaxon('Reduviasporonites', 'Fungi')
    if red != None: ott.setDivision(red, 'Chloroplastida')

    return a

def align_fungorum_sans_fungi(sans, ott):
    a = ott.alignment(sans)
    if sans.maybeTaxon('Byssus') != None: # seems to have disappeared
        a.same(sans.taxon('Byssus'), ott.taxon('Trentepohlia', 'Chlorophyta'))
    a.same(sans.taxon('Achlya'), ott.taxon('Achlya', 'Stramenopiles'))
    return a

# ----- Lamiales taxonomy from study 713 -----
# http://dx.doi.org/10.1186/1471-2148-10-352

def adjust_lamiales(study713):
    return study713

def align_lamiales(study713, ott):
    a = ott.alignment(study713)
    # Without the explicit alignment of Chloroplastida, alignment thinks that
    # the study713 Chloroplastida cannot be the same as the OTT Chloroplastida,
    # because of something something something Buchnera (which is a
    # bacteria/plant polysemy).
    a.same(study713.taxon('Chloroplastida'), ott.taxon('Chloroplastida'))
    # Buchnera is also a hemihomonym with a bacterial genus.  Checked in inclusions.csv
    return a

# WoRMS

def adjust_worms(worms):
    worms.smush()

    # 2015-02-17 According to WoRMS web site.  Occurs in pg_1229
    if worms.maybeTaxon('Scenedesmus communis') != None:
        worms.taxon('Scenedesmus communis').synonym('Scenedesmus caudata')

    # Species fungorum puts this species in Candida
    worms.taxon('Trichosporon diddensiae').rename('Candida diddensiae')

    # https://github.com/OpenTreeOfLife/feedback/issues/194 I think
    worms.taxon('Actinopterygii').notCalled('Osteichthyes')

    worms.smush()  # Gracilimesus gorbunovi, pg_1783

    # According to NCBI and IF, this is not in Fungi
    worms.taxon('Fungi').parent.take(worms.taxon('Eccrinales'))

    # 2016-09-02 on gitter: Pisces vs. Mososauridae confusion
    worms.taxon('Tylosurus').notCalled('Tylosaurus')

    pl = worms.maybeTaxon('Pleuromamma abdominalis abyssalis natio hypothermophil')
    if pl != None: pl.prune()

    bad_ecc = worms.maybeTaxon('Trichomycetes', 'Zygomycota')
    if bad_ecc != None: bad_ecc.prune()

    return worms

# NCBI

def adjust_ncbi(ncbi):
    patch_ncbi(ncbi)
    return ncbi

def patch_ncbi(ncbi):

    # New NCBI top level taxa introduced circa July 2014
    for toplevel in ["Viroids", "other sequences", "unclassified sequences"]:
        if ncbi.maybeTaxon(toplevel) != None:
            ncbi.taxon(toplevel).prune(this_source)

    # - Canonicalize division names (cf. separation) -
    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms these synonymies.
    ncbi.taxon('Glaucocystophyceae').rename('Glaucophyta')
    ncbi.taxon('Haptophyceae').rename('Haptophyta')

    # - Touch-up -

    # RR 2014-04-12 #49
    ncbi.taxon('leotiomyceta').rename('Leotiomyceta', 'spelling variant')

    # RR #53
    ncbi.taxon('White-sloanea').synonym('White-Sloanea', 'spelling variant')

    # RR #56
    ncbi.taxon('sordariomyceta').rename('Sordariomyceta', 'spelling variant')

    # RR #52
    if ncbi.maybeTaxon('spinocalanus spinosus') != None:
        ncbi.taxon('spinocalanus spinosus').rename('Spinocalanus spinosus', 'spelling variant')
    if ncbi.maybeTaxon('spinocalanus angusticeps') != None:
        ncbi.taxon('spinocalanus angusticeps').rename('Spinocalanus angusticeps', 'spelling variant')

    # RR #59
    ncbi.taxon('candidate division SR1').rename('Candidate division SR1', 'spelling variant')
    ncbi.taxon('candidate division WS6').rename('Candidate division WS6', 'spelling variant')
    ncbi.taxon('candidate division BRC1').rename('Candidate division BRC1', 'spelling variant')
    ncbi.taxon('candidate division OP9').rename('Candidate division OP9', 'spelling variant')
    ncbi.taxon('candidate division JS1').rename('Candidate division JS1', 'spelling variant')

    # RR #51
    ncbi.taxon('Dendro-hypnum').synonym('Dendro-Hypnum', 'spelling variant')
    # RR #45
    ncbi.taxon('Cyrto-hypnum').synonym('Cyrto-Hypnum', 'spelling variant')
    # RR #54
    ncbi.taxon('Sciuro-hypnum').synonym('Sciuro-Hypnum', 'spelling variant')

    # RR 2014-04-12 #46
    ncbi.taxon('Pechuel-loeschea').synonym('Pechuel-Loeschea', 'spelling variant')

    # RR #50
    ncbi.taxon('Saxofridericia').synonym('Saxo-Fridericia', 'spelling variant')
    ncbi.taxon('Saxofridericia').synonym('Saxo-fridericia', 'spelling variant')

    # RR #57
    ncbi.taxon('Solms-laubachia').synonym('Solms-Laubachia', 'spelling variant')

    # Mark Holder https://github.com/OpenTreeOfLife/reference-taxonomy/issues/120
    ncbi.taxon('Cetartiodactyla').synonym('Artiodactyla')

    # Cody Howard https://github.com/OpenTreeOfLife/feedback/issues/57
    # http://dx.doi.org/10.1002/fedr.19971080106
    ncbi.taxon('Massonieae').take(ncbi.taxon('Resnova'))

    # From examining the deprecated OTU list.  This one occurs in study pg_188
    # Name got better, old name lost  JAR 2015-06-27
    # This could probably be automated, just by looking up the NCBI id
    # in the right table.  2016-11-15 This is probably not needed now that
    # ids are found via source ids.
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
    ncbi.taxon('Protostomia').take(ncbi.taxonThatContains('Chaetognatha', 'Sagittoidea'))

    # https://github.com/OpenTreeOfLife/feedback/issues/184
    h = ncbi.maybeTaxon('Hylobates alibarbis')
    if h != None:
        if ncbi.maybeTaxon('Hylobates albibarbis') != None:
            ncbi.taxon('Hylobates albibarbis').absorb(h)
        else:
            h.rename('Hylobates albibarbis', 'misspelling')

    # https://github.com/OpenTreeOfLife/feedback/issues/194
    ncbi.taxon('Euteleostomi').synonym('Osteichthyes')

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/...
    ncbi.taxon('Galeocerdo cf. cuvier GJPN-2012').prune(this_source)

    # 2016-06-30 JAR found these while reviewing list of subgenus names
    if ncbi.maybeTaxon('Festuca subg. Vulpia') != None:
        ncbi.taxon('Festuca subg. Vulpia').rename('Vulpia')
    if ncbi.maybeTaxon('Acetobacter subgen. Acetobacter') != None:
        ncbi.taxon('Acetobacter subgen. Acetobacter').rename('Acetobacter subgenus Acetobacter')
    if ncbi.maybeTaxon('Ophion (Platophion)') != None:    # gone at NCBI?
        ncbi.taxon('Ophion (Platophion)').rename('Platophion')
    # TBD: deal with 'Plasmodium (Haemamoeba)' and siblings

    # JAR 2016-07-29 Researched Choano* taxa a bit.  NCBI has a bogus synonymy with
    # paraphyletic Choanozoa.  SILVA calls this taxon Choanomonada (see above).
    ncbi.taxon('Choanoflagellida', 'Opisthokonta').notCalled('Choanozoa')

    # https://github.com/OpenTreeOfLife/feedback/issues/281
    # ncbi.taxon('Equisetopsida', 'Moniliformopses').take(ncbi.taxon('Equisetidae', 'Moniliformopses'))

    # https://github.com/OpenTreeOfLife/feedback/issues/278
    # NCBI Pinidae = Coniferales = Wikipedia Pinophyta = Coniferophyta
    ncbi.taxon('Pinidae', 'Acrogymnospermae').absorb(ncbi.taxon('Coniferopsida', 'Coniferophyta'))
    ncbi.taxon('Pinidae', 'Acrogymnospermae').absorb(ncbi.taxon('Coniferophyta', 'Acrogymnospermae'))

    # https://github.com/OpenTreeOfLife/feedback/issues/248
    ncbi.taxon('Acomys cahirinus').notCalled('Acomys airensis')

    # https://github.com/OpenTreeOfLife/feedback/issues/152
    ncbi.taxon('Selachii').synonym('Selachimorpha')

    # https://github.com/OpenTreeOfLife/feedback/issues/142
    ncbi.taxon('Aotus azarai').rename('Aotus azarae', 'misspelling')
    ncbi.taxon('Aotus azarai azarai').rename('Aotus azarae azarae', 'misspelling')
    ncbi.taxon('Aotus azarai infulatus').rename('Aotus azarae infulatus', 'misspelling')
    ncbi.taxon('Aotus azarai boliviensis').rename('Aotus azarae boliviensis', 'misspelling')

    # 2016-08-31 This matches SILVA, and was breaking Streptophyta.  Cluster EF023721
    capenv = ncbi.taxon('Caprifoliaceae environmental sample')
    ncbi.taxon('Eukaryota').take(capenv)
    capenv.unplaced()

# ----- NCBI Taxonomy -----

def align_ncbi(ncbi, silva, ott):

    a = ott.alignment(ncbi)

    for name in ['Stramenopiles', 'Alveolata', 'Rhizaria']:
        ott.setDivision(ncbi.taxon(name), 'SAR')

    a.same(ncbi.taxon('Viridiplantae'), ott.taxon('Chloroplastida'))

    a.same(ncbi.taxonThatContains('Ctenophora', 'Ctenophora pulchella'),
           ott.taxonThatContains('Ctenophora', 'Ctenophora pulchella')) # should be 103964
    a.same(ncbi.taxonThatContains('Ctenophora', 'Pleurobrachia bachei'),
           ott.taxon('641212')) # comb jelly
    a.same(ncbi.taxon('Ctenophora', 'Arthropoda'),
           ott.taxon('Ctenophora', 'Arthropoda')) # crane fly

    # David Hibbett has requested that for Fungi, only Index Fungorum
    # should be seen.  Rather than delete the NCBI fungal taxa, we just
    # mark them 'hidden' so they can be suppressed downstream.  This
    # preserves the identifier assignments, which may have been used
    # somewhere.
    ncbi.taxon('Fungi').hideDescendantsToRank('species')

    # Position is equivocal, but everyone else puts it in Annelids.
    # In Annelids also agrees with a tree that's in synthesis.
    # NCBI is definitely the minority position.
    ncbi.taxon('Annelida').take(ncbi.taxon('Myzostomida'))

    # - Alignment to OTT -

    #a.same(ncbi.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
    # #### Check - was fungi.taxon
    # Not sure what happened with these:
    # ** No unique taxon found with this name: Burkea
    # ** No unique taxon found with this name: Coscinium
    # ** No unique taxon found with this name: Perezia

    # JAR 2014-04-11 Epiphloea problem discovered during regression testing
    # Now handled in other ways (Rhodophyta vs. Ascomycota)

    # JAR attempt to resolve ambiguous alignment of Trichosporon in IF to
    # NCBI based on common member.
    # T's type = T. beigelii, which is current, according to Mycobank,
    # but it's not in our copy of IF.
    # I'm going to use a different exemplar, Trichosporon cutaneum, which
    # seems to occur in all of the source taxonomies.
    a.same(ncbi.taxon('5552'),
           ott.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))

    # 2014-04-23 In new version of IF - obvious misalignment
    # #### Check - was fungi.taxon
    # a.notSame(ncbi.taxon('Crepidula', 'Gastropoda'), ott.taxon('Crepidula', 'Microsporidia'))
    # a.notSame(ncbi.taxon('Hessea', 'Viridiplantae'), ott.taxon('Hessea', 'Microsporidia'))
    # 2014-04-23 Resolve ambiguity introduced into new version of IF
    # http://www.speciesfungorum.org/Names/SynSpecies.asp?RecordID=331593
    # #### Check - was fungi.taxon
    a.same(ncbi.taxon('Gymnopilus spectabilis var. junonius'), ott.taxon('Gymnopilus junonius'))

    # JAR 2014-04-23 More sample contamination in SILVA 115
    # #### Check - was fungi.taxon
    # a.same(ncbi.taxon('Lamprospora'), ott.taxon('Lamprospora', 'Pyronemataceae'))

    # JAR 2014-04-25
    # ### CHECK: was silva.taxon
    # a.notSame(ncbi.taxon('Bostrychia', 'Aves'), ott.taxon('Bostrychia', 'Rhodophyceae'))

    # Dail 2014-03-31 https://github.com/OpenTreeOfLife/feedback/issues/5
    # updated 2015-06-28 NCBI Katablepharidophyta = SILVA Kathablepharidae.
    # ### CHECK: was silva.taxon
    a.same(ncbi.taxon('Katablepharidophyta'), ott.taxon('Kathablepharidae'))
    # was: ott.taxon('Katablepharidophyta').hide()

    # probably not needed
    a.same(ncbi.taxon('Ciliophora', 'Alveolata'), ott.taxon('Ciliophora', 'Alveolata'))

    # SILVA has Diphylleia < Palpitomonas < Incertae Sedis < Eukaryota
    # IRMNG has Diphylleida < Diphyllatea < Apusozoa < Protista
    # They're probably the same thing.  So not sure why this is being
    # handled specially.

    # Annoying polysemy
    a.same(ncbi.taxon('Podocystis', 'Bacillariophyta'),
           ott.taxon('Podocystis', 'Bacillariophyta'))

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/198 see above
    a.same(ncbi.taxon('Euxinia', 'Pseudostomidae'), ott.taxon('476941'))
    a.same(ncbi.taxon('Euxinia', 'Crustacea'), ott.taxon('329188'))

    # NCBI has Leotiales as a synonym for Helotiales, but h2007 and IF
    # have them as separate orders.  This shouldn't cause a problem, but does.
    ncbi.taxon('Helotiales').notCalled('Leotiales')

    return a

def align_ncbi_to_silva(mappings, a):
    changes = 0
    for taxon in mappings:
        a.same(taxon, mappings[taxon])
        changes += 1
    print changes, '| NCBI taxa mapped to SILVA clusters'


# ----- GBIF (Global Biodiversity Information Facility) taxonomy -----

def adjust_gbif(gbif):
    gbif.smush()

    # In GBIF, if a rank is skipped for some children but not others, that
    # means the rank-skipped children are incertae sedis.  Mark them so.
    gbif.analyzeMajorRankConflicts()

    patch_gbif(gbif)
    return gbif

def align_gbif(gbif, ott):

    a = ott.alignment(gbif)

    # First fix the divisions
    ott.setDivision(gbif.taxon('Chromista'), 'Eukaryota')
    ott.setDivision(gbif.taxon('Protozoa'), 'Eukaryota')

    plants = set_divisions(gbif, ott)
    a.same(plants, ott.taxon('Archaeplastida'))
    ott.setDivision(gbif.taxon('Ciliophora', 'Chromista'), 'SAR')  #GBIF puts it in Chromista, actually Alveolata

    print '# Chromista division =', gbif.taxon('Chromista').getDivision()
    print '# Ciliophora division =', gbif.taxon('Ciliophora', 'Chromista').getDivisionProper()
    print '# Ciliophora division =', gbif.taxon('Ciliophora', 'Chromista').getDivision()
    # print '# 8248855 division =', gbif.taxon('8248855').getDivisionProper()
    # print '# 8248855 division =', gbif.taxon('8248855').getDivision()

    a.same(gbif.taxon('Animalia'), ott.taxon('Metazoa'))
    # End divisions

    # can't figure out why this is here.  maybe in wrong place.
    a.same(gbif.taxon('Cyclophora', 'Bacillariophyceae'),
           ott.taxon('Cyclophora', 'SAR'))

    # GBIF puts this one directly in Animalia, but Annelida is a barrier node
    gbif.taxon('Annelida').take(gbif.taxon('Echiura'))
    # similarly
    gbif.taxon('Cnidaria').take(gbif.taxon('Myxozoa'))

    # Fungi suppressed at David Hibbett's request
    gbif.taxon('Fungi').hideDescendantsToRank('species')

    # Suppressed at Laura Katz's request
    gbif.taxonThatContains('Bacteria', 'Escherichia').hideDescendants()
    gbif.taxonThatContains('Archaea', 'Halobacteria').hideDescendants()

    # - Alignment -

    #a.same(gbif.taxon('Cyanobacteria'), silva.taxon('Cyanobacteria', 'Cyanobacteria')) #'D88288/#3'

    # Automatic alignment makes the wrong choice for this one
    # a.same(ncbi.taxon('5878'), gbif.taxon('10'))    # Ciliophora
    a.same(gbif.maybeTaxon('10'), ott.taxon('Ciliophora', 'Alveolata'))  # in Protozoa
    # Not needed?
    # a.same(ott.taxon('Ciliophora', 'Ascomycota'), gbif.taxon('3269382')) # in Fungi

    # Automatic alignment makes the wrong choice for this one
    # NCBI says ncbi:29178 is in Rhizaria in Eukaryota and contains Allogromida (which is not in GBIF)
    # OTT 2.8 has 936399 = in Retaria (which isn't in NCBI) extinct_inherited ? - no good.
    # GBIF 389 is in Protozoa... but it contains nothing!!  No way to identify it other than by id.
    #   amoeboid ...
    # 2016-11-20 This may be fixed now that SAR is a division.
    a.same(gbif.maybeTaxon('389'), ott.taxon('Foraminifera', 'Rhizaria'))  # Foraminifera gbif:4983431

    # Tetrasphaera is a messy multi-way homonym
    #### Check: was ncbi.taxon
    a.same(gbif.taxon('Tetrasphaera','Intrasporangiaceae'), ott.taxon('Tetrasphaera','Intrasporangiaceae'))

    # Bad alignments to NCBI
    # #### THESE NEED TO BE CHECKED - was ncbi.taxon

    # Labyrinthomorpha (synonym for Labyrinthulomycetes)
    # No longer in GBIF... the one in IRMNG is a Cambrian sponge-like thing
    # a.notSame(ott.taxon('Labyrinthomorpha', 'Stramenopiles'), gbif.taxon('Labyrinthomorpha'))

    # a.notSame(ott.taxon('Ophiurina', 'Echinodermata'), gbif.taxon('Ophiurina','Ophiurinidae'))
    #  taken care of in adjustments.py

    # There is a test for this.  The GBIF taxon no longer exists.
    # a.notSame(ott.taxon('Rhynchonelloidea', 'Brachiopoda'), gbif.taxon('Rhynchonelloidea'))

    # Neoptera - there are tests.  Seems OK

    # a.notSame(gbif.taxon('Tipuloidea', 'Chiliocyclidae'), ott.taxon('Tipuloidea', 'Diptera')) # genus Tipuloidea
    #  taken care of in adjustments.py
    # ### CHECK: was silva.taxon
    # SILVA = GN013951 = Tetrasphaera (bacteria)

    # This one seems to have gone away given changes to GBIF
    # a.notSame(gbif.taxon('Gorkadinium', 'Dinophyta'),
    #              ott.taxon('Tetrasphaera', 'Intrasporangiaceae')) # = Tetrasphaera in Protozoa

    # Rick Ree 2014-03-28 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/37
    # ### CHECK - was ncbi.taxon
    # a.same(gbif.taxon('Calothrix', 'Rivulariaceae'), ott.taxon('Calothrix', 'Rivulariaceae'))
    a.same(gbif.taxon('Chlorella', 'Chlorellaceae'),
           ott.taxon('Chlorella', 'Chlorellaceae'))
    a.same(gbif.taxonThatContains('Myrmecia', 'Myrmecia irregularis'),
           ott.taxonThatContains('Myrmecia', 'Myrmecia irregularis'))

    # JAR 2014-04-18 attempt to resolve ambiguous alignment of
    # Trichosporon in IF and GBIF based on common member
    a.same(gbif.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'),
           ott.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))

    # Obviously the same genus, can't tell what's going on
    # if:17806 = Hygrocybe = ott:282216
    # #### CHECK: was fungi
    a.same(gbif.taxon('Hygrocybe'), ott.taxon('Hygrocybe', 'Hygrophoraceae'))

    # JAR 2014-04-23 More sample contamination in SILVA 115
    # redundant
    # a.same(gbif.taxon('Lamprospora'), fungi.taxon('Lamprospora'))

    # JAR 2014-04-23 IF update fallout
    # - CHECK - was ncbi.taxon
    a.same(gbif.taxonThatContains('Penicillium', 'Penicillium salamii'),
           ott.taxonThatContains('Penicillium', 'Penicillium salamii'))

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    if False:
        a.same(gbif.taxon('Choanoflagellida'),
               ott.taxon('Choanoflagellida', 'Opisthokonta'))

    # diatom
    a.same(gbif.taxonThatContains('Ctenophora', 'Ctenophora pulchella'),
           ott.taxonThatContains('Ctenophora', 'Ctenophora pulchella'))

    # comb jellies
    a.same(gbif.taxonThatContains('Ctenophora', 'Pleurobrachia bachei'),
           ott.taxonThatContains('Ctenophora', 'Pleurobrachia bachei'))

    a.same(gbif.taxonThatContains('Trichoderma', 'Trichoderma koningii'),
           ott.taxonThatContains('Trichoderma', 'Trichoderma koningii'))

    # Polysemy with an order in Chaetognatha (genus is a brachiopod)
    # https://github.com/OpenTreeOfLife/feedback/issues/306
    establish('Phragmophora', ott, ancestor='Brachiopoda', rank='genus', ott_id='5972959')
    # gbif:5430295 seems to be gone from 2016 GBIF.  Hmmph.
    a.same(gbif.maybeTaxon('Phragmophora', 'Brachiopoda'), ott.taxon('Phragmophora', 'Brachiopoda'))

    # Annelida is a barrier, need to put Sipuncula inside it
    gbif.taxon('Annelida').take(gbif.taxon('Sipuncula'))

    # IRMNG has better placement for these things
    target = gbif.taxon('Plantae').parent
    for name in ['Pithonella',
                 'Vavosphaeridium',
                 'Sphaenomonas',
                 'Orthopithonella',
                 'Euodiella',
                 'Medlinia',
                 'Quadrodiscus',
                 'Obliquipithonella',
                 'Damassadinium',
                 'Conion',
                 'Gaillionella']:
        taxon = gbif.maybeTaxon(name, 'Plantae')
        if taxon != None:
            target.take(taxon)
            taxon.incertaeSedis()

    # Noticed while scanning species polysemies
    gbif.taxon('Euglenales').take(gbif.taxon('Heteronema', 'Rhodophyta'))

    # WoRMS says it's not a fungus
    gbif.taxonThatContains('Minchinia', 'Minchinia cadomensis').prune(this_source)

    # Taxon is in NCBI with bad primary name; correct name is a synonym
    ott.taxon('Chaetocalyx longiflorus').rename('Chaetocalyx longiflorus')

    # 2016-12-18 somehow came loose.  4738987 = apusozoan, 570408 = plant
    # Without this, the plant was lumping in with the apusozoan.
    a.same(gbif.taxon('3236805'), ott.taxon('4738987'))
    a.same(gbif.taxon('3033928'), ott.taxon('570408'))

    # 2016-11-20 Showed up as ambiguous in transcript; log says ncbi and gbif records are separated because disjoint.
    # But they cannot be proper homonyms; too close.
    # (gbif is in Agaricomycotina, ncbi is in Ustilaginomycotina)
    a.same(gbif.taxon('Moniliellaceae', 'Fungi'), ott.taxon('Moniliellaceae', 'Fungi'))

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/301
    a.same(gbif.taxon('Dermocystidium salmonis'), ott.taxon('Dermocystidium salmonis', 'Ichthyosporea'))

    # 2017-03-26 Noticed this during the EOL id import, see above
    chon = gbif.maybeTaxon('Chondromyces', 'Fungi')
    if chon != None: chon.prune()

    # 2017-03-26 This has been moved to Cercozoa - not a fungus.  See e.g.
    # the web version of Index Fungorum.
    plas = gbif.maybeTaxon('Plasmodiophora', 'Fungi')
    if plas != None: ott.setDivision(plas, 'SAR')

    return a

def patch_gbif(gbif):
    # - Touch-up -

    # Rod Page blogged about this one
    # http://iphylo.blogspot.com/2014/03/gbif-liverwort-taxonomy-broken.html
    bad_jung = gbif.maybeTaxon('Jungermanniales','Bryophyta')
    if bad_jung != None:
        gbif.taxon('Jungermanniales','Marchantiophyta').absorb(bad_jung)

    # Joseph 2013-07-23 https://github.com/OpenTreeOfLife/opentree/issues/62
    # GBIF has two copies of Myospalax
    myo = gbif.maybeTaxon('6006429')
    if myo != None:
        myo.absorb(gbif.taxon('2439119'))

    for (synonym, primary, qid) in [
            ('Chryso-Hypnum', 'Chryso-hypnum', otc('24')),  # 2014-04-13 JAR noticed while grepping
            ('Drake-Brockmania', 'Drake-brockmania', otc(2)),  # RR 2014-04-12 #47
            ('Saxo-Fridericia', 'Saxofridericia',
             otc(36, evidence='https://github.com/OpenTreeOfLife/feedback/issues/50')),  # RR #50 - this one is in NCBI, see above
            ('Solms-Laubachia', 'Solms-laubachia', otc(37)), # RR #57 - the genus is in NCBI, see above
            ('Solms-Laubachia pulcherrima', 'Solms-laubachia pulcherrima', otc(38)), # RR #57
            ('Cyrto-Hypnum', 'Cyrto-hypnum', otc(39)),
            ('Ebriacea', 'Ebriaceae', otc(40)),  # JAR 2014-04-23 Noticed while perusing silva/gbif conflicts
            ('Acanthocistidae', 'Acanthocystidae', otc(41)),
            ('Bullacta ecarata', 'Bullacta exarata', otc(42))
    ]:
        proclaim(gbif, synonym_of(taxon(synonym), taxon(primary), 'spelling variant', qid))

    # These three went away after change to gbif processing after OTT 2.9
    # Whether_same('Drepano-Hypnum', 'Drepano-hypnum', True),
    # Whether_same('Complanato-Hypnum', 'Complanato-hypnum', True),
    # Whether_same('Leptorrhyncho-Hypnum', 'Leptorrhyncho-hypnum', True),

    # See new versions above
    # gbif.taxon('Chryso-hypnum').absorb(gbif.taxon('Chryso-Hypnum'))
    # gbif.taxon('Complanato-Hypnum').rename('Complanato-hypnum')
    # gbif.taxon('Leptorrhyncho-Hypnum').rename('Leptorrhyncho-hypnum')

    # wrong: gbif.taxon('Dinophyta').synonym('Dinophyceae')  # according to NCBI
    # these groups are missing from gbif 2016 anyhow
    # paraphyletic ??

    # JAR 2014-06-29 stumbled on this while trying out new alignment
    # methods and examining troublesome homonym Bullacta exarata.
    # GBIF obviously puts it in the wrong place, see description at
    # http://www.gbif.org/species/4599744 (it's a snail, not a shrimp).
    bex = gbif.maybeTaxon('Bullacta exarata', 'Atyidae')
    if bex != None:
        bex.detach()

    # JAR 2014-07-18  - get rid of Helophorus duplication
    # GBIF 3263442 = Helophorus Fabricius, 1775, from CoL
    #    in 6985 = Helophoridae Leach, 1815
    # GBIF 6757656 = Helophorus Leach, from IRMNG homonym list
    #    in 7829 = Hydraenidae Mulsant, 1844
    #  ('Helophorus', 'Helophoridae') ('Helophorus', 'Hydraenidae')
    proclaim(gbif, alias_of(taxon(id='6757656'), taxon(id='3263442'), otc(43)))

    # JAR 2015-06-27  there are two Myospalax myospalax
    # The one in Spalacidae is the right one, Myospalacinae is the wrong one
    # (according to NCBI)
    # Probably should clean up the whole genus
    # 2439121 = Myospalax myospalax (Laxmann, 1773) from CoL
    myo2 = gbif.maybeTaxon('6075534')
    if myo2 != None:
        gbif.taxon('2439121').absorb(myo2)

    # 4010070	pg_1378	Gelidiellaceae	gbif:8998  -- ok, paraphyletic

    tip = gbif.maybeTaxon('6101461') # ('Tipuloidea', 'Hemiptera') gbif:6101461 - extinct
    if tip != None:
        tip.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    oph = gbif.taxon('4872240')  # ('Ophiurina', 'Ophiurinidae') gbif:4872240 - extinct
    if oph != None:
        oph.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    # 2015-07-25 Extra Dipteras are confusing new division logic.  Barren genus
    bad_dip = gbif.maybeTaxon('3230674')
    if bad_dip != None: bad_dip.elide()

    # JAR 2016-07-01 while studying rank inversions.
    # Ophidiasteridae the genus was an error and has been deleted from GBIF.
    # Similarly Paracalanidae, Cornirostridae, Scaliolidae, Asterinidae
    for badid in ['6103275', '6128386', '7348034', '6141880', '6135675']:
        if gbif.maybeTaxon(badid) != None:
            gbif.taxon(badid).elide()

    # JAR 2016-07-04 observed while scanning rank inversion messages.
    # Corrected rank from https://en.wikipedia.org/wiki/Protochonetes
    proclaim(gbif, has_rank(taxon('Chonetoidea'), 'superfamily', oct(44)))

    # https://github.com/OpenTreeOfLife/feedback/issues/144
    lepi = gbif.maybeTaxon('Lepilemur tymerlachsonorum')
    if lepi != None:
        lepi.rename('Lepilemur tymerlachsoni')

    # Related to https://github.com/OpenTreeOfLife/feedback/issues/307
    # This problem is remedied by the 2016 GBIF update.
    navi = gbif.maybeTaxon('Naviculae mesoleiae')
    if navi != None:
        navi.rename('Navicula mesoleiae')

    # Bogus species-rank record in 2016 GBIF making 'Navicula' ambiguous
    nav_species = gbif.maybeTaxon('8616388')
    if nav_species != None: nav_species.prune()

    proclaim(gbif, synonym_of(taxon('Naviculae'), taxon('Navicula'), 'spelling variant', otc(45)))

    # GBIF as of 2016-09-01 says "doubtful taxon"
    bad_foram = gbif.maybeTaxon('Foraminifera', 'Granuloreticulosea')
    if bad_foram != None:
        bad_foram.prune(this_source)

    # 2016-09-01 Wrongly in Hymenoptera
    proclaim(gbif, has_parent(taxon('Aporia', descendant='Aporia gigantea'),
                              taxon('Pieridae', 'Insecta'),
                              otc(46)))

    # The Bdellodes mites were in flatworms in GBIF 2013.  This
    # has been straightened out.
    proclaim(gbif, has_parent(taxon('Bdellodes', descendant='Bdellodes robusta'),
                              taxon('Bdellidae'),
                              otc(47)))

    # 2016-09-01 These are fossil fungi spores, not plants.  They're in
    # Index Fungorum, so we don't need wrong info from GBIF.
    inap = gbif.maybeTaxon('Inapertisporites', 'Plantae')
    if inap != None:
        inap.prune(this_source)

    # 2016-09-01 This is not a plant.  Checked W. van Hoven 1987, found
    # in pubmed via web search, which says these things are ciliates,
    # and puts them in Cycloposthiidae.  http://dx.doi.org/10.1111/j.1550-7408.1987.tb03186.x
    # gbif.taxon('Cycloposthiidae').take(gbif.taxon('Monoposthium'))

    # 2016-09-02 Confusion with Tylosurus
    ty = gbif.taxon('Tylosaurus', 'Belonidae')
    if ty != None: ty.prune(this_source)

    bad_ecc = gbif.maybeTaxon('Eccrinaceae', 'Zygomycota')
    if bad_ecc != None: bad_ecc.prune()

    # 2016-11-15 Noticed these while perusing the deprecated.tsv after the
    # GBIF 2016 update.  GBIF has corrected the gender on many species names.
    # These are the ones occurring in phylesystem.
    for (name, wrong_name) in [
            ('Alopochen mauritiana', 'Alopochen mauritianus'),
            ('Tchagra minuta', 'Tchagra minutus'),
            ('Eudynamys melanorhyncha', 'Eudynamys melanorhynchus'),
            ('Aquila africana', 'Aquila africanus'),
            ('Alectroenas pulcherrima', 'Alectroenas pulcherrimus'),
            ('Megaceryle maxima', 'Megaceryle maximus'),
            ('Coracias naevius', 'Coracias naevia'),
            ('Myiothlypis coronata', 'Myiothlypis coronatus'),
            ('Myiothlypis leucoblephara', 'Myiothlypis leucoblepharus'),
            ('Myiothlypis bivittata', 'Myiothlypis bivittatus'),
            ('Arizelocichla montana', 'Arizelocichla montanus'),
            ('Myiothlypis flaveola', 'Myiothlypis flaveolus'),
            ('Myiothlypis conspicillata', 'Myiothlypis conspicillatus'),
            ('Myiothlypis signata', 'Myiothlypis signatus'),
            ('Arizelocichla tephrolaema', 'Arizelocichla tephrolaemus'),
            ('Monticola erythronotus', 'Monticola erythronota'),
            ('Sturnia sturninus', 'Sturnia sturnina'),
            ('Collocalia spodiopygius', 'Collocalia spodiopygia'),
            ('Ceratogymna subcylindricus', 'Ceratogymna subcylindrica'),
            ('Amazona mercenaria', 'Amazona mercenarius'),
            ('Phaethornis aethopygus', 'Phaethornis aethopyga'),
            ('Chlorestes notatus', 'Chlorestes notata'),
            ('Mesitornis variegata', 'Mesitornis variegatus'),
            ('Erethizon dorsatus', 'Erethizon dorsata'),
            ('Incana incana', 'Incana incanus'),
            ('Nemapteryx augusta', 'Nemapteryx augustus'),
            ('Chaetocalyx longiflorus', 'Chaetocalyx longiflora'),
            ('Monticola imerina', 'Monticola imerinus'),
    ]:
        if gbif.maybeTaxon(name) != None:
            if gbif.maybeTaxon(wrong_name) == None:
                gbif.taxon(name).synonym(wrong_name, 'gender variant')
            else:
                gbif.taxon(name).absorb(gbif.taxon(wrong_name))

    # GBIF changed Chaetocalyx longiflora to -us.
    # GBIF changed Collocalia spodiopygia to -us.

    # 2016-11-18 These GBIF duplicates show up as ambiguous while processing
    # the chromista spreadsheet.
    for (name, bad_id) in [
            ('Rhaphidoscene', '7738163'),
            ('Echinogromia', '8370412'),
            ('Septammina', '7556572'),
            ('Dendropela', '7509811'),
            ('Ceratestina', '8322228'),
            ('Turriclavula', '7705478'),
            ('Buccinina', '7655969'),
            ('Rhaphidohelix', '7431039'),
            ('Marenda', '8281602'),
            ('Urnulina', '7457155'),
            ('Rhaphidodendron', '7957205'),
            ('Millettella', '7884383'),
    ]:
        tax = gbif.taxon(name, 'Foraminifera')
        if tax != None and gbif.maybeTaxon(bad_id) != None:
            tax.absorb(gbif.taxon(bad_id))

    # 2016-11-20 Diaphoropodon showed up as ambiguous in transcript.
    # There are two Diaphoropodon in new GBIF (4898754 + 8212987).
    # 4 is a foram, 8 is a 'Sarcomastigophora', which is paraphyletic.
    # OTT thinks Sarc. is inconsistent.  8 is a child of
    # Chlamydophryidae -- which is in SAR!  So these two things are
    # actually the same.
    # 2017-06-04 The one in Sarcomastigophora has been removed from GBIF online.
    if gbif.maybeTaxon('Diaphoropodon', 'Sarcomastigophora') != None:
        gbif.taxon('Diaphoropodon', 'Sarcomastigophora').prune(otc(48))

    # Similar cases (probably): (from Chromista spreadsheet ambiguities)
    # Umbellina, Rotalina

    # 2016-11-23 Unseparated homonym found while trying to use Holozoa
    # as an example of something
    dist = gbif.maybeTaxon('Distaplia', 'Chordata')
    if dist != None:
        dist.notCalled('Holozoa')

    # 2017-02-15 JAR Noted as suspicious during 3.0 build
    och = gbif.maybeTaxon('Ochrophyta')
    if och != None:
        och.notCalled('Chrysophyceae')

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/397
    # (gbif places a scallop in Cnidaria)
    # Exists in other taxonomies in correct place, so we simply prune it from gbif
    # (using the incorrect parent, so that we will get it back in corrected future
    # updates
    gbif.taxon("Placopecten","Pectiniidae").prune()

    return gbif

# Align low-priority WoRMS
def align_worms(worms, ott):

    a = ott.alignment(worms)

    # First get the divisions right
    if worms.maybeTaxon('Biota') != None:
        a.same(worms.taxon('Biota'), ott.taxon('life'))
    a.same(worms.taxon('Animalia'), ott.taxon('Metazoa'))

    ott.setDivision(worms.taxon('Chromista'), 'Eukaryota')
    ott.setDivision(worms.taxon('Protozoa'), 'Eukaryota')

    a.same(worms.taxon('Harosa'), ott.taxon('SAR'))     # Cool!
    a.same(worms.taxon('Heterokonta'), ott.taxon('Stramenopiles'))

    plants = set_divisions(worms, ott)
    a.same(plants, ott.taxon('Archaeplastida'))
    ott.setDivision(worms.taxon('Ciliophora', 'Chromista'), 'SAR')

    worms.taxon('Glaucophyta').absorb(worms.taxon('Glaucophyceae'))
    # End divisions adjustments

    a.same(worms.taxonThatContains('Trichosporon', 'Trichosporon lodderae'),
           ott.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))
    a.same(worms.taxonThatContains('Trichoderma', 'Trichoderma koningii'),
           ott.taxonThatContains('Trichoderma', 'Trichoderma koningii'))

    # extinct foram, polyseym risk with extant bryophyte
    # worms.taxon('Pohlia', 'Rhizaria').prune(this_source)

    # Annelida is a barrier, need to put Sipuncula inside it
    worms.taxon('Annelida').take(worms.taxon('Sipuncula'))

    # See above
    plas = worms.maybeTaxon('Plasmodiophora', 'Fungi')
    if plas != None: ott.setDivision(plas, 'SAR')

    return a

# ----- Interim Register of Marine and Nonmarine Genera (IRMNG) -----

def adjust_irmng(irmng):
    irmng.smush()
    irmng.analyzeMajorRankConflicts()

    # patch_irmng

    # JAR 2014-04-26 Flush all 'Unaccepted' taxa
    if irmng.maybeTaxon('Unaccepted', 'life') != None:
        irmng.taxon('Unaccepted', 'life').prune(this_source)

    # Fixes

    # Neopithecus (extinct) occurs in two places.  Flush one, mark the other
    if irmng.maybeTaxon('1413316') != None:
        irmng.taxon('1413316').prune(this_source) #Neopithecus in Mammalia
    if irmng.maybeTaxon('1413315') != None:
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

    tip = irmng.maybeTaxon('Tipuloidea', 'Hemiptera')  # irmng:1170022
    if tip != None:
        tip.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    oph = irmng.maybeTaxon('1346026') # irmng:1346026 'Ophiurina', 'Ophiurinidae'
    if oph != None:
        oph.prune("about:blank#this-homonym-is-causing-too-much-trouble")

    # NCBI synonymizes Pelecypoda = Bivalvia
    if irmng.maybeTaxon('Pelecypoda') != None:
        irmng.taxon('Bivalvia').absorb(irmng.taxon('Pelecypoda')) # bogus order

    # This one was mapping to Blattodea, and making it extinct.
    # Caused me a couple of hours of grief.
    # My guess is it's because its unique child Sinogramma is in Blattodea in GBIF.
    # Wikipedia says it's paraphyletic.
    if irmng.maybeTaxon('Blattoptera', 'Insecta') != None:
        irmng.taxon('Blattoptera', 'Insecta').prune('https://en.wikipedia.org/wiki/Blattoptera')

    # 2015-07-25 Extra Dipteras are confusing new division logic.  Barren genus
    if irmng.maybeTaxon('1323521') != None:
        irmng.taxon('1323521').prune(this_source)

    # 2015-09-10 This one is unclassified (Diptera) and is leading to confusion with two other Steinias.
    if irmng.maybeTaxon('1299622') != None:
        irmng.taxon('1299622').prune(this_source)

    # 2015-09-11 https://github.com/OpenTreeOfLife/feedback/issues/74
    # Lymnea is a snail, not a shark
    if irmng.maybeTaxon('1317416') != None:
        irmng.taxon('1317416').prune(this_source)

    # 2015-10-12 JAR checked IRMNG online and this taxon (Ctenophora in Chelicerata) did not exist
    if irmng.maybeTaxon('1279363') != None:
        irmng.taxon('1279363').prune(this_source)

    # https://github.com/OpenTreeOfLife/feedback/issues/285
    if irmng.maybeTaxon('Notochelys', 'Cheloniidae') != None:
        irmng.taxon('Notochelys', 'Cheloniidae').prune(this_source)

    # JAR 2016-07-04 Duplicates of leps in Diptera, noticed when reviewing
    # homonym report
    for (id, name) in [('10888189', 'Aricia brunnescens'),
                       ('10095324', 'Aricia deleta'),
                       ('10094174', 'Aricia striata')]:
        tax = irmng.maybeTaxon(id)
        if tax != None: tax.prune(this_source)

    # 2016-07-28 JAR
    # Discovered by ambiguity in an inclusion test.  The IRMNG genus appears to be
    # disjoint with the NCBI genus of the same name, but they're
    # actually the same due to species synonymy (from wikispecies).
    irmng.taxon('Aulacomonas submarina').synonym('Diphylleia rotans')

    # https://github.com/OpenTreeOfLife/feedback/issues/167
    irmng.taxon('Plectophanes altus').absorb(irmng.taxon('Plectophanes alta'))

    # wrongly in Plantae, should be a Lep
    irmng.taxon('Pieridae', 'Insecta').take(irmng.taxonThatContains('Aporia', 'Aporia agathon'))

    # members of Charis (a Riodinid genus) wrongly placed in genus Epimelitta in Cerambycidae
    losers = []
    for species in irmng.taxon('Charis aphanis', 'Insecta').parent.children:
        if species.name.startswith('Charis '):
            losers.append(species)
    for species in losers:
        species.prune(this_source)

    # See above for GBIF
    #irmng.taxon('Cycloposthiidae').take(irmng.taxon('Monoposthium'))

    # 2016-09-02 on gitter: Pisces vs. Mososauridae confusion
    irmng.taxon('Tylosurus').notCalled('Tylosaurus')

    # 2016-02-13 Fallout from IRMNG update - confusion with the flatworm.
    # No species records for this genus.
    luth_girault = irmng.maybeTaxon('1427241')
    if luth_girault != None and luth_girault.hasChildren():
        luth_girault.prune()

    # 2017-03-26 Redundant Cornuta (11647 is better)
    if irmng.maybeTaxon('1161') != None: irmng.taxon('1161').elide()

    return irmng

def align_irmng(irmng, ott):

    a = ott.alignment(irmng)

    # Fix divisions
    ott.setDivision(irmng.taxon('Protista'), 'Eukaryota')

    a.same(irmng.taxon('Animalia'), ott.taxon('Metazoa'))

    plants = set_divisions(irmng, ott)
    ott.setDivision(irmng.taxon('Ciliophora', 'Protista'), 'SAR')
    a.same(plants, ott.taxon('Archaeplastida'))
    # End divisions

    # Fungi suppressed at David Hibbett's request
    irmng.taxon('Fungi').hideDescendantsToRank('species')

    # Microbes suppressed at Laura Katz's request
    irmng.taxonThatContains('Bacteria','Escherichia coli').hideDescendants()
    irmng.taxonThatContains('Archaea','Halobacteria').hideDescendants()

    # Cnidaria is a barrier node
    irmng.taxon('Cnidaria').take(irmng.taxon('Myxozoa'))
    # Annelida is a barrier, need to put Sipuncula inside it
    irmng.taxon('Annelida').take(irmng.taxon('Sipuncula'))

    a.same(irmng.taxon('1381293'), ott.taxon('Veronica', 'Plantaginaceae'))  # ott:648853
    # genus Tipuloidea (not superfamily) ott:5708808 = gbif:6101461
    # Taken care of in assemble_ott.py:
    # a.same(ott.taxon('Tipuloidea', 'Dicondylia'), irmng.taxon('1170022'))
    # IRMNG has four Tetrasphaeras.
    a.same(irmng.taxon('Tetrasphaera','Intrasporangiaceae'), ott.taxon('Tetrasphaera','Intrasporangiaceae'))
    ottgork = ott.maybeTaxon('Gorkadinium','Dinophyceae')
    if ottgork != None:
        a.same(irmng.taxon('Gorkadinium','Dinophyceae'), ottgork)

    # JAR 2014-04-18 attempt to resolve ambiguous alignment of
    # Match Trichosporon in IRMNG to one of three in OTT based on common member.
    # Trichosporon in IF = if:10296 genus in Trichosporonaceae, contains cutaneum
    a.same(irmng.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'), \
           ott.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))


    # https://github.com/OpenTreeOfLife/feedback/issues/45
    # IRMNG has Choanoflagellida < Zoomastigophora < Sarcomastigophora < Protozoa
    # might be better to look for something it contains
    a.same(irmng.taxon('Choanoflagellida', 'Zoomastigophora'),
             ott.taxon('Choanoflagellida', 'Eukaryota'))

    # probably not needed
    a.same(irmng.taxon('239'), ott.taxon('Ciliophora', 'Alveolata'))  # in Protista
    # Gone away...
    # a.same(ott.taxon('Ciliophora', 'Ascomycota'), irmng.taxon('Ciliophora', 'Ascomycota'))

    # Could force this to not match the arthropod.  But much easier just to delete it.
    irmng.taxon('Morganella', 'Brachiopoda').prune(this_source)
    #  ... .notSame(ott.taxon('Morganella', 'Arthropoda'))

    def trouble(name, ancestor, not_ancestor):
        probe = irmng.maybeTaxon(name, ancestor)
        if probe == None: return
        probe.prune(this_source)

    # JAR 2014-04-24 false match
    # IRMNG has one in Pteraspidomorphi (basal Chordate) as well as a
    # protozoan (SAR; ncbi:188977).
    trouble('Protaspis', 'Chordata', 'Cercozoa')

    # JAR 2014-04-18 while investigating hidden status of Coscinodiscus radiatus.
    # tests
    trouble('Coscinodiscus', 'Porifera', 'Heterokontophyta')

    # 2015-09-10 Inclusion test failing irmng:1340611
    trouble('Retaria', 'Brachiopoda', 'Rhizaria')

    # 2015-09-10 Inclusion test failing irmng:1289625
    trouble('Campanella', 'Cnidaria', 'SAR')

    # Bad homonyms
    trouble('Neoptera', 'Tachinidae', 'Pterygota')
    trouble('Hessea', 'Holozoa', 'Fungi')

    a.same(irmng.taxonThatContains('Trichoderma', 'Trichoderma koningii'),
           ott.taxonThatContains('Trichoderma', 'Trichoderma koningii'))

    # https://github.com/OpenTreeOfLife/feedback/issues/241
    # In IRMNG L. and S. are siblings (children of Actinopterygii), but in NCBI
    # Lepisosteiformes is a synonym of Semionotiformes (in Holostei, etc.).
    irmng.taxon('Semionotiformes').absorb(irmng.taxon('Lepisosteiformes'))
    irmng.taxon('Semionotiformes').extant()

    # From deprecated.tsv file for OTT 2.10
    irmng.taxon('Plectospira', 'Brachiopoda').prune(this_source)    # extinct, polysemy with SAR
    irmng.taxon('Leptomitus', 'Porifera').prune(this_source)  # extinct, SAR polysemy, =gbif:3251526

    # Annelida is a barrier, need to put Sipuncula inside it
    irmng.taxon('Annelida').take(irmng.taxon('Sipuncula'))

    # Noticed while scanning species polysemies
    irmng.taxon('Peranemaceae').take(irmng.maybeTaxon('Heteronema', 'Rhodophyta'))

    # 2016-10-28 Noticed Goeppertia wrongly extinct while eyeballing
    # the deprecated.tsv file
    goe = irmng.maybeTaxon('Goeppertia', 'Pteridophyta')
    if goe != None:
        goe.prune(this_source)

    # Not fungi - Romina 2014-01-28
    # ott.taxon('Adlerocystis').show()  - it's Chromista ...
    # Index Fungorum says Adlerocystis is Chromista, but I don't believe it
    # ott.taxon('Chromista').take(ott.taxon('Adlerocystis','Fungi'))

    # IRMNG says Adlerocystis is a fungus (Zygomycota), but unclassified - JAR 2014-03-10
    # ott.taxon('Adlerocystis', 'Fungi').incertaeSedis()

    adler = irmng.maybeTaxon('Adlerocystis', 'Fungi')
    if adler != None:
        adler.prune()

    # Discovered on looking at build diagnostics for Chromista/Protozoa spreadsheet
    mic = irmng.maybeTaxon('Microsporidia', 'Protista')
    if mic != None:
        mic.prune()

    for sad in ['Xanthophyta', 'Chrysophyta', 'Phaeophyta', 'Raphidophyta', 'Bacillariophyta']:
        irmng.taxon('Heterokontophyta').notCalled(sad)

    # 2017-02-15 Noticed during 3.0 build
    a.same(irmng.taxon('Heterokontophyta'), ott.taxon('Stramenopiles'))

    # Yan Wong https://github.com/OpenTreeOfLife/feedback/issues/345
    # Conolophus should synonymized with Minchenella... see comments
    # Minchenella is known to newer GBIF, but not to older IRMNG, so this
    # should align now.
    if irmng.maybeTaxon('Conolophus', 'Mammalia') != None:
        irmng.taxon('Conolophus', 'Mammalia').clobberName('Minchenella')

    # 2017-03-26 See above
    plas = irmng.maybeTaxon('Plasmodiophora', 'Fungi')
    if plas != None: ott.setDivision(plas, 'SAR')

    return a


# Common to GBIF, IRMNG, and WoRMS

nonchloroplastids = ['Rhodophyta', 'Rhodophyceae', 'Glaucophyta', 'Glaucophyceae']

def set_divisions(taxonomy, ott):

    plants = taxonomy.taxon('Plantae') # = Archaeplastida

    # Any child that's not a rhodophyte or glaucophyte is a chloroplastid
    for plant in plants.children:
        if (not (plant.name in nonchloroplastids)
            and plant.rank.level <= Rank.FAMILY_RANK.level):
            ott.setDivision(plant, 'Chloroplastida')

    # These aren't plants
    set_SAR_divisions(taxonomy, ott)

    # Where does this belong?
    z = taxonomy.maybeTaxon('Microsporidia')
    if z != None:
        ott.setDivision(z, 'Fungi')  #GBIF puts it in Protozoa, IF in Fungi

    return plants

sar_contains = ['Stramenopiles',
                'Heterokonta',
                'Heterokontophyta',
                'Alveolata',
                'Rhizaria',
                'Bacillariophyta',
                'Bacillariophyceae',
                'Foraminifera',
                'Foraminiferida',
                'Myzozoa',     #GBIF puts it in Chromista
                'Ochrophyta',  #GBIF puts it in Chromista
                'Oomycota',    #GBIF puts it in Chromista
                'Radiozoa',	   #GBIF puts it in Chromista, acutally Rhizaria
                'Dinophyta',   #IRMNG puts in in Protista, actually Rhizaria
                'Dinophyceae', #found while chasing Piscinoodinium dup
                'Sphaeriparaceae',
                'Cercozoa',    #found during EOL review
                'Desmothoracida',   #was in Heliozoa
                'Apodiniaceae', #since removed from GBIF... protozoan
                'Actoniphryida',
                ]

def set_SAR_divisions(taxonomy, ott):
    for name in sar_contains:
        z = taxonomy.maybeTaxon(name, 'Chromista')
        if z == None:
            z = taxonomy.maybeTaxon(name, 'Protista')
            if z == None:
                z = taxonomy.maybeTaxon(name, 'Protozoa')
        if z != None:
            ott.setDivision(z, 'SAR')
