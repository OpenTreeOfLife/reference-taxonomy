
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
            System.out.println("No nodes in common");
    }

    // Find OTT ids for OTUs that have them, and use them to map
    // bidirectionally between input tree tips and ref tips
    // deprecated?
    boolean mapTips(Taxon node, Taxonomy ref) {
        if (node.children != null) {
            boolean any = false;
            for (Taxon child : node.children)
                any = mapTips(child, ref) || any;
            if (any) return true;
        }
        return mapTip(node, ref);
    }

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
            map.put(node, node2);
            comap.put(node2, node);
            return true;
        }
        if (false && node.name != null) {
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
            System.out.format("| mapped %s, comapped %s\n", map.size(), comap.size());
            if (this.inducedRoot == null)
                System.err.format("** Nothing maps\n");
            else
                induce(this.inducedRoot, input, comap);
        }
    }

    // This runs twice, once in each direction input->ref / ref->input
    Taxon induce(Taxon node, Taxonomy other, Map<Taxon, Taxon> map) {
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

    public Disposition disposition(Taxon node) {
        if (node.taxonomy == input)
            return disposition(node, map, comap);
        else
            return disposition(node, comap, map);
    }

    public Disposition disposition(Taxon node, Map<Taxon, Taxon> map, Map<Taxon, Taxon> comap) {
        if (node.children == null)
            return Disposition.NONE;
        Taxon conode = map.get(node);
        if (conode == null)
            return Disposition.NONE;
        Taxon bounce = comap.get(conode);
        if (bounce == null)
            return Disposition.NONE; // shouldn't happen
        if (bounce == node)
            return Disposition.CONGRUENT;
        if (node.parent == null)
            return Disposition.NONE; // shouldn't happen
        Taxon m = node.parent.mrca(bounce);
        if (m == bounce)
            // parent is, or descends from, bounce
            return Disposition.REFINES;
        else
            return Disposition.CONFLICTS;
    }

    public int[] dispositionCounts() {
        int none = 0, congruent = 0, refines = 0, conflicts = 0;
        for (Taxon node : ingroup.descendants(true)) {
            switch(this.disposition(node)) {
            case NONE: ++none; break;
            case CONGRUENT: ++congruent; break;
            case REFINES: ++refines; break;
            case CONFLICTS: ++conflicts; break;
            }
        }
        conflicting = conflicts;
        opportunities = congruent + refines + conflicts;
        return new int[]{none, congruent, refines, conflicts};
    }

    public Taxon witness(Taxon node) {
        Disposition d = disposition(node);
        Map<Taxon, Taxon> map = this.map;
        Map<Taxon, Taxon> comap = this.comap;
        if (node.taxonomy == ref) {
            map = this.comap;
            comap = this.map;
        }
        switch(this.disposition(node, map, comap)) {
        case NONE:
            return null;
        case CONGRUENT:
            return map.get(node);
        case REFINES:
            return map.get(node); // ???
        case CONFLICTS:
            return findConflicting(node, map, comap);
        }
        // not reached
        return null;
    }

    Taxon findConflicting(Taxon node, Map<Taxon, Taxon> map, Map<Taxon, Taxon> comap) {
        // Find a reference node that conflicts with this input node.
        // At least one child must map to a conflicting reference node (yes?).
        for (Taxon child : node.children) {
            Taxon cochild = map.get(child);
            if (cochild == null) continue;
            Taxon childbounce = comap.get(cochild);
            if (childbounce == null) continue;
            // Is childbounce under node?
            Taxon m = node.mrca(childbounce);
            if (m != node)
                return cochild;
        }
        System.err.format("** Failed to find reference node conflicting with %s\n", node);
        return null;
    }

    // ------------------------------------------------------------------
    // First attempt at conflict analysis

    List<Conflict> findConflicts() {
        if (inducedIngroup != null) {
            findConflicts(inducedIngroup);
            Collections.sort(conflicts, worseThan);
            return conflicts;
        } else {
            System.err.println("** No induced ingroup\n");
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
