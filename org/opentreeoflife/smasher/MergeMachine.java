package org.opentreeoflife.smasher;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.io.PrintStream;
import java.io.IOException;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.QualifiedId;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.TaxonMap;

import org.opentreeoflife.conflict.ConflictAnalysis;
import org.opentreeoflife.conflict.Articulation;
import org.opentreeoflife.conflict.Disposition;

// Create new nodes in the union taxonomy, when source nodes don't
// match, and hook them up appropriately.

class MergeMachine {

    UnionTaxonomy union;
    Taxonomy source;
    Alignment alignment;
    Map<String, Integer> summary;
    private Map<Taxon, Op> plan = new HashMap<Taxon, Op>();
    private Map<Taxon, Op> coplan = new HashMap<Taxon, Op>();
    TaxonMap coxmrca;
    boolean bluster;

    MergeMachine(Taxonomy source, UnionTaxonomy union, Alignment a, int blustery, Map<String, Integer> summary) {
        this.source = source;
        this.union = union;
        this.alignment = a;
        this.summary = summary;
        this.bluster = blustery > 1;
    }

    TaxonMap inverseAlign = new TaxonMap() {
            public Taxon get(Taxon unode) {
                return alignment.invert(unode);
            }
        } ;

	void augment() {

        union.reset();
        union.eventLogger.resetEvents();

        // No need to inferFlags() at this point since merge doesn't
        // pay attention to inferred flags at any point (just the
        // proper ones)

        boolean windyp = (union.count() > 1);

        // Add heretofore unaligned nodes to union
        if (windyp)
            System.out.format("--- Merging new nodes from %s into union ---\n", source.getTag());
        int startroots = union.rootCount();
        int startcount = union.count();

        // Pass 1: create an Op for every in-between node, and set its subproblem.
        if (bluster) System.out.format("# Pass 1\n");
        plan = new HashMap<Taxon, Op>();
        coplan = new HashMap<Taxon, Op>();
        Taxon coroot = setSubproblems(source.forest, null, alignment, plan);
        if (coroot != null) {
            Taxon root = setSubproblems(coroot, null, inverseAlign, coplan);

            if (bluster)
                System.out.format("# root %s, coroot %s, plan size %s %s\n",
                                  root, coroot, plan.size(), coplan.size());

            // Pass 2: do cross-mrcas for all in-between nodes
            if (bluster) System.out.format("# Pass 2\n");
            doXmrcas(root, alignment, plan);
            doXmrcas(coroot, inverseAlign, coplan);

            // Pass 3: get articulations
            // xcomrca is a TaxonMap that takes a union node to its xmrca in the source.
            if (bluster) System.out.format("# Pass 3\n");
            coxmrca = new TaxonMap() {
                    public Taxon get(Taxon node) {
                        Op chop = coplan.get(node);
                        if (chop != null)
                            return chop.xmrca;
                        else
                            return inverseAlign.get(node);
                    }
                };
            articulate(root, coxmrca, alignment, plan);
        }

        // Pass 4: Edit the union to add copies of nodes from source
        if (bluster) System.out.format("# Pass 4\n");
        alignment.setAnswer(source.forest, Answer.yes(source.forest, union.forest, "taxonomy-merge", null));
        this.augment(source.forest);

        // Passes 5: Finish up
        if (bluster) System.out.format("# Pass 5\n");
        transferProperties(source);
        copyMappedSynonyms(); // this = union

        if (windyp) {
            report(source, startroots, startcount);
            if (union.count() == 0)
                source.forest.show();
            union.eventLogger.eventsReport("|   ");
            System.out.format("| Ended with: %s roots, %s taxa\n",
                              union.rootCount(), union.count());
        }
        if (union.numberOfNames() < 10)
            System.out.println(" -> " + union.toNewick());
	}

    // An Op is created for each taxonomy node that has an aligned
    // node as ancestor, and at least one aligned node as a descendant.

    class Op {
        Taxon subproblem = null; // in same taxonomy
        int mapped = 0, unmapped = 0;
        boolean reflectedp = false;
        boolean consistentp = true;
        boolean mrcaIsChild = false;
        Taxon xmrca = null;      // mrca in target of alice, bob, and others
        Taxon target = null;     // match

        // Not set up any more
        Taxon alice = null, bob = null, zelda = null;

