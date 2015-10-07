# Jython script to build the Open Tree reference taxonomy
# coding=utf-8

# Unless specified otherwise issues are in the reference-taxonomy repo:
# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/...

import sys

from org.opentreeoflife.smasher import Taxonomy
from ncbi_ott_assignments import ncbi_assignments_list
sys.path.append("feed/misc/")
from chromista_spreadsheet import fixChromista
import taxonomies
import check_inclusions
from claim import Has_child, test_claims

this_source = 'https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/make-ott.py'

# temporary debugging thing
invariants = [Has_child('Nucletmycea', 'Fungi'),
              Has_child('Opisthokonta', 'Nucletmycea'),
              Has_child('Rhizobiales', 'Xanthobacteraceae'), # D11342/#4 parent of D11342/#5
          ]

def check_invariants(ott):
    test_claims(ott, invariants)

def create_ott():

    ott = Taxonomy.newTaxonomy()

    # There ought to be tests for all of these...

    for name in names_of_interest:
        ott.namesOfInterest.add(name)

    # When lumping, prefer to use ids that have been used in OTU matching
    # This list could be used for all sorts of purposes...
    ott.loadPreferredIds('ids-that-are-otus.tsv', False)
    ott.loadPreferredIds('ids-in-synthesis.tsv', True)

    ott.setSkeleton(Taxonomy.getTaxonomy('tax/skel/', 'skel'))

    silva = prepare_silva(ott)
    ott.absorb(silva)
    check_invariants(ott)

    h2007 = prepare_h2007(ott)
    ott.absorb(h2007)

    (fungi, fungorum_sans_fungi) = prepare_fungorum(ott)
    ott.absorb(fungi)
    check_invariants(ott)

    # the non-Fungi from Index Fungorum get absorbed below

    lamiales = prepare_lamiales(ott)
    ott.absorb(lamiales)

    (malacostraca, worms_sans_malacostraca) = prepare_worms(ott)
    ott.absorb(malacostraca)

    ncbi = prepare_ncbi(ott)
    ott.absorb(ncbi)
    check_invariants(ott)

    ott.absorb(worms_sans_malacostraca)

    ott.absorb(fungorum_sans_fungi)

    gbif = prepare_gbif(ott)
    ott.absorb(gbif)

    irmng = prepare_irmng(ott)
    ott.absorb(irmng)

    taxonomies.link_to_h2007(ott)

    get_default_extinct_info_from_gbif(gbif, ott)

    check_invariants(ott)
    # consider try: ... except: print '**** Exception in patch_ott'
    patch_ott(ott)

    # Experimental...
    unextinct_ncbi(ncbi, ott)

    # Remove all trees but the largest (or make them life incertae sedis)
    ott.deforestate()

    # -----------------------------------------------------------------------------
    # OTT id assignment

    # Force some id assignments... will try to automate this in the future.
    # Most of these come from looking at the otu-deprecated.tsv file after a 
    # series of smasher runs.

    for (inf, sup, id) in [
            ('Tipuloidea', 'Diptera', '722875'),
            ('Saccharomycetes', 'Saccharomycotina', '989999'),
            ('Phaeosphaeria', 'Ascomycota', '5486272'),
            ('Synedra acus','Eukaryota','992764'),
            ('Epiphloea','Halymeniaceae','5342325'),
            ('Hessea','Archaeplastida','600099'),
            ('Morganella','Arthropoda','6400'),
            ('Rhynchonelloidea','Rhynchonellidae','5316010'),
            ('Epiphloea', 'Lichinales', '5342482'),
            ('Morganella', 'Fungi', '973932'),
            ('Parmeliaceae', 'Lecanorales', '305904'),
    ]:
        tax = ott.taxon(inf, sup)
        if tax != None:
            tax.setId(id)

    ott.taxonThatContains('Rhynchonelloidea', 'Sphenarina').setId('795939') # NCBI

    for (ncbi_id, ott_id, name) in ncbi_assignments_list:
        n = ncbi.maybeTaxon(ncbi_id)
        if n != None:
            im = ott.image(n)
            if im != None:
                im.setId(ott_id)
            else:
                print '** NCBI %s not mapped - %s' % (ncbi_id, name)
        else:
            print '** No NCBI taxon %s - %s' % (ncbi_id, name)

    # Cylindrocarpon is now Neonectria
    ott.image(gbif.taxon('2563163')).setId('51754')

    # Foo
    trich = fungi.maybeTaxon('Trichosporon')
    if trich != None:
        ott.image(trich).setId('364222')

    #ott.image(fungi.taxon('11060')).setId('4107132') #Cryptococcus - a total mess


    # Assign OTT ids to taxa that don't have them, re-using old ids when possible
    ids = Taxonomy.getTaxonomy('tax/prev_ott/')

    # Assign old ids to nodes in the new version
    ott.assignIds(ids)

    report_on_h2007(h2007, ott)

    return ott

