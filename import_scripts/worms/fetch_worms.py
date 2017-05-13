#!/usr/bin/python

# WoRMS had about 330,000 taxon records in 2014
# Square root of 360,000 is 600.
# If harvested over 24 hours, that would be 229 records per minute
# or 4 per second.
# Maybe: harvest 229, then wait to fill out one minute?
#        harvest 4, then wait to fill out one second?
#        harvest 8, then wait one second?

# Starts with:
#   queue.txt - list of aphia ids for subtress that remain to be harvested.
#   records/*.csv - file of digested worms records already harvested.

# Every member of the queue is an aphia id for a record that has been 
# processed, but whose children and synonyms have not.

REQUESTS_PER_SLEEP = 3
SECONDS_PER_SLEEP = 1
DEFAULT_CHUNK_SIZE = 100   # eventually maybe 800

DEFAULT_PROXY = 'http://www.marinespecies.org/aphia.php?p=soap&wsdl=1'
DEFAULT_ROOT_ID = 1  # Biota
stop_at_species = True

# Need:
#  https://sourceforge.net/projects/pywebsvcs/files/SOAP.py/

import os, sys, re, csv
import codecs  # maybe not
import argparse
from collections import deque
from time import sleep
from SOAPpy import WSDL

WORMSPROXY = WSDL.Proxy(DEFAULT_PROXY)

seen = {}

# For phase I
def fetch_worms(root, queuepath, outdir, chunk_size, chunk_count):
    queue = load_queue(queuepath, root)
    j = 0
    while len(queue) > 0 and j < chunk_count:
        chunk = get_chunk(chunk_size, queue)
        save_chunk(chunk, outdir)
        save_queue(queue, queuepath)
        j += 1

# For phase II

def fetch_worms_synonyms(outdir, chunk_count):
    done = 0
    for name in sorted(os.listdir(outdir)):
        if done >= chunk_count: break
        if name.startswith('a'):
            inpath = os.path.join(outdir, name)
            synpath = os.path.join(outdir, 's%s.csv' % name[1:-4])
            if not os.path.exists(synpath):
                print '%s -> %s' % (inpath, synpath)
                taxon_aphias = load_aphias(inpath)
                syn_aphias = get_synonym_aphias(taxon_aphias)
                save_aphias(syn_aphias, synpath)
                done += 1

def save_chunk(chunk, outdir):
    (aphias, links) = chunk
    if len(links) > 0:
        if not os.path.isdir(outdir):
            os.makedirs(outdir)
        id = links[0][0]
        print 'chunk', id
        # %07d
        path = os.path.join(outdir, 'l%07d.csv' % id)
        print 'writing', path, len(links)
        with open(path, 'w') as outfile:
            writer = csv.writer(outfile)
            writer.writerow(links_header)
            for row in links:
                writer.writerow(row)
        path = os.path.join(outdir, 'a%07d.csv' % id)
        save_aphias(aphias, path)

def save_aphias(aphias, path):
    if len(aphias) > 0:
        print 'writing', path, len(aphias)
        with open(path, 'w') as outfile:
            writer = csv.writer(outfile)
            writer.writerow(digest_header)
            for row in aphias:
                writer.writerow([fieldify(x) for x in row])

# For phase II
def load_aphias(inpath):
    aphias = []
    with open(inpath, 'r') as infile:
        reader = csv.reader(infile)
        reader.next()  # header
        for row in reader:
            aphias.append(row)
    return aphias

def fieldify(x):
    if isinstance(x, unicode):
        return x.encode('utf-8')
    elif x is True:
        return 'true'
    elif x is False:
        return 'false'
    else:
        return x

def load_queue(queuepath, root):
    q = deque()
    if os.path.exists(queuepath):
        with open(queuepath, 'r') as infile:
            for line in infile:
                id = int(line.strip())
                q.append(id)
                seen[id] = 'queued'
    else:
        q.append(root)
        seen[root] = 'queued'
    print '%s ids queued' % len(q)
    return q

def save_queue(queue, queuepath):
    print 'queued:', len(queue)
    with open(queuepath, 'w') as outfile:
        writer = csv.writer(outfile)
        for id in queue:
            writer.writerow([id])

def get_chunk(chunk_size, queue):
    record_count = [0]
    request_count = [0]
    aphias = []
    links = []
    def throttle():
        request_count[0] += 1
        if request_count[0] % REQUESTS_PER_SLEEP == 0:
            sleep(SECONDS_PER_SLEEP)
    def see(child, parent_id, rel):
        if child.AphiaID in seen:
            if child.status != 'unaccepted':
                parent_id2 = seen[child.AphiaID]
                print ('** REPEAT ENCOUNTER: %s ~ %s + %s (%s) **' %
                       (child.AphiaID,
                        parent_id2,
                        parent_id,
                        child.status))
            s = False
        else:
            # First time encountering this id
            seen[child.AphiaID] = parent_id
            aphias.append(digest(child))
            record_count[0] += 1
            s = True
        links.append((child.AphiaID, parent_id, rel))
        return s
    while len(queue) > 0 and record_count[0] < chunk_size:
        parent_id = queue.pop()
        siblings = {}

        # print 'requesting children of', parent_id
        children = sort_aphia(get_one_taxon_children(parent_id))
        to_q = []
        for child in children:

            if child.AphiaID in siblings:
                # Ignore duplicate children, and children that
                # duplicate synonyms
                continue
            siblings[child.AphiaID] = True

            # If valid id != id then it's actually a
            # synonym (perhaps of a sibling)
            if synonymp(child):
                see(child, child.valid_AphiaID, 's')
                continue

            if see(child, parent_id, 'c'):
                # Open tree policy for WoRMS: no subspecies.
                # Huge performance win on download.
                if not (stop_at_species and child.rank == 'Species'):
                    to_q.append(child.AphiaID)
        # try to do lowest numbered subtrees first
        queue.extend(to_q)

        throttle()
    return (aphias, links)

