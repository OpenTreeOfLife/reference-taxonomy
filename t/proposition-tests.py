from proposition import *
from establish import *

from org.opentreeoflife.taxa import SourceTaxonomy, Taxon

taxo = SourceTaxonomy('proptest')
life = Taxon(taxo, 'life')
taxo.addRoot(life)

# establish(name, taxonomy, rank=None, parent=None, ancestor=None, division=None, ott_id=None, source=None)

establish('Anura', taxo, 'order', parent='life')
establish('Ranidae', taxo, 'order', parent='Anura')

proclaim(taxo, has_parent(taxon('Ranidae'), taxon('Anura'), 'foo:1'))

proclaim(taxo, synonym_of(taxon('Salientia'), taxon('Anura'), 'objective synonym', 'foo:2'))

proclaim(taxo, synonym_of(taxon('Anura'), taxon('Anura2'), 'objective synonym', 'foo:3'))
