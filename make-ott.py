# Called from Makefile

import sys
import assemble_ott

version = sys.argv[1]

ott = assemble_ott.create_ott(version)

ott.dump('tax/ott/')
assemble_ott.report(ott)
print '-- Done'