# ----- SILVA microbial taxonomy -----
def prepare_silva(ott):
    silva = taxonomies.load_silva()
    silva.setTarget(ott)

    # JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
    # confirms this synonymy.  Dail L. prefers -phyta to -phyceae
    # but says -phytina would be more correct per code.
    # Skeleton taxonomy has -phyta (on Dail's advice).
    silva.taxon('Rhodophyceae').synonym('Rhodophyta')    # moot now?

    silva.taxon('Florideophycidae', 'Rhodophyceae').synonym('Florideophyceae')
    silva.taxon('Stramenopiles', 'SAR').synonym('Heterokonta') # needed by WoRMS

    return silva

# ----- Hibbett 2007 updated upper fungal taxonomy -----

def prepare_h2007(ott):
    h2007 = taxonomies.load_h2007()
    return h2007

# ----- Index Fungorum -----
# IF is pretty comprehensive for Fungi, but has an assortment of other
# things, mostly eukaryotic microbes.  We should treat the former as
# more authoritative than NCBI, and the latter as less authoritative
# than NCBI.

def prepare_fungorum(ott):
    fungorum = taxonomies.load_fung()
    fungorum.setTarget(ott)

    fungi_root = fungorum.taxon('Fungi')
    fungi = fungorum.select(fungi_root)
    fungi_root.trim()

    print "Fungi in Index Fungorum has %s nodes"%fungi.count()

    # *** Alignment to SILVA

    # 2014-03-07 Prevent a false match
    # https://groups.google.com/d/msg/opentreeoflife/5SAPDerun70/fRjA2M6z8tIJ
    # This is a fungus in Pezizomycotina
    # ### CHECK: was silva.taxon('Phaeosphaeria')
    # ott.notSame(ott.taxon('Phaeosphaeria', 'Rhizaria'), fungi.taxon('Phaeosphaeria', 'Ascomycota'))

    # 2014-04-08 This was causing Agaricaceae to be paraphyletic
    # ### CHECK: was silva.taxon('Morganella')
    # ott.notSame(ott.taxon('Morganella'), fungi.taxon('Morganella'))

    # 2014-04-08 More IF/SILVA bad matches
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/63
    # The notSame directives are unnecessary if SAR is a division
    for name in ['Acantharia',     # in Venturiaceae < Fungi < Opisth. / Rhizaria < SAR
                 'Steinia',        # in Lecideaceae < Fungi / Alveolata / insect < Holozoa in irmng
                 'Epiphloea',      # in Pezizomycotina < Opisth. / Rhodophyta  should be OK, Rh. is a division
                 'Campanella',     # in Agaricomycotina < Nuclet. / SAR / Cnidaria
                 'Lacrymaria',     # in Agaricomycotina / SAR
                 #'Phialina',       # in Pezizomycotina   - not in fung
                 #'Frankia',        # in Pezizomycotina / Bacteria - not even in latest fung
                 'Bogoriella',     # in Verrucariaceae < Pezizomycotina < Euk. / Bogoriellaceae < Bacteria  should be ok
                 ]:
        # ### CHECK: was silva.taxon
        ott.notSame(ott.taxon(name), fungi.taxon(name, 'Fungi'))
    # Trichoderma harzianum, Sclerotinia homoeocarpa, Puccinia
    # triticina are removed from SILVA early
                 
    # 2014-04-25 JAR
    # There are three Bostrychias: a rhodophyte, a fungus, and a bird.
    # The fungus name is a synonym for Cytospora.
        # ### CHECK: was silva.taxon
    if fungi.maybeTaxon('Bostrychia', 'Ascomycota') != None:
        ott.notSame(ott.taxon('Bostrychia', 'Rhodophyceae'),
                    fungi.taxon('Bostrychia', 'Ascomycota'))

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
        # ### CHECK: was silva.taxon
    ott.notSame(ott.taxon('Podocystis', 'Stramenopiles'), fungi.taxon('Podocystis', 'Fungi'))

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    if False:
        ott.same(fungorum.maybeTaxon('Choanoflagellida'),
                 ott.maybeTaxon('Choanoflagellida', 'Opisthokonta'))

    # Create a homonym (the one in Fungi, not the same as the one in Alveolata)
    # so that the IF Ciliophora can map to it
    if ott.maybeTaxon('Ciliophora', 'Fungi') == None:
        cil = ott.newTaxon('Ciliophora', 'genus', 'if:7660')
        cil.setId('5343665')
        ott.taxon('Fungi').take(cil)
        cil.incertaeSedis()

    # Create a homonym (the one in Fungi, not the same as the one in Rhizaria)
    # so that the IF Phaeosphaeria can map to it
    if ott.maybeTaxon('Phaeosphaeria', 'Fungi') == None:
        cil = ott.newTaxon('Phaeosphaeria', 'genus', 'if:3951')
        cil.setId('5486272')
        ott.taxon('Fungi').take(cil)
        cil.incertaeSedis()

    return (fungi, fungorum)

