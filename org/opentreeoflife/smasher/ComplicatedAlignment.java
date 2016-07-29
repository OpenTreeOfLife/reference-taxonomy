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
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

public class ComplicatedAlignment extends Alignment {

    ComplicatedAlignment(Taxonomy source, Taxonomy target) {
        super(source, target);
    }

    ComplicatedAlignment(Alignment a) {
        super(a);
    }

    // Alignment by name - old method

    void reallyAlign() {
        assignBrackets();
		if (source.rootCount() > 0) {

            Criterion[] criteria = oldCriteria;

			int beforeCount = target.numberOfNames();

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
					List<Node> unodes = target.lookup(node.name);
					if (unodes != null)
						for (Node unode : unodes)
							if (unode.taxonNameIs(node.name)) {
                                seen.add(node.name);
                                todo.add(node.name);
                                break;
                            }
				}
			// primary / synonym
			for (Taxon unode : target.taxa())
				if (source.lookup(unode.name) != null &&
					!seen.contains(unode.name))
					{ seen.add(unode.name); todo.add(unode.name); }
			// synonym / primary    -- maybe disallow !?
			for (Taxon node : source.taxa())
				if (target.lookup(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// synonym / synonym probably just generates noise

			int incommon = 0;
			int homcount = 0;
			for (String name : todo) {
				List<Node> unodes = target.lookup(name);
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
		}
    }

    // For each source node, consider all possible target nodes it might map to

    class Matrix {

        String name;
        List<Node> nodes;
        List<Node> unodes;
        Alignment alignment;
        int m;
        int n;
        Answer[][] suppressp;   // A record of 'no' and unique 'yes' answers

        Matrix(String name, List<Node> nodes, List<Node> unodes, Alignment alignment) {
            this.name = name;
            this.nodes = nodes;
            this.unodes = unodes;
            this.alignment = alignment;
            m = nodes.size();
            n = unodes.size();
            if (m*n > 50)
                System.out.format("!! Badly homonymic: %s %s in source, %s in target\n", name, m, n);
        }

        void clear() {
            suppressp = new Answer[m][];
            for (int i = 0; i < m; ++i)
                suppressp[i] = new Answer[n];
        }

        // Compare every node to every other node, according to a list of criteria.
        void run(Criterion[] criteria) {

            clear();

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
            int[] uniq = new int[m];	// target nodes uniquely assigned to each source node
            for (int i = 0; i < m; ++i) uniq[i] = -1;
            int[] uuniq = new int[n];	// source nodes uniquely assigned to each target node
            for (int j = 0; j < n; ++j) uuniq[j] = -1;
            Answer[] answer = new Answer[m];
            Answer[] uanswer = new Answer[n];

            for (int i = 0; i < m; ++i) { // For each source node...
                Taxon x = nodes.get(i).taxon();
                Answer[] suppress_i = suppressp[i];
                for (int j = 0; j < n; ++j) {  // Find a target node to map it to...
                    if (suppress_i[j] != null) continue;
                    Taxon y = unodes.get(j).taxon();
                    Answer z = criterion.assess(x, y);

                    if (z.subject == null) continue; // dunno
                    z.maybeLog();
                    if (z.value == Answer.DUNNO) continue;

                    // suppress all nos
                    if (z.value < Answer.DUNNO) {
                        suppress_i[j] = z;
                        continue;
                    }

                    // update best union target (j) for this source node (i)
                    if (answer[i] == null || z.value > answer[i].value) {
                        uniq[i] = j;
                        answer[i] = z;
                    } else if (z.value == answer[i].value)
                        uniq[i] = -2; // Ambiguous.  This case is not dealt with properly.

                    // update best source node (i) for this union target (j)
                    if (uanswer[j] == null || z.value > uanswer[j].value) {
                        uuniq[j] = i;
                        uanswer[j] = z;
                    } else if (z.value == uanswer[j].value)
                        uuniq[j] = -2;

                }
            }
            for (int i = 0; i < m; ++i) { // iterate over source nodes
                int j = uniq[i];
                if (j >= 0) {             // Is there a unique best candidate for this source?
                    Answer[] suppress_i = suppressp[i];

                    Taxon x = nodes.get(i).taxon();   // source node
                    Taxon y = unodes.get(j).taxon();  // candidate that's being elected
                    Answer a = answer[i]; // best/first yes-ish answer so far

                    // Does any other source taxon map to this
                    // candidate?  If so, back off, we don't want
                    // collisions.  (?)
                    if (uuniq[j] < 0 || suppress_i[j] != null) {
                        x.markEvent("contends");
                        continue;
                    }

                    // See versions of this code from before 28 June 2016 for
                    // interesting logic that I excised

                    Answer prior = alignment.getAnswer(x);
                    if (prior == null)
                        this.alignment.alignWith(x, y, a);
                    // Probably an ad hoc mapping. This case doesn't happen, but could.
                    else if (prior.isYes())
                        if (prior.target == y) {
                            a = Answer.no(x, y, "lost-race-to-source(" + criterion.toString() + ")",
                                          (y.getSourceIdsString() + " lost to " +
                                           prior.target.getSourceIdsString()));
                            a.maybeLog();
                    } else if (prior.target == y && prior.value < Answer.DUNNO) {
                        // Previous answer is a 'no'
                        a = prior; // ????
                        x.markEvent("new assignment prevented by previous negative assignment");
                    }
                    suppress_i[j] = a;
                }
            }
        }

        // in x[i][j] i specifies the row and j specifies the column

        // Record reasons for mapping failure - for each unmapped source node, why didn't it map?
        void postmortem() {
            Taxonomy target = unodes.get(0).getTaxonomy();
            for (int i = 0; i < m; ++i) {
                Taxon node = nodes.get(i).taxon();
                // Suppress synonyms
                if (alignment.getTaxon(node) == null) {
                    Answer[] suppress_i = suppressp[i];
                    int alts = 0;	 // how many target nodes might we have gone to?
                    int altj = -1;
                    for (int j = 0; j < n; ++j)
                        if (suppress_i[j] == null
                            // && unodes.get(j).comapped == null
                            ) { ++alts; altj = j; }
                    Answer explanation; // Always gets set
                    if (alts == 1) {
                        // There must be multiple source nodes i1, i2, ... competing
                        // for this one target node.	 Merging them is (probably) fine.
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
                        // Multiple target nodes to which this source can map... no way to tell
                        // ids have not been assigned yet
                        //	  for (int j = 0; j < n; ++j) others.add(unodes.get(j).taxon().id);
                        String w = null;
                        for (int j = 0; j < n; ++j)
                            if (suppress_i[j] == null) {
                                Taxon candidate = unodes.get(j).taxon();	// in target taxonomy
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
                        // Iterate through the target nodes for this name that we didn't map to
                        // and collect all the reasons.
                        /*
                        if (n == 1) {
                            explanation = suppress_i[0];
                            if (explanation.reason.equals("not-same/weak-division"))
                                target.weakLog.add(explanation);
                        } else
                        */
                            {
                            for (int j = 0; j < n; ++j)
                                if (suppress_i[j] != null) // how does this happen?
                                    suppress_i[j].maybeLog();
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
                                explanation = Answer.no(node, null, "unresolved/blocked", kludge);
                        }
                    }
                    explanation.maybeLog(target);
                    // remember, source could be either gbif or idsource
                    alignment.setAnswer(node, explanation);  
                }
            }
        }
    }

	static Criterion[] oldCriteria = {
		Criterion.division,
		Criterion.lineage,
        Criterion.subsumption,
		Criterion.sameSourceId,
		Criterion.anySourceId,
		// knowDivision,
        Criterion.weakDivision,
		Criterion.byRank,
        Criterion.byPrimaryName,
        Criterion.elimination,
    };


}
