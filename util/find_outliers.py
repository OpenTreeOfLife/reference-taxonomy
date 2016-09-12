# For every tree in every study, display a list of those taxa, mapped
# from tips in the ingroup, that lie outside the focal clade taxon.

# Much of this code was copied from germinator/trees_report/conflict.py.
# It might be nice to have a module of shared functions...

# Arguments:
#   --treelist X    - X is pathname of file listing trees (one study@tree per line)
#   --shard X       - X is pathname of directory containing phylesystem shard
#   --taxonomy X    - X is pathname of taxonomy directory (ends with /)
#   --out X         - X is pathname of where to write the report

# Run with bin/jython (uses classes in smasher) (create with 'make bin/jython')


import sys, os, json, argparse, csv, codecs
from org.opentreeoflife.taxa import Taxonomy, Nexson, Flag

repo_dir = '..'              # directory containing repo clones

trees_in_synthesis = {}

def report_on_shard(study_tree_pairs_path, shard, taxpath, allp, outpath):
    if not allp:
        with open(study_tree_pairs_path, 'r') as infile:
            print 'reading', study_tree_pairs_path
            for line in infile:
                (study_id, tree_id) = line.strip().split('@')
                trees = trees_in_synthesis.get(study_id)
                if trees == None: trees = []
                trees.append(tree_id)
                trees_in_synthesis[study_id] = trees
    taxonomy = Taxonomy.getTaxonomy(taxpath, 'ott')
    with codecs.open(outpath, 'w', 'utf-8') as outfile:
        if allp:
            study_ids = all_study_ids(shard)
        else:
            study_ids = trees_in_synthesis.iterkeys()
        for study_id in study_ids:
            if hash(study_id) % 100 == 0:
                print study_id
            report_on_study(study_id, shard, taxonomy, allp, outfile)

def report_on_study(study_id, shard, taxonomy, allp, outfile):
    study = get_study(study_id, shard)
    if study == None: return
    focal = get_focal_taxon(study, taxonomy)
    for tree in tree_iter_nexson_proxy(study):
        if allp or tree.tree_id in trees_in_synthesis.get(study_id, []):
            report_on_tree(study, tree, focal, taxonomy, outfile)

def report_on_tree(study, tree, focal, taxonomy, outfile):
    tree_as_taxo = import_tree(tree, study)
    count = 0
    losers = []
    focal_name = focal.name if focal != None else ''
    m = None
    forcer = None
    for taxon in tree_as_taxo.taxa():
        if not taxon.hasChildren():
            ott_taxon = map_to_reference(taxon, taxonomy)
            if ott_taxon != None:
                count += 1
                if m == None:
                    m = ott_taxon
                    forcer = ott_taxon
                else:
                    m2 = m.mrca(ott_taxon)
                    if m2 != m:
                        forcer = ott_taxon
                    m = m2
                if focal != None and not ott_taxon.descendsFrom(focal):
                    # print 'loser: %s = %s not under %s' % (taxon, ott_taxon, focal)
                    losers.append(taxon)
    if m == None:
        return
    outfile.write('study %s focal %s tree %s ingroup %s mapped %s mrca %s forcer %s(%s)\n' %
                  (study.id, focal_name, tree.tree_id, tree_as_taxo.ingroupId, count, m.name, forcer.name, forcer.id))
    if len(losers) > 0:
        print '%s losers for %s@%s, mrca %s' % (len(losers), study.id, tree.tree_id, m.name)
    for loser in losers:
        ott_loser = map_to_reference(loser, taxonomy)
        outfile.write('  loser: %s not under %s, mrca %s\n' % (loser, focal_name, focal.mrca(ott_loser).name))

def map_to_reference(taxon, taxonomy):
    if taxon.sourceIds != None:
        for qid in taxon.sourceIds:
            if qid.prefix == taxonomy.idspace:
                return taxonomy.lookupId(qid.id)
    return None

warnp = False

def get_focal_taxon(study, taxonomy):
    focal = None
    focal_id = study.get(u'^ot:focalClade')
    if focal_id != None:
        focal = taxonomy.lookupId(str(focal_id))
        if focal == None:
            # too many of these for asterales
            if warnp:
                print 'focal clade id %s does not resolve for study' % (focal_id, study.id)
    if focal == None:
        focal_name = study.get(u'^ot:focalCladeOTTTaxonName')
        if focal_name != None:
            focal = taxonomy.unique(focal_name)
            if focal == None:
                if warnp:
                    print 'focal clade name %s does not resolve for study %s' % (focal_name, study.id)
    return focal

# --------------------

# Functions copied from germinator/trees_report/conflict.py

# Load a study

single_study_cache = {'id': None, 'study': None}

def get_study(study_id, shard):
    if study_id == single_study_cache['id']:
        study = single_study_cache['study']
    else:
        single_study_cache['id'] = None
        single_study_cache['study'] = None
        study = gobble_study(study_id, shard)
        if study != None:
            single_study_cache['study'] = study
            single_study_cache['id'] = study_id
    return study

def gobble_study(study_id, phylesystem):
    filepath = study_id_to_path(study_id, phylesystem)
    # should do try/catch for file-not-found
    if not os.path.exists(filepath):
        # foo, should be using try/catch
        print '** Not found:', filepath
        return None
    return NexsonProxy(filepath)

# shard is the path to the root of the repository (or shard) clone

def study_id_to_path(study_id, shard):
    (prefix, number) = study_id.split('_', 1)
    if len(number) == 1:
        residue = '_' + number
    else:
        residue = number[-2:]
    return os.path.join(shard, 'study', prefix + '_' + residue, study_id, study_id + '.json')

# Java class for parsing nexson

def import_tree(tree, study):
    return Nexson.importTree(tree.nexson_tree,
                             tree._nexson_proxy.reftax_otus,
                             '%s@%s' % (study.id, tree.tree_id))

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

# All study ids within a given phylesystem (shard)

def all_study_ids(shard):
    ids = []
    top = os.path.join(shard, 'study')
    hundreds = os.listdir(top)
    for dir in hundreds:
        if not dir.startswith('.'):
            dir2 = os.path.join(top, dir)
            if os.path.isdir(dir2):
                dirs = os.listdir(dir2)
                for study_dir in dirs:
                    dir3 = os.path.join(dir2, study_dir)
                    if os.path.isdir(dir3):
                        ids.append(study_dir)
    print len(ids), 'studies'
    return sorted(ids)

if __name__ == '__main__':
    argparser = argparse.ArgumentParser(description='Show taxa outside ingroups.')
    argparser.add_argument('--treelist', dest='treelist', default='study_tree_pairs.txt')
    argparser.add_argument('--shard', dest='shard',
                           default=os.path.join(repo_dir, 'phylesystem-1'),
                           help='root directory of repository (shard) containing nexsons')
    argparser.add_argument('--taxonomy', dest='taxonomy',
                           default='tax/ott/',
                           help='reference taxonomy')
    argparser.add_argument('--out', dest='outfile', default='check_ingroups.out')
    args = argparser.parse_args()

    # refs = get_refs(args.refs, os.path.join(registry_dir, 'plants-ott29/'))
    # refs = get_refs(args.refs, os.path.join(registry_dir, 'ott2.9/'))

    report_on_shard(args.treelist,
                    args.shard,
                    args.taxonomy,
                    False,
                    args.outfile)
