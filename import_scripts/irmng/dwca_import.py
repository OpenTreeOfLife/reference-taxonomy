# -*- coding: utf-8 -*-

# Import for DwC taxonomies (GBIF, IRMNG, others)

import sys
from argparse import ArgumentParser
from dwca.read import DwCAReader
from dwca.darwincore.utils import qualname

# The columns we want in outfile:
#   taxonID
#   parentNameUsageID
#   acceptedNameUsageID
#   scientificName or canonicalName
#   taxonRank
#   taxonomicStatus
#   datasetID (formerly nameAccordingTo)

#scientific_name_uri = qualname('scientificName')
#canonical_name_uri = 'http://rs.gbif.org/terms/1.0/canonicalName'

taxon_id_uri = qualname('taxonID')

column_uris = [qualname('taxonID'),
               qualname('parentNameUsageID'),
               qualname('acceptedNameUsageID'),
               qualname('scientificName'),
               qualname('taxonRank'),
               qualname('taxonomicStatus')]

def import_dwca(inpath, outpath):

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


if __name__ == '__main__':
    parser = ArgumentParser()
    parser.add_argument(
        "archive",
        help="path to Darwin Core archive.",
    )
    parser.add_argument(
        "outfile",
        help="path to output file; will be tsv format",
    )
    args = parser.parse_args()
    import_dwca(args.archive, args.outfile)
