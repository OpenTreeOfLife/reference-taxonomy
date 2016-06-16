''''
Script received from Jessica Grant, 8 October 2013

This script takes as input the ssu fasta from Silva (in this case SSURef_NR99_115_tax_silva.fasta
but change as necessary.)  Also 'tax_rank.txt' for the rank file from Silva ftp.

It outputs 
  The taxonomy in the Open Tree format:
    <taxid>\t|\t<parentid>\t|\t<taxon>\t|\t<rank>\t|\t<seqid>
  A synonyms file

seqid is the unique identifier from Silva of the SSU reference sequence, 
e.g. 'A45315.1.1521', and is there for the species only.  Other 
ranks have 'no seq'

Be aware of a few things - 
    I [Jessica] have ignored plants, animals and fungi because they have reasonable taxonomies elsewhere.

    Silva has 'uncultured' as a taxon, between the species and its genus. e.g.:
    
>U81762.1.1448 Bacteria;Firmicutes;Clostridia;Clostridiales;Ruminococcaceae;uncultured;uncultured bacterium
    
    I have ignored this so this uncultured bacterium would get mapped to Ruminococcaceae

    All duplicate species names get their own unique number, but duplicate names of genera or above
    are checked for homonyms. - Homonyms are determined by having different parents - in some cases this
    can add taxa that really are the same.  e.g. Dinobryon here:
    
['Eukaryota', 'SAR', 'Stramenopiles', 'Chrysophyceae', 'Ochromonadales', 'Dinobryon', 'Dinobryon divergens']
['Eukaryota', 'SAR', 'Stramenopiles', 'Chrysophyceae', 'Ochromonadales', 'Ochromonas', 'Dinobryon', 'Dinobryon cf. sociale']    

    
    Other problems exist in Silva - for example in these two paths, 'marine group' is treated as a taxon (and is 
    picked up as homonymic) when it really isn't.
    
['Bacteria', 'Acidobacteria', 'Holophagae', 'Holophagales', 'Holophagaceae', 'marine group', 'uncultured bacterium']
['Bacteria', 'Cyanobacteria', 'SubsectionI', 'FamilyI', 'marine group', 'cyanobacterium UCYN-A']

    Or  here, where '43F-1404R' seems to be treated as a taxon when it is probably a primer set: 
    
['Bacteria', 'Acidobacteria', 'Holophagae', 'Subgroup 10', '43F-1404R', 'uncultured Acidobacteria bacterium']
['Bacteria', 'Proteobacteria', 'Deltaproteobacteria', '43F-1404R', 'uncultured bacterium']

    
    I left in things like 'Bacteria', 'Cyanobacteria', 'Chloroplast', 'Oryza sativa Japonica Group'
    even though the it could be confusing.  Chloroplast (and other symbiont) data may need special treatment

'''
import sys
import re
import string
import os, time, json, os.path
import csv
import argparse

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

pathdict = {} #given the cluster identifier, return the taxonomy path
#seqdict = {} #given the taxon name, return (<species id assigned here>,<unique sequence id from SSU ref file>) 
Seen = {} # to tell whether the taxon name has been seen before - to know if homonym check needs to be done

# Input: name of the SILVA fasta file (with sequences removed, or not)
# Return value: a dict mapping sequence specifier (e.g. 'A45315.1.1521')
# to lineage

def makePathDict(infilename):
    infile = open(infilename,'rU')
    inclusive = 0
    exclusive = 0
    for line in infile:
        if line[0] == '>':
            inclusive += 1
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
            #   taxlist.remove('uncultured') #not sure...
            pathdict[uid] = taxlist
    print "Clusters: %s  Exclusive of kill list: %s"%(inclusive, exclusive)
    return pathdict 

# Not used for now...
def checkHomonym(uid,taxon,pathdict,olduid,homfilename):
    newpath = pathdict[uid]
    oldpath = pathdict[olduid]
    newparindex = newpath.index(taxon) - 1
    oldparindex = oldpath.index(taxon) - 1
    if oldpath[oldparindex] in newpath[newparindex] and oldpath[oldparindex]: #parent of original in new path
        return True #link new to old
    else:
        hom_outfile = open(homfilename,'a')
        hom_outfile.write(taxon + ',' + str(newpath)+ ',' +str(oldpath) + '\n')
        hom_outfile.close()
        return newpath[newparindex]

ranks = {}

# No longer used - the SILVA ranks turned out to be really random

