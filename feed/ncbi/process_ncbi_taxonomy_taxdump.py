#!/usr/bin/env python

# Arguments:
#	download - T or F - whether or not to download the tar.gz file from NCBI
#	downloaddir - where to find (or put) the tar.gz and its contents
#	kill list file
#	destination dir - where taxonomy.tsv etc. are to be put

# JAR copied this file from data/ in the taxomachine repository
# to smasher/ in the opentree repository on 2013-04-25.
# Some subsequent modifications:
#  - remove "unclassified"
#  - add command line argument for directory in which to put ncbi
#  - change skipids from list to dictionary for speed

import sys,os,time
import os.path
from collections import Counter

"""
this processes the ncbi taxonomy tables for the synonyms and the 
names that will be included in the upload to the taxomachine
"""

"""
skipping
- X 
-environmental
-unknown
-unidentified
-endophyte
-uncultured
-scgc
-libraries
-unclassifed

if it is a series based on names 3rd column
adding series to the name

connecting these to there parents
-unclassified
-incertae sedis
"""

if __name__ == "__main__":
	if len(sys.argv) != 6:
		print "Usage: python process_ncbi_taxonomy_taxdump.py {T|F} tmpdir skipids.file outdir url"
		sys.exit(1)
	download = sys.argv[1]
	downloaddir = sys.argv[2]	# e.g. feed/ncbi/tmp
	nodesfilename = downloaddir + "/nodes.dmp"
	namesfilename = downloaddir + "/names.dmp"
	skipfile = sys.argv[3]
	taxdir = sys.argv[4]
	url = sys.argv[5]

	aboutfilename = taxdir+"/about.json"
	aboutfile = open(aboutfilename,"w")
	aboutfile.write('{ "prefix" "ncbi",\n')
	aboutfile.write('  "prefixDefinition": "http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=",\n')
	aboutfile.write('  "description": "NCBI Taxonomy",\n')
	# Get file date from nodes.dmp in downloaddir
	# os.path.getmtime(file)   => number of seconds since epoch
	ncbitime = os.path.getmtime(nodesfilename)
	tuple_time = time.gmtime(ncbitime)
	iso_time = time.strftime("%Y-%m-%dT%H:%M:%S", tuple_time)
	aboutfile.write('  "source": {"URL": "%s", "date": "%s"},\n'%(url, iso_time))
	aboutfile.write('}\n')
	aboutfile.close()

	outfile = open(taxdir+"/taxonomy.tsv","w")
	outfilesy = open(taxdir+"/synonyms.tsv","w")
	if download.upper() == "T":
		print("downloading taxonomy")
		os.system("wget --output-document=" +
				  downloaddir + "/taxdump.tar.gz " + url)
		os.system("tar -C " +
				  downloaddir +
				  " -xzvf taxdump.tar.gz")

	if os.path.isfile(nodesfilename) == False:
		print nodesfilename + " is not present"
		sys.exit(0)
	if os.path.isfile(namesfilename) == False:
		print namesfilename + " is not present"
		sys.exit(0)

	nodesf = open(nodesfilename,"r")
	namesf = open(namesfilename,"r")

	count = 0
	pid = {} #key is the child id and the value is the parent
	cid = {} #key is the parent and value is the list of children
	nrank = {} #key is the node id and the value is the rank
	for i in nodesf:
		spls = i.split("\t|\t")
		tid = spls[0].strip()
		parentid = spls[1].strip()
		rank = spls[2].strip()
		pid[tid] = parentid
		nrank[tid] = rank
		if parentid not in cid: 
			cid[parentid] = []
		cid[parentid].append(tid)
		count += 1
		if count % 100000 == 0:
			print count
	nodesf.close()

	# Removed "unclassified" 2013-04-25
	skip = []
	# skip = ["viral","other","viroids","viruses","artificial","x","environmental","unknown","unidentified","endophyte","endophytic","uncultured","scgc","libraries","virus","mycorrhizal samples"]
	skipids = {}
	#run through the skip ids file
	skipidf = open(skipfile,"r")
	for i in skipidf:
		skipids[i.strip()] = True
	skipidf.close()
	
	count = 0
	classes = []
	idstoexclude = []
	nm_storage = {}
	lines = {}
	synonyms = {}
	namesd = []
	allnames = []
	for i in namesf:
		spls = i.strip().split("\t|") #if you do \t|\t then you don't get the name class right because it is "\t|"
		gid = spls[0].strip()
		par = pid[gid]
		# was nm = spls[1].strip().replace("[","").replace("]","")
		nm = spls[1].strip()
		homonc = spls[2].strip() #can get if it is a series here
		nm_c = spls[3].strip()
		if nm_c not in classes:
			classes.append(nm_c)
		nm_keep = True
		nms = nm.split(" ")
		for j in nms:
			if j.lower() in skip:
				nm_keep = False
		if gid in skipids:
			nm_keep = False
		if nm_keep == False:
			idstoexclude.append(gid)
			continue
		if "<series>" in homonc:
			nm = nm + " series"
		if "subgroup <" in homonc: #corrects some nested homonyms
			nm = homonc.replace("<","").replace(">","")
		if nm_c != "scientific name":
			if gid not in synonyms:
				synonyms[gid] = []
			synonyms[gid].append(i.strip())
		else:
			lines[gid] = i.strip()
			nm_storage[gid] = nm
			allnames.append(nm)
		count += 1
		if count % 100000 == 0:
			print count
	print "number of lines: ",count
	namesf.close()

	#get the nameids that are double
	c = Counter(allnames)
	namesd = []
	for i in c:
		if c[i] > 1:
			namesd.append(i)
	ndoubles = []
	namesf = open(namesfilename,"r")
	for i in namesf:
		spls = i.strip().split("\t|") #IF YOU DO \T|\T THEN YOU DON'T GET THE NAME CLASS RIGHT BECAUSE IT IS "\T|"
		gid = spls[0].strip()
		nm = spls[1].strip()
		if nm in namesd:
			ndoubles.append(gid)
	namesf.close()

	#now making sure that the taxonomy is functional before printing to the file

	skipids = {}
	stack = idstoexclude


	print "checking functionality of taxonomy"
	print "count lefttocompare"

	count = 0
	while len(stack) != 0:
		curid = stack.pop()
		if curid in skipids:
			continue
		skipids[curid] = True
		if curid in cid:
			ids = cid[curid]
			for i in ids:
				stack.append(i)
		count += 1
		if count % 10000 == 0:
			print count,len(stack)

	for i in skipids:
		if i in lines:
			del lines[i]
		if i in synonyms:
			del synonyms[i]
		if i in nm_storage:
			del nm_storage[i]
	
	print "number of scientific names: ",len(lines)
	print "number of synonyms: ",len(synonyms)

	"""
	in this section we change the names of the parent child identical names for
	1) if parent and child have the same name higher than genus, they are sunk
	2) if the parent and child have the same name at genus and subspecies (etc), the subname
	is called genusname rank subgenus name
	"""

	final_nm_storage = {}

	for i in nm_storage:
		if nm_storage[i] != "root":
			if i in pid:
				if nm_storage[i] == nm_storage[pid[i]]:
				#do something for the genus 
					if nrank[pid[i]] == "genus":
						final_nm_storage[i] = nm_storage[pid[i]]+" "+nrank[i]+" "+nm_storage[i]
					else:
						idstoch = cid[i]
						for j in idstoch:
							pid[j] = pid[i]
						if i in synonyms:
							for j in synonyms[i]:
								if pid[i] in synonyms:
									synonyms[pid[i]].append(j)
								else:
									synonyms[pid[i]] = [j]
							del synonyms[i]
						del lines[i]
				#do something for everything else

	#checking for names that are the same in lineage but not parent child
	for i in ndoubles:
		if i not in nm_storage:
			continue
		stack = []
		if i in final_nm_storage:
			continue
		stack.append(i)
		while len(stack) > 0:
			cur = stack.pop()
			if cur in nm_storage:
				if cur in final_nm_storage:
					continue
				if nm_storage[cur] == nm_storage[i]:
					tname = ""
					tcur = cur
					if tcur == i:
						continue
					while tcur != i:
						tname += nm_storage[tcur] +" "+nrank[tcur]+" "
						if tcur in pid:
							tcur = pid[tcur]
						else:
							break
					final_nm_storage[cur] = nm_storage[i]+" "+nrank[i]+" "+tname
			if cur in cid:
				for j in cid[cur]:
					stack.append(j)
	outfile.write("uid\t|\tparent_uid\t|\tname\t|\trank\t|\t\n")

	#need to print id, parent id, and name	 
	for i in lines:
		spls = lines[i].split("\t|\t")
		id = spls[0].strip()
		prid = pid[spls[0]].strip()
		sname = spls[1].strip()

		#changed from sname to nm_storage to fix the dup name issue
		if i in final_nm_storage:
			nametowrite = final_nm_storage[i]
		else:
			nametowrite = nm_storage[i]

		# if it is the root node then we need to make its parent id blank and rename it "life"
		if nametowrite.strip() == "root":
			nametowrite = "life"
			prid = ""
		rankwrite = nrank[id]
		outfile.write(id+"\t|\t"+prid+"\t|\t"+nametowrite+"\t|\t"+rankwrite+"\t|\t\n")

	outfile.close()

	outfilesy.write("uid\t|\tname\t|\ttype\t|\t\n")

	for i in synonyms:
		if i in lines:
			for j in synonyms[i]:
				spls = j.split("\t|\t")
				id = spls[0].strip()
				sname = spls[1].strip()
				nametp = spls[3].strip()
				outfilesy.write(id+"\t|\t"+sname+"\t|\t"+nametp+"\t|\t\n")
	outfilesy.close()
