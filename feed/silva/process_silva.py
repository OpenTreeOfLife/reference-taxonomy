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

class Taxon:
    def __init__(self):
        self.id = None
        self.parent_id = None
        self.name = None
        self.rank = 'no rank'
        self.height = -1    # root = 0
        self.exemplar_id = None
        self.clusters = 0

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
            cluster_id = line.split()[0].strip() # was going to use the species but there are multiple 'unidentified', for example
            taxlist_1  = line.strip(cluster_id).strip().split(';')
            cluster_id = cluster_id.lstrip('>')
            for taxname in taxlist_1:
                # JAR commented out the following... smasher takes care of these
                # if not re.search('Incertae Sedis',tax) and tax not in taxlist:            
                taxlist.append(taxname)
            #if 'uncultured' in taxlist:
            #   taxlist.remove('uncultured') #not sure...
            pathdict[cluster_id] = taxlist
    print "Clusters: %s  Exclusive of kill list: %s"%(inclusive, exclusive)
    return pathdict 

# Not used for now...
def checkHomonym(cluster_id,taxon,pathdict,olduid,homfilename):
    newpath = pathdict[cluster_id]
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

# Each row is (accession id, ncbi id, strain, taxon name)

def read_accession_to_ncbi_info(ncbifilename):
    accession_to_ncbi_info = {}
    with open(ncbifilename, 'r') as ncbifile:
        print 'Reading', ncbifilename
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
                accession_to_ncbi_info[genbank_id] = (ncbi_id, name, strain)
    return accession_to_ncbi_info

synonyms = {}

def process_silva(pathdict, outdir):
    taxa = {}
    synonyms = {}
    cluster_parent = get_higher_taxa(pathdict, taxa, synonyms)
    ncbi_to_taxon = get_tips(taxa, cluster_parent)
    return (taxa, synonyms, ncbi_to_taxon)

# Returns map from cluster id to higher taxon (actual OTT taxon will
# be somewhere in between)

def get_higher_taxa(pathdict, taxa, synonyms):
    i = 0
    taxon_id_counter = 1
    taxa_by_key = {}  # maps (parentid, name) to taxon
    cluster_parent = {}

    # This gets updated as we look down the path
    root = Taxon()
    root.id = 0
    root.name = "life"
    root.height = 0
    taxa[root.id] = root
    # cluster_id is a unique cluster reference sequence id e.g. A58083.1.1474

    # Cluster ids - sort canonically so that there's a chance they'll match up from one run to the next
    cluster_ids = [cluster_id for (z, cluster_id) in sorted([(len(cluster_id), cluster_id) for cluster_id in pathdict])]

    for cluster_id in cluster_ids:

        accession_id = string.split(cluster_id,".",1)[0]

        i = i + 1
        if i % 100000 == 0: print i, taxon_id_counter
        path = pathdict[cluster_id]
        parent = root

        # For a single cluster, look at all names on path from root to cluster
        for depth in range(0, len(path)-1):
            name = path[depth]
            taxon_key = (parent.id, name)
            taxon = taxa_by_key.get(taxon_key)
            if taxon == None:
                # Subpath not seen before; create new taxon
                taxon = Taxon()
                taxon.id = taxon_id_counter
                taxon_id_counter += 1
                taxon.parent_id = parent.id
                taxon.name = name
                if depth == 0:  #taxname in ['Bacteria','Eukaryota','Archaea']: 
                    taxon.rank = 'domain'
                taxon.height = parent.height + 1
                taxa_by_key[taxon_key] = taxon
                taxa[taxon.id] = taxon
                cluster_parent[cluster_id] = taxon

            # Removing plants, animals, fungi, chloroplast and mitochondrial 
            # clusters - also specifying Oryza because it is problematic in 
            # this version the database (SSURef_NR99_115_tax_silva.fasta).
            if name in ['Metazoa',
                        'Fungi',
                        'Chloroplast',
                        'mitochondria',
                        'Herdmania',
                        'Oryza',
                        'Chloroplastida',
                        ]:
                if name == 'Chloroplastida':
                    # What NCBI calls it
                    synonyms['Viridiplantae'] = taxon
                elif name == 'Metazoa':
                    # What GBIF calls it
                    synonyms['Animalia'] = taxon

            # Update for next iteration down lineage
            parent = taxon

        # Done traversing path
        cluster_parent[cluster_id] = taxa[parent.id]

        # Set examplar for lineage, if not set already
        accession_id = string.split(cluster_id,".",1)[0]
        anc = parent
        while anc.exemplar_id == None:
            anc.exemplar_id = accession_id
            if anc.parent_id == None: break
            anc = taxa[anc.parent_id]

    print 'Selected clusters:', len(cluster_parent)
    print 'Higher taxa:', len(taxa)
    return cluster_parent

# Populate the taxa table with taxa covering clusters.
# Some of these will be single clusters, others groups of cluster.
# (For now at least.)
# Returns map from ncbi id to taxon; if there are multiple taxa, value in the 
# map is True.

