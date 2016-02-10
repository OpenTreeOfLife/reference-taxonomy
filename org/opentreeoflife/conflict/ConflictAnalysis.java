
package org.opentreeoflife.conflict;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;

import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.QualifiedId;

public class ConflictAnalysis {

    // input is an input tree, ref is a 'reference' tree (either taxonomy or synthetic tree)
    public Taxonomy input;
    public Taxonomy ref;

    // The Taxonomy class allows multiple roots, but we can only deal with one
    Taxon inputRoot = null;
    Taxon refRoot = null;

    public Taxon inducedRoot = null;          // node in ref induced by whole input
    public Taxon inducedIngroup = null;       // node in ref induced by ingroup

    public Taxon ingroup = null;              // node in input

    Map<Taxon, Taxon> map = new HashMap<Taxon, Taxon>(); // input -> ref
    Map<Taxon, Taxon> comap = new HashMap<Taxon, Taxon>(); // ref -> input

    int conflicting = 0;
    int opportunities = 0;

    boolean includeIncertaeSedis;

    List<Conflict> conflicts = new ArrayList<Conflict>();

    public ConflictAnalysis(Taxonomy input, Taxonomy ref) {
        this(input, ref, null, true);
    }
    public ConflictAnalysis(Taxonomy input, Taxonomy ref, String ingroupId) {
        this(input, ref, ingroupId, true);
    }
    public ConflictAnalysis(Taxonomy input, Taxonomy ref, String ingroupId, boolean includeIncertaeSedis) {
        this.input = input;
        this.ref = ref;
        this.inputRoot = uniqueRoot(input);
        this.refRoot = uniqueRoot(ref);
        this.induce();

        if (ingroupId == null)
            ingroupId = input.ingroupId;

        if (ingroupId != null)
            this.ingroup = input.lookupId(ingroupId);
        if (this.ingroup == null)
            this.ingroup = inputRoot;
        this.inducedIngroup = map.get(this.ingroup);
        // inducedIngroup = mrca in ref of all the shared tips in input
    }

    Taxon uniqueRoot(Taxonomy tax) {
        Taxon root = null;
        for (Taxon r : tax.roots()) {
            if (root != null)
                System.out.format("** Cannot deal with multiple roots: %s %s %s\n", r, root, tax.getTag());
            else
                root = r;
        }
        if (root == null)
            System.out.format("** No root for %s\n", tax.getTag());
        return root;
    }

    // One measure of conflict is #conflicting divided by #opportunities
    public double conflictivity() {
        if (opportunities == 0)
            return 0.0d;
        else
            return ((double)conflicting) / ((double)opportunities);
    }

    public double unmappedness() {
        if (this.ingroup == null)
            return 1.0d;
        else
            return 1.0d - (((double)countOtus(this.ingroup)) / ((double)this.ingroup.tipCount()));
    }

    public int awfulness() {
        if (conflicts.size() == 0)
            return 0;
        else
            return conflicts.get(0).badness();
    }

    // Apply to a node in input (e.g. ingroup)
    int countOtus(Taxon node) {
        if (node.children != null) {
            int total = 0;
            for (Taxon child : node.children)
                total += countOtus(child);
            return total;
        } else if (map.get(node) != null)
            return 1;
        else
            return 0;
    }

    public void printReport() {
        if (this.inducedIngroup != null) {
            System.out.format("%s nodes, %s tips, %s mapped, %s mapped from input to ref, %s from ref to input\n",
                              input.count(), input.tipCount(), countOtus(this.ingroup),
                              map.size(), comap.size());
            System.out.format("%s conflicts out of %s opportunities (%.2f)\n", conflicting, opportunities, conflictivity());
            int i = 0;
            for (Conflict c : conflicts) {
                c.print();
                if (++i > 10) {
                    System.out.println("...");
                    break;
                }
            }
        } else
            System.out.format("No nodes in common\n");
    }

    // Map tips bidirectionally

    boolean mapTip(Taxon node, Taxonomy ref) {
        String id = null;
        if (node.taxonomy.idspace.equals(ref.idspace)) // useful for testing
            id = node.id;
        else if (node.sourceIds != null)
            for (QualifiedId qid : node.sourceIds)
                if (qid.prefix.equals(ref.idspace)) {
                    id = qid.id;
                    break;
                }
        Taxon node2 = ref.lookupId(id);
        if (node2 != null
            && (includeIncertaeSedis || !node2.isHidden())) {
            Taxon othernode = comap.get(node2);
            if (othernode == null) {
                map.put(node, node2);
                comap.put(node2, node);
                return true;
            } else
                // Multiple input nodes map to a single ref node.
                // Map only one of them to the ref node, leaving the
                // others unmapped.
                return false;
        } else if (false && node.name != null) {
            Taxon probe = ref.unique(node.name);
            if (probe != null) {
                // There are lots of these e.g. all of Metalasia in pg_1572
                System.out.format("OTU autolookup? %s\n", probe);
                // map.put(node, probe);
                // comap.put(probe, node);
            }
        }
        return false;
    }

