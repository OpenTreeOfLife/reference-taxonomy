# Python on my mac is not configured to pick up
# up packages in site-package in /usr/local (I am using the
# Apple-provided python)
import sys
sys.path.append('/usr/local/lib/python2.7/site-packages')

# To install 'dwca':
# pip install python-dwca-reader
# For documentation, see: http://python-dwca-reader.readthedocs.io/en/latest/index.html
from dwca.read import DwCAReader
from dwca.darwincore.utils import qualname as qn

# '/Users/jar/a/ot/repo/reference-taxonomy/r/gbif-20160729/source/gbif-backbone.zip'

archive = sys.argv[1]
print 'archive is', archive

print '1'

def project_gbif(inpath, outpath):

    with DwCAReader(inpath) as dwca:

        print '2'
        sys.stdout.flush()

        print 'Core file has type', dwca.descriptor.core.type
        sys.stdout.flush()

        print 'Terms are', map(lambda (x): x.split('/')[-1], dwca.descriptor.core.terms)
        sys.stdout.flush()

        # The columns we want in outfile:
        #   taxonID
        #   parentNameUsageID
        #   acceptedNameUsageID
        #   scientificName or canonicalName
        #   taxonRank
        #   taxonomicStatus
        #   datasetID (formerly nameAccordingTo)

        columns = ['taxonID',
                   'parentNameUsageID',
                   'acceptedNameUsageID',
                   'canonicalName',
                   'taxonRank',
                   'taxonomicStatus',
                   'datasetID']

        column_uris = map(qn, columns)
        canonical_name_uri = qn('canonicalName')
        scientific_name_uri = qn('scientificName')

        def get_qn(shortname):
            q = qn(shortname)
            if q in column_uris:
                return q
            else:
                print 'Did not find term:', shortname
                return None

        def get_value(row, uri):
            if uri in row:
                value = row[uri]
            elif uri == scientific_name_uri:
                # Assume the library has taken care of utf-8 issues for us
                value = canonical_name(row[canonical_name_uri])
            else:
                print '** field is required but missing:', uri
            return value

        with open(outpath, 'w') as outfile:
            i = 0
            for row in dwca:
                outfile.write('\t'.join(map(lambda uri: get_value(row, uri).encode('utf-8'), columns)))
            i += 1
            if i % 100000 == 0: print i, scientific.encode('utf-8'), '=>', canenc

# Needed for 2016 GBIF, but not for later or earlier ones

def canonical_name(scientific_name):
    print '** NYI'
    return 'uluz'

if __name__ == '__main__':
    project_gbif(sys.argv[1], sys.argv[2])
