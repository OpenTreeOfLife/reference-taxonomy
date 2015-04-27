''''
Script modified from process_silva.py
Peter Midford 24 April 2015

The command line arguments are the inputs and output directories (doesn't generate
a json 'about' file so no URL is required).

SILVATAXONOMYFILE specifies the taxonomy file from the inputs directory
SILVACLUSTERFILE specifies the clusters file from the inputs directory

This script takes as input the Silva provided taxonomy file and the Silva provided clusters
file that defines clusters of sequences (accession numbers), with each cluster identified by
a reference cluster (accession id).

It attaches cluster members as new tips on the taxonomy and writes the taxonomy file out
in approximately the same format as the Silva taxonomy.  The main difference is that no attempt
is made to report the start and end points of accessions in clusters other than the reference
sequence (which come from the original taxonomy) - the start and end points of these tip sequences
are set to 0.
'''

import sys
import re
import string
import os, time, json, os.path


SILVATAXONOMYFILE = 'taxmap_slv_ssu_nr_119.txt'
SILVACLUSTERFILE = 'silva.clstr'

class Silva_taxon:
    def __init__(self,id,name):
        self.id = id
        self.lineage = None
        self.parent_id = None
        self.name = name
        self.rank = 'no rank'
        self.start = 0
        self.end = 0

    
def readSilvaTaxonomy(indir):
    """this function was broken up like this for testing"""
    return readSilvaTaxonomy1(indir + '/' + SILVATAXONOMYFILE)


def readSilvaTaxonomy1(path):
    taxonomy = {}
    tax_file = None
    with open(path, "r") as tax_file:
        for line in tax_file:
            fields = line.split('\t')
            id=fields[0].strip()
            split_lineage = fields[3].strip().split(';')
            leaf = Silva_taxon(id=fields[0].strip(), name=fields[4].strip('\n'))
            leaf.start = fields[1].strip()
            leaf.end = fields[2].strip()
            leaf.lineage = split_lineage
            taxonomy[id] = leaf
    return taxonomy


class Silva_cluster:
    def __init__(self,reference):
        self.reference = reference
        self.members = set()

    def add_member(self,new_member):
        self.members.add(new_member)

def readSilvaClusters(indir):
    path = indir + '/' + SILVACLUSTERFILE
    cluster_accessions = {}
    with open(path,"r") as clusters_file:
        cur_cluster = None
        for line in clusters_file:
            tokens = line.split(' ')
            for i,token in enumerate(tokens):
                if token[0] == '>':  # assessions start with >
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


def updateLineages(lineages, clusters):
    newLineages = {}
    for t_id in lineages.keys():
        if t_id not in clusters:
            print "No cluster found for {}".format(t_id)
        else:
            cluster = clusters[t_id]
            if len(cluster.members) > 1:
                parent = lineages[t_id]
                new_lineage = parent.lineage
                new_lineage.append(parent.name)
                for token in cluster.members:
                    if token != t_id:
                        new_name = t_id + " " + token
                        new_id = t_id + "." + token
                        new_leaf = Silva_taxon(id=new_id,
                                              name=new_name)
                        new_leaf.lineage = new_lineage
                        lineages[new_id] = new_leaf

SILVA_TEMPLATE = "{}\t{}\t{}\t{}\t{}\n"


def dumpSilvaLineages(lineages,lineages_output_path):
    """dump silva lineages in original format"""
    with open(lineages_output_path,"w") as lineages_file:
        for id,taxon in lineages.items():
            new_lineage = ";".join(taxon.lineage)
            outstr = SILVA_TEMPLATE.format(id,
                                           taxon.start,
                                           taxon.end,
                                           new_lineage,
                                           taxon.name)
            lineages_file.write(outstr)

TAXON_KEYS = {}

def inflateTaxonomy(lineages):
    """builds full taxonomy out of lineages"""
    taxonomy = []
    for leaf_id,leaf in lineages.items():
        taxonomy.append(leaf)
        parentid = "0"
        leafname = leaf.name
        for depth,parentName in enumerate(leaf.lineage):
            taxon_key = (parentid, parentName)
            if taxon_key in TAXON_KEYS:
                taxid = TAXON_KEYS[taxon_key]
            else:
                taxid = "%s/#%d"%(leaf_id, depth+1)
                newTaxon = Silva_taxon(id=taxid, name=parentName)
                TAXON_KEYS[taxon_key] = taxid
                newTaxon.parent_id = parentid
                if depth == 0:
                    newTaxon.rank = 'domain'
                taxonomy.append(newTaxon)
            parentid = taxid
        leaf.parent_id = parentid
    return taxonomy

TAXONOMY_TEMPLATE = "%s\t|\t%s\t|\t%s\t|\t%s\t|\t\t|\t\n"        
def exportSilvaTaxonomy(taxonomy, taxonomy_output_path):
    with open(taxonomy_output_path,"w") as taxonomyFile:
        taxonomyFile.write('uid\t|\tparent_uid\t|\tname\t|\trank\t|\tsourceinfo\t|\t\n')
        taxonomyFile.write('0\t|\t\t|\tlife\t|\tno rank\t|\t\t|\t\n')
        for taxon in taxonomy:
            outstr = TAXONOMY_TEMPLATE%(taxon.id, taxon.parent_id, taxon.name, taxon.rank)
            taxonomyFile.write(outstr)
        
        
def main():
    import collections
    indir = sys.argv[1]
    outdir = sys.argv[2]
    silvaLineages = readSilvaTaxonomy(indir)
    print "Silva leaf count = {}".format(len(silvaLineages))
    sortedRawLineages = collections.OrderedDict(sorted(silvaLineages.items(),
                                                       key=lambda t: t[0]))
    dumpSilvaLineages(sortedRawLineages,'sortedraw.x.txt')
    silvaClusters = readSilvaClusters(indir)
    print "Silva cluster count = {}".format(len(silvaClusters))
    updateLineages(silvaLineages,silvaClusters)
    silvaTaxonomy = inflateTaxonomy(silvaLineages)
    exportSilvaTaxonomy(silvaTaxonomy, outdir + "/test.tsv")
    #dumpSilvaLineages(silvaTaxonomy, "augmentedSilva.tsv.new")
    #os.rename('augmentedSilva.tsv.new', outdir + '/augmentedSilva.tsv')

#import cProfile     
#cProfile.run('main()')
if __name__ == '__main__':
    main()
    