    void induce() {
        // Get the two mrca-based maps.  First the input->ref, then
        // (starting at induced root) the ref->input map.
        // Look for cases where mapping A-B-A goes to an ancestor of the start node
        // (descendant is OK, that's sort of like monotypy)
        if (this.inputRoot == null)
            System.err.format("** No tree %s\n", input.getTag());
        else {
            this.inducedRoot = induce(this.inputRoot, ref, map);
            //System.out.format("| mapped %s, comapped %s\n", map.size(), comap.size());
            if (this.inducedRoot == null)
                System.err.format("** Nothing maps\n");
            else
                induce(this.inducedRoot, input, comap);
        }
    }

    // This runs twice, once in each direction input->ref / ref->input
    Taxon induce(Taxon node, Taxonomy other, Map<Taxon, Taxon> map) {
        if (false) {
            Taxon probe = map.get(node);
            if (probe != null) return probe; // encountered as tip in other direction
        }
        if (node.children != null) {
            Taxon mrca = null;
            for (Taxon child : node.children) {
                Taxon a = induce(child, other, map);
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
            /* fall through - node is a 'virtual tip' */
        }
        if (other == this.ref) // kludge
            mapTip(node, other);
        return map.get(node);
    }

    // Set the name of any input node that matches up with a node in the reference tree.
    // Also add ott:nnnnn as a source id for any named node, so we can tell which OTT taxon was matched.

    Taxonomy setNames() {
        setNames(inputRoot);
        return input;
    }

    void setNames(Taxon conode) {
        if (conode.children != null) {
            conode.name = null;
            conode.sourceIds = null;
            Taxon node = map.get(conode); // in reference (taxonomy or synth)
            if (node != null) {
                Taxon cobounce = comap.get(node);
                if (cobounce == conode) {
                    conode.name = node.name;
                    conode.addSourceId(new QualifiedId(ref.getIdspace(), node.id));
                }
            }
            for (Taxon child : conode.children)
                setNames(child);
        }
    }

    // Tree derived from ref with tips from input
    Taxonomy inducedTree() {
        Set<Taxon> seen = new HashSet<Taxon>(); // set of ref nodes
        Map<Taxon, Taxon> selected = new HashMap<Taxon, Taxon>(); // map from ref to induced
        Taxonomy induced = new SourceTaxonomy(ref.getIdspace());
        // Create node selection
        for (Taxon node : inputRoot.descendants(true))
            if (node.children == null) {
                Taxon refNode = map.get(node);
                if (refNode != null && selected.get(refNode) == null) {
                    Taxon tip = new Taxon(induced);
                    if (refNode.id != null) tip.setId(refNode.id);
                    if (refNode.name != null) tip.setName(refNode.name);
                    selected.put(refNode, tip);
                    seen.add(refNode);

                    // Scan rootward through reference tree from every tip (OTU)
                    for (Taxon scan = refNode.parent; scan != null; scan = scan.parent) {
                        if (!seen.contains(scan))
                            seen.add(scan);
                        else {
                            // seen twice or more
                            Taxon sel = selected.get(scan);
                            if (sel == null) {
                                // second visit
                                sel = new Taxon(induced);
                                if (scan.id != null) sel.setId(scan.id);
                                if (scan.name != null) sel.setName(scan.name);
                                selected.put(scan, sel);
                            }
                            break;
                        }
                    }
                }
            }
        System.out.format("%s nodes in induced tree (%s seen, %s mapped)\n", selected.size(), seen.size(), map.size());
        // Set parent pointers for all nodes
        int nroots = 0;
        for (Taxon refNode : selected.keySet()) {
            Taxon node = selected.get(refNode);
            boolean rootp = true;
            // Find parent of node
            for (Taxon scan = refNode.parent; scan != null; scan = scan.parent) {
                Taxon p = selected.get(scan);
                if (p != null) {
                    p.addChild(node);
                    rootp = false;
                    break;
                }
            }
            if (rootp) {
                induced.addRoot(node);
                ++nroots;
            }
        }
        if (nroots != 1)
            System.out.format("Induced tree has %s roots, should have 1\n", nroots);
        return induced;
    }

    // ------------------------------------------------------------
    // Second attempt at conflict analysis

    public Articulation articulation(Taxon node) {
        if (node.taxonomy == input)
            return articulation(node, map, comap);
        else
            return articulation(node, comap, map);
    }

    public Articulation articulation(Taxon node, Map<Taxon, Taxon> map, Map<Taxon, Taxon> comap) {
        if (node.children == null)
            return null;
        Taxon conode = map.get(node);
        if (conode == null)
            return null;
        Taxon bounce = comap.get(conode);
        if (bounce == null) {
            System.err.format("Shouldn't happen 1 %s\n", node);
            return null; // shouldn't happen
        }
        if (node.mrca(bounce) == node) {  // bounce <= node?
            // If bounce and its parent both map to conode, then SUPPORTED_BY
            // Otherwise, PARTIAL_PATH_OF
            // TBD: get highest congruent ancestor of conode
            Taxon bounceparent = bounce.parent;
            if (bounceparent != null && map.get(bounceparent) == conode)
                return new Articulation(Disposition.PATH_SUPPORTED_BY, conode);
            else
                return new Articulation(Disposition.SUPPORTED_BY, conode);
        }
        if (node.parent == null) {
            System.err.format("Shouldn't happen 2 %s\n", node);
            return null; // shouldn't happen
        }
        if (conode.children == null) {
            System.out.format("** shouldn't happen\n");
            return null;
        }

        for (Taxon cochild : conode.children) {
            if (checkConflict(node, cochild, map, comap) == Disposition.CONFLICTS_WITH)
                return new Articulation(Disposition.CONFLICTS_WITH, cochild);
        }
        return new Articulation(Disposition.RESOLVES, conode);
    }

    Disposition checkConflict(Taxon node, Taxon conode, Map<Taxon, Taxon> map, Map<Taxon, Taxon> comap) {
        Taxon bounce = comap.get(conode);
        if (bounce == null) return null;

        Taxon m = bounce.mrca(node);
        if (m == node)
            // Everything in cochild is in node
            return Disposition.CONTAINS;
        else if (bounce.children == null) {
            return Disposition.EXCLUDES;
        } else {
            boolean c = false, e = false;
            for (Taxon cochild : conode.children) {
                Disposition d = checkConflict(node, cochild, map, comap);
                if (d == null) continue;
                if (d == Disposition.CONFLICTS_WITH)
                    return d;
                if (d == Disposition.CONTAINS) c = true;
                if (d == Disposition.EXCLUDES) e = true;
                if (c && e)
                    return Disposition.CONFLICTS_WITH;
            }
            if (c) return Disposition.CONTAINS; // shouldn't happen
            if (e) return Disposition.EXCLUDES;
            return null;
        }
    }


    // Find a node in ref that conflicts with node.
    // (Must rule out resolution case.)

    // Suppose N maps to N', N' maps to N'', N'' bigger than N.
    // Then N < N' < N''.  In particular N' does not conflict with N.
    // But if it's not that N resolves N', then N must conflict with
    // some node Q < N'.  We much find such a node Q.

    // Q and N must intersect without containment or disjointness.

    Articulation findConflicting(Taxon node, Taxon conode, Map<Taxon, Taxon> map, Map<Taxon, Taxon> comap) {

        Taxon bounce = comap.get(conode);
        if (bounce == null) return null;

        // Node and bounce are in the same tree, so they cannot conflict.

        // See if bounce is entirely within node (if so, conode is too)
        Taxon m = bounce.mrca(node);
        if (m == node)
            // Yes, congruence or resolution, not conflict
            return new Articulation(Disposition.CONTAINS, conode);

        if (m != bounce)
            // node and bounce are disjoint, therefore node and conode are.
            return new Articulation(Disposition.EXCLUDES, conode);

        if (conode.children == null) return null;

        // Perhaps a descendant of conode conflicts.
        Articulation d = null, r = null, q = null;
        for (Taxon cochild : conode.children) {
            Articulation a = findConflicting(node, cochild, map, comap);
            if (a != null)
                switch(a.disposition) {
                case EXCLUDES:
                    d = a; break;
                case CONTAINS:
                    r = a; break;
                case CONFLICTS_WITH:
                    q = a;
                }
            if (a != null && d != null)
                return new Articulation(Disposition.CONFLICTS_WITH, conode);
            }
        if (q != null)
            return q;

        // Failed to find conflict.  Probably conode resolves node.
        return new Articulation(Disposition.CONTAINS, conode);
    }

    /**
    public int[] dispositionCounts() {
        int none = 0, congruent = 0, resolves = 0, conflicts = 0;
        for (Taxon node : ingroup.descendants(true)) {
            Articulation a = this.articulation(node);
            if (a == null)
                ++none;
            else
                switch(a.disposition) {
                case NONE: ++none; break;
                case SUPPORTS: case SUPPORTS_PATH:
                case SUPPORTED_BY: case PATH_SUPPORTED_BY:
                case CONGRUENT: ++congruent; break;
                case RESOLVES: ++resolves; break;
                case CONFLICTS_WITH: ++conflicts; break;
                }
        }
        conflicting = conflicts;
        opportunities = congruent + resolves + conflicts;
        return new int[]{none, congruent, resolves, conflicts};
    }
    */

    // ------------------------------------------------------------------
    // First attempt at conflict analysis

    List<Conflict> findConflicts() {
        if (inducedIngroup != null) {
            findConflicts(inducedIngroup);
            Collections.sort(conflicts, worseThan);
            return conflicts;
        } else {
            System.err.format("** No induced ingroup\n");
            return null;
        }
    }
    
    class Conflict {
        Taxon outlier, inlier;
        Taxon node, bounce;
        Taxon comesFrom;
        Conflict(Taxon outlier, Taxon inlier, Taxon node, Taxon bounce, Taxon comesFrom) {
            this.outlier = outlier; this.inlier = inlier;
            this.node = node; this.bounce = bounce; this.comesFrom = comesFrom;
        }
        int badness() {
            return (distance(this.node, this.bounce) +
                    distance(this.outlier, this.bounce));
        }
        void print() {
            System.out.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                              node.name, node.count(),
                              bounce.name, bounce.count(),
                              outlier.name + " + " + inlier.name,
                              distance(node, bounce),
                              distance(outlier, bounce));
        }
    }

