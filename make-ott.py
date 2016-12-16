# Called from Makefile

import assemble_ott

ott = assemble_ott.create_ott()

ott.dump('tax/ott/')
assemble_ott.report(ott)
print '-- Done'

