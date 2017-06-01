import sys

from org.opentreeoflife.taxa import Taxonomy, Rank

ott = Taxonomy.getRawTaxonomy(sys.argv[1], 'ott')

# Look for splitting:
#   Suppose X, Y are distinct in GBIF, but both align to X in NCBI,
#   because NCBI says Y a synonym of X.
#   Then we have X in NCBI with GBIF X and Y aligning to it, and
#   Y a synonym via NCBI but not via GBIF.
#   So GBIF X is a source for X, and GBIF Y is in sources for Y-synonym of X.

for X in ott.taxa():
    # Species only
    if X.rank != Rank.SPECIES_RANK: continue

    xid = X.sourceIds[0].id

    # Look for Y, a synonym of X...
    for Y in X.getSynonyms():

        yids = [qid.id for qid in Y.sourceIds]

        # that has same source as X...
        if not xid in yids:
            continue

        # but, an alignment from Y
        for yid in yids:

            # Same genus
            if X.name.split(' ')[0] != Y.name.split(' ')[0]:
                continue

            # Different epithets
            z = max(0, min(len(X.name), len(Y.name)) - 3)
            if X.name[0:z] == Y.name[0:z]:
                continue

            if yid != xid:

                print X, Y


def foo():
    nodes = ott.lookup(taxon.name)
    if nodes != None:
        # Look for synonym Y of X
        for node in nodes:
            t = node.taxon()
            # Synonyms only
            if t == node: continue

            # From different sources
            if node.sourceIds[0].id == sources[0]:
                continue
            # This source is also a taxon source
            if not node.sourceIds[0].id in sources:
                continue
            # Synonym for some other taxon
            if t == taxon: continue
            # In same genus
            if t.parent == taxon.parent:
                # Taxon has a synonym S such that S also names a sibling of taxon
                print "%s, %s, %s" % (taxon, node, t)
