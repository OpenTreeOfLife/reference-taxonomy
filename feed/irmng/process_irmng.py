
import csv, string, sys

# feed/irmng/in/IRMNG_DWC_20140113.csv

taxa = {}
synonyms = {}

# 0 "TAXONID","SCIENTIFICNAME","SCIENTIFICNAMEAUTHORSHIP","GENUS",
# 4 "SPECIFICEPITHET","FAMILY","TAXONRANK","TAXONOMICSTATUS",
# 8 "NOMENCLATURALSTATUS","NAMEACCORDINGTO","ORIGINALNAMEUSAGEID",
# 11 "NAMEPUBLISHEDIN","ACCEPTEDNAMEUSAGEID","PARENTNAMEUSAGE",
# 14 "PARENTNAMEUSAGEID","TAXONREMARKS","MODIFIED","NOMENCLATURALCODE"

with open(sys.argv[1], 'rb') as csvfile:
	csvreader = csv.reader(csvfile)
	header = csvreader.next()
	if header[5] != 'FAMILY':
		print '** Unexpected column name in header row', header[-3]
	for row in csvreader:
		taxonid = row[0]
		longname = row[1]
		genus = row[3]
		family = row[5]
		rank = row[6]
		status = row[7]
		syn_target = row[12]
		parent = row[-4]
		if syn_target != '' and syn_target != taxonid:
			synonyms[taxonid] = syn_target
			continue
#		if (status != '' and status != 'accepted' and status != 'valid' and 
#		       status != 'available' and status != 'proParteSynonym'):
#			continue
		if rank == 'genus':
			name = genus
		elif rank == 'family':
			name = family
		else:	
			name = longname
		taxa[taxonid] = (parent, name, rank)

extinctp = {}

not_extinct = ['1530',     # Actinopterygii
			   '1531',	   # Sarcopterygii
			   '10565',    # Saurischia
			   '11919',	   # Didelphimorphia
			   '1170022',  # Tipuloidea
			   '1340611',  # Retaria
			   '1124871',  # Labyrinthulomycetes
			   '102024',   # Ophiurinidae
			   '1064058',  # Rhynchonelloidea
			   '1021564',  # Cruciplacolithus
			   '1114655',  # Tetrasphaera
			   ]

with open(sys.argv[2], 'rb') as csvfile:
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
	(parent, name, rank) = taxa[taxonid]
	if parent in synonyms:
		parent = synonyms[parent]
	if not (taxonid in extinctp):
		while parent in extinctp:
			count += 1
			if count <= 10:
				print >>sys.stderr, ("Non-extinct taxon with extinct parent: %s(%s) in %s(%s)"%
									 (name, taxonid, taxa[parent][1], parent))
			del extinctp[parent]
			parent = taxa[parent][0]    # tuple (parent, name, rank)
			if parent in synonyms:
				parent = synonyms[parent]
print >>sys.stderr, "Non-extinct taxa with extinct parent:", count

print '%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t'%('uid', 'parent_uid', 'name', 'rank', 'flags')
for taxonid in taxa:
	(parent, name, rank) = taxa[taxonid]
	if parent in synonyms:
		parent = synonyms[parent]
	flags = ''
	if taxonid in extinctp:
		flags = 'extinct'
	print '%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t'%(taxonid, parent, name, rank, flags)

