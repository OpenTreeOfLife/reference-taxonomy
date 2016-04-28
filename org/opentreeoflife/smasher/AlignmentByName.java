package org.opentreeoflife.smasher;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

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

// At the end of this, every source node should have its .answer field set.

public class AlignmentByName extends Alignment {

	Taxonomy source, union;

    // Return the node that this one maps to under this alignment, or null
    Answer answer(Taxon subject) {
        if (subject.answer != null)
            return subject.answer;
        else if (subject.mapped != null)
            // shouldn't happen
            return Answer.yes(subject, subject.mapped, "mapped-should-not-happen", null);
        else
            return null;
    }

    private int losers, winners, fresh, grafts, outlaws;

    void cacheInSourceNodes() {
        union.alignWith(source.forest, union.forest, "align-forests");

        // The answers are already stored in .answer of each node
        // Now do the .lubs
        losers = winners = fresh = grafts = outlaws = 0;
        for (Taxon root : source.roots())
            cacheLubs(root);
        if (losers + winners + fresh + grafts + outlaws > 0)
            System.out.format("| LUB match: %s mismatch: %s graft: %s differ: %s bad: %s\n",
                              winners, losers, fresh, grafts, outlaws);
    }

    // An important case is where a union node is incertae sedis and the source node isn't.

    // Input is in source taxonomy, return value is in union taxonomy

    Taxon cacheLubs(Taxon node) {
        if (node.children == null)
            return node.lub = node.mapped();
        else {
            Taxon mrca = null;  // in union
            for (Taxon child : node.children) {
                if (!(child.taxonomy instanceof SourceTaxonomy))
                    System.out.format("** Child in wrong taxonomy: %s\n", child);
                Taxon a = cacheLubs(child); // in union
                if (a != null && !(a.taxonomy instanceof UnionTaxonomy))
                    System.out.format("** Lub in wrong taxonomy: %s\n", a);
                if (child.isPlaced()) {
                    if (a != null) {
                        if (a.noMrca()) continue;
                        a = a.parent; // in union
                        if (!(a.taxonomy instanceof UnionTaxonomy))
                            System.out.format("** Lub in wrong taxonomy: %s\n", a);
                        if (a.noMrca()) continue;
                        if (mrca == null)
                            mrca = a; // in union
                        else {
                            Taxon m = mrca.mrca(a);
                            if (!(m.taxonomy instanceof UnionTaxonomy))
                                System.out.format("** Mrca in wrong taxonomy: %s\n", m);

                            if (m.noMrca()) continue;
                            if (false) {
                                Taxon div1 = mrca.getDivision();
                                Taxon div2 = a.getDivision();
                                if (div1 != div2 && div1.divergence(div2) != null)
                                    // 2015-07-23 this happens about 300 times
                                    System.out.format("! Children of %s are in disjoint divisions (%s in %s + %s in %s)\n",
                                                      node, mrca, div1, a, div2);
                            }
                            mrca = m;
                        }
                    }
                }
            }

            node.lub = mrca;

            // Reporting
            if (node.mapped == null) {
                ++fresh;
                return null;
            } else {
                if (mrca == node.mapped)
                    ++winners;
                else if (mrca == null)
                    ++grafts;
                else {
                    // divergence across taxonomies
                    Taxon[] div = mrca.divergence(node.mapped);
                    if (div != null && !div[0].isRoot()
                        // Hmm... allow siblings (and cousins) to merge.  Blumeria graminis
                        && (div[0] != mrca && div[1] != node.mapped)) {
                        if (outlaws < 50)
                            System.out.format("! %s maps by name to %s which is disjoint from mrca %s; they meet at %s\n",
                                              node, node.mapped, mrca, div[0].parent);
                        ++outlaws;
                        // OVERRIDE.
                        node.answer = Answer.no(node, node.mapped, "not-same/disjoint", null);
                        node.answer.maybeLog();
                        node.mapped = null;
                    } else
                        ++losers;
                }
                if (node.mapped != null && !(node.mapped.taxonomy instanceof UnionTaxonomy))
                    System.out.format("** Mapped to wrong taxonomy: %s -> %s\n", node, node.mapped);
                return node.mapped;
            }
        }
    }

