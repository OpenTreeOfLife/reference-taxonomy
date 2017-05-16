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
    # This dependency list refers to the symbolic links
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

    # For 'make store-all'
    print ('STORES=%s' %
           ' '.join(map((lambda series: os.path.join('store', series + '-HEAD')),
                        serieses)))
    # For 'make fetch-all'
    print ('UNPACKS=%s' % 
           ' '.join(map((lambda series: os.path.join(root, series + '-HEAD',
                                                     'source', '.made')),
                        serieses)))

    # For 'make ott'
    print ('RESOURCES=%s' % 
           ' '.join(map((lambda series: os.path.join(root, series + '-HEAD', 
                                                     'resource', '.made')),
                        serieses)))

    for series in serieses:
        print '%s: r/%s-HEAD/resource/.made' % (series, series)

def get_config():
    if not os.path.exists('config.json'):
        return
    props_path = 'r/ott-HEAD/properties.json'
    if os.path.exists(props_path):
        with open(props_path, 'r') as infile:
            blob = json.load(infile)
            config = blob["source"]
        with open('config.json', 'w') as outfile:
            json.dump(config, outfile, indent=1, sort_keys=True)
            outfile.write('\n')

def write_config(blob):
    sys.stderr.write('Writing updated config.json\n')
    with open('config.json', 'w') as outfile:
        json.dump(blob, outfile, indent=1, sort_keys=True)
        outfile.write('\n')

stem_re = re.compile('^[^0-9]*')

parser = argparse.ArgumentParser(description='Config tool')
parser.add_argument('resource', nargs='?', default=None)
parser.add_argument('version', nargs='?', default=None)
args = parser.parse_args()

blob = json.load(sys.stdin)

if args.resource != None:
    if not args.resource in blob:
        sys.stderr.write('** No such resource: %s\n' % args.resource)
        sys.exit(1)
    blob[args.resource] = args.version
    write_config(blob)

convert_config(blob)









# Not used
# Create properties.json file, if it doesn't already exist
def stub_properties(series, version):
    blob = {"series":series, "version":version}
    dir = os.path.join('r', version)
    prop_path = os.path.join(dir, 'properties.json')
    if not os.path.exists(prop_path):
        if not os.path.isdir(dir):
            os.makedirs(dir)
        with open(prop_path, 'w') as outfile:
            json.dump(blob, outfile, indent=2, sort_keys=True)

# Note used
# Link from foo-HEAD to foo-123, if link doesn't already exist
def set_head(series, version):
    h = os.path.join('r', series + '-HEAD')
    if not os.path.lexists(h):
        os.symlink(version, h)

# This isn't used but I can't bear to throw it away
def stem(v):
    if v.startswith('amendments'):
        # kludge due to hex digits in commit hashes
        return 'amendments'
    m = stem_re.match(v)
    if m == None:
        print >>sys.stderr, 'Cannot find stem of %s' % v
        sys.exit(1)
    s = m.group(0)
    if s.endswith('-'): s = s[0:-1]
    return s
