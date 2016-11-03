# jython?  yes, I think so.

from org.opentreeoflife.taxa import Taxonomy, Taxon
from org.opentreeoflife.taxa import QualifiedId

import os, sys, csv, json

home = '..'

def dir_key(d):
    s = d.split('.')
    return (s[0], int(s[1]))

with open(os.path.join(home, 'resources', 'captures.json'), 'r') as infile:
    captures_info = json.load(infile)

source_info = {}

for capture_info in captures_info:
    if capture_info["capture_of"] == 'ott':
        source_info[capture_info["name"]] = capture_info["sources"]

ncbi_merges = {}
with open(os.path.join(home, 'tax/ncbi/forwards.tsv'), 'r') as infile:
    for line in infile:
        s = line.split('\t')
        ncbi_merges[s[0].strip()] = s[1].strip()
print 'NCBI merges:', len(ncbi_merges)

def canonicalize(qid):
    if qid.prefix == 'ncbi':
        nid = ncbi_merges.get(qid.id)
        if nid != None:
            return QualifiedId('ncbi', nid)
        else:
            return qid
    elif qid.prefix == 'IF':
        return QualifiedId('if', qid.id)
    else:
        return qid

def doit(ottpath, registry_path):

    # Registration records (qid, capture, taxon.name) indexed by id
    registrations_by_id = {}

    # OTT ids indexed by qid.toString()
    registrations_by_qid = {}

    dirs = []
    for file in os.listdir(ottpath):
        if file.startswith('ott'):
            full = os.path.join(ottpath, file)
            if os.path.isdir(full):
                dirs.append(file)
    dirs = sorted(dirs, key=dir_key)
    for dir in dirs:
        if not dir in source_info:
            print '** no source info for', dir

    if not os.path.isdir(registry_path):
        os.makedirs(registry_path)

    seqnum = 0

    for dir in dirs:
        print >>sys.stderr, dir
        info = source_info[dir]
        full = os.path.join(ottpath, dir)
        if os.path.isdir(os.path.join(full, 'ott')):
            full = os.path.join(full, 'ott')
        elif os.path.isdir(os.path.join(full, dir)):
            full = os.path.join(full, dir)

        ott = Taxonomy.getRawTaxonomy(full + '/', 'ott')
        new_regs = []
        dups = 0
        changes = 0
        merges = 0

        for taxon in ott.taxa():
            id = int(taxon.id)
            qids = taxon.sourceIds
            if qids == None:
                print >>sys.stderr, '** No source: %s %s' % (taxon, dir)
                qids = [QualifiedId('no-source', str(id))]
            qid = qids[0]

            can_qids = [canonicalize(q) for q in qids]

            # Existing id(s) for this qid
            prev_id = None
            for q in can_qids:
                old_ids = registrations_by_qid.get(q.toString())
                if old_ids != None:
                    prev_id = old_ids[-1]
                    break
            if prev_id == id:
                # Re-used!
                continue

            # Existing qid(s) for this id
            regs = registrations_by_id.get(id)

            if regs == None:
                # Are we creating a new id for a qid that already has one?
                if prev_id == None:
                    reg_type = ''       # New id
                else:
                    reg_type = 'dup of %s' % prev_id
                    dups += 1
            else:
                prev_reg = regs[-1]      # most recent registration
                prev_qid = prev_reg[1]   # e.g. gbif:4275326
                can_prev_qid = canonicalize(prev_qid)
                # If previous qid is among the current qids, this is a simple reuse.
                reuse = None
                for q in can_qids:    # e.g. ncbi:1155186,gbif:4275326
                    if q.equals(can_prev_qid): # Java
                        # Re-used!  No new registration
                        reuse = q
                        break
                if reuse != None:
                    continue
                # We're changing the qid for a registered id
                if prev_id == None:
                    # Reverting to a previous qid?
                    if qid in [reg[1] for reg in regs]:
                        print >>sys.stderr, '%s returning' % id
                        reg_type = 'return %s' % prev_qid.toString() # Java
                    else:
                        reg_type = 'was %s' % prev_qid.toString() # Java
                    changes += 1
                else:
                    reg_type = 'merge %s %s' % (prev_qid.toString(), prev_id) # Java
                    merges += 1

            # Need to make a new registration (id/qid association).
            reg = (id, qid, info.get(qid.prefix, ''), reg_type, taxon.name)
            new_regs.append(reg)

        # Write new registrations to file
        if len(new_regs) > 0:
            new_regs = sorted(new_regs, key=lambda(reg):reg[0])
            print >>sys.stderr, ('%s registrations [%s, %s]' %
                                 (len(new_regs), new_regs[0][0], new_regs[-1][0]))
            print >>sys.stderr, ('%s dups, %s merge, %s change' % (dups, merges, changes))
            chunksize = 100000
            start = 0
            while start < len(new_regs):
                stop = min(start + chunksize, len(new_regs))
                seqnum += 1
                with open(os.path.join(registry_path,
                                       'ids-%03d-%s.csv' % (seqnum, dir)),
                                       # "%s-%s.csv" % (str(new_regs[start][0]), str(new_regs[stop-1][0]))
                          'w') as outfile:
                    writer = csv.writer(outfile)
                    for i in range(start, stop):
                        if i >= len(new_regs): break # needed?
                        (id, qid, version, reg_type, name) = new_regs[i]
                        writer.writerow([id, qid, version, reg_type])
                start = stop

        # Stow the registrations for reference next time around
        for reg in new_regs:
            id = reg[0]
            regs = registrations_by_id.get(id)
            if regs == None:
                registrations_by_id[id] = [reg]
            else:
                regs.append(reg)
            qstring = canonicalize(qid).toString()
            ids = registrations_by_qid.get(qstring)
            if ids == None:
                registrations_by_qid[qstring] = [id]
            else:
                ids.append(id)


# doit('../repo/files.opentreeoflife.org/ott', 'registry')
# doit('test', 'test_registry')


doit(sys.argv[1], sys.argv[2])


"""
mkdir test
for d in ../repo/files.opentreeoflife.org/ott/*; do
    if [ -e $d/*/taxonomy* ]; then
        b=`basename $d`
        target=test/$b/ott
        mkdir -p $target
        echo $target/taxonomy.tsv
        head -100000 $d/*/taxonomy* >$target/taxonomy.tsv
    fi
done
"""