        Op(Taxon subproblem) { this.subproblem = subproblem; }
        String comment() {
            return String.format("mrca = %s", xmrca.name);
        }
    }

    // Pass 1: cache subproblems and compute virtual roots.
    // Returns mrca of aligned nodes in partner taxonomy
    Taxon setSubproblems(Taxon node, Taxon subproblem, TaxonMap align, Map<Taxon, Op> plan) {

        Taxon tnode = align.get(node);
        if (tnode != null)
            subproblem = tnode;

        Taxon mrca = tnode;
        for (Taxon child : node.getChildren()) {
            Taxon sub = setSubproblems(child, subproblem, align, plan);
            if (sub != null)
                if (mrca == null)
                    mrca = sub;
                else
                    mrca = mrca.mrca(sub);
        }
        if (mrca == null) return null;

        if (tnode == null) {
            Op op = new Op(subproblem);
            plan.put(node, op);
        }
        return mrca;
    }

    // Pass 2: compute cross-MRCAs and cache them in Op records

    // Node is in source taxonomy.  Cf. ConflictAnalysis.induce()

    private void doXmrcas(Taxon node,
                          TaxonMap align,
                          Map<Taxon, Op> plan) {
        Taxon asub = align.get(node);
        if (asub != null)
            for (Taxon child : node.getChildren())
                doXmrcas2(child, asub, align, plan);
        else
            for (Taxon child : node.getChildren())
                doXmrcas(child, align, plan);
    }

    private Taxon doXmrcas2(Taxon node, // returns xmrca
                            Taxon asub,
                            TaxonMap align,
                            Map<Taxon, Op> plan) {
        Taxon aligned = align.get(node);
        if (aligned != null) {
            doXmrcas(node, align, plan);
            if (aligned.mrca(asub) == asub)
                return aligned;
            else {
                if (node.name.hashCode() % 20 == 7)
                    System.out.format("## Ejecting %s which is outside of %s\n", node, asub);
                return asub;
            }
        } else {
            Op op = plan.get(node);
            if (op == null) return null;

            Taxon mrca = null;
            int mapped = 0, unmapped = 0;
            boolean mrcaIsChild = false;
            for (Taxon child : node.getChildren()) {
                Taxon x = doXmrcas2(child, asub, align, plan);
                if (child.isPlaced()) {
                    if (x == null)
                        op.unmapped++;
                    else {
                        op.mapped++;
                        if (mrca == null) {
                            mrca = x;
                            mrcaIsChild = true;
                        } else {
                            Taxon mrca2 = mrca.mrca(x);
                            if (mrca2 != mrca) {
                                mrca = mrca2;
                                mrcaIsChild = false;
                            }
                        }
                    }
                }
            }
            if (mrca == null) {
                System.out.format("** shouldn't happen: null mrca for %s\n", node);
                return null;
            }
            op.xmrca = mrca;    // cache it
            op.mapped = mapped;
            op.unmapped = unmapped;
            op.mrcaIsChild = mrcaIsChild;
            return mrca;
        }
    }

    /*private int winners, smaller, larger;
        winners = smaller = larger = 0;
        if (winners + smaller + larger > 0 && taxy == source)
            System.out.format("| MRCAs: %s match, %s smaller than aligned, %s larger than aligned\n",
                              winners, smaller, larger);
    */

    // Pass 3 - compute merge-mapping information
    Set<Taxon> targeted = new HashSet<Taxon>();

    void articulate(Taxon node, TaxonMap coxmrca, TaxonMap align, Map<Taxon, Op> plan) {
        Taxon tnode = align.get(node);
        Op op = plan.get(node);
        if (tnode != null || op != null) {
            for (Taxon child : node.getChildren())
                articulate(child, coxmrca, align, plan);
            if (op != null) {
                if (op.xmrca == null)
                    System.err.format("** Null conode 2: %s\n", op.xmrca);
                op.target = chooseTarget(node, op);    // the place that gets new & unplaced children
                op.consistentp = consistentp(node, op.xmrca, coxmrca);
                op.reflectedp = reflectedp(op.target, node);
                targeted.add(op.target);
            }
        }
    }

    boolean reflectedp(Taxon conode, Taxon node) {
        Taxon bounce = coxmrca.get(node);
        if (bounce != null)
            return bounce.mrca(node) == node;
        else
            return false;
    }

    // Two ways of matching.  Try to match them.
    Taxon chooseTarget(Taxon node, Op op) {
        Taxon conode = op.xmrca;
        if (conode == null)
            System.err.format("** Null conode 1: %s\n", conode);
        if (op.mrcaIsChild && conode.parent != null) conode = conode.parent;
        Taxon target = maybeElevate(node, conode, byname(node));
        if (target == null)
            throw new RuntimeException(String.format("** Null target for %s\n", node));
        return target;
    }

    // Node could potentially map to an 'equivalent' ancestor
    //  (node -> conode -> bounce <= node)
    //      xmrca    coxmrca   descendsFrom

    Taxon maybeElevate(Taxon node, Taxon conode, Taxon stop) {
        if (conode == null)
            System.err.format("** Null conode: %s\n", conode);
        Taxon scan = conode;
        while (true) {
            boolean nu = bluster && (scan != conode);
            if (conode == stop) {
                if (nu)
                    System.out.format("# By-name and by-xmrca coincide at %s\n", conode);
                break;
            }
            if (nu)
                System.out.format("# Trying parent %s of %s... %s\n",
                                  scan, conode, reflectedp(scan, node));
            if (!reflectedp(scan, node))
                break;
            conode = scan;
            scan = scan.parent;
            if (scan == null)
                return conode;
        }
        return conode;
    }

    Taxon byname(Taxon node) {
        Answer byname = alignment.findAlignment(node);
        if (byname != null && byname.isYes())
            return byname.target;
        else
            return null;
    }


    // True if disjoint, same, or resolution.  false if conflict.
    // cf. ConflictAnalysis.articulation.
    boolean consistentp(Taxon node, Taxon x, TaxonMap coxmrca) {
        if (node == null) System.err.format("** Null node checking consistentp with %s\n", x);
        if (x == null) System.err.format("** Null conode checking consistentp of %s\n", node);
        for (Taxon cochild : x.getChildren()) {
            Taxon back = coxmrca.get(cochild);
            if (back == null)
                ;
            else if (back.mrca(node) == node)
                ;
            else if (ConflictAnalysis.intersects(node, cochild, coxmrca) == null)
                ;
            else
                return false;
        }
        return true;
    }


	// 3799 conflicts as of 2014-04-12
	void reportConflict(Taxon node, Op op) {
        if (op.xmrca.taxonomy == union && op.alice != null) {
            union.conflicts.add(new Conflict(node, op.alice, op.bob, op.xmrca));
            if (union.markEvent("reported conflict"))
                System.out.format("| Conflict %s %s\n", union.conflicts.size(), node);
        }
	}

    void reportWayward(Taxon node, Op op, TaxonMap map) {
        if (op.xmrca.taxonomy == union &&
            (node.count() > 500000 ||
             (op.unmapped > 0 &&
              union.markEvent("wayward")))) {

            Taxon scan = node;
            while (scan.parent != null && map.get(scan) == null)
                scan = scan.parent;
            if (scan == null) return;
            Taxon bridge = map.get(scan);
            Taxon[] div = bridge.divergence2(op.xmrca);

            Taxon big = null;
            if (div[0] != null) big = div[0].parent;
            if (div[1] != null) big = div[1].parent;

            String path0 = "-", path1 = "-";
            if (div[0] != null) path0 = String.format("| wayward %s < %s", node, div[0].name);
            if (div[1] != null) path1 = String.format("| wayward %s < %s", op.xmrca, div[1].name);

            System.out.format("| %s < [%s | %s] < %s [%s:%s]\n",
                              op.alice.name,
                              path0, path1,
                              (big == null ? "-" : big.name),
                              op.mapped, op.unmapped);
        }
    }