def readRanks(indir):
    rankfilename = indir + '/tax_ranks.txt'
    rankfile = open(rankfilename,'r')
    for line in rankfile:
        path,node,rank,remark = line.split('\t')
        components = path.split(';')
        if len(components) >= 2:
            rank_key = (node,components[-2])
            if rank_key in ranks and ranks[rank_key] != rank:
                print "Homonym with parent homonym", rank_key
                ranks[rank_key] = 'no rank'
            else:
                ranks[rank_key] = rank
    rankfile.close()

# Each row is genbank id, ncbi id, strain, taxon name

def get_accession_to_taxon(ncbifilename):
    accession_to_taxon = {}
    with open(ncbifilename, 'r') as ncbifile:
        for fields in csv.reader(ncbifile, delimiter='\t'):
            ncbi_id = fields[1]
            if ncbi_id != '*':
                genbank_id = fields[0]
                if len(fields) >= 3 and fields[2] != '':
                    strain = fields[2]
                else:
                    strain = None
                    if len(fields) >= 3 and fields[3] != '':
                        name = fields[3]
                    else:
                        name = None
                accession_to_taxon[genbank_id] = (ncbi_id, name, strain)
    return accession_to_taxon

synonyms = {}

taxondict = {}  # maps (parentid, name) to taxid

def processSilva(pathdict, outdir):
    rank = 'no rank' #for now
    taxfile = open(outdir + '/taxonomy.tsv.new','w')
    taxfile.write('uid\t|\tparent_uid\t|\tname\t|\trank\t|\tsourceinfo\t|\t\n')
    taxfile.write('0\t|\t\t|\tlife\t|\tno rank\t|\t\t|\t\n')
    homfilename = outdir + '/homonym_paths.txt'
    blocked_or_missing = 0
    acc_success = 0
    internal = 0
    i = 0

    # Cluster ids
    uids = [uid for (z, uid) in sorted([(len(uid), uid) for uid in pathdict.keys()])]

    # uid is a unique cluster id e.g. A58083.1.1474
    for uid in uids:
        i = i + 1
        if i % 100000 == 0: print i
        parentid = "0"
        path = pathdict[uid]
        accession = string.split(uid,".",1)[0]

        # For a single cluster, look at names on path from root
        for depth in range(0, len(path)-1):
            taxname = path[depth]
            taxon_key = (parentid, taxname)
            if taxon_key in taxondict:
                taxid = taxondict[taxon_key]    #smasher id
            else:
                taxid = "%s/#%d"%(accession, depth+1)
                # To resolve taxid, use this URL prefix: http://www.arb-silva.de/browser/ssu/silva/
                internal = internal + 1
                if depth == 0:  #taxname in ['Bacteria','Eukaryota','Archaea']: 
                    rank = 'domain'
                else:                                   
                    #returns true if this taxon and the one in the db have the same parent
                    #if taxname in seqdict:
                    #   checkHomonym(uid,taxname,pathdict,seqdict[taxname],homfilename)
                    rank = 'no rank'
                    # We considered using tax_ranks but they're not helpful.

                #seqdict[taxname] = uid
                taxondict[taxon_key] = taxid

                # Some of the 'uncultured' taxa are incorrectly assigned rank 'class' or 'order'
                if taxname == 'uncultured':
                    rank = 'no rank'
                taxfile.write("%s\t|\t%s\t|\t%s\t|\t%s\t|\t\t|\t\n" %
                              (taxid, parentid, taxname, rank))

            parentid = taxid    #for next iteration

            # Removing plants, animals, fungi, chloroplast and mitochondrial 
            # clusters - also specifying Oryza because it is problematic in 
            # this version the database (SSURef_NR99_115_tax_silva.fasta).
            if taxname in ['Metazoa',
                           'Fungi',
                           'Chloroplast',
                           'mitochondria',
                           'Herdmania',
                           'Oryza',
                           'Chloroplastida',
                           ]:
                parentid = None
                if taxname == 'Chloroplastida':
                    # What NCBI calls it
                    synonyms['Viridiplantae'] = taxid
                    # What GBIF calls it
                    synonyms['Plantae'] = taxid
                elif taxname == 'Metazoa':
                    # What GBIF calls it
                    synonyms['Animalia'] = taxid
                break

        # End of loop (for a single cluster, look at names on path from root)

        # value of parentid is set by the loop, feeds into the below.

        # Now process this particular cluster.  We're trying to insert an NCBI
        # taxon between a set of clusters and the clusters' parent in SILVA.
        # If an NCBI taxon doesn't fit inside of a cluster-parent, it's paraphyletic
        # and is omitted.
        # N.b. we may encounter the same accession number multiple times

        if parentid != None:
            if process_accession(accession, parentid, path[len(path)-1]):
                acc_success += 1
            else:
                blocked_or_missing += 1

    # End of loop over cluster uids

    print "Higher taxa: %d"%internal
    print "Accessions: %d  Mapped to NCBI: %d"%(len(acc_seen), len(accession_to_taxon))
    print "Cluster to NCBI taxon mappings: %d successful, %d blocked or missing"%(acc_success, blocked_or_missing)

    print "NCBI taxa: %d"%(len(ncbi_info))

    paraphyletic = []
    ncbi_count = 0

    # ncbi_silva_parent maps NCBI id to id of its parent in SILVA

    # Write out the tips of the smasher taxonomy.  These correspond to
    # taxa in NCBI that are placed under SILVA taxa that are one level
    # above the cluster level.

    for ncbi_id in ncbi_info.keys():
        info = ncbi_info[ncbi_id]
        parentid = info.silva_parent
        if parentid != True:
            taxid = info.sample_accession    # becomes URL
            rank = 'cluster'    # rank will be set from NCBI
            qid = "ncbi:%s" % ncbi_id
            # synonyms[qid] = taxid
            name = info.name
            taxfile.write("%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n" %
                          (taxid, parentid, name, rank, qid))
            ncbi_count = ncbi_count + 1
            # This isn't useful, is it?
            #longname = "%s %s"%(taxname, qid)
            #synonyms[longname] = taxid
        else:
            paraphyletic.append(ncbi_id)
    print "NCBI taxa incorporated: %d"%(ncbi_count)
    print "Paraphyletic NCBI taxa: %d"%(len(paraphyletic))    #e.g. 1536

    taxfile.close()
    os.rename(outdir + '/taxonomy.tsv.new', outdir + '/taxonomy.tsv')

    xref_path = outdir + '/ncbi_to_silva.tsv'
    with open(xref_path + '.new','w') as xref:
        for ncbi_id in ncbi_info.keys():
            info = ncbi_info[ncbi_id]
            parentid = info.silva_parent
            if parentid != True:
                taxid = info.sample_accession    # becomes URL
                xref.write("%s\t%s\n" % (ncbi_id, taxid))
    os.rename(xref_path + '.new', xref_path)


