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

public class AlignmentByName extends Alignment {

	Taxonomy source, union;

    // Return the node that this one maps to under this alignment, or null

    Taxon target(Taxon node) {
        return node.mapped;
    }

    AlignmentByName(SourceTaxonomy source, UnionTaxonomy union) {

        this.source = source;
        this.union = union;

        Criterion[] criteria = Criterion.criteria;
		if (source.roots.size() > 0) {

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
				boolean painful = name.equals("Nematoda");
				List<Taxon> unodes = union.nameIndex.get(name);
				if (unodes != null) {
					++incommon;
					List<Taxon> nodes = source.nameIndex.get(name);
					if (false &&
						(((nodes.size() > 1 || unodes.size() > 1) && (++homcount % 1000 == 0)) || painful))
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
            if (m*n > 100)
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
                            ;
                        // Did someone else get there first?
                        // Actually maybe this shouldn't matter.
                        else if (y.comapped != null) {
                            x.deprecationReason = a;
                            a = Answer.no(x, y,
                                          "lost-race-to-union(" + criterion.toString() + ")",
                                          ("lost to " +
                                           y.comapped.getQualifiedId().toString()));
                        } else if (x.mapped != null) {
                            // This case doesn't happen
                            x.deprecationReason = a;
                            a = Answer.no(x, y, "lost-race-to-source(" + criterion.toString() + ")",
                                          (y.getSourceIdsString() + " lost to " +
                                           x.mapped.getSourceIdsString()));
                        } else
                            x.unifyWith(y);
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
                    Answer explanation;
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
                                    System.err.println("?!! No source ids: " + candidate);
                                QualifiedId qid = candidate.sourceIds.get(0);
                                if (w == null) w = qid.toString();
                                else w += ("," + qid.toString());
                            }
                        explanation = Answer.noinfo(node, null, "unresolved/ambiguous", w);
                    } else {
                        // Important case, mapping blocked, give gory details.
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
                    if (node.deprecationReason == null)
                        node.deprecationReason = explanation;  
                }
            }
        }
    }

}

