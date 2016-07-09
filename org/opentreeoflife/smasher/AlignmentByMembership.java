/* ***** THIS CODE IS NOT IN PRODUCTION YET ***** */

/*
  The goal here is to be able to unify source nodes with union nodes.

  The unification is done by Taxon.alignWith and has the effect of
  setting the 'mapped' field of the node.

  */


// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same / notSame list as input.


package org.opentreeoflife.smasher;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.io.PrintStream;
import java.io.IOException;

public class AlignmentByMembership extends Alignment {

    // Map source node to union node that includes its tips
	Map<Taxon, Taxon> sourceHalfMap = new HashMap<Taxon, Taxon>();

    // Map union node to source node that includes it
	Map<Taxon, Taxon> unionHalfMap = new HashMap<Taxon, Taxon>();

	// Need to know which union taxa are targets of mrcas (range of
	// scaffold or mapping)
	// Need to compute and store # of union tips under each mrca
	// Need to do this in both passes
	// scaffoldCounts, mappingCounts

	Map<Taxon, Collection<Taxon>> candidatesMap =
		new HashMap<Taxon, Collection<Taxon>>();

    private static Taxon AMBIGUOUS = new Taxon(null, null);

	AlignmentByMembership(SourceTaxonomy source, UnionTaxonomy union) {
        super(source, union);
    }

    public void align() {
        // Invert the name->node map: for each node, store the names by
        // which it is known.
        Map<Taxon, Collection<String>> sourceSynonymIndex;
        Map<Taxon, Collection<String>> unionSynonymIndex;

		sourceSynonymIndex = source.makeSynonymIndex();
        System.out.println("sourceSynonymIndex: " + sourceSynonymIndex.size());
		unionSynonymIndex = union.makeSynonymIndex();
        System.out.println("unionSynonymIndex: " + unionSynonymIndex.size());
        System.out.println("a"); System.out.flush();
        halfMap(sourceSynonymIndex, unionSynonymIndex);           // tips and mrcas
        System.out.println("b"); System.out.flush();
        this.alignWith(source.forest, union.forest, "align-forests");
        alignify();                // mutual-mrca
	}

    // Is it important to distinguish the two cases of 
    // merge-compatibility and refinement?
    // I.e. is it really necessary to do reciprocal-mrca?

    // Should we do member-mapping in one direction, name-mapping
    // in the other?

    void halfMap(Map<Taxon, Collection<String>> sourceSynonymIndex,
                 Map<Taxon, Collection<String>> unionSynonymIndex) {
        System.out.println("halfMap");
        System.out.flush();
        for (Taxon node: source.roots())
            halfMapSubtree(node, union, sourceSynonymIndex, sourceHalfMap);
        System.out.println("sourceHalfMap: " + sourceHalfMap.size());
        for (Taxon unode: union.roots())
            halfMapSubtree(unode, source, unionSynonymIndex, unionHalfMap);
        System.out.println("unionHalfMap: " + unionHalfMap.size());
    }

    // Map tips by name, internal nodes by membership
    Taxon halfMapSubtree(Taxon node,
                         Taxonomy dest,
                         Map<Taxon, Collection<String>> nameMap,
                         Map<Taxon,Taxon> halfMap) {
        Taxon unode = getTaxon(node); // THIS SEEMS WRONG.
        // Alignment forced by manual intervention
        if (unode != null) {
            halfMap.put(node, unode);
            return unode;
        }
        if (node.children != null) {
            Taxon mrca = null;
            for (Taxon child : node.children) {
                Taxon a = halfMapSubtree(child, dest, nameMap, halfMap);
                if (a != null)
                    if (mrca == null)
                        mrca = a;
                    else
                        mrca = mrca.mrca(a);
            }
            if (mrca != null) {
                halfMap.put(node, mrca);
                return mrca;
            }
            /* fall through - node is a 'virtual tip' */
        }
        unode = mapByName(node, nameMap, dest); //could be ambiguous
        if (unode != null) {
            halfMap.put(node, unode);
            return unode;
        } else
            return null;
    }

    // require vs. prefer:
    //   same division is required
    //   same rank is preferred

