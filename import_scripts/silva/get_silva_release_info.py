# The SILVA archive directory contains many releases.
# For purposes ' refresh' we want to latest one.

# Scan the index file at
# https://www.arb-silva.de/no_cache/download/archive/
# for the last (highest numbered) release occurring in it.

import sys, requests, re, time
from dateutil.parser import parse

rel = re.compile('release_([0-9]+)')

last = more = None
for line in sys.stdin:
    pos = 0
    while True:
        m = rel.search(line, pos)
        if m == None:
            break
        all = m.group(0)
        pos += len(all)
        last = m.group(1)
        more = line[pos:]

if last == None:
    print '*** no release ***'
    sys.exit(1)

# e.g. 28-Sep-2016

dat = re.compile('[0-9][0-9]-[A-Z][a-z][a-z]-[0-9]+')

m = dat.search(more)
if m == None:
    print '*** no date ***', last
    sys.exit(1)

# parse returns a datetime.datetime object.
dtime = parse(m.group(0))

iso = dtime.date().isoformat()

print last, iso

