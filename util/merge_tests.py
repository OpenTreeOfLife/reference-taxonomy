import sys, codecs
from org.opentreeoflife.taxa import Taxonomy, Newick
from org.opentreeoflife.smasher import UnionTaxonomy

blustery = 0

tests = []

def tst(target, source, want):
    global tests
    t = Taxonomy.getRawTaxonomy(target, 'target')
    s = Taxonomy.getRawTaxonomy(source, 'source')
    u = combine(t, s, blustery)
    tests.append((t, s, u, want))

def combine(t, s, bluster):
    u = UnionTaxonomy.newTaxonomy('union')
    u.blustery = 0

    ta = u.alignment(t)
    u.align(ta)
    u.merge(ta)

    u.blustery = bluster
    sa = u.alignment(s)

    for root in s.roots():
        sa.alignTaxon(root)

    u.align(sa)
    u.merge(sa)

    u.check()
    return u

def done():
    print
    print '----------'
    redo = []
    for (t, s, u, want) in tests:
        ttree = newick(t)
        stree = newick(s)
        utree = newick(u)
        z = '?'
        if want != None:
            if utree == want or utree == want + ';':
                z = 'ok'
            else:
                z = '***WRONG*** want %s' % want;
                redo.append((t, s, u))
        print '  %s + %s = %s %s' % (ttree, stree, utree, z)
    if len(redo) > 0:
        (t, s, u) = redo[0]
        print
        print '----------'
        print 'Redoing %s + %s' % (newick(t), newick(s))
        combine(t, s, 2)     # get diagnostics
        

def write_newick(taxy):
    sys.stdout.write(newick(taxy))
    sys.stdout.write('\n')

def newick(taxy):
    return Newick.toNewick(taxy, Newick.USE_NAMES)

if True:
    tst('(a,b)c', '(b,e)c', '(a,b,e)c')
    tst('((a,b)x,(c,d)y)z',  '(a,b,c,d)z', '((a,b)x,(c,d)y)z')
    tst('(a,b,c,d)z', '((a,b)ab,c,d)z', '((a,b)ab,c,d)z')
    tst('(a,b,c,d)z', '((a,b)x,(c,d)y)z', '((a,b)x,(c,d)y)z')
    tst('((a,b)x,(c,d,e)y)z',  '(a,b,c,d)z', '((a,b)x,(c,d,e)y)z')
    tst('((a,b)x,(c,d)y)z',  '(a,b,c,d,e)z', '(e,(a,b)x,(c,d)y)z')

    tst('(a,b,c,d)z',  '((a,b)x,(c,d,e)y)z', '((a,b)x,(c,d,e)y)z')
    tst('(a,b,c,d,e)z',  '((a,b)x,(c,d)y)z', '(a,b,c,d,e)z')

if True:
    tst('(((a)b)c)d', '(((a)w)c)d', '(((a)b)c)d')

    tst('((Eo,Ey)E)z', '((Eo,Ez)E,(Eo)M)z', None)

    tst('((Wr,Wj,Wg)W)z', '((Wr,Wt)W,(Wj)N)z', None)

done()
