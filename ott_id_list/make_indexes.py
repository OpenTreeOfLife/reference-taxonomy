import csv, os, sys

dirname = sys.argv[1]

count = 0

by_id = {}
by_qid = {}

for name in sorted(os.listdir(dirname)):
    path = os.path.join(dirname, name)
    if path.endswith('.csv'):
        with open(path, 'r') as infile:
            reader = csv.reader(infile)
            for row in reader:
                if len(row) == 4:
                    (id, qid, source, note) = row
                    ott = ''
                else:
                    (id, qid, source, ott, note) = row
                id = int(id)
                if qid.startswith('IF'): qid = qid.lower()
                qids = by_id.get(id)
                if qids == None:
                    qids = [qid]
                    by_id[id] = qids
                else:
                    qids.append(qid)
                ids = by_qid.get(qid)
                if ids == None:
                    ids = [id]
                    by_qid[qid] = ids
                else:
                    ids.append(id)
                count += 1
    print >>sys.stderr, path, count

print >>sys.stderr, len(by_qid), 'qids'

with open('by_qid.csv', 'w') as outfile:
    writer = csv.writer(outfile)
    for qid in sorted(by_qid.keys()):
        # ott ids can get out of order, e.g. gbif:6197514,3190274;3185577
        writer.writerow([qid, ';'.join([str(i) for i in by_qid[qid]])])

print >>sys.stderr, len(by_id), 'ids'

with open('by_id.csv', 'w') as outfile:
    writer = csv.writer(outfile)
    for id in by_id:
        writer.writerow([id, ';'.join(by_id[id])])

