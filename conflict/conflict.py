# Some class and method names borrowed from peyotl/nexson_proxy.py


import sys, os, json, argparse, csv
from org.opentreeoflife.taxa import Taxonomy, Nexson, Flag
from org.opentreeoflife.conflict import ConflictAnalysis

repo_dir = '../..'              # directory containing repo clones

default_shard = os.path.join(repo_dir, 'phylesystem-1')

# Report generation

# Report on every preferred tree in each of the studies

def report_on_studies(study_ids, shard, refs, format, outfile):
    out = sys.stderr
    if outfile != '-': out = open(outfile, 'w')
    writer = start_report(format, refs, out)
    for study_id in study_ids:
        study = gobble_study(study_id, shard)
        if study.nexson.get(u'^ot:notIntendedForSynthesis') != True:
            for tree in tree_iter_nexson_proxy(study):
                if is_preferred(tree.tree_id, study):
                    report_for_tree(tree, study, refs, format, writer)
    if outfile != '-': out.close()

def report_on_trees(treespecs, shard, refs, format, outfile):
    out = sys.stderr
    if outfile != '-': out = open(outfile, 'w')
    writer = start_report(format, refs, out)

    # There might be multiple trees per study, so it should help to cache.
    cached_studies = {}
    counts = tree_counts(treespecs)

    for (study_id, tree_id) in treespecs:
        if study_id in cached_studies:
            study = cached_studies[study_id]
        else:
            study = gobble_study(study_id, shard)
            cached_studies[study_id] = study
        report_for_tree(study.get_tree(tree_id), study, refs, format, writer)
        counts[study_id] -= 1
        if counts[study_id] == 0:
            del cached_studies[study_id] # GC
    if outfile == '-': out.close()

def tree_counts(treespecs):
    counts = {}
    for (study_id, tree_id) in treespecs:
        if study_id in counts:
            counts[study_id] += 1
        else:
            counts[study_id] = 1
    return counts

def start_report(format, refs, out):
    writer = csv.writer(out)
    if format == 'tree':
        writer.writerow(tree_report_header(refs))
    elif format == 'conflict':
        writer.writerow(conflict_report_header)
    return writer

def report_for_tree(tree, study, refs, format, writer):
    if format == 'conflict':
        report_conflicts(tree, study, refs, writer)
    elif format == 'tree':
        report_on_tree(tree, study, refs, writer)
    else:
        print '** unrecognized format', format


# Write one row for each conflict between the given tree and
# taxonomy/synthesis.

conflict_report_header = ['study', 'tree', 'ref', 'node', 'count', 'bounce', 'bcount', 'outlier', 'inlier', 'node height', 'outlier height']

def report_conflicts(tree, study, refs, writer):
    input = import_tree(tree)

    # One row per conflict
    print ('\n%s %s %s preferred: %s in synthesis: %s' %
           (study.id, tree.tree_id, study.get(u'^ot:studyYear'),
            preferred_status(tree.tree_id, study), synthesis_status(tree.tree_id, study)))
    for ref in refs:
        analysis = ConflictAnalysis(input, ref, tree.ingroup())
        skew = compute_skew(study, analysis)
        print ("\n%s nodes, %s tips, %s mapped, %s mapped from input to ref, %s from ref to input, skew %s" %
               (input.count(), input.tipCount(), analysis.countOtus(analysis.ingroup),
                analysis.map.size(), analysis.comap.size(), skew))
        print ("%s conflicts out of %s opportunities (%.2f)" %
               (analysis.conflicting, analysis.opportunities, analysis.conflictivity()))
        i = 0
        for conflict in analysis.conflicts:
            writer.writerow([study.id, tree.tree_id, ref.getTag(),
                             cobble_name(conflict.node), conflict.node.count(),
                             cobble_name(conflict.bounce), conflict.bounce.count(),
                             cobble_name(conflict.outlier), cobble_name(conflict.inlier),
                             ConflictAnalysis.distance(conflict.node, conflict.bounce),
                             ConflictAnalysis.distance(conflict.outlier, conflict.bounce)])
            i += 1
            if i >= 20:
                writer.writerow(['...'])
                break

# Write one row using the given csv writer summarizing how the given
# tree conflicts with taxonomy and/or synthesis.

tree_report_header_1 = ['study', 'tree', 'year', 'type', 'candidate?', 'in synth?', 'tips']
tree_report_header_2 = ['induced', 'unmapped', 'conflicted', 'awfulness', 'skew']

def tree_report_header(refs):
    row = tree_report_header_1
    for ref in refs:
        row = row + tree_report_header_2
    return row