	/* Method called on every node in the source taxonomy.  If node is
       aligned, usually we create a new target node, and align the
       source node to it.  The new target node will initially be
       detached, and becomes attached to the target taxonomy when its
       parent node is processed.
       */
	void augment(Taxon node) {

        // preorder
        for (Taxon child: node.getChildren())
            augment(child);

        // merge answer, not align answer
        Answer m = null;

        int flag = 0;

        // see what alignment has to say
        Answer a = alignment.getAnswer(node);

        if (a != null) {
            if (a.isYes()) {
                Taxon target = a.target;
                // node is aligned by name
                if (node.children == null)
                    m = accept(node, target, "aligned/tip");
                else
                    m = accept(node, target, "aligned/internal");
            } else if (a.value > Answer.HECK_NO && !node.hasChildren()) {
                // equivocal noinfo or no - ambiguous - throw it away
                // Don't create homonym if it's equivocal (too close a match)
                // HECK_NO < NO < NOINFO < YES   (sorry)
                m = Answer.no(node, null, a.reason, a.witness);
                union.mergeDetails.add(m);
            } else {
                m = acceptNew(node, "new/polysemy");
            }
		} else {
            Op op = plan.get(node);

            // Examine aligned parents of the children
            // Consult merge plan - cf. doXmrcas, above
            if (op == null) {
                // graft
                if (node.children == null)
                    m = acceptNew(node, "new/tip");
                else
                    m = acceptNew(node, "new/graft");

            } else {
                Taxon target = op.target;
                if (target == null)
                    System.err.format("** Null target in augment: %s\n", target);
                if (op.consistentp) {
                    if (op.reflectedp) {
                        // if some ancestors and/or descendants of node
                        // are also reflected (with the same target),
                        // choose which ones are to match.
                        // descendants will be processed first of course.
                        m = accept(node, target, "match/by-membership");
                    } else if (resolvable(target)) {
                        // resolves target
                        m = acceptNew(node, "new/resolves");
                        if (takeOld(m.target, node, target))   // Move from mrca down to new resolution node
                            // ((Wg,Wj,Wr)W)z + ((Wj)N,(Wr,Wt)W)z = ((Wg,(Wj)N,Wr,Wt)W)z ?
                            target = target.parent;
                        setParent(m.target, target, "resolution");
                    } else {
                        // absorption
                        m = reject(node, "reject/absorbed", op, target, Taxonomy.MERGED);
                    } 
                } else {
                    // conflict
                    m = reject(node, "reject/conflict", op, target, Taxonomy.INCONSISTENT);
                    flag = Taxonomy.UNPLACED;
                }
                union.mergeDetails.add(m);
            }
        }
        takeOn(node, m.target, flag);
        // try to do more work here. e.g. alignment.setAnswer(node, m);
        // if node has children but m.target doesn't, drop m.target
        tick(m);
    }

    // This is not right at all.
    // Trying to check whether there are unmatched children of target.

    boolean resolvable(Taxon target) {
        for (Taxon cochild : target.getChildren()) {
            if (cochild.isPlaced()) {
                boolean q = targeted.contains(cochild);
                if (bluster)
                    System.out.format("# Targeted check: %s in %s -> %s\n", cochild, target, q);
                if (!q) return false;
            }
        }
        return true;
    }

    // Compare source node to union node

    Answer accept(Taxon node, Taxon target, String reason) {
        return Answer.yes(node, target, reason, null);
    }

    public boolean MAKE_FAKES = false;

    // Reject an unaligned node because it is merged [absorbed], conflict, or ambiguous

    Answer reject(Taxon node, String reason, Op op, Taxon target, int flag) {
        // Could leave lub behind as a forwarding address...
        if (MAKE_FAKES) {
            Answer fake = acceptNew(node, reason); // does setAnswer
            fake.witness = op.comment(); // kludge
            fake.target.addFlag(flag);
            fake.target.setParent(target, reason);
            fake.target.rename("??" + node.name);
        }
        return Answer.noinfo(node, target, reason, null);
    }

    // Node is not aligned; copy it over

    Answer acceptNew(Taxon node, String reason) {
        if (alignment.getTaxon(node) != null)
            System.err.format("** Shouldn't happen - acceptNew %s %s\n", node, reason);

        // dup makes the new node placed, iff the source node is.
        // various other properties carry over as well.
        Taxon newnode = union.dupWithoutId(node, reason);

        if (bluster)
            System.out.format("# acceptNew %s: %s -> %s\n", reason, node, newnode);

        Answer answer = Answer.yes(node, newnode, reason, null);

        alignment.setAnswer(node, answer);

        answer.maybeLog(union);
        newnode.addSource(node);
        return answer;
	}

