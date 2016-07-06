/*
  The goal here is to be able to unify source nodes with union nodes.

  The unification is done by Taxon.unifyWith and has the effect of
  setting the 'mapped' field of the node.

  */


// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same / notSame list as input.


package org.opentreeoflife.smasher;

import java.util.List;
import java.util.Collection;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

public abstract class Alignment {

	SourceTaxonomy source;
    UnionTaxonomy union;

    Alignment(SourceTaxonomy source, UnionTaxonomy union) {
        this.source = source;
        this.union = union;
    }

    public abstract void align();

    // not used?
    abstract Answer answer(Taxon node);

    // not used?
    Taxon map(Taxon node) {
        Answer a = this.answer(node);
        if (a == null) return null;
        else if (a.isYes()) return a.target;
        else return null;
    }

    // ----- Aligning individual nodes -----

	// unode is a preexisting node in this taxonomy.

	public void alignWith(Taxon node, Taxon unode, String reason) {
        try {
            Answer answer = Answer.yes(node, unode, reason, null);
            this.alignWith(node, unode, answer);
            answer.maybeLog();
        } catch (Exception e) {
            System.err.format("** Exception in alignWith %s %s\n", node, unode);
            e.printStackTrace();
        }
    }

    // Set the 'mapped' property of this node, carefully
	public void alignWith(Taxon node, Taxon unode, Answer answer) {
		if (node.mapped == unode) return; // redundant
        if (!(unode.taxonomy == this.union)) {
            System.out.format("** Alignment target %s is not in the union taxonomy\n", node);
            Taxon.backtrace();
        } else if (!(node.taxonomy == this.source)) {
            System.out.format("** Alignment source %s is not in the source taxonomy\n", unode);
            Taxon.backtrace();
        } else if (node.noMrca() != unode.noMrca()) {
            System.out.format("** attempt to unify forest %s with non-forest %s\n",
                              node, unode);
            Taxon.backtrace();
        } else if (node.mapped != null) {
			// Shouldn't happen - assigning a single source taxon to two
			//	different union taxa
			if (node.report("Already assigned to node in union:", unode))
				Taxon.backtrace();
		} else if (unode.prunedp) {
            System.out.format("** attempt to map %s to pruned node %s\n",
                              node, unode);
            Taxon.backtrace();
		} else {
            node.mapped = unode;
            node.answer = answer;
            if (node.name != null && unode.name != null && !node.name.equals(unode.name))
                Answer.yes(node, unode, "synonym-match", node.name).maybeLog();
            if (unode.comapped != null) {
                // Union node has already been matched to, but synonyms are OK
                if (unode.comapped != node)
                    node.markEvent("lumped");
            } else
                unode.comapped = node;
        }
    }

	public boolean same(Taxon node1, Taxon node2) {
		return sameness(node1, node2, true, true);
	}

	public boolean notSame(Taxon node1, Taxon node2) {
		return sameness(node1, node2, false, true);
	}

