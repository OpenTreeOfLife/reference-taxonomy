# Jython script to build the Open Tree reference taxonomy

from org.opentreeoflife.smasher import Taxonomy
import sys
sys.path.append("feed/misc/")
from chromista_spreadsheet import fixChromista

ott = Taxonomy.newTaxonomy()

# Hibbett 2007 updated upper fungal taxonomy
h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')
ott.absorb(h2007)

# SILVA microbial taxonomy
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
ott.absorb(silva)

# Lamiales taxonomy from study 713
study713  = Taxonomy.getTaxonomy('tax/713/', 'study713')
ott.notSame(study713.taxon('Buchnera'), silva.taxon('Buchnera'))
ott.absorb(study713)

# Index Fungorum
fung  = Taxonomy.getTaxonomy('tax/if/', 'if')
# smush will fold sibling taxa that have the same name.
fung.smush()

# 2014-03-07 Prevent a false match
ott.notSame(silva.taxon('Phaeosphaeria'), fung.taxon('Phaeosphaeria'))

# JAR 2014-03-11 after scouring Mycobank and IRMNG
fung.taxon('Trichosporon').absorb(fung.taxon('Chlamydotomus'))
fung.taxon('Chlamydotomus beigelii').rename('Trichosporon beigelii')
# Don't know what to do about this one.  Not in Mycobank or
# IRMNG... I don't know whether the T.c. name is valid.  Leave as is I guess
# fung.taxon('Chlamydotomus cellaris').rename('Trichosporon cellaris')

# analyzeMajorRankConflicts sets the "major_rank_conflict" flag when
# intermediate ranks are missing (e.g. a family that's a child of a
# class)
fung.analyzeMajorRankConflicts()

ott.absorb(fung)

# NCBI Taxonomy
ncbi  = Taxonomy.getTaxonomy('tax/ncbi/', 'ncbi')
# David Hibbett has requested that for Fungi, only Index Fungorum
# should be seen.  Rather than delete the NCBI fungal taxa, we just
# mark them 'hidden' so they can be suppressed downstream.  This
# preserves the identifier assignments, which may have been used
# somewhere.
ncbi.taxon('Fungi').hideDescendants()

#ott.same(ncbi.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
ott.notSame(ncbi.taxon('Burkea'), fung.taxon('Burkea'))
ott.notSame(ncbi.taxon('Coscinium'), fung.taxon('Coscinium'))
ott.notSame(ncbi.taxon('Perezia'), fung.taxon('Perezia'))
# analyzeOTUs sets flags on questionable taxa ("unclassified",
#  hybrids, and so on) to allow the option of suppression downstream
ncbi.analyzeOTUs()
ott.absorb(ncbi)

# 2014-01-27 Joseph: Quiscalus is incorrectly in Fringillidae instead
# of Icteridae.  NCBI is wrong, GBIF is correct.
ott.taxon('Icteridae').take(ott.taxon('Quiscalus', 'Fringillidae'))

# Misspelling in GBIF... seems to already be known
# ott.taxon("Torricelliaceae").synonym("Toricelliaceae")

# GBIF (Global Biodiversity Information Facility) taxonomy
gbif  = Taxonomy.getTaxonomy('tax/gbif/', 'gbif')
gbif.smush()

# Fungi suppressed at David Hibbett's request
gbif.taxon('Fungi').hideDescendants()

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
gbif.analyzeMajorRankConflicts()
ott.absorb(gbif)

# Joseph 2014-01-27
ott.taxon('Parulidae').take(ott.taxon('Myiothlypis', 'Passeriformes'))
# I don't get why this one isn't a major_rank_conflict !?
ott.taxon('Blattaria').take(ott.taxon('Phyllodromiidae'))

irmng = Taxonomy.getTaxonomy('tax/irmng/', 'irmng')
irmng.smush()

# Fungi suppressed at David Hibbett's request
irmng.taxon('Fungi').hideDescendants()

irmng.taxon('1413316').prune() #Neopithecus in Mammalia
irmng.taxon('1413315').extinct() #Neopithecus in Primates (Pongidae)
ott.same(gbif.taxon('3172047'), irmng.taxon('1381293'))  # Veronica
ott.same(gbif.taxon('6101461'), irmng.taxon('1170022')) # genus Tipuloidea
# IRMNG has four Tetrasphaeras.
ott.same(ncbi.taxon('Tetrasphaera','Intrasporangiaceae'), irmng.taxon('Tetrasphaera','Intrasporangiaceae'))
ott.same(gbif.taxon('Gorkadinium','Dinophyceae'), irmng.taxon('Gorkadinium','Dinophyceae'))
irmng.analyzeMajorRankConflicts()

irmng.taxon('Unaccepted').hide()

# Finished loading source taxonomies.  Now patch things up.

# Microbes suppressed at Laura Katz's request
irmng.taxon('Bacteria','life').hideDescendants()
irmng.taxon('Protista','life').hideDescendants()
irmng.taxon('Archaea','life').hideDescendants()

ott.absorb(irmng)

# Finished loading source taxonomies.  Now patch things up.

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
ott.taxon('Protozoa').take(ott.taxon('Amylophagus','Fungi'))#parasitic, Paddy

# Adlerocystis seems to be a fungus, but unclassified - JAR 2014-03-10
ott.taxon('Adlerocystis').incertaeSedis()

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
fixChromista(ott)

# From Laura and Dail on 5 Feb 2014
ott.taxon('Chlamydiae/Verrucomicrobia group').rename('Verrucomicrobia group')
ott.taxon('Diatomea').rename('Bacillariophyta')
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

# Finish up

# "Old" patch system
ott.edit('feed/ott/edits/')

# Remove all trees but the largest 
ott.deforestate()

# Assign OTT ids to all taxa, re-using old ids when possible
ott.assignIds(Taxonomy.getTaxonomy('tax/prev_ott/'))

ott.parentChildHomonymReport()

# Write files
ott.dump('tax/ott/')
