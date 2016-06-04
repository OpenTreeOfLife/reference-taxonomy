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
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.QualifiedId;
import org.opentreeoflife.taxa.Answer;

// Create new nodes in the union taxonomy, when source nodes don't
// match, and hook them up appropriately.

class MergeMachine {

    UnionTaxonomy union;
    SourceTaxonomy source;
    Alignment alignment;

    MergeMachine(SourceTaxonomy source, UnionTaxonomy union, Alignment a) {
        this.source = source;
        this.union = union;
        this.alignment = a;
    }

	void augment() {

        union.reset();
        union.eventlogger.resetEvents();

        // No need to inferFlags() at this point since merge doesn't
        // pay attention to inferred flags at any point (just the
        // proper ones)

        boolean windyp = (union.count() > 1);

        // Add heretofore unmapped nodes to union
        if (windyp)
            System.out.println("--- Augmenting union with new nodes from " + source.getTag() + " ---");
        int startroots = union.rootCount();
        int startcount = union.count();

        // This was supposed to be taken care of... guess not
        for (Taxon node : source.taxa())
            if (node.mapped != null)
                node.mapped.comapped = node;

        for (Taxon root : source.roots()) {
            this.augment(root, union.forest);
            Taxon newroot = root.mapped;
            if (newroot != null && newroot.isDetached() && !newroot.noMrca())
                union.addRoot(newroot);
        }

        transferProperties(source);

        if (UnionTaxonomy.windyp) {
            report(source, startroots, startcount);
            if (union.count() == 0)
                source.forest.show();
            union.eventlogger.eventsReport("|   ");
            System.out.format("| Ended with: %s roots, %s taxa\n",
                              union.rootCount(), union.count());
        }
        if (union.numberOfNames() < 10)
            System.out.println(" -> " + union.toNewick());
	}

    // Called on source taxonomy to transfer flags, rank, etc. to union taxonomy
    void transferProperties(Taxonomy source) {
        for (Taxon node : source.taxa()) {
            Taxon unode = node.mapped;
            if (unode != null)
                transferProperties(node, unode);
        }
    }

    // This is used when the union node is NOT new

    public void transferProperties(Taxon node, Taxon unode) {
        if (node.name != null) {
            if (unode.name == null)
                unode.setName(node.name);
            else if (unode.name != node.name)
                // ???
                unode.taxonomy.addSynonym(node.name, unode, "synonym");
        }

		if ((unode.rank == Rank.NO_RANK || unode.rank.equals("samples"))
            && (node.rank != Rank.NO_RANK))
            unode.rank = node.rank;

		unode.addFlag(node.flagsToAdd(unode));

        // No change to hidden or incertae sedis flags.  Union node
        // has precedence.

        unode.addSource(node);
        if (node.sourceIds != null)
            for (QualifiedId id : node.sourceIds)
                unode.addSourceId(id);

        // ??? retains pointers to source taxonomy... may want to fix for gc purposes
        if (unode.answer == null)
            unode.answer = node.answer;
	}

    Map<String, Integer> reasonCounts = new HashMap<String, Integer>();
    List<String> reasons = new ArrayList<String>();

