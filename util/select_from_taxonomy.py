import sys, codecs
from org.opentreeoflife.taxa import Taxonomy, Newick

source = sys.argv[1]    # Name of directory containing original taxonomy (must end in /)
name = sys.argv[2]      # Name of taxon to extract
dest = sys.argv[3]      # Directory to store result (must end in /)

if not (dest.endswith('/') or dest.endswith('.tre')):
    print >>sys.stderr, 'Invalid taxonomy destination (need / or .tre)', dest
    sys.exit(1)

selection = Taxonomy.getRawTaxonomy(source, 'foo').select(name)

if dest.endswith('.tre'):
    with codecs.open(dest, 'w', 'utf-8') as outfile:
        outfile.write(Newick.toNewick(selection, Newick.USE_NAMES_AND_IDS))
        outfile.write('\n')
else:
    selection.dump(dest)
