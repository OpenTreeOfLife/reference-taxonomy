
# Pretty-print json - a filter

import sys
import json

j = json.load(sys.stdin)
json.dump(j, sys.stdout, indent=2)
sys.stdout.write('\n')
