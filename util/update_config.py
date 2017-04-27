# Filter converting configuration file to 'make' format file for
# inclusion by main Makefile.
# Input: config.json 
# Output: config.mk

import sys, json, argparse

def convert_config(blob):
    versions=[]
    for key in sorted(blob.keys()):
        v = blob[key]["version"]
        print '%s=%s' % (key.upper(), v)
        versions.append('retrieve-' + key)
    print 'retrieve-all=%s' % ' '.join(versions)
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

