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
                canenc = canonical.encode('utf-8')
                outfile.write('%s\t%s\t%s\t%s\t%s\t%s\t%s\n' %
                              (row[1], # taxonID
                               row[3], # parentNameUsageID
                               row[4], # acceptedNameUsageID
                               canenc, # canonicalName
                               row[7], # taxonRank
                               row[10], # taxonomicStatus
                               row[2], # nameAccordingTo / datasetID
                               ))
                if i % 500000 == 0: print i, scientific.encode('utf-8'), '=>', canenc
                i += 1


# Cases to deal with:
#  Foo bar
#  Foo bar Putnam
#  Foo bar Putnam, 1972
#  Foo bar Putnam, 4723     no authority
#  Foo bar Putnam 1972      no authority (in GBIF)
#  Enterobacteria phage PA-2
#  Ajuga pyramidalis L.	


lower = u"a-záåäàãçëéèïíøöóü'×?"
upper = u"A-ZÄÁÅÁÁÇČÐĎĐÉÉÎİŁŘŠŚŞȘÔØÖÔÓÜÚŽ"

epithet = u" +[%s0-9.-]+" % lower

# Matches a canonical name
canon_re = u"[A-ZÖ%s-]+(|%s|%s%s|%s%s%s)" % (lower, epithet, epithet, epithet, epithet, epithet, epithet)

auth_re = u" +(d'|von |van |de |dem |der |da |del |di |le |f\\. |[%s(])(..|\\.).*" % (upper)

trimmer = re.compile(u"(%s)(%s)" % (canon_re, auth_re))

year_re = re.compile(u".*, [12][0-9][0-9][0-9?]\\)?")

has_digit = re.compile(u".*[0-9].*")

count = 0

def canonical_name(name):
    global count
    if ' phage ' in name or name.endswith(' phage'): return name
    if ' virus ' in name or name.endswith(' virus'): return name
    m = trimmer.match(name)
    if m != None:
        canon = m.group(1)
        # group 1 = canonical name
        # group 2 = epithet(s)
        # group 3 = authority
        # group 4 = capital letter or prefix
        if has_digit.match(name):
            haz = year_re.match(name) != None
        else:
            haz = True
        if count < 30 and not haz:
            print "%s '%s' '%s'" % (('+' if haz else '-'), name, canon)
            count += 1
        if haz:
            if canon == 'Enterobacteria phage':
                print '! %s => %s' % (name, canon)
            return canon
        else:
            return name
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
