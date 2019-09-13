# coding=utf-8

from proposition import *

# ----- Final patches -----

def patch_ott(ott):

    # troublemakers.  we don't use them
    print '| Flushing %s viruses' % ott.taxon('Viruses').count()
    ott.taxon('Viruses').prune()

    print '-- more patches --'

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
    proclaim(ott, has_parent(taxon('Quiscalus', descendant='Quiscalus mexicanus'),
                             taxon('Icteridae', 'Aves'),
                             otc(60)))

    # Misspelling in GBIF... seems to already be known
    # Stephen email to JAR 2014-01-26
    # ott.taxon("Torricelliaceae").synonym("Toricelliaceae")

    # Joseph 2014-01-27 https://code.google.com/p/gbif-ecat/issues/detail?id=104
    ott.taxon('Parulidae').take(ott.taxon('Myiothlypis', 'Passeriformes'))
    # I don't get why this one isn't a major_rank_conflict !? - bug. (so to speak.)
    ott.taxon('Blattodea').take(ott.taxon('Phyllodromiidae'))

    # See above (occurs in both IF and GBIF).  Also see issue #67
    #chlam = ott.taxonThatContains('Chlamydotomus', 'Chlamydotomus beigelii')
    #if chlam != None: chlam.incertaeSedis()
    # As of 2017-06-03, Chlamydotomus beigelii doesn't exist.

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
        tax = ott.maybeTaxon(name, 'Chloroplastida')
        if tax != None and ott.maybeTaxon('Rosa', name) == None:
            tax.incertaeSedis()

    # Patches from the Katz lab to give decent parents to taxa classified
    # as Chromista or Protozoa
    # DISABLED: as of 2017-02-19, all but one of the changes listed on the spreadsheet
    # either were already there, or else the taxon was missing.  So it doesn't
    # make much sense to continue using it.
    #
    # print '-- Chromista/Protozoa spreadsheet from Katz lab --'
    # fixChromista(ott)
    # 2016-06-30 deleted from spreadsheet because ambiguous:
    #   Enigma,Protozoa,Polychaeta ,,,,, -
    #   Acantharia,Protozoa,Radiozoa,,,,,
    #   Lituolina,Chromista,Lituolida ,WORMS,,,,


    # From Laura and Dail on 5 Feb 2014
    # https://groups.google.com/d/msg/opentreeoflife/a69fdC-N6pY/y9QLqdqACawJ
    tax = ott.maybeTaxon('Chlamydiae/Verrucomicrobia group')
    if tax != None and tax.name != 'Bacteria':
        tax.rename('Verrucomicrobia group')
    # The following is obviated by algorithm changes
    # ott.taxon('Heterolobosea','Discicristata').absorb(ott.taxon('Heterolobosea','Percolozoa'))

    # There's no more Oxymonadida; it seems to have been replaced by Trimastix.
    #tax = ott.taxonThatContains('Excavata', 'Euglena')
    #if tax != None:
    #    tax.take(ott.taxon('Oxymonadida','Eukaryota'))

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
    ara = ott.maybeTaxon('Araripia')
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
    if ott.maybeTaxon('Magnoliophyta') != None:
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

    # 2014-04-21 RR
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/45
    for (epithet, qid) in [('cylindraceum', otc(25)),
                           # ('lepidoziaceum', otc(26)), vanished
                           ('intermedium', otc(27)),
                           ('espinosae', otc(28)),
                           ('pseudoinvolvens', otc(29)),
                           ('arzobispoae', otc(30)),
                           ('sharpii', otc(31)),
                           ('frontinoae', otc(32)),
                           ('atlanticum', otc(33)),
                           ('stevensii', otc(34)),
                           # ('brachythecium', otc(35)), vanished
                    ]:
        prop = synonym_of(taxon('Cyrto-Hypnum ' + epithet),
                          taxon('Cyrto-hypnum ' + epithet),
                          'spelling variant',
                          qid)
        proclaim(ott, prop)
        # was gbif.taxon('Cyrto-hypnum ' + epithet).absorb(gbif.taxon('Cyrto-Hypnum ' + epithet))

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
    proclaim(ott, synonym_of(taxon('Norops', 'Iguanidae'),
                             taxon('Anolis'),
                             'proparte synonym',
                             otc(52)))

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
        # ('Myoxidae', 'Rodentia'),

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
    if ott.maybeTaxon('Berendtiella rugosa') != None:
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
        tax = ott.maybeTaxon(name, 'Proboscidea')
        if tax != None:
            tax.extinct()

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
        tax = ott.maybeTaxon(name, anc)
        if tax != None:
            tax.extant()

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
    if ott.maybeTaxon('Notobalanus','Maxillopoda') != None:
        ott.taxon('Notobalanus', 'Maxillopoda').extant() # IRMNG

    # https://github.com/OpenTreeOfLife/feedback/issues/303
    if ott.maybeTaxon('Neolepas','Maxillopoda') != None:
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
        proclaim(ott, is_extinct(taxon(name), None))  # was otc(53)
                            # 'https://github.com/OpenTreeOfLife/reference-taxonomy/issues/116'

    # MTH 2016-01-05 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/182
    h2 = ott.maybeTaxon('Homarus', 'Coleoptera')
    if h2 != None and not h2.hasChildren(): h2.prune()

    # https://github.com/OpenTreeOfLife/feedback/issues/294
    # There are four Heterodons in IRMNG, and we picked the wrong one.
    ott.taxonThatContains('Heterodon', 'Heterodon platirhinos').extant()

    # https://github.com/OpenTreeOfLife/feedback/issues/258
    ott.taxon('Hippopotamus madgascariensis').rename('Hippopotamus madagascariensis')

    for (bad, good) in [('Naultinus gemmeus', 'Heteropholis genneus'), #249
                        ('Raphus ineptus', 'Raphus cucullatus'), #187
                        ('Cephalorhyncus hectori', 'Cephalorhynchus hectori'), #145
                        ('Cephalorhyncus eutropia', 'Cephalorhynchus eutropia'), #145
                        ('Pristophorus lanceolatus', 'Pristiophorus lanceolatus'), #136
                        ('Galeolerdo vouncus', 'Galeocerdo vouncus')]: #138
        if ott.maybeTaxon(bad) != None:
            if ott.maybeTaxon(good) != None:
                ott.taxon(good).absorb(ott.taxon(bad))
            else:
                ott.taxon(bad).rename(good)

    # https://github.com/OpenTreeOfLife/feedback/issues/138
    if ott.maybeTaxon('Galeocerdo', 'Vertebrata'):
        gal = ott.taxon('Galeocerdo', 'Vertebrata')
        for child in gal.getChildren():
            if not child.name.endswith(' cuvier'):
                child.extinct()

    # https://github.com/OpenTreeOfLife/feedback/issues/356
    for genus in ['Chinlea', 'Diplurus', 'Graphiurichthys',
                  'Moenkopia', 'Quayia']:
        g = ott.maybeTaxon(genus, 'Coelacanthidae')
        if g != None:
            g.extinct()

    # https://github.com/OpenTreeOfLife/feedback/issues/352
    for name in ['Lasioseisus chenpengi', 'Rhynacadicrus asperulus']:
        if ott.maybeTaxon(name, ' Heliconiaceae') != None:
            ott.taxon(name, ' Heliconiaceae').hide()

    # https://github.com/OpenTreeOfLife/feedback/issues/336
    proclaim(ott, synonym_of(taxon('Emberiza calandra'),
                             taxon('Miliaria calandra'),
                             'objective synonym',
                             otc(55)))

    # https://github.com/OpenTreeOfLife/feedback/issues/332
    proclaim(ott, synonym_of(taxon('Strigops habroptila'),
                             taxon('Strigops habroptilus'),
                             'spelling variant',
                             otc(56)))

    # https://github.com/OpenTreeOfLife/feedback/issues/317
    proclaim(ott, is_extinct(taxon('Kummelonautilus'), otc(57)))
    proclaim(ott, is_extinct(taxon('Westonoceras'), otc(58)))

    # 2017-05-29 Work around bug in WoRMS import
    # (missing nonmarine species)
    # (manifested as an inclusions test failure; see maintenance
    # manual)
    proclaim(ott, has_parent(taxon('Stylommatophora', 'Gastropoda'),
                             taxon('Pulmonata', 'Gastropoda'),
                             otc(59)))

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/331
    # http://irmng.org/aphia.php?p=taxdetails&id=1280648
    proclaim(ott, synonym_of(taxon('Ericodes', 'Ericales'),
                             taxon('Erica', 'Ericales'),
                             'synonym',
                             otc(62)))

    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/397
    # (gbif places a scallop in Cnidaria)
    proclaim(ott, has_parent(taxon('Placopecten', descendant='Placopecten magellanicus'),
                             taxon('Pectinidae', 'Bivalvia'),
                             otc(63)))
    
