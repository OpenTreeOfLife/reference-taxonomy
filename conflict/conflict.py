
import sys, os, json, argparse, csv
from org.opentreeoflife.taxa import Taxonomy, Nexson
from org.opentreeoflife.conflict import ConflictAnalysis

repo_dir = '../..'              # directory containing repo clones

# Report conflicts between input tree and reference tree

def report_on_tree(tree_id, refs, study, format, writer):
    trees = study.trees
    treeson = trees[tree_id]
    input = Nexson.importTree(treeson, study.otus, tree_id)
    ctype = treeson[u'^ot:curatedType']

    cand_status = 'unknown'
    if len(study.candidate_trees_for_synthesis) > 0:
        if tree_id in study.candidate_trees_for_synthesis:
            cand_status = 'yes'
        else:
            cand_status = 'no'

    in_synth = 'no'
    if in_synthesis(study.id, tree_id):
        in_synth = 'yes'

    if format == 'long':
        print study.id, tree_id, study.get(u'^ot:studyYear'), 'candidate:', cand_status, 'in synthesis:', in_synth
    else:
        row = [study.id,
               tree_id,
               study.get(u'^ot:studyYear'),
               ctype,
               cand_status,
               in_synth,
               # focal, # = focal_clade(study, analysis) or ''
               input.tipCount()]

    for ref in refs:
        analysis = ConflictAnalysis(input, ref, ingroup(treeson)).analyze()
        skew = compute_skew(study, analysis)
        if format == 'long':
            print 'skew: %s' % skew
            analysis.printReport()
            sys.stdout.write('\n')
        else:
            name = ''
            if analysis.inducedIngroup != None:
                name = cobble_name(analysis.inducedIngroup)
            row = row + [name,
                         '%.2f' % analysis.unmappedness(),
                         '%.2f' % analysis.conflictivity(),
                         analysis.awfulness(),
                         skew]
    if format != 'long':
        writer.writerow(row)

tree_report_header_1 = ['study', 'tree', 'year', 'type', 'candidate?', 'in synth?', 'tips']
tree_report_header_2 = ['induced', 'unmapped', 'conflicted', 'awfulness', 'skew']

def ingroup(treeson):
    ingroup = treeson.get(u'^ot:inGroupClade')
    if ingroup == '':
        return None
    else:
        return ingroup

def cobble_name(node):
    if node.name != None:
        return node.name
    if True:
        while node.name == None:
            node = node.parent
        return '(' + node.name + ')'
    else:
        left = node
        while left.name == None:
            left = left.children[0]
        right = node
        while right.name == None:
            right = right.children[-1]
        return 'mrca(%s,%s)' % (left.id, right.id)

# Proxy object for nexson study

class Study:
    def __init__(self, study_id):
        self.id = study_id
        self.nexson = None
        self.otus = {}
        self.trees = {}
        self.candidate_trees_for_synthesis = []

    def get(self, attribute):
        if attribute in self.nexson[u'nexml']:
            return self.nexson[u'nexml'][attribute]
        else:
            return None

    def load(self, shard):
        if self.nexson == None:
            path = study_id_to_path(self.id, shard)
            if os.path.exists(path):
                self.nexson = Nexson.load(path)
                self.otus = Nexson.getOtus(self.nexson)
                self.trees = Nexson.getTrees(self.nexson)
                z = self.get(u'^ot:candidateTreeForSynthesis')
                if z != None: self.candidate_trees_for_synthesis = z
            else:
                print '** Not found:', path

cached_studies = {}

def get_study(study_id):
    if study_id in cached_studies:
        return cached_studies[study_id]
    else:
        study = Study(study_id)
        cached_studies[study_id] = study
        return study

# shard is the path to the root of the repository (or shard) clone

def study_id_to_path(study_id, shard):
    (prefix, number) = study_id.split('_', 1)
    if len(number) == 1:
        residue = '_' + number
    else:
        residue = number[-2:]
    return os.path.join(shard, 'study', prefix + '_' + residue, study_id, study_id + '.json')

default_shard = os.path.join(repo_dir, 'phylesystem-1')

# Load a study

def gobble_study(study_id, phylesystem):
    study = get_study(study_id)
    study.load(phylesystem)
    if study.nexson != None:
        return study
    else:
        return None

# See if any OTUs map to OTT ids.  There have to be at least 3

def check_study(study):
    if study != None:
        otus = study.otus
        trees = study.trees
        count = 0
        for otu in otus.values():
            if otu.get(u'^ot:ottId') != None:
                count += 1
        return count >= 3
    else:
        return False

# All study ids within a given phylesystem (shard)

def all_study_ids(shard):
    ids = []
    top = os.path.join(shard, 'study')
    for dir in os.listdir(top):
        for study_dir in os.listdir(os.path.join(top, dir)):
            ids.append(study_dir)
    return ids

# Study/tree ids in a collection

def study_and_tree_ids(path):
    with open(path, 'r') as infile:
        collection_json = json.load(infile)
        return map(lambda coll: (coll[u'studyID'], coll[u'treeID']),
                   collection_json[u'decisions'])

trees_in_synthesis = {}

def read_synthesis_collections():
    for collection_name in ['fungi',
                            'safe-microbes',
                            'metazoa',
                            'plants']:
        path = os.path.join(repo_dir, 'collections-1/collections-by-owner/opentreeoflife', collection_name + '.json')
        print 'reading', path
        for pair in study_and_tree_ids(path):
            trees_in_synthesis[pair] = True

def in_synthesis(study_id, tree_id):
    if len(trees_in_synthesis) == 0:
        read_synthesis_collections()
    pair = (study_id, tree_id)
    if pair in trees_in_synthesis:
        return trees_in_synthesis[pair]
    else:
        return False

