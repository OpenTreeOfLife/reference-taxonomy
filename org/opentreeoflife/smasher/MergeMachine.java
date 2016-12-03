package org.opentreeoflife.smasher;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

// Create new nodes in the union taxonomy, when source nodes don't
// match, and hook them up appropriately.

class MergeMachine {

    UnionTaxonomy union;
    Taxonomy source;
    Alignment alignment;
    Map<String, Integer> summary;
    private Map<Taxon, Op> plan = new HashMap<Taxon, Op>();
    private Map<Taxon, Op> coplan = new HashMap<Taxon, Op>();

    MergeMachine(Taxonomy source, UnionTaxonomy union, Alignment a, Map<String, Integer> summary) {
        this.source = source;
        this.union = union;
        this.alignment = a;
        this.summary = summary;
    }

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

        // For refinementp
        coplan = this.preparePlan(union,
                                  source.forest,
                                  new TaxonMap() {
                                      public Taxon get(Taxon unode) {
                                          return alignment.invert(unode);
                                      }
                                  });

        plan = this.preparePlan(source, union.forest, alignment);

        for (Taxon root : source.roots()) {
            this.augment(root);
            Taxon newroot = alignment.getTaxon(root);
            if (newroot != null && newroot.isDetached() && !newroot.noMrca())
                union.addRoot(newroot);
        }

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

    // Planning phase - before any mutation occurs that will screw up
    // cached depth counts

    class Op {
        Taxon other = null;     // as aligned
        Taxon alice = null, bob = null;
        Taxon mrca = null;      // mrca of parents of alice, bob, and others
        boolean wayward = false;
        int mapped = 0, unmapped = 0;
        Taxon sink = null;
    }

    // Compute cross-MRCAs and put them in a Map

    private int winners, smaller, larger;

    Map<Taxon, Op> preparePlan(Taxonomy taxy, Taxon troot, TaxonMap map) {
        winners = smaller = larger = 0;
        Map<Taxon, Op> plan = new HashMap<Taxon, Op>();
        preparePlan(taxy.forest, troot, map, plan);
        if (winners + smaller + larger > 0)
            System.out.format("| MRCAs: %s match, %s smaller than sink, %s larger than sink\n",
                              winners, smaller, larger);
        return plan;
    }

    // Input is in source taxonomy, sink is in target, return value is in target

    private Op preparePlan(Taxon node,
                           Taxon sink,
                           TaxonMap map,
                           Map<Taxon, Op> plan) {
        Taxon unode = map.get(node);
        if (unode != null) {
            morePreparePlan(node, unode, map, plan);
            Op op = new Op();
            op.alice = unode;
            op.mrca = unode.parent;
            return op;
        } else
            return morePreparePlan(node, sink, map, plan);
    }

    private Op morePreparePlan(Taxon node,
                               Taxon sink,
                               TaxonMap map,
                               Map<Taxon, Op> plan) {

        // cf. MergeMachine.augment
        Taxon mrca = null, alice = null, bob = null;
        int mapped = 0, unmapped = 0;
        for (Taxon child : node.getChildren()) {
            Op childOp = preparePlan(child, sink, map, plan);
            if (child.isPlaced()) {
                if (childOp == null)
                    unmapped++;
                else {
                    mapped++;
                    Taxon p = childOp.mrca; // in target
                    if (mrca == null) {
                        mrca = p;
                        alice = childOp.alice;
                    } else {
                        Taxon mrca2 = mrca.mrca(p);
                        if (mrca2 != mrca) {
                            mrca = mrca2;
                            bob = childOp.alice;
                        }
                    }
                }
            }
        }
        if (alice == null) return null;

        Op op = new Op();
        op.mrca = mrca;
        op.alice = alice; op.bob = bob;
        op.mapped = mapped;
        op.unmapped = unmapped;
        op.sink = sink;         // in target

        if (sink != null && mrca != null)
            op.wayward = (mrca.mrca(sink) != sink);

        plan.put(node, op);

        /*
          union node != null: as aligned
          alice == null: graft
          bob != null: conflict
          mrca above sink: wayward
          refinement: refinement
          otherwise: ordinary merge
        */

        // Metrics
        if (bob != null)   // conflict
            ;
        else if (op.wayward) {  // wayward
            ++larger;
            if (op.unmapped > 0){
                if (larger < 20)
                    System.out.format("** %s shares children (e.g. %s) with %s, but that's outside of %s [%s:%s]\n",
                                      node, op.alice.name, op.mrca.name, op.sink.name, op.mapped, op.unmapped);
                else if (larger == 20)
                    System.out.format("** %s shares etc. etc.\n", node);
            }
        } else {                // refinement or otherwise
            if (sink == op.mrca) 
                ++winners;
            else
                ++smaller;
        }
        return op;
    }

	/* Method called on every node in the source taxonomy.  If node is
       aligned, usually we create a new union node, and align the
       source node to it.  The new union node will initially be
       detached, and becomes attached to the union taxonomy when its
       parent node is processed.
       */
	void augment(Taxon node) {
        Taxon unode = alignment.getTaxon(node);

		if (node.children == null) {
            if (unode != null)
                accept(node, "aligned/tip");
			else {
                Answer a = alignment.getAnswer(node);
                if (a == null)
                    acceptNew(node, "new/tip");
                else if (a.value <= Answer.HECK_NO)
                    // Don't create homonym if it's too close a match
                    // (weak no) or ambiguous (noinfo)
                    // YES > NOINFO > NO > HECK_NO  (sorry)
                    acceptNew(node, "new/polysemy");
                else
                    // NOINFO - ambiguous
                    tick(a.reason);
            } 
		} else {
            if (unode != null) {
                for (Taxon child: node.children)
                    augment(child);
                takeOn(node, unode, 0);
                accept(node, "aligned/internal");
            } else {
                for (Taxon child: node.children)
                    augment(child);
                // Examine aligned parents of the children
                // cf. preparePlan, above
                Op op = plan.get(node);
                if (op == null) {
                    // new & unplaced old children only... copying stuff over to union.
                    Taxon newnode = acceptNew(node, "new/graft");
                    takeOn(node, newnode, 0);
                } else if (op.bob != null) {
                    // alice and bob are target nodes with different parents
                    reportConflict(node, op.alice, op.bob, op.mrca);
                    takeOn(node, op.mrca, Taxonomy.UNPLACED);
                    reject(node, "reject/inconsistent", op.mrca, Taxonomy.INCONSISTENT);
                } else if (op.wayward) {
                    takeOn(node, op.mrca, 0);
                    reject(node, "reject/wayward", op.mrca, Taxonomy.MERGED);
                } else if (refinementp(node, op.sink)) {
                    Taxon newnode = acceptNew(node, "new/refinement");
                    takeOld(node, newnode);
                    takeOn(node, newnode, 0);
                } else {
                    // 'trouble' = paraphyly risk - plain merge.
                    takeOn(node, op.mrca, 0);
                    // should include a witness for debugging purposes - merged to/from what?
                    reject(node, "reject/merged", op.mrca, Taxonomy.MERGED);
                }
            }
            // the following is just a sanity check
			for (Taxon child: node.children) {
                Taxon uchild = alignment.getTaxon(child);
                if (uchild != null && uchild.parent == null)
                    // Does not happen
                    System.err.format("** Unattached child %s %s %s\n", child, node, uchild);
            }
        }
    }

    /* Refinement: feature necessary for merging Silva into the
       skeleton and NCBI into Silva.  This lets an internal "new" node
       (in the "new" taxonomy) be inserted in between internal "old"
       nodes (in the "old" taxonomy).

       node.lub = the mrca, in the old taxonomy, of all the children
       of the new node.

       This is called only if the new and old nodes are consistent,
       i.e. if every [mapped] child of the new node is [maps to] a
       child of node.lub.  Let S be that subset of old nodes.

	   We can move the members of S to (a copy of) the new node, which
	   later will get inserted back into the union tree under
	   node.lub.

	   This is a cheat because some of the old children's siblings
	   might be more correctly classified as belonging to the new
	   taxon, rather than being siblings.  So we might want to
	   further qualify this.

	   Caution: See https://github.com/OpenTreeOfLife/opentree/issues/73 ...
	   family as child of subfamily is confusing.
	   node1.rank.level <= node2.rank.level ....
    */
    
	boolean refinementp(Taxon node, Taxon sink) {
        if (node.isAnnotatedHidden()) {
            // Prevent non-priority inner taxa from entering in Index Fungorum
            node.markEvent("not-refinement/hidden");
            return false;
        }
        // Temporary shortcut to force refinements to happen
        if (true) {
            Op cop = coplan.get(sink);
            if (cop != null)
                return cop.unmapped == 0;
            else
                return false;
        } else if (sink.children != null) {
            for (Taxon child : sink.children)
                if (child.isPlaced())
                    if (alignment.invert(child) == null) {
                        // If we do decide to allow these, we
                        // ought to flag the unmatched siblings somehow.
                        node.markEvent("not-refinement/nonsurjective");
                        return false;
                    }
        }
        return true;
	}