# Deal with one Genbank accession id.  N.b. we might encounter the same id
# multiple times.

acc_seen = {}

class Ncbi_info:
    def __init__(self, id):
        self.id = id
        self.silva_parent = None
        self.sample_accession = None
        self.name = None

ncbi_info = {}

# Process a single cluster

def process_accession(accession, parentid, name):
    if not accession in acc_seen:
        acc_seen[accession] = True
    if accession in accession_to_taxon:
        (ncbi_id, newname, strain) = accession_to_taxon[accession]
        if ncbi_id in ncbi_info:
            info = ncbi_info[ncbi_id]
            if info.silva_parent != parentid:
                info.silva_parent = True
        else:
            info = Ncbi_info(ncbi_id)
            ncbi_info[ncbi_id] = info
            info.silva_parent = parentid
            info.sample_accession = accession
            if newname != None and strain != None and not newname.endswith(strain):
                info.name = newname + ' ' + strain
                print 'strain:', info.name
            if newname != None:
                # 2016-06-15 newname != name happens 4260 times
                info.name = newname
            else:
                info.name = name
        return True
    else:
        return False

def do_synonyms(outpath):
    newpath = outpath + '.new'
    with open(newpath, "w") as outfile:
        outfile.write('uid\t|\tname\t|\t\n')
        for name in synonyms.keys():
            taxid = synonyms[name]
            outfile.write("%s\t|\t%s\t|\t\n" % (taxid, name))
    os.rename(newpath, outpath)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Process SILVA distribution to make opentree-format taxonomy')
    parser.add_argument('silva', help='silva.fast file (with or without sequences)')
    parser.add_argument('mapping', help='genbank id to NCBI taxon id mapping')
    parser.add_argument('outdir', help='taxonomy directory')
    parser.add_argument('url', help='URL for the about file')
    args = parser.parse_args()

    fasta_path = args.silva
    accessionid_to_taxonid_path = args.mapping
    outdir = args.outdir
    url = args.url
    accession_to_taxon = get_accession_to_taxon(accessionid_to_taxonid_path)
    pathdict = makePathDict(fasta_path)
    writeAboutFile(url, fasta_path, outdir)
    processSilva(pathdict, outdir)
    do_synonyms(os.path.join(outdir, 'synonyms.tsv'))

#                       if False:
#                           rank_key = (taxname,par)
#                           if rank_key in ranks:
#                               rank = ranks[rank_key].lower()
#                           else:
#                               rank = 'no rank'
#                           if rank == 'major_clade':
#                               rank = 'no rank'
