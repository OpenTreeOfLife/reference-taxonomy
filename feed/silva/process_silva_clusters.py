''''
Script received from Jessica Grant, 8 October 2013

This script takes as input the ssu fasta from Silva (in this case SSURef_NR99_115_tax_silva.fasta
but change as necessary.)  Also 'tax_rank.txt' for the rank file from Silva ftp.

It outputs 
	the taxonomy in this format:
	<taxid>\t|\t<parentid>\t|\t<taxon>\t|\t<rank>\t|\t<seqid>
	also two additional files that might not be necessary:
	homonym_paths.txt for checking taxa called as homonyms
	silva_taxonly.txt a list of the taxa that are included in the taxonomy.

seqid is the unique identifier from Silva of the SSU reference sequence, 
e.g. 'A45315.1.1521', and is there for the species only.  Other 
ranks have 'no seq'

Be aware of a few things - 
	I have ignored plants, animals and fungi because they have reasonable taxonomies elsewhere.

	Silva has 'uncultured' as a taxon, between the species and its genus. e.g.:
	
>U81762.1.1448 Bacteria;Firmicutes;Clostridia;Clostridiales;Ruminococcaceae;uncultured;uncultured bacterium
	
	I have ignored this so this uncultured bacterium would get mapped to Ruminococcaceae

	All duplicate species names get their own unique number, but duplicate names of genera or above
	are checked for homonyms. - Homonyms are determined by having different parents - in some cases this
	can add taxa that really are the same.	e.g. Dinobryon here:
	
['Eukaryota', 'SAR', 'Stramenopiles', 'Chrysophyceae', 'Ochromonadales', 'Dinobryon', 'Dinobryon divergens']
['Eukaryota', 'SAR', 'Stramenopiles', 'Chrysophyceae', 'Ochromonadales', 'Ochromonas', 'Dinobryon', 'Dinobryon cf. sociale']	

	
	Other problems exist in Silva - for example in these two paths, 'marine group' is treated as a taxon (and is 
	picked up as homonymic) when it really isn't.

['Bacteria', 'Acidobacteria', 'Holophagae', 'Holophagales', 'Holophagaceae', 'marine group', 'uncultured bacterium']
['Bacteria', 'Cyanobacteria', 'SubsectionI', 'FamilyI', 'marine group', 'cyanobacterium UCYN-A']

	Or	here, where '43F-1404R' seems to be treated as a taxon when it is probably a primer set: 
	
['Bacteria', 'Acidobacteria', 'Holophagae', 'Subgroup 10', '43F-1404R', 'uncultured Acidobacteria bacterium']
['Bacteria', 'Proteobacteria', 'Deltaproteobacteria', '43F-1404R', 'uncultured bacterium']

	
	I left in things like 'Bacteria', 'Cyanobacteria', 'Chloroplast', 'Oryza sativa Japonica Group'
	even though the it could be confusing.	Chloroplast (and other symbiont) data may need special treatment

'''
import sys
import re
import string
import os, time, json, os.path


SILVATAXONOMYFILE = 'taxmap_slv_ssu_nr_119.txt'
SILVACLUSTERFILE = '/silva.clstr'



def writeAboutFile(url, nodesfilename, taxdir):
	aboutfilename = taxdir+"/about.json"
	aboutfile = open(aboutfilename,"w")
	# Get file date from nodes.dmp in downloaddir
	# os.path.getmtime(file)   => number of seconds since epoch
	filetime = os.path.getmtime(nodesfilename)
	tuple_time = time.gmtime(filetime)
	iso_time = time.strftime("%Y-%m-%dT%H:%M:%S", tuple_time)
	# This is pretty much garbage, pending overhaul of the metadata feature.
	about = {"type": "source",
			 "namespace": {"prefix": "silva",
						   "definition": "http://www.arb-silva.de/browser/ssu/silva/",
						   "description": "SILVA Taxonomy"},
			 "prefix": "silva",
			 "namespaces": [],
			 "url": url,
			 "lastModified": iso_time,
			 "smush": False}
	aboutfile.write(json.dumps(about))
	print "Wrote", aboutfilename
	aboutfile.close()