# ----- Lamiales taxonomy from study 713 -----
# http://dx.doi.org/10.1186/1471-2148-10-352

def prepare_lamiales(ott):
    study713  = taxonomies.load_713()
        # ### CHECK: was silva.taxon
    ott.notSame(study713.taxon('Buchnera', 'Orobanchaceae'), ott.taxon('Buchnera', 'Enterobacteriaceae'))
    return study713

# ----- WoRMS -----

def prepare_worms(ott):
    worms = taxonomies.load_worms()
    worms.taxon('Viruses').prune("make-ott.py")

    # Malacostraca instead of Decapoda because it's in the skeleton
    mal = worms.taxon('Malacostraca')
    malacostraca = worms.select(mal)
    mal.trim()
    worms_sans_malacostraca = worms

    # Alignment

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    if False:
        ott.same(ott.taxon('Choanoflagellida', 'Opisthokonta'),
                 worms_sans_malacostraca.taxon('Choanoflagellida'))

    return (malacostraca, worms_sans_malacostraca)

# ----- NCBI Taxonomy -----

def prepare_ncbi(ott):

    ncbi = taxonomies.load_ncbi()

    # David Hibbett has requested that for Fungi, only Index Fungorum
    # should be seen.  Rather than delete the NCBI fungal taxa, we just
    # mark them 'hidden' so they can be suppressed downstream.  This
    # preserves the identifier assignments, which may have been used
    # somewhere.
    ncbi.taxon('Fungi').hideDescendantsToRank('species')

    # - Alignment to OTT -

    #ott.same(ncbi.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
    # #### Check - was fungi.taxon
    # ** No unique taxon found with this name: Burkea
    # ** No unique taxon found with this name: Coscinium
    # ** No unique taxon found with this name: Perezia
    # ott.notSame(ncbi.taxon('Burkea', 'Viridiplantae'), ott.taxon('Burkea'))
    # ott.notSame(ncbi.taxon('Coscinium', 'Viridiplantae'), ott.taxon('Coscinium'))
    # ott.notSame(ncbi.taxon('Perezia', 'Viridiplantae'), ott.taxon('Perezia'))

    # JAR 2014-04-11 Discovered during regression testing
    # now handled in other ways
    # ott.notSame(ncbi.taxon('Epiphloea', 'Rhodophyta'), ott.taxon('Epiphloea', 'Ascomycota'))

    # JAR attempt to resolve ambiguous alignment of Trichosporon in IF and
    # NCBI based on common parent and member.
    # Type = T. beigelii, which is current, according to Mycobank.
    # But I'm going to use a different 'type', Trichosporon cutaneum.
    # #### Check - was fungi.taxon
    ott.same(ott.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'),
             #ncbi.taxonThatContains('Trichosporon', 'Trichosporon cutaneum')
             ncbi.taxon('5552')
             )

    # 2014-04-23 In new version of IF - obvious misalignment
    # #### Check - was fungi.taxon
    # ott.notSame(ncbi.taxon('Crepidula', 'Gastropoda'), ott.taxon('Crepidula', 'Microsporidia'))
    # ott.notSame(ncbi.taxon('Hessea', 'Viridiplantae'), ott.taxon('Hessea', 'Microsporidia'))
    # 2014-04-23 Resolve ambiguity introduced into new version of IF
    # http://www.speciesfungorum.org/Names/SynSpecies.asp?RecordID=331593
    # #### Check - was fungi.taxon
    ott.same(ncbi.taxon('Gymnopilus spectabilis var. junonius'), ott.taxon('Gymnopilus junonius'))

    # JAR 2014-04-23 More sample contamination in SILVA 115
    # #### Check - was fungi.taxon
    # ott.same(ncbi.taxon('Lamprospora'), ott.taxon('Lamprospora', 'Pyronemataceae'))

    # JAR 2014-04-25
        # ### CHECK: was silva.taxon
    # ott.notSame(ott.taxon('Bostrychia', 'Rhodophyceae'), ncbi.taxon('Bostrychia', 'Aves'))

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    if False:
        ott.notSame(ott.maybeTaxon('Choanoflagellida', 'Ichthyosporea'),
                    ncbi.maybeTaxon('Choanoflagellida', 'Opisthokonta'))

    # Dail 2014-03-31 https://github.com/OpenTreeOfLife/feedback/issues/5
    # updated 2015-06-28 NCBI Katablepharidophyta = SILVA Kathablepharidae.
        # ### CHECK: was silva.taxon
    ott.same(ncbi.taxon('Katablepharidophyta'), ott.taxon('Kathablepharidae'))
    # was: ott.taxon('Katablepharidophyta').hide()

    # probably not needed
    ott.same(ncbi.taxon('Ciliophora', 'Alveolata'), ott.taxon('Ciliophora', 'Alveolata'))

    ott.notSame(ncbi.taxon('Diphylleia', 'Chloroplastida'),
                ott.taxonThatContains('Diphylleia', 'Diphylleia rotans'))

    # 2015-10-06 JAR noticed while debugging surprisingly large number of newly-hidden ids
    ott.notSame(ncbi.taxon('Ctenophora', 'Metazoa'),
                ott.taxon('Ctenophora', 'Stramenopiles')) # from SILVA, also GBIF, IRMNG, GBIF

    return ncbi