def synonymp(aphia):
    return (aphia.valid_AphiaID != aphia.AphiaID and
            aphia.valid_AphiaID != None and
            aphia.valid_AphiaID > 0)
            
# Phase II

def get_synonym_aphias(aphias):
    status_column = digest_header.index('status')
    request_count = 0
    for taxon in aphias:
        taxon_id = int(taxon[0])
        seen[taxon_id] = True
    syn_aphias = []
    for taxon in aphias:
        if taxon[status_column] == 'accepted':
            taxon_id = int(taxon[0])
            cosynonyms = {}
            # print 'requesting synonyms of', taxon_id
            # Get all aphia records for synonyms of aphia
            for syn in sort_aphia(get_one_taxon_synonyms(taxon_id)):
                if not syn.AphiaID in cosynonyms:
                    cosynonyms[syn.AphiaID] = True
                    if not syn.AphiaID in seen:
                        # First time encountering this id
                        seen[syn.AphiaID] = taxon_id
                        if synonymp(syn):
                            syn_aphias.append(digest(syn))
            request_count += 1
            if request_count % REQUESTS_PER_SLEEP == 0:
                sleep(SECONDS_PER_SLEEP)
    return syn_aphias

def sort_aphia(aphia_list):
    return sorted(aphia_list, key=(lambda aphia: aphia.AphiaID))

def digest(aphia_record):
    return (aphia_record.AphiaID,
            aphia_record.scientificname,
            aphia_record.authority,
            aphia_record.rank,
            aphia_record.status,  #e.g. 'accepted'
            aphia_record.unacceptreason,
            aphia_record.valid_AphiaID,
            aphia_record.isExtinct)

digest_header = [
    'id', 'name', 'authority', 'rank', 'status',
    'unacceptreason', 'valid', 'extinct']

links_header = [
    'id', 'parent', 'rel']

"""
Keep these aphia fields:

    AphiaID: unique and persistent identifier within WoRMS. Primary key in the database.
    scientificname: the full scientific name without authorship
    authority: the authorship information for the scientificname formatted 
      according to the conventions of the applicable nomenclaturalCode
    rank: the taxonomic rank of the most specific name in the scientificname
    status: the status of the use of the scientificname (usually a taxonomic 
      opinion). Additional technical statuses are (1) quarantined: hidden from 
      public interface after decision from an editor and (2) deleted: AphiaID 
      should NOT be used anymore, please replace it by the valid_AphiaID
      E.g. 'accepted'
    unacceptreason: the reason why a scientificname is unaccepted
    valid_AphiaID: the AphiaID (for the scientificname) of the currently accepted taxon
    isExtinct: a flag indicating an extinct organism. Possible values: 0/1/NUL
"""


MAXLENGTH = 50


def get_one_taxon_children(taxon_id):
    result = []
    offset = 0
    try:
        wsdlChildren = WORMSPROXY.getAphiaChildrenByID(taxon_id,
                                                       offset=offset)
        if wsdlChildren:
            result.extend(wsdlChildren)
            while len(wsdlChildren) == MAXLENGTH:
                offset += MAXLENGTH
                wsdlChildren = WORMSPROXY.getAphiaChildrenByID(taxon_id,
                                                               offset=offset)
                if wsdlChildren:
                    result.extend(wsdlChildren)
    except socket.error as e:
        print e
        raise RuntimeException("dummy")
    finally:
        return result


def get_one_taxon_synonyms(taxon_id):
    result = []
    offset = 0
    wsdlSyns = WORMSPROXY.getAphiaSynonymsByID(taxon_id)
    if wsdlSyns:
        result.extend(wsdlSyns)    # return wsdlSyns ?
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--synonyms', dest='synonymsp', action='store_true')
    parser.add_argument('--queue', dest='queuefile')
    parser.add_argument('--out', dest='outdir')
    parser.add_argument('--chunks', dest='chunk_count', type=int)
    parser.add_argument('--root', dest='root', type=int, default=DEFAULT_ROOT_ID)
    parser.add_argument('--chunksize', dest='chunksize', type=int,
                        default=DEFAULT_CHUNK_SIZE)
    args = parser.parse_args()
    if not args.synonymsp:
        print 'phase 1'
        fetch_worms(args.root, args.queuefile, args.outdir,
                    args.chunksize, args.chunk_count)
    else:
        print 'phase 2'
        fetch_worms_synonyms(args.outdir, args.chunk_count)
