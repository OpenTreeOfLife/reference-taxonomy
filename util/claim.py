# Use this with 'from claim import *'
#   ... which unfortunately doesn't work in jython

from org.opentreeoflife.smasher import Taxonomy

def test():
    tax = Taxonomy.newTaxonomy()
    tax.newTaxon('Mouse', 'species', 'about:blank')
    tax.newTaxon('Dog', 'species', 'about:blank')
    tax.newTaxon('Mammal', 'class', 'about:blank')

    new_claims = [
        Has_child('Mammal', 'Mouse', 'about:blank'),
        Whether_same('Mouse', 'Mus', True),
        Whether_same('Muus', 'Mouse', True),
    ]
    expectations = [
        Has_child('Mammal', 'Mouse'),
        Has_child('Mammal', With_ancestor('Mouse', 'Mammal')),
        Has_child(With_descendant('Mammal', 'Mouse'), 'Mouse'),
        Whether_same('Mammal', 'Mammal', True),
        Whether_same('Mammal', 'Meemmal', False),
        Whether_same('Mouse', 'Mus', True),
        Whether_same('Mus', 'Mouse', True),
        Whether_same('Mus', 'Horse', False),
    ]
    surprises = [
        Has_child('Mouse', 'Mammal'),
        Has_child('Mammal', 'Dog'), # fails
        Whether_same('Mammal', 'Meemmal', True),
        Whether_same('Mammal', 'Mammal', False),
    ]
    make_claims(tax, new_claims)
    passed = test_claims(tax, expectations)
    passed = test_claims(tax, new_claims)
    find_surprises(tax, surprises)
    return passed

def make_claims(tax, claims):
    passed = True
    for claim in claims:
        if not claim.make_true(tax):
            print '** claim failed:', stringify(claim)
        elif not claim.check(tax):
            print '** claim seemed to be made, but not verified:', stringify(claim)
    return passed

def test_claims(tax, claims):
    passed = True
    for claim in claims:
        if not claim.check(tax): 
            print '** test failed:', stringify(claim)
            passed = False
    return passed

def find_surprises(tax, claims):
    passed = False
    for claim in claims:
        if claim.check(tax): 
            print '| surprise passed:', stringify(claim)
            passed = True
    return passed

# -- Claims --

# That two taxa are the same
# The name the latter is preferred to that of the former

class Whether_same:
    def __init__(self, source, target, whether, reason=None):
        self.source = source
        self.target = target
        self.reason = reason
        self.whether = whether

    def make_true(self, taxonomy):
        source_taxon = resolve_in(self.source, taxonomy, windy=False)
        target_taxon = resolve_in(self.target, taxonomy, windy=False)

        if source_taxon != None:
            attempt_mapping(source_taxon, self.target, self.whether)

        if source_taxon == None and target_taxon != None:
            print 'flipping', self.source, '><', self.target
            (source_taxon, target_taxon) = (target_taxon, source_taxon)
            (source, target) = (self.target, self.source)
        else:
            (source, target) = (self.source, self.target)

        if source_taxon == None:
            resolve_in(self.target, taxonomy) # print diagnostic
            resolve_in(self.source, taxonomy) # print diagnostic
            return False

        if target_taxon != None:

            if source_taxon == target_taxon:
                # Same when want different
                # Maybe remove a synonym here?
                return whether

            if not whether:
                # Different when want different
                return True

            # Figure out correct argument order ...
            # sets synonym
            # N.b. not flipped - order matters a lot
            return target_taxon.absorb(source_taxon)

        elif isinstance(target, str):
            return source_taxon.whetherHasName(target, self.whether, True)

        return False

    def check(self, taxonomy):
        source_taxon = resolve_in(self.source, taxonomy, windy=False)
        target_taxon = resolve_in(self.target, taxonomy, windy=False)
        if source_taxon != None and target_taxon != None:
            return (source_taxon == target_taxon) == self.whether
        return not self.whether

    def unapply(self):
        return ('Whether_same', [self.source, self.target, self.whether, self.reason])

def attempt_mapping(source_taxon, target, whether):
    union = source_taxon.taxonomy.target()
    if union != None:
        union_target = resolve_in(target, union, windy=False)
        if union_target != None:
            source_taxon.whetherHasName(union_target.name, whether, True)
            union.sameness(source_taxon, union_target, whether, True)


# That one taxon has another as a child

class Has_child:                # replaces .take
    def __init__(self, parent, child, reason=None):
        self.parent = parent
        self.child = child
        self.reason = reason

    def make_true(self, taxonomy):
        child_taxon = resolve_in(self.child, taxonomy)
        parent_taxon = resolve_in(self.parent, taxonomy)
        if child_taxon != None and parent_taxon != None:
            return parent_taxon.take(child_taxon)
        return False

    def check(self, taxonomy):
        child_taxon = resolve_in(self.child, taxonomy)
        parent_taxon = resolve_in(self.parent, taxonomy)
        if child_taxon != None and parent_taxon != None:
            return child_taxon.parent == parent_taxon
        return False

    def unapply(self):
        return ('Has_child', [self.parent, self.child])

