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

// Create new nodes in the union taxonomy, when source nodes don't
// match, and hook them up appropriately.

class MergeMachine {

    UnionTaxonomy union;
    Taxonomy source;
    Alignment alignment;
    Map<String, Integer> summary;

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

        // Add heretofore unmapped nodes to union
        if (windyp)
            System.out.format("--- Merging nodes from %s into %s ---\n", source.getTag(), union.getTag());
        int startroots = union.rootCount();
        int startcount = union.count();

        // This was supposed to be taken care of... guess not
        for (Taxon node : source.taxa()) {
            Taxon unode = alignment.getTaxon(node);
            if (unode != null) {
                if (unode.prunedp) {
                    System.err.format("** Pruned taxon found as mapping target: %s -> %s\n",
                                      node, unode);
                } else
                    unode.comapped = node;
            }
        }

        for (Taxon root : source.roots()) {
            this.augment(root, union.forest);
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

	/* Method called on every node in the source taxonomy.  If node is
       aligned, usually we create a new union node, and align the
       source node to it.  The new union node will initially be
       detached, and becomes attached to the union taxonomy when its
       parent node is processed.
       Sink is the target of the nearest source taxonomy node ancestor
       that has one.
       */
	void augment(Taxon node, Taxon sink) {
        if (node.prunedp) return;
        String reason;
        Taxon unode = alignment.getTaxon(node);

		if (node.children == null) {
            if (unode != null)
                reason = accept(node, "mapped/tip");
			else {
                Answer a = alignment.getAnswer(node);
                if (a == null)
                    reason = acceptNew(node, "new/tip");
                else if (a.value <= Answer.HECK_NO)
                    // Don't create homonym if it's too close a match
                    // (weak no) or ambiguous (noinfo)
                    // YES > NOINFO > NO > HECK_NO  (sorry)
                    reason = acceptNew(node, "new/polysemy");
                else
                    reason = "ambiguous/redundant";
            }
		} else {
            if (unode != null) {
                for (Taxon child: node.children)
                    augment(child, unode);
                takeOn(node, unode, 0);
                reason = accept(node, "mapped/internal");
            } else {
                for (Taxon child: node.children)
                    augment(child, sink);
                // Examine mapped parents of the children
                boolean consistentp = true;
                Taxon commonParent = null;
                Taxon child1 = null, child2 = null; // for inconsistency reporting
                int count = 0;
                for (Taxon child : node.children) {
                    if (child.isPlaced()) {
                        Taxon childTarget = alignment.getTaxon(child);
                        if (childTarget != null &&
                            !childTarget.isDetached() &&
                            childTarget.isPlaced()) {
                            if (commonParent == null) {
                                commonParent = childTarget.parent;
                                child1 = child;
                            } else if (childTarget.parent != commonParent) {
                                consistentp = false;
                                child2 = child;
                            }
                            ++count;
                        }
                    }
                }
                if (count == 0) {
                    // new & unplaced old children only... copying stuff over to union.
                    Taxon newnode = acceptNew(node, "new/graft");
                    takeOn(node, newnode, 0);
                    reason = "new/graft";
                } else if (!consistentp) {
                    reason = inconsistent(node, child1, child2, sink);
                } else if (!commonParent.descendsFrom(sink)) {
                    reason = overtake(node, commonParent, sink);
                } else if (refinementp(node, sink)) {
                    Taxon newnode = acceptNew(node, "new/refinement");
                    takeOld(node, newnode);
                    takeOn(node, newnode, 0); // augmentation
                    reason = "new/refinement"
                } else {
                    takeOn(node, commonParent, 0);
                    // should include a witness for debugging purposes - merged to/from what?
                    // 2017-02-19 happens 7586 times
                    reason = reject(node, "reject/absorbed", commonParent, Taxonomy.MERGED);
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

        Integer count = summary.get(reason);
        if (count == null) count = 0;
        summary.put(reason, count + 1);

        return reason;
    }

    String inconsistent(Taxon node, Taxon child1, Taxon child2, Taxon sink) {
        // Paraphyletic / conflicted.
        // Put the new children unplaced under the sink, or the mrca of the
        // placed children, whichever is smaller.
        reportConflict(node, child1, child2, sink);
        // Tighten it if possible... does this always make sense?
        Taxon unode = alignment.getTargetMrca(node);
        if (unode != null && unode.descendsFrom(sink))
            sink = unode;
        takeOn(node, sink, Taxonomy.UNPLACED);
        return reject(node, "reject/inconsistent", sink, Taxonomy.INCONSISTENT);
    }
    
    private final static boolean MORE_SENSIBLE_BUT_DOESNT_WORK = false;

    // The symptom of getting this wrong is the creation of a cycle.

    String overtake(Taxon node, Taxon commonParent, Taxon sink) {
        // This is a troublesome case.
        // Workspace says children are under sink, but source says they're not.
        if (node.markEvent("sibling-sink mismatch"))
            System.out.format("* Parent of %s's children's images, %s, is an ancestor of %s\n",
                              node,
                              commonParent,
                              sink);

        if (MORE_SENSIBLE_BUT_DOESNT_WORK) {
            takeOn(node, commonParent, 0);
            return reject(node, "reject/overtaken", commonParent, Taxonomy.MERGED);
        } else {
            // was: inconsistent(node, child1, child2, sink);
            Taxon point;
            Taxon unode = alignment.getTargetMrca(node);
            if (unode != null && unode.descendsFrom(sink))
                point = unode;
            else
                point = sink;
            takeOn(node, point, Taxonomy.UNPLACED);
            // 2017-02-19 happens 1310 times
            return reject(node, "reject/absorbed", point, Taxonomy.MERGED);
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
        if (sink.children != null) {
            for (Taxon child : sink.children)
                if (child.isPlaced())
                    if (child.comapped == null) {
                        // If we do decide to allow these, we
                        // ought to flag the siblings somehow.
                        Answer.no(node, sink, "not-refinement/nonsurjective", child.name);
                        return false;
                    }
        }
        return true;
	}

    // Reject an unmapped node because it is merged or inconsistent.

    String reject(Taxon node, String reason, Taxon replacement, int flag) {
        // Could leave lub behind as a forwarding address...
        Taxon newnode = acceptNew(node, reason); // does setAnswer
        newnode.addFlag(flag);
        replacement.addChild(newnode);
        return reason;
    }

    // Node is mapped; accept mapping

    String accept(Taxon node, String reason) {
        if (alignment.getTaxon(node) == null)
            System.err.format("** Shouldn't happen - accept %s %s\n", node, reason);
        return reason;
    }

    // Node is not mapped; copy it over

    Taxon acceptNew(Taxon node, String reason) {
        if (alignment.getTaxon(node) != null)
            System.err.format("** Shouldn't happen - acceptNew %s %s\n", node, reason);

        // dup makes the new node placed, iff the source node is.
        // various other properties carry over as well.
        Taxon newnode = union.dupWithoutId(node, reason);

        Answer answer = Answer.yes(node, newnode, reason, null);

        alignment.setAnswer(node, answer);
        tick(reason);

        // grumble. comapped should be stored in the alignment.
        newnode.comapped = node;
        answer.maybeLog(union);
        newnode.addSource(node);
        return newnode;
	}

    // implement a refinement
    void takeOld(Taxon node, Taxon newnode) {
        for (Taxon child: node.children) {
            Taxon childTarget = alignment.getTaxon(child);
            if (childTarget != null && !childTarget.isDetached() && childTarget.isPlaced())
                childTarget.changeParent(newnode);
        }
    }

    // Set parent pointers of mapped targets of children of source to target.

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
                    target.addChild(uchild); // if unplaced, stay unplaced
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
                    uchild.changeParent(target, 0);
                } else {
                    //System.out.format("| %s moved to %s because %s, was under %s\n", uchild, target, source, uchild.parent);
                    Answer.noinfo(child, null, "promoted/internal", target.name).maybeLog(union);
                    uchild.changeParent(target, flags | (child.properFlags & Taxonomy.INCERTAE_SEDIS_ANY));
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
    // alice and bob are children of node
	void reportConflict(Taxon node, Taxon alice, Taxon bob, Taxon sink) {
        union.conflicts.add(new Conflict(node, alice, bob, sink, alignment, node.isHidden()));
        if (union.markEvent("reported conflict"))
            System.out.format("| conflict %s %s\n", union.conflicts.size(), node);
    }
}

// How to read this:
// alice and bob are two of node's children.
// They align to nodes with different parents, which means we a have conflict.
// (Well, one could be an ancestor of the other, and that 
// wouldn't be a conflict.  But that situation isn't handled yet.)

class Conflict {
	Taxon node;				// in source taxonomy
	Taxon alice, bob;				// children, in union taxonomy
    boolean isHidden;
    Taxon aliceTarget;
    Taxon bobTarget;
    Taxon sink;

	Conflict(Taxon node, Taxon alice, Taxon bob, Taxon sink, Alignment al, boolean isHidden) {
		this.node = node;
        this.alice = alice;
        this.bob = bob;
        this.sink = sink;
        this.isHidden = isHidden;
        aliceTarget = al.getTaxon(alice);
        bobTarget = al.getTaxon(bob);
	}
    static String formatString = ("%s\t" + // node
                                  "%s\t%s\t%s\t%s\t" + // alice, aliceTarget, a,
                                  "%s\t%s\t%s\t%s\t" + // bob, bobTarget, b,
                                  "%s\t%s\t" +     // mrca, sink, 
                                  "%s\t%s\t%s"); // unplaced, depth, visible
    static void printHeader(PrintStream out) throws IOException {
		out.format(Conflict.formatString,
                   "parent",
                   "alice", "alice_target", "alice_parent", "alice_ancestor",
                   "bob", "bob_target", "bob_parent", "bob_ancestor",
				   "mrca", "sink",
				   "unplaced", "depth", "visible");
        out.println();
    }

	public String toString() {
		// cf. Taxon.mrca
        try {
            Taxon[] div = null;
            if (aliceTarget != null && bobTarget != null &&
                !aliceTarget.prunedp && !bobTarget.prunedp)
                div = aliceTarget.divergence(bobTarget);
            int unplaced = 0;
            for (Taxon child : sink.getChildren())
                if ((child.properFlags & Taxonomy.UNPLACED) != 0)
                    ++unplaced;
            if (div != null) {
                Taxon a = div[0];
                Taxon b = div[1];
                int da = a.getDepth() - 1;
                String m = (a.parent == null ? "-" : a.parent.name);
                // node, alice, aliceTarget, a, bob, b, bobTarget, a.parent, mrca, sink
                return String.format(formatString,
                                     node,
                                     alice, aliceTarget, aliceTarget.parent, a,
                                     bob, bobTarget, bobTarget.parent, b,
                                     a.parent, sink,
                                     unplaced, da, (isHidden ? 0 : 1));
            } else
                return String.format(formatString,
                                     node,
                                     alice, aliceTarget, aliceTarget.parent, "",
                                     bob, bobTarget, (bobTarget != null ? bobTarget.parent : ""), "",
                                     "", sink,
                                     unplaced, "?", (isHidden ? 0 : 1));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.format("*** Conflict info: %s %s %s\n", node, alice, bob);
            return "failed";
        }
	}
}
