
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

    public Map<Taxon, Taxon> map = new HashMap<Taxon, Taxon>(); // input -> ref
    public Map<Taxon, Taxon> comap = new HashMap<Taxon, Taxon>(); // ref -> input

    int conflicting = 0;
    int opportunities = 0;

    boolean includeIncertaeSedis;

    List<Conflict> conflicts = new ArrayList<Conflict>();

    public ConflictAnalysis(Taxonomy input, Taxonomy ref) {
        
        this(input, ref, input.ingroupId, true);
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
                    Taxon tip = new Taxon(induced, refNode.name);    //may be null
                    if (refNode.id != null) tip.setId(refNode.id);
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
                                sel = new Taxon(induced, scan.name);
                                if (scan.id != null) sel.setId(scan.id);
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

    public static boolean maximizeWitness = true;

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

            Taxon witness = conode;
            if (maximizeWitness)
                // Witness should be largest congruent conode, not smallest
                while (witness.parent != null && (comap.get(witness.parent) == bounce))
                    witness = witness.parent;

            // If bounce and its parent both map to conode, then SUPPORTED_BY
            // Otherwise, PARTIAL_PATH_OF
            if (bounce.parent != null && (map.get(bounce.parent) == conode))
                return new Articulation(Disposition.PATH_SUPPORTED_BY, witness);
            else
                return new Articulation(Disposition.SUPPORTED_BY, witness);
        }
        if (node.parent == null) {
            System.err.format("Shouldn't happen 2 %s\n", node);
            return null; // shouldn't happen
        }
        if (conode.children == null) {
            System.out.format("** shouldn't happen\n");
            return null;
        }

        // ???? in (a,(b,c)) vs. (a,b,c)  (b,c) conflicts with a ????
        
        // Distinguish the conflict case from the resolution case.
        // If node conflicts with anything, it conflicts with one of conode's children
        // That is, if node either contains or excludes every child of
        // conode, then node resolves conode, otherwise it conflicts.
        for (Taxon cochild : conode.children) {
            Taxon back = comap.get(cochild);
            if (back == null)
                ;
            else if (back.mrca(node) == node)
                ;               // node includes cochild
            else if (!intersects(node, cochild, comap))
                ;               // node excludes cochild
            else
                // neither includes nor excludes
                return new Articulation(Disposition.CONFLICTS_WITH, cochild);
        }
        return new Articulation(Disposition.RESOLVES, conode);
    }

    // Check whether conode shares any tips with node.

    public boolean intersects(Taxon node, Taxon conode, Map<Taxon, Taxon> comap) {
        Taxon back = comap.get(conode); // image of conode in tree1
        if (back == null)
            return false;
        int hn = node.getDepth();
        int hb = back.getDepth();
        if (hn <= hb) {
            Taxon b = back;
            while (hb > hn) {
                b = b.parent;
                --hb;
            }
            if (b != node)
                return false;   // node and back are disjoint
            else
                return true;    // back descends from node
        } else {
            Taxon n = node;
            while (hn > hb) {
                n = n.parent;
                --hn;
            }
            if (n != back)
                return false;    // node and back are disjoint
            else {
                // node descends from back.  Uninformative
                if (conode.children != null)
                    for (Taxon cochild : conode.children)
                        if (intersects(node, cochild, comap))
                            return true;
                return false;
            }
        }
    }

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