	int nextSequenceNumber = 0;

	public void reset() {
		this.nextSequenceNumber = 0;
        this.source.reset();    // depths and comapped
        this.union.reset();

        for (Taxon node : this.source.taxa())
            node.seq = NOT_SET;

		for (Taxon root : this.union.roots())
			assignBrackets(root);

        // unnecessary?
        this.source.inferFlags(); 
        this.union.inferFlags(); 

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
				node.seq = unode.seq; // Else leave seq as NOT_SET
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

	// Cheaper test, without seeking a witness
	boolean isNotSubsumedBy(Taxon node, Taxon unode) {
		getBracket(node, unode.taxonomy);
		return node.start < unode.start || node.end > unode.end; // spills out?
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
        SourceTaxonomy t1 = Taxonomy.getTaxonomy("(a,b,c)d");
        SourceTaxonomy t2 = Taxonomy.getTaxonomy("(a,b,c)d");
        UnionTaxonomy u = new UnionTaxonomy();
        u.mergeIn(t1);
        u.mergeIn(t2);
        System.out.println(witness(t1.taxon("d"), u.taxon("d")));
    }


    AlignmentByName(SourceTaxonomy source, UnionTaxonomy union) {

        this.source = source;
        this.union = union;

        this.reset();          // depths, brackets, comapped

        Criterion[] criteria = Criterion.criteria;
		if (source.rootCount() > 0) {

			union.eventlogger.resetEvents();
			System.out.println("--- Mapping " + source.getTag() + " into union ---");

			int beforeCount = union.numberOfNames();

			union.markDivisionsUnion(source);

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
                        new Matrix(name, nodes, unodes).run(criteria);
                    }
				}
			}
			System.out.println("| Names in common: " + incommon);

			union.eventlogger.eventsReport("| ");

