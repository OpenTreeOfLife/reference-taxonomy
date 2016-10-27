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
        union.eventLogger.resetEvents();

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
			System.out.println("| Added " + count + " synonyms");
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
        Taxon unode = alignment.getTaxon(node);

		if (node.children == null) {
            if (unode != null)
                accept(node, "mapped/tip");
			else {
                Answer a = alignment.getAnswer(node);
                if (a == null)
                    acceptNew(node, "new/tip");
                else if (a.value <= Answer.HECK_NO)
                    // Don't create homonym if it's too close a match
                    // (weak no) or ambiguous (noinfo)
                    // YES > NOINFO > NO > HECK_NO  (sorry)
                    acceptNew(node, "new/polysemy");
                }
		} else {
            if (unode != null) {
                for (Taxon child: node.children)
                    augment(child, unode);
                takeOn(node, unode, 0);
                accept(node, "mapped/internal");
            } else {
                for (Taxon child: node.children)
                    augment(child, sink);
                // Examine mapped parents of the children
                boolean consistentp = true;
                boolean paraconsistentp = true;
                Taxon commonParent = null;    // should end up being node.lub
                int count = 0;
                for (Taxon child : node.children) {
                    Taxon childTarget = alignment.getTaxon(child);
                    if (childTarget != null && !childTarget.isDetached() && childTarget.isPlaced()) {
                        if (commonParent == null)
                            commonParent = childTarget.parent;
                        else if (childTarget.parent != commonParent) {
                            consistentp = false;
                            if (child.isPlaced())
                                paraconsistentp = false;
                        }
                        ++count;
                    }
                }
                if (paraconsistentp && !consistentp) {
                    // 110 occurrences of this situation
                    System.out.format("* %s has unplaced children that make it inconsistent\n", node);
                    // consistentp = true;
                }

                if (count == 0) {
                    // new & unplaced old children only... copying stuff over to union.
                    Taxon newnode = acceptNew(node, "new/graft");
                    takeOn(node, newnode, 0);
                } else if (!consistentp) {
                    inconsistent(node, sink);
                } else if (!commonParent.descendsFrom(sink)) {
                    // This is the philosophically troublesome case.
                    // Could be either an outlier/mistake, or something serious.
                    if (node.markEvent("sibling-sink mismatch"))
                        System.out.format("* Parent of %s's children's images, %s, is not a descendant of %s\n",
                                          node, commonParent, sink);
                    inconsistent(node, sink);
                } else if (refinementp(node, sink)) {
                    Taxon newnode = acceptNew(node, "new/refinement");
                    takeOld(node, newnode);
                    takeOn(node, newnode, 0); // augmentation
                } else {
                    // 'trouble' = paraphyly risk - plain merge.
                    takeOn(node, commonParent, 0);
                    // should include a witness for debugging purposes - merged to/from what?
                    reject(node, "merged", commonParent, Taxonomy.MERGED);
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

    void inconsistent(Taxon node, Taxon sink) {
        // Paraphyletic / conflicted.
        // Put the new children unplaced under the mrca of the placed children.
        reportConflict(node);
        // Tighten it if possible... does this always make sense?
        if (node.lub != null && node.lub.descendsFrom(sink))
            sink = node.lub;
        takeOn(node, sink, Taxonomy.UNPLACED);
        reject(node, "reject/inconsistent", sink, Taxonomy.INCONSISTENT);
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
            alignment.setAnswer(node, Answer.noinfo(node, newnode, reason, replacement.name));
            replacement.addChild(newnode);
        } else {
            alignment.setAnswer(node, Answer.noinfo(node, null, reason, null));
        }
    }

    // Node is mapped; accept mapping

    Taxon accept(Taxon node, String reason) {
        if (node == null) {
            System.err.format("** Shouldn't happen\n"); return null;
        }
        Taxon unode = alignment.getTaxon(node);
        if (unode == null) {
            System.err.format("** Also shouldn't happen: %s\n", node); return null;
        }
        Answer a = alignment.getAnswer(node);
        if (a == null) {
            System.err.format("** Also also shouldn't happen: %s\n", node);
            alignment.setAnswer(node, Answer.yes(node, unode, reason, null));
        } else if (!a.isYes()) {
            System.err.format("** Also also also shouldn't happen: %s %s\n", node, a.reason);
            alignment.setAnswer(node, Answer.yes(node, unode, reason, null));
        }
        return unode;
    }

    // Node is not mapped; copy it over

    Taxon acceptNew(Taxon node, String reason) {
        // dup makes the new node placed, iff the source node is.
        // various other properties carry over as well.
        Taxon newnode = alignWithNew(node, union, reason);
        newnode.addSource(node);
        return newnode;
	}

    // target is the union taxonomy

    Taxon alignWithNew(Taxon node, Taxonomy target, String reason) {
        Taxon newnode = target.dupWithoutId(node, reason);
        Answer answer = Answer.yes(node, newnode, reason, null);
        alignment.setAnswer(node, answer);
        newnode.comapped = node;
        answer.maybeLog(target);
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
	void reportConflict(Taxon node) {
        // node.lub is mrca of the children's map targets and/or lubs ...
        Taxon alice = null, bob = null, mrca = null;
        boolean foundit = false;
        for (Taxon child: node.children) {

            // This was a particulary hard-to-debug problem... the solution ended up
            // breaking a false merge for Euxinia (a sibling of the offender) #198
            // Keeping the code in case it needs to be used again.
            if (child.name != null && child.name.equals("Pseudostomum"))
                foundit = true;

            Taxon uchild = alignment.getTaxon(child);
            if (uchild != null && !uchild.isDetached() && uchild.isPlaced()) {
                // This method of finding fighting children is
                // heuristic... cf. AlignmentByName
                if (alice == null)
                    alice = mrca = uchild;
                else {
                    bob = uchild;
                    // We're called deep inside of augment(), so tree may have been edited.
                    // ergo, .carefulMrca instead of .mrca
                    Taxon newmrca = mrca.carefulMrca(bob);
                    if (newmrca != null)
                        mrca = newmrca;
                    if (mrca == node.lub)
                        break;
                }
            }
        }

        if (foundit) {
            System.err.format("** Pseudostomum is in inconsistent taxon %s, moving to %s\n",
                              node, node.lub);
            for (Taxon child : node.children) {
                System.err.format("**   Child: %s\n", child);
                Taxon uchild = alignment.getTaxon(child);
                if (uchild != null && !uchild.isDetached())
                    uchild.show();
            }
        }

        if (alice == null || bob == null || alice == bob)
            //System.err.format("** Can't find two children %s %s\n", alice, bob);
            union.markEvent("incomplete conflict");
        else {
            if (alice.taxonomy != bob.taxonomy)
                System.err.format("** taxonomy mismatch - shouldn't happen %s %s\n", alice, bob);
            if (alice.prunedp || bob.prunedp) {
                System.err.format("** pruned node in reportConflict - shouldn't happen %s %s %s\n",
                                  node, alice, bob);
                return;
            }
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
            System.err.format("*** Conflict info: %s %s %s %s\n", node, node.lub, alice, bob);
            return "failed";
        }
	}
}
