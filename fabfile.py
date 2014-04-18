import os
from fabric.api import *

@task
def clean():
    for c in (
        'rm -rf tax/ncbi',
        'rm -rf tax/gbif',
        'rm -rf tax/silva',
        'rm -rf tax/irmng'):
        local(c)
    for d in 'ncbi gbif silva irmng'.split():
        local('rm -rf tax/{}'.format(d))
        local('rm feed/{}/in/*'.format(d))

@task
def fetch26():
    s = """
    http://files.opentreeoflife.org/ott/2.6-inputs/feed/gbif/in/checklist1.zip
    http://files.opentreeoflife.org/ott/2.6-inputs/feed/irmng/in/IRMNG_DWC.zip
    http://files.opentreeoflife.org/ott/2.6-inputs/feed/silva/in/silva.fasta.tgz
    http://files.opentreeoflife.org/ott/2.6-inputs/feed/ncbi/taxdump.tar.gz
    http://files.opentreeoflife.org/ott/2.6-inputs/tax/if/synonyms.tsv
    http://files.opentreeoflife.org/ott/2.6-inputs/tax/if/taxonomy.tsv"""
    for f in s.split():
        x = f.replace('http://files.opentreeoflife.org/ott/2.6-inputs/','')
        d,n = os.path.split(x)
        c = 'wget -P ./{} {} && touch ./{}/{}'.format(d,f,d,n)
        local(c)
