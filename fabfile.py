import os
from fabric.api import *

@task
def clean():
    for c in (
        'rm -rf tax/ncbi',
        'rm -rf tax/gbif',
        'rm -rf tax/if',
        'rm -rf tax/silva',
        'rm -rf tax/irmng'):
        local(c)
    for d in 'ncbi gbif silva irmng if'.split():
        local('rm -rf tax/{}'.format(d))
        local('rm -f feed/{}/in/*'.format(d))

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
        c = 'wget -P ./{} {}'.format(d,f)
        local(c)
@task
def unpack26():
    with lcd('feed/gbif/in'):
        local('unzip checklist1.zip || true')
    with lcd('feed/irmng/in'):
        local('unzip IRMNG_DWC.zip || true')
    with lcd('feed/silva/in'):
        local('tar xzvf silva.fasta.tgz && mv *silva.fasta silva.fasta')
    local(('mkdir feed/ncbi/in && '
           'tar -C feed/ncbi/in -xzvf feed/ncbi/taxdump.tar.gz && '
           'touch feed/ncbi/in/nodes.dmp'))
    local('touch tax/if/synonyms.tsv')
    local('touch tax/if/taxonomy.tsv')
    