def report_on_tree(tree, study, refs, writer):
    input = import_tree(tree)

    # One row per tree
    ctype = tree.nexson_tree[u'^ot:curatedType']
    row = [study.id,
           tree.tree_id,
           study.get(u'^ot:studyYear'),
           ctype.encode("utf-8"),
           preferred_status(tree.tree_id, study),
           synthesis_status(tree.tree_id, study),
           # focal, # = focal_clade(study, analysis) or ''
           input.tipCount()]
    for ref in refs:
        analysis = ConflictAnalysis(input, ref, tree.ingroup())
        skew = compute_skew(study, analysis)
        row = row + [cobble_name(analysis.inducedIngroup),
                     '%.2f' % analysis.unmappedness(),
                     '%.2f' % analysis.conflictivity(),
                     analysis.awfulness(),
                     skew]
    writer.writerow(row)

# Display utilities for reporting

def cobble_name(node):
    if node == None: return ''
    if node.name != None:
        if node.isHidden():
            return '*' + node.name
        else:
            return node.name
    elif True:
        while node.name == None:
            node = node.parent
        return '(%s)' % node.name
    else:
        left = node
        while left.name == None:
            left = left.children[0]
        right = node
        while right.name == None:
            right = right.children[-1]
        return 'mrca(%s,%s)' % (left.id, right.id)

def synthesis_status(tree_id, study):
    if in_synthesis(study.id, tree_id):
        return 'yes'
    else:
        return 'no'

def preferred_status(tree_id, study):
    if len(study.nexson_trees) == 1:
        return 'yes'
    elif len(study.preferred_trees) > 0:
        if tree_id in study.preferred_trees:
            return 'yes'
        else:
            return 'no'
    else:
        return 'unknown'

def is_preferred(tree_id, study):
    return preferred_status(tree_id, study) != 'no'

# Information of interest in reports

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
    return 'nfc'

# Proxy object for study file in nexson format

class NexsonProxy(object):
    def __init__(self, filepath):
        self.filepath = filepath # peyotl name
        self.nexson = None
        self.reftax_otus = {}
        self.nexson_trees = {}         # tree_id -> blob
        self.preferred_trees = []
        self._tree_proxy_cache = {}

        self.nexson = Nexson.load(self.filepath)
        self._nexml_element = self.nexson[u'nexml'] # peyotl name
        self.reftax_otus = Nexson.getOtus(self.nexson) # sort of wrong
        z = self.get(u'^ot:candidateTreeForSynthesis')
        if z != None: self.preferred_trees = z
        self.id = self.get(u'^ot:studyId')

    def get(self, attribute):
        if attribute in self.nexson[u'nexml']:
            return self._nexml_element[attribute]
        else:
            return None

    # cf. peyotl _create_tree_proxy (does not always create)
    def _get_tree_proxy(self, tree_id, tree, otus_id):
        tp = self._tree_proxy_cache.get(tree_id)
        if tp is None:
            np = NexsonTreeProxy(tree, tree_id, otus_id, self)
            self._tree_proxy_cache[tree_id] = np
        return np

    def get_tree(self, tree_id):
        np = self._tree_proxy_cache.get(tree_id)
        if np is not None:
            return np
        tgd = self._nexml_element[u'treesById']
        for tg in tgd.values():
            tbid = tg[u'treeById']
            if tree_id in tbid:
                otus_id = tg[u'@otus']
                nexson_tree = tbid[tree_id]
                return self._get_tree_proxy(tree_id=tree_id, tree=nexson_tree, otus_id=otus_id)
        return None

def tree_iter_nexson_proxy(nexson_proxy): # peyotl
    '''Iterates over NexsonTreeProxy objects in order determined by the nexson blob'''
    nexml_el = nexson_proxy._nexml_element
    tg_order = nexml_el['^ot:treesElementOrder']
    tgd = nexml_el['treesById']
    for tg_id in tg_order:
        tg = tgd[tg_id]
        tree_order = tg['^ot:treeElementOrder']
        tbid = tg['treeById']
        otus_id = tg['@otus']
        for k in tree_order:
            v = tbid[k]
            yield nexson_proxy._get_tree_proxy(tree_id=k, tree=v, otus_id=otus_id)

class NexsonTreeProxy(object):
    def __init__(self, nexson_tree, tree_id, otus_id, nexson_proxy):
        self.nexson_tree = nexson_tree
        self.nexson_otus_id = otus_id
        self.tree_id = tree_id
        self._nexson_proxy = nexson_proxy
        by_id = nexson_proxy._nexml_element[u'otusById']
        if otus_id not in by_id:
            print '** otus id not found', nexson_proxy.id, tree_id, otus_id, by_id.keys()
        self._otus = by_id[otus_id][u'otuById']
    def ingroup(self):
        ingroup = self.nexson_tree.get(u'^ot:inGroupClade')
        if ingroup == '':
            return None
        else:
            return ingroup

# Marshalling arguments

# shard is the path to the root of the repository (or shard) clone

def study_id_to_path(study_id, shard=default_shard):
    (prefix, number) = study_id.split('_', 1)
    if len(number) == 1:
        residue = '_' + number
    else:
        residue = number[-2:]
    return os.path.join(shard, 'study', prefix + '_' + residue, study_id, study_id + '.json')

# Load a study

