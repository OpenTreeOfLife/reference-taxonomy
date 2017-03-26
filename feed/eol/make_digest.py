# From Yan Wong:
#  ((('ncbi', 1172), ('if', 596), ('worms', 123), ('irmng', 1347), ('gbif', 800)))

# 	gunzip -c $< | grep ',596,\|,1172,\|,123,\|,1347,\|,800,' > $@.new

import sys, csv

sources = [('ncbi', 1172), ('if', 596), ('worms', 123), ('irmng', 1347), ('gbif', 800)]

sources_dict = {str(id): prefix for (prefix, id) in sources}

writer = csv.writer(sys.stdout)

row_count = 0
hit_count = 0

for row in csv.reader(sys.stdin):
    row_count += 1
    if len(row) != 5:
        # print 'bad row:', row  #about ten of these
        continue
    [uid, source_id, hierarchy, page_id, name] = row
    if row_count % 1000000 == 0:
        print >>sys.stderr, row_count, name
    probe = sources_dict.get(hierarchy)
    if probe != None:
        writer.writerow([page_id, probe, source_id])
        hit_count += 1

print >>sys.stderr, "%s input rows, %s output rows" % (row_count, hit_count)