pathdict = {} #given the sequence identifier, return the taxonomy path
#seqdict = {} #given the taxon name, return (<species id assigned here>,<unique sequence id from SSU ref file>) 
Seen = {} # to tell whether the taxon name has been seen before - to know if homonym check needs to be done

# It was judged that other taxonomies on balance will be better than Silva
# for certain groups.
# Silva classifies ABEG02010941 Caenorhabditis brenneri as a bacterium; this 
# is clearly an artifact of sample contamination.

kill = re.compile('|'.join(['ABEG02010941',	 # Caenorhabditis brenneri
							'ABRM01041397',	 # Hydra magnipapillata
							'ALWT01111512',	 # Myotis davidii
							'HP641760',		 # Alasmidonta varicosa
							'JR876587',		 # Embioptera sp. UVienna-2012
							 ]))

# AB564305 AB564301 AB564299 ... all garbage

def makePathDict(infilename, outfilename):
	infile = open(infilename,'rU')
	outfile = open(outfilename,'w') # I'm writing this out to have the taxonomy separate from the sequences - not necessary
	inclusive = 0
	exclusive = 0
	for line in infile:	 #removing plants, animals, fungi, chloroplast and mitochondrial clusters - also specifying Oryza because it is problematic in this version the database (SSURef_NR99_115_tax_silva.fasta).
		if line[0] == '>':
			inclusive += 1
			if not re.search(kill,line):
				exclusive += 1
				taxlist = []		
				uid = line.split()[0].strip() # was going to use the species but there are multiple 'unidentified', for example
				taxlist_1  = line.strip(uid).strip().split(';')
				uid = uid.lstrip('>')
				for taxname in taxlist_1:
					# JAR commented out the following... smasher takes care of these
					# if not re.search('Incertae Sedis',tax) and tax not in taxlist:			
					taxlist.append(taxname)
				#if 'uncultured' in taxlist:
				#	taxlist.remove('uncultured') #not sure...
				pathdict[uid] = taxlist
				outfile.write(uid + ',' + str(pathdict[uid]) + '\n')
	outfile.close()		
	print "Clusters: %s	 Exclusive of kill list: %s"%(inclusive, exclusive)
	return pathdict 

def checkHomonym(uid,taxon,pathdict,olduid,homfilename):
	newpath = pathdict[uid]
	oldpath = pathdict[olduid]
	newparindex = newpathindex(taxon) - 1
	oldparindex = oldpath.index(taxon) - 1
	if oldpath[oldparindex] in newpath[newparindex] and oldpath[oldparindex]: #parent of original in new path
		return True #link new to old
	else:
		hom_outfile = open(homfilename,'a')
		hom_outfile.write(taxon + ',' + str(newpath)+ ',' +str(oldpath) + '\n')
		hom_outfile.close()
		return newpath[newparindex]

ranks = {}

accession_to_ncbi = {}

def readNcbi(indir):
	ncbifilename = indir + "/accessionid_to_taxonid.tsv"
	ncbifile = open(ncbifilename, 'r')
	for line in ncbifile:
		fields = line.split('\t')
		ncbi_id = fields[1].strip()
		if ncbi_id != '*':
			genbank_id = fields[0].strip()
			if len(fields) >= 3 and fields[2] != '':
				strain = fields[2].strip()
			else:
				strain = None
			accession_to_ncbi[genbank_id] = (ncbi_id, strain)
	ncbifile.close()

class Silva_leaf:
	def __init__(self,start,end,lineage,name):
		self.id = id
		self.lineage = lineage
		self.name = name
		self.start = start
		self.end = end

	
def readSilvaTaxonomy(indir):
	return readSilvaTaxonomy1(indir + '/' + SILVATAXONOMYFILE)

