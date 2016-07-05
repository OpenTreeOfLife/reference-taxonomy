#!/usr/bin/env python

# Author: Stephen Smith

# Arguments:
#   download - T or F - whether or not to download the tar.gz file from NCBI
#   downloaddir - where to find (or put) the tar.gz and its contents
#   kill list file
#   destination dir - where taxonomy.tsv etc. are to be put

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
    downloaddir = sys.argv[2]   # e.g. feed/ncbi/tmp
    nodesfilename = downloaddir + "/nodes.dmp"
    namesfilename = downloaddir + "/names.dmp"
    skipfile = sys.argv[3]
    taxdir = sys.argv[4]
    url = sys.argv[5]

    aboutfilename = taxdir+"/about.json"
    with open(aboutfilename, "w") as aboutfile:
        aboutfile.write('{ "prefix": "ncbi",\n')
        aboutfile.write('  "prefixDefinition": "http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=",\n')
        aboutfile.write('  "description": "NCBI Taxonomy",\n')
        # Get file date from nodes.dmp in downloaddir
        # os.path.getmtime(file)   => number of seconds since epoch
        ncbitime = os.path.getmtime(nodesfilename)
        tuple_time = time.gmtime(ncbitime)
        iso_time = time.strftime("%Y-%m-%dT%H:%M:%S", tuple_time)
        aboutfile.write('  "source": {"URL": "%s", "date": "%s"},\n'%(url, iso_time))
        aboutfile.write('}\n')

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

    count = 0
    parent_ids = {} #key is the child id and the value is the parent
    cid = {} #key is the parent and value is the list of children
    nrank = {} #key is the node id and the value is the rank
    with open(nodesfilename,"r") as nodesf:
        for line in nodesf:
            spls = line.split("\t|\t")
            node_id = spls[0].strip()
            parentid = spls[1].strip()
            rank = spls[2].strip()
            parent_ids[node_id] = parentid
            nrank[node_id] = rank
            if parentid not in cid: 
                cid[parentid] = []
            cid[parentid].append(node_id)
            count += 1
            if count % 100000 == 0:
                print count

    # Removed "unclassified" 2013-04-25
    skip = []
    # skip = ["viral","other","viroids","viruses","artificial","x","environmental","unknown","unidentified","endophyte","endophytic","uncultured","scgc","libraries","virus","mycorrhizal samples"]
    skipids = {}
    #run through the skip ids file
    if os.path.isfile(skipfile):
        with open(skipfile,"r") as skipidf:
            for line in skipidf:
                skipids[line.strip()] = True
    
    count = 0
    idstoexclude = []
    nm_storage = {}
    lines = {}
    synonyms = {}
    namesd = []
    allnames = []
    with open(namesfilename,"r") as namesf:
        for line in namesf:
            line = line.strip()
            spls = line.split("\t|") #if you do \t|\t then you don't get the name class right because it is "\t|"
            node_id = spls[0].strip()
            par = parent_ids[node_id]
            # was name = spls[1].strip().replace("[","").replace("]","")
            name = spls[1].strip()
            homonc = spls[2].strip() #can get if it is a series here
            nm_c = spls[3].strip()   # scientific name, synonym, etc.
            nm_keep = True
            name_parts = name.split(" ")
            for j in name_parts:
                if j.lower() in skip:
                    nm_keep = False
            if node_id in skipids:
                nm_keep = False
            if nm_keep == False:
                idstoexclude.append(node_id)
                continue
            if "<series>" in homonc:
                name = name + " series"
            if "subgroup <" in homonc: #corrects some nested homonyms
                name = homonc.replace("<","").replace(">","")
            if nm_c != "scientific name":
                # scientific name   - the name used in OTT as primary.
                # synonym
                # equivalent name  - usually misspelling or spelling variant
                # misspelling
                # authority  - always extends scientific name
                # type material  - bacterial strain as type for prokaryotic species ??
                # common name
                # genbank common name
                # blast name   - 247 of them - a kind of common name
                # in-part (e.g. Bacteria in-part: Monera)
                # includes (what polarity?)
                if nm_c != "in-part":
                    if node_id not in synonyms:
                        synonyms[node_id] = []
                    synonyms[node_id].append(line)
            else:
                lines[node_id] = line
                nm_storage[node_id] = name
                allnames.append(name)
            count += 1
            if count % 100000 == 0:
                print count
    print "number of lines in names file: ",count

    #get the nameids that are double
    c = Counter(allnames)
    namesd = []
    for i in c:
        if c[i] > 1:
            namesd.append(i)
    ndoubles = []
    with open(namesfilename, "r") as namesf:
        for line in namesf:
            spls = line.strip().split("\t|") #IF YOU DO \T|\T THEN YOU DON'T GET THE NAME CLASS RIGHT BECAUSE IT IS "\T|"
            node_id = spls[0].strip()
            name = spls[1].strip()
            if name in namesd:
                ndoubles.append(node_id)

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
            if i in parent_ids:
                if nm_storage[i] == nm_storage[parent_ids[i]]:
                #do something for the genus 
                    if nrank[parent_ids[i]] == "genus":
                        final_nm_storage[i] = nm_storage[parent_ids[i]]+" "+nrank[i]+" "+nm_storage[i]
                    else:
                        idstoch = cid[i]
                        for j in idstoch:
                            parent_ids[j] = parent_ids[i]
                        if i in synonyms:
                            for j in synonyms[i]:
                                if parent_ids[i] in synonyms:
                                    synonyms[parent_ids[i]].append(j)
                                else:
                                    synonyms[parent_ids[i]] = [j]
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
                        if tcur in parent_ids:
                            tcur = parent_ids[tcur]
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
        node_id = spls[0].strip()
        prid = parent_ids[spls[0]].strip()
        sname = spls[1].strip()

        #changed from sname to nm_storage to fix the dup name issue
        if i in final_nm_storage:
            nametowrite = final_nm_storage[i]
        else:
            nametowrite = nm_storage[i]

        # if it is the root node then we need to make its parent id blank and rename it "life"
        if nametowrite == "root":
            nametowrite = "life"
            prid = ""
        elif nametowrite == 'environmental samples':
            nametowrite = nm_storage[parent_ids[i]] + ' ' + nametowrite
            if False:
                if nametowrite not in synonyms:
                    synonyms[i] = []
                # kludge, would be better to change synonyms table representation
                synonyms[i].append('%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n' % (i, 'environmental samples', '', 'synonym'))
        rankwrite = nrank[node_id]
        outfile.write(node_id+"\t|\t"+prid+"\t|\t"+nametowrite+"\t|\t"+rankwrite+"\t|\t\n")

    outfile.close()

    outfilesy.write("uid\t|\tname\t|\ttype\t|\t\n")

    for i in synonyms:
        if i in lines:
            for j in synonyms[i]:
                spls = j.split("\t|\t")
                node_id = spls[0].strip()
                sname = spls[1].strip()
                nametp = spls[3].strip()
                outfilesy.write(node_id+"\t|\t"+sname+"\t|\t"+nametp+"\t|\t\n")
    outfilesy.close()

    mergedfilename = downloaddir + "/merged.dmp"
    if os.path.isfile(mergedfilename):
        merge_count = 0
        with open(mergedfilename, 'r') as mergedfile:
            with open(taxdir + '/forwards.tsv', 'w') as forwardsfile:
                for line in mergedfile:
                    row = line.split('|')
                    from_id = row[0].strip()
                    to_id = row[1].strip()
                    forwardsfile.write("%s\t%s\n" % (from_id, to_id))
                    merge_count += 1
        print 'number of merges:', merge_count
