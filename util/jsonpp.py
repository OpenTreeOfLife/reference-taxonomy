#!/usr/bin/python

# Pretty-print json - a filter

import sys, json
json.dump(json.load(sys.stdin), sys.stdout, indent=2)
sys.stdout.write('\n')