    // Reject an unaligned node because it is merged [absorbed], inconsistent, or ambiguous

    void reject(Taxon node, String reason, Taxon replacement, int flag) {
        // Could leave lub behind as a forwarding address...
        Taxon newnode = acceptNew(node, reason); // does setAnswer
        newnode.addFlag(flag);
        newnode.setParent(replacement, reason);
    }

    // Node is aligned; accept mapping

    void accept(Taxon node, String reason) {
        if (alignment.getTaxon(node) == null)
            System.err.format("** Shouldn't happen - accept %s %s\n", node, reason);
        tick(reason);
        return;
    }

    // Node is not aligned; copy it over

    Taxon acceptNew(Taxon node, String reason) {
        if (alignment.getTaxon(node) != null)
            System.err.format("** Shouldn't happen - acceptNew %s %s\n", node, reason);

        // dup makes the new node placed, iff the source node is.
        // various other properties carry over as well.
        Taxon newnode = union.dupWithoutId(node, reason);

        Answer answer = Answer.yes(node, newnode, reason, null);

        alignment.setAnswer(node, answer);
        tick(reason);

        answer.maybeLog(union);
        newnode.addSource(node);
        return newnode;
	}

    // implement a refinement
    void takeOld(Taxon node, Taxon newnode) {
        for (Taxon child: node.children) {
            Taxon childTarget = alignment.getTaxon(child);
            if (childTarget != null && !childTarget.isDetached() && childTarget.isPlaced())
                childTarget.changeParent(newnode, "takeOld");
        }
    }

    // Set parent pointers of aligned targets of children of source to target.

    Taxon takeOn(Taxon source, Taxon target, int flags) {
        for (Taxon child: source.children) {
            Taxon uchild = alignment.getTaxon(child);
            if (uchild == null)
                ;               // inconsistent, merged, ambiguous, ...
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

                    // Need to do something with it
                    reject(child, "reject/cycle", union.forest, Taxonomy.UNPLACED);
                    target.taxonomy.addRoot(uchild);
                } else {
                    //if (??child??.isRoot())
                    //    child.markEvent("placed-former-root");
                    uchild.addFlag(flags);
                    uchild.setParent(target, "takeOn"); // if unplaced, stay unplaced
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
                    uchild.changeParent(target, 0, "moved from root");
                } else {
                    //System.out.format("| %s moved to %s because %s, was under %s\n", uchild, target, source, uchild.parent);
                    Answer.noinfo(child, null, "promoted/internal", target.name).maybeLog(union);
                    uchild.changeParent(target, flags | (child.properFlags & Taxonomy.INCERTAE_SEDIS_ANY), "promoted");
                }
            }
        }
        return target;
    }

    // Called on source taxonomy to transfer flags, rank, etc. to union taxonomy
    public void transferProperties(Taxonomy source) {
        for (Taxon node : source.taxa()) {
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

	// 3799 conflicts as of 2014-04-12
	void reportConflict(Taxon node, Taxon alice, Taxon bob, Taxon mrca) {
        union.conflicts.add(new Conflict(node, alice, bob, mrca, node.isHidden()));
        if (union.markEvent("reported conflict"))
            System.out.format("| conflict %s %s\n", union.conflicts.size(), node);
	}

    void tick(String action) {
        Integer count = summary.get(action);
        if (count == null) count = 0;
        summary.put(action, count + 1);
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
    Taxon mrca;
    boolean isHidden;
	Conflict(Taxon node, Taxon alice, Taxon bob, Taxon mrca, boolean isHidden) {
		this.node = node;
        this.alice = alice;
        this.bob = bob;
        this.isHidden = isHidden;
        this.mrca = mrca;
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
            if (!alice.prunedp && !bob.prunedp)
                div = alice.divergence(bob);
            if (div != null) {
                Taxon a = div[0];
                Taxon b = div[1];
                int da = a.getDepth() - 1;
                String m = (a.parent == null ? "-" : a.parent.name);
                return String.format(formatString,
                                     node.getQualifiedId(),
                                     node.name, 
                                     alice.name, a.name,
                                     bob.name, b.name,
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