    Comparator<Conflict> worseThan = new Comparator<Conflict>() {
            public int compare(Conflict c1, Conflict c2) {
                int d1 = c1.badness();
                int d2 = c2.badness();
                return d2 - d1;
            }
        };

    // 'vertical' distance in tree between a node and one of its ancestors
    public static int distance(Taxon node, Taxon ancestor) {
        int dist = 0;
        for (Taxon n = node; n != ancestor; n = n.parent)
            ++dist;
        return dist;
    }

    // Recursive.
    // Input: a node in the reference taxonomy
    // Output: the 'bounce' of that node
    // Side effect: adds Conflict records to this.conflicts
    Conflict findConflicts(Taxon node) {
        Taxon conode = comap.get(node);
        if (conode != null) {
            Taxon bounce = map.get(conode);
            if (bounce != null) {
                // Preorder traversal.
                Conflict covered = null;
                if (node.children != null)
                    for (Taxon child : node.children) {
                        Conflict childConflict = findConflicts(child);
                        if (childConflict != null) {
                            if (childConflict.bounce == bounce)
                                covered = childConflict;
                        }
                    }
                // Now deal with this node
                ++opportunities;
                if (node.mrca(bounce) != node) { // if bounce doesn't descend from node
                    ++conflicting;
                    if (covered == null) {
                        Conflict conflict = suspect(node, bounce);
                        if (conflict != null)
                            conflicts.add(conflict);
                        return conflict;
                    } else
                        return new Conflict(covered.outlier, covered.inlier, node, bounce, covered.node);
                }
            }
        }
        return null;
    }

    // Need a way to assign blame for paraphyly.
    // If the reference has ((a,b),d), and the input has ((a,d),b),
    // then we want to 'blame' the breakup of (a,b) on d.
    Conflict suspect(Taxon broken, Taxon bounce) {
        Taxon conode = comap.get(broken);
        if (conode == null) return null;
        return hunt(conode, broken, bounce);
    }

    // Find a descendant of conode that is disjoint from
    // broken, but has a sibling that's not.
    Conflict hunt(Taxon conode, Taxon broken, Taxon brokenbounce) {
        if (conode.children != null) {
            Taxon in = null, out = null;
            Conflict more = null;
            for (Taxon cochild : conode.children) {
                Taxon bounce = map.get(cochild);
                if (bounce != null) {
                    Taxon m = bounce.mrca(broken);
                    if (m == broken)
                        in = bounce;
                    else if (m != bounce)
                        out = bounce;
                    else if (more == null) {
                        Conflict probe = hunt(cochild, broken, brokenbounce);
                        if (probe != null) more = probe;
                    }
                    if (in != null && out != null)
                        return new Conflict(out, in, broken, brokenbounce, null);
                }
            }
            return more;
        }
        return null;
    }

}
