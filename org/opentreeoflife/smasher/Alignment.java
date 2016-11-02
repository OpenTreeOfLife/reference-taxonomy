/*
  The goal here is to be able to unify source nodes with target nodes.

  The unification is done by Taxon.unifyWith and has the effect of
  setting the 'mapped' field of the node.

  */


// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same list as input.


package org.opentreeoflife.smasher;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

public class Alignment {

    boolean EXPERIMENTALP = true;

	Taxonomy source;
    Taxonomy target;

    private Map<Taxon, Answer> mappings;

    Alignment(Taxonomy source, Taxonomy target) {
        this.source = source;
        this.target = target;
        this.mappings = new HashMap<Taxon, Answer>();
        start();
    }

    // clone an alignment, for comparison
    Alignment(Alignment a) {
        this.source = a.source;
        this.target = a.target;
        this.mappings = a.copyMappings();
        start();
    }

    public Set<Taxon> keySet() {
        return mappings.keySet();
    }

    private void start() {
        target.eventLogger.resetEvents();
        this.reset();          // depths, brackets, comapped
        this.alignWith(source.forest, target.forest, "align-forests");
    }

    public Answer getAnswer(Taxon node) {
        return mappings.get(node);
    }

    public void setAnswer(Taxon node, Answer answer) {
        if (answer.target != null && answer.target.prunedp)
            System.err.format("** Pruned taxon found as mapping target: %s -> %s\n",
                              node, answer.target);
        else {
            Taxon have = getTaxon(node);
            if (have != null && have != answer.target)
                System.err.format("** Node %s is already aligned to %s, can't align it to %s\n",
                                  node, have, answer.target);
            else
                mappings.put(node, answer);
        }
    }

    public Taxon getTaxon(Taxon node) {
        Answer a = mappings.get(node);
        if (a == null) return null;
        else if (a.isYes()) return a.target;
        else return null;
    }

    public final void align() {
        System.out.println("--- Mapping " + source.getTag() + " to target ---");
        this.reallyAlign();     // calls this.assignBrackets();

        this.computeLubs();
        target.eventLogger.eventsReport("| ");

        // Report on how well the merge went.
        this.alignmentReport();
    }

    // Should be overridden
    void reallyAlign() {
        System.err.format("** Alignment method not overridden\n");
    }

    Map<Taxon, Answer> copyMappings() {
        Map<Taxon, Answer> m = new HashMap<Taxon, Answer>();
        for (Taxon t : mappings.keySet())
            m.put(t, mappings.get(t));
        return m;
    }

    int size() {
        return this.mappings.size();
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
		if (getTaxon(node) == unode) return; // redundant
        if (!(unode.taxonomy == this.target)) {
            System.err.format("** Alignment target %s is not in the target taxonomy\n", node);
            Taxon.backtrace();
        } else if (!(node.taxonomy == this.source)) {
            System.err.format("** Alignment source %s is not in the source taxonomy\n", unode);
            Taxon.backtrace();
        } else if (node.noMrca() != unode.noMrca()) {
            System.err.format("** attempt to unify forest %s with non-forest %s\n",
                              node, unode);
            Taxon.backtrace();
        } else if (getTaxon(node) != null) {
			// Shouldn't happen - assigning a single source taxon to two
			//	different target taxa
			if (node.report("Already assigned to node in target:", unode))
				Taxon.backtrace();
		} else if (unode.prunedp) {
            System.err.format("** attempt to map %s to pruned node %s\n",
                              node, unode);
            Taxon.backtrace();
		} else {
            this.setAnswer(node, answer);
            if (node.name != null && unode.name != null && !node.name.equals(unode.name))
                Answer.yes(node, unode, "synonym-match", node.name).maybeLog();
            if (unode.comapped != null) {
                // Target node has already been matched to, but synonyms are OK
                if (unode.comapped != node)
                    Answer.yes(node, unode, "lumped", null).maybeLog();
            } else
                unode.comapped = node;
        }
    }

	public boolean same(Taxon node1, Taxon node2) {
		return sameness(node1, node2, true, true);
	}

