/*
  Sketch of proposition language described at iEvoBio 2014.
  This code is incomplete and untested.

  The current 'smasher' - including what it does wrong - can be
  explained in this framework.  The framework suggests rewrites of
  parts of 'smasher', especially the treatment of paraphyletic taxa in
  Taxon.augment.  Perhaps a complete overhaul of data structure and
  algorithms...
*/

package org.opentreeoflife.smasher;

import java.util.List;

public abstract class Claim {
    
    Claim() { }

    public abstract boolean check();
    public abstract void affirm();
    abstract void forget();

    public static Claim includes(final Taxon tax1, final Taxon tax2) {
        return new Claim() {
            public boolean check() {
                return tax2.descendsFrom(tax1);
            }
            public void affirm() {
                // Should probably be careful about incertae sedis here.
                tax1.includes(tax2);
            }
            public void forget() { /* NYI */ }
        };
    }

    // True iff the given taxon is disjoint from the children of
    // another taxon, where the children are as specified in source q.
    public static Claim disjointFromChildren(final Taxon tax, final Taxon p, final Taxonomy q) {
        return new Claim() {
            public boolean check() {
                // Rather complex determination - NYI
                if (!tax.descendsFrom(p))
                    // Not quite right; the question is not whether we
                    // know whether p is an ancestor of tax, but
                    // whether it is known that it is not.
                    return false;
                else if (p.children.contains(tax))
                    // We should be checking only non-incertae-sedis
                    // children, and children according to q as opposed to
                    // p.taxonomy
                    return true;
                else
                    // Rather complex determination - need to check
                    // disjointness with children of p in q - NYI
                    return false;
            }
            public void affirm() {
                if (tax.descendsFrom(p))
                    // TBD: Check for containment in children of p according to q
                    p.take(tax);
                else
                    /* warn */;
            }
            public void forget() { /* NYI */ }
        };
    }

    public static Claim same (final Taxon tax1, final Taxon tax2) {
        return new Claim() {
            public boolean check() {
                Taxon t1 = (tax1.mapped != null ? tax1.mapped : tax1);
                Taxon t2 = (tax2.mapped != null ? tax2.mapped : tax2);
                return t1 == t2;
            }
            public void affirm() {
                Taxonomy union = null;
                if (tax1.mapped != null) union = tax1.mapped.taxonomy;
                if (tax2.mapped != null) union = tax2.mapped.taxonomy;
                if (union != null)
                    union.same(tax1, tax2);
                else
                    /* complain */ ;
            }
            public void forget() { /* NYI */ }
        };
    }

    public static Claim notSame (final Taxon tax1, final Taxon tax2) {
        // analogous to same(), NYI
        return null;
    }

    public static Claim isClade(final Taxon tax1) {
        // assumed by default, but can be forgotten, NYI
        return null;
    }

    public static Taxon sensu(final String name, final Taxonomy tax) {
        return tax.taxon(name);
    }

    // Non-biology claims

    // hasName: true if somebody has called this taxon by this name.
    public static Claim hasName(final Taxon tax, final String name) {
        return new Claim() {
            public boolean check() {
                Taxon t = (tax.mapped != null ? tax.mapped : tax);
                List<Taxon> nodes = t.taxonomy.lookup(name);
                if (nodes == null) return false;
                for (Taxon node : nodes)
                    if (node == t) return true;
                return false;
            }
            public void affirm() {
                tax.synonym(name);
            }
            public void forget() { /* NYI */ }
        };
    }

    // Has given string as its 'preferred' name - this is basically fiat.
    public static Claim hasPrimaryName(final Taxon tax, final String name) {
        return new Claim() {
            public boolean check() {
                return tax.name.equals("name");
            }
            public void affirm() {
                tax.rename(name);
            }
            public void forget() { /* NYI */ }
        };
    }

    // says(source, claim) -> claim ???

}
