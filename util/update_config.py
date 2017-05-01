#!/bin/python

# Filter converting configuration file to 'make' format file for
# inclusion by main Makefile.
# Input (stdin): config.json 
# Output (stdout): config.mk

import sys, os, json, argparse

suffixes_path = 'suffix'

def convert_config(blob):
    fu_versions=[]
    ps_versions=[]
    if not os.path.isdir(suffixes_path):
        print >>sys.stderr, 'Creating', suffixes_path
        os.mkdir(suffixes_path)
    for key in sorted(blob.keys()):
        cap = blob[key]
        v = cap["version"]
        print '%s=%s' % (key.upper(), v)
        with open(os.path.join(suffixes_path, key), 'w') as outfile:
            outfile.write("%s\n" % cap["suffix"])
        # OTT is a derived object, not a source
        if key != "ott":
            fu_versions.append(v)
        if key != "prev-ott":
            ps_versions.append(v)
    print 'fetch-all: %s' % ' '.join(map((lambda v:'fetch/%s' % v), fu_versions))
    print 'unpack-all: %s' % ' '.join(map((lambda v:'unpack/%s' % v), fu_versions))
    print 'store-all: %s' % ' '.join(map((lambda v:'store/%s' % v), ps_versions))
    print 'pack-all: %s' % ' '.join(map((lambda v:'pack/%s' % v), ps_versions))
    print 'WHICH=%s' % blob["ott"]["version"][3:]
    print 'PREV_WHICH=%s' % blob["prev-ott"]["version"][3:]
    print 'AMENDMENTS_REFSPEC=%s' % blob["amendments"]["refspec"]

def write_config(blob):
    sys.stderr.write('Writing updated config.json\n')
    with open('config.json', 'w') as outfile:
        json.dump(blob, outfile, indent=1, sort_keys=True)

parser = argparse.ArgumentParser(description='Config tool')
parser.add_argument('resource', nargs='?', default=None)
parser.add_argument('version', nargs='?', default=None)
args = parser.parse_args()

blob = json.load(sys.stdin)

if args.resource != None:
    if not args.resource in blob:
        sys.stderr.write('** No such resource: %s\n' % args.resource)
        sys.exit(1)
    blob[args.resource]["version"] = args.version
    write_config(blob)

convert_config(blob)