    void report(SourceTaxonomy source, int startroots, int startcount) {
        System.out.format("| Started with: %s roots, %s taxa + %s source roots, %s source taxa\n",
                          startroots,
                          startcount,
                          source.rootCount(),
                          source.count());
        for (Taxon node : source.taxa()) {
            String reason;
            Answer answer = node.answer;
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

	// Method called on every node in the source taxonomy.  If node is
	// aligned, usually we create a new union node, and align the
	// source node to it.  The new union node will initially be
	// detached, and becomes attached to the union taxonomy when its
	// parent node is processed.
	void augment(Taxon node, Taxon sink) {

		if (node.children == null) {
            if (node.mapped != null)
                accept(node, "mapped/tip");
			else if (node.answer == null ||
                     node.answer.value <= Answer.HECK_NO)
                // Don't create homonym if it's too close a match
                // (weak no) or ambiguous (noinfo)
                // YES > NOINFO > NO > HECK_NO  (sorry)
				acceptNew(node, "new/tip");

		} else {
            if (node.mapped != null)
                sink = node.mapped;
			for (Taxon child: node.children)
                augment(child, sink);
            if (node.mapped != null) {
                takeOn(node, node.mapped, 0);
                accept(node, "mapped/internal");
            } else {
                // Examine mapped parents of the children
                boolean graftp = true;
                boolean consistentp = true;
                Taxon common = null;
                for (Taxon child : node.children) {
                    Taxon augChild = child.mapped;
                    if (augChild != null && !augChild.isDetached() && augChild.isPlaced()) {
                        graftp = false;
                        if (common == null)
                            common = augChild.parent;
                        else if (augChild.parent != common) {
                            consistentp = false;
                            break;
                        }
                    }
                }
                if (graftp) {
                    // new & unplaced old children only... copying stuff over to union.
                    Taxon newnode = acceptNew(node, "new/graft");
                    takeOn(node, newnode, 0);
                } else if (!consistentp) {
                    inconsistent(node, sink);
                } else if (!common.descendsFrom(sink)) {
                    // This is the philosophically troublesome case.
                    // Could be either an outlier/mistake, or something serious.
                    if (node.markEvent("sibling-sink mismatch"))
                        System.out.format("!! Parent of %s's children's images, %s, is not a descendant of %s\n",
                                          node, common, sink);
                    inconsistent(node, sink);
                } else if (refinementp(node, sink)) {
                    Taxon newnode = acceptNew(node, "new/refinement");
                    takeOld(node, newnode);
                    takeOn(node, newnode, 0); // augmentation
                } else {
                    // 'trouble' = paraphyly risk - plain merge.
                    takeOn(node, common, 0);
                    // should include a witness for debugging purposes - merged to/from what?
                    reject(node, "merged", common, Taxonomy.MERGED);
                }
            }
			for (Taxon child: node.children) {
                if (child.mapped != null && child.mapped.parent == null)
                    // Does not happen
                    System.err.format("** Unattached child %s %s %s\n", child, node, child.mapped);
            }
            if (false && node.mapped == null)
                System.err.format("** Unaligned node %s %s with %s children\n", node, node.answer, node.children.size());
        }
    }

    void inconsistent(Taxon node, Taxon sink) {
        // Paraphyletic / conflicted.
        // Put the new children unplaced under the mrca of the placed children.
        reportConflict(node);
        Taxon target = chooseTarget(node.lub, sink);
        takeOn(node, target, Taxonomy.UNPLACED);
        reject(node, "reject/inconsistent", target, Taxonomy.INCONSISTENT);
    }
    
    // Refinement: feature necessary for merging Silva into the
    // skeleton and NCBI into Silva.  This lets an internal "new" node
    // (in the "new" taxonomy) be inserted in between internal "old"
    // nodes (in the "old" taxonomy).

    // node.lub = the mrca, in the old taxonomy, of all the children
    // of the new node.

    // This is called only if the new and old nodes are consistent,
    // i.e. if every [mapped] child of the new node is [maps to] a
    // child of node.lub.  Let S be that subset of old nodes.

	// We can move the members of S to (a copy of) the new node, which
	// later will get inserted back into the union tree under
	// node.lub.

	// This is a cheat because some of the old children's siblings
	// might be more correctly classified as belonging to the new
	// taxon, rather than being siblings.  So we might want to
	// further qualify this.

	// Caution: See https://github.com/OpenTreeOfLife/opentree/issues/73 ...
	// family as child of subfamily is confusing.
	// ranks.get(node1.rank) <= ranks.get(node2.rank) ....
    
	boolean refinementp(Taxon node, Taxon target) {
        if (node.isAnnotatedHidden()) {
            // Prevent non-priority inner taxa from entering in Index Fungorum
            node.markEvent("not-refinement/hidden");
            return false;
        }
        if (target.children != null)
            for (Taxon child : target.children)
                if (child.isPlaced())
                    if (child.comapped == null) {
                        // If we do decide to allow these, we
                        // ought to flag the siblings somehow.
                        if (node.markEvent("not-refinement/nonsurjective"))
                          if (false)  // too much verbiage
                            System.out.format("! Trouble with inserting %s into %s is %s\n", node, target, child);
                        return false;
                    }
        return true;
	}

    // Reject an unmapped node.
    // No way to win.  If we add it to the hierarchy, we get a gazillion homonyms.
    // If we leave it out, there is often nothing for the previous OTT version to map it to.

    void reject(Taxon node, String reason, Taxon replacement, int flag) {
        checkRejection(node, reason); // for diagnostics
        // Could leave lub behind as a forwarding address.
        // But be careful about replacement ids when doing deprecation.
        // HEURISTIC.
        if (replacement.taxonomy.lookup(node.name) == null) {
            Taxon newnode = acceptNew(node, reason);
            newnode.addFlag(flag);
            node.answer = Answer.noinfo(node, newnode, reason, replacement.name);
            replacement.addChild(newnode);
        } else {
            node.answer = Answer.noinfo(node, null, reason, null);
        }
    }

    // Node is mapped; accept mapping

    Taxon accept(Taxon node, String reason) {
        if (node == null) {
            System.err.format("Shouldn't happen\n"); return null;
        }
        if (node.mapped == null) {
            System.err.format("Also shouldn't happen: %s\n", node); return null;
        }
        if (node.answer == null) {
            System.err.format("Also also shouldn't happen: %s\n", node);
            node.answer = Answer.yes(node, node.mapped, reason, null);
        } else if (!node.answer.isYes()) {
            System.err.format("Also also also shouldn't happen: %s %s\n", node, node.answer.reason);
            node.answer = Answer.yes(node, node.mapped, reason, null);
        }
        return node.mapped;
    }

    // Node is not mapped; copy it over

    Taxon acceptNew(Taxon node, String reason) {
        // dup makes the new node placed, iff the source node is.
        // various other properties carry over as well.
        Taxon newnode = alignWithNew(node, union, reason);
        newnode.addSource(node);
        return newnode;
	}

    public Taxon alignWithNew(Taxon node, Taxonomy target, String reason) {
        Taxon newnode = target.dupWithoutId(node, reason);
        node.mapped = newnode;
        newnode.comapped = node;
        node.answer = Answer.yes(node, newnode, reason, null);
        node.answer.maybeLog();
        return newnode;
    }

    void checkRejection(Taxon node, String reason) {
        if (union != null && union.importantIds != null) {
            List<Node> probe = union.importantIds.lookup(node.name);
            if (probe != null) {
                Answer.no(node, null, "reject/otu", reason).maybeLog(union);
                // System.out.format("| Rejecting OTU %s (ott:%s) because %s\n", node, node.id, reason);
                // this.show();
            }
        }
    }

    static boolean USE_LUB = false;

    Taxon chooseTarget(Taxon lub, Taxon sink) {
        if (USE_LUB)
            return lub;
        else if (lub == null)
            return sink;
        else if (lub.descendsFrom(sink)) // ??
            return lub;
        else
            return sink;
    }

    // implement a refinement
    void takeOld(Taxon node, Taxon newnode) {
        for (Taxon child: node.children) {
            Taxon augChild = child.mapped;
            if (augChild != null && !augChild.isDetached() && augChild.isPlaced())
                augChild.changeParent(newnode);
        }
    }

    // Set parent pointers.

    Taxon takeOn(Taxon source, Taxon target, int flags) {
        for (Taxon child: source.children) {
            Taxon uchild = child.mapped;
            if (uchild == null)
                ;               // inconsistent, merged, ambiguous, ...
            else if (uchild.noMrca())
                ;               // it's a root
            else if (uchild.isDetached()) {
                // "new" child
                if (target == uchild)
                    ;               // Lacrymaria Morganella etc. - shouldn't happen
                else if (target.descendsFrom(uchild)) {
                    System.out.format("** Adoption would create a cycle\n");
                    System.out.format("%s ?< ", uchild);
                    target.showLineage(uchild.parent);

                    // Need to do something with it
                    reject(child, "reject/cycle", union.forest, Taxonomy.UNPLACED);
                    target.taxonomy.addRoot(uchild);
                } else {
                    //if (??child??.isRoot())
                    //    child.markEvent("placed-former-root");
                    target.addChild(uchild); // if unplaced, stay unplaced
                    uchild.addFlag(flags);
                }
            } else if (!uchild.isPlaced()) {
                // "old" child maybe not well placed in union.  consider moving it
                // is target a better (more specific) placement for uchild than uchild's current parent?
                if (!target.descendsFrom(uchild.parent))
                    child.markEvent("not-placed/does-not-descend");
                else if (target == uchild.parent) {
                    // A placement here could promote an unplaced taxon in union to placed...
                    // sort of dangerous, because later taxonomies (e.g. worms) tend to be unreliable
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

	// 3799 conflicts as of 2014-04-12
	void reportConflict(Taxon node) {
        // node.lub is mrca of the children's map targets and/or lubs ...
        Taxon alice = null, bob = null, mrca = null;
        for (Taxon child: node.children)
            if (child.mapped != null && !child.mapped.isDetached() && child.mapped.isPlaced()) {
                // This method of finding fighting children is
                // heuristic... cf. AlignmentByName
                if (alice == null)
                    alice = mrca = child.mapped;
                else {
                    bob = child.mapped;
                    // We're called deep inside of augment(), so tree may have been edited.
                    // ergo, .carefulMrca instead of .mrca
                    Taxon newmrca = mrca.carefulMrca(bob);
                    if (newmrca != null)
                        mrca = newmrca;
                    if (mrca == node.lub)
                        break;
                }
            }
        if (alice == null || bob == null || alice == bob)
            //System.out.format("** Can't find two children %s %s\n", alice, bob);
            union.markEvent("incomplete conflict");
        else {
            if (alice.taxonomy != bob.taxonomy)
                System.err.format("** taxonomy mismatch - shouldn't happen %s %s\n", alice, bob);
            union.conflicts.add(new Conflict(node, alice, bob, node.isHidden()));
            if (union.markEvent("reported conflict"))
                System.out.format("| conflict %s %s\n", union.conflicts.size(), node);
        }
	}

}

class Conflict {
	Taxon node;				// in source taxonomy
	Taxon alice, bob;				// children, in union taxonomy
    boolean isHidden;
	Conflict(Taxon node, Taxon alice, Taxon bob, boolean isHidden) {
		this.node = node;
        this.alice = alice;
        this.bob = bob;
        this.isHidden = isHidden;
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
		Taxon[] div = alice.divergence(bob);
        try {
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
            System.err.format("*** Conflict info: %s %s %s\n", node, node.lub, div);
            return "failed";
        }
	}
}
