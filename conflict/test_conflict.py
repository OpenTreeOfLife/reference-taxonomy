# Test the conflict service(s)

# Another test:
# Start the server with this script, then exercise it using curl
# e.g. 
# curl -v "http://localhost:8081/conflict-status?tree1=pg_2539%23tree6294&tree2=ott"

from org.opentreeoflife.server import Services
from org.opentreeoflife.taxa import Nexson, Taxonomy
from org.opentreeoflife.conflict import ConflictAnalysis

def prep(newick):
    tax = Taxonomy.getTaxonomy(newick, 'test')
    for node in tax:
        if node.id == None:
            node.setId(composeName(node))
    return tax

def composeName(node):
    if node.name != None:
        return node.name
    if node.children != None:
        return ''.join([composeName(child) for child in node.children])
    return 'loser'

def test_two_trees(n1, n2):
    print n1, n2
    t1 = prep(n1)
    t2 = prep(n2)
    status = Services.conflictStatus(t1, t2)
    if status != None:
        print 'got status', len(status)
        print status

def tree_tests():
    test_two_trees('((a,b),c)', '(a,(b,c))')
    test_two_trees('(a,b,c)', '(a,(b,c))')
    test_two_trees('(a,(b,c))', '(a,b,c)')

def service_test():
    tax = Taxonomy.getTaxonomy('../registry/aster-ott29/', 'ott')
    tree = Taxonomy.getTaxonomy('../registry/aster-synth4/', 'ott')

    study_id = 'pg_2539'
    tree_id = 'tree6294'

    services = Services(tax, None)

    study = services.getStudy(study_id, True)
    if study == None: 
        print 'no study', study_id
    else:
        print 'got study', len(study)

    trees = Nexson.getTrees(study)
    print 'got tree list', len(trees), trees.keys()

    tree = services.getSourceTree(study_id, tree_id)
    print 'got tree', tree

    print 'count', tree.count()

    treespec = '%s#%s' % (study_id, tree_id)
    tree = services.specToTree(treespec)
    if tree == None:
        print 'no tree', treespec
    else:
        print 'got tree again', treespec, tree

    r = ConflictAnalysis(tree, tax)
    print 'got conflict'

    d = r.disposition(r.ingroup)
    print 'disposition', d

    print services.conflictStatus(treespec, 'ott')

tree_tests()
service_test()
