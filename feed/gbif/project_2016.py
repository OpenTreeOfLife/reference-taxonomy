# -*- coding: utf-8 -*-

# In principle the column numbers could be extracted programmatically
# from meta.xml

import sys, re

def project_2016_gbif(inpath, outpath):
    with open(inpath, 'r') as infile:
        with open(outpath, 'w') as outfile:
            for line in infile:
                row = line.split('\t')
                outfile.write('%s\t%s\t%s\t%s\t%s\t%s\t%s\n' %
                              (row[1], # taxonID
                               row[3], # parentNameUsageID
                               row[4], # acceptedNameUsageID
                               trim_name(row[6]), # canonicalName / scientificName
                               row[7], # taxonRank
                               row[10], # taxonomicStatus
                               row[2], # nameAccordingTo / datasetID
                               ))


trimmer = re.compile(u"([A-Za-zäåàáãçéèëïíøöóü-]+( [a-záåäëèéïóöü'0-9.-]+)*) +(d'|von |van der )?[A-ZÄÁÅÇÐÉÎŠÔØÖÔÓÜ(].*")

def trim_name(name):
    m = trimmer.match(name)
    if m:
        return m.group(1)
    else:
        return name

project_2016_gbif(sys.argv[1], sys.argv[2])
