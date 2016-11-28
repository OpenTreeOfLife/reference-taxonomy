
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
import org.opentreeoflife.taxa.TaxonMap;
import org.opentreeoflife.smasher.Alignment;
import org.opentreeoflife.smasher.AlignmentByName;

public class ConflictAnalysis {

    // input is an input tree, ref is a 'reference' tree (either taxonomy or synthetic tree)
    public Taxonomy input;
    public Taxonomy ref;

    boolean includeUnplaced;
    TaxonMap tipMap;

    // The Taxonomy class allows multiple roots, but we can only deal with one
    public Taxon inputRoot;
    public Taxon refRoot;

    public Taxon ingroup = null;              // node in input, either ingroup or root
    public Taxon inducedRoot = null;          // node in ref induced by whole input
    public Taxon inducedIngroup = null;       // node in ref induced by ingroup

    public TaxonMap xmrca;   // input -> ref
    public TaxonMap coxmrca; // ref -> input

    public int conflicting = 0;
    public int opportunities = 0;

    public ConflictAnalysis(Taxonomy input, Taxonomy ref) {
        this(input, ref, true);
    }

    public ConflictAnalysis(Taxonomy input, Taxonomy ref, boolean includeUnplaced) {
        TaxonMap tipMap;
        if (input.idspace == null ?
            (input.idspace == ref.idspace) :
            input.idspace.equals(ref.idspace))
            // Input uses same idspace as ref.  Just look up using id.
            tipMap = new TaxonMap() {
                    public Taxon get(Taxon node) {
                        return ref.lookupId(node.id);
                    }
                };
        else
            // Source tree (nexson) vs. taxonomy (or similar).
            tipMap = new TaxonMap() {
                    public Taxon get(Taxon node) {
                        if (node.sourceIds != null)
                            for (QualifiedId qid : node.sourceIds)
                                if (qid.prefix.equals(ref.idspace))
                                    return ref.lookupId(qid.id);
                        return null;
                    }
                };
        init(input, ref, tipMap, includeUnplaced);
    }

    // This is used to analyze a study against the synthetic tree.
    // Taxon ids in the synthetic tree look like "ott123".

    public static ConflictAnalysis againstSynthesis(Taxonomy input, Taxonomy synth, boolean includeUnplaced) {
        TaxonMap tipMap;
        if ("ott".equals(input.idspace))
            // Taxonomy vs. synthetic tree
            tipMap = new TaxonMap() {
                    public Taxon get(Taxon node) {
                        if (node.id != null)
                            return synth.lookupId("ott" + node.id);
                        else
                            return null;
                    }
                };
        else
            // Source tree vs. synthetic tree
            tipMap = new TaxonMap() {
                    public Taxon get(Taxon node) {
                        if (node.sourceIds != null)
                            for (QualifiedId qid : node.sourceIds)
                                if (qid.prefix.equals("ott"))
                                    return synth.lookupId("ott" + qid.id);
                        return null;
                    }
                };
        return new ConflictAnalysis(input,
                                    synth,
                                    tipMap,
                                    includeUnplaced);
    }

    public ConflictAnalysis(Taxonomy input, Taxonomy ref, TaxonMap tipMap, boolean includeUnplaced) {
        init(input, ref, tipMap, includeUnplaced);
    }

    // tipMap maps input node to ref node.  Only used for tips and quasi-tips

    private void init(Taxonomy input, Taxonomy ref, TaxonMap tipMap, boolean includeUnplaced) {
        this.input = input;
        this.ref = ref;
        this.tipMap = tipMap;
        this.includeUnplaced = includeUnplaced;

        this.inputRoot = uniqueRoot(input);
        this.refRoot = uniqueRoot(ref);

        // Copy tips from tipMap into map.
        Map<Taxon, Taxon> map = new HashMap<Taxon, Taxon>();
        this.mapTips(this.inputRoot, map);

        // Initialize comap with inverse tip map.
        Map<Taxon, Taxon> comap = this.invertMap(map);

        // Populate map and comap with xmrcas for internal nodes.
        this.induce(map, comap);

        this.xmrca = taxonMap(map);
        this.coxmrca = taxonMap(comap);

        // Establish ingroups
        this.ingroup = input.ingroup;
        if (this.ingroup == null)
            this.ingroup = inputRoot;
        this.inducedIngroup = xmrca.get(this.ingroup);
        // inducedIngroup = mrca in ref of all the shared tips in input
    }

    // Convert Map to TaxonMap

    private TaxonMap taxonMap(Map<Taxon, Taxon> map) {
        return new TaxonMap() {
            public Taxon get(Taxon node) {
                return map.get(node);
            }
        };
    }

    Taxon uniqueRoot(Taxonomy tax) {
        Taxon root = null;
        for (Taxon r : tax.roots()) {
            if (root != null)
                System.err.format("** Cannot deal with multiple roots: %s %s %s\n", r, root, tax.getTag());
            else
                root = r;
        }
        if (root == null)
            System.err.format("** No root for %s\n", tax.getTag());
        return root;
    }

    // Compute comap from map

    Map<Taxon, Taxon> invertMap(Map<Taxon, Taxon> map) {
        Map<Taxon, Taxon> comap = new HashMap<Taxon, Taxon>();
        Set<Taxon> ambiguous = new HashSet<Taxon>(); // set of ambiguous ref nodes
        int collisions = 0;
        for (Taxon node : map.keySet()) {
            Taxon refnode = map.get(node);
            if (!ambiguous.contains(refnode)) {
                Taxon othernode = comap.get(refnode);
                if (othernode != null) {
                    ++collisions;
                    // Things one might do when there is a collision:
                    // 1. take the mrca
                    //     comap.put(refnode, othernode.mrca(node));
                    // 2. lexicographically least
                    //     if (node.id.compareTo(othernode.id) < 0) comap.put(refnode, othernode);
                    // 3. remove entirely
                    //     comap.remove(refnode); ambiguous.add(refnode);
                    // 4. first come first served
                    //     ;
                    if (true) {
                        if (node.id.compareTo(othernode.id) < 0)
                            comap.put(refnode, othernode);
                    } else {
                        comap.remove(refnode);
                        ambiguous.add(refnode);
                    } 
                } else
                    comap.put(refnode, node);
            }
        }
        if (collisions > 0)
            System.out.format("* %s collisions\n", collisions);
        return comap;
    }

    // Map tips bidirectionally
    // node is a node in the input tree

    boolean mapTips(Taxon node, Map<Taxon, Taxon> map) {
        boolean anyMapped = false;
        if (node.hasChildren()) {
            for (Taxon child : node.children)
                anyMapped = mapTips(child, map) || anyMapped;
        }
        if (anyMapped)
            return true;
        else {
            Taxon refnode = this.tipMap.get(node);
            if (refnode != null
                && (includeUnplaced || refnode.isPlaced())) {
                map.put(node, refnode);
                return true;
            } else
                return false;
        }

    }

    // requires mutable maps

    boolean induce(Map<Taxon, Taxon> map, Map<Taxon, Taxon> comap) {
        // Get the two mrca-based maps.  First the input->ref, then
        // (starting at induced root) the ref->input map.
        // Look for cases where mapping A-B-A goes to an ancestor of the start node
        // (descendant is OK, that's sort of like monotypy)
        if (this.inputRoot == null) {
            // System.err.format("** No tree %s\n", input.getTag());
            return false;
        } else {
            this.inducedRoot = induce(this.inputRoot, map);
            //System.out.format("| mapped %s, comapped %s\n", map.size(), comap.size());
            if (this.inducedRoot == null)
                return false; // System.err.format("** Nothing maps\n");
            else {
                induce(this.inducedRoot, comap);
                return true;
            }
        }
    }

    // This runs twice, once in each direction input->ref / ref->input
    Taxon induce(Taxon node, Map<Taxon, Taxon> map) {
        Taxon mrca = map.get(node);
        if (mrca == null) {
            for (Taxon child : node.getChildren()) {
                Taxon x = induce(child, map);
                if (x != null)
                    if (mrca == null)
                        mrca = x;
                    else
                        mrca = mrca.mrca(x);
            }
            if (mrca != null)
                map.put(node, mrca);
        }
        return mrca;
    }

    // ----- end of initialization code. -----

    // Set the name of any input node that matches up with a node in the reference tree.
    // Also add ott:nnnnn as a source id for any named node, so we can tell which OTT taxon was matched.

