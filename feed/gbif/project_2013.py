# In principle the column numbers could be extracted programmatically
# from meta.xml

import sys

def project_2013_gbif(inpath, outpath):
    with open(inpath, 'r') as infile:
        with open(outpath, 'w') as outfile:
            for line in infile:
                row = line.split('\t')
                outfile.write('%s\t%s\t%s\t%s\t%s\t%s\t%s\n' %
                              (row[0], # taxonID
                               row[1], # parentNameUsageID
                               row[2], # acceptedNameUsageID
                               row[4], # canonicalName
                               row[5], # taxonRank
                               row[6], # taxonomicStatus
                               row[12], # nameAccordingTo
                               ))

project_2013_gbif(sys.argv[1], sys.argv[2])
