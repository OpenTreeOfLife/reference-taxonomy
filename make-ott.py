# Called from Makefile

import sys
import assemble_ott

version = sys.argv[1]
config = sys.argv[2]
ott_path = sys.argv[3]

ott = assemble_ott.create_ott(version, config, ott_path)

assemble_ott.report(ott)
print '-- Done'

