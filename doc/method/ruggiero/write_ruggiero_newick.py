import sys

from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.smasher import AlignmentByName
from org.opentreeoflife.conflict import ConflictAnalysis

rug = Taxonomy.getTaxonomy('scratch/Ruggiero/', 'rug')

with open('scratch/Ruggiero.tre', 'w') as outfile:
     outfile.write(rug.toNewick(False))
     outfile.write('\n')

