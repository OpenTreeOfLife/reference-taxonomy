# Jython script to build the Open Tree reference taxonomy

from org.opentreeoflife.smasher import Taxonomy
import sys
sys.path.append("feed/misc/")
from chromista_spreadsheet import fixChromista

ott = Taxonomy.newTaxonomy()

# ----- Hibbett 2007 updated upper fungal taxonomy -----
h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')

# 2014-04-08 Misspelling
h2007.taxon('Chaetothryriomycetidae').rename('Chaetothyriomycetidae')

ott.absorb(h2007)

# h2007/if synonym https://github.com/OpenTreeOfLife/reference-taxonomy/issues/40
ott.taxon('Urocystales').synonym('Urocystidales')

# ----- SILVA microbial taxonomy -----
silva = Taxonomy.getTaxonomy('tax/silva/', 'silva')

# Deal with parent/child homonyms in SILVA.
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

ott.absorb(silva)

# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/30
# https://github.com/OpenTreeOfLife/feedback/issues/5
for name in ['GAL08', 'GOUTA4', 'JL-ETNP-Z39', 'Kazan-3B-28',
			 'LD1-PA38', 'MVP-21', 'NPL-UPA2', 'OC31', 'RsaHF231',
			 'S2R-29', 'SBYG-2791', 'SM2F11', 'WCHB1-60', 'T58',
			 'LKM74', 'LEMD255', 'CV1-B1-93', 'H1-10', 'H26-1',
			 'M1-18D08', 'D4P07G08', 'DH147-EKD10', 'LG25-05',
			 'NAMAKO-1', 'RT5iin25', 'SA1-3C06', 'DH147-EKD23']:
    ott.taxon(name).elide()

# Lamiales taxonomy from study 713
# http://dx.doi.org/10.1186/1471-2148-10-352
study713  = Taxonomy.getTaxonomy('tax/713/', 'study713')
ott.notSame(study713.taxon('Buchnera'), silva.taxon('Buchnera'))
ott.absorb(study713)

# ----- Index Fungorum -----
fung  = Taxonomy.getTaxonomy('tax/if/', 'if')

# JAR 2014-04-11 Missing in earlier IF, mistake in later IF -
# extraneous authority string
cyph = fung.maybeTaxon('Cyphellopsis')
if cyph == None:
	cyph = fung.maybeTaxon('Cyphellopsis Donk 1931')
	if cyph != None:
		cyph.rename('Cyphellopsis')
	else:
		cyph = fung.newTaxon('Cyphellopsis', 'genus', 'if:17439')
fung.taxon('Niaceae').take(cyph)

# smush will fold sibling taxa that have the same name.
fung.smush()

# 2014-03-07 Prevent a false match
ott.notSame(silva.taxon('Phaeosphaeria'), fung.taxon('Phaeosphaeria'))

# 2014-04-08 This was causing Agaricaceae to become 'tattered'
ott.notSame(silva.taxon('Morganella'), fung.taxon('Morganella'))

# 2014-04-08 More IF/SILVA bad matches (probably sample contamination)
# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/63
# These will probably be fixed in SILVA 117
for name in ["Trichoderma harzianum",
			 "Acantharia",
			 "Bogoriella",
			 "Steinia",
			 "Sclerotinia homoeocarpa",
			 "Epiphloea",
			 "Campanella",
			 "Lacrymaria"]:
	ott.notSame(silva.taxon(name), fung.taxon(name))

# Romina email to JAR 2014-04-09
# IF has both Hypocrea and Trichoderma.  Hypocrea is the right name.
fung.taxon('Trichoderma viride').rename('Hypocrea rufa')  # Type
fung.taxon('Hypocrea').absorb(fung.taxonThatContains('Trichoderma', 'Hypocrea rufa'))

# Romina https://github.com/OpenTreeOfLife/reference-taxonomy/issues/42
fung.taxon('Trichoderma deliquescens').rename('Hypocrea lutea')

# 2014-04-14 Bad Fungi homonyms in new version of IF
if fung.maybeTaxon('90154') != None:
	fung.taxon('90154').prune()
if fung.maybeTaxon('90155') != None:
	fung.taxon('90155').prune()

# analyzeMajorRankConflicts sets the "major_rank_conflict" flag when
# intermediate ranks are missing (e.g. a family that's a child of a
# class)
fung.analyzeMajorRankConflicts()

ott.absorb(fung)

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