	public boolean sameness(Taxon node, Taxon unode, boolean whether, boolean setp) {
        if (node == null || unode == null) return false;
        if (node.taxonomy != source) {
            System.err.format("** node1 %s not in source taxonomy\n", node);
            return false;
        }
        if (unode.taxonomy != union) {
            System.err.format("** node2 %s not in source taxonomy\n", unode);
            return false;
        }
        // start logging this name
        if (node.name != null && union.eventlogger != null)
            union.eventlogger.namesOfInterest.add(node.name);
		if (whether) {			// same
			if (node.mapped != null) {
				if (node.mapped != unode) {
					System.err.format("** The taxa have already been determined to be different: %s\n", node);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                this.alignWith(node, unode, "same/ad-hoc");
                return true;
            } else return false;
		} else {				// notSame
			if (node.mapped != null) {
				if (node.mapped == unode) {
					System.err.format("** The taxa have already been determined to be the same: %s\n", node);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                // Give the source node (node) a place to go in the union that is
                // different from the union node it's different from
                Taxon evader = new Taxon(unode.taxonomy, unode.name);
                this.alignWith(node, evader, "not-same/ad-hoc");

                union.addRoot(evader);
                // Now evader != unode, as desired.
                return true;
            } else return false;
		}
	}


    // ----- Align divisions from skeleton taxonomy -----

	public void markDivisions(SourceTaxonomy source) {
		if (union.skeleton == null)
			this.pin(source);	// Obsolete code, for backward compatibility!
		else
            markDivisionsFromSkeleton(source, union.skeleton);
	}

	// Before every alignment pass (folding source taxonomy into
	// union), all 'division' taxa (from the skeleton) that occur in
	// the source taxonomy are identified and the
	// 'division' field of each is set to the corresponding
	// division node from the skeleton taxonomy.  Also the corresponding
	// division nodes are aligned.

	// This operation is idempotent.

    public void markDivisionsFromSkeleton(Taxonomy source, Taxonomy skel) {
        for (String name : skel.allNames()) {
            Taxon div = skel.unique(name);
            Taxon node = highest(source, name);
            if (node != null) {
                if (node.getDivisionProper() == null) {
                    node.setDivision(div);
                    Taxon unode = div.mapped;
                    if (unode == null)
                        System.out.format("** Skeleton node not mapped to union: %s\n", div);
                    else
                        alignWith(node, unode, "same/by-division-name");
                } else if (node.getDivisionProper() != div)
                    System.out.format("** Help!  Conflict over division mapping: %s have %s want %s\n",
                                      node, node.getDivisionProper(), div);
            }
        }
    }

	// Most rootward node in this taxonomy having a given name
	public static Taxon highest(Taxonomy tax, String name) { // See pin()
		List<Node> l = tax.lookup(name);
		if (l == null) return null;
		Taxon best = null, otherbest = null;
		int depth = 1 << 30;
        Rank genus = Rank.getRank("genus");
		for (Node nodenode : l) {
            Taxon node = nodenode.taxon();
            // This is for Ctenophora
            if (node.rank == genus) continue;
			int d = node.measureDepth();
			if (d < depth) {
				depth = d;
				best = node;
				otherbest = null;
			} else if (d == depth && node != best)
				otherbest = node;
		}
		if (otherbest != null) {
			if (otherbest == best)
				// shouldn't happen
				System.err.format("** Multiply indexed: %s %s %s\n", best, otherbest, depth);
			else
				System.err.format("** Ambiguous division name: %s %s %s\n", best, otherbest, depth);
            return null;
		}
		return best;
	}

	// List determined manually and empirically
	// @deprecated
	void pin(Taxonomy source) {
		String[][] pins = {
			// Stephen's list
			{"Fungi"},
			{"Bacteria"},
			{"Alveolata"},
			// {"Rhodophyta"},	creates duplicate of Cyanidiales
			{"Glaucophyta", "Glaucocystophyceae"},
			{"Haptophyta", "Haptophyceae"},
			{"Choanoflagellida"},
			{"Metazoa", "Animalia"},
			{"Chloroplastida", "Viridiplantae", "Plantae"},
			// JAR's list
			{"Mollusca"},
			{"Arthropoda"},		// Tetrapoda, Theria
			{"Chordata"},
			// {"Eukaryota"},		// doesn't occur in gbif, but useful for ncbi/ncbi test merge
			// {"Archaea"},			// ambiguous in ncbi
			{"Viruses"},
		};
		int count = 0;
		for (int i = 0; i < pins.length; ++i) {
			String names[] = pins[i];
			Taxon n1 = null, div = null;
			// The division (div) is in the union taxonomy.
			// For each pinnable name, look for it in both taxonomies
			// under all possible synonyms
			for (int j = 0; j < names.length; ++j) {
				String name = names[j];
				Taxon m1 = highest(source, name);
				if (m1 != null) n1 = m1;
				Taxon m2 = highest(union, name);
				if (m2 != null) div = m2;
			}
			if (div != null) {
				div.setDivision(div);
				if (n1 != null)
					n1.setDivision(div);
				if (n1 != null && div != null)
					alignWith(n1, div, "same/pinned"); // hmm.  TBD: move this out of here
				if (n1 != null || div != null)
					++count;
			}
		}
		if (count > 0)
			System.out.println("Pinned " + count + " out of " + pins.length);
	}


	// What was the fate of each of the nodes in this source taxonomy?

	static void alignmentReport(SourceTaxonomy source, UnionTaxonomy union) {
		if (UnionTaxonomy.windyp) {

			int total = 0;
			int nonamematch = 0;
			int prevented = 0;
			int corroborated = 0;

			// Could do a breakdown of matches and nonmatches by reason

			for (Taxon node : source.taxa()) {
				++total;
				if (union.lookup(node.name) == null)
					++nonamematch;
				else if (node.mapped == null)
					++prevented;
				else
					++corroborated;
			}

			System.out.println("| Of " + total + " nodes in " + source.getTag() + ": " +
							   (total-nonamematch) + " with name in common, of which " + 
							   corroborated + " matched with existing, " + 
							   prevented + " blocked");
		}
	}

    // Compute LUBs (MRCAs) and cache them in Taxon objects

    void cacheLubs() {

        // The answers are already stored in .answer of each node
        // Now do the .lubs
        winners = fresh = grafts = outlaws = 0;
        for (Taxon root : source.roots())
            cacheLubs(root);
        if (winners + fresh + grafts + outlaws > 0)
            System.out.format("| LUB match: %s graft: %s differ: %s bad: %s\n",
                              winners, fresh, grafts, outlaws);
    }

    private int winners, fresh, grafts, outlaws;

    // An important case is where a union node is incertae sedis and the source node isn't.

    // Input is in source taxonomy, return value is in union taxonomy

    Taxon cacheLubs(Taxon node) {
        if (node.children == null)
            return node.lub = node.mapped();
        else {
            Taxon mrca = null;  // in union
            for (Taxon child : node.children) {
                Taxon a = cacheLubs(child); // in union
                if (child.isPlaced()) {
                    if (a != null) {
                        if (a.noMrca()) continue;
                        a = a.parent; // in union
                        if (a.noMrca()) continue;
                        if (mrca == null)
                            mrca = a; // in union
                        else {
                            Taxon m = mrca.mrca(a);
                            if (m.noMrca()) continue;
                            mrca = m;
                        }
                    }
                }
            }

            node.lub = mrca;

            if (node.mapped == null)
                ++fresh;
            else if (mrca == null)
                ++grafts;
            else if (node.mapped == mrca)
                // Ideal case - mrca of children is the node itself
                ++winners;
            else {
                // Divergence across taxonomies.  MRCA of children can be
                // ancestor, descendant, or disjoint from target.
                Taxon[] div = mrca.divergence(node.mapped);
                if (div == null)
                    // If div == null, then either mrca descends from node.mapped or the other way around.
                    ++winners;
                else if (div[0] == mrca || div[1] == node.mapped || div[1].parent == node.mapped)
                    // Hmm... allow siblings (and cousins) to merge.  Blumeria graminis
                    ++winners;
                else {
                    if (outlaws < 10 || node.name.equals("Elaphocordyceps subsessilis") || node.name.equals("Bacillus selenitireducens"))
                        System.out.format("! %s maps by name to %s which is disjoint from children-mrca %s; they meet at %s\n",
                                          node, node.mapped, mrca, div[0].parent);
                    ++outlaws;
                    // OVERRIDE.
                    node.answer = Answer.no(node, node.mapped, "not-same/disjoint", null);
                    node.answer.maybeLog();
                    node.mapped = null;
                }
            }
            return node.mapped;
        }
    }

    /*
                            if (false) {
                                Taxon div1 = mrca.getDivision();
                                Taxon div2 = a.getDivision();
                                if (div1 != div2 && div1.divergence(div2) != null)
                                    // 2015-07-23 this happens about 300 times
                                    System.out.format("! Children of %s are in disjoint divisions (%s in %s + %s in %s)\n",
                                                      node, mrca, div1, a, div2);
                            }
    */


}

// Assess a criterion for judging whether x <= target or not x <= target
// Positive means yes, negative no, zero I couldn't tell you
// x is source node, target is union node

abstract class Criterion {

	abstract Answer assess(Taxon x, Taxon target);

    static boolean HALF_DIVISION_EXCLUSION = true;

    static int kludge = 0;
    static int kludgeLimit = 100;

	static Criterion division =
		new Criterion() {
			public String toString() { return "same-division"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv || xdiv.noMrca() || ydiv.noMrca())
					return Answer.NOINFO;
				else if (xdiv.descendsFrom(ydiv))
                    return Answer.NOINFO;
				else if (ydiv.descendsFrom(xdiv))
                    return Answer.NOINFO;
                else
                    return Answer.heckNo(subject, target, "not-same/division", xdiv.name);
			}
		};

	static Criterion weakDivision =
		new Criterion() {
			public String toString() { return "same-division-weak"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv)
					return Answer.NOINFO;
				else if (xdiv.noMrca() || ydiv.noMrca())
					return Answer.NOINFO;
				else
                    // about 17,000 of these... that's too many
                    // 2016-06-26 down to about 900 now.
                    // but I bet they're almost all supposed to be matches.
                    return Answer.weakNo(subject, target, "not-same/weak-division", xdiv.name);
			}
		};

