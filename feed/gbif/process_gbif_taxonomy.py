#!/usr/bin/env python

# Command line arguments
#   1: taxon.txt
#   2: kill list
#   3: directory in which to put taxonomy.tsv and synonyms.tsv


import sys,os
from collections import Counter

"""
ignore.txt should include a list of ids to ignore, all of their children
should also be ignored but do not need to be listed
"""

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print "python process_ottol_taxonomy.py taxa.txt ignore.txt outfile"
        sys.exit(0)
    
    infile = open(sys.argv[1],"r")
    infile2 = open(sys.argv[2],"r")
    ignore = []    # stack
    for i in infile2:
        ignore.append(i.strip())
    outfile = open(sys.argv[3]+"/taxonomy.tsv","w")
    outfilesy = open(sys.argv[3]+"/synonyms.tsv","w")
    names = [] 
    parents = []    #list of ids
    count = 0
    skipcount = 0
    parent ={} #parent ids key is the id and the value is the parent
    children ={} #child ids key is the id and the value is the children
    nm_storage = {} # storing the id and the name
    nrank = {}
    synnames = {}
    syntargets = {}
    syntypes = {}
    irmngs = {}
    print "count skipped"
    infile.next()
    for row in infile:
        fields = row.strip().split("\t")
        # For information on what information is in each column see
        # meta.xml in the gbif distribution.
        id = fields[0].strip()
        if not id.isdigit():
            # Header line has "taxonID" here
            continue
        if "International Plant Names Index" in row:
            skipcount += 1
            continue
        if id == "0":
            continue #gbif incertae sedis
        rank = fields[5].strip()
        parent_id = fields[1].strip()  # parent number
        # "unclassified" doesn't occur 2013-07-02
        # "unassigned" doesn't occur 2013-07-02
        # "other" never occurs as a word
        # there are two "artificial" but they look OK
        # there is one "insertion" and it looks OK
        #if "unclassified" in row or "unassigned" in row or "other" in row or "artificial" in row or "insertion" in row:
        #    skipcount += 1
        #    continue
        if parent_id == "0":
            continue
        name = fields[4].strip()
        if rank == "form" or rank == "variety" or rank == "subspecies" or rank == "infraspecificname":
            continue
        if rank == "kingdom":
            parent_id = "0"
        if len(id) == 0 or len(name) == 0:
            skipcount += 1
            continue
        id = int(id)
        if ("IRMNG Homonym" in row) or ((parent_id == "1" or parent_id == "6") and rank == "genus" and "Interim Register of Marine" in row):
            irmngs[id] = True
        accepted_status = fields[6].strip()
        if accepted_status != "accepted":
            skipcount += 1
            synnames[id] = name
            syntargets[id] = fields[2].strip()
            syntypes[id] = accepted_status
            continue
        if len(parent_id) == 0:
            skipcount += 1
            continue
        nrank[id] = rank
        if parent_id == '':
            parent_id = 0       # life
        else:
            parent_id = int(parent_id)
        parent[id] = parent_id
        nm_storage[id] = name
        if parent_id not in children:
            children[parent_id] = []
        children[parent_id].append(id)
        parents.append(parent_id)
        names.append(name)
        count += 1
        if count % 100000 == 0:
            print count,skipcount

    infile.close()

    print "counting"
    b  = Counter(names)

    dnames = {}
    for i in b:
        if b[i] > 1:
            dnames[i] = True
    names = None  #GC

    b = Counter(parents)
    dparents = None
    dparents = b.keys()
    parents = None

    print "getting the parent child duplicates fixed"
    ignoreid = {}
    for id in nm_storage:
        if id in parent and parent[id] in nm_storage:
            if nm_storage[id] == nm_storage[parent[id]]:
#                print id,nm_storage[id],nrank[id],parent[id],nm_storage[parent[id]],nrank[parent[id]]
#                sys.exit(0)
#                if nrank[id] == nrank[parent[id]]:
                if id in children:     # If it has children,
                    idstoch = children[id]
                    for j in idstoch:
                        parent[j] = parent[id]
                ignoreid[id] = True
    for id in ignoreid.keys():
        del nm_storage[id]

    # Flush taxa from the IRMNG homonym list that don't have children
    count = 0
    for id in irmngs:
        if (not id in children) and id in nrank and nrank[id] != "species":
            if id in nm_storage:
                del nm_storage[id]
            count += 1
    print "IRMNG names deleted:", count

    #putting parentless taxa into the ignore list
    count = 0
    for id in nm_storage:
        if parent[id] not in nm_storage:
            count += 1
            if parent[id] != 0:
                ignore.append(id)
                if count % 1000 == 0:
                    print "example orphan ",id,nm_storage[id]
            else:
                print "top level ",id,nm_storage[id]
    print "orphans: ", len(ignore)

    #now making sure that the taxonomy is functional before printing to the file
    print "checking the tree structure"
    skipids = {}
    stack = ignore
    while len(stack) != 0:
        curid = stack.pop()
        if curid in skipids:
            continue
        skipids[curid] = True
        if curid in children:
            ids = children[curid]
            for id in ids:
                stack.append(id)

    for id in skipids:
        if id in nm_storage:
            del nm_storage[id]

    """
    output the id parentid name rank
    """
    print "done counting"
    count = 0
    for id in nm_storage:
        if id in parent:
            parent_id = parent[id]
        else:
            parent_id = ""
        outfile.write("%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n" %
                      (id, parent_id, nm_storage[id], nrank[id]))
        count += 1
        if count % 100000 == 0:
            print count
    outfile.write("0\t|\t\t|\tlife\t|\t\t|\t\n")
    outfile.close()

    print "synonyms"
    for id in synnames:
        target = syntargets[id]
        if target in nm_storage:
            outfilesy.write(target+"\t|\t"+synnames[id]+"\t|\t"+syntypes[id]+"\t|\t\n")
    outfilesy.close()
