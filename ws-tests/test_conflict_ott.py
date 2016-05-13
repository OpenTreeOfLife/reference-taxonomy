#!/usr/bin/env python

import sys
from opentreetesting import get_obj_from_http, config

# https://devapi.opentreeoflife.org/v2/conflict/compare?tree1=pg_1700%23tree3429&tree2=ott

DOMAIN = config('host', 'apihost')
URL = DOMAIN + '/v2/conflict/compare'
STUDY = 'pg_1700'
TREE = 'tree3429'
REF = 'ott'
TEST_NODE = u'node672503'

input = {'tree1': STUDY + '#' + TREE, 'tree2': 'ott'}
result = get_obj_from_http(URL, verb='GET', params=input)

if not TEST_NODE in result:
    sys.stderr.write('node %s missing from result' % result)
    sys.exit(1)
if not u'witness' in result[TEST_NODE]:
    sys.stderr.write('missing witness; %s' % result[TEST_NODE])
    sys.exit(1)
if result[TEST_NODE][u'witness'] != u'239671':
    sys.stderr.write('bad witness %s' % result[TEST_NODE]['witness'])
    sys.exit(1)
sys.exit(0)

