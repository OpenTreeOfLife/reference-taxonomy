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

    private boolean debugp = false;

    // this is pretty gross. needs to be moved & rewritten.

    public void align() {
        Alignment basis = new Alignment(this);
        System.out.format("| Basis: %s mappings\n", basis.size());

        AlignmentByName old = new AlignmentByName(basis);
        old.oldAlign();

        AlignmentByName neu2 = null;
        if (debugp) {
            AlignmentByName old2 = new AlignmentByName(basis);
            old2.oldAlign();
            old2.compareAlignments(old, "comparing old to old");
            neu2 = new AlignmentByName(basis);
        }

        this.newAlign();
        this.compareAlignments(old, "comparing old to new");

        if (debugp) {
            neu2.newAlign();
            neu2.compareAlignments(this, "comparing new to new");
        }
    }

    public void newAlign() {
        union.eventlogger.resetEvents();
        this.reset();          // depths, brackets, comapped
        this.alignWith(source.forest, union.forest, "align-forests");
        this.markDivisions(source);

        for (Taxon node : source.taxa()) {
            if (getTaxon(node) == null) {
                Answer a = newAlign(node);
                if (a.isYes())
                    alignWith(node, a.target, a);
                else
                    this.setAnswer(node, a);
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
            return Answer.heckNo(node, null, "no-candidates", null);

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
                    Answer no = Answer.no(node, null, "all-candidates-rejected", null);
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

    // Alignment by name - old method

    public void oldAlign() {
        this.reset();          // depths, brackets, comapped
        this.alignWith(source.forest, union.forest, "align-forests");
        this.markDivisions(source);
        union.eventlogger.resetEvents();

		if (source.rootCount() > 0) {

            Criterion[] criteria = Criterion.oldCriteria;
			System.out.println("--- Mapping " + source.getTag() + " to union ---");

			int beforeCount = union.numberOfNames();

			Set<String> seen = new HashSet<String>();
			List<String> todo = new ArrayList<String>();

			// Consider all matches where names coincide.
			// When matching P homs to Q homs, we get PQ choices of which
			// possibility to attempt first.
			// Treat each name separately.

			// Be careful about the order in which names are
			// processed, so as to make the 'races' come out the right
			// way.	 This is a kludge.

			// primary / primary
			for (Taxon node : source.taxa())
				if (!seen.contains(node.name)) {
					List<Node> unodes = union.lookup(node.name);
					if (unodes != null)
						for (Node unode : unodes)
							if (unode.taxonNameIs(node.name)) {
                                seen.add(node.name);
                                todo.add(node.name);
                                break;
                            }
				}
			// primary / synonym
			for (Taxon unode : union.taxa())
				if (source.lookup(unode.name) != null &&
					!seen.contains(unode.name))
					{ seen.add(unode.name); todo.add(unode.name); }
			// synonym / primary    -- maybe disallow !?
			for (Taxon node : source.taxa())
				if (union.lookup(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// synonym / synonym probably just generates noise

			int incommon = 0;
			int homcount = 0;
			for (String name : todo) {
				List<Node> unodes = union.lookup(name);
				if (unodes != null) {
					++incommon;
					List<Node> nodes = source.lookup(name);
                    if (nodes != null) {
                        if (false &&
                            (((nodes.size() > 1 || unodes.size() > 1) && (++homcount % 1000 == 0))))
                            System.out.format("| Mapping: %s %s*%s (name #%s)\n", name, nodes.size(), unodes.size(), incommon);
                        new Matrix(name, nodes, unodes, this).run(criteria);
                    }
				}
			}
			System.out.println("| Names in common: " + incommon);

			union.eventlogger.eventsReport("|? ");

			// Report on how well the merge went.
			this.alignmentReport();
		}
    }

    // For each source node, consider all possible union nodes it might map to

    class Matrix {

        String name;
        List<Node> nodes;
        List<Node> unodes;
        Alignment alignment;
        int m;
        int n;
        Answer[][] suppressp;

        Matrix(String name, List<Node> nodes, List<Node> unodes, Alignment alignment) {
            this.name = name;
            this.nodes = nodes;
            this.unodes = unodes;
            this.alignment = alignment;
            m = nodes.size();
            n = unodes.size();
            if (m*n > 50)
                System.out.format("!! Badly homonymic: %s %s in source, %s in union\n", name, m, n);
        }

        void clear() {
            suppressp = new Answer[m][];
            for (int i = 0; i < m; ++i)
                suppressp[i] = new Answer[n];
        }

        // Compare every node to every other node, according to a list of criteria.
        void run(Criterion[] criteria) {

            clear();

            // Log the fact that there are synonyms involved in these comparisons
            if (false)
                for (Node nodenode : nodes) {
                    Taxon node = nodenode.taxon();
                    if (!node.name.equals(name)) {
                        Taxon unode = unodes.get(0).taxon();
                        // node.markEvent("synonym(s)");   ?
                        Answer.noinfo(node, unode, "synonym(s)", node.name).maybeLog();
                        break;
                    }
                }

            for (Criterion criterion : criteria)
                run(criterion);

            // see if any source node remains unassigned (ties or blockage)
            postmortem();
            suppressp = null;  //GC
        }

        // i, m,  node
        // j, n, unode

        void run(Criterion criterion) {
            int m = nodes.size();
            int n = unodes.size();
            int[] uniq = new int[m];	// union nodes uniquely assigned to each source node
            for (int i = 0; i < m; ++i) uniq[i] = -1;
            int[] uuniq = new int[n];	// source nodes uniquely assigned to each union node
            for (int j = 0; j < n; ++j) uuniq[j] = -1;
            Answer[] answer = new Answer[m];
            Answer[] uanswer = new Answer[n];

            for (int i = 0; i < m; ++i) { // For each source node...
                Taxon x = nodes.get(i).taxon();
                Answer[] suppress_i = suppressp[i];
                for (int j = 0; j < n; ++j) {  // Find a union node to map it to...
                    if (suppress_i[j] != null) continue;
                    Taxon y = unodes.get(j).taxon();
                    Answer z = criterion.assess(x, y);
                    if (z.value == Answer.DUNNO)
                        continue;
                    z.log(y.taxonomy);
                    if (z.value < Answer.DUNNO) {
                        suppress_i[j] = z;
                        continue;
                    }
                    if (answer[i] == null || z.value > answer[i].value) {
                        uniq[i] = j;
                        answer[i] = z;
                    } else if (z.value == answer[i].value)
                        uniq[i] = -2;

                    if (uanswer[j] == null || z.value > uanswer[j].value) {
                        uuniq[j] = i;
                        uanswer[j] = z;
                    } else if (z.value == uanswer[j].value)
                        uuniq[j] = -2;
                }
            }
            for (int i = 0; i < m; ++i) { // iterate over source nodes
                // Don't assign a single source node to two union nodes...
                Answer[] suppress_i = suppressp[i];
                if (uniq[i] >= 0) {
                    int j = uniq[i];
                    // Avoid assigning two source nodes to the same union node (synonym creation)...
                    if (uuniq[j] >= 0 && suppress_i[j] == null) {
                        Taxon x = nodes.get(i).taxon(); // == uuniq[j]
                        Taxon y = unodes.get(j).taxon();

                        // See versions of this code from before 28 June 2016 for
                        // interesting logic that I excised

                        Answer a = answer[i];
                        Taxon ux = alignment.getTaxon(x);
                        if (ux == y)
                            ;   // multiple criteria met uniquely
                        else if (ux != null) {
                            // This case doesn't happen
                            a = Answer.no(x, y, "lost-race-to-source(" + criterion.toString() + ")",
                                          (y.getSourceIdsString() + " lost to " +
                                           ux.getSourceIdsString()));
                        } else if (alignment.getAnswer(x) != null) {
                            Answer.no(x, y, "blocked-because-" + alignment.getAnswer(x).reason, null).maybeLog();
                            // System.out.format("| Blocked from mapping %s to %s because %s\n", x, y, x.answer.reason);
                        } else if (false && y.comapped != null && x.children == null) {
                            // There was already a mapping because of a higher-quality criterion.
                            // Keeping this mapping could cause
                            // trouble, like introduction of
                            // extraneous 'extinct' flags...
                            Answer no = Answer.no(x, y, "redundant", null);
                            alignment.setAnswer(x, no);
                            no.maybeLog();
                        } else {
                            this.alignment.alignWith(x, y, a);
                        }
                        suppress_i[j] = a;
                    }
                }
            }
        }

        // in x[i][j] i specifies the row and j specifies the column

        // Record reasons for mapping failure - for each unmapped source node, why didn't it map?
        void postmortem() {
            UnionTaxonomy union = (UnionTaxonomy)unodes.get(0).taxonomy;
            for (int i = 0; i < m; ++i) {
                Taxon node = nodes.get(i).taxon();
                Answer[] suppress_i = suppressp[i];
                // Suppress synonyms
                if (alignment.getTaxon(node) == null) {
                    int alts = 0;	 // how many union nodes might we have gone to?
                    int altj = -1;
                    for (int j = 0; j < n; ++j)
                        if (suppress_i[j] == null
                            // && unodes.get(j).comapped == null
                            ) { ++alts; altj = j; }
                    Answer explanation; // Always gets set
                    if (alts == 1) {
                        // There must be multiple source nodes i1, i2, ... competing
                        // for this one union node.	 Merging them is (probably) fine.
                        String w = null;
                        for (int ii = 0; ii < m; ++ii)
                            if (suppressp[ii][altj] == null) {
                                Taxon rival = nodes.get(ii).taxon();	// in source taxonomy or idsource
                                if (rival == node) continue;
                                QualifiedId qid = rival.getQualifiedId();
                                if (w == null) w = qid.toString();
                                else w += ("," + qid.toString());
                            }
                        explanation = Answer.noinfo(node, unodes.get(altj).taxon(), "unresolved/lumping", w);
                    } else if (alts > 1) {
                        // Multiple union nodes to which this source can map... no way to tell
                        // ids have not been assigned yet
                        //	  for (int j = 0; j < n; ++j) others.add(unodes.get(j).taxon().id);
                        String w = null;
                        for (int j = 0; j < n; ++j)
                            if (suppress_i[j] == null) {
                                Taxon candidate = unodes.get(j).taxon();	// in union taxonomy
                                // if (candidate.comapped == null) continue;  // ???
                                if (candidate.sourceIds == null)
                                    ;
                                else {
                                    QualifiedId qid = candidate.sourceIds.get(0);
                                    if (w == null) w = qid.toString();
                                    else w += ("," + qid.toString());
                                }
                            }
                        explanation = Answer.noinfo(node, null, "unresolved/ambiguous", w);
                    } else {
                        // Important case, mapping blocked, maybe a brand new taxon.  Give gory details.
                        // Iterate through the union nodes for this name that we didn't map to
                        // and collect all the reasons.
                        /*
                        if (n == 1) {
                            explanation = suppress_i[0];
                            if (explanation.reason.equals("not-same/weak-division"))
                                union.weakLog.add(explanation);
                        } else
                        */
                            {
                            for (int j = 0; j < n; ++j)
                                if (suppress_i[j] != null) // how does this happen?
                                    suppress_i[j].log(union);
                            String kludge = null;
                            int badness = -100;
                            for (int j = 0; j < n; ++j) {
                                Answer a = suppress_i[j];
                                if (a == null)
                                    continue;
                                if (a.value > badness)
                                    badness = a.value;
                                if (kludge == null)
                                    kludge = a.reason;
                                else if (j < 5)
                                    kludge = kludge + "," + a.reason;
                                else if (j == 5)
                                    kludge = kludge + ",...";
                            }
                            if (kludge == null) {
                                System.err.println("!? No reasons: " + node);
                                explanation = Answer.NOINFO;
                            } else
                                explanation = new Answer(node, null, badness, "unresolved/blocked", kludge);
                        }
                    }
                    explanation.maybeLog(union);
                    // remember, source could be either gbif or idsource
                    alignment.setAnswer(node, explanation);  
                }
            }
        }
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
