#!/usr/bin/env python

import sys
from opentreetesting import test_http_json_method, config

DOMAIN = config('host', 'apihost')
SUBMIT_URI = DOMAIN + '/v2/conflict/compare?tree1=pg_715%23tree1289&tree2=synth'
TEST_NODE = u'node436069'
WANT_WITNESS = u'mrcaott374954ott448619'  #hoping this will be stable

# The study is chosen because if its small size.  The tree has only 7 OTUs.
test, result = test_http_json_method(SUBMIT_URI, "GET",
                                     expected_status=200,
                                     return_bool_data=True)
if not test:
    sys.exit(1)
if not TEST_NODE in result:
    sys.stderr.write('{} not in result {}\n'.format(TEST_NODE, result))
    sys.exit(1)
art = result[TEST_NODE]
if art.get(u'status') != u'conflicts_with':
    sys.stderr.write('{} relation is not "resolves": {}\n'.format(TEST_NODE, art))
    sys.exit(1)
if art.get(u'witness') != WANT_WITNESS:
    sys.stderr.write('{} witness is {}; expected {}\n'.format(TEST_NODE, art.get(u'witness'), WANT_WITNESS))
    sys.exit(1)
