package org.opentreeoflife.smasher;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
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
import org.opentreeoflife.taxa.Synonym;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

public class AlignmentByName extends Alignment {

    AlignmentByName(SourceTaxonomy source, UnionTaxonomy union) {
        super(source, union);
    }

    AlignmentByName(Alignment a) {
        super(a);
    }

    private int debug = 0;

    // this is pretty gross. needs to be moved & rewritten.
    // method is invoked from python.

    public void align() {

        Alignment old = null;
        AlignmentByName neu2 = null;

        if (debug >= 1) {
            Alignment basis = new Alignment(this);    // copy ad-hoc mappings
            System.out.format("| Basis: %s mappings\n", basis.size());
            old = new ComplicatedAlignment(basis);
            old.align();
            if (debug >= 2) {
                Alignment old2 = new ComplicatedAlignment(basis);
                old2.align();
                old2.compareAlignments(old, "comparing old to old");
                neu2 = new AlignmentByName(basis);
            }
        }

        this.newAlign();

        if (debug >= 1) {
            this.compareAlignments(old, "comparing old to new");
            if (debug >= 2) {
                neu2.newAlign();
                neu2.compareAlignments(this, "comparing new to new");
            }
        }
    }

    public void newAlign() {
        union.eventlogger.resetEvents();
        this.reset();          // depths, brackets, comapped
        this.markDivisions(source);

        for (Taxon node : source.taxa()) {
            if (getTaxon(node) == null) {
                Answer a = newAlign(node);
                if (a != null) {
                    if (a.isYes())
                        alignWith(node, a.target, a);
                    else
                        this.setAnswer(node, a);
                }
            }
        }
        union.eventlogger.eventsReport("| ");

        // Report on how well the merge went.
        this.alignmentReport();
    }

    // Alignment - new method

    public Answer newAlign(Taxon node) {
        Map<Taxon, String> candidateMap = getCandidates(node);
        Set<Taxon> candidates = new HashSet<Taxon>(candidateMap.keySet());

        if (candidates.size() == 0)
            return null;
        if (candidates.size() > 1)
            union.eventlogger.namesOfInterest.add(node.name);
        for (Taxon candidate : candidates)
            Answer.noinfo(node, candidate, "candidate", candidateMap.get(candidate));

        int max;
        Answer anyAnswer = null;
        Taxon anyCandidate = null;
        String deciding = "no-choice";
        for (Criterion criterion : Criterion.criteria) {
            Set<Taxon> winners = new HashSet<Taxon>();
            max = -100;
            for (Taxon unode : candidates) {
                Answer a = criterion.assess(node, unode);
                a.maybeLog(union);
                if (a.value >= max) {
                    if (a.value > max) {
                        max = a.value;
                        winners = new HashSet<Taxon>();
                    }
                    winners.add(unode);
                    anyCandidate = unode;
                    anyAnswer = a;
                }
            }
            // Accept a yes answer
            if (max > Answer.DUNNO && winners.size() == 1)
                return anyAnswer;

            // Accept a no answer
            if (max < Answer.DUNNO) {
                if (winners.size() == 1)
                    return anyAnswer;
                else {
                    Answer no = new Answer(node, null, max, "all-candidates-rejected", null);
                    no.maybeLog(union);
                    return no;
                }
            }

            // NOINFO
            if (winners.size() == 1 && candidates.size() > 1)
                deciding = criterion.toString();

            candidates = winners;
        }
        // No yeses, no nos
        if (candidates.size() == 1) {
            Answer yes = Answer.yes(node, anyCandidate, deciding, null);
            yes.maybeLog(union);
            return yes;
        } else {

            // Dump candidates & mode info
            System.out.format("? %s -> ", node);
            for (Taxon c : candidates)
                System.out.format(" %s[%s]", c, candidateMap.get(c));
            System.out.println();

            // avoid creating yet another homonym
            Answer no = Answer.no(node, null, "ambiguous", null);
            no.maybeLog(union);
            return no;
        }
    }

    private static boolean allowSynonymSynonymMatches = false;

    // Given a source taxonomy return, return a set of target (union)
    // taxonomy nodes that it might plausibly match

    Map<Taxon, String> getCandidates(Taxon node) {
        Map<Taxon, String> candidateMap = new HashMap<Taxon, String>();
        getCandidatesViaName(node, "C", candidateMap); // canonical
        for (Synonym syn : node.getSynonyms())
            getCandidatesViaName(syn, "S", candidateMap);
        return candidateMap;
    }

