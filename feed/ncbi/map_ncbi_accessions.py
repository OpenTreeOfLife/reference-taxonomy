# This script maps sequence accession numbers (e.g. 'AM947437') 
# NCBI taxon ids (e.g. '329270') by calling out to the NCBI
# eutils service. If there is a strain field in the results, it also 
# gets that.

# Authors:
# Jonathan Rees
# Karen Cranston

"""
 time python feed/ncbi/seq_taxon_xref.py \
   feed/ncbi/accessionid_to_taxonid.tsv feed/silva/out/silva_taxonly.txt v9.tsv 20 10
"""

import sys,os
import time
import string
import xml.etree.ElementTree as ET
import argparse
import csv

# set command line arguments
# note that there are two input files: the existing file of 
# accessions \t taxonID \t strain (mappedfilename) and the list 
# of accessions that need mapping (unmappedfilename)
parser = argparse.ArgumentParser(description='Lookup GenBank accession numbers')
parser.add_argument('mappedfilename', help='master file containing three tab-separated columns: accession number, taxon_id, strain; will be created if it does not exist')
parser.add_argument('unmappedfilename', help='file containing a list of accession numbers to map, one per line. Version numbers are ignored.')
parser.add_argument('-o','--outputfile', help='where to write the output; will have same format as accessionfile; default is out.tsv',default='out.tsv')
parser.add_argument('-b','--batchsize', type=int, help='the number of accession numbers to send to GenBank in each request; default=10', default=10)
parser.add_argument('-m','--maxbatches', type=int, help='the number of requests to send to GenBank; the total number of accessions queries is batchsize*maxbatches; default=10',default=10)

# Get the accessions that have been mapped so far from previous version of
# mapping file, and put into two maps: taxaMap for taxonid and strainMap 
# for strains
# TODO : this is hacky, should be some other data structure - nested dict?
# dataframe?
def getMappedAccessions(filename):
	mappedfile = open(filename,"r")
	map = {}
	for line in mappedfile:
	# might be two or three columns: accession \t taxonid \t strain
		fields = line.split("\t")
		if len(fields) >= 2:
			map[fields[0]] = fields[1].strip()
	mappedfile.close()

	print "Number accessions to start: ", len(map)
	return map
	
# Get the list of accession ids to map
# The formatting differences between silva and greengenes inputs were 
# sufficently different to make a generic solution problematic
# Instead, we assume that the unmapped file simply contains
# accession numbers, one per line
# Ignores versions of accessions (i.e. uses only the part before the first '.'
# in the accession number)

def getUnmappedAccessions(filename, map):
	unmapped = []
	unmappedfile = open(filename, 'r')
	for line in unmappedfile:
		# keep only portion before the first '.'
		accession = line.split('.',1)[0]
		if not (accession in map):
			unmapped.append(accession)

	print "Unmapped: ", len(unmapped)
	fewer_unmapped = []
	for acc in unmapped:
		if not(acc in map):
			fewer_unmapped.append(acc)

	unmapped = fewer_unmapped
	return unmapped

# Function to loop up a batch of accession ids
def do_one_batch(batch,map):
	#print batch
	tempfilename = "efetch.tmp"
	callEutils(tempfilename,batch)
	
	# parse the NCBI XML
	# using http://www.ncbi.nlm.nih.gov/dtd/NCBI_Seqset.dtd as schema
	try:
		tree = ET.parse(tempfilename)
	except IOError:
		print "Did not find output file from eutils call"
		raise

	root = tree.getroot()
	
	acc = None
	strainname = ""
	taxid = None
	count = 0
	for seq in root.iter('Seq-entry'):
		acc = seq.find(".//Textseq-id_accession").text
		taxid = seq.find(".//Object-id_id").text
		if taxid is not None:
			map[acc] = taxid
			count = count+1
		# assuming we found a taxaid, look for strain information 
		# in an OrgMod element
		# not yet printing strain info to file
		orgmod = seq.find(".//OrgMod")
		if taxid is not None and orgmod is not None:
			subtype = orgmod.find('OrgMod_subtype').get("value")
			if subtype == "strain":
				strainname = orgmod.find('OrgMod_subname').text

		print (acc,taxid,strainname)
		acc = None
		strainname = ""
		taxid = None

	for acc in batch:
		if not (acc in map):
			print "Did not find taxon id for accession number ", acc;
			map[acc] = '*'
	return map

def callEutils(tempfilename,batch):
	# using rettype=native&retmode=xml in order to get strain info
	command = ("wget -q -O " + tempfilename +
			  " \"http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=nuccore&id=" +
			  ",".join(batch) +
			  "&rettype=native&retmode=xml\"")
	print command
	os.system(command)

def main(args):
	map = getMappedAccessions(args.mappedfilename)
	unmapped = getUnmappedAccessions(args.unmappedfilename,map)
	
	nbatches = len(unmapped)/args.batchsize
	# for i in range(nbatches):
	for i in range(args.maxbatches):
		start = i*args.batchsize
		end = min(start+args.batchsize,len(unmapped))
		if end > start:
			try:
				map=do_one_batch(unmapped[start:end],map)
			except IOError:
				quit()
			
			time.sleep(1)

	# Write out all of the mappings, both old and new.

	outfile = open(args.outputfile,"w")
	for accid in map.iterkeys():
		outfile.write("%s\t%s\n"%(accid, map[accid]))
	outfile.close()

if __name__ == '__main__':
    args = parser.parse_args()
    main(args)