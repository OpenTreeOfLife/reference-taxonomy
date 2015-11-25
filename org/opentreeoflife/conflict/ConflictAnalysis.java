
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
    public Taxonomy input = null;
    public Taxonomy ref = null;

    public Taxon inducedRoot = null;          // node in ref induced by whole input
    public Taxon inducedIngroup = null;       // node in ref induced by ingroup

    public Taxon ingroup = null;              // node in input

    // Taxonomy class allows multiple roots, but we can only deal with one
    Taxon inputRoot = null;

    Map<Taxon, Taxon> map = new HashMap<Taxon, Taxon>(); // input -> ref
    Map<Taxon, Taxon> comap = new HashMap<Taxon, Taxon>(); // ref -> input

    int conflicting = 0;
    int opportunities = 0;

    List<Conflict> conflicts = new ArrayList<Conflict>();

    public ConflictAnalysis(Taxonomy input, Taxonomy ref, String ingroup) {
        this.input = input;
        this.ref = ref;
        for (Taxon r : input.roots()) {
            if (inputRoot != null)
                System.out.format("** Cannot deal with multiple roots: %s %s\n", r, inputRoot);
            else
                inputRoot = r;
        }
        if (inputRoot == null)
            System.out.format("** No root for %s\n", input.getTag());
        this.ingroup = input.lookupId(ingroup);
        if (this.ingroup == null)
            this.ingroup = inputRoot;
    }

    // One measure of conflict is #conflicting divided by #opportunities
    public double conflictivity() {
        if (opportunities == 0)
            return 0.0d;
        else
            return ((double)conflicting) / ((double)opportunities);
    }

    public double unmappedness() {
        if (ingroup == null)
            return 1.0d;
        else
            return 1.0d - (((double)countOtus(ingroup)) / ((double)ingroup.tipCount()));
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
        if (inducedIngroup != null) {
            System.out.format("%s nodes, %s tips, %s mapped, %s mapped from input to ref, %s from ref to input\n",
                              input.count(), input.tipCount(), countOtus(ingroup),
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

    ConflictAnalysis analyze() {
        // Get the two mrca-based maps.  First the input->ref, then
        // (starting at induced root) the ref->input map.
        // Look for cases where mapping A-B-A goes to an ancestor of the start node
        // (descendant is OK, that's sort of like monotypy)
        if (ingroup == null)
            System.out.format("** No input tree %s\n", input.getTag());
        else {
            mapInputTips(inputRoot);
            inducedRoot = induce(inputRoot, ref, map);
            inducedIngroup = map.get(ingroup);
            // inducedIngroup = mrca in ref of all the shared tips in input
            if (inducedRoot != null) {
                induce(inducedRoot, input, comap);
                findConflicts(inducedIngroup);
                Collections.sort(conflicts, worseThan);
            }
        }
        return this;
    }

    // Find OTT ids for OTUs that have them, and use them to map
    // bidirectionally between input tree tips and ref tips
    void mapInputTips(Taxon node) {
        if (node.children != null)
            for (Taxon child : node.children)
                mapInputTips(child);
        else {
            if (node.sourceIds != null) {
                QualifiedId qid = node.sourceIds.get(0);
                if (qid.prefix.equals("ott")) {
                    String ottid = qid.id;
                    Taxon refNode = ref.lookupId(ottid);
                    if (refNode != null) {
                        map.put(node, refNode);
                        comap.put(refNode, node);
                        return;
                    }
                }
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
        }
    }

    class Conflict {
        Taxon outlier, inlier;
        Taxon node, bounce;
        Conflict(Taxon outlier, Taxon inlier, Taxon node, Taxon bounce) {
            this.outlier = outlier; this.inlier = inlier;
            this.node = node; this.bounce = bounce;
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

    // Recursive.  Return value is 'bounce' in ref 
    Taxon findConflicts(Taxon node) {
        Taxon conode = comap.get(node);
        if (conode != null) {
            Taxon bounce = map.get(conode);
            if (bounce != null) {
                // Preorder traversal
                boolean covered = false;
                if (node.children != null)
                    for (Taxon child : node.children) {
                        Taxon childBounce = findConflicts(child);
                        if (childBounce == bounce)
                            covered = true;
                    }
                // Now deal with this node
                ++opportunities;
                if (node.mrca(bounce) != node) {
                    ++conflicting;
                    if (!covered) {
                        Conflict conflict = suspect(node, bounce);
                        if (conflict != null)
                            conflicts.add(conflict);
                    }
                    return bounce;
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
        // return hunt2(broken, comap.get(broken));
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
                        return new Conflict(out, in, broken, brokenbounce);
                }
            }
            return more;
        }
        return null;
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
        return map.get(node);
    }

    // Set the name of any node that matches up with taxonomy

    void setNames() {
        setNames(inputRoot);
    }

    void setNames(Taxon conode) {
        if (conode.children != null) {
            conode.name = null;
            conode.sourceIds = null;
            Taxon node = map.get(conode); // in taxonomy or synth
            if (node != null) {
                Taxon cobounce = comap.get(node);
                if (cobounce == conode) {
                    conode.name = node.name;
                    conode.addSourceId(new QualifiedId(ref.getTag(), node.id));
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

                    // Scan rootward through taxonomy from every tip (OTU)
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

}
