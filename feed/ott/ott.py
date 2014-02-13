# Jython script to build the Open Tree reference taxonomy

from org.opentreeoflife.smasher import Taxonomy
import sys
sys.path.append("feed/ott/")
from chromista_spreadsheet import fixChromista

ott = Taxonomy.newTaxonomy()

h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')
ott.absorb(h2007)

silva = Taxonomy.getTaxonomy('tax/silva/', 'silva')
ott.absorb(silva)

study713  = Taxonomy.getTaxonomy('tax/713/', 'study713')
ott.notSame(study713.taxon('Buchnera'), silva.taxon('Buchnera'))
ott.absorb(study713)

fung  = Taxonomy.getTaxonomy('tax/if/', 'if')
fung.smush()
fung.analyzeMajorRankConflicts()
ott.absorb(fung)

ncbi  = Taxonomy.getTaxonomy('tax/ncbi/', 'ncbi')
ncbi.taxon('Fungi').hideDescendants()
ott.same(ncbi.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
ott.notSame(ncbi.taxon('Burkea'), fung.taxon('Burkea'))
ott.notSame(ncbi.taxon('Coscinium'), fung.taxon('Coscinium'))
ott.notSame(ncbi.taxon('Perezia'), fung.taxon('Perezia'))
# This one should be temporary, might change with SILVA 117.
ott.same(silva.taxon('X85212/#6'), ncbi.taxon('Tetrasphaera','Intrasporangiaceae'))
ncbi.analyzeOTUs()
ott.absorb(ncbi)

# 2014-01-27 Joseph: Quiscalus is incorrectly in Fringillidae instead
# of Icteridae.  NCBI is wrong, GBIF is correct.
ott.taxon('Icteridae').take(ott.taxon('Quiscalus', 'Fringillidae'))

# Misspelling in GBIF
# ott.taxon("Torricelliaceae").synonym("Toricelliaceae")

gbif  = Taxonomy.getTaxonomy('tax/gbif/', 'gbif')
gbif.smush()
gbif.taxon('Fungi').hideDescendants()
ott.same(gbif.taxon('Cyanobacteria'), silva.taxon('D88288/#3'))
ott.same(ncbi.taxon('5878'), gbif.taxon('10'))	  # Ciliophora
ott.same(ncbi.taxon('29178'), gbif.taxon('389'))  # Foraminifera
ott.same(ncbi.taxon('Tetrasphaera','Intrasporangiaceae'), gbif.taxon('Tetrasphaera','Intrasporangiaceae'))
ott.notSame(silva.taxon('Retaria'), gbif.taxon('Retaria'))
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
irmng.taxon('Fungi').hideDescendants()
irmng.taxon('1413316').prune() #Neopithecus in Mammalia
irmng.taxon('1413315').extinct() #Neopithecus in Primates (Pongidae)
ott.same(gbif.taxon('3172047'), irmng.taxon('1381293'))  # Veronica
ott.same(gbif.taxon('6101461'), irmng.taxon('1170022')) # genus Tipuloidea
# IRMNG has four Tetrasphaeras.
ott.same(ncbi.taxon('Tetrasphaera','Intrasporangiaceae'), irmng.taxon('Tetrasphaera','Intrasporangiaceae'))
ott.same(gbif.taxon('Gorkadinium','Dinophyceae'), irmng.taxon('Gorkadinium','Dinophyceae'))
irmng.analyzeMajorRankConflicts()
ott.absorb(irmng)

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
# ott.taxon('Chromista').absorb(ott.taxon('Adlerocystis','Fungi'))
ott.taxon('Protozoa').absorb(ott.taxon('Amylophagus','Fungi'))#parasitic, Paddy

# Bad synonym - Tony Rees 2014-01-28
ott.taxon('Lemania pluvialis').prune()

#Pinophyta and daughters need to be deleted; - Bryan 2014-01-28
#Lycopsida and daughters need to be deleted;
#Pteridophyta and daughters need to be deleted;
#Gymnospermophyta and daughters need to be deleted;
ott.taxon('Pinophyta','Chloroplastida').incertaeSedis()
ott.taxon('Pteridophyta','Chloroplastida').incertaeSedis()
ott.taxon('Gymnospermophyta','Chloroplastida').incertaeSedis()

fixChromista(ott)

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
ott.taxon('Boraginaceae').absorb(ott.taxon('Hydrophyllaceae'))
ott.taxon('Boraginales').take(ott.taxon('Boraginaceae'))
ott.taxon('lamiids').take(ott.taxon('Boraginales'))

# Vahlia 26024 <- Vahliaceae 23372 <- lammids 596112 (was incertae sedis)
ott.taxon('lamiids').take(ott.taxon('Vahliaceae'))

# http://www.sciencedirect.com/science/article/pii/S0034666703000927
ott.taxon('Araripia').extinct()

# http://www.mobot.org/mobot/research/apweb/
ott.taxon('Viscaceae').rename('Visceae')
ott.taxon('Amphorogynaceae').rename('Amphorogyneae')
ott.taxon('Thesiaceae').rename('Thesieae')
ott.taxon('Santalaceae').take(ott.taxon('Visceae'))
ott.taxon('Santalaceae').take(ott.taxon('Amphorogyneae'))
ott.taxon('Santalaceae').take(ott.taxon('Thesieae'))
ott.taxon('Santalaceae').absorb(ott.taxon('Cervantesiaceae'))
ott.taxon('Santalaceae').absorb(ott.taxon('Comandraceae'))

# http://dx.doi.org/10.1126/science.282.5394.1692 
ott.taxon('Magnoliophyta').take(ott.taxon('Archaefructus'))

# http://deepblue.lib.umich.edu/bitstream/handle/2027.42/48219/ID058.pdf
ott.taxon('eudicotyledons').take(ott.taxon('Phyllites'))

ott.taxon('Oleaceae').extinct()

# Finish up

ott.edit('feed/ott/edits/')
ott.deforestate()
ott.assignIds(Taxonomy.getTaxonomy('tax/prev_ott/'))
ott.dump('tax/ott/')

