# jython - compare the Ruggiero taxonomy to OTT

from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.smasher import UnionTaxonomy

def doit():
    rug = Taxonomy.getTaxonomy('scratch/Ruggiero/', 'rug')
    ott = Taxonomy.getTaxonomy('tax/ott/', 'ott')
    union = UnionTaxonomy.newTaxonomy('ott')
    union.absorb(rug)
    union.absorb(ott)
    union.dump('scratch/compare_Ruggiero/', '\t')

doit()
