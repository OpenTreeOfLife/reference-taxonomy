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

// At the end of this, every source node should have its .answer field set.

public class AlignmentByName extends Alignment {

	Taxonomy source, union;

    // Return the node that this one maps to under this alignment, or null
    Answer answer(Taxon subject) {
        if (subject.answer != null)
            return subject.answer;
        else if (subject.mapped != null)
            // shouldn't happen
            return Answer.yes(subject, subject.mapped, "mapped", null);
        else
            return null;
    }

    private int losers, winners, fresh, grafts, outlaws;

    void cacheInSourceNodes() {
        source.forest.alignWith(union.forest, "align-forests");

        // The answers are already stored in .answer of each node
        // Now do the .lubs
        losers = winners = fresh = grafts = outlaws = 0;
        for (Taxon root : source.roots())
            cacheLubs(root);
        if (losers + winners + fresh + grafts + outlaws > 0)
            System.out.format("| LUB match: %s mismatch: %s graft: %s differ: %s bad: %s\n",
                              winners, losers, fresh, grafts, outlaws);
    }

    // The important case is where a union node is incertae sedis and the source node isn't.

    Taxon cacheLubs(Taxon node) {
        if (node.children == null)
            return node.lub = node.mapped();
        else {
            Taxon mrca = null;
            for (Taxon child : node.children) {
                Taxon a = cacheLubs(child);
                if (child.isPlaced()) {
                    if (a != null) {
                        if (a.noMrca()) continue;
                        a = a.parent;
                        if (a.noMrca()) continue;
                        if (mrca == null)
                            mrca = a;
                        else {
                            Taxon m = mrca.mrca(a);
                            if (m.noMrca()) continue;
                            Taxon div1 = mrca.getDivision();
                            Taxon div2 = a.getDivision();
                            if (div1 != div2 && div1.divergence(div2) != null)
                                System.out.format("** Children of %s are in disjoint divisions (%s in %s + %s in %s)\n",
                                                  node, mrca, div1, a, div2);
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
                    Taxon[] div = mrca.divergence(node.mapped);
                    if (div != null && !div[0].isRoot()
                        // Hmm... allow siblings (and cousins) to merge.  Blumeria graminis
                        && (div[0] != mrca && div[1] != node.mapped)) {
                        if (outlaws < 50)
                            System.out.format("** %s maps by name to %s which is disjoint from mrca %s; they meet at %s\n",
                                              node, node.mapped, mrca, div[0].parent);
                        ++outlaws;
                        // OVERRIDE.
                        node.answer = Answer.no(node, node.mapped, "not-same/disjoint", null);
                        node.mapped = null;
                    } else
                        ++losers;
                }
                return node.mapped;
            }
        }
    }

	int nextSequenceNumber = 0;

	public void reset() {
		this.nextSequenceNumber = 0;
        this.source.reset();
        this.union.reset();
		for (Taxon root: union.roots())
			// Prepare for subsumption checks
            resetBrackets(root);
		for (Taxon root: union.roots())
			assignBrackets(root);

	}

	// 'Bracketing' logic.  Every node in the union taxonomy is
	// assigned a unique integer, ordered sequentially by a preorder
	// traversal.  Taxon inclusion across taxonomies can be determined
	// (approximately) by looking at shared names and doing a range check.

	static final int NOT_SET = -7; // for source nodes

	void resetBrackets(Taxon node) {			  // for union nodes
		node.seq = NOT_SET;			  // Self
		node.start = NOT_SET;	// First taxon included not including self
		node.end = NOT_SET;					   // Next taxon *not* included
	}

	// Applied to a union node
	void assignBrackets(Taxon node) {
		// Only consider names in common ???
		node.seq = nextSequenceNumber++;
		node.start = nextSequenceNumber;
		if (node.children != null)
			for (Taxon child : node.children)
				assignBrackets(child);
		node.end = nextSequenceNumber;
	}

	// Applied to a source node
	static void getBracket(Taxon node, Taxonomy union) {
		if (node.end == NOT_SET) {
			Taxon unode = union.unique(node.name);
			if (unode != null)
				node.seq = unode.seq; // Else leave seq as NOT_SET
			if (node.children != null) {
				int start = Integer.MAX_VALUE;
				int end = -1;
				for (Taxon child : node.children) {
					getBracket(child, union);
					if (child.start < start) start = child.start;
					if (child.end > end) end = child.end;
					if (child.seq != NOT_SET) {
						if (child.seq < start) start = child.seq;
						if (child.seq > end) end = child.seq+1;
					}
				}
				node.start = start;
				node.end = end;
			}
		}
	}

	// Cheaper test, without seeking a witness
	boolean isNotSubsumedBy(Taxon node, Taxon unode) {
		getBracket(node, unode.taxonomy);
		return node.start < unode.start || node.end > unode.end; // spills out?
	}

	// Look for a member of the source taxon that's not a member of the union taxon,
	// but is a member of some other union taxon.
	static Taxon antiwitness(Taxon node, Taxon unode) {
		getBracket(node, unode.taxonomy);
		if (node.start >= unode.start && node.end <= unode.end)
			return null;
		else if (node.children != null) { // it *will* be nonnull actually
			for (Taxon child : node.children)
				if (child.seq != NOT_SET && (child.seq < unode.start || child.seq >= unode.end))
					return child;
				else {
					Taxon a = antiwitness(child, unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

	// Look for a member of the source taxon that's also a member of the union taxon.
	static Taxon witness(Taxon node, Taxon unode) { // assumes is subsumed by unode
		getBracket(node, unode.taxonomy);
		if (node.start >= unode.end || node.end <= unode.start) // Nonoverlapping => lose
			return null;
		else if (node.children != null) { // it *will* be nonnull actually
			for (Taxon child : node.children)
				if (child.seq != NOT_SET && (child.seq >= unode.start && child.seq < unode.end))
					return child;
				else {
					Taxon a = witness(child, unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}


    AlignmentByName(SourceTaxonomy source, UnionTaxonomy union) {

        this.source = source;
        this.union = union;

        union.reset();          // depths, brackets, comapped

        Criterion[] criteria = Criterion.criteria;
		if (source.rootCount() > 0) {

			Taxon.resetStats();
			System.out.println("--- Mapping " + source.getTag() + " into union ---");

			int beforeCount = union.nameIndex.size();

			union.markDivisionsx(source);

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
			for (Taxon node : source)
				if (!seen.contains(node.name)) {
					List<Taxon> unodes = union.nameIndex.get(node.name);
					if (unodes != null)
						for (Taxon unode : unodes)
							if (unode.name.equals(node.name))
								{ seen.add(node.name); todo.add(node.name); break; }
				}
			// primary / synonym
			for (Taxon node : union)
				if (source.nameIndex.get(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// synonym / primary
			for (Taxon node : source)
				if (union.nameIndex.get(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// This one probably just generates noise
			if (false)
			// synonym / synonym
			for (String name : source.nameIndex.keySet())
				if (union.nameIndex.get(name) != null &&
					!seen.contains(name))
					{ seen.add(name); todo.add(name); }

			int incommon = 0;
			int homcount = 0;
			for (String name : todo) {
				List<Taxon> unodes = union.nameIndex.get(name);
				if (unodes != null) {
					++incommon;
					List<Taxon> nodes = source.nameIndex.get(name);
					if (false &&
						(((nodes.size() > 1 || unodes.size() > 1) && (++homcount % 1000 == 0))))
						System.out.format("| Mapping: %s %s*%s (name #%s)\n", name, nodes.size(), unodes.size(), incommon);
					new Matrix(name, nodes, unodes).run(criteria);
				}
			}
			System.out.println("| Names in common: " + incommon);

			Taxon.printStats();

			// Report on how well the merge went.
			Alignment.alignmentReport(source, union);
		}
    }

    // For each source node, consider all possible union nodes it might map to
    // TBD: Exclude nodes that have 'prunedp' flag set

    class Matrix {

        String name;
        List<Taxon> nodes;
        List<Taxon> unodes;
        int m;
        int n;
        Answer[][] suppressp;

        Matrix(String name, List<Taxon> nodes, List<Taxon> unodes) {
            this.name = name;
            this.nodes = nodes;
            this.unodes = unodes;
            m = nodes.size();
            n = unodes.size();
            if (m*n > 50)
                System.out.format("!! Badly homonymic: %s %s*%s\n", name, m, n);
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
                for (Taxon node : nodes)
                    if (!node.name.equals(name)) {
                        Taxon unode = unodes.get(0);
                        ((UnionTaxonomy)unode.taxonomy).logAndMark(Answer.noinfo(node, unode, "synonym(s)", node.name));
                        break;
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
                Taxon x = nodes.get(i);
                for (int j = 0; j < n; ++j) {  // Find a union node to map it to...
                    if (suppressp[i][j] != null) continue;
                    Taxon y = unodes.get(j);
                    Answer z = criterion.assess(x, y);
                    if (z.value == Answer.DUNNO)
                        continue;
                    ((UnionTaxonomy)y.taxonomy).log(z);
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
                        Taxon x = nodes.get(i); // == uuniq[j]
                        Taxon y = unodes.get(j);

                        // Block out column, to prevent other source nodes from mapping to the same union node
                        if (false)
                        for (int ii = 0; ii < m; ++ii)
                            if (ii != i && suppressp[ii][j] == null)
                                suppressp[ii][j] = Answer.no(nodes.get(ii),
                                                             y,
                                                             "excluded(" + criterion.toString() +")",
                                                             x.getQualifiedId().toString());
                        // Block out row, to prevent this source node from mapping to multiple union nodes (!!??)
                        for (int jj = 0; jj < n; ++jj)
                            if (jj != j && suppressp[i][jj] == null)
                                // This case seems to never happen
                                suppressp[i][jj] = Answer.no(x,
                                                             unodes.get(jj),
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
                            System.out.format("** Blocked from mapping %s to %s because %s\n", x, y, x.answer.reason);
                        } else {
                            x.alignWith(y, a); // sets .mapped, .answer
                        }
                        suppressp[i][j] = a;
                    }
                }
        }

        // in x[i][j] i specifies the row and j specifies the column

        // Record reasons for mapping failure - for each unmapped source node, why didn't it map?
        void postmortem() {
            for (int i = 0; i < m; ++i) {
                Taxon node = nodes.get(i);
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
                                Taxon rival = nodes.get(ii);	// in source taxonomy or idsource
                                if (rival == node) continue;
                                // if (rival.mapped == null) continue;	// ???
                                QualifiedId qid = rival.getQualifiedId();
                                if (w == null) w = qid.toString();
                                else w += ("," + qid.toString());
                            }
                        explanation = Answer.noinfo(node, unodes.get(altj), "unresolved/contentious", w);
                    } else if (alts > 1) {
                        // Multiple union nodes to which this source can map... no way to tell
                        // ids have not been assigned yet
                        //	  for (int j = 0; j < n; ++j) others.add(unodes.get(j).id);
                        String w = null;
                        for (int j = 0; j < n; ++j)
                            if (suppressp[i][j] == null) {
                                Taxon candidate = unodes.get(j);	// in union taxonomy
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
                        if (n == 1)
                            explanation = suppressp[i][0];
                        else {
                            for (int j = 0; j < n; ++j)
                                if (suppressp[i][j] != null) // how does this happen?
                                    union.log(suppressp[i][j]);
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
                    union.logAndMark(explanation);
                    // remember, source could be either gbif or idsource
                    node.answer = explanation;  
                }
            }
        }
    }

}