def gobble_study(study_id, phylesystem):
    filepath = study_id_to_path(study_id, phylesystem)
    # should do try/catch for file-not-found
    if not os.path.exists(filepath):
        # foo, should be using try/catch
        print '** Not found:', self.filepath
        return None
    return NexsonProxy(filepath)

# See if any OTUs map to OTT ids.  There have to be at least 3

def check_study(study):
    if study != None:
        if study.nexson.get(u'^ot:notIntendedForSynthesis') == True:
            return False
        otus = study.reftax_otus
        count = 0
        for otu in otus.values():
            if otu.get(u'^ot:ottId') != None:
                count += 1
        return count >= 3
    else:
        return False

# Set names of internal nodes from taxonomy, and print newick to a file.
# This doesn't really belong in this file.

def add_names(study_id, tree_id, shard, ref, outfile):
    study = gobble_study(study_id, shard)
    tree = study.get_tree(tree_id)
    input = import_tree(tree)
    analysis = ConflictAnalysis(input, ref, tree.ingroup())
    newick = analysis.setNames().toNewick(False)

    out = sys.stdout
    if outfile != '-': out = open(outfile, 'w')
    out.write(newick)
    out.write('\n')
    if outfile == '-': out.close()

def import_tree(tree):
    return Nexson.importTree(tree.nexson_tree, tree._nexson_proxy.reftax_otus, tree.tree_id)

# Study/tree ids in a collection

def collection_treespecs(path):
    with open(path, 'r') as infile:
        collection_json = json.load(infile)
        return map(lambda coll: (coll[u'studyID'], coll[u'treeID']),
                   collection_json[u'decisions'])

synthesis_treespecs = []        # rank order
trees_in_synthesis = {}

def read_synthesis_collections():
    if len(synthesis_treespecs) > 0: return
    for collection_name in ['fungi',
                            'safe-microbes',
                            'metazoa',
                            'plants']:
        path = os.path.join(repo_dir, 'collections-1/collections-by-owner/opentreeoflife', collection_name + '.json')
        print 'reading', path
        for treespec in collection_treespecs(path):
            synthesis_treespecs.append(treespec)
            trees_in_synthesis[treespec] = True

def in_synthesis(study_id, tree_id):
    if len(trees_in_synthesis) == 0:
        read_synthesis_collections()
    treespec = (study_id, tree_id)
    if treespec in trees_in_synthesis:
        return trees_in_synthesis[treespec]
    else:
        return False

# Utilities associated with obtaining study and tree lists for reporting

# All study ids within a given phylesystem (shard)

def all_study_ids(shard):
    ids = []
    top = os.path.join(shard, 'study')
    for dir in os.listdir(top):
        for study_dir in os.listdir(os.path.join(top, dir)):
            ids.append(study_dir)
    return ids

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

def get_refs(paths, default):
    if paths == None: paths = [default]
    return map(get_taxonomy, paths)

def get_taxonomy(path):
    return Taxonomy.getTaxonomy(path, path.split('/')[-2])

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
                           default='conflict',
                           help='output format, long (many lines per tree) or short (one line per tree)')
    args = argparser.parse_args()

    if args.command == 'small':
        # Smaller test: eight trees (list aboved) vs. asterales
        refs = get_refs(args.refs, '../registry/aster-ott29/')
        report_on_trees(asterales_treespecs, args.shard, refs, args.format, args.outfile)

    elif args.command == 'larger':
        # Medium test: all asterales studies vs. plants
        refs = get_refs(args.refs, '../registry/plants-ott29/')
        # Get study list from asterales, but the studies themselves from phylesystem-1
        report_on_studies(all_study_ids(os.path.join(repo_dir, 'asterales-phylesystem')), args.shard, refs, args.format, args.outfile)

    elif args.command == 'synthesis':
        # Larger test: all synthesis trees vs. OTT
        refs = get_refs(args.refs, '../registry/ott2.9/')
        read_synthesis_collections()
        report_on_trees(synthesis_treespecs, args.shard, refs, args.format, args.outfile)

    elif args.command == 'all':
        # The whole kaboodle: all phylesystem studies vs. OTT
        refs = get_refs(args.refs, '../registry/ott2.9/')
        report_on_studies(all_study_ids(args.shard), args.shard,
                          refs, # synthetic tree is a newick...
                          args.format, args.outfile)

    elif args.command == 'tree':
        # Report on a single tree
        refs = get_refs(args.refs, '../registry/aster-ott29/')
        report_on_trees([(args.study_id, args.tree_id)], args.shard, refs, args.format, args.outfile)

    elif args.command == 'names':
        refs = get_refs(refs, '../registry/aster-ott29/')
        add_names(args.study_id, args.tree_id, args.shard, refs[0], args.outfile)

    else:
        print 'unrecognized command', args.command


#   /Users/jar/a/ot/repo/phylesystem-1/study/ot_31/ot_31/ot_31.json
#          ls -l ../repo/phylesystem-1/study/ot_31/ot_31/ot_31.json 


# look at u'^ot:candidateTreeForSynthesis' = list of tree ids
# look at list of trees in synthesis (from collections)