    static final int family = Rank.getRank("family").level;
    static final int genus = Rank.getRank("genus").level;

    static Criterion ranks =
        new Criterion() {
            public String toString() { return "ranks"; }
            Answer assess(Taxon subject, Taxon target) {
                if (subject.rank != Rank.NO_RANK &&
                    target.rank != Rank.NO_RANK &&
                    ((subject.rank.level >= genus) && (target.rank.level <= family) ||
                     (subject.rank.level <= family) && (target.rank.level >= genus))) {
                    System.out.format("| Separation by rank: %s %s | %s %s\n",
                                      subject, subject.rank.name, target, target.rank.name);
                    return Answer.weakNo(subject, target, "not-same/ranks",
                                         String.format("%s|%s", subject.rank.name, target.rank.name));
                }
                return Answer.NOINFO;
            }
        };

	static Criterion eschewTattered =
		new Criterion() {
			public String toString() { return "eschew-tattered"; }
			Answer assess(Taxon x, Taxon target) {
				if (!target.isPlaced() //from a previous merge
					&& isHomonym(target))  
					return Answer.weakNo(x, target, "not-same/unplaced", null);
				else
					return Answer.NOINFO;
			}
		};

	// Homonym discounting synonyms
	static boolean isHomonym(Taxon taxon) {
		List<Node> alts = taxon.taxonomy.lookup(taxon.name);
		if (alts == null) {
			System.err.println("Name not indexed !? " + taxon.name);
			return false;
		}
		for (Node alt : alts)
			if (alt != taxon && alt.taxonNameIs(taxon.name))
				return true;
		return false;
	}

