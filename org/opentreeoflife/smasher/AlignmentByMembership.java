/* ***** THIS CODE IS NOT IN PRODUCTION YET ***** */

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

	Taxonomy source, union;

    // Map source node to union node that includes it
	Map<Taxon, Taxon> sourceMap = new HashMap<Taxon, Taxon>();

    // Map union node to source node that includes it
	Map<Taxon, Taxon> unionMap = new HashMap<Taxon, Taxon>();

    // Final source-to-union alignment
	Map<Taxon, Taxon> alignmentMap = new HashMap<Taxon, Taxon>();

	// Invert the name->node map: for each node, store the names by
	// which it is known.
	Map<Taxon, Collection<String>> sourceNameMap;
	Map<Taxon, Collection<String>> unionNameMap;

	// Need to know which union taxa are targets of mrcas (range of
	// scaffold or mapping)
	// Need to compute and store # of union tips under each mrca
	// Need to do this in both passes
	// scaffoldCounts, mappingCounts

	Map<Taxon, Collection<Taxon>> candidatesMap =
		new HashMap<Taxon, Collection<Taxon>>();

	AlignmentByMembership(Taxonomy source, Taxonomy union) {
		this.source = source;
		this.union = union;
		this.sourceNameMap = makeNameMap(source);
		this.unionNameMap = makeNameMap(source);

        map();                  // tips and mrcas
        align();                // mutual-mrca
	}

    Taxon target(Taxon node) {
        return alignmentMap.get(node);
    }

    // Is it important to distinguish the two cases of 
    // merge-compatibility and refinement?
    // I.e. is it really necessary to do reciprocal-mrca?

    // Should we do member-mapping in one direction, name-mapping
    // in the other?

    void map() {
        for (Taxon node: source.roots)
            mapSubtree(node, union, sourceNameMap, sourceMap);
        for (Taxon unode: union.roots)
            mapSubtree(unode, source, unionNameMap, unionMap);
    }

    // Map tips by name, internal nodes by membership
    static Taxon mapSubtree(Taxon node,
                            Taxonomy dest,
                            Map<Taxon, Collection<String>> nameMap,
                            Map<Taxon,Taxon> map) {
        if (node.children != null) {
            Taxon mrca = null;
            for (Taxon child : node.children) {
                Taxon a = mapSubtree(node, dest, nameMap, map);
                if (a != null)
                    if (mrca == null)
                        mrca = a;
                    else
                        mrca = mrca.mrca(a);
            }
            if (mrca != null) {
                map.put(node, mrca);
                return mrca;
            }
        }
        Taxon unode = mapByName(node, nameMap, dest);
        if (unode != null) {
            map.put(node, unode);
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
            List<Taxon> unionNodes = dest.nameIndex.get(node.name);
            if (unionNodes != null)
                for (Taxon unode : unionNodes)
                    if (unode.name.equals(node.name))
                        if (!differentDivisions(node, unode))
                            candidates.add(unode);
            Taxon unode = tryCandidates(node, candidates);
            if (unode != null) return unode;
        }

        {
            // Then try source node against all dest nodes that have
            // one of the source node's names as one of the dest
            // node's names, subject to either the node's name or the
            // dest node's name being the node's primary name.

            List<Taxon> candidates = new ArrayList<Taxon>();
            for (String name : nameMap.get(node)) {
                if (name.equals(node.name)) {
                    List<Taxon> unionNodes = dest.nameIndex.get(name);
                    if (unionNodes != null)
                        for (Taxon unode : unionNodes)
                            if (!differentDivisions(node, unode))
                                candidates.add(unode);
                } else {
                    List<Taxon> unionNodes = dest.nameIndex.get(name);
                    if (unionNodes != null)
                        for (Taxon unode : unionNodes)
                            if (unode.name.equals(node.name))
                                if (!differentDivisions(node, unode))
                                    candidates.add(unode);
                }
                Taxon unode = tryCandidates(node, candidates);
                if (unode != null) return unode;
            }
        }
        return null;
    }
        
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
        return null;
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

    // Is unification really the right relationship? ... I don't think
    // so, it's already a many-to-one mapping, shouldn't be called
    // unification.

    void align() {
        for (Taxon node : source.roots)
            alignSubtree(node);
    }

    void alignSubtree(Taxon node) {
        if (node.children != null) {
            Taxon unode = sourceMap.get(node);
            if (unode == null)
                return;
            for (Taxon child : node.children)
                alignSubtree(child);
            Taxon renode = unionMap.get(unode);
            // unode and renode are both 'clean'
            if (unode == renode) {
                // Round trip m->n->m
                List<Taxon> candidates = new ArrayList<Taxon>();
                for (Taxon ancestor = unode; ancestor != null; ancestor = ancestor.parent) {
                    // Wrong test, we should try synonyms as well
                    if (ancestor.name.equals(node.name) &&
                        unionMap.get(ancestor) == renode)
                        candidates.add(ancestor);
                }
                Taxon match = tryCandidates(node, candidates);
                if (match != null)
                    alignmentMap.put(node, match);
                else
                    // Ambiguous.  Map to 'smallest' compatible node
                    alignmentMap.put(node, unode);
            }
        }
    }

    // This is top-down... bottom-up might be better
    // If all children map to the same node or to children of that
    // node, then we have a refinement...

    boolean refinementp(Taxon node, Taxon unode, List<Taxon> children) {
        Taxon unext = sourceMap.get(node);
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


	// Compute the inverse of the node->name map.
	static Map<Taxon, Collection<String>> makeNameMap(Taxonomy tax) {
		Map<Taxon, Collection<String>> nameMap = new HashMap<Taxon, Collection<String>>();
		for (String name : tax.nameIndex.keySet())
			for (Taxon node : tax.nameIndex.get(name)) {
				Collection<String> names = nameMap.get(node);  // of this node
				if (names == null) {
					names = new ArrayList(1);
					nameMap.put(node, names);
				}
				names.add(name);
			}
		return nameMap;
	}

}
