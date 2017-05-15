# Called from Makefile

import sys
import assemble_ott

ott_spec = sys.argv[1]
        
ott = assemble_ott.create_ott(ott_spec)

assemble_ott.report(ott)
print '-- Done'