    void setNames(Taxon conode) {
        if (conode.children != null) {
            conode.name = null;
            conode.sourceIds = null;
            Taxon node = xmrca.get(conode); // in reference (taxonomy or synth)
            if (node != null) {
                Taxon cobounce = coxmrca.get(node);
                if (cobounce == conode) {
                    conode.name = node.name;
                    conode.addSourceId(new QualifiedId(ref.getIdspace(), node.id));
                }
            }
            for (Taxon child : conode.children)
                setNames(child);
        }
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

    // Apply to a node in input (e.g. ingroup)
    public int countOtus(Taxon node) {
        if (node.children != null) {
            int total = 0;
            for (Taxon child : node.children)
                total += countOtus(child);
            return total;
        } else if (xmrca.get(node) != null)
            return 1;
        else
            return 0;
    }

    // Tree derived from ref with tips from input
    Taxonomy inducedTree() {
        Set<Taxon> seen = new HashSet<Taxon>(); // set of ref nodes
        Map<Taxon, Taxon> selected = new HashMap<Taxon, Taxon>(); // map from ref to induced
        Taxonomy induced = new SourceTaxonomy(ref.getIdspace());
        // Create node selection
        for (Taxon node : inputRoot.descendants(true))
            if (node.children == null) {
                Taxon refNode = xmrca.get(node);
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
        System.out.format("%s nodes in induced tree (%s seen)\n", selected.size(), seen.size());
        // Set parent pointers for all nodes
        int nroots = 0;
        for (Taxon refNode : selected.keySet()) {
            Taxon node = selected.get(refNode);
            boolean rootp = true;
            // Find parent of node
            for (Taxon scan = refNode.parent; scan != null; scan = scan.parent) {
                Taxon p = selected.get(scan);
                if (p != null) {
                    node.setParent(p, "induced");
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
            return articulation(node, xmrca, coxmrca);
        else
            return articulation(node, coxmrca, xmrca);
    }

    public static boolean maximizeWitness = true;

    public static Articulation articulation(Taxon node, TaxonMap xmrca, TaxonMap coxmrca) {
        Taxon conode = xmrca.get(node); // usually in ref. taxonomy
        if (conode == null)
            return null;
        Taxon bounce = coxmrca.get(conode); // back in original taxonomy
        if (bounce == null) {
            System.err.format("Shouldn't happen 1 %s\n", node);
            return null; // shouldn't happen
        }
        if (node.mrca(bounce) == node) {  // bounce <= node?

            Taxon witness = conode;
            if (maximizeWitness)
                // Witness should be largest congruent conode, not smallest
                while (witness.parent != null && (coxmrca.get(witness.parent) == bounce))
                    witness = witness.parent;

            // If bounce and its parent both map to conode, then SUPPORTED_BY
            // Otherwise, PARTIAL_PATH_OF
            if (bounce.parent != null && (xmrca.get(bounce.parent) == conode))
                return new Articulation(Disposition.PATH_SUPPORTED_BY, witness);
            else
                return new Articulation(Disposition.SUPPORTED_BY, witness);
        }
        if (node.parent == null) {
            System.err.format("Shouldn't happen 2 %s\n", node);
            return null; // shouldn't happen
        }

        // ???? in (a,(b,c)) vs. (a,b,c)  (b,c) conflicts with a ????
        
        // Distinguish the conflict case from the resolution case.
        // If node conflicts with anything, it conflicts with one of conode's children
        // That is, if node either contains or excludes every child of
        // conode, then node resolves conode, otherwise it conflicts.
        for (Taxon cochild : conode.getChildren()) {
            Taxon back = coxmrca.get(cochild);
            if (back == null)
                ;
            else if (back.mrca(node) == node)
                ;               // node includes cochild
            else if (intersects(node, cochild, coxmrca) == null)
                ;               // node excludes cochild
            else
                // neither includes nor excludes
                return new Articulation(Disposition.CONFLICTS_WITH, cochild);
        }
        return new Articulation(Disposition.RESOLVES, conode);
    }

    // Check whether conode shares any tips with node.

    public static Taxon intersects(Taxon node, Taxon conode, TaxonMap coxmrca) {
        Taxon back = coxmrca.get(conode); // image of conode in tree1
        if (back == null)
            return null;
        int hn = node.getDepth();
        int hb = back.getDepth();
        if (hn <= hb) {
            Taxon b = back;
            while (hb > hn) {
                b = b.parent;
                --hb;
            }
            if (b != node)
                return null;   // node and back are disjoint
            else
                return b;    // back descends from node
        } else {
            Taxon n = node;
            while (hn > hb) {
                n = n.parent;
                --hn;
            }
            if (n != back)
                return null;    // node and back are disjoint
            else {
                // node descends from back.  Uninformative
                if (conode.children != null)
                    for (Taxon cochild : conode.children) {
                        Taxon b = intersects(node, cochild, coxmrca);
                        if (b != null)
                            return b;
                    }
                return null;
            }
        }
    }

    public static void main(String[] argv) throws Exception {
        Taxonomy t1 = Taxonomy.getTaxonomy("((a,b)ab,(c,d)cd)top", "t1");
        Taxonomy u = Taxonomy.getTaxonomy("(a,(b,(c,d)cd)bcd)top", "u");
        Alignment a = new AlignmentByName(t1, u);
        a.align();
        u.eventLogger.eventsReport("| ");
        ConflictAnalysis c = new ConflictAnalysis(t1, u, a, true);
        for (Taxon t : t1.taxa())
            System.out.format("%s %s\n", t, c.articulation(t));
    }

}
