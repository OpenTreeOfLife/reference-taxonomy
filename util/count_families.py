# counts number of taxa with rank=family in a given taxon

from org.opentreeoflife.taxa import Taxonomy, Rank
import argparse

parser = argparse.ArgumentParser(description='load nexsons into postgres')
parser.add_argument('taxonname',
    help='name of taxon to count'
    )
args = parser.parse_args()

name = args.taxonname
ott_path = '/Users/karen/Documents/opentreeoflife/data/ott/ott2.9draft12/'
ott = Taxonomy.getTaxonomy(ott_path, 'ott')
def count_families(taxon):
    count = 0
    with open('families.txt','w') as f:
        for t in taxon.descendants(False):
            if t.rank == Rank.FAMILY_RANK:
                f.write("{n}\n".format(n=t.name))
                count += 1
    f.close()
    return count
print "number families: ",count_families(ott.taxon(name))