    static Taxon mapByName(Taxon node,
                           Map<Taxon, Collection<String>> nameMap,
                           Taxonomy dest) {
        {
            // First try for exact name match.
            List<Taxon> candidates = new ArrayList<Taxon>();
            List<Node> unionNodes = dest.lookup(node.name);
            if (unionNodes != null)
                for (Node unode : unionNodes)
                    if (unode.taxonNameIs(node.name)) {
                        Taxon utaxon = unode.taxon();
                        if (!differentDivisions(node, utaxon))
                            candidates.add(utaxon);
                    }
            Taxon unode = tryCandidates(node, candidates);
            if (unode != null) return unode; // possibly ambiguous
        }

        {
            // Then try source node against all dest nodes that have
            // one of the source node's names as one of the dest
            // node's names, subject to either the node's name or the
            // dest node's name being the node's primary name.

            List<Taxon> candidates = new ArrayList<Taxon>();

            // Consider all union nodes that have this node's primary
            // name among their names
            {
                List<Node> unionNodes = dest.lookup(node.name);
                if (unionNodes != null)
                    for (Node unode : unionNodes) {
                        Taxon utaxon = unode.taxon();
                        if (!differentDivisions(node, utaxon))
                            candidates.add(utaxon);
                    }
            }

            // Consider all union nodes that have one of this nodes'
            // names as their primary name
            Collection<String> names = nameMap.get(node);
            if (names != null)
                for (String name : names) {
                    if (!name.equals(node.name)) {
                        List<Node> unionNodes = dest.lookup(name);
                        if (unionNodes != null)
                            for (Node unode : unionNodes)
                                if (unode.taxonNameIs(node.name)) {
                                    Taxon utaxon = unode.taxon();
                                    if (!differentDivisions(node, utaxon))
                                        candidates.add(utaxon);
                                }
                    }
                }
            return tryCandidates(node, candidates);
        }
    }
        
    // Taxon, AMBIGUOUS, or null
    static Taxon tryCandidates(Taxon node, List<Taxon> candidates) {
        if (candidates.size() == 1)
            return candidates.get(0);
        else if (candidates.size() == 0)
            return null;
        else
            for (Criterion c : criteria) {
                List<Taxon> filtered = new ArrayList<Taxon>();
                for (Taxon unode : candidates)
                    if (c.metBy(node, unode))
                        filtered.add(unode);
                if (filtered.size() == 1)
                    return filtered.get(0);
                if (filtered.size() > 1)
                    candidates = filtered;
            }
        // Fatally ambiguous!  Maybe choose based on distance in tree?
        // but that would require matches for interior nodes, which we
        // don't have yet.
        return AMBIGUOUS;
    }

    static Criterion[] criteria = {
		Criterion.lineage,
		Criterion.sameSourceId,
		Criterion.anySourceId,
		Criterion.byRank
    };

    static boolean differentDivisions(Taxon node, Taxon unode) {
        Taxon div = node.getDivision(), udiv = unode.getDivision();
        if (div != null && udiv != null)
            return div != udiv;
        else
            return false;
    }

    void alignify() {
        for (Taxon node : source.roots())
            alignSubtree(node);
    }

    void alignSubtree(Taxon node) {
        Taxon unode = sourceHalfMap.get(node);
        if (unode == null)
            return;
        if (unode == AMBIGUOUS) {
            this.setAnswer(node, Answer.no(node, null, "ambiguous", null));
            return;
        }
        if (node.children != null) {
            for (Taxon child : node.children)
                alignSubtree(child);
        }
        Taxon renode = unionHalfMap.get(unode);
        // unode and renode are both 'clean'
        if (node == renode) {
            // Round trip m->n->m
            List<Taxon> candidates = new ArrayList<Taxon>();
            Taxon biggest = null;
            for (Taxon ancestor = unode; ancestor != null; ancestor = ancestor.parent) {
                // Wrong test, we should try synonyms as well
                if (ancestor.name.equals(node.name) &&
                    unionHalfMap.get(ancestor) == renode) {
                    candidates.add(ancestor);
                    biggest = ancestor;
                }
            }
            Taxon match = tryCandidates(node, candidates);
            if (match == AMBIGUOUS)
                this.setAnswer(node, Answer.no(node, null, "ambiguous-internal", null));
            else if (match != null)
                this.setAnswer(node, Answer.yes(node, match, "matched", null));
            else
                // Ambiguous.  Map to 'largest' compatible ancestor node
                this.setAnswer(node, Answer.yes(node, biggest, "slop", null));
        }
    }

    // This is top-down... bottom-up might be better
    // If all children map to the same node or to children of that
    // node, then we have a refinement...

    boolean refinementp(Taxon node, Taxon unode, List<Taxon> children) {
        Taxon unext = sourceHalfMap.get(node);
        if (unext == null)
            return true;        // ???
        else if (unext == unode) {
            if (node.children != null)
                for (Taxon child: node.children)
                    if (!refinementp(child, unode, children))
                        return false;
            return true; // ????
        } else if (unext.parent == unode) {
            children.add(node);
            return true;
        } else
            return false;
    }


        // For each source/union/source 
        //  For m and each of its ancestors m' with m'->n:
        //   try to map m by name to one of n, n', ...
        //  If compatible pairing is unique:
        //   Make assignment ?
        //  Else...
        //   For each compatible pair up the tree:
        //    Attempt mapping by name
        //  For every refinement option down the tree:
        //    ... do it ...

        // R is a refiner of n iff either
        //   R is a child of n, or
        //   every child of R is a a refiner of n
        // R is not a refiner of n if it's not a child of n and its mrca isn't n.

        // m repartions n iff
        // every child of n maps to a node under a child of m??


    void sluggishly(Taxon node) {
        try {
            Thread.currentThread().sleep(100);
        } catch (InterruptedException e) { ; }
        System.out.println(node);
        System.out.flush();
    }
}
