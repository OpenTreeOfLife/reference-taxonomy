# -*- coding: utf-8 -*-

# Import script for DwC version of GBIF

# That's pro-ject' with the emphasis on the 2nd syllable.

# Python on my mac is not configured to pick up
# up packages in site-package in /usr/local (I am using the
# Apple-provided python)
import sys
sys.path.append('/usr/local/lib/python2.7/site-packages')

# To install 'dwca':
# pip install python-dwca-reader
# For documentation, see: http://python-dwca-reader.readthedocs.io/en/latest/index.html
from dwca.read import DwCAReader
from dwca.darwincore.utils import qualname

# '/Users/jar/a/ot/repo/reference-taxonomy/r/gbif-20160729/source/gbif-backbone.zip'

# The columns we want in outfile:
#   taxonID
#   parentNameUsageID
#   acceptedNameUsageID
#   scientificName or canonicalName
#   taxonRank
#   taxonomicStatus
#   datasetID (formerly nameAccordingTo)

scientific_name_uri = qualname('scientificName')
canonical_name_uri = 'http://rs.gbif.org/terms/1.0/canonicalName'

taxon_id_uri = qualname('taxonID')

column_uris = [qualname('taxonID'),
               qualname('parentNameUsageID'),
               qualname('acceptedNameUsageID'),
               canonical_name_uri,
               qualname('taxonRank'),
               qualname('taxonomicStatus'),
               qualname('datasetID')]

def project_gbif(inpath, outpath):

    print 'Opening archive', inpath

    with DwCAReader(inpath) as dwca:

        print 'Opened.  Core file has type', dwca.descriptor.core.type
        sys.stdout.flush()

        terms = {term:True for term in dwca.descriptor.core.terms}
        if True:
            print 'Columns in DwCA are:'
            for uri in terms:
                print ' ', uri
            sys.stdout.flush()

        def get_value(row, uri):
            if uri in terms:
                value = row.data[uri]
            elif uri == scientific_name_uri:
                # Assume the library has taken care of utf-8 issues for us
                value = canonical_name(row.data[canonical_name_uri])
            elif uri == taxon_id_uri:
                value = row.id
            else:
                print '** Field is required but missing:', uri
                value = ''
            return value

        with open(outpath, 'w') as outfile:
            i = 0
            for row in dwca:
                outfile.write('\t'.join(map(lambda uri: get_value(row, uri).encode('utf-8'),
                                            column_uris)))
                outfile.write('\n')
                i += 1
                if i % 50000 == 0: print i

# canonical_name() is needed for 2016 GBIF, but not for later or earlier ones
import re

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


if __name__ == '__main__':
    project_gbif(sys.argv[1], sys.argv[2])
