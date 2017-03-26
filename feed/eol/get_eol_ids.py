# From Yan Wong:
#  ((('ncbi', 1172), ('if', 596), ('worms', 123), ('irmng', 1347), ('gbif', 800)))

import csv
from org.opentreeoflife.taxa import QualifiedId

sources = [('ncbi', 1172), ('if', 596), ('worms', 123), ('irmng', 1347), ('gbif', 800)]

sources_dict = {str(id): prefix for (prefix, id) in sources}

def get_eol_ids(path, tax):
    with open(path, 'r') as infile:
        print 'loading ids file...'
        reader = csv.reader(infile)
        row_count = 0
        hits = 0
        for row in reader:
            row_count += 1
            # 35694175,"7459",596,13179252,"Byssocystis Riess, 1853"
            if len(row) != 5:
                # print 'bad row:', row  #about ten of these
                continue
            [uid, source_id, hierarchy, page_id, name] = row
            if row_count % 250000 == 0:
                print row_count, name
            probe = sources_dict.get(hierarchy)
            if probe != None:
                if try_id(probe, source_id, page_id, tax):
                    hits += 1
        print '...done', hits

def try_id(idspace, source_id, page_id, tax):
    qid = QualifiedId(idspace, source_id)
    probe = tax.lookupQid(qid)
    if probe != None:
        eid = QualifiedId('eol', page_id)
        eprobe = tax.lookupQid(eid)
        if eprobe == None:
            probe.addSourceId(eid)
            return True
        elif eprobe != probe:
            print 'page id conflict', qid, probe, eid, eprobe
    return False

