#!/usr/bin/env python

# Command line arguments
#   1: taxon.txt
#   2: kill list
#   3: directory in which to put taxonomy.tsv and synonyms.tsv

# Assumed column order in GBIF dump - this is almost certain to change 
# should there ever be a new version of GBIF
# 0:taxonID	1:parentNameUsageID	2:acceptedNameUsageID	3:scientificName
#	4:canonicalName	5:taxonRank	6:taxonomicStatus	7:nomenclaturalStatus
#	8:genus	9:specificEpithet	10:infraspecificEpithet	11:namePublishedIn
#	12:nameAccordingTo	13:kingdom	14:phylum	15:class	16:order	17:family

import sys,os
from collections import Counter

"""
ignore.txt should include a list of ids to ignore, all of their children
should also be ignored but do not need to be listed
"""

incertae_sedis_kingdom = 0

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print "python process_ottol_taxonomy.py taxa.txt ignore.txt outfile"
        sys.exit(0)
    
    ignorefile = open(sys.argv[2],"r")
    to_ignore = []    # stack
    for i in ignorefile:
        to_ignore.append(int(i.strip()))
    to_ignore.append(incertae_sedis_kingdom)  #kingdom incertae sedis

    infile = open(sys.argv[1],"r")
    outfile = open(sys.argv[3]+"/taxonomy.tsv","w")
    outfilesy = open(sys.argv[3]+"/synonyms.tsv","w")
    infile_taxon_count = 0
    infile_synonym_count = 0
    count = 0
    bad_id = 0
    no_parent = 0
    parent ={}      #key is taxon id, value is the parent
    children ={}    #key is taxon id, value is list of children (ids)
    nm_storage = {} #key is taxon id, value is the name
    nrank = {}      #key is taxon id, value is rank
    synnames = {}   #key is synonym id, value is name
    syntargets = {} #key is synonym id, value is taxon id of target
    syntypes = {}   #key is synonym id, value is synonym type
    to_remove = []  #list of ids
    paleos = []     #ids that come from paleodb
    flushed_because_source = 0
    print "taxa synonyms no_parent"
    infile.next()
    for row in infile:
        fields = row.strip().split('\t')
        # For information on what information is in each column see
        # meta.xml in the gbif distribution.
   
        # acceptedNameUsageID
        syn_target_id_string = fields[2].strip()
        synonymp = syn_target_id_string.isdigit()

        if synonymp:
            infile_synonym_count += 1
        else:
            infile_taxon_count += 1

        id_string = fields[0].strip()
        if len(id_string) == 0 or not id_string.isdigit():
            # Header line has "taxonID" here
            bad_id += 1
            continue
        id = int(id_string)

        name = fields[4].strip()
        if name == '':
            bad_id += 1
            continue

        source = fields[12].strip()
        tstatus = fields[6].strip()  # taxonomicStatus

        # Filter out IRMNG and IPNI tips
        if (("IRMNG Homonym" in source) or ("Interim Register of Marine" in source) or
            ("International Plant Names Index" in source)):
            flushed_because_source += 1
            if synonymp:
                continue
            else:
                to_remove.append(id)
        elif synonymp:
            synnames[id] = name
            syntargets[id] = int(syn_target_id_string)
            syntypes[id] = tstatus    # heterotypic synonym, etc.
            continue
        elif "Paleobiology Database" in source:
            paleos.append(id)

        if tstatus == 'doubtful' or tstatus == 'synonym':
            to_remove.append(id)
            continue
        if tstatus != 'accepted':
            print id, name, tstatus, source

        rank = fields[5].strip()
        if rank == "form" or rank == "variety" or rank == "subspecies" or rank == "infraspecificname":
            to_ignore.append(id)

        parent_id_string = fields[1].strip()
        if len(parent_id_string) == 0 and rank != 'kingdom':
            no_parent += 1
            continue

        # Past all the filters, time to store
        nm_storage[id] = name
        nrank[id] = rank

        if len(parent_id_string) > 0:
            parent_id = int(parent_id_string)
            parent[id] = parent_id
            if parent_id not in children:
                children[parent_id] = [id]
            else:
                children[parent_id].append(id)

        count += 1
        if count % 100000 == 0:
            print count, len(synnames), no_parent

    infile.close()

    print ('%s taxa, %s synonyms\n' % (infile_taxon_count, infile_synonym_count))

    print ('%s bad id; %s no parent id; %s synonyms; %s bad source' % 
           (bad_id, no_parent, len(synnames), flushed_because_source))

    # Parent/child homonyms now get fixed by smasher

    # Flush terminal taxa from IRMNG and IPNI (OTT picks up IRMNG separately)
    count = 0
    for id in to_remove:
        if (not id in children): # and id in nrank and nrank[id] != "species":
            if id in nm_storage:
                del nm_storage[id]
                # should remove from children[parent[id]] too
            count += 1
    print "tips removed (IRMNG and IPNI):", count

    # Put parentless taxa into the ignore list.
    # This isn't really needed any more; smasher can cope with multiple roots.
    count = 0
    for id in nm_storage:
        if id in parent and parent[id] not in nm_storage:
            count += 1
            if parent[id] != 0:
                to_ignore.append(id)
                if count % 1000 == 0:
                    print "example orphan ",id,nm_storage[id]
    print "orphans to be pruned: ", count

    # Now delete the taxa-to-be-ignored and all of their descendants.
    if len(to_ignore) > 0:
        print 'pruning %s taxa' % len(to_ignore)
        seen = {}
        stack = to_ignore
        while len(stack) != 0:
            curid = stack.pop()
            if curid in seen:
                continue
            seen[curid] = True
            if curid in children:
                for id in children[curid]:
                    stack.append(id)
        for id in seen:
            if id in nm_storage:
                del nm_storage[id]

    """
    output the id parentid name rank
    """
    print "writing %s taxa" % len(nm_storage)
    outfile.write("uid\t|\tparent_uid\t|\tname\t|\trank\t|\t\n")

    count = 0
    for id in nm_storage:
        parent_id = ""
        if id == incertae_sedis_kingdom:
            print "kingdom incertae sedis should have been deleted by now"
        elif id in parent:
            parent_id = str(parent[id])
        elif nrank[id] == 'kingdom':
            parent_id = "0"
        outfile.write("%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n" %
                      (id, parent_id, nm_storage[id], nrank[id]))
        count += 1
        if count % 100000 == 0:
            print count
    outfile.write("0\t|\t\t|\tlife\t|\t\t|\t\n")
    outfile.close()

    print "writing %s synonyms" % len(synnames)
    outfilesy.write('uid\t|\tname\t|\ttype\t|\t\n')
    for id in synnames:
        target = syntargets[id]    # taxon id of target (int)
        if target in nm_storage:
            outfilesy.write('%s\t|\t%s\t|\t%s\t|\t\n' %
                            (target, synnames[id], syntypes[id]))
    outfilesy.close()

    print 'writing %s paleodb ids' % len(paleos)
    paleofile = open(sys.argv[3]+'/paleo.tsv', 'w')
    for id in paleos:
        paleofile.write(('%s\n' % id))
    paleofile.close()
