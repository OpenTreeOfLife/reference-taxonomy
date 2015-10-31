from org.opentreeoflife.taxa import Taxonomy
import sys

source = sys.argv[1]    # Name of directory containing original taxonomy (must end in /)
name = sys.argv[2]      # Name of taxon to extract
dest = sys.argv[3]      # Directory to store result (must end in /)

Taxonomy.getTaxonomy(source).select(name).dump(dest)
