#!/usr/bin/env python

import sys
from opentreetesting import test_http_json_method, config

DOMAIN = config('host', 'apihost')
SUBMIT_URI = DOMAIN + '/v2/conflict/compare?tree1=pg_2100%23tree4347&tree2=synth'
TEST_NODE = u'node794743'

# The study is chosen because if its small size.  The tree has only 7 OTUs.
test, result = test_http_json_method(SUBMIT_URI, "GET",
                                     expected_status=200,
                                     return_bool_data=True)
if not test:
    sys.exit(1)
if not TEST_NODE in result:
    sys.stderr.write('{} not in result {}\n'.format(TEST_NODE, result))
    sys.exit(1)
item = result[TEST_NODE]
if item.get(u'status') != u'resolves':
    sys.stderr.write('{} does not resolve in {}\n'.format(TEST_NODE, result))
    sys.exit(1)
if item.get(u'witness') != u'54936':
    sys.stderr.write('{} does not have witness 54936 in {}\n'.format(TEST_NODE, result))
    sys.exit(1)
