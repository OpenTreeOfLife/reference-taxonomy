# Command line arguments:
#  - path to directory containing previously generated .csv files
#  - path to new OTT version
#  - path to captures file   e.g. ../resources/captures.json
#  - path to output directory (will hold idlists/ott*.csv and by_qid.csv)

import sys, os, csv, json

def extend_idlist(previous, ott_path, ott_version, outpath):
    info = get_sources_table(ott_version, ott_path)

    # previous is a directory of .csv files, one per OTT version
    registrations = read_registrations(previous)
    (registrations_by_id, ids_for_qid) = index_registrations(registrations)

    new_regs = do_one_taxonomy(ott_version, ott_path, info, registrations_by_id, ids_for_qid)
    write_registrations(new_regs, outpath)
    write_indexes(registrations_by_id, ids_for_qid, os.path.dirname(os.path.dirname(outpath)))

def do_one_taxonomy(ott_version, ott_path, info, registrations_by_id, ids_for_qid):
    if ott_path.endswith('/'): ott_path = ott_path[0:-1]

    ott = read_taxonomy(ott_path)

    new_regs = []
    merges = changes = dups = 0
    info_losers = 0

    for taxon in sorted_taxa(ott):
        (id, qids) = taxon
        if len(qids) == 0:
            qids = [('ott', str(id))]
            # Could be from edit/
            print >>sys.stderr, '** Sourceless taxon: %s in %s' % (id, ott_version)

        qid = qids[0]

        # Existing id(s) for this qid
        prev_id = None
        for q in qids:
            old_ids = ids_for_qid.get(q)
            if old_ids != None:
                prev_id = old_ids[-1]
                break
        if prev_id == id:
            # Re-used!
            continue

        # Does this id already map?
        regs = registrations_by_id.get(id)

        if regs == None:
            # Are we creating a new id for a qid that already has one?
            if prev_id == None:
                note = ''       # New id
            else:
                note = 'dup of %s' % prev_id
                dups += 1
        else:
            prev_reg = regs[-1]      # most recent registration
            prev_qid = prev_reg[1]   # e.g. gbif:4275326
            # If previous qid is among the current qids, this is a simple reuse.
            reuse = None
            for q in qids:    # e.g. ncbi:1155186,gbif:4275326
                if q == prev_qid: # Java
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
                    note = 'return %s' % unparse_qid(prev_qid)
                else:
                    note = 'was %s' % unparse_qid(prev_qid)
                changes += 1
            else:
                note = 'merge %s %s' % (unparse_qid(prev_qid), prev_id) # Java
                merges += 1

        # Need to make a new registration (id/qid association).
        (src, sid) = qid
        if src == 'ott':
            source_version = ''
        elif src.startswith('additions'):
            # Should be git commit
            source_version = ''
        elif not src in info:
            info_losers += 1
            if info_losers <= 10:
                # Could be from edits/
                print '** Missing version info for %s (OTT id %s)' % (src, id)
                print info
            source_version = ''
        else:
            source_version = info[unicode(src)]
        reg = (id, qid, source_version, ott_version, note)
        new_regs.append(reg)

    print merges, 'merges'
    print changes, 'changes'
    print dups, 'dups'
    return new_regs

def read_taxonomy(tax_path):
    # was: ott = Taxonomy.getRawTaxonomy(ott_path + '/', 'ott')
    tax = []
    path = os.path.join(tax_path, 'taxonomy.tsv')
    if not os.path.exists(path):
        path = os.path.join(tax_path, 'taxonomy')
    with open(path, 'r') as infile:
        print 'Reading', path
        id_column = 0
        info_column = None
        source_column = None
        sourceid_column = None
        for line in infile:
            row = line.strip().split('\t|\t')
            if row[id_column].isdigit():
                qids = []
                if info_column != None:
                    sourceids = row[info_column]
                    if sourceids == 'null':
                        qids = []
                    else:
                        qids = map(parse_qid, sourceids.split(','))
                elif source_column != None:
                    sid = row[sourceid_column]
                    if sid == '':
                        qids = []
                    else:
                        qids = [(row[source_column].lower(), sid)]
                qids = [canonicalize(q) for q in qids]
                id = int(row[id_column])
                tax.append((id, qids))
                if len(tax) % 500000 == 0:
                    print len(tax), id
            else:
                id_column = row.index('uid')
                if id_column == None:
                    print '** No uid column'
                if 'sourceinfo' in row:
                    info_column = row.index('sourceinfo')
                elif 'source' in row:
                    source_column = row.index('source')
                    sourceid_column = row.index('sourceid')
    print 'taxa:', len(tax)
    return tax