def get_tips(taxa, cluster_parent):

    # Cluster ids - sort canonically so that there's a chance they'll match up from one run to the next
    cluster_ids = [cluster_id for (z, cluster_id) in sorted([(len(cluster_id), cluster_id) for cluster_id in cluster_parent.keys()])]

    # Map from (parent_taxon_id, ncbi) to list of clusters... and an id for the list...
    taxa_by_key = {}

    # Map from integer id to external id G01123/#4
    ncbi_to_taxon = {}

    tip_taxa = []

    for cluster_id in cluster_parent:
        parent_taxon = cluster_parent.get(cluster_id)
        if parent_taxon != None:
            accession_id = string.split(cluster_id,".",1)[0]
            ncbi_info = accession_to_ncbi_info.get(accession_id)
            if ncbi_info != None:
                (ncbi_id, name, strain) = ncbi_info

                if name != None and strain != None and not name.endswith(strain):
                    name = "%s %s" % (newname, strain)
                    print 'strain:', name

                taxon_key = (parent_taxon.id, ncbi_id, strain)
                taxon = taxa_by_key.get(taxon_key)
                if taxon == None:
                    taxon = Taxon()

                    # Choose a unique id for this taxon
                    if accession_id in taxa:
                        id = cluster_id
                    else:
                        id = accession_id
                    taxon.id = id
                    taxon.parent_id = parent_taxon.id
                    taxon.name = name
                    taxon.height = parent_taxon.height + 1
                    taxa[id] = taxon
                    taxa_by_key[taxon_key] = taxon

                    # Save it for next pass (name and rank)
                    tip_taxa.append((taxon, ncbi_id))

                taxon.clusters += 1

    paraphyletic_count = 0

    max_clusters = -1    # 11039
    for (taxon, ncbi_id) in tip_taxa:
        if taxon.clusters > max_clusters:
            max_clusters = taxon.clusters 
        if taxon.clusters > 5000:
            print '%s ncbi:%s (%s) has %s clusters' % (taxon.id, ncbi_id, taxon.name, taxon.clusters)

        # This turns out to be misleading.  The actual taxon could be bigger 
        # than one cluster.
        if False and taxon.clusters == 1:
            taxon.rank = 'cluster'

        # Find NCBI taxa whose cluster all have same parent (one taxon)
        other = ncbi_to_taxon.get(ncbi_id)
        if other == None:
            ncbi_to_taxon[ncbi_id] = taxon
        elif other == taxon:
            # shouldn't happen
            break
        elif other != True:
            ncbi_to_taxon[ncbi_id] = True
            paraphyletic_count += 1

        # We'll have homonyms galore, so distinguish them by name
        # if taxon.clusters == 1 and ncbi_to_taxon[ncbi_id] == True:
        if ncbi_to_taxon[ncbi_id] == True:
            # taxon.name = '%s (SILVA %s)' % (taxon.name, taxon.id)
            taxon.name = None  # suppress

    print 'Tip taxa:', len(tip_taxa)
    print 'Max clusters per tip taxon:', max_clusters
    print 'NBCI taxa that span multiple SILVA "genera":', paraphyletic_count
    return ncbi_to_taxon

def write_taxonomy(taxa, synonyms, ncbi_to_taxon, url, nodesfilename, outdir):
    taxpath = os.path.join(outdir, 'taxonomy.tsv')
    newtaxpath = taxpath + '.new'
    with open(newtaxpath, 'w') as taxfile:
        taxfile.write('uid\t|\tparent_uid\t|\tname\t|\trank\t|\tsourceinfo\t|\t\n')
        for taxon in taxa.itervalues():
            taxid = form_id(taxon)
            if taxid == None:
                continue
            elif taxon.parent_id in taxa:
                parent_id = form_id(taxa[taxon.parent_id])
            else:
                parent_id = ''
            source_info = ''
            probe = accession_to_ncbi_info.get(taxid)
            if probe != None:
                source_info = 'ncbi:' + probe[0]
            taxfile.write("%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n" %
                          (taxid, parent_id, taxon.name, taxon.rank, source_info))
    os.rename(newtaxpath, taxpath)

    synpath = os.path.join(outdir, 'synonyms.tsv')
    newsynpath = synpath + '.new'
    with open(newsynpath, "w") as outfile:
        outfile.write('uid\t|\tname\t|\t\n')
        for name in synonyms.keys():
            taxon = synonyms[name]
            taxid = form_id(taxon)
            if taxid != None:
                outfile.write("%s\t|\t%s\t|\t\n" % (taxid, name))
    os.rename(newsynpath, synpath)

    write_ncbi_to_taxonid(ncbi_to_taxon, outdir)
    writeAboutFile(url, nodesfilename, outdir)

def form_id(taxon):
    if taxon.name == None:
        return None
    elif taxon.height == 0:
        return '0'
    elif taxon.exemplar_id != None:
        return "%s/#%s" % (taxon.exemplar_id, taxon.height)
    elif isinstance(taxon.id, int):
        return None
    else:
        return taxon.id

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


# NCBI id to SILVA/Open Tree taxon id
# Map from NCBI id to taxon and from there to taxon id.

def write_ncbi_to_taxonid(ncbi_to_taxon, outdir):
    xref_path = os.path.join(outdir, 'ncbi_to_silva.tsv')
    with open(xref_path + '.new','w') as xref:
        print 'Writing', xref_path
        for ncbi_id in ncbi_to_taxon:
            taxon = ncbi_to_taxon[ncbi_id]
            if taxon != True:
                xref.write("%s\t%s\n" % (ncbi_id, taxon.id))
    os.rename(xref_path + '.new', xref_path)


class Ncbi_info:
    def __init__(self, id):
        self.id = id
        self.silva_parent = None
        self.sample_accession = None
        self.name = None

ncbi_info = {}

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
    accession_to_ncbi_info = read_accession_to_ncbi_info(accessionid_to_taxonid_path)
    pathdict = makePathDict(fasta_path)
    (taxa, synonyms, ncbi_to_taxon) = process_silva(pathdict, outdir)
    write_taxonomy(taxa, synonyms, ncbi_to_taxon, url, args.silva, outdir)


#                       if False:
#                           rank_key = (taxname,par)
#                           if rank_key in ranks:
#                               rank = ranks[rank_key].lower()
#                           else:
#                               rank = 'no rank'
#                           if rank == 'major_clade':
#                               rank = 'no rank'
