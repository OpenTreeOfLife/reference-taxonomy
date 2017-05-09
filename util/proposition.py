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

# A different false value, used here to mean "I don't know"
Dunno = None

def proclaim(tax, prop):        # called make_claim in claim.py
    attitude1 = prop.proclaim(tax, True)
    attitude2 = prop.check(tax, not attitude1)
    if attitude2:
        return attitude2
    elif attitude1:
        print ('** %s seemed to accept %s, but we couldn\'t confirm' %
               (tax.getTag(), prop.stringify()))
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
                print '** id does not resolve' % self.id
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
        elif c.descendsFrom(p):
            c.changeParent(p)
            return True
    def stringify(self):
        return ('has_parent(%s, %s, %s, %s)' %
                self.child.stringify(),
                self.parent.stringify(),
                self.source_id)

# This is mainly for objective synonyms. proparte and subjective don't really work.
# Still pretty ugly, but should work for current OTT purposes.

def synonym_of(syn, pri, kind, sid):
    return _Synonym_of(syn, pri, kind, sid)

class _Synonym_of:
    def __init__(self, syn, pri, kind, sid):
        self.synonym = syn
        self.primary = pri
        self.kind = kind
        self.source_id = sid
        if self.primary.name == None:
            print '** Missing name', self.primary.stringify(), sid
        if self.synonym.name == None:
            print '** Missing name', self.synonym.stringify(), sid
    def check(self, tax, windy):
        s = self.synonym.resolve_in(tax, windy)
        p = self.primary.resolve_in(tax, windy)
        if p == None:
            return Dunno
        if s == None:
            return Dunno
        elif p.name == self.synonym.name:
            if windy:
                print ('** Warning: %s is primary, not synonym, of %s' %
                       (self.synonym.name, p))
            return False
        elif p != s:
            if windy:
                print '** no synonymy'
            return False
        else: 
            return True
    def proclaim(self, tax, windy):
        s = self.synonym.resolve_in(tax, False)
        p = self.primary.resolve_in(tax, False)
        # OK this is tricky.  Nodes p and s have have their own names,
        # as do the designators self.primary and self.synonym.
        # This primary objective is for node p to have self.synonym as a synonym.
        if p == None:
            if s == None:
                if windy:
                    print ('** Cannot claim %s synonym of %s - neither resolves in %s' %
                           (self.synonym.name, self.primary.name, tax.getTag()))
                return Dunno
            else:
                s.rename(self.primary.name)
                return True
        else:
            if s != None:
                p.absorb(s, self.kind, self.source_id)
            p.synonym(self.synonym.name, self.kind, self.source_id)
            if p.name == self.synonym.name:
                p.rename(self.primary.name)
                if p.name == self.synonym.name:
                    if windy:
                        print ('** %s is primary name of %s, not synonym' %
                               (self.synonym.name, p))
                    return False
                else:
                    return True
            else:
                return True
    def stringify(self):
        return ('synonym_of(%s, %s, %s, %s)' %
                (self.synonym.stringify(),
                 self.primary.stringify(),
                 self.kind,
                 self.source_id))


# Sort of like synonym, but for id aliases.

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