# That a taxon is monophyletic ('elide')

class Whether_monophyletic:
    def __init__(self, des, whether, reason=None):
        self.designator = des
        self.whether = whether
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.whetherMonophyletic(self.whether, True)
        return False

    def check(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.whetherMonophyletic(self.whether, False)
        else:
            return not whether

    def unapply(self):
        return ('Whether_monophyletic', [self.whether, self.reason])

# Whether it should be in the taxonomy at all ('prune')

class None_good:
    def __init__(self, des, reason=None):
        self.designator = des
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.prune()
        return False

    def check(self, taxonomy):
        return len(get_candidates(self.designator, taxonomy)) == 0

# Whether it should have any children ('trim')

class Children_no_good:
    def __init__(self, des, reason=None):
        self.designator = des
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.trim()
        return False

    def check(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.children == None
        return False

# Whether_visible or not

class Whether_visible:
    def __init__(self, des, whether, reason=None):
        self.designator = des
        self.whether = whether
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            if self.whether:
                return taxon.unhide()
            else:
                return taxon.hide()
        return False

    def check(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        return taxon.isHidden() == self.whether

# Whether extinct (very similar)

class Whether_extant:
    def __init__(self, des, whether, reason=None):
        self.designator = des
        self.whether = whether
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            if self.whether:
                return taxon.extant()
            else:
                return taxon.extinct()
        return False

    def check(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        return taxon.isExtant() == self.whether

    def unapply(self):
        return ('Whether_extant', [self.designator, self.whether, self.reason])

# Hide taxon and all descendants
class All_hidden:
    def __init__(self, des, reason=None):
        self.designator = des
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.hideDescendants()
        return False

    def check(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if (taxon != None):
            return taxon.isHidden()
        return False

# Hide taxon's descendants
class All_hidden_to_rank:
    def __init__(self, des, rank, reason=None):
        self.designator = des
        self.rank = rank
        self.reason = reason

    def make_true(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            return taxon.hideDescendantsToRank(rank)
        return False

    def test(self, taxonomy):
        taxon = resolve_in(self.designator, taxonomy)
        if taxon != None:
            if taxon.children != None:
                for child in taxon.children:
                    if not child.isHidden():
                        return False
                return True
        return False

# -- Designators --

class With_ancestor:
    def __init__(self, designator, ancestor):
        self.designator = designator
        self.ancestor = ancestor
    def generate(self, taxonomy):
        ancestors = get_candidates(self.ancestor, taxonomy)
        if ancestors != None:
            descendants = get_candidates(self.designator, taxonomy)
            return [d for d in descendants for a in ancestors if (d.descendsFrom(a) and d != a)]
        else: return []
    def unapply(self):
        return ('With_ancestor', [self.designator, self.ancestor])

class With_descendant:
    def __init__(self, designator, descendant):
        self.designator = designator # e.g. Mammalia
        self.descendant = descendant # e.g. Mus
    def generate(self, taxonomy):
        ancestors = get_candidates(self.designator, taxonomy)  # the ancestor
        if ancestors != None:
            descendants = get_candidates(self.descendant, taxonomy)
            return [a for d in descendants for a in ancestors if (d.descendsFrom(a) and d != a)]
        else: return []
    def unapply(self):
        return ('With_descendant', [self.designator, self.descendant])

# To be called by claim methods to resolve a designator to a single taxon in a taxonomy

def resolve_in(designator, taxonomy, windy=True):
    candidates = get_candidates(designator, taxonomy)
    if len(candidates) == 1:
        return candidates[0]
    elif len(candidates) == 0:
        if windy:
            print '** no taxon %s in %s (%s)' % (stringify(designator), taxonomy.getTag(), windy)
        return None
    else:
        if windy:
            print '** %s is ambiguous' % stringify(designator)
            for candidate in candidates:
                print ' ', candidate
        return None

# Designator implementation infrastructure

def get_candidates(designator, taxonomy):
    if isinstance(designator, str):
        candidates = taxonomy.lookup(designator)
        if candidates == None:
            return []
        else:
            return candidates
    else:
        return designator.generate(taxonomy)

# Turn a term (designator or claim) into a string

def stringify(term):
    if isinstance(term, str):
        if '"' in term:
            return "'" + term + "'"
        else:
            # Fails if both " and ' occur in string
            return "'" + term + "'"
    elif isinstance(term, bool):
        if term: return 'True'
        else: return 'False'
    elif term == None:
        return 'None'
    else:
        (operator, args) = term.unapply()
        return '%s(%s)' % (operator, ', '.join([stringify(arg) for arg in args]))


test()

# x such that P(x)
# x such that P(x) and Q(x)