def readSilvaTaxonomy1(path):
	taxonomy = {}
	tax_file = None
	with open(path, "r") as tax_file:
		for line in tax_file:
			fields = line.split('\t')
			id = fields[0].strip()
			start = fields[1].strip()
			end = fields[2].strip()
			split_lineage = fields[3].strip().split(';')
			name = fields[4].strip('\n')
			leaf = Silva_leaf(start=start, end=end, lineage=split_lineage, name=name)
			taxonomy[id] = leaf
	return taxonomy


class Silva_cluster:
	def __init__(self,reference):
		self.reference = reference
		self.members = set()

	def add_member(self,new_member):
		self.members.add(new_member)

def readSilvaClusters(indir):
	path = indir + SILVACLUSTERFILE
	cluster_accessions = {}
	with open(path,"r") as clusters_file:
		cur_cluster = None
		for line in clusters_file:
			tokens = line.split(' ')
			for i,token in enumerate(tokens):
				if token[0] == '>':	 # assessions start with >
					token = token[1:]
					if token[-2] == '.':  # cleanup
						token = token[:-3]
					else:
						token = token[:-1]
					if i == 0: # reference accessions are not indented
						cur_cluster = Silva_cluster(token)
						cluster_accessions[token] = cur_cluster
					else:
						cur_cluster.add_member(token)
	return cluster_accessions


def updateTaxonomy(taxonomy, clusters):
	newTaxonomy = {}
	for id in taxonomy.keys():
		if id not in clusters:
			print "No cluster found for {}".format(id)
		else:
			cluster = clusters[id]
			if len(cluster.members) > 1:
				parent = taxonomy[id]
				new_lineage = parent.lineage
				new_lineage.append(parent.name)
				for token in cluster.members:
					if token != id:
						new_name = id + " " + token
						new_id = id + "." + token
						new_leaf = Silva_leaf(start=0,
											  end=0,
											  name=new_name,
											  lineage=new_lineage)
						taxonomy[new_id] = new_leaf

SILVA_TEMPLATE = "{}\t{}\t{}\t{}\t{}\n"


def dumpSilvaLineages(taxonomy,lineages_output_path):
	"""dump silva taxonomy in original format"""
	with open(lineages_output_path,"w") as lineages_file:
		for id,taxon in taxonomy.items():
			new_lineage = ";".join(taxon.lineage)
			outstr = SILVA_TEMPLATE.format(id,
										   taxon.start,
										   taxon.end,
										   new_lineage,
										   taxon.name)
			lineages_file.write(outstr)




synonyms = {}

taxondict = {}	# maps (parentid, name) to taxid

# Deal with one Genbank accession id.  N.b. we might encounter the same id
# multiple times.

acc_seen = {}



def main():
	import collections
	# was: infile = open('SSURef_NR99_115_tax_silva.fasta','rU')
	indir = sys.argv[1]
	outdir = sys.argv[2]
	url = sys.argv[3]
	fastafilename = indir + '/silva.fasta'
	readNcbi(indir)
	silvaTaxonomy = readSilvaTaxonomy(indir)
	print "Silva taxonomy size = {}".format(len(silvaTaxonomy))
	sortedRawTaxonomy = collections.OrderedDict(sorted(silvaTaxonomy.items(),
													   key=lambda t: t[0]))
	dumpSilvaLineages(sortedRawTaxonomy,'sortedraw.x.txt')
	silvaClusters = readSilvaClusters(indir)
	print "Silva cluster count = {}".format(len(silvaClusters))
	updateTaxonomy(silvaTaxonomy,silvaClusters)
	dumpSilvaLineages(silvaTaxonomy, "augmentedSilva.tsv.new")
	# newTaxonomy = readSilvaTaxonomy1("augmentedSilva.tsv.new")
	# print "reloaded taxonomy size = {}".format(len(newTaxonomy))
	# sortedNewTaxonomy = collections.OrderedDict(sorted(newTaxonomy.items(), key=lambda t: t[0]))
	# dumpSilvaLineages(sortedNewTaxonomy,"sortednew.x.txt")
	os.rename('augmentedSilva.tsv.new', outdir + '/augmentedSilva.tsv')

import cProfile		
cProfile.run('main()')