def focal_clade(study, analysis):
    id = study.get('^ot:focalClade') # OTT id as integer
    if id != None:
        return analysis.ref.lookupId(str(id))
    else:
        return None

def compute_skew(study, analysis):
    if analysis.inducedIngroup == None:
        return 0
    focal = focal_clade(study, analysis)
    if focal != None:
        if analysis.inducedIngroup == focal:
            return 0
        else:
            m = analysis.inducedIngroup.mrca(focal)
            if m == focal:
                return -ConflictAnalysis.distance(analysis.inducedIngroup, focal)
            elif m == analysis.inducedIngroup:
                return ConflictAnalysis.distance(focal, analysis.inducedIngroup)
            else:
                return 'focal clade / tree conflict'
    return None

def print_header(format, refs, out):
    writer = csv.writer(out)
    if format == 'short':
        row = tree_report_header_1
        for ref in refs:
            row = row + tree_report_header_2
        writer.writerow(row)
    return writer

def report_on_studies(studies, shard, refs, format, outfile):
    out = sys.stderr
    if outfile != '-': out = open(outfile, 'w')
    writer = print_header(format, refs, out)
    for study_id in studies:
        study = gobble_study(study_id, shard)
        if check_study(study):
            candidates = study.candidate_trees_for_synthesis
            if candidates != None and len(candidates) > 0:
                for tree_id in candidates:
                    report_on_tree(tree_id, refs, study, format, writer)
            else:
                # look at "^ot:treesElementOrder" ?
                for tree_id in study.trees:
                    report_on_tree(tree_id, refs, study, format, writer)
    if outfile == '-': out.close()

def report_on_trees(treespecs, shard, refs, format, outfile):
    out = sys.stderr
    if outfile != '-': out = open(outfile, 'w')
    writer = print_header(format, refs, out)
    for (study_id, tree_id) in treespecs:
        study = gobble_study(study_id, shard)
        if check_study(study):
            report_on_tree(tree_id, refs, study, format, writer)
    if outfile == '-': out.close()

# These are some asterales studies.  List is from gcmdr repo.
asterales_treespecs=[("pg_2539", "tree6294"), # Soltis et al. 2011
               ("pg_715", "tree1289"),  # Barnadesioideae
               ("pg_329", "tree324"),   # Hieracium
        #       ("pg_9", "tree1"),       # Campanulidae
               ("pg_1944", "tree3959"), # Campanulidae; replacement of 9 above
               ("pg_709", "tree1276"),  # Lobelioideae, very non monophyletic
               ("pg_41", "tree1396"),   # Feddea
               ("pg_82", "tree5792"),   # Campanula, very non monophyletic campanula
               ("pg_932", "tree1831")   # Goodeniaceae, tons of non monophyly
               ]

# consider comparing the "^ot:focalClade" to the induced root

if __name__ == '__main__':

    argparser = argparse.ArgumentParser(description='Play around with conflict.')

    argparser.add_argument('command')
    argparser.add_argument('--study', dest='study_id', default=None)
    argparser.add_argument('--tree', dest='tree_id', default=None)
    argparser.add_argument('--outfile', dest='outfile', default='-')

    argparser.add_argument('--ref', dest='refs', nargs='+',
                           default=None,
                           help='reference tree (taxonomy or synth, smasher format, name ends with /)')
    argparser.add_argument('--shard', dest='shard',
                           default=default_shard,
                           help='root directory of repository containing nexsons')
    argparser.add_argument('--format', dest='format',
                           default='long',
                           help='output format, long (many lines per tree) or short (one line per tree)')
    args = argparser.parse_args()

    print 'refs =', args.refs

    if args.command == 'foo':
        read_synthesis_collections()
        print len(trees_in_synthesis)
        #print study_and_tree_ids(os.path.join(repo_dir, 'collections-1/collections-by-owner/opentreeoflife/fungi.json'))
    elif args.command == 'small':
        # Smaller test
        rs = args.refs
        if rs == None: rs = ['../registry/aster-ott29/']
        refs = map(lambda (r): Taxonomy.getTaxonomy(r), rs)
        #ref = Taxonomy.getTaxonomy('../registry/aster-synth4/')
        report_on_trees(asterales_treespecs, args.shard, refs, args.format, args.outfile)
    elif args.command == 'larger':
        # Bigger test
        rs = args.refs
        if rs == None: rs = ['../registry/plants-ott29/']
        refs = map(lambda (r): Taxonomy.getTaxonomy(r), rs)
        # Get study list from asterales, but the studies themselves from phylesystem-1
        report_on_studies(all_study_ids(os.path.join(repo_dir, 'asterales-phylesystem')), args.shard, refs, args.format, args.outfile)
    elif args.command == 'all':
        rs = args.refs
        if rs == None: rs = '../registry/ott2.9/'
        refs = map(lambda (r): Taxonomy.getTaxonomy(r), rs)
        report_on_studies(all_study_ids(args.shard), args.shard, refs, args.format, args.outfile)
    elif args.command == 'tree':
        # Report on a single tree
        rs = args.refs
        if rs == None: rs = '../registry/aster-ott29/'
        refs = map(lambda (r): Taxonomy.getTaxonomy(r), rs)
        report_on_trees([(args.study_id, args.tree_id)], args.shard, refs, args.format, args.outfile)
    else:
        print 'unrecognized command', args.command


#   /Users/jar/a/ot/repo/phylesystem-1/study/ot_31/ot_31/ot_31.json
#          ls -l ../repo/phylesystem-1/study/ot_31/ot_31/ot_31.json 


# look at u'^ot:candidateTreeForSynthesis' = list of tree ids
# look at list of trees in synthesis (from collections)