# Don't know what to do about Chlamydotomus cellaris or the genus.
# Genus is not in Mycobank or IRMNG... and I don't know whether T. cellaris
# would be valid.  Leave as is I guess, and set incertae sedis

# 2014-04-13 Romina #40, #60
for foo in [('Neozygitales', ['Neozygitaceae']),
			('Asteriniales', ['Asterinaceae']),
			('Savoryellales', ['Savoryella', 'Ascotaiwania', 'Ascothailandia']), 
			('Cladochytriales', ['Cladochytriaceae', 'Nowakowskiellaceae', 'Septochytriaceae', 'Endochytriaceae']),
			('Jaapiales', ['Jaapiaceae']),
			('Coniocybales', ['Coniocybaceae']),
			('Hyaloraphidiales', ['Hyaloraphidiaceae']),
			('Mytilinidiales', ['Mytilinidiaceae', 'Gloniaceae'])]:
	order = ott.taxon(foo[0])
	for family in foo[1]:
		order.take(ott.taxon(family))

# ** No taxon found with this name: Nowakowskiellaceae
# ** No taxon found with this name: Septochytriaceae
# ** No taxon found with this name: Jaapiaceae
# ** (null=if:81865 Rhizocarpaceae) is already a child of (null=h2007:212 Rhizocarpales)
# ** No taxon found with this name: Hyaloraphidiaceae


# ----- NCBI Taxonomy -----
ncbi  = Taxonomy.getTaxonomy('tax/ncbi/', 'ncbi')
ncbi.taxon('Viruses').hide()

# David Hibbett has requested that for Fungi, only Index Fungorum
# should be seen.  Rather than delete the NCBI fungal taxa, we just
# mark them 'hidden' so they can be suppressed downstream.  This
# preserves the identifier assignments, which may have been used
# somewhere.
ncbi.taxon('Fungi').hideDescendantsToRank('species')

#ott.same(ncbi.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
ott.notSame(ncbi.taxon('Burkea'), fung.taxon('Burkea'))
ott.notSame(ncbi.taxon('Coscinium'), fung.taxon('Coscinium'))
ott.notSame(ncbi.taxon('Perezia'), fung.taxon('Perezia'))

# JAR 2014-04-11 Discovered during regression testing
ott.notSame(ncbi.taxon('Epiphloea', 'Rhodophyta'), fung.taxon('Epiphloea', 'Lichinaceae'))

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

# Romina email to JAR 2014-04-09
# NCBI has both Hypocrea and Trichoderma.
ncbi.taxon('Trichoderma viride').rename('Hypocrea rufa')  # Type
ncbi.taxon('Hypocrea').absorb(ncbi.taxonThatContains('Trichoderma', 'Hypocrea rufa'))

