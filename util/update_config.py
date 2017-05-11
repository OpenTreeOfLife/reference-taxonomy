#!/bin/python

# A filter that converts the configuration file 'config.json' to a
# 'make' format file 'config.mk' for inclusion by the main Makefile.

# Input (stdin): config.json 
# Output (stdout): config.mk

# Side effect: 
#  - update config.json, if a field update was specified


import sys, os, json, argparse, re

root = 'r'

def convert_config(blob):

    # Input might be a properties file
    if "sources" in blob:
        blob = blob["sources"]

    serieses = sorted(blob.keys())
    versions = map(lambda series: blob[series], serieses)

    if False:
        # Set individual 'make' variables ...
        for series in serieses:
            v = blob[series]
            print '%s=%s' % (series.upper(), v)

    # Lists
    for series in serieses:
        v = blob[series]
        print (('refresh/%s: r/%s-NEW/source/.made\n' +
                '\tbin/christen %s-NEW') %
               (series, series, series))
        print (('r/%s-HEAD/source/.made: r/%s-PREVIOUS/source/.made\n' +
                '\tbin/set-head %s %s-PREVIOUS easy') %
               (series, series, series, series))
        print (('r/%s-PREVIOUS/source/.made: r/%s-PREVIOUS/.issue\n' +
                '\tbin/unpack-archive %s-PREVIOUS') %
               (series, series, series))
        print (('r/%s-PREVIOUS/.issue:\n' +
                '\tbin/set-previous %s %s') %
               (series, series, v))
        print

    # For 'make fetch-all' which is redundant
    print ('UNPACKS=%s' % 
           ' '.join(map((lambda series: 'r/%s-HEAD/source/.made' % series),
                        serieses)))

    serieses.remove("ott")
    # For 'make ott'
    print ('RESOURCES=%s' % 
           ' '.join(map((lambda series: os.path.join(root, series + '-HEAD', 
                                                     'resource', '.made')),
                        serieses)))

    for series in serieses:
        print '%s: r/%s-HEAD/resource/.made' % (series, series)

stem_re = re.compile('^[^0-9]*')

parser = argparse.ArgumentParser(description='Config tool')
parser.add_argument('resource', nargs='?', default=None)
parser.add_argument('version', nargs='?', default=None)
args = parser.parse_args()

blob = json.load(sys.stdin)

convert_config(blob)
