# Command like arguments: something like
#      feed/irmng/in/IRMNG_DWC.csv
#      feed/irmng/in/IRMNG_DWC_SP_PROFILE.csv
#      tax/irmng/taxonomy.tsv
#      tax/irmng/synonyms.tsv

import csv, string, sys

status_for_keeps = {}
for status in [
               '',
               'conservandum',
               'nom. cons.',
               'Nom. cons.',
               'nom. cons. des.',
               'nom. cons. prop.',
               'protectum',
               'Nomen novum',
               'correctum',
               'orth. cons.',
               'legitimate',
               'www.nearctica.com/nomina/beetle/colteneb.htm',
               'Ruhberg et al., 1988',
               'later usage',
               ]:
    status_for_keeps[status] = True

not_extinct = ['1531',     # Sarcopterygii
               '10565',    # Saurischia
               '118547',    # Aviculariidae
               '1402700', # Trophomera
               # '11919',    # Didelphimorphia
               # '1021564',  # Cruciplacolithus
               # '1530',     # Actinopterygii
               
               #'1170022',  # Tipuloidea
               #'1340611',  # Retaria
               #'1124871',  # Labyrinthulomycetes [Labyrinthomorpha??]
               #'102024',   # Ophiurinidae - problem is Ophiurina
               #'1064058',  # Rhynchonelloidea genus/superfamily
               #'1114655',  # Tetrasphaera - different from GBIF
               ]

# These are the taxa with taxonomic status '' that occurred in phylesystem as of
# June 2016 (when these taxa were deleted from OTT).  (from deprecated.tsv)

keep_these = {}

for (id, name) in [
    ('10180190', 'Opulaster opulifolius'),
    ('11899420', 'Palaemonetes granulosus'),
    ('11704707', 'Olivioxantho denticulatus'),
    ('10527330', 'Chamaeleolis chameleontides'),
    ('11399158', 'Phyrignathus lesuerii'),
    ('10527966', 'Cylicia magna'),
    ('11444963', 'Epicrates anguilifer'),
    ('11078615', 'Egernia whitei'),
    ('10522666', 'Trogon aurantiventris'),
    ('10692084', 'Tauraco livingstoni'),
    ('10525002', 'Piculus leucolalemus'),
    ('10520170', 'Archaeopteryx lithographica'),
    ('11444785', 'Mesopropithecus pithecoides'),
    ('11167068', 'Zalambdalestes lechei'),
    ('10531957', 'Protungulatum donnae'),
    ('11024850', 'Megaladapis madagascariensis'),
    ('11078603', 'Megaladapis grandidieri'),
    ('11458858', 'Anthropornis nordenskjoeldi'),
    ('11081142', 'Gobipteryx minuta'),
    ('11390044', 'Pagophilus groenlandica'),
    ('10793056', 'Ommatophoca rossi'),
    ('10525092', 'Sivatherium giganteum'),
    ('10692824', 'Mesohippus bairdi'),
    ('10689467', 'Penaeus semisculcatus'),
    ('10543655', 'Palaemonetes atribunes'),
    ('10530648', 'Albunea occulatus'),
    ('102843', 'Hypsidoridae'),
    ('10697026', 'Badumna longinquus'),
    ('10184114', 'Cylactis pubescens'),
    ('11256401', 'Melanobatus leucodermis'),
    ('11083597', 'Squilla mikado'),
    ('11102182', 'Basilosaurus cetoides'),
    ('11103647', 'Pseudastacus pustulosa'),
    ('10532033', 'Hyopsodus paulus'),
]:
    keep_these[id] = name

irmng_file_name = sys.argv[1]
profile_file_name = sys.argv[2]
taxonomy_file_name = sys.argv[3]
synonyms_file_name = sys.argv[4]

taxa = {}
synonyms = {}
roots = []

class Taxon:
    def __init__(self, id, parentid, name, rank, tstatus, nstatus):
        self.id = id
        self.parentid = parentid
        self.name = name
        self.rank = rank
        self.tstatus = tstatus
        self.nstatus = nstatus
        self.keep = False
        self.extinctp = False

def read_irmng():

    # 0 "TAXONID","SCIENTIFICNAME","SCIENTIFICNAMEAUTHORSHIP","GENUS",
    # 4 "SPECIFICEPITHET","FAMILY","TAXONRANK","TAXONOMICSTATUS",
    # 8 "NOMENCLATURALSTATUS","NAMEACCORDINGTO","ORIGINALNAMEUSAGEID",
    # 11 "NAMEPUBLISHEDIN","ACCEPTEDNAMEUSAGEID","PARENTNAMEUSAGE",
    # 14 "PARENTNAMEUSAGEID","TAXONREMARKS","MODIFIED","NOMENCLATURALCODE"

    rows = 0

    with open(irmng_file_name, 'rb') as csvfile:
        csvreader = csv.reader(csvfile)
        header = csvreader.next()
        if header[5] != 'FAMILY':
            print >>sys.stderr, '** Unexpected column name in header row', header[-3]
        for row in csvreader:
            taxonid = row[0]
            longname = row[1]
            auth = row[2]
            rank = row[6]
            tstatus = row[7]         # TAXONOMICSTATUS
            nstatus = row[8]         # NOMENCLATURALSTATUS
            syn_target_id = row[12]
            parent = row[-4]
            if parent == '':
                roots.append(taxonid)
            # Calculate taxon name
            genus = row[3]
            if rank == 'species':
                epithet = row[4]
                name = ('%s %s')%(genus,epithet)
            elif rank == 'genus':
                name = genus
            elif rank == 'family':
                family = row[5]
                name = family
            elif len(auth) > 0 and longname.endswith(auth):
                name = longname[0:len(longname)-len(auth)-1]
            else:
                name = longname
            taxon = Taxon(taxonid, parent, name, rank, tstatus, nstatus)
            if (tstatus == 'synonym' or (syn_target_id != '' and syn_target_id != taxonid)):
                taxon.parentid = syn_target_id
                synonyms[taxonid] = taxon
            else:
                taxa[taxonid] = taxon
            rows += 1
            if rows % 250000 == 0:
                print >>sys.stderr, rows, taxonid, name
                # FOR DEBUGGING
                # break

    print >>sys.stderr, "%s taxa, %s synonyms" % (len(taxa), len(synonyms))