    // implement a resolution
    boolean takeOld(Taxon newnode, Taxon node, Taxon target) {
        boolean any = false;
        for (Taxon child: node.children) {
            Taxon childTarget = alignment.getTaxon(child);
            if (childTarget != null && !childTarget.isDetached() && childTarget.isPlaced())
                changeParent(childTarget, newnode, 0, "takeOld");
            if (childTarget == target)
                any = true;
        }
        return any;
    }

    void changeParent(Taxon uchild, Taxon target, int flags, String reason) {
        if (bluster)
            System.out.format("# %s: change %s parent from %s to %s\n", reason, uchild, uchild.parent, target);
        uchild.changeParent(target, flags, reason); // if unplaced, stay unplaced
    }

    void setParent(Taxon uchild, Taxon target, String reason) {
        if (bluster)
            System.out.format("# %s: set %s parent to %s\n", reason, uchild, target);
        uchild.setParent(target, reason); // if unplaced, stay unplaced
    }

    // Set parent pointers of aligned targets of children of source to target.

    Taxon takeOn(Taxon source, Taxon target, int flags) {
        for (Taxon child: source.getChildren()) {
            Taxon uchild = alignment.getTaxon(child);
            if (uchild == null)
                ;               // conflict, merged, ambiguous, ...
            else if (uchild.noMrca())
                ;               // it's a root
            else if (uchild.isDetached()) {
                // "new" child
                if (target == uchild)
                    ;               // Lacrymaria Morganella etc. - shouldn't happen
                else if (target.descendsFrom(uchild)) {
                    System.err.format("** Adoption would create a cycle\n");
                    System.out.format("%s ?< ", uchild);
                    target.showLineage(uchild.parent);
                } else {
                    //if (??child??.isRoot())
                    //    child.markEvent("placed-former-root");
                    uchild.addFlag(flags);
                    setParent(uchild, target, "takeOn"); // if unplaced, stay unplaced
                }
            } else if (!uchild.isPlaced()) {
                // "old" child maybe not well placed in union.  consider moving it
                // is target a better (more specific) placement for uchild than uchild's current parent?
                if (!target.descendsFrom(uchild.parent))
                    child.markEvent("not-placed/does-not-descend");
                else if (target == uchild.parent) {
                    // A placement here could promote an unplaced taxon in union to placed...
                    // sort of dangerous, because lower-priority taxonomies tend to be unreliable
                    if (flags > 0) {
                        child.markEvent("not-placed/already-unplaced");
                    } else {
                        // System.out.format("| %s not placed because %s goes to %s\n", uchild, source, uchild.parent);
                        child.markEvent("not-placed/same-taxon");
                    }
                } else if (target.descendsFrom(uchild)) {
                    if (false)
                        System.out.format("| Moving %s from %s to %s (says %s) would lose information\n",
                                          uchild, uchild.parent, target, source);
                    child.markEvent("not-placed/would-lose-information");
                } else if (uchild.isRoot() && child.isPlaced()) {
                    System.out.format("| %s moved from root to %s because %s\n", uchild, target, source);
                    Answer.noinfo(child, null, "promoted/from-root", target.name).maybeLog(union);
                    changeParent(uchild, target, 0, "moved from root");
                } else {
                    //System.out.format("| %s moved to %s because %s, was under %s\n", uchild, target, source, uchild.parent);
                    Answer.noinfo(child, null, "promoted/internal", target.name).maybeLog(union);
                    changeParent(uchild, target, flags | (child.properFlags & Taxonomy.INCERTAE_SEDIS_ANY), "promoted");
                }
            }
        }
        return target;
    }

	// Propogate synonyms from source taxonomy to union or selection.
	// Some names that are synonyms in the source might be primary names in the union,
	//	and vice versa.
	public void copyMappedSynonyms() {
		int count = 0;
        for (Taxon taxon : source.taxa()) {
            Taxon targetTaxon = alignment.getTaxon(taxon);
            if (targetTaxon == null) continue;
            count += taxon.copySynonymsTo(targetTaxon);
        }
		if (count > 0)
			System.out.format("| Added %s synonyms\n", count);
	}

