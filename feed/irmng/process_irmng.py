# Command like arguments: something like
#	   feed/irmng/in/IRMNG_DWC.csv
#	   feed/irmng/in/IRMNG_DWC_SP_PROFILE.csv
#	   tax/irmng/taxonomy.tsv
#	   tax/irmng/synonyms.tsv

import csv, string, sys

irmng_file_name = sys.argv[1]
profile_file_name = sys.argv[2]
taxonomy_file_name = sys.argv[3]
synonyms_file_name = sys.argv[4]

taxa = {}
synonyms = {}

# 0 "TAXONID","SCIENTIFICNAME","SCIENTIFICNAMEAUTHORSHIP","GENUS",
# 4 "SPECIFICEPITHET","FAMILY","TAXONRANK","TAXONOMICSTATUS",
# 8 "NOMENCLATURALSTATUS","NAMEACCORDINGTO","ORIGINALNAMEUSAGEID",
# 11 "NAMEPUBLISHEDIN","ACCEPTEDNAMEUSAGEID","PARENTNAMEUSAGE",
# 14 "PARENTNAMEUSAGEID","TAXONREMARKS","MODIFIED","NOMENCLATURALCODE"

with open(irmng_file_name, 'rb') as csvfile:
	csvreader = csv.reader(csvfile)
	header = csvreader.next()
	if header[5] != 'FAMILY':
		print >>sys.stderr, '** Unexpected column name in header row', header[-3]
	for row in csvreader:
		taxonid = row[0]
		longname = row[1]
		auth = row[2]
		genus = row[3]
		epithet = row[4]
		family = row[5]
		rank = row[6]
		status = row[7]
		syn_target_id = row[12]
		parent = row[-4]
		if rank == 'species':
			name = ('%s %s')%(genus,epithet)
		elif rank == 'genus':
			name = genus
		elif rank == 'family':
			name = family
		elif len(auth) > 0 and longname.endswith(auth):
			name = longname[0:len(longname)-len(auth)-1]
		else:
			name = longname
#		if (status != '' and status != 'accepted' and status != 'valid' and 
#			   status != 'available' and status != 'proParteSynonym'):
#			continue
		if syn_target_id != '' and syn_target_id != taxonid:
			synonyms[taxonid] = (syn_target_id, name, status)
		else:
			taxa[taxonid] = (parent, name, rank)

# "10704","Decapoda Latreille, 1802","Latreille, 1802",,,,"order",,,,,,,"Malacostraca","1190","cf. Decapoda (Mollusca)","01-01-2012","ICZN"

loser_synonyms = {}
for taxonid in synonyms:
	(syn_target_id, name, status) = synonyms[taxonid]
	if syn_target_id in synonyms:
		loser_synonyms[taxonid] = True
for taxonid in loser_synonyms:
	del synonyms[taxonid]
print >>sys.stderr, "Indirect synonyms:", len(loser_synonyms), "out of", len(synonyms)

extinctp = {}

not_extinct = ['1530',	   # Actinopterygii
			   '1531',	   # Sarcopterygii
			   '10565',	   # Saurischia
			   '11919',	   # Didelphimorphia
			   #'1170022',	# Tipuloidea
			   #'1340611',	# Retaria
			   #'1124871',	# Labyrinthulomycetes [Labyrinthomorpha??]
			   #'102024',	# Ophiurinidae - problem is Ophiurina
			   #'1064058',	# Rhynchonelloidea genus/superfamily
			   '1021564',  # Cruciplacolithus
			   #'1114655',	# Tetrasphaera - different from GBIF
			   ]

with open(profile_file_name, 'rb') as csvfile:
	csvreader = csv.reader(csvfile)
	header = csvreader.next()
	if header[1] != 'ISEXTINCT':
		print >>sys.stderr, "** Expected to find ISEXTINCT in header row but didn't:", header[1]
	for row in csvreader:
		taxonid = row[0]
		if taxonid in not_extinct:
			continue
		isextinct = row[1]
		if isextinct == 'TRUE':
			extinctp[taxonid] = True

count = 0
for taxonid in taxa:
	(parentid, name, rank) = taxa[taxonid]
	if not (taxonid in extinctp):
		detect = 0
		while parentid in extinctp:
			count += 1
			detect += 1
			if detect > 10:
				print >>sys.stderr, "Cycle detected", name
				break
			if count <= 10:
				print >>sys.stderr, ("Non-extinct taxon with extinct parent: %s(%s) in %s"%
									 (taxonid, name, parentid))
			del extinctp[parentid]
			if parentid in synonyms:
				parentid = synonyms[parentid][0]
			else:
				parentid = taxa[parentid][0]	# tuple (parentid, name, rank)

print >>sys.stderr, 'Non-extinct taxa with extinct parent:', count

with open(taxonomy_file_name, 'w') as taxfile:
	print 'Writing %s'%taxonomy_file_name
	taxfile.write('%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n'%('uid', 'parent_uid', 'name', 'rank', 'flags'))
	taxfile.write('%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n'%('0', '', 'life', 'no rank', ''))
	for taxonid in taxa:
		(parent, name, rank) = taxa[taxonid]
		if parent in synonyms:
			parent = synonyms[parent][0]
		if parent == '':
			parent = '0'
		flags = ''
		if taxonid in extinctp:
			flags = 'extinct'
		taxfile.write('%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n'%(taxonid, parent, name, rank, flags))

with open(synonyms_file_name, 'w') as synfile:
	print 'Writing %s'%synonyms_file_name
	synfile.write('uid\t|\tname\t|\ttype\t|\t\n')
	# These are big distractions!  They create homonyms etc.
	for synid in synonyms:
		(targetid, name, status) = synonyms[synid]
		# TBD: Chase targetid through synonyms?..
		if targetid in synonyms:
			continue
		if not (targetid in extinctp):
			synfile.write('%s\t|\t%s\t|\t%s\t|\t\n'%(targetid, name, status))