# ----- GBIF (Global Biodiversity Information Facility) taxonomy -----

def prepare_gbif(ott):

    gbif = taxonomies.load_gbif()
    gbif.setTarget(ott)

    gbif.taxon('Viruses').hide()

    # Fungi suppressed at David Hibbett's request
    gbif.taxon('Fungi').hideDescendantsToRank('species')

    # Suppressed at Laura Katz's request
    gbif.taxon('Bacteria','life').hideDescendants()
    gbif.taxon('Archaea','life').hideDescendants()

    # - Alignment -

    #ott.same(gbif.taxon('Cyanobacteria'), silva.taxon('Cyanobacteria','Cyanobacteria')) #'D88288/#3'

    # Automatic alignment makes the wrong choice for this one
    # ott.same(ncbi.taxon('5878'), gbif.taxon('10'))    # Ciliophora
    ott.same(ott.taxon('Ciliophora', 'Alveolata'), gbif.taxon('10'))  # in Protozoa
    # Not needed?
    # ott.same(ott.taxon('Ciliophora', 'Ascomycota'), gbif.taxon('3269382')) # in Fungi

    # Automatic alignment makes the wrong choice for this one
    # NCBI says ncbi:29178 is in Rhizaria in Eukaryota and contains Allogromida (which is not in GBIF)
    # OTT 2.8 has 936399 = in Retaria (which isn't in NCBI) extinct_inherited ? - no good.
    # GBIF 389 is in Protozoa... but it contains nothing!!  No way to identify it other than by id.
    #   amoeboid ...
    ott.same(ott.taxon('Foraminifera', 'Rhizaria'), gbif.taxon('389'))  # Foraminifera gbif:4983431

    # Tetrasphaera is a messy multi-way homonym
    #### Check: was ncbi.taxon
    ott.same(ott.taxon('Tetrasphaera','Intrasporangiaceae'), gbif.taxon('Tetrasphaera','Intrasporangiaceae'))

    # Bad alignments to NCBI
    # #### THESE NEED TO BE CHECKED - was ncbi.taxon

    # Labyrinthomorpha (synonym for Labyrinthulomycetes)
    # No longer in GBIF... the one in IRMNG is a Cambrian sponge-like thing
    # ott.notSame(ott.taxon('Labyrinthomorpha', 'Stramenopiles'), gbif.taxon('Labyrinthomorpha'))

    # ott.notSame(ott.taxon('Ophiurina', 'Echinodermata'), gbif.taxon('Ophiurina','Ophiurinidae'))
    #  taken care of in taxonomies.py

    # There is a test for this.  The GBIF taxon no longer exists.
    # ott.notSame(ott.taxon('Rhynchonelloidea', 'Brachiopoda'), gbif.taxon('Rhynchonelloidea'))

    # There are tests.  Seems OK
    ott.notSame(ott.taxonThatContains('Neoptera', 'Lepidoptera'), gbif.taxon('Neoptera', 'Diptera'))

    # ott.notSame(gbif.taxon('Tipuloidea', 'Chiliocyclidae'), ott.taxon('Tipuloidea', 'Diptera')) # genus Tipuloidea
    #  taken care of in taxonomies.py
    # ### CHECK: was silva.taxon
    # SILVA = GN013951 = Tetrasphaera (bacteria)

    ott.notSame(ott.taxon('Tetrasphaera', 'Intrasporangiaceae'),
                gbif.taxon('Gorkadinium', 'Dinophyta')) # = Tetrasphaera in Protozoa

    # Rick Ree 2014-03-28 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/37
    # ### CHECK: was ncbi.taxon
    ott.same(ott.taxon('Calothrix', 'Rivulariaceae'), gbif.taxon('Calothrix', 'Rivulariaceae'))
    ott.same(ott.taxon('Chlorella', 'Chlorellaceae'), gbif.taxon('Chlorella', 'Chlorellaceae'))
    ott.same(ott.taxon('Myrmecia', 'Microthamniales'), gbif.taxon('Myrmecia', 'Microthamniales'))

    # JAR 2014-04-18 attempt to resolve ambiguous alignment of
    # Trichosporon in IF and GBIF based on common member
    # ott.same(fungorum.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'),
    #          gbif.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))
    # doesn't work.  brute force.
    # was: ott.same(fungorum.taxon('10296'), gbif.taxon('2518163')) = ott:364222
    ##### RECOVER THIS IF NECESSARY
    # ott.same(fungi.taxon('10296'), ott.taxon('364222'))

    # Obviously the same genus, can't tell what's going on
    # if:17806 = Hygrocybe = ott:282216
    # #### CHECK: was fungi
    ott.same(gbif.taxon('Hygrocybe'), ott.taxon('Hygrocybe', 'Hygrophoraceae'))

    # JAR 2014-04-23 More sample contamination in SILVA 115
    # redundant
    # ott.same(gbif.taxon('Lamprospora'), fungi.taxon('Lamprospora'))

    # JAR 2014-04-23 IF update fallout
    # ### CHECK: was ncbi.taxon
    ott.same(gbif.taxonThatContains('Penicillium', 'Penicillium expansum'),
             ott.taxonThatContains('Penicillium', 'Penicillium expansum'))

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    # ### CHECK: was ncbi.taxon
    if False:
        ott.same(gbif.taxon('Choanoflagellida'),
                 ott.taxon('Choanoflagellida', 'Opisthokonta'))

    return gbif