	public boolean sameness(Taxon node, Taxon unode, boolean whether, boolean setp) {
        if (node == null || unode == null) return false;
        if (node.taxonomy != source) {
            System.err.format("** node1 %s not in source taxonomy\n", node);
            return false;
        }
        if (unode.taxonomy != target) {
            System.err.format("** node2 %s not in target taxonomy\n", unode);
            return false;
        }
        // start logging this name
        if (node.name != null && target.eventLogger != null)
            target.eventLogger.namesOfInterest.add(node.name);
		if (whether) {			// same
			if (getTaxon(node) != null) {
				if (getTaxon(node) != unode) {
					System.err.format("** Attempt to unify distinct taxa: %s\n", node);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                this.alignWith(node, unode, "same/ad-hoc");
                return true;
            } else return false;
		} else {				// notSame - no longer used.
            System.err.format("** notSame is no longer supported: %s\n", node);
            return false;
		}
	}


	// What was the fate of each of the nodes in this source taxonomy?

	void alignmentReport() {

        int total = 0;
        int nonamematch = 0;
        int prevented = 0;
        int corroborated = 0;

        // Could do a breakdown of matches and nonmatches by reason

        for (Taxon node : source.taxa()) {
            ++total;
            if (target.lookup(node.name) == null)
                ++nonamematch;
            else if (getTaxon(node) == null)
                ++prevented;
            else
                ++corroborated;
        }

        System.out.println("| Of " + total + " nodes in " + source.getTag() + ": " +
                           (total-nonamematch) + " with name in common, of which " + 
                           corroborated + " matched with existing, " + 
                           prevented + " blocked");
    }

    // Compute LUBs (MRCAs) and cache them in Taxon objects

    void computeLubs() {

        // The answers are already stored in the alignment.
        // Now do the lubs
        winners = fresh = grafts = outlaws = 0;
        for (Taxon root : source.roots())
            computeLubs(root);
        if (winners + fresh + grafts + outlaws > 0)
            System.out.format("| LUB match: %s graft: %s differ: %s bad: %s\n",
                              winners, fresh, grafts, outlaws);
    }

    private int winners, fresh, grafts, outlaws;

    // An important case is where a target node is incertae sedis and the source node isn't.

    // Input is in source taxonomy, return value is in target taxonomy

    Taxon computeLubs(Taxon node) {
        if (node.children == null)
            return node.lub = getTaxon(node);
        else {
            Taxon mrca = null;  // in target
            for (Taxon child : node.children) {
                Taxon a = computeLubs(child); // in target
                if (child.isPlaced()) {
                    if (a != null) {
                        if (a.noMrca()) continue;
                        a = a.parent; // in target
                        if (a.noMrca()) continue;
                        if (mrca == null)
                            mrca = a; // in target
                        else {
                            Taxon m = mrca.mrca(a);
                            if (m.noMrca()) continue;
                            mrca = m;
                        }
                    }
                }
            }

            node.lub = mrca;

            if (getTaxon(node) == null)
                ++fresh;
            else if (mrca == null)
                ++grafts;
            else if (getTaxon(node) == mrca)
                // Ideal case - mrca of children is the node itself
                ++winners;
            else {
                // Divergence across taxonomies.  MRCA of children can be
                // ancestor, descendant, or disjoint from target.
                Taxon[] div = mrca.divergence(getTaxon(node));
                if (div == null)
                    // If div == null, then either mrca descends from getTaxon(node) or the other way around.
                    ++winners;
                else if (div[0] == mrca || div[1] == getTaxon(node) || div[1].parent == getTaxon(node))
                    // Hmm... allow siblings (and cousins) to merge.  Blumeria graminis
                    ++winners;
                else {
                    if (outlaws < 10 || node.name.equals("Elaphocordyceps subsessilis") || node.name.equals("Bacillus selenitireducens"))
                        System.out.format("! %s maps by name to %s which is disjoint from children-mrca %s; they meet at %s\n",
                                          node, getTaxon(node), mrca, div[0].parent);
                    ++outlaws;
                    // OVERRIDE.
                    Answer answer = Answer.no(node, getTaxon(node), "not-same/disjoint", null);
                    answer.maybeLog();
                    setAnswer(node, answer);
                }
            }
            return getTaxon(node);
        }
    }

    // this = after, other = before

    void compareAlignments(Alignment other, String greeting) {

        System.out.format("| (%s)\n", greeting);

        // Compare this mapping to other mapping
        int count = 0, gained = 0, lost = 0, changed = 0;
        for (Taxon node : source.taxa()) {
            Answer ol = other.getAnswer(node); // old
            Answer nu = this.getAnswer(node);     // new
            if (ol != null || nu != null) {
                if (ol == null) ol = Answer.no(node, null, "compare/no-old", null);
                if (nu == null) nu = Answer.no(node, null, "compare/no-new", null); // shouldn't happen

                if (ol.isYes()) {
                    if (nu.isYes()) {
                        ++count;
                        if (ol.target == nu.target)
                            continue;
                        else
                            ++changed;
                    } else
                        ++lost;
                } else {
                    if (nu.isYes()) {
                        // New but no old
                        ++count;
                        ++gained;
                        continue;
                    } else
                        // Neither new nor old
                        continue;
                }
                // A case that's interesting enough to report.
                if (!nu.reason.equals("same/primary-name")) //too many
                    System.out.format("+ %s new-%s> %s %s, old-%s> %s %s\n",
                                      node,
                                      (nu.isYes() ? "" : "/"), nu.target, nu.reason,
                                      (ol.isYes() ? "" : "/"), ol.target, ol.reason);
            }
        }
        System.out.format("| Before %s, gained %s, lost %s, changed %s, after %s\n",
                          other.count(), gained, lost, changed, count);
    }

    int count() {
        int n = 0;
        for (Taxon node : mappings.keySet())
            if (mappings.get(node).isYes()) ++n;
        return n;
    }

    // Map source taxon to nearest available target taxon
    public Taxon bridge(Taxon node) {
        Taxon unode;
        while ((unode = getTaxon(node)) == null) {
            if (node.parent == null)
                // No bridge!  Shouldn't happen
                return node;
            node = node.parent;
        }
        return unode;
    }

    // --- to be called from jython ---
    public Taxon image(Taxon node) {
        return this.getTaxon(node);
    }

    // Trying to phase out the following - we shouldn't be caching information
    // in Taxons

	void reset() {
        this.source.reset();    // depths
        this.target.reset();

        // Flush inverse mappings from previous alignment
		for (Taxon node: this.target.taxa())
			node.comapped = null;
    }

	// 'Bracketing' logic.  Every node in the target taxonomy is
	// assigned a unique integer, ordered sequentially by a preorder
	// traversal.  Taxon inclusion across taxonomies can be determined
	// (approximately) by looking at shared names and doing a range
	// check.  This heuristic can fail in the presence of names that
	// are homonyms *across* taxonomies (e.g. a bacteria Buchnera in
	// taxonomy A and a plant Buchnera in taxonomy B).

    void assignBrackets() {
        int seq = 0;
		for (Taxon uroot : this.target.roots())
			seq = assignBrackets(uroot, seq);
		for (Taxon root : this.source.roots())
            getBracket(root, target);
	}

	static final int NO_SEQ = -8;  // for source nodes

	// Applied to a target node.  Sets seq, start, end recursively.
	int assignBrackets(Taxon unode, int seq) {
		// Only consider names in common ???
		unode.seq = seq++;
		unode.start = seq;
		if (unode.children != null)
			for (Taxon uchild : unode.children)
				seq = assignBrackets(uchild, seq);
		unode.end = seq;
        return seq;
	}

    boolean USE_ALIGNMENT = false;

	// Applied to a source node.  Sets start = smallest sequence number among all descendants,
    // end = 1 + largest sequence number among all descendants.
    // Sets seq = sequence number of corresponding target node (if any).
	void getBracket(Taxon node, Taxonomy target) {
        // would like to do  ...
        Taxon unode;
        if (USE_ALIGNMENT)
            unode = this.getTaxon(node);
        else
            unode = target.unique(node.name);
        if (unode != null)
            node.seq = unode.seq;
        else
            node.seq = NO_SEQ;
        int start = Integer.MAX_VALUE;
        int end = -1;
        if (node.children != null) {
            for (Taxon child : node.children) {
                getBracket(child, target);
                if (child.start < start) start = child.start;
                if (child.end > end) end = child.end;
                if (child.seq != NO_SEQ) {
                    if (child.seq < start) start = child.seq;
                    if (child.seq > end) end = child.seq;
                }
            }
        }
        node.start = start;
        node.end = end+1;
	}

	// Look for a member of the source taxon that's also a member of the target taxon.
    // Compare: ConflictAnalysis.intersects()
	static Taxon witness(Taxon node, Taxon unode) { // assumes is subsumed by unode
		if (node.start >= unode.end || node.end <= unode.start) // Nonoverlapping => lose
			return null;
		else if (node.children != null) { // it *will* be nonnull actually
			for (Taxon child : node.children)
				if (child.seq != NO_SEQ && (child.seq >= unode.start && child.seq < unode.end))
					return child;
				else {
					Taxon a = witness(child, unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

	// Look for a member of the source taxon that's not a member of the target taxon,
	// but is a member of some other target taxon.
	static Taxon antiwitness(Taxon node, Taxon unode) {
		if (node.start >= unode.start && node.end <= unode.end)
			return null;
		else if (node.children != null) { // it *will* be nonnull actually
			for (Taxon child : node.children)
				if (child.seq != NO_SEQ && (child.seq < unode.start || child.seq >= unode.end))
					return child;
				else {
					Taxon a = antiwitness(child, unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

}