			// Report on how well the merge went.
			Alignment.alignmentReport(source, union);
		}
    }

    // For each source node, consider all possible union nodes it might map to
    // TBD: Exclude nodes that have 'prunedp' flag set

    class Matrix {

        String name;
        List<Node> nodes;
        List<Node> unodes;
        int m;
        int n;
        Answer[][] suppressp;

        Matrix(String name, List<Node> nodes, List<Node> unodes) {
            this.name = name;
            this.nodes = nodes;
            this.unodes = unodes;
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
                for (int j = 0; j < n; ++j) {  // Find a union node to map it to...
                    if (suppressp[i][j] != null) continue;
                    Taxon y = unodes.get(j).taxon();
                    Answer z = criterion.assess(x, y);
                    if (z.value == Answer.DUNNO)
                        continue;
                    z.log(y.taxonomy);
                    if (z.value < Answer.DUNNO) {
                        suppressp[i][j] = z;
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
            for (int i = 0; i < m; ++i) // iterate over source nodes
                // Don't assign a single source node to two union nodes...
                if (uniq[i] >= 0) {
                    int j = uniq[i];
                    // Avoid assigning two source nodes to the same union node (synonym creation)...
                    if (uuniq[j] >= 0 && suppressp[i][j] == null) {
                        Taxon x = nodes.get(i).taxon(); // == uuniq[j]
                        Taxon y = unodes.get(j).taxon();

                        // Block out column, to prevent other source nodes from mapping to the same union node
                        if (false)
                        for (int ii = 0; ii < m; ++ii)
                            if (ii != i && suppressp[ii][j] == null)
                                suppressp[ii][j] = Answer.no(nodes.get(ii).taxon(),
                                                             y,
                                                             "excluded(" + criterion.toString() +")",
                                                             x.getQualifiedId().toString());
                        // Block out row, to prevent this source node from mapping to multiple union nodes (!!??)
                        for (int jj = 0; jj < n; ++jj)
                            if (jj != j && suppressp[i][jj] == null)
                                // This case seems to never happen
                                suppressp[i][jj] = Answer.no(x,
                                                             unodes.get(jj).taxon(),
                                                             "coexcluded(" + criterion.toString() + ")",
                                                             null);

                        Answer a = answer[i];
                        if (x.mapped == y)
                            ;   // multiple criteria met uniquely
                        else if (x.mapped != null) {
                            // This case doesn't happen
                            a = Answer.no(x, y, "lost-race-to-source(" + criterion.toString() + ")",
                                          (y.getSourceIdsString() + " lost to " +
                                           x.mapped.getSourceIdsString()));
                        } else if (x.mapped == y) {
                            ;
                        } else if (x.answer != null) {
                            Answer.no(x, y, "blocked-because-" + x.answer.reason, null).maybeLog();
                            // System.out.format("| Blocked from mapping %s to %s because %s\n", x, y, x.answer.reason);
                        } else if (false && y.comapped != null && x.children == null) {
                            // There was already a mapping because of a higher-quality criterion.
                            // Keeping this mapping could cause
                            // trouble, like introduction of
                            // extraneous 'extinct' flags...
                            x.answer = Answer.no(x, y, "redundant", null);
                            x.answer.maybeLog();
                        } else {
                            y.taxonomy.alignWith(x, y, a); // sets .mapped, .answer
                        }
                        suppressp[i][j] = a;
                    }
                }
        }

        // in x[i][j] i specifies the row and j specifies the column

        // Record reasons for mapping failure - for each unmapped source node, why didn't it map?
        void postmortem() {
            for (int i = 0; i < m; ++i) {
                Taxon node = nodes.get(i).taxon();
                // Suppress synonyms
                if (node.mapped == null) {
                    int alts = 0;	 // how many union nodes might we have gone to?
                    int altj = -1;
                    for (int j = 0; j < n; ++j)
                        if (suppressp[i][j] == null
                            // && unodes.get(j).comapped == null
                            ) { ++alts; altj = j; }
                    UnionTaxonomy union = (UnionTaxonomy)unodes.get(0).taxonomy;
                    Answer explanation; // Always gets set
                    if (alts == 1) {
                        // There must be multiple source nodes i1, i2, ... competing
                        // for this one union node.	 Merging them is (probably) fine.
                        String w = null;
                        for (int ii = 0; ii < m; ++ii)
                            if (suppressp[ii][altj] == null) {
                                Taxon rival = nodes.get(ii).taxon();	// in source taxonomy or idsource
                                if (rival == node) continue;
                                // if (rival.mapped == null) continue;	// ???
                                QualifiedId qid = rival.getQualifiedId();
                                if (w == null) w = qid.toString();
                                else w += ("," + qid.toString());
                            }
                        explanation = Answer.noinfo(node, unodes.get(altj).taxon(), "unresolved/contentious", w);
                    } else if (alts > 1) {
                        // Multiple union nodes to which this source can map... no way to tell
                        // ids have not been assigned yet
                        //	  for (int j = 0; j < n; ++j) others.add(unodes.get(j).taxon().id);
                        String w = null;
                        for (int j = 0; j < n; ++j)
                            if (suppressp[i][j] == null) {
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
                        if (n == 1) {
                            explanation = suppressp[i][0];
                            if (explanation.reason.equals("not-same/weak-division"))
                                union.weakLog.add(explanation);
                        } else {
                            for (int j = 0; j < n; ++j)
                                if (suppressp[i][j] != null) // how does this happen?
                                    suppressp[i][j].log(union);
                            String kludge = null;
                            int badness = -100;
                            for (int j = 0; j < n; ++j) {
                                Answer a = suppressp[i][j];
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
                    node.answer = explanation;  
                }
            }
        }
    }

}