# ----- Interim Register of Marine and Nonmarine Genera (IRMNG) -----

def prepare_irmng(ott):

    irmng = taxonomies.load_irmng()

    irmng.taxon('Viruses').hide()

    # Fungi suppressed at David Hibbett's request
    irmng.taxon('Fungi').hideDescendantsToRank('species')

    # Microbes suppressed at Laura Katz's request
    irmng.taxon('Bacteria','life').hideDescendants()
    irmng.taxon('Archaea','life').hideDescendants()

    ott.same(ott.taxon('Veronica', 'Plantaginaceae'), irmng.taxon('1381293'))  # ott:648853
    # genus Tipuloidea (not superfamily) ott:5708808 = gbif:6101461
    # Taken care of in assemble_ott.py:
    # ott.same(ott.taxon('Tipuloidea', 'Dicondylia'), irmng.taxon('1170022'))
    # IRMNG has four Tetrasphaeras.
    ott.same(ott.taxon('Tetrasphaera','Intrasporangiaceae'), irmng.taxon('Tetrasphaera','Intrasporangiaceae'))
    ott.same(ott.taxon('Gorkadinium','Dinophyceae'), irmng.taxon('Gorkadinium','Dinophyceae'))

    # JAR 2014-04-18 attempt to resolve ambiguous alignment of
    # Trichosporon in IF and IRMNG based on common parent and member
    # Trichosporon in IF = if:10296 genus in Trichosporonaceae
    ott.same(irmng.taxon('Trichosporon'), ott.taxon('Trichosporon', 'Trichosporonaceae'))

    # JAR 2014-04-24 false match
    # tests pass
    ott.notSame(irmng.taxon('Protaspis', 'Chordata'), ott.taxon('Protaspis', 'Cercozoa'))

    # JAR 2014-04-18 while investigating hidden status of Coscinodiscus radiatus
    # tests
    ott.notSame(irmng.taxon('Coscinodiscus', 'Porifera'), ott.taxon('Coscinodiscus', 'Stramenopiles'))

    # https://github.com/OpenTreeOfLife/feedback/issues/45
    # IRMNG has Choanoflagellida < Zoomastigophora < Sarcomastigophora < Protozoa
    # might be better to look for something it contains
    ott.same(irmng.taxon('Choanoflagellida', 'Zoomastigophora'),
             ott.taxon('Choanoflagellida', 'Eukaryota'))

    # probably not needed
    ott.same(ott.taxon('Ciliophora', 'Alveolata'), irmng.taxon('239'))  # in Protista
    # Gone away...
    # ott.same(ott.taxon('Ciliophora', 'Ascomycota'), irmng.taxon('Ciliophora', 'Ascomycota'))

    # Could force this to not match the arthropod.  But much easier just to delete it.
    irmng.taxon('Morganella', 'Brachiopoda').prune(this_source)
    #  ... .notSame(ott.taxon('Morganella', 'Arthropoda'))

    # 2015-09-10 Inclusion test failing
    ott.notSame(irmng.taxon('Retaria', 'Brachiopoda'), # irmng:1340611
                ott.taxon('Retaria', 'Rhizaria'))

    # 2015-09-10 Inclusion test failing
    ott.notSame(irmng.taxon('Campanella', 'Cnidaria'), # irmng:1289625
                ott.taxon('Campanella', 'SAR'))

    return irmng

