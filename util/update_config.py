#!/bin/python

# A filter that converts the configuration file 'config.json' to a
# 'make' format file 'config.mk' for inclusion by the main Makefile.

# Input (stdin): config.json 
# Output (stdout): config.mk

# Side effect: 
#  - create symbolic links r/foo -> r/foo-123
#  - update config.json, if a field update was specified


import sys, os, json, argparse, re

root = 'r'
# Option of putting all the links in a different directory
links_path = root

def convert_config(blob):
    serieses = sorted(blob.keys())
    versions = map(lambda series: blob[series], serieses)

    if False:
        # Set individual 'make' variables ...
        for series in serieses:
            v = blob[series]
            print '%s=%s' % (series.upper(), v)

    # Set symbolic links ...
    if not os.path.isdir(links_path):
        print >>sys.stderr, 'Creating', links_path
        os.mkdir(links_path)
    for series in serieses:
        v = blob[series]
        link = os.path.join(links_path, series + '-HEAD')
        if os.path.lexists(link):
            os.unlink(link)
        n = series + '-NEW'
        if os.path.isdir(os.path.join(root, n)):
            os.symlink(n, link)
        else:
            os.symlink(v, link)

    # Lists
    # This dependency list refers to the symbolic links
    for series in serieses:
        v = blob[series]
        print ('r/%s-HEAD/source/.made:\n\tbin/unpack-archive %s %s' %
               (series, series, v))

    print ('SOURCES=%s' % 
           ' '.join(map((lambda series: os.path.join(root, series + '-HEAD', 
                                                     'source', '.made')),
                        serieses)))
    print ('RESOURCES=%s' % 
           ' '.join(map((lambda series: os.path.join(root, series + '-HEAD', 
                                                     'resource', '.made')),
                        serieses)))
    print ('STORES=%s' %
           ' '.join(map((lambda series: os.path.join('store', series + '-NEW')),
                        serieses)))
    print 'SERIESES=%s' % ' '.join(serieses)

def write_config(blob):
    sys.stderr.write('Writing updated config.json\n')
    with open('config.json', 'w') as outfile:
        json.dump(blob, outfile, indent=1, sort_keys=True)

stem_re = re.compile('^[^0-9]*')

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

