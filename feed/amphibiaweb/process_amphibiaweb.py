# 
# read amphib_names.txt
# 
# read header row
# get column indexes species, gaa_name    synonymies  itis_names
# save header row for rank information
# 
# set up a table for the higher taxa.
# 
# for each row
# 
# taxon names are in columns 0 up to species
# 
# for each taxon name
#   (might be blank)
#   if in table, no action, but remember id in case it's a parent
#   if not
#     emit:  new id, parent, name, rank
#     if species: collect and emit synonyms (that aren't same as primary name)
#
# convert latin-1 to utf-8

import sys
import os

taxondict = {}

infilename = sys.argv[1]
outdir = sys.argv[2]

infile = open(infilename, 'r')
taxfile = open(outdir + 'taxonomy.tsv','w')
synfile = open(outdir + 'synonyms.tsv','w')

header = infile.readline().split('\t')

species_column = header.index('species')
genus_column = header.index('genus')
synonymies_column = header.index('synonymies')
gaa_name_column = header.index('gaa_name')
itis_names_column = header.index('itis_names')
aweb_uid_column = header.index('aweb_uid')
#     

def process():
    emit("uid", "parent_uid", "name", "rank")
    emitsyns("name", "uid", {})
    id = 0
    for line in infile:
        row = line.split('\t')
        parentid = None
        uid = row[aweb_uid_column]
        for i in range(species_column+1):
            name = row[i].decode('latin-1').encode('utf-8')
            if name != '':
                if name in taxondict:
                    parentid = taxondict[name]
                else:
                    rank = header[i]
                    if rank == 'species':
                        name = row[genus_column] + ' ' + name
                        thisid = uid
                    else:
                        thisid = 'T' + str(id)
                        id = id + 1
                    emit(thisid, parentid, name, rank)
                    taxondict[name] = thisid
                    seen = {}
                    seen[name] = True
                    emitsyns(row[synonymies_column], thisid, seen)
                    emitsyns(row[gaa_name_column], thisid, seen)
                    emitsyns(row[itis_names_column], thisid, seen)
                    parentid = thisid

def emit(id, parentid, name, rank):
    taxfile.write("%s\t%s\t%s\t%s\n"%(id, parentid, name.strip(), rank))

def emitsyns(syns, id, seen):
    if syns != '':
        for syn in syns.split(','):
            if not (syn in seen):
                seen[syn] = True
                syn = syn.decode('latin-1').encode('utf-8')
                synfile.write("%s\t%s\n"%(syn.strip(),id))

process()
infile.close()
taxfile.close()
synfile.close()
