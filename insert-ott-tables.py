import sys, os, re, csv, ivy
from collections import defaultdict, Counter
from itertools import imap, ifilter, izip
dump_loc = './tax/ott'
jp = lambda x:os.path.join(dump_loc, x)

def getdb():
    import sys
    paths = ('/home/rree/src/web2py', '/home/rree/src/phylografter/models')
    for p in paths:
        if p not in sys.path: sys.path.append(p)
    from gluon import DAL
    from gluon.tools import Auth
    from A_define_tables import define_tables
    user = 'phylo'; password = 'grafter'
    host = 'localhost'
    dbname = 'phylografter'
    db = DAL("mysql://%s:%s@%s/%s" % (user, password, host, dbname),
             folder='/home/rree/src/phylografter/databases',
             driver_args=dict(unix_socket='/var/run/mysqld/mysqld.sock'),
             auto_import=True)
    define_tables(db, migrate=False)
    return db

# load taxonomy.tsv into a list of rows
data = []
n = 0
split = lambda s: (
    [ x.strip() or None for x in s.split('|')][:-1] if s[-2]=='\t'
    else [ x.strip() or None for x in s.split('|')]
)
c2p = dict() # map child uid to parent uid
filt = lambda line:not line.startswith('#')
with open(jp('taxonomy.tsv')) as f:
    taxonomy_fields = split(f.readline())
    for v in imap(split, ifilter(filt, f)):
        # convert uid and parent_uid to ints
        for i in 0,1: v[i] = int(v[i] or 0)
        taxid = v[0]
        v[4] = dict([ x.split(':') for x in v[4].split(',') ])
        data.append(v)
        c2p[taxid] = v[1]
        print n, '\r',
        n += 1
    print '\ndone'

## srcs = set()
## for v in data: srcs.update(v[4].keys())

print 'deprecated'
deprecated = {} # maps old uid to None (if decommissioned) or a new uid
with open(jp('deprecated.tsv')) as f:
    f.next()
    for s in f:
        w = s.split('\t')
        k = int(w[0])
        try:
            v = int(w[5]) if (w[5] and w[5].strip() != '*') else None
            deprecated[k] = v
        except ValueError:
            print sys.exc_info()[0]
        print k, '\r',
print '\ndone'

# synonyms
print 'synonyms'
syndata = []
uid2synrows = defaultdict(list)
unames = defaultdict(list)
with open(jp('synonyms.tsv')) as f:
    synonym_fields = split(f.readline())
    for i, v in enumerate(imap(split, f)):
        syndata.append(v)
        uid = int(v[1])
        uid2synrows[uid].append(i)
        unames[v[3]].append(i)
        print i, '\r',
print '\ndone'

c = Counter()
for r in data:
    c[(r[5] or r[2]).lower()] += 1
v = [ k for k in c if c[k] > 1 ]
## for r in syndata:
##     c[r[3].lower()] += 1
## v2 = [ k for k in c if c[k] > 1 ]

