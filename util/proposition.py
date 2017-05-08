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

from org.opentreeoflife.taxa import Rank

def proclaim(tax, prop):        # called make_claim in claim.py
    if prop.proclaim(tax, True):
        if prop.check(tax, True):
            return True
        else:
            print ('** %s seemed to accept %s, but we couldn\'t verify' %
                   (tax.getTag(), prop.stringify()))
            return False
    else:
        # Diagnostics will already have been printed at this point
        return False

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
        else:
            result = taxonomy.taxon(self.name, self.ancestor, self.descendant, False)
        if result == None and windy:
            print '** %s does not resolve' % self.stringify()
        return result
    def stringify(self):
        return ("taxon('%s'%s%s%s)" %
                (self.name,
                 (", '%s'" % self.ancestor) if (self.ancestor != None) else '',
                 (", descendant='%s'" % self.descendant) if (self.descendant != None) else '',
                 (", id=%s" % self.id) if (self.id != None) else ''
             ))

def has_parent(child, parent, sid):
    return _Has_parent(child, parent, sid)

class _Has_parent:
    def __init__(self, child, parent, sid):
        self.child = child
        self.parent = parent
        self.source_id = sid
    def check(self, tax, windy):
        c = self.child.resolve_in(tax, windy)
        p = self.parent.resolve_in(tax, windy)
        if c == None:
            return False
        if p == None:
            return False
        else:
            return c.parent == p
    def proclaim(self, tax, windy):
        c = self.child.resolve_in(tax, windy)
        p = self.parent.resolve_in(tax, windy)
        if c == None:
            return False
        if p == None:
            return False
        if p.descendsFrom(c):
            if windy:
                print ('Cannot make %s a child of %p because %c is one of its ancestors' %
                       (p.name, c.name))
            return False
        elif c.parent == p:
            return True
        elif c.descendsFrom(p):
            c.changeParent(p)
            return True
    def stringify(self):
        return ('has_parent(%s, %s, %s, %s)' %
                self.child.stringify(),
                self.parent.stringify(),
                self.source_id)

# This is mainly for objective synonyms. proparte and subjective don't really work.

def synonym_of(syn, pri, kind, sid):
    return _Synonym_of(syn, pri, kind, sid)

class _Synonym_of:
    def __init__(self, syn, pri, kind, sid):
        self.primary = pri
        self.synonym = syn
        self.kind = kind
        self.source_id = sid
        if self.primary.name == None:
            print '** Missing name', self.primary.stringify(), sid
        if self.synonym.name == None:
            print '** Missing name', self.synonym.stringify(), sid
    def check(self, tax, windy):
        p = self.primary.resolve_in(tax, windy)
        s = self.synonym.resolve_in(tax, windy)
        if p == None:
            return False
        if s == None:
            return False
        elif p != s:
            if windy:
                print '** no synonymy'
            return False
        elif p.name != self.primary.name:
            if windy:
                print '** name of %s is not %s' % (p, self.primary.name)
            return False
        else: 
            return True
    def proclaim(self, tax, windy):
        p = self.primary.resolve_in(tax, False)
        s = self.synonym.resolve_in(tax, False)
        # OK this is tricky.  p and have have their own names,
        # as do self.primary and self.synonym.
        # This primary objective is for node p to have self.synonym as a synonym.
        if p != None and s != None and p != s:
            p.absorb(s, self.kind, self.source_id)
        if p != None:
            if p.name == self.primary.name:
                return True
            elif p.name == self.synonym.name:
                p.rename(self.primary.name)
                return True
            else:
                if windy:
                    print ('** Warning: synonym conflict: %s %s %s' %
                           (p.name, self.primary.stringify(), self.synonym.stringify()))
                return p == s
        elif s != None:
            # Ugh.  Synonym types need to get inverted.
            s.rename(self.primary.name)
            return True
        else:
            # Diagnostics will NOT have already been displayed
            if windy:
                print ('** Cannot claim %s synonym of %s - neither resolves in %s' %
                       (self.synonym.name, self.primary.name, tax.getTag()))
            return False
    def stringify(self):
        return ('synonym_of(%s, %s, %s, %s)' %
                (self.synonym.stringify(),
                 self.primary.stringify(),
                 self.kind,
                 self.source_id))


# Sort of like synonym, but for aliases

def alias_of(syn, pri, sid):
    return _Alias_of(syn, pri, sid)

class _Alias_of:
    def __init__(self, syn, pri, sid):
        self.primary = pri
        self.alias = syn
        self.source_id = sid
    def check(self, tax, windy):
        p = self.primary.resolve_in(tax, windy)
        s = self.alias.resolve_in(tax, windy)
        if p == None:
            return False
        if s == None:
            return False
        elif p != s:
            if windy:
                print '** no alias'
            return False
        elif p.id != self.primary.id:
            if windy:
                print '** id of %s is not %s' % (p, self.primary.id)
            return False
        else: 
            return True
    def proclaim(self, tax, windy):
        p = self.primary.resolve_in(tax, False)
        s = self.alias.resolve_in(tax, False)
        if p != None and s != None:
            if p != s:
                p.absorb(s)
                p.synonym(s.name, 'alias', self.source_id)
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
                print ('** Cannot claim %s alias of %s - neither resolves in %s' %
                       (self.alias.name, self.primary.name, tax.getTag()))
            return False
    def stringify(self):
        return ('alias_of(%s, %s, %s, %s)' %
                (self.alias.stringify(),
                 self.primary.stringify(),
                 self.source_id))

def is_extinct(taxon, sid):
    return _Is_extinct(taxon, sid)

class _Is_extinct:
    def __init__(self, taxon, sid):
        self.taxon = taxon
        self.sid = sid
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
                self.source_id)

def has_rank(taxon, rank, sid):
    return _Has_rank(taxon, rank, sid)

class _Has_rank:
    def __init__(self, taxon, rank, sid):
        self.taxon = taxon
        self.rank = Rank.getRank(rank)
        self.sid = sid
        if self.rank == None:
            print '** Unrecognized rank:', rank
    def check(self, tax, windy):
        t = self.taxon.resolve_in(tax, windy)
        if t == None:
            return False
        else:
            if t.rank != self.rank:
                if windy:
                    print '** Wrong rank'
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
                self.source_id)
