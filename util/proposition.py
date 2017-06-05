# Propositions about taxonomies - for use as a 'patch language'

from org.opentreeoflife.taxa import Rank
import sys

# A different false value, used here to mean "I don't know"
Dunno = None

def proclaim(tax, prop):        # called make_claim in claim.py
    attitude1 = prop.proclaim(tax, True)
    if attitude1:
        attitude2 = prop.check(tax, True)
        if not attitude2:
            print >>sys.stderr, \
                  ('** %s seemed to enact %s, but we couldn\'t confirm (%s)' %
                   (tax.getTag(), prop.stringify(), attitude2))
    else:
        # errors have already been reported
        attitude2 = prop.check(tax, False)
        if attitude2:
            print >>sys.stderr, \
                  ('** %s seemed to fail (%s %s), but we couldn\'t confirm' %
                   (tax.getTag(), prop.stringify(), attitude1))
    return attitude2

def taxon(name=None, ancestor=None, descendant=None, id=None):
    return _Designator(name, ancestor, descendant, id)

class _Designator:
    def __init__(self, name, ancestor, descendant, id):
        self.name = name
        self.ancestor = ancestor
        self.descendant = descendant
        self.id = id
    def resolve_in(self, taxonomy, windy):
        if self.id != None:
            result = taxonomy.lookupId(self.id)
            if result == None and windy:
                print >>sys.stderr, \
                      '** id does not resolve' % self.id
        else:
            result = taxonomy.taxon(self.name, self.ancestor, self.descendant, windy)
        return result
    def stringify(self):
        return ("taxon('%s'%s%s%s)" %
                (self.name,
                 (", '%s'" % self.ancestor) if (self.ancestor != None) else '',
                 (", descendant='%s'" % self.descendant) if (self.descendant != None) else '',
                 (", id=%s" % self.id) if (self.id != None) else ''
             ))

# Propositions also want to have:
#   URL - of a document justifying the claim.
#   issue URL - where the problem was raised.
#   note - information that's currently only in comments in the 
#     taxonomy sources (curation/amendments.py and so on).

def has_parent(child, parent, qid):
    return _Has_parent(child, parent, qid)

class _Has_parent:
    def __init__(self, child, parent, qid):
        self.child = child
        self.parent = parent
        self.qid = qid
    def check(self, tax, windy):
        c = self.child.resolve_in(tax, windy)
        p = self.parent.resolve_in(tax, windy)
        if c == None:
            return Dunno
        if p == None:
            return Dunno
        elif c.parent == p:
            return True
        else:
            # if windy: ...
            # could report on different cases
            return False
    def proclaim(self, tax, windy):
        c = self.child.resolve_in(tax, windy)
        p = self.parent.resolve_in(tax, windy)
        if c == None:
            return Dunno
        if p == None:
            return Dunno
        if p.descendsFrom(c):
            if windy:
                print ('Cannot make %s a child of %p because %c is one of its ancestors' %
                       (p.name, c.name))
            return False
        elif c.parent == p:
            return True
        else:
            if c.descendsFrom(p) and windy:
                print ('* Losing information by moving %s to shallower location (%s)' %
                       (c.name, p.name))
            c.changeParent(p)
            return True
    def stringify(self):
        return ('has_parent(%s, %s, %s)' %
                (self.child.stringify(),
                 self.parent.stringify(),
                 self.qid))

# This is mainly for objective synonyms. proparte and subjective don't really work.
# Still pretty ugly, but should work for current OTT purposes.

def synonym_of(syn, pri, kind, qid):
    return _Synonym_of(syn, pri, kind, qid)

class _Synonym_of:
    def __init__(self, syn, pri, kind, qid):
        if syn == None: print >>sys.stderr, '** Missing synonym taxon', qid
        if pri == None: print >>sys.stderr, '** Missing primary taxon', qid
        self.synonym = syn
        self.primary = pri
        self.kind = kind
        self.qid = qid
    def check(self, tax, windy):
        s = self.synonym.resolve_in(tax, windy)
        p = self.primary.resolve_in(tax, windy)
        if p == None:
            return Dunno
        if s == None:
            return Dunno
        elif p.name == self.synonym.name:
            if windy:
                print >>sys.stderr, \
                      ('** Warning: %s is primary, not synonym, of %s' %
                       (self.synonym.name, p))
            return False
        elif p != s:
            if windy:
                print >>sys.stderr, '** no synonymy'
            return False
        else: 
            return True
    def proclaim(self, tax, windy):
        s = self.synonym.resolve_in(tax, False)
        p = self.primary.resolve_in(tax, False)
        # OK this is tricky.  Nodes p and s have have their own names,
        # as do the designators self.primary and self.synonym.
        # The primary objective is for node p to have self.synonym as a synonym.
        if p == None:
            if s == None:
                if windy:
                    print >>sys.stderr, \
                          ('** Cannot make %s a synonym of %s - neither resolves in %s' %
                           (self.synonym.name, self.primary.name, tax.getTag()))
                return Dunno
            else:
                s.rename(self.primary.name)
                return True
        else:
            # Figure out what name should not be primary
            synname = self.synonym.name
            if synname == None and s != None:
                synname = s.name
            if synname == None:
                print >>sys.stderr, '** no synonym name supplied'

            # We don't care what p's name is, so long as it isn't
            # synonym.name.  First rule out that case.
            if p.name == synname:
                # Name in synonym taxon must not be primary
                p.rename(self.primary.name)

            # Now do the deed
            if s != None:
                p.absorb(s, self.kind, self.qid)
                # after which s has been pruned - do not reference
            else:
                p.synonym(synname, self.kind, self.qid)
            return True
    def stringify(self):
        return ('synonym_of(%s, %s, %s, %s)' %
                (self.synonym.stringify(),
                 self.primary.stringify(),
                 self.kind,
                 self.qid))