	// x is source node, target is union node

	static Criterion lineage =
		new Criterion() {
			public String toString() { return "same-ancestor"; }
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
					return Answer.heckYes(x, target, "same/parent+parent", x0.name);
				else if (online(x0.name, y0))
					// differentiating the two levels
					// helps to deal with the Nitrospira situation (7 instances)
					return Answer.heckYes(x, target, "same/ancestor+parent", x0.name);
				else if (online(y0.name, x0))
					return Answer.heckYes(x, target, "same/parent+ancestor", y0.name);
				else
					// Incompatible parents.  Who knows what to do.
					return Answer.NOINFO;
			}
		};

	// Find a near-ancestor (parent, grandparent, etc) node that's in
	// common with the other taxonomy
	Taxon scan(Taxon node, Taxonomy other) {
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

	static Criterion subsumption =
		new Criterion() {
			public String toString() { return "overlaps"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon a = AlignmentByName.antiwitness(x, target);
				Taxon b = AlignmentByName.witness(x, target);
				if (b != null) { // good
					if (a == null)	// good
						// 2859
						return Answer.heckYes(x, target, "same/is-subsumed-by", b.name);
					else
						// 94
						return Answer.yes(x, target, "same/overlaps", b.name);
				} else {
					if (a == null)
						// ?
						return Answer.NOINFO;
					else if (target.children != null)		// bad
						// 13 ?
						return Answer.no(x, target, "not-same/incompatible", a.name);
                    else
						return Answer.NOINFO;
				}
			}
		};

	static Criterion sameSourceId =
		new Criterion() {
			public String toString() { return "same-source-id"; }
			Answer assess(Taxon x, Taxon target) {
				// x is source node, target is union node.
				QualifiedId xid = maybeQualifiedId(x);
				QualifiedId yid = maybeQualifiedId(target);
                if (xid != null && xid.equals(yid))
					return Answer.yes(x, target, "same/source-id", null);
				else
					return Answer.NOINFO;
			}
		};


	// Match NCBI or GBIF identifiers
	// This kicks in when we try to map the previous OTT to assign ids, after we've mapped GBIF.
	// x is a node in the old OTT.	target, the union node, is in the new OTT.
	static Criterion anySourceId =
		new Criterion() {
			public String toString() { return "any-source-id"; }
			Answer assess(Taxon x, Taxon target) {
				// x is source node, target is union node.
				// Two cases:
				// 1. Mapping x=NCBI to target=union(SILVA): target.sourceIds contains x.id
				// 2. Mapping x=idsource to target=union: x.sourceIds contains ncbi:123
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

    static QualifiedId maybeQualifiedId(Taxon node) {
        QualifiedId qid = node.putativeSourceRef();
        if (qid != null) return qid;
        if (node.id != null && node.taxonomy.getIdspace() != null)
            return node.getQualifiedId();
        else return null;
    }

	// Buchnera in Silva and 713
	static Criterion knowDivision =
		new Criterion() {
			public String toString() { return "same-division-knowledge"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon xdiv = x.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv != ydiv) // One might be null
					// Evidence of difference, good enough to prevent name-only matches
					return Answer.heckNo(x, target, "not-same/division-knowledge", x.divisionName());
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Steganina, Tripylina in NCBI - they're distinguishable by their ranks
	static Criterion byRank =
		new Criterion() {
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

	static Criterion byPrimaryName =
		new Criterion() {
			public String toString() { return "same-primary-name"; }
			Answer assess(Taxon x, Taxon target) {
				if (x.name.equals(target.name))
					return Answer.weakYes(x, target, "same/primary-name", x.name);
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Paraphelenchus
	// E.g. Steganina in NCBI - distinguishable by their ranks
	static Criterion elimination =
		new Criterion() {
			public String toString() { return "name-in-common"; }
			Answer assess(Taxon x, Taxon target) {
				return Answer.weakYes(x, target, "same/name-in-common", null);
			}
		};

	static Criterion[] criteria = {
		division,
		lineage,
        subsumption,
		sameSourceId,
		anySourceId,
        weakDivision,
		byRank,
        ranks,
        byPrimaryName,
        elimination,
    };

    boolean metBy(Taxon node, Taxon unode) {
        return this.assess(node, unode).isYes();
    }
}

