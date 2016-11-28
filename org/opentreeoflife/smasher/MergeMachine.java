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
                                  source,
                                  new TaxonMap() {
                                      public Taxon get(Taxon unode) {
                                          return alignment.invert(unode);
                                      }
                                  },
                                  null);

        coxmrca = new TaxonMap() {
                public Taxon get(Taxon node) {
                    Op cop = coplan.get(node);
                    if (cop != null) return cop.mrca;
                    else return null;
                }
            };

        plan = this.preparePlan(source, union, alignment, coxmrca);

        for (Taxon root : source.roots()) {
            this.augment(root, root);
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
        Taxon target = null;
        Taxon alice = null, bob = null;
        Taxon mrca = null;      // mrca of parents of alice, bob, and others
        Taxon zelda = null;
        int mapped = 0, unmapped = 0;
        boolean consistentp = true, monotypic = false;
        Op(Taxon mrca, Taxon alice, Taxon target) {
            if (alice != null && mrca.taxonomy != alice.taxonomy)
                throw new RuntimeException(String.format("mrca %s in %s but alice %s in %s",
                                                         mrca, mrca.taxonomy,
                                                         alice, alice.taxonomy));
            this.mrca = mrca;
            this.alice = alice;
            if (target == null)
                throw new RuntimeException("shouldn't happen");
            this.target = target;
        }
        String comment() {
            return String.format("%s; %s", mrca.name,
                                 (zelda == null ? "-" : zelda.name));
        }
    }

    // Compute cross-MRCAs and put them in a Map

    private int winners, smaller, larger;

    Map<Taxon, Op> preparePlan(Taxonomy taxy, Taxonomy target, TaxonMap map, TaxonMap coxmrca) {
        winners = smaller = larger = 0;
        Map<Taxon, Op> plan = new HashMap<Taxon, Op>();
        for (Taxon root : taxy.roots())
            preparePlan(root, map, plan, coxmrca);
        if (winners + smaller + larger > 0 && taxy == source)
            System.out.format("| MRCAs: %s match, %s smaller than aligned, %s larger than aligned\n",
                              winners, smaller, larger);
        return plan;
    }

    // Node is in source taxonomy, return value is in target taxonomy.
    // Return value is mrca of aligned nodes under node.
    // Cf. ConflictAnalysis.induce()

    private Op preparePlan(Taxon node,
                           TaxonMap map,
                           Map<Taxon, Op> plan,
                           TaxonMap coxmrca) {
        // cf. MergeMachine.augment
        Taxon mrca = null;
        Taxon alice = null, bob = null, zelda = null;
        int mapped = 0, unmapped = 0;
        boolean monotypic = true;
        for (Taxon child : node.getChildren()) {
            Op chop = preparePlan(child, map, plan, coxmrca);
            // Want two things for each child:
            //   1. The aligned target node, if there is one
            //   2. The MRCA of {all target nodes under this one} ??
            if (child.isPlaced()) {
                if (chop == null) {
                    zelda = child;
                    unmapped++;
                } else {
                    Taxon x = chop.target;
                    mapped++;
                    if (mrca == null) {
                        mrca = x;
                        alice = x;
                    } else {
                        Taxon mrca2 = mrca.mrca(x);
                        if (mrca2 != mrca) {
                            mrca = mrca2;
                            bob = x;
                        }
                        monotypic = false;
                    }
                }
            }
        }

        Taxon tnode = map.get(node);

        if (mrca == null && tnode == null)
            return null;        // Graft

        Taxon target;           // may get overridden below

        if (mrca == null)
            mrca = target = tnode;       // Tip
        else {
            target = null;  // may get overridden below
            if (tnode == mrca)
                ++winners;
            else if (tnode != null) {
                Taxon mrca2 = mrca.mrca(tnode);
                if (mrca2== null) System.err.format("** shouldn't happen %s %s\n", mrca, tnode);
                if (mrca2 != mrca) {
                    if (PREFER_ALIGNMENT_TO_MRCA)
                        // Treat alignment target as an honorary child
                        target = mrca2;
                    ++larger;
                } else
                    ++smaller;
            }
            if (target == null)
                target = mrca;
        }

        Op op = new Op(mrca, alice, target);
        op.bob = bob;
        op.unmapped = unmapped;
        op.mapped = mapped;
        op.zelda = zelda;
        op.monotypic = monotypic;

        if (coxmrca != null) {
            op.consistentp = consistentp(node, mrca, coxmrca);
            if (false && mrca != tnode && alice != null) {
                Taxon bounce = coxmrca.get(mrca);
                if (bounce != null)
                    System.out.format("# %s -> %s -> %s, %s %s/%s\n", node, mrca, bounce, op.consistentp,
                                      node.count(), bounce.count());
            }
        }

        plan.put(node, op);

        return op;
    }

    static boolean PREFER_ALIGNMENT_TO_MRCA = false;

    // True if disjoint, same, or resolution.  false if conflict.
    // cf. ConflictAnalysis.articulation.
    boolean consistentp(Taxon node, Taxon x, TaxonMap coxmrca) {
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
        if (op.mrca.taxonomy == union && op.alice != null) {
            union.conflicts.add(new Conflict(node, op.alice, op.bob, op.mrca));
            if (union.markEvent("reported conflict"))
                System.out.format("| Conflict %s %s\n", union.conflicts.size(), node);
        }
	}

    void reportWayward(Taxon node, Op op, TaxonMap map) {
        if (op.mrca.taxonomy == union &&
            (node.count() > 500000 ||
             (op.unmapped > 0 &&
              union.markEvent("wayward")))) {

            Taxon scan = node;
            while (scan.parent != null && map.get(scan) == null)
                scan = scan.parent;
            if (scan == null) return;
            Taxon bridge = map.get(scan);
            Taxon[] div = bridge.divergence2(op.mrca);

            Taxon big = null;
            if (div[0] != null) big = div[0].parent;
            if (div[1] != null) big = div[1].parent;

            String path0 = "-", path1 = "-";
            if (div[0] != null) path0 = String.format("| wayward %s < %s", node, div[0].name);
            if (div[1] != null) path1 = String.format("| wayward %s < %s", op.mrca, div[1].name);

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
	void augment(Taxon node, Taxon boss) {

        Op op = plan.get(node);
        if (op != null && !op.monotypic) boss = node;

        // preorder
        for (Taxon child: node.getChildren())
            augment(child, boss);

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
            // Examine aligned parents of the children
            // Consult merge plan - cf. preparePlan, above
            if (op == null) {
                // graft
                if (node.children == null)
                    m = acceptNew(node, "new/tip");
                else
                    m = acceptNew(node, "new/graft");

            } else {
                Taxon target = getTarget(op);
                if (target == null)
                    System.err.format("** Null target in augment: %s\n", target);
                if (op.consistentp) {
                    if (op.unmapped > 0) {
                        // absorption
                        m = reject(node, "reject/absorbed", op, target, Taxonomy.MERGED);
                    } else {
                        // resolves target
                        m = acceptNew(node, "new/refinement");
                        takeOld(m.target, node); // Move from mrca down to new refinement node
                        if (target.descendsFrom(m.target))
                            // target is already monotypy-maximal, so its parent
                            // will be strictly larger.
                            // ((Wg,Wj,Wr)W)z + ((Wj)N,(Wr,Wt)W)z = ((Wj)N,(Wg,Wr,Wt)W)z
                            target = target.parent;
                        m.target.setParent(target, "refinement");
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
        tick(m.reason);
    }

    Taxon getTarget(Op op) {
        Taxon target = op.target; // union node
        if (target == null)
            System.err.format("** Null target: %s\n", target);
        // Follow parent chain of union nodes
        Taxon bounce = coxmrca.get(target); // source node
        if (bounce != null)
            while (true) {
                Taxon up = target.parent;
                if (up == null) break;
                Taxon over = coxmrca.get(up); // source
                if (over != bounce)
                    break;
                System.out.format("| Going from %s up to %s\n", target, up);
                target = up;
            }
        return target;
    }

    Answer accept(Taxon node, Taxon target, String reason) {
        return Answer.yes(node, target, reason, null);
    }

    // Reject an unaligned node because it is merged [absorbed], conflict, or ambiguous

    Answer reject(Taxon node, String reason, Op op, Taxon target, int flag) {
        // Could leave lub behind as a forwarding address...
        Answer fake = acceptNew(node, reason); // does setAnswer
        fake.witness = op.comment(); // kludge
        fake.target.addFlag(flag);
        fake.target.setParent(target, reason);
        fake.target.rename("??" + node.name);
        return Answer.noinfo(node, target, reason, null);
    }

    // Node is not aligned; copy it over

    Answer acceptNew(Taxon node, String reason) {
        if (alignment.getTaxon(node) != null)
            System.err.format("** Shouldn't happen - acceptNew %s %s\n", node, reason);

        // dup makes the new node placed, iff the source node is.
        // various other properties carry over as well.
        Taxon newnode = union.dupWithoutId(node, reason);

        Answer answer = Answer.yes(node, newnode, reason, null);

        alignment.setAnswer(node, answer);

        answer.maybeLog(union);
        newnode.addSource(node);
        return answer;
	}

    // implement a refinement
    void takeOld(Taxon newnode, Taxon node) {
        for (Taxon child: node.children) {
            Taxon childTarget = alignment.getTaxon(child);
            if (childTarget != null && !childTarget.isDetached() && childTarget.isPlaced())
                childTarget.changeParent(newnode, "takeOld");
        }
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

    // Should be called exactly once for every node in the source taxonomy
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