# JAR attempt to resolve ambiguous alignment of Trichosporon in IF and
# NCBI based on common parent and member.
# Type = T. beigelii, which is current, according to Mycobank.
# But I'm going to use a different 'type', Trichosporon cutaneum.
ott.same(fung.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'), ncbi.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))

# analyzeOTUs sets flags on questionable taxa ("unclassified",
#  hybrids, and so on) to allow the option of suppression downstream
ncbi.analyzeOTUs()

ott.absorb(ncbi)

# 2014-01-27 Joseph: Quiscalus is incorrectly in Fringillidae instead
# of Icteridae.  NCBI is wrong, GBIF is correct.
ott.taxon('Icteridae').take(ott.taxon('Quiscalus', 'Fringillidae'))

# Misspelling in GBIF... seems to already be known
# ott.taxon("Torricelliaceae").synonym("Toricelliaceae")


# ----- GBIF (Global Biodiversity Information Facility) taxonomy -----
gbif  = Taxonomy.getTaxonomy('tax/gbif/', 'gbif')
gbif.smush()
gbif.taxon('Viruses').hide()

# Fungi suppressed at David Hibbett's request
gbif.taxon('Fungi').hideDescendantsToRank('species')

# Microbes suppressed at Laura Katz's request
gbif.taxon('Bacteria','life').hideDescendants()
gbif.taxon('Protozoa','life').hideDescendants()
gbif.taxon('Archaea','life').hideDescendants()
gbif.taxon('Chromista','life').hideDescendants()

#ott.same(gbif.taxon('Cyanobacteria'), silva.taxon('Cyanobacteria','Cyanobacteria')) #'D88288/#3'

# Automatic merge makes the wrong choice for the following two
ott.same(ncbi.taxon('5878'), gbif.taxon('10'))	  # Ciliophora
ott.same(ncbi.taxon('29178'), gbif.taxon('389'))  # Foraminifera

ott.same(ncbi.taxon('Tetrasphaera','Intrasporangiaceae'), gbif.taxon('Tetrasphaera','Intrasporangiaceae'))
ott.notSame(silva.taxon('Retaria'), gbif.taxon('Retaria'))

# Rod Page blogged about this one
gbif.taxon('Jungermanniales','Marchantiophyta').absorb(gbif.taxon('Jungermanniales','Bryophyta'))

# Bad synonym in NCBI
ott.notSame(ncbi.taxon('Labyrinthomorpha'), gbif.taxon('Labyrinthomorpha'))
ott.notSame(ncbi.taxon('Ophiurina'), gbif.taxon('Ophiurina','Ophiurinidae'))
ott.notSame(ncbi.taxon('Rhynchonelloidea'), gbif.taxon('Rhynchonelloidea'))
ott.notSame(ncbi.taxon('Neoptera'), gbif.taxon('Neoptera', 'Diptera'))
ott.notSame(gbif.taxon('6101461'), ncbi.taxon('Tipuloidea')) # genus Tipuloidea
ott.notSame(silva.taxon('GN013951'), gbif.taxon('Gorkadinium')) #Tetrasphaera

# Joseph 2013-07-23 https://github.com/OpenTreeOfLife/opentree/issues/62
# GBIF has two copies of Myospalax
gbif.taxon('6006429').absorb(gbif.taxon('2439119'))

# Rick Ree 2014-03-28 https://github.com/OpenTreeOfLife/reference-taxonomy/issues/37
ott.same(ncbi.taxon('Calothrix', 'Rivulariaceae'), gbif.taxon('Calothrix', 'Rivulariaceae'))
ott.same(ncbi.taxon('Chlorella', 'Chlorellaceae'), gbif.taxon('Chlorella', 'Chlorellaceae'))
ott.same(ncbi.taxon('Myrmecia', 'Microthamniales'), gbif.taxon('Myrmecia', 'Microthamniales'))

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

# Romina email to JAR 2014-04-09
# GBIF has both Hypocrea and Trichoderma.  And it has four Trichoderma synonyms...
gbif.taxon('Trichoderma viride').rename('Hypocrea rufa')  # Type
gbif.taxon('Hypocrea').absorb(gbif.taxonThatContains('Trichoderma', 'Hypocrea rufa'))

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

# JAR 2014-04-18 attempt to resolve ambiguous alignment of
# Trichosporon in IF and GBIF based on common member
ott.same(fung.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'), gbif.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))

gbif.analyzeMajorRankConflicts()
ott.absorb(gbif)


# Joseph 2014-01-27 https://code.google.com/p/gbif-ecat/issues/detail?id=104
ott.taxon('Parulidae').take(ott.taxon('Myiothlypis', 'Passeriformes'))
# I don't get why this one isn't a major_rank_conflict !?
ott.taxon('Blattaria').take(ott.taxon('Phyllodromiidae'))


# ----- Interim Register of Marine and Nonmarine Genera (IRMNG) -----

irmng = Taxonomy.getTaxonomy('tax/irmng/', 'irmng')
irmng.smush()
irmng.taxon('Viruses').hide()

# Fungi suppressed at David Hibbett's request
irmng.taxon('Fungi').hideDescendantsToRank('species')

irmng.taxon('1413316').prune() #Neopithecus in Mammalia
irmng.taxon('1413315').extinct() #Neopithecus in Primates (Pongidae)
ott.same(gbif.taxon('3172047'), irmng.taxon('1381293'))  # Veronica
ott.same(gbif.taxon('6101461'), irmng.taxon('1170022')) # genus Tipuloidea
# IRMNG has four Tetrasphaeras.
ott.same(ncbi.taxon('Tetrasphaera','Intrasporangiaceae'), irmng.taxon('Tetrasphaera','Intrasporangiaceae'))
ott.same(gbif.taxon('Gorkadinium','Dinophyceae'), irmng.taxon('Gorkadinium','Dinophyceae'))
irmng.analyzeMajorRankConflicts()

irmng.taxon('Unaccepted').hide()

# Microbes suppressed at Laura Katz's request
irmng.taxon('Bacteria','life').hideDescendants()
irmng.taxon('Protista','life').hideDescendants()
irmng.taxon('Archaea','life').hideDescendants()

# RR #50
irmng.taxon('Saxo-Fridericia').rename('Saxofridericia')
irmng.taxon('Saxofridericia').absorb(irmng.taxon('Saxo-fridericia'))

# Romina email to JAR 2014-04-09
# IRMNG has EIGHT different Trichodermas.  (Four are synonyms of other things.)
# 1307461 = Trichoderma Persoon 1794, in Hypocreaceae
irmng.taxon('Hypocrea').absorb(irmng.taxon('1307461'))

# JAR 2014-04-18 attempt to resolve ambiguous alignment of
# Trichosporon in IF and IRMNG based on common parent and member
ott.same(fung.taxon('Trichosporon'), irmng.taxon('Trichosporon'))


ott.absorb(irmng)


# ----- Final patches -----

# Finished loading source taxonomies.  Now patch things up.

# See above (occurs in both IF and GBIF).  Also see #67
ott.taxon('Chlamydotomus').incertaeSedis()

# Joseph Brown email to JAR 2014-01-27
ott.taxon('Thamnophilus bernardi').absorb(ott.taxon('Sakesphorus bernardi'))
ott.taxon('Thamnophilus melanonotus').absorb(ott.taxon('Sakesphorus melanonotus'))
ott.taxon('Thamnophilus melanothorax').absorb(ott.taxon('Sakesphorus melanothorax'))
ott.taxon('Thamnophilus bernardi').synonym('Sakesphorus bernardi')
ott.taxon('Thamnophilus melanonotus').synonym('Sakesphorus melanonotus')
ott.taxon('Thamnophilus melanothorax').synonym('Sakesphorus melanothorax')

# Mammals
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
# Need to hide it because it clutters top level of Fungi
ott.taxon('Amylophagus','Fungi').incertaeSedis()

# Bad synonym - Tony Rees 2014-01-28
ott.taxon('Lemania pluvialis').prune()

#Pinophyta and daughters need to be deleted; - Bryan 2014-01-28
#Lycopsida and daughters need to be deleted;
#Pteridophyta and daughters need to be deleted;
#Gymnospermophyta and daughters need to be deleted;
ott.taxon('Pinophyta','Chloroplastida').incertaeSedis()
ott.taxon('Pteridophyta','Chloroplastida').incertaeSedis()
ott.taxon('Gymnospermophyta','Chloroplastida').incertaeSedis()

# Patches from the Katz lab to give decent parents to taxa classified
# as Chromista or Protozoa
print '-- Chromista/Protozoa spreadsheet from Katz lab --'
fixChromista(ott)

print '-- more patches --'

# From Laura and Dail on 5 Feb 2014
# https://groups.google.com/forum/#!topic/opentreeoflife/a69fdC-N6pY
ott.taxon('Chlamydiae/Verrucomicrobia group').rename('Verrucomicrobia group')
ott.taxon('Heterolobosea','Discicristata').absorb(ott.taxon('Heterolobosea','Percolozoa'))
ott.taxon('Excavata','Eukaryota').take(ott.taxon('Oxymonadida','Eukaryota'))

# Work in progress - Joseph
ott.taxon('Reptilia').hide()

# Chris Owen patches 2014-01-30
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
ott.taxon('Salazaria mexicana').rename('Scutellaria mexicana')
ott.taxon('Scutellaria','Lamiaceae').absorb(ott.taxon('Salazaria'))

#  Move children of Lophoziaceae into Scapaniaceae [in order Jungermanniales]
ott.taxon('Scapaniaceae').absorb(ott.taxon('Lophoziaceae'))

#  Make an order Boraginales that contains Boraginaceae + Hydrophyllaceae
#  http://dx.doi.org/10.1111/cla.12061
# Bryan Drew email to JAR on 2013-09-30
ott.taxon('Boraginaceae').absorb(ott.taxon('Hydrophyllaceae'))
ott.taxon('Boraginales').take(ott.taxon('Boraginaceae'))
ott.taxon('lamiids').take(ott.taxon('Boraginales'))

# Bryan Drew email to JAR and SAS 2014-01-30
# Vahlia 26024 <- Vahliaceae 23372 <- lammids 596112 (was incertae sedis)
ott.taxon('lamiids').take(ott.taxon('Vahliaceae'))

# Bryan Drew email to JAR and SAS 2014-01-30
# http://www.sciencedirect.com/science/article/pii/S0034666703000927
ott.taxon('Araripia').extinct()

# Bryan Drew email to JAR 2014-02-05
# http://www.mobot.org/mobot/research/apweb/
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
# http://dx.doi.org/10.1007/978-3-540-31051-8_2
ott.taxon('Alseuosmiaceae').take(ott.taxon('Platyspermation'))

# JAR 2014-02-24.  We are getting extinctness information for genus
# and above from IRMNG, but not for species.
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
	ott.taxon(name).extinct()

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
# http://tolweb.org/tree?group=Temnospondyli
ott.taxon('Temnospondyli').extinct()
# https://en.wikipedia.org/wiki/Eobatrachus
ott.taxon('Eobatrachus').extinct()
# https://en.wikipedia.org/wiki/Vulcanobatrachus
ott.taxon('Vulcanobatrachus').extinct()
# https://en.wikipedia.org/wiki/Beelzebufo
ott.taxon('Beelzebufo').extinct()
# https://en.wikipedia.org/wiki/Iridotriton
ott.taxon('Iridotriton').extinct()
# https://en.wikipedia.org/wiki/Baurubatrachus
ott.taxon('Baurubatrachus').extinct()

# Dail 2014-03-31 https://github.com/OpenTreeOfLife/feedback/issues/5
ott.taxon('Katablepharidophyta').hide()

# Dail 2014-03-31 https://github.com/OpenTreeOfLife/feedback/issues/4
# no evidence given
ott.taxonThatContains('Bacteria', 'Lentisphaerae').take(ott.taxon('Lentisphaerae'))

# David Hibbett 2014-04-02 misspelling in h2007 file
# (Dacrymecetales is 'no rank', Dacrymycetes is a class)
if ott.taxon('Dacrymecetales') != None:
	ott.taxon('Dacrymecetales').rename('Dacrymycetes')

# Dail https://github.com/OpenTreeOfLife/feedback/issues/6
ott.taxon('Telonema').synonym('Teleonema')

# Joseph https://github.com/OpenTreeOfLife/reference-taxonomy/issues/43
ott.taxon('Lorisiformes').take(ott.taxon('Lorisidae'))

# Romina https://github.com/OpenTreeOfLife/reference-taxonomy/issues/42
ott.taxon('Cyphellopsis','Cyphellaceae').unhide()
ott.taxon('Cyphellopsis','Cyphellaceae').absorb(ott.taxon('Cyphellopsis','Niaceae'))
ott.taxon('Diaporthaceae').take(ott.taxon('Phomopsis'))
ott.taxon('Valsaceae').take(ott.taxon('Valsa', 'Fungi'))
ott.taxon('Agaricaceae').take(ott.taxon('Cystoderma','Fungi'))
# Invert the synonym relationship
ott.taxon('Hypocrea lutea').absorb(ott.taxon('Trichoderma deliquescens'))

# Fold Norops into Anolis
# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/31
# TBD: Change species names from Norops X to Anolis X for all X
ott.taxon('Anolis').absorb(ott.taxon('Norops', 'Iguanidae'))

# JAR 2014-04-08 - these are in study OTUs - see IRMNG
ott.taxon('Inseliellum').extant()
ott.taxon('Conus', 'Gastropoda').extant()
ott.taxon('Patelloida').extant()
ott.taxon('Phyllanthus', 'Phyllanthaceae').extant()
ott.taxon('Stelis','Orchidaceae').extant()
ott.taxon('Chloris', 'Poaceae').extant()
ott.taxon('Acropora', 'Acroporidae').extant()
ott.taxon('Diadasia').extant()

# -----------------------------------------------------------------------------
# Finish up

# "Old" patch system
ott.edit('feed/ott/edits/')

# Remove all trees but the largest 
ott.deforestate()

# Assign OTT ids to all taxa, re-using old ids when possible
ids = Taxonomy.getTaxonomy('tax/prev_ott/')

# JAR manual intervention to preserve ids
# These OTUs cases came up as ambiguous. Keep old ids.
ott.same(ids.taxon('4107132'), fung.taxon('11060')) #Cryptococcus
ott.same(ids.taxon('339002'), ncbi.taxon('3071')) #Chlorella
ott.same(ids.taxon('342868'), ncbi.taxon('56708')) #Tetraphyllidea

ott.same(fung.taxon('Trichosporon'), ids.taxonThatContains('Trichosporon', 'Trichosporon cutaneum'))

ott.assignIds(ids)

ott.parentChildHomonymReport()

# Write files
ott.dump('tax/ott/')