# ----- Final patches -----

def patch_ott(ott):

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
    ott.taxon('Chlamydotomus').incertaeSedis()

    # Joseph Brown 2014-01-27
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/87
    # Occurs as Sakesphorus bernardi in ncbi, gbif, irmng, as Thamnophilus bernardi in bgif
    ott.taxon('Thamnophilus bernardi').absorb(ott.taxon('Sakesphorus bernardi'))
    ott.taxon('Thamnophilus melanonotus').absorb(ott.taxon('Sakesphorus melanonotus'))
    ott.taxon('Thamnophilus melanothorax').absorb(ott.taxon('Sakesphorus melanothorax'))
    ott.taxon('Thamnophilus bernardi').synonym('Sakesphorus bernardi')
    ott.taxon('Thamnophilus melanonotus').synonym('Sakesphorus melanonotus')
    ott.taxon('Thamnophilus melanothorax').synonym('Sakesphorus melanothorax')

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

    # Not fungi - Romina 2014-01-28
    # ott.taxon('Adlerocystis').show()  - it's Chromista ...
    # Index Fungorum says Adlerocystis is Chromista, but I don't believe it
    # ott.taxon('Chromista').take(ott.taxon('Adlerocystis','Fungi'))

    # Adlerocystis seems to be a fungus, but unclassified - JAR 2014-03-10
    ott.taxon('Adlerocystis').incertaeSedis()

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
    for name in ['Pinophyta', 'Pteridophyta', 'Gymnospermophyta']:
        if ott.maybeTaxon(name,'Chloroplastida'):
            ott.taxon(name,'Chloroplastida').incertaeSedis()

    # Patches from the Katz lab to give decent parents to taxa classified
    # as Chromista or Protozoa
    print '-- Chromista/Protozoa spreadsheet from Katz lab --'
    fixChromista(ott)

    print '-- more patches --'

    # From Laura and Dail on 5 Feb 2014
    # https://groups.google.com/d/msg/opentreeoflife/a69fdC-N6pY/y9QLqdqACawJ
    tax = ott.maybeTaxon('Chlamydiae/Verrucomicrobia group')
    if tax != None and tax.name != 'Bacteria':
        tax.rename('Verrucomicrobia group')
    ott.taxon('Heterolobosea','Discicristata').absorb(ott.taxon('Heterolobosea','Percolozoa'))
    ott.taxon('Excavata','Eukaryota').take(ott.taxon('Oxymonadida','Eukaryota'))

    # Work in progress - Joseph
    if ott.maybeTaxon('Reptilia') != None:
        ott.taxon('Reptilia').hide()

    # Chris Owen patches 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/88
    ott.taxon('Protostomia').take(ott.taxon('Chaetognatha','Deuterostomia'))
    ott.taxon('Lophotrochozoa').take(ott.taxon('Platyhelminthes'))
    ott.taxon('Polychaeta','Annelida').take(ott.taxon('Myzostomida'))
    ott.taxon('Lophotrochozoa').take(ott.taxon('Gnathostomulida'))
    ott.taxon('Bilateria').take(ott.taxon('Acoela'))
    ott.taxon('Bilateria').take(ott.taxon('Xenoturbella'))
    ott.taxon('Bilateria').take(ott.taxon('Nemertodermatida'))
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
    ott.taxon('Boraginaceae').absorb(ott.taxon('Hydrophyllaceae'))
    ott.taxon('Boraginales').take(ott.taxon('Boraginaceae'))
    ott.taxon('lamiids').take(ott.taxon('Boraginales'))

    # Bryan Drew 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/90
    # Vahlia 26024 <- Vahliaceae 23372 <- lammids 596112 (was incertae sedis)
    ott.taxon('lamiids').take(ott.taxon('Vahliaceae'))

    # Bryan Drew 2014-01-30
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/90
    # http://www.sciencedirect.com/science/article/pii/S0034666703000927
    ott.taxon('Araripia').extinct()

    # Bryan Drew  2014-02-05
    # http://www.mobot.org/mobot/research/apweb/
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/92
    ott.taxon('Viscaceae').rename('Visceae')
    ott.taxon('Amphorogynaceae').rename('Amphorogyneae')
    ott.taxon('Thesiaceae').rename('Thesieae')
    ott.taxon('Santalaceae').take(ott.taxon('Visceae'))
    ott.taxon('Santalaceae').take(ott.taxon('Amphorogyneae'))
    ott.taxon('Santalaceae').take(ott.taxon('Thesieae'))
    ott.taxon('Santalaceae').absorb(ott.taxon('Cervantesiaceae'))
    ott.taxon('Santalaceae').absorb(ott.taxon('Comandraceae'))

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
    for name in ['Homo sapiens neanderthalensis',
                 'Homo sapiens ssp. Denisova',
                 'Homo habilis',
                 'Homo erectus',
                 'Homo cepranensis',
                 'Homo georgicus',
                 'Homo floresiensis',
                 'Homo kenyaensis',
                 'Homo rudolfensis',
                 'Homo antecessor',
                 'Homo ergaster',
                 'Homo okotensis']:
        tax = ott.maybeTaxon(name)
        if tax != None:
            tax.extinct()

    # JAR 2014-03-07 hack to prevent H.s. from being extinct due to all of
    # its subspecies being extinct.
    # I wish I knew what the authority for the H.s.s. name was.
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
    # no evidence given
    ott.taxonThatContains('Bacteria', 'Lentisphaerae').take(ott.taxon('Lentisphaerae'))

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

    # "Old" patch system
    ott.edit('feed/ott/edits/')

    # This is a randomly chosen bivalve to force Bivalvia to not be extinct
    ott.taxon('Corculum cardissa', 'Bivalvia').extant()
    # Similarly for roaches
    ott.taxon('Periplaneta americana', 'Blattodea').extant()