def fix_irmng():

    # Get rid of all synonym of a synonym

    # "10704","Decapoda Latreille, 1802","Latreille, 1802",,,,"order",,,,,,,"Malacostraca","1190","cf. Decapoda (Mollusca)","01-01-2012","ICZN"

    loser_synonyms = {}
    for syn in synonyms.itervalues():
        if syn.parentid in synonyms:
            loser_synonyms[syn.id] = True
    print >>sys.stderr, "Indirect synonyms:", len(loser_synonyms)
    for syn_id in loser_synonyms:
        del synonyms[syn_id]

    # Short-circuit taxon parents that are synonyms

    loser_parent_count = 0
    for taxon in taxa.itervalues():
        if taxon.parentid in synonyms:
            taxon.parentid = synonyms[taxon.parentid].parentid
            loser_parent_count += 1
    print >>sys.stderr, "Indirect parents:", loser_parent_count

    # Decide which taxa to keep

    keep_count = 0
    no_status = 0
    missing_parent_count = 0
    for taxon in taxa.itervalues():
        if taxon.keep: continue
        if not taxon.tstatus in keep_these:
            if taxon.tstatus == 'synonym': continue
            if taxon.tstatus == '':
                # reduces number of kept taxa from 1685133 to 1351145
                no_status += 1
                if no_status <= 20:
                    print >>sys.stderr, 'No status: %s(%s)' % (taxon.id, taxon.name)
                continue
            if not taxon.nstatus in status_for_keeps: continue
        scan = taxon
        while not scan.keep:
            scan.keep = True
            keep_count += 1
            if scan.parentid == '':
                break
            parent = taxa.get(scan.parentid)
            if parent == None:
                missing_parent_count += 1
                break
            scan = parent

    print >>sys.stderr, "Keeping %s taxa" % keep_count
    print >>sys.stderr, "%s missing parents" % missing_parent_count
    print >>sys.stderr, "%s status is ''" % no_status

    # Read the file that has the extinct annotations

    with open(profile_file_name, 'rb') as csvfile:
        csvreader = csv.reader(csvfile)
        header = csvreader.next()
        if header[1] != 'ISEXTINCT':
            print >>sys.stderr, "** Expected to find ISEXTINCT in header row but didn't:", header[1]
        for row in csvreader:
            taxonid = row[0]
            taxon = taxa.get(taxonid)
            if taxon == None: continue
            taxon.extinctp = (row[1] == 'TRUE')
            if taxonid in not_extinct:
                if not taxon.extinctp:
                    print >>sys.stderr, 'Already not extinct: %s(%s)' % (taxonid, taxon.name)
                else:
                    print >>sys.stderr, 'Fixing extinctness of %s(%s)' % (taxonid, taxon.name)
                    taxon.extinctp = False

def extinctness_report():
    # Report on nonextinct descended from extinct

    count = 0
    for taxon in taxa.itervalues():
        if taxon.keep and not taxon.extinctp:
            parentid = taxon.parentid
            parent = taxa.get(parentid)
            if parent != None and parent.extinctp:
                count += 1
                if taxon.rank != 'species':
                    print >>sys.stderr, ("Extant taxon %s(%s) with extinct parent %s(%s)"%
                                         (taxon.id, taxon.name, parentid, parent.name))

    print >>sys.stderr, 'Extant taxa with extinct parent:', count

# Write it out

# Returns True if one or more children also got written

def write_irmng():

    def write_taxon(taxon, taxfile):
        parentid = taxon.parentid
        if parentid == '':
            parentid = '0'
        flags = ''
        if taxon.extinctp:
            flags = 'extinct'
        taxfile.write('%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n'%(taxon.id, parentid, taxon.name, taxon.rank, flags))

    with open(taxonomy_file_name, 'w') as taxfile:
        print 'Writing %s'%taxonomy_file_name
        taxfile.write('%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n'%('uid', 'parent_uid', 'name', 'rank', 'flags'))
        taxfile.write('%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n'%('0', '', 'life', 'no rank', ''))
        for taxon in taxa.itervalues():
            if taxon.keep:
                write_taxon(taxon, taxfile)

    with open(synonyms_file_name, 'w') as synfile:
        print 'Writing %s'%synonyms_file_name
        synfile.write('uid\t|\tname\t|\ttype\t|\t\n')
        for syn in synonyms.itervalues():
            taxon = taxa.get(syn.parentid)
            if taxon != None and taxon.keep and not taxon.extinctp:
                status = syn.nstatus
                if status == '':
                    status = syn.tstatus
                    if status == '': status = 'synonym'
                synfile.write('%s\t|\t%s\t|\t%s\t|\t\n'%(syn.parentid, syn.name, status.lower()))

read_irmng()
fix_irmng()
extinctness_report()
write_irmng()
