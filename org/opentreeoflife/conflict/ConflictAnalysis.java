
package org.opentreeoflife.conflict;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.QualifiedId;

public class ConflictAnalysis {

    // input is an input tree, ref is a 'reference' tree (either taxonomy or synthetic tree)
    public Taxonomy input = null;
    public Taxonomy ref = null;

    public Taxon inducedIngroup = null;       // node in ref induced by ingroup
    public Taxon ingroup = null;

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
            inducedIngroup = induce(ingroup, ref, map);
            // inducedIngroup = mrca in ref of all the shared tips in input
            if (inducedIngroup != null) {
                induce(inducedIngroup, input, comap);
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
        else 
            if (node.sourceIds != null) {
                QualifiedId qid = node.sourceIds.get(0);
                if (qid.prefix.equals("ott")) {
                    String ottid = qid.id;
                    Taxon refNode = ref.lookupId(ottid);
                    map.put(node, refNode);
                    comap.put(refNode, node);
                }
            }
    }

    class Conflict {
        Taxon node, bounce;
        Taxon[] outlier;
        Conflict(Taxon node, Taxon bounce, Taxon[] outlier) {
            this.node = node; this.bounce = bounce; this.outlier = outlier;
        }
        int badness() {
            return (distance(this.node, this.bounce) +
                    distance(this.outlier[0], this.bounce));
        }
        void print() {
            System.out.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                              node.name, node.count(),
                              bounce.name, bounce.count(),
                              outlier[0].name + " + " + outlier[1].name,
                              distance(node, bounce),
                              distance(outlier[0], bounce));
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
                    Taxon[] outlier = suspect(node);
                    if (outlier != null)
                        conflicts.add(new Conflict(node, bounce, outlier));
                    return bounce;
                }
            }
        }
        return null;
    }

    // Need a way to assign blame for paraphyly.
    // If the reference has ((a,b),d), and the input has ((a,d),b),
    // then we want to 'blame' the breakup of (a,b) on d.

    Taxon[] suspect(Taxon node) {
        Taxon conode = comap.get(node);
        if (conode == null) return null;
        return hunt(conode, node);
        // return hunt2(node, comap.get(node));
    }

    // Find a descendant of conode that is disjoint from
    // broken, but has a sibling that's not.

    Taxon[] hunt(Taxon conode, Taxon broken) {
        if (conode.children != null) {
            Taxon in = null, out = null;
            Taxon[] more = null;
            for (Taxon cochild : conode.children) {
                Taxon bounce = map.get(cochild);
                if (bounce != null) {
                    Taxon m = bounce.mrca(broken);
                    if (m == broken)
                        in = bounce;
                    else if (m != bounce)
                        out = bounce;
                    else if (more == null) {
                        Taxon[] probe = hunt(cochild, broken);
                        if (probe != null) more = probe;
                    }
                    if (in != null && out != null)
                        return new Taxon[]{out, in};
                }
            }
            if (more != null) return more;
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


}
