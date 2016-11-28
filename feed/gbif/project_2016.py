# -*- coding: utf-8 -*-

# In principle the column numbers could be extracted programmatically
# from meta.xml

import sys, re

def project_2016_gbif(inpath, outpath):
    i = 0
    with open(inpath, 'r') as infile:
        with open(outpath, 'w') as outfile:
            for line in infile:
                row = line.split('\t')
                scientific = row[6].decode('utf-8')
                canonical = canonical_name(scientific)
                outfile.write('%s\t%s\t%s\t%s\t%s\t%s\t%s\n' %
                              (row[1], # taxonID
                               row[3], # parentNameUsageID
                               row[4], # acceptedNameUsageID
                               canonical.encode('utf-8'), # canonicalName
                               row[7], # taxonRank
                               row[10], # taxonomicStatus
                               row[2], # nameAccordingTo / datasetID
                               ))
                if row[1] == '8395045':
                    print row[6]
                    print 'raw utf-8 ', list(row[6])
                    print 'sci uni   ', list(scientific)
                    print 'can uni   ', list(canonical)
                    print 'can utf-8 ', list(canonical.encode('utf-8'))
                    want = u'Navicula allorgei É.Manguin, 1952'
                    print 'want uni  ', list(want)
                    print 'want utf-8', list(want.encode('utf-8'))
                if i % 500000 == 0: print i, scientific, '=>', canonical
                i += 1


epi = u" +[a-záåäëèéïóöü'×0-9.-]+"

canon = u"[A-Za-zÖäåàáãçéèëïíøöóü×?-]+(|%s|%s%s|%s%s%s)" % (epi, epi, epi, epi, epi, epi)

auth1 = u" +(d'|von |van |de |dem |der |da |del |di |le |[A-ZÄÁÅÁÁÇÐÉÉÎİŠŚÔØÖÔÓÜÚŽĎĐŁŞČŘȘ(]).*"

auth2 = u"%s, [12][0-9][0-9][0-9]" % epi

trimmer = re.compile(u"(%s)((%s)|(%s))" % (canon, auth1, auth2))

def canonical_name(name):
    m = trimmer.match(name)
    if m:
        return m.group(1)
    else:
        return name

# Problem cases
#  Bütschliella longicollus Georgevitch, 1941
#  ×Pseudadenia P.F. Hunt, 1971
#  Terpsinoë americana (Bailey) Grunow, 1868
#  Corymbitodes kiiensis Ôhira, 1999
#  Navicula allorgei É.Manguin

# print canonical_name(u'Bütschliella longicollus Georgevitch, 1941')  - works

project_2016_gbif(sys.argv[1], sys.argv[2])

# QC check: 
#  grep " [0-9][0-9][0-9][0-9]	" feed/gbif/work/projection_2016.tsv  >tmp.tmp
