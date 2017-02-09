// Assess whether x = target
// Positive means yes, negative no, zero I couldn't tell you
// x is source node, target is target node

package org.opentreeoflife.smasher;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

abstract class Heuristic {

    final boolean EXPERIMENTALP = true;

	abstract Answer assess(Taxon x, Taxon target);

    String informative;

    Heuristic() {
        informative = "used " + this.toString();
    }

    // Called 'separation' in the writeup
	static Heuristic division =
		new Heuristic() {
			public String toString() { return "disjoint divisions"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();

				if (xdiv == ydiv)
					return Answer.NOINFO;
				else if (xdiv == null)
                    return Answer.noinfo(subject, target, "note/weak-null-source-division", xdiv, ydiv);
				else if (ydiv == null)
                    return Answer.noinfo(subject, target, "note/weak-null-target-division", xdiv, ydiv);
				else if (xdiv.divergence(ydiv) == null)
                    return Answer.noinfo(subject, target, "note/weak-division", xdiv, ydiv);
                else
                    return Answer.heckNo(subject, target, "not-same/division",
                                         xdiv, ydiv);
			}
		};

    // Not currently used
	static Heuristic weakDivision =
		new Heuristic() {
			public String toString() { return "same-division-weak"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv)
					return Answer.NOINFO;
                else if (xdiv == null)
                    return Answer.noinfo(subject, target, "note/null-source-division", xdiv, ydiv);
				else if (ydiv == null)
                    return Answer.noinfo(subject, target, "note/null-target-division", xdiv, ydiv);
				else if (xdiv.noMrca() || ydiv.noMrca())
					return Answer.NOINFO;
				else
                    // about 17,000 of these... that's too many
                    // 2016-06-26 down to about 900 now.
                    return Answer.weakNo(subject, target, "not-same/weak-division", xdiv, ydiv);
			}
		};

    // Called 'proximity' in the writeup
	static Heuristic sameDivisionPreferred =
		new Heuristic() {
			public String toString() { return "same division"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv)
					return Answer.yes(subject,
                                      target,
                                      "same/division",
                                      xdiv, ydiv);
				else
					return Answer.noinfo(subject,
                                         target,
                                         "noinfo/division",
                                         xdiv, ydiv);
			}
		};


    static Heuristic ranks =
        new Heuristic() {
            public String toString() { return "disparate ranks"; }
            Answer assess(Taxon subject, Taxon target) {
                if (subject.rank != Rank.NO_RANK &&
                    target.rank != Rank.NO_RANK &&
                    ((subject.rank.level >= Rank.GENUS_RANK.level) && (target.rank.level <= Rank.FAMILY_RANK.level) ||
                     (subject.rank.level <= Rank.FAMILY_RANK.level) && (target.rank.level >= Rank.GENUS_RANK.level))) {
                    //System.out.format("| Separation by rank: %s %s | %s %s\n",
                    //                  subject, subject.rank.name, target, target.rank.name);
                    return Answer.heckNo(subject, target, "not-same/ranks",
                                         String.format("%s|%s", subject.rank.name, target.rank.name));
                }
                return Answer.NOINFO;
            }
        };

	// x is source node, target is target node

	static Heuristic lineage =
		new Heuristic() {
			public String toString() { return "by lineage"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon y0 = scan(target, x.taxonomy);	  // ignore names not known in both taxonomies
				Taxon x0 = scan(x, target.taxonomy);
				if (x0 == null || y0 == null)
					return Answer.NOINFO;
				if (x0.name == null)
					return Answer.NOINFO;
				if (y0.name == null)
					return Answer.NOINFO;
				if (x0.name.equals(y0.name))
					return Answer.heckYes(x, target, "same/parent+parent", x0, y0);
				else if (online(x0.name, y0))
					// differentiating the two levels
					// helps to deal with the Nitrospira situation (7 instances)
					return Answer.heckYes(x, target, "same/ancestor+parent", x0, y0);
				else if (online(y0.name, x0))
					return Answer.heckYes(x, target, "same/parent+ancestor", x0, y0);
				else
					// Incompatible parents.  Who knows what to do.
					return Answer.noinfo(x, target, "noinfo/lineage", x0, y0);
			}
		};

	// Find a near-ancestor (parent, grandparent, etc) node that's in
	// common with the other taxonomy
	Taxon scan(Taxon node, Taxonomy other) {
        // if (!node.isPlaced()) return null; // Protozoa

		Taxon up = node.parent;

		// Cf. informative() method
		// Without this we get ambiguities when the taxon is a species
		while (up != null && (up.name == null || node.name.startsWith(up.name)))
			up = up.parent;

		while (up != null && (up.name == null || other.lookup(up.name) == null))
			up = up.parent;

        return up;
	}

	static boolean online(String name, Taxon node) {
		for ( ; node != null; node = node.parent)
			if (node.name != null && node.name.equals(name)) return !node.noMrca(); // kludge
		return false;
	}

	static Heuristic subsumption =
		new Heuristic() {
			public String toString() { return "overlapping membership"; }
			Answer assess(Taxon x, Taxon target) {
                if (x.children == null)
                    return Answer.NOINFO;                         // possible attachment point
                if (target.children == null)
                    return Answer.noinfo(x, target, "noinfo/target-no-children", null, null);  // possible attachment point

                // Higher taxa must share at least one descendant in order to match
				Taxon b = AlignmentByName.witness(x, target);    // in both
				if (b == null) {   // no overlap
                    // we don't need this, but useful for reporting
                    Taxon a = AlignmentByName.antiwitness(x, target);// in x but not target
                    if (a == null)
                        return Answer.noinfo(x, target, "noinfo/unknown-overlap", b, a);
                    else
                        return Answer.noinfo(x, target, "noinfo/disjoint", b, a);

                    /*
                    if (x.rank != null && x.rank.level >= Rank.SPECIES_RANK.level)
                        return Answer.NOINFO;                         // possible attachment point
                    if (target.rank != null && target.rank.level >= Rank.SPECIES_RANK.level)
                        return Answer.NOINFO;                         // possible attachment point

                    if (false)
                        // FAIL: NCBI Jefea = GBIF Jefea, but no members in common
                        //  and lineage check fails, too (79 similar cases in Asterales)
                        return Answer.no(x, target, "not-same/disjoint", null);
                    else
                        return Answer.NOINFO;
                    */
                }

				Taxon a = AlignmentByName.antiwitness(x, target);// in x but not target
                if (a == null)	// good
                    // 2859
                    return Answer.heckYes(x, target, "same/is-subsumed-by", b, a);
                else
                    // 94
                    return Answer.yes(x, target, "same/overlaps", b, a);
			}
		};

    // work in progress
	static Heuristic disjoint =
		new Heuristic() {
			public String toString() { return "disjoint membership"; }
			Answer assess(Taxon x, Taxon target) {
                if (x.children != Taxon.NO_CHILDREN &&
                    target.children != Taxon.NO_CHILDREN &&
                    AlignmentByName.witness(x, target) == null &&
                    AlignmentByName.antiwitness(x, target) != null)
                    return Answer.no(x, target, "not-same/no-overlap", null);
                else
                    return Answer.NOINFO;
			}
		};

	static Heuristic byPrimaryName =
		new Heuristic() {
			public String toString() { return "by name"; }
			Answer assess(Taxon x, Taxon target) {
				if (x.name == null) {
                    System.out.format("** No name! %s\n", x);
					return Answer.NOINFO;
				} else if (target == null) {
                    System.out.format("** No target! %s\n", x);
					return Answer.NOINFO;
				} else if (x.name.equals(target.name))
					return Answer.heckYes(x, target, "same/primary-name", x.name);
				else
					return Answer.NOINFO;
			}
		};

	static Heuristic sameSourceId =
		new Heuristic() {
			public String toString() { return "by source id"; }
			Answer assess(Taxon x, Taxon target) {
				// x is source node, target is target node.
				QualifiedId xid = maybeQualifiedId(x);
				QualifiedId yid = maybeQualifiedId(target);
                if (xid != null && xid.equals(yid))
					return Answer.yes(x, target, "same/source-id", null);
				else
					return Answer.NOINFO;
			}
		};


    static QualifiedId maybeQualifiedId(Taxon node) {
        QualifiedId qid = node.putativeSourceRef(); // first among sources
        if (qid != null) return qid;
        if (node.id != null && node.taxonomy.getIdspace() != null)
            return node.getQualifiedId();
        else return null;
    }

	// Match NCBI or GBIF identifiers
	// This kicks in when we try to map the previous OTT to assign ids, after we've mapped GBIF.
	// x is a node in the old OTT.	target, the target node, is in the new OTT.
	static Heuristic anySourceId =
		new Heuristic() {
			public String toString() { return "any source id in common"; }
			Answer assess(Taxon x, Taxon target) {
				// x is source node, target is target node.
				// Two cases:
				// 1. Mapping x=NCBI to target=target(SILVA): target.sourceIds contains x.id
				// 2. Mapping x=idsource to target=target: x.sourceIds contains ncbi:123
				// compare x.id to target.sourcenode.id
                if (x.sourceIds == null) return Answer.NOINFO;
                if (target.sourceIds == null) return Answer.NOINFO;

                boolean firstxy = true;
                for (QualifiedId xsourceid : x.sourceIds)
                    for (QualifiedId ysourceid : target.sourceIds) {
                        if (!firstxy && xsourceid.equals(ysourceid))
                            return Answer.yes(x, target, "same/any-source-id", null);
                        firstxy = false;
                    }
				return Answer.NOINFO;
			}
		};

	// E.g. Steganina, Tripylina in NCBI - they're distinguishable by their ranks
	static Heuristic byRank =
		new Heuristic() {
			public String toString() { return "same-rank"; }
			Answer assess(Taxon x, Taxon target) {
				if ((x == null ?
					 x == target :
					 (x.rank == target.rank)))
					// Evidence of difference, but not good enough to overturn name evidence
					return Answer.weakYes(x, target, "same/rank", x.rank.name);
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Paraphelenchus
	// E.g. Steganina in NCBI - distinguishable by their ranks
	static Heuristic elimination =
		new Heuristic() {
			public String toString() { return "only-candidate"; }
			Answer assess(Taxon x, Taxon target) {
				return Answer.weakYes(x, target, "same/only-candidate", null);
			}
		};

    boolean metBy(Taxon node, Taxon unode) {
        return this.assess(node, unode).isYes();
    }
}