# Sort of like synonym, but for id aliases.
# I don't know whether this is a good idea, or even useful.

def alias_of(syn, pri, qid):
    return _Alias_of(syn, pri, qid)

class _Alias_of:
    def __init__(self, syn, pri, qid):
        self.primary = pri
        self.alias = syn
        self.qid = qid
    def check(self, tax, windy):
        p = self.primary.resolve_in(tax, windy)
        s = self.alias.resolve_in(tax, windy)
        if p == None:
            return False
        if s == None:
            return False
        elif p != s:
            if windy:
                print >>sys.stderr, '** no alias'
            return False
        elif p.id != self.primary.id:
            if windy:
                print >>sys.stderr, '** id of %s is not %s' % (p, self.primary.id)
            return False
        else: 
            return True
    def proclaim(self, tax, windy):
        p = self.primary.resolve_in(tax, False)
        s = self.alias.resolve_in(tax, False)
        if p != None and s != None:
            if p != s:
                p.absorb(s)
                p.synonym(s.name, 'alias', self.qid)
                p.addId(s.id)
                for syn in s.getSynonyms():
                    # Not quite right!  Need a LUB for alias types
                    p.synonym(s.name, s.type)
            return True
        elif p != None:
            p.addId(self.alias.id)
            return True
        elif s != None:
            s.setId(self.primary.id)
            return True
        else:
            if windy:
                print >>sys.stderr, \
                      ('** Cannot claim %s alias of %s - neither resolves in %s' %
                       (self.alias.name, self.primary.name, tax.getTag()))
            return False
    def stringify(self):
        return ('alias_of(%s, %s, %s, %s)' %
                (self.alias.stringify(),
                 self.primary.stringify(),
                 self.qid))

def is_extinct(taxon, qid):
    return _Is_extinct(taxon, qid)

class _Is_extinct:
    def __init__(self, taxon, qid):
        self.taxon = taxon
        self.qid = qid
    def check(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            return not t.isExtant()
    def proclaim(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            t.extinct()
            return True
    def stringify(self):
        return ('is_extinct(%s, %s)' %
                self.taxon.stringify(),
                self.qid)

def is_extant(taxon, qid):
    return _Is_extant(taxon, qid)

class _Is_extant:
    def __init__(self, taxon, qid):
        self.taxon = taxon
        self.qid = qid
    def check(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            return t.isExtant()
    def proclaim(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            t.extant()
            return True
    def stringify(self):
        return ('is_extant(%s, %s)' %
                self.taxon.stringify(),
                self.qid)

def has_rank(taxon, rank, qid):
    return _Has_rank(taxon, rank, qid)

class _Has_rank:
    def __init__(self, taxon, rank, qid):
        self.taxon = taxon
        self.rank = Rank.getRank(rank)
        self.qid = qid
        if self.rank == None:
            print >>sys.stderr, '** Unrecognized rank:', rank
    def check(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            if t.rank != self.rank:
                if windy:
                    print >>sys.stderr, '** Wrong rank'
                return False
            else:
                return True
    def proclaim(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            if self.rank == None:
                return False
            else:
                t.rank = self.rank
                return True
    def stringify(self):
        return ('has_rank(%s, %s)' %
                self.taxon.stringify(),
                self.qid)

# Open Tree curation.
# id is an integer unique to the particular call to otc().
# issue is a URL such as https://github.com/OpenTreeOfLife/feedback/issues/45
# evidence is a URL such as http://dx.doi.org/10.1002/fedr.19971080106
# For now these are ignored.  But it should be possible to fix this later.

_ids_used = {}

def otc(id, issue=None, evidence=None):
    if id in _ids_used:
        print 'curation id %s already in use' % id
    _ids_used[id] = True
    return 'otc:' + str(id)


# Replacement for claim.py

# Reasons why claim.py didn't work out:
#
#  - The 'whether' business never worked.  Negation is way too messy and 
#    we need to get rid of it somehow.
#
#  - The 'reason' business never really worked.  We need to know not
#    only that the proposition is part of the Open Tree curation
#    process, but where it is in the source file, for some future
#    localization feature.