def ivy_taxonomy_tree(prune_suppressed=True):
    print 'building tree'
    Node = ivy.tree.Node
    nodes = [ Node(**dict(izip(taxonomy_fields, row))) for row in data ]
    print 'nodes created'
    uid2node = dict([ (n.uid, n) for n in nodes ])
    print 'nodes indexed'
    r = nodes[0]
    r.isroot = True
    print 'branches'
    N = len(nodes)
    for n in nodes[1:]:
        p = uid2node[n.parent_uid]
        p.add_child(n)
        N -= 1
        print N, '\r',
    print '\ndone'

    suppress_labels = [
        'Deltavirus',
        'Viruses',
        'vectors',
        'Satellite Viruses',
        'Tobacco leaf curl Japan beta',
        'Viroids',
        'Prions',
        'artificial sequences',
        'organismal metagenomes',
        'eukaryotic vectors'
        ]

    print 'filtering'
    N = len(nodes)
    for n in nodes:
        n.suppress = 0
        if not n.children: n.isleaf = True
        else: n.isleaf = False
        n.label = n.uniqname or n.name
        if not n.flags: n.flags = ''
        if not n.sourceinfo: n.sourceinfo = {}
        for k in ('ncbi', 'gbif', 'irmng', 'silva'):
            setattr(n, k, n.sourceinfo.get(k) or '')
        n.ncbi = int(n.ncbi) if n.ncbi else n.ncbi
        n.gbif = int(n.gbif) if n.gbif else n.gbif
        n.irmng = int(n.irmng) if n.irmng else n.irmng
        name = n.name.lower()
        if (('viral' in n.flags) or
            ('not_otu' in n.flags) or
            ('unclassified_direct' in n.flags) or
            ('environmental' in n.flags) or
            ## ('hidden' in n.flags) or
            ## ('barren' in n.flags) or
            ## ('major_rank_conflict' in n.flags) or
            ## ('extinct' in n.flags) or
            ## ('incertae_sedis' in n.flags) or
            ## ('tattered' in n.flags) or
            ('metagenome' in name) or
            ('artificial' in name) or
            ('unallocated' in name) or
            (' phage ' in name) or
            (' vector ' in name) or
            (('environmental' in name) and ('ncbi' not in n.sourceinfo)) or
            (name.endswith(' prion') or name.endswith(' prions')) or
            (n.name in suppress_labels)):
            n.suppress = 1
        N -= 1
        print N, '\r',
    print '\ndone'

    def prune(x):
        try:
            p = x.parent
            p.children.remove(x)
            if not p.children: p.isleaf = True
        except:
            pass
        x.parent = None
        x.children = []

    for x in nodes:
        if x.suppress: prune(x)

    v = [ x for x in r if not x.isleaf and not x.children ]
    while v:
        print 'pruning', len(v)
        map(prune, v)
        v = [ x for x in r if not x.isleaf and not x.children ]
    print 'done'

    v = []
    print 'collapsing',
    for n in r:
        name = n.name.lower()
        if (name.startswith('basal ') or
            name.startswith('stem ') or
            name.startswith('early diverging ') or
            ('incertae sedis' in name and n.children and (not n.silva))):
            print 'collapsing', n.name
            v.append(n)
    for n in v: n.collapse()
    print 'done'
    assert len([ x for x in r if not x.isleaf and not x.children ])==0
    
    print 'indexing',
    ivy.tree.index(r, n=1)
    print 'done'
    return r

r = ivy_taxonomy_tree()
uid2node = dict([ (n.uid, n) for n in r ])

d = defaultdict(list)
for n in r:
    d[n.label.lower()].append(n)
d = dict([ (k,v) for k,v in d.iteritems() if len(v)>1 ])

def write_ott_node_values(r):
    with open('ott_node.csv','w') as f:
        for n in r:
            values = '\t'.join(map(str, (
                n.uid,
                n.parent.uid if n.parent else '',
                n.next,
                n.back,
                n.node_depth,
                n.name,
                n.uniqname or n.name,
                n.ncbi,
                n.gbif,
                n.irmng,
                n.silva
                )))
            f.write('{}\n'.format(values))

def write_ott_name_values(r):
    with open('ott_name.csv','w') as f:
        for n in r:
            values = '\t'.join(map(str, (
                0,
                n.uid,
                n.name,
                n.uniqname or n.name
                )))
            f.write('{}\n'.format(values))
            for i in uid2synrows[n.uid]:
                syn = syndata[i]
                values = '\t'.join(map(str, (
                    0,
                    n.uid,
                    syn[0],
                    syn[3] or syn[0]
                    )))
                f.write('{}\n'.format(values))
            
if not d:
    print 'writing'
    write_ott_node_values(r)
    write_ott_name_values(r)

db = getdb()

## inserted = [ x[0] for x in db.executesql('select id from ott_node') ]

sql = []
dep = set()
s = 'update {} set ott_node = {} where id = {}'
for tname in ('otu', 'snode'):
    t = db[tname]
    q = db((t.ottol_name==db.ottol_name.id)&(t.ottol_name!=None)&
           (db.ottol_name.accepted_uid!=None))._select(
               t.id, db.ottol_name.accepted_uid, distinct=True)
    rows = db.executesql(q)
    for recid, ottid in rows:
        ottid = int(deprecated.get(ottid) or ottid)
        if ottid in uid2node:
            sql.append(s.format(tname, ottid, int(recid)))
        else:
            dep.add(ottid)
with open('update-otu-snode.sql','w') as f:
    f.write(';\n'.join(sql))

sql = []
uid2node = dict([ (n.uid, n) for n in r ])
dep = set()
s = 'update study set focal_clade_ott = {} where id = {}'
t = db.study
q = db((t.focal_clade_ottol==db.ottol_name.id)&(t.focal_clade_ottol!=None)&
       (db.ottol_name.accepted_uid!=None))._select(
           t.id, db.ottol_name.accepted_uid, distinct=True)
rows = db.executesql(q)
for recid, ottid in rows:
    ottid = int(deprecated.get(ottid) or ottid)
    if ottid in uid2node:
        sql.append(s.format(ottid, int(recid)))
    else:
        dep.add(ottid)
with open('update-study.sql','w') as f:
    f.write(';\n'.join(sql))
    
