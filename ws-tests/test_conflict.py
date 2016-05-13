
import sys
from opentreetesting import get_obj_from_http, config

# https://devapi.opentreeoflife.org/v2/conflict/compare?tree1=ot_530%23Tr81396&tree2=synth


DOMAIN = config('host', 'apihost')
url = DOMAIN + '/v2/conflict/compare'

# ?tree1=ot_530%23Tr81396&tree2=synth

input = {'tree1': 'pg_1700#tree3429', 'tree2': 'synth'}

output = get_obj_from_http(url, verb='GET', params=input)

if not 'node672503' in output:
    print 'missing node', output
    sys.exit(1)
if not 'witness' in output['node672503']:
    print 'missing witness', output['node672503']
    sys.exit(1)
if output['node672503']['witness'] != '239671':
    print 'bad example witness', output['node672503']['witness']
    sys.exit(1)
sys.exit(0)

