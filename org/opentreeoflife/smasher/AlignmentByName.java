package org.opentreeoflife.smasher;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.io.PrintStream;
import java.io.IOException;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;

public class AlignmentByName extends Alignment {

    public AlignmentByName(Taxonomy source, Taxonomy target) {
        super(source, target);
    }

    AlignmentByName(Alignment a) {
        super(a);
    }

    // this is pretty gross. needs to be moved & rewritten.
    // method is invoked from python.

    public void reallyAlign() {
        alignQuasiTips();
        internalp = true;
        assignBrackets(this);
        for (Taxon root : source.roots())
            alignInternal(root);
    }

    // align internal nodes (above quasi-tips)
    void alignInternal(Taxon node) {
        for (Taxon child : node.getChildren())
            alignInternal(child);
        alignTaxon(node, internalHeuristics);
    }


	// Ancient 'bracketing' logic.  Every node in the target taxonomy is
	// assigned a unique integer, ordered sequentially by a preorder
	// traversal.  Taxon inclusion across taxonomies can be determined
	// (approximately) by looking at shared names and doing a range
	// check.  This heuristic can fail in the presence of names that
	// are homonyms *across* taxonomies (e.g. a bacteria Buchnera in
	// taxonomy A and a plant Buchnera in taxonomy B).

    static void assignBrackets(Alignment a) {
        int seq = 0;
		for (Taxon uroot : a.target.roots())
			seq = assignBrackets(uroot, seq);
		for (Taxon root : a.source.roots())
            getBracket(root, a);
	}

	static final int NO_SEQ = -8;  // for source nodes

	// Applied to a target node.  Sets seq, start, end recursively.
	static int assignBrackets(Taxon unode, int seq) {
		// Only consider names in common ???
		unode.seq = seq++;
		unode.start = seq;
		if (unode.children != null)
			for (Taxon uchild : unode.children)
				seq = assignBrackets(uchild, seq);
		unode.end = seq;
        return seq;
	}

    static boolean USE_ALIGNMENT = true;

	// Applied to a source node.  Sets start = smallest sequence number among all descendants,
    // end = 1 + largest sequence number among all descendants.
    // Sets seq = sequence number of corresponding target node (if any).
	static void getBracket(Taxon node, Alignment a) {
        Taxonomy target = a.target;
        // would like to do  ...
        Taxon unode;
        if (USE_ALIGNMENT)
            unode = a.getTaxon(node);
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
                getBracket(child, a);
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

    // same as quasi-tip heuristics, but with subsumption added

	static Heuristic[] internalHeuristics = {
		Heuristic.disjointDivisions,      // fail if disjoint divisions
        Heuristic.disparateRanks,         // fail if incompatible ranks
		Heuristic.lineage,                // prefer shared lineage
        Heuristic.subsumption,            // prefer shared membership
        Heuristic.sameDivisionPreferred,  // prefer same division
        Heuristic.byPrimaryName,          // prefer same name
		Heuristic.sameSourceId,           // prefer same source id
		Heuristic.anySourceId,            // prefer candidate with any source id the same
    };

}