def get_default_extinct_info_from_gbif(gbif, ott):
    infile = open('tax/gbif/paleo.tsv')
    paleos = 0
    flagged = 0
    for row in infile:
        paleos += 1
        id = row.strip()
        gtaxon = gbif.lookupId(id)
        if gtaxon != None:
            taxon = ott.image(gtaxon)
            if taxon != None:
                if len(taxon.sourceIds) == 1:
                    flagged += 1
                    taxon.extinct()
    infile.close()
    print '| Flagged %s of %s taxa from paleodb\n' % (flagged, paleos)

def unextinct_ncbi(ncbi, ott):
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/68
    # 'Extinct' would really mean 'extinct and no sequence' with this change
    print 'Non-extincting NCBI'

    def recur(node):
        unode = ott.image(node)
        if node.children == None:
            if unode == None:
                return True
            else:
                return unode.isAnnotatedExtinct()
        else:
            inct = True
            for child in node.children:
                inct = inct and recur(child)
            if not inct:
                if unode != None and unode.isAnnotatedExtinct():
                    # Contains a possibly extant descendant...
                    print 'Changing from extinct to extant', unode.name, unode.id
                    unode.extant()
                return False
            else:
                return True

    for node in ncbi.roots():
        recur(node)


# Reports

def report_on_h2007(h2007, ott):
    # https://github.com/OpenTreeOfLife/reference-taxonomy/issues/40
    print '-- Checking realization of h2007'
    for taxon in h2007:
        im = ott.image(taxon)
        if im != None:
            if im.children == None:
                print '** Barren taxon from h2007', taxon.name
        else:
            print '** Missing taxon from h2007', taxon.name

def report(ott):

    print '-- Parent/child homonyms'
    ott.parentChildHomonymReport()

    print '-- Inclusion tests'
    check_inclusions.check(ott)

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
                     'Bostrychia',
                     'Choanoflagellida',
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
                     ]

