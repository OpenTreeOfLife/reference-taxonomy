# coding=utf-8
# Trivial conversion of EOL digest to a form more easily ingested by
# OTT assembly.

# The digest looks something like this:
# 63908503,"849976",123,46591739,"Martinottiella minuta (Hofker, 1951)"
# 63908504,"849977",123,46591740,"Martinottiella primaeva (Cushman, 1913)"
# 63908505,"849672",123,46591741,"Eggerella nitens (Wiesner, 1931)"
# 63908506,"850015",123,46591742,"Pseudoclavulina humilis (Brady, 1884)"
# 63908507,"851930",123,46591743,"Pseudoclavulina skeletori Strotz, 2015"
# 63908508,"480194",123,46591744,"Siphoniferoides siphonifera (Brady, 1881)"
# 63908509,"852076",123,46591745,"Clavulina tokaiensis Asano, 1936"
# 63908510,"849849",123,46591746,"Goesella cylindrica (Cushman, 1922)"
# 63908511,"881565",123,46591747,"Gyrovalvulina columnatortilis (d'Orbigny in Guérin-Méneville, 1844)"
# 63908512,"850217",123,46591748,"Bigenerina taiwanica Nakamura, 1937"

# Mapping of EOL classication ids to OTT idspaces, from Yan Wong:
#  ((('ncbi', 1172), ('if', 596), ('worms', 123), ('irmng', 1347), ('gbif', 800)))

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