    // Called on source taxonomy to transfer flags, rank, etc. to union taxonomy
    public void transferProperties(Taxonomy source) {
        for (Taxon node : source.taxa()) {
            // consider also getting properties from lumped source nodes
            Taxon unode = alignment.getTaxon(node);
            if (unode != null)
                transferProperties(node, unode);
        }

        // Hack for dealing with NCBI taxon merges
        int count = 0;
        for (String id: source.allIds()) {
            Taxon node = source.lookupId(id);
            if (node != null && !node.id.equals(id)) {
                Taxon unode = alignment.getTaxon(node);
                if (unode != null) {
                    union.indexByQid(unode,
                                     new QualifiedId(source.getIdspace(), id));
                    ++count;
                }
            }
        }
        if (count > 0)
            System.out.format("| Transferred %s id aliases\n", count);
    }

    // This is used when the union node is NOT new

    public void transferProperties(Taxon node, Taxon unode) {
        if (unode.name == null && node.name != null)
            unode.setName(node.name);

		if (unode.rank == Rank.NO_RANK || unode.rank == Rank.CLUSTER_RANK)
            unode.rank = node.rank;

		unode.addFlag(node.flagsToAdd(unode));

        // No change to hidden or incertae sedis flags.  Union node
        // has precedence.

        unode.addSource(node);
        // see https://github.com/OpenTreeOfLife/reference-taxonomy/issues/36
	}

    // Should be called exactly once for every node in the source taxonomy
    void tick(Answer m) {
        String action = m.reason;
        Integer count = summary.get(action);
        if (count == null) count = 0;
        summary.put(action, count + 1);
        if (bluster)
            System.out.format("# m %s\n", m);
    }

    Map<String, Integer> reasonCounts = new HashMap<String, Integer>();
    List<String> reasons = new ArrayList<String>();

    void report(Taxonomy source, int startroots, int startcount) {
        System.out.format("| Started with: %s roots, %s taxa + %s source roots, %s source taxa\n",
                          startroots,
                          startcount,
                          source.rootCount(),
                          source.count());
        for (Taxon node : source.taxa()) {
            String reason;
            Answer answer = alignment.getAnswer(node);
            if (answer == null) reason = "** no answer";
            else {
                reason = answer.reason;
                if (reason == null) reason = "** no reason";
            }
            Integer count = reasonCounts.get(reason);
            if (count == null) {
                reasonCounts.put(reason, 1);
                reasons.add(reason);
            } else
                reasonCounts.put(reason, count + 1);
        }
    }

}

class Conflict {
	Taxon node;				// in source taxonomy
	Taxon alice, bob;				// children, in union taxonomy
    Taxon aliceX, bobX;
    Taxon mrca;
    boolean isHidden;
	Conflict(Taxon node, Taxon alice, Taxon bob, Taxon mrca) {
		this.node = node;
        this.alice = alice;
        this.bob = bob;
        this.mrca = mrca;
        this.isHidden = node.isHidden();

        Taxon[] div = alice.divergence2(bob);
        this.aliceX = div[0];
        this.bobX = div[1];
	}
    static String formatString = ("%s\t%s\t" + // id, name
                                  "%s\t%s\t" + // a, aname
                                  "%s\t%s\t" + // b, bname
                                  "%s\t%s\t%s"); // mrca, depth, visible
    static void printHeader(PrintStream out) throws IOException {
		out.format(Conflict.formatString,
                   "para_id",
                   "para", 
                   "a", "a_ancestor",
                   "b", "b_ancestor",
				   "mrca",
				   "depth",
                   "visible");
        out.println();
    }

	public String toString() {
		// cf. Taxon.mrca
        try {
            Taxon[] div = null;
            if (div != null) {
                Taxon aliceX = div[0];
                Taxon bobX = div[1];
                int da = aliceX.getDepth() - 1;
                String m = (aliceX.parent == null ? "-" : aliceX.parent.name);
                return String.format(formatString,
                                     node.getQualifiedId(),
                                     node.name, 
                                     alice.name, aliceX.name,
                                     bob.name, bobX.name,
                                     m,
                                     da,
                                     (isHidden ? 0 : 1));
            } else
                return String.format(formatString,
                                     node.getQualifiedId(),
                                     node.name, 
                                     alice.name, "",
                                     bob.name, "",
                                     "",
                                     "?",
                                     (isHidden ? 0 : 1));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.format("*** Conflict info: %s %s %s\n", node, alice, bob);
            return "failed";
        }
	}
}
