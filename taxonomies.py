# coding=utf-8

from org.opentreeoflife.smasher import Taxonomy

def loadSilva():
	silva = Taxonomy.getTaxonomy('tax/silva/', 'silva')

	# JAR 2014-05-13 scrutinizing pin() and BarrierNodes.  Wikipedia
	# confirms this synonymy.  Dail L. prefers -phyta to -phyceae
	# but says -phytina would be more correct per code.
	silva.taxon('Rhodophyceae').rename('Rhodophyta')

	# Sample contamination
	silva.taxon('Trichoderma harzianum').prune()
	silva.taxon('Sclerotinia homoeocarpa').prune()
	silva.taxon('Puccinia triticina').prune()

	# https://github.com/OpenTreeOfLife/reference-taxonomy/issues/104
	silva.taxon('Caenorhabditis elegans').prune()

	silva.taxon('Solanum lycopersicum').prune()

	return silva

def loadH2007():
	h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')

	# 2014-04-08 Misspelling
	if h2007.maybeTaxon('Chaetothryriomycetidae') != None:
		h2007.taxon('Chaetothryriomycetidae').rename('Chaetothyriomycetidae')

	if h2007.maybeTaxon('Asteriniales') != None:
		h2007.taxon('Asteriniales').rename('Asterinales')
	else:
		h2007.taxon('Asterinales').synonym('Asteriniales')

	return h2007

def loadFung():
	fung = Taxonomy.getTaxonomy('tax/if/', 'if')

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
	fung.smush()

	return fung

def load713():
	study713 = Taxonomy.getTaxonomy('tax/713/', 'study713')
	return study713

def loadNcbi():
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

	# analyzeOTUs sets flags on questionable taxa ("unclassified",
	#  hybrids, and so on) to allow the option of suppression downstream
	ncbi.analyzeOTUs()
	ncbi.analyzeContainers()

	return ncbi

def loadGbif():
	gbif = Taxonomy.getTaxonomy('tax/gbif/', 'gbif')
	gbif.smush()

	# In GBIF, if a rank is skipped for some children but not others, that
	# means the rank-skipped children are incertae sedis.  Mark them so.
	gbif.analyzeMajorRankConflicts()

	fixProtists(gbif)  # creates a Eukaryota node
	fixPlants(gbif)
	gbif.taxon('Animalia').synonym('Metazoa')

	# JAR 2014-07-18  - get rid of Helophorus duplication
	gbif.taxon('3263442').absorb(gbif.taxon('6757656'))

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
			bad.hide()	 # recursive
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
	# phyla below that.	 Move up to be a sibling of Plantae.
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

		("Fungi", "Tuber indicum"),	   # MANUAL

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