    Map<Taxon, String> getCandidatesViaName(Node node, String mode, Map<Taxon, String> candidateMap) {
        if (node.name != null) {
            Collection<Node> unodes = union.lookup(node.name);
            if (unodes != null)
                for (Node unode : unodes)
                    addCandidate(candidateMap, unode, mode);
        }
        // Add nodes that share a qid with this one (for idsource alignment)
        if (node.sourceIds != null) {
            String mode2 = mode + "I";
            for (QualifiedId qid : node.sourceIds) {
                Node unode = union.lookupQid(qid);
                if (unode != null) {
                    Taxon utaxon = unode.taxon();
                    if (!utaxon.prunedp) {
                        String xmode = mode2 + ((utaxon.sourceIds != null &&
                                                 utaxon.sourceIds.get(0).equals(qid)) ?
                                                "I" : "J");
                        addCandidate(candidateMap, unode, xmode);
                    }
                }
                mode2 = mode + "J";
            }
        }
        return candidateMap;
    }

    void addCandidate(Map<Taxon, String> candidateMap, Node unode, String mode) {
        Taxon utaxon = unode.taxon();
        mode = mode + (unode instanceof Taxon ? "C" : "S");

        String modes = candidateMap.get(utaxon);
        if (modes == null)
            modes = mode;
        else
            modes = modes + " " + mode;
        candidateMap.put(utaxon, modes);
    }

	int nextSequenceNumber = 0;

	public void reset() {
        this.source.reset();    // depths
        this.union.reset();

        // Flush inverse mappings from previous alignment
		for (Taxon node: this.union.taxa())
			node.comapped = null;

        for (Taxon node : this.source.taxa())
            node.seq = NOT_SET;

		this.nextSequenceNumber = 0;
		for (Taxon root : this.union.roots())
			assignBrackets(root);

        // unnecessary?
        // this.source.inferFlags(); 
        // this.union.inferFlags(); 
	}

	// 'Bracketing' logic.  Every node in the union taxonomy is
	// assigned a unique integer, ordered sequentially by a preorder
	// traversal.  Taxon inclusion across taxonomies can be determined
	// (approximately) by looking at shared names and doing a range check.
    // This heuristic can fail in the presence of names that are homonyms
    // across taxonomies.

	static final int NOT_SET = -7; // for source nodes
	static final int NO_SEQ = -8;  // for source nodes

	// Applied to a union node.  Sets seq, start, end recursively.
	void assignBrackets(Taxon node) {
		// Only consider names in common ???
		node.seq = nextSequenceNumber++;
		node.start = nextSequenceNumber;
		if (node.children != null)
			for (Taxon child : node.children)
				assignBrackets(child);
		node.end = nextSequenceNumber;
	}

	// Applied to a source node.  Sets start = smallest sequence number among all descendants,
    // end = 1 + largest sequence number among all descendants.
    // Sets seq = sequence number of corresponding union node (if any).
	static void getBracket(Taxon node, Taxonomy union) {
		if (node.seq == NOT_SET) {
			Taxon unode = union.unique(node.name);
			if (unode != null)
				node.seq = unode.seq;
            else
                node.seq = NO_SEQ;
            int start = Integer.MAX_VALUE;
            int end = -1;
			if (node.children != null) {
				for (Taxon child : node.children) {
					getBracket(child, union);
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
	}

	// Look for a member of the source taxon that's also a member of the union taxon.
	static Taxon witness(Taxon node, Taxon unode) { // assumes is subsumed by unode
		getBracket(node, unode.taxonomy);
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

	// Look for a member of the source taxon that's not a member of the union taxon,
	// but is a member of some other union taxon.
	static Taxon antiwitness(Taxon node, Taxon unode) {
		getBracket(node, unode.taxonomy);
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

    public static void testWitness() throws Exception {
        SourceTaxonomy t1 = Taxonomy.getTaxonomy("(a,b,c)d", "z1");
        SourceTaxonomy t2 = Taxonomy.getTaxonomy("(a,b,c)d", "z2");
        UnionTaxonomy u = new UnionTaxonomy("u");
        u.absorb(t1);
        u.absorb(t2);
        System.out.println(witness(t1.taxon("d"), u.taxon("d")));
    }


}
