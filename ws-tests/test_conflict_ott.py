#!/usr/bin/env python

import sys
from opentreetesting import get_obj_from_http, config

# https://devapi.opentreeoflife.org/v2/conflict/compare?tree1=pg_1700%23tree3429&tree2=ott

DOMAIN = config('host', 'apihost')
URL = DOMAIN + '/v2/conflict/compare'
STUDY = 'pg_715'
TREE = 'tree1289'
REF = 'ott'
TEST_NODE = u'node436083'
WANT_WITNESS = u'503060'

input = {'tree1': STUDY + '#' + TREE, 'tree2': 'ott'}
result = get_obj_from_http(URL, verb='GET', params=input)

if not TEST_NODE in result:
    sys.stderr.write('node %s missing from result %s' % (TEST_NODE, result))
    sys.exit(1)
art = result[TEST_NODE]
if not art[u'status'] == u'conflicts_with':
    sys.stderr.write('unexpected relation; %s' % art)
    sys.exit(1)
if not u'witness' in art:
    sys.stderr.write('missing witness; %s' % art)
    sys.exit(1)
if art[u'witness'] != WANT_WITNESS:
    sys.stderr.write('bad witness: %s, expected %s' % (art[u'witness'], WANT_WITNESS))
    sys.exit(1)
sys.exit(0)