def parse_qid(qid_string):
    if qid_string.startswith('http'):
        return (qid_string, None)
    parts = qid_string.split(':', 1)
    if len(parts) == 2:
        (prefix, n) = parts
        return (prefix, n)
    else:
        print 'Odd qid: %s' % qid_string
        return (qid_string, None)

def unparse_qid(qid):
    (prefix, n) = qid
    if n == None:
        return prefix
    else:
        return '%s:%s' % (qid)

def sorted_taxa(ott):
    ott.sort(key=lambda (id, qids): id)
    return ott

def index_registrations(registrations):
    registrations_by_id = {}
    ids_for_qid = {}
    for reg in registrations:
        (id, qid, version, reg_type, name) = reg
        regs = registrations_by_id.get(id)
        if regs == None:
            registrations_by_id[id] = [reg]
        else:
            regs.append(reg)
        if qid[1] != None:
            ids = ids_for_qid.get(qid)
            if ids == None:
                ids_for_qid[qid] = [id]
            else:
                ids.append(id)
    return (registrations_by_id, ids_for_qid)

def read_registrations(previous_path):
    regs = []
    names = os.listdir(previous_path)
    names = [name for name in names if name.startswith('ott') and name.endswith('.csv')]
    def sort_key(name):
        (major, minor) = name[3:][0:-4].split('.')
        return (int(major), int(minor))
    names = sorted(names, key=sort_key)
    for name in names:
        path = os.path.join(previous_path, name)
        with open(path, 'r') as infile:
            print 'Reading', path
            reader = csv.reader(infile)
            for row in reader:
                (id, qid, source, ottver, note) = row
                regs.append((int(id), parse_qid(qid), source, ottver, note))
    print 'Got %s registrations' % len(regs)
    return regs

def write_registrations(new_regs, outpath):
    print 'Writing %s registrations to %s' % (len(new_regs), outpath)
    with open(outpath, 'w') as outfile:
        writer = csv.writer(outfile)
        for (id, qid, source, ottver, note) in new_regs:
            writer.writerow([id, unparse_qid(qid), source, ottver, note])

# Return mapping source series -> source version for a particular OTT version
# as stored in properties file

def get_sources_table(ott_version, ott_path):
    path = os.path.join(ott_path, '..', 'properties.json')
    if not os.path.exists(path):
        path = os.path.join(ott_path, '..', '..', 'properties.json')
        if not os.path.exists(path):
            path = os.path.join('properties', ott_version, 'properties.json')
            if not os.path.exists(path):
                print >>sys.stderr, '** Missing sources for %s' % ott_version
    with open(path, 'r') as infile:
        info = json.load(infile)
    sources = info["sources"]
    # Convert Makefile source name to idspace name
    if "fung" in sources and not "if" in sources:
        sources["if"] = sources["fung"]
    return sources

def canonicalize(qid):
    (prefix, n) = qid
    if prefix == 'IF':
        return ('if', n)
    else:
        return qid

def write_indexes(registrations_by_id, ids_for_qid, path):

    qid_path = os.path.join(path, 'by_qid.csv')

    print >>sys.stderr, 'Writing %s qids to %s' % (len(ids_for_qid), qid_path)

    with open(qid_path, 'w') as outfile:
        writer = csv.writer(outfile)
        for qid in sorted(ids_for_qid.keys()):
            # ott ids can get out of order, e.g. gbif:6197514,3190274;3185577
            writer.writerow([unparse_qid(qid), ';'.join([str(i) for i in ids_for_qid[qid]])])

    id_path = os.path.join(path, 'by_id.csv')

    print >>sys.stderr, 'Writing %s ids to %s' % (len(registrations_by_id), id_path)

    with open(id_path, 'w') as outfile:
        writer = csv.writer(outfile)
        for id in registrations_by_id:
            regs = registrations_by_id[id]
            qid_strings = [unparse_qid(reg[1]) for reg in regs]
            writer.writerow([id, ';'.join(qid_strings)])



if __name__ == '__main__':
    # previous, ott, ottver, captures, out
    extend_idlist(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
