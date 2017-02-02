/*

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

*/

package org.opentreeoflife.smasher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.io.PrintStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.File;
import org.json.simple.JSONObject; 

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;
import org.opentreeoflife.taxa.Flag;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.EventLogger;
import org.opentreeoflife.taxa.Addition;
import org.opentreeoflife.taxa.InterimFormat; // for writeAsJson


public class UnionTaxonomy extends Taxonomy {

	List<Taxonomy> sources = new ArrayList<Taxonomy>();
	Alignment idsourceAlignment = null;
	// One log per name-string
    List<Answer> weakLog = new ArrayList<Answer>();

    public Map<String, Integer> alignmentSummary = new HashMap<String, Integer>();
    public Map<String, Integer> mergeSummary = new HashMap<String, Integer>();

    public int blustery = 1;

	UnionTaxonomy(String idspace) {
        super(idspace);
		this.setTag("union");
        this.startQidIndex();

        // dummy taxonomy in case no one ever sets a skeleton
        this.setSkeleton(new SourceTaxonomy());
	}

	public static UnionTaxonomy newTaxonomy(String idspace) {
		return new UnionTaxonomy(idspace);
	}

	// ----- INITIALIZATION -----

    boolean useCompleteSkeleton = true;

	// The 'division' field of a Taxon is always either null or a
	// taxon belonging to the skeleton taxonomy...

	public Alignment skeletonAlignment;

	// for jython - methods defined on class Taxonomy
	public void setSkeleton(SourceTaxonomy skel) {
		// Copy skeleton into union
		Alignment a = new AlignmentByName(skel, this);
		for (Taxon div : skel.taxa())
            div.unsourced = true;
        this.merge(a);

        if (useCompleteSkeleton) {
            // set divisions on union nodes (initially all null)
            this.forest.setDivision(skel.forest);
            for (Taxon div : skel.taxa()) {
                if (div.name == null)
                    System.err.format("## Anonymous division ?! %s in %s\n", div, div.parent);
                else {
                    Taxon udiv = a.getTaxon(div);
                    udiv.setId(div.id);
                    udiv.setDivision(div);
                }
            }
        }

        this.skeletonAlignment = a;
	}

    public void setDivision(Taxon node, String divname) {
        Taxon div = skeletonAlignment.source.unique(divname);
        if (div != null)
            node.setDivision(div);
        else
            System.err.format("** Unrecognized division: %s (for %s)\n", divname, node);
    }

	// -----

    /*
      Absorb a new source taxonomy.
      The steps (from python) would normally be:
      1. create an alignment with a = .alignment(source)
      2. add ad-hoc node alignments to it with a.same(...)
      3. .align(a) to finish alignment
      4. .merge(a) to merge in new taxa
      If 2. is empty, then 1+3+4 can be expressed as .absorb(source)
      */

	public Alignment alignment(SourceTaxonomy source) { // called from jython
        if (source.idspace == null)
            setIdspace(source);
        source.setEventLogger(this.eventLogger);
        Alignment a = new AlignmentByName(source, this);
        source.clearDivisions(); // division determinations are cached in nodes
        // source.forest.setDivision(this.skeletonAlignment.source.forest);
        return a;
    }

    public void align(Alignment a) {
        this.markDivisions(a);
        a.align();
        // Reporting
        for (Taxon node : a.source.taxa()) {
            Answer answer = a.getAnswer(node);
            String reason;
            if (answer == null) reason = "none";
            else reason = answer.reason;
            Integer count = alignmentSummary.get(reason);
            if (count == null) count = 0;
            alignmentSummary.put(reason, 1 + count);
        }
    }

	public void absorb(SourceTaxonomy source, Alignment a) { // called from jython
        this.align(a);
        merge(a);
	}

	public void merge(Alignment a) { // called from jython
        Taxonomy source = a.source;
        try {
            new MergeMachine(source, this, a, mergeSummary).augment();
            this.check();
            this.sources.add(source);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
	}

    // Abbreviation for u.alignment(source) + u.absorb(source, a)
    // for when there are no ad-hoc alignments.
	public Alignment absorb(SourceTaxonomy source) { // called from jython
        Alignment a = this.alignment(source);
        this.absorb(source, a);
        return a;
    }

    // ----- Align divisions from skeleton taxonomy -----

	public void markDivisions(Alignment a) {
        markDivisionsFromSkeleton(a, this.skeletonAlignment);
	}

	// Before every alignment pass (folding source taxonomy into
	// union), all 'division' taxa (from the skeleton) that occur in
	// the source taxonomy are identified and the
	// 'division' field of each is set to the corresponding
	// division node from the skeleton taxonomy.  Also the corresponding
	// division nodes are aligned.

	// This operation is idempotent.

    // skeletonAlignment aligns skeleton to target
    // a will align source to target

    private void markDivisionsFromSkeleton(Alignment a, Alignment skeletonAlignment) {
        Taxonomy skel = skeletonAlignment.source; // maps skeleton to union
        if (!useCompleteSkeleton) {
            this.clearDivisions();
            this.forest.setDivision(skel.forest);
        }

        Taxonomy source = a.source;
        source.forest.setDivision(skel.forest);

        // Instead of the following, we could just get an alignment
        // between the skeleton and the source.

        for (String name : skel.allNames()) {
            Taxon node = highest(source, name);
            if (node != null) {
                Taxon div = skel.unique(name);
                Taxon unode = skeletonAlignment.getTaxon(div);
                if (unode == null)
                    System.err.format("** Skeleton node %s not mapped to union\n", div);
                else if (unode.prunedp)
                    ;           // Viruses
                else if (node.getDivisionProper() != null && node.getDivisionProper() != div)
                    System.err.format("** Help!  Conflict over division mapping: %s have %s want %s\n",
                                      node, node.getDivisionProper(), div);
                else {
                    Taxon otherUnode = a.getTaxon(node);
                    if (otherUnode != null && otherUnode != unode)
                        // e.g. Ctenophora in SILVA
                        System.out.format("| Source node %s maps to %s, not to division %s\n", node, otherUnode, unode);
                    else {
                        a.alignWith(node, unode, "same/by-division-name");
                        // %% this is setting a nonnull div node with a null name
                        node.setDivision(div);
                        if (!useCompleteSkeleton)
                            unode.setDivision(div);
                        //System.out.format("## Division of %s is %s\n", node, div);
                    }
                }
            }
        }
    }

	// Most rootward node in the given taxonomy having a given name
	private static Taxon highest(Taxonomy tax, String name) {
		List<Node> l = tax.lookup(name);
		if (l == null) return null;
		Taxon best = null, otherbest = null;
		int depth = 1 << 30;
		for (Node node : l) {
            Taxon taxon = node.taxon();
            // This is for Ctenophora, Bacteria, Archaea
            if (taxon.rank == Rank.GENUS_RANK) continue;
			int d = taxon.measureDepth();
			if (d < depth) {
				depth = d;
				best = taxon;
				otherbest = null;
			} else if (d == depth && taxon != best)
				otherbest = taxon;
		}
		if (otherbest != null) {
			if (otherbest == best)
				// shouldn't happen
				System.err.format("** Multiply indexed: %s %s %s\n", best, otherbest, depth);
			else
				System.err.format("** Ambiguous division name: %s %s %s\n", best, otherbest, depth);
            return null;
		}
		return best;
	}



    // ----- Finish up -----

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void additionsAndIds(SourceTaxonomy idsource, String amendmentsPath, String newTaxaPath) {
        // Phase 1: assign ids from idsource
        carryOverIds(idsource);

        // Phase 2: apply additions (which contain ids)
        try {
            Addition.processAdditions(amendmentsPath, this);
        } catch (Exception e) {
            // doesn't seem worth the effort to process this in good taste. just march on.
            System.err.format("** Exceptions are annoying (processAdditions): %s\n", e);
        }
		// Phase 3: give new ids to union nodes that didn't get them above.
		this.assignNewIds(newTaxaPath);
		// remember, this = union, idsource = previous version of ott
    }


    // Assign ids from previous version of taxonomy, when possible

	public void carryOverIds(SourceTaxonomy idsource) {
        // Align the taxonomies; generates report
		Alignment a = this.alignment(idsource);
		this.idsourceAlignment = a;

        // Don't bump alignment type counts
        this.markDivisions(a);
        a.align();

		// Copy ids using alignment
		this.transferIds(idsource, a);

        this.prepareMetadata();

        // Report event counts
		this.eventLogger.eventsReport("| ");		// Taxon id clash
	}

	public void assignNewIds(String newTaxaPath) {
        List<Taxon> nodes = new ArrayList<Taxon>();
        for (Taxon root: this.roots())
            findTaxaNeedingIds(root, nodes);

        // cross-check max id with what's stored
        long maxid = maxid(this);
        long sourcemax = maxid(this.idsourceAlignment.source);
        if (sourcemax > maxid) maxid = sourcemax;

        System.out.format("| %s taxa need ids; greatest id so far is %s\n", nodes.size(), maxid);

        if (nodes.size() > 0)
            Addition.assignNewIds(nodes, newTaxaPath);
	}

    public void findTaxaNeedingIds(Taxon node, List<Taxon> nodes) {
        if (node.id == null)
            nodes.add(node);
        if (node.children != null) {
            // Strive for reproducibility
            List<Taxon> children = new ArrayList<Taxon>(node.children);
            children.sort(compareNodesByName);
            for (Taxon child : children)
                findTaxaNeedingIds(child, nodes);
        }
    }

	static Comparator<Taxon> compareNodesByName = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
            if (x == y) return 0;
            if (y.name == null) return -1;
            if (x.name == null) return 1;
            return x.name.compareTo(y.name);
		}
	};

    // The highest numbered id of any taxon in the taxonomy (including merged ids).
	public static long maxid(Taxonomy tax) {
		long maxid = -1;
		for (String id : tax.allIds()) {
            try {
                long idAsLong = Long.parseLong(id);
                if (idAsLong > maxid) maxid = idAsLong;
            } catch (NumberFormatException e) {
                ;
            }
		}
		return maxid;
	}

	public void transferIds0(SourceTaxonomy idsource, Alignment a) {
		System.out.println("--- Assigning ids to union starting with " + idsource.getTag() + " ---");

        Map<Taxon, Taxon> assignments = new HashMap<Taxon, Taxon>();

        int carryOver = 0;
		for (Taxon idnode : idsource.taxa()) {
            // Consider using idnode.id as the id for the union node it maps to
			Taxon unode = a.getTaxon(idnode);
			if (unode != null &&
                unode.id == null &&
                this.lookupId(idnode.id) == null) {

                Taxon haveIdNode = assignments.get(unode);
                if (haveIdNode == null)
                    assignments.put(unode, idnode);
                else if (betterIdNode(idnode, haveIdNode, unode))
                    // Lumping.
                    assignments.put(unode, idnode);
			}
		}

        for (Taxon unode : assignments.keySet())
            unode.setId(assignments.get(unode).id);

        System.out.format("| %s ids transferred\n", assignments.size());

        // Carry over forwards from previous OTT.
        int forwardsCount = 0;
        for (String id : idsource.allIds()) {
            Taxon idnode = idsource.lookupId(id);
            Taxon unode = a.getTaxon(idnode);
            if (unode != null && this.lookupId(id) == null) {
                this.addId(unode, id);
                ++forwardsCount;
            }
        }
        System.out.format("| %s merged ids transferred\n", forwardsCount);
	}

	public void transferIds(SourceTaxonomy idsource, Alignment a) {
        List<String> all = new ArrayList<String>();
        for (String id : idsource.allIds())
            all.add(id);
        Collections.sort(all);
        int count = 0;
        int collisions = 0;
        for (String id : all) {
            Taxon idnode = idsource.lookupId(id);
            Taxon unode = a.getTaxon(idnode);
            if (unode != null) {
                Taxon collision = this.lookupId(id);
                if (collision == null) {
                    this.addId(unode, id);
                    ++count;
                } else if (collision != unode) {
                    System.out.format("| collision: id %s for %s now assigned to %s\n",
                                      id,
                                      idnode,
                                      collision);
                    ++collisions;
                }
            }
        }
        System.out.format("| %s ids transferred, %s collisions\n", count, collisions);
    }

    // Return true if node1 is a better node to get id from than node2
    boolean betterIdNode(Taxon node1, Taxon node2, Taxon unode) {
        if (node1 == null || node1.id == null) return false;
        if (node2 == null || node2.id == null) return true;
        if (node1 == node2) return false;

        // Prefer id node that has new node's source
        if (unode.sourceIds != null) {
            QualifiedId source = unode.sourceIds.get(0);
            boolean s1 = (node1.sourceIds != null && node1.sourceIds.contains(source));
            boolean s2 = (node1.sourceIds != null && node2.sourceIds.contains(source));
            if (s1 != s2) return s1;
        }

        // Prefer id of node of same name
        if (unode.name != null) {
            boolean n1 = unode.name.equals(node1.name);
            boolean n2 = unode.name.equals(node2.name);
            if (n1 != n2) return n1;
        }

        /*
        // Prefer formerly placed nodes ? ...
        boolean p1 = node1.isPlaced();
        boolean p2 = node2.isPlaced();
        if (p1 != p2) return p1;

        // Prefer nodes that are used in phylesystem or synthesis ? ...
        boolean i1 = this.importantIds.lookupId(node1.id) != null;
        boolean i2 = this.importantIds.lookupId(node2.id) != null;
        if (i1 != i2) return i1;
        */

        // Prefer smaller node ids.
        try {
            return Integer.parseInt(node1.id) < Integer.parseInt(node2.id);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ----- "Preferred" ids (those in phylesystem or synthesis) - for reporting -----

	private static Pattern tabPattern = Pattern.compile("\t");

    public SourceTaxonomy importantIds = null;

    // e.g. "ids-that-are-otus.tsv", False
    // e.g. "ids-in-synthesis.tsv", True
    public SourceTaxonomy loadPreferredIds(String filename, boolean seriousp)
           throws IOException {
		System.out.println("--- Loading list of important taxa from " + filename + " ---");
        if (this.importantIds == null)
            this.importantIds = new SourceTaxonomy();
		BufferedReader br = Taxonomy.fileReader(new File(filename));
		String str;
		while ((str = br.readLine()) != null) {
            String[] row = tabPattern.split(str);

            // new - row[1] is list of studies
            String name = null;
            if (row.length > 2) name = row[2];

            String id = row[0];
            Taxon node = this.importantIds.lookupId(id);
            if (node == null) {
                node = new Taxon(this.importantIds, name);
                node.addFlag(Taxonomy.UNPLACED); // prevent warning
                this.importantIds.addRoot(node);
                node.setId(id);
            }

            // node.setSourceIds(row[1]);  these aren't IRIorCURIEs
            if (seriousp)
                node.inSynthesis = true;
        }
        br.close();
        return importantIds;
    }

	// Cf. assignIds()
	// x is a source node drawn from the idsource taxonomy file.
	// target is the union node it might or might not map to.
    // -- appears to be unused now.

	static Answer assessSource(Taxon x, Taxon target) {
		QualifiedId ref = x.putativeSourceRef();
		if (ref != null) {
			String putativeSourceTag = ref.prefix;
			String putativeId = ref.id;

			// Find source node in putative source taxonomy, if any
			QualifiedId sourceThere = null;
			// Every union node should have at least one source node
			// ... except those added through the patch facility ...
			// FIX ME
			if (target.sourceIds == null) return Answer.NOINFO;	  //won't happen?
			for (QualifiedId source : target.sourceIds)
				if (source.prefix.equals(putativeSourceTag)) {
					sourceThere = source;
					break;
				}

			if (sourceThere == null)
				return Answer.no(x, target, "not-same/source",
								 ref
								 + "->" +
								 target.getSourceIdsString());
			if (!putativeId.equals(sourceThere.id))
				return Answer.no(x, target, "not-same/source-id",
								 ref
								 + "->" +
								 sourceThere.toString());
			else
				return Answer.NOINFO;
		} else
			return Answer.NOINFO;
	}

    // Overrides method in class Taxonomy
    public void dumpExtras(String outprefix) throws IOException {
		Set<String> scrutinize = new HashSet<String>();
        scrutinize.addAll(this.eventLogger.namesOfInterest);
		if (this.idsourceAlignment != null)
			scrutinize.addAll(this.dumpDeprecated(this.idsourceAlignment, outprefix + "deprecated.tsv"));
        if (this.eventLogger.namesOfInterest.size() > 0)
            this.dumpLog(outprefix + "log.tsv", scrutinize);
        // this.dumpWeakLog(outprefix + "weaklog.csv");
		this.dumpConflicts(outprefix + "conflicts.tsv");

 	    InterimFormat.writeAsJson(this.alignmentSummary, new File(outprefix, "alignment_summary.json"));
 	    InterimFormat.writeAsJson(this.mergeSummary, new File(outprefix, "merge_summary.json"));

    }

    /*
    void dumpWeakLog(String filename) throws IOException {
        if (this.weakLog.size() == 0) return;
        PrintStream out = Taxonomy.openw(filename);
        for (Answer a : this.weakLog)
            if (z.getTaxon(a.subject) != null) {
                Taxon[] div = z.bridge(a.subject).divergence(a.target);
                Taxon div1 = (div == null ? a.subject.getDivision() : div[0]);
                Taxon div2 = (div == null ? a.target.getDivision() : div[1]);
                out.format("%s,%s,%s,%s,%s,%s,%s\n",
                           a.subject, div1.name,
                           a.target, div2.name,
                           z.getTaxon(a.subject),
                           (a.subject.children == null ? 0 : a.subject.children.size()),
                           (a.target.children == null ? 0 : a.target.children.size())
                           );
            }
		out.close();
    }
    */

    @SuppressWarnings("unchecked") // this.properties is a json object
	public void prepareMetadata() {
		List<Object> sourceMetas = new ArrayList<Object>();
		for (Taxonomy source : this.sources)
			if (source.properties != null)
				sourceMetas.add(source.properties);
			else
				sourceMetas.add(source.getTag());
		this.properties.put("inputs", sourceMetas);
	}

	Set<String> dumpDeprecated(Alignment idsourceAlignment, String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("id\tsize\tname\tsourceinfo\treason\twitness\treplacement" +
                    "\tstatus");

        System.out.format("| prepare union for dump deprecated\n");
        this.inferFlags();  // was done earlier, but why not again - for hidden
        System.out.format("| prepare idsource for dump deprecated\n");
        Taxonomy idsource = idsourceAlignment.source;
        idsource.inferFlags();  // was done earlier, but why not again

		for (String id : idsource.allIds()) {

            Taxon node = idsource.lookupId(id);
            if (!id.equals(node.id)) continue;

			String reason = null; // report on it iff this is nonnull
			String replacementId = null;
			String witness = null;
            String flagstring = "";
            int size = 0;

            // Don't report on ids that aren't used as OTUs - too overwhelming to
            // look at everything.
            // The difference is between about 200 and about 60,000 ids to look at.
            Taxon important = null;
            if (this.importantIds != null)
                important = this.importantIds.lookupId(id);

            Taxon unode = idsourceAlignment.getTaxon(node);
            if (unode == null) {

                if (this.importantIds != null && important == null) {
                    this.markEvent("unimportant/not-mapped", node);
                    continue;
                }

                size = node.count();

                // definitely a problem, need to diagnose
                Taxon idnode = this.lookupId(id);
                if (idnode != null) {
                    // id not retired, used for something else
                    if (id.equals(idnode.id)) {
                        // 16/55 in gone set
                        reason = "id-used-differently";
                        // replacementId = "?=";
                    } else {
                        // 0/55
                        reason = "smushing";
                        replacementId = "?" + idnode.id;
                    }
                } else {
                    // id has been retired
                    List<Node> nodes = idsource.lookup(node.name);
                    List<Node> unodes = this.lookup(node.name);
                    if (unodes == null) {
                        // 0/55
                        if (checkGenderChange(node))
                            reason = "id-retired/gender-change";
                        else
                            reason = "id-retired/name-retired";
                    } else {
                        // None of the unodes has the id in question.

                        if (unodes.size() > nodes.size())
                            // 4/55
                            reason = "id-retired/possible-splitting";
                        else if (nodes.size() > unodes.size())
                            // 21/55
                            reason = "id-retired/probable-lumping";
                        else if (unodes.size() == 1) {
                            // 14/55
                            reason = "id-retired/incompatible-use";
                            Taxon[] div = idsourceAlignment.bridge(node).divergence(unodes.get(0).taxon());
                            if (div != null)
                                witness = div[0].name + "->" + div[1].name;
                        } else
                            // 14/55
                            reason = "id-retired/incompatible-uses";

                        // this is useless, always shows same/...

                        if (unodes.size() == 1) {
                            Taxon div1 = node.getDivision();
                            Taxon tax2 = unodes.get(0).taxon();
                            Taxon div2 = tax2.getDivision();
                            if (div1 != div2) {
                                reason = "id-retired/changed-divisions";
                                witness = String.format("%s->%s",
                                                        (div1 == null ? "forest" : div1.name),
                                                        (div2 == null ? "forest" : div2.name));
                            }
                            replacementId = "!" + tax2.id;
                        }
                    }
                }
            } else {
                // Is mapped

                if (this.importantIds != null && important == null) {
                    this.markEvent("unimportant/mapped", node);
                    continue;
                }

                size = unode.count();

                if (unode.id == null)
                    // why would this happen? - actually it doesn't.
                    reason = "id-changed/old-id-retired";
                else if (!id.equals(unode.id)) {
                    // 143/199
                    ;
                    // Disable this now that the new-forwards.tsv file is being written.
                    // reason = "id-changed/lump-or-split"; // mapped elsewhere, maybe split?
                    // replacementId = '=' + unode.id;
                }
                
                if (unode.isHidden()) {
                    if (!node.isHidden()) {
                        if ((unode.properFlags & Taxonomy.FORMER_CONTAINER) != 0) {
                            if ((unode.properFlags & Taxonomy.MERGED) != 0) {
                                // Can override previously set reason
                                reason = "merged";
                                replacementId = "<" + unode.parent.id;
                                witness = unode.parent.name;
                            } else {
                                Taxon target = unode.parent;
                                reason = "emptied-container";
                                replacementId = "<" + target.id;
                                witness = target.name;
                            }
                        } else {
                            // incertae sedis or annotated hidden
                            reason = "newly-hidden";
                            Taxon highest = null;
                            for (Taxon n = unode; n != null; n = n.parent)
                                if (n.isHidden())
                                    highest = n;
                                else
                                    break;
                            if (highest != null)
                                witness = highest.name;
                        }
                        int newflags  = (unode.properFlags & ~node.properFlags);
                        int newiflags = (unode.inferredFlags & ~node.inferredFlags);
                        if (newflags != 0 || newiflags != 0) {
                            String f = Flag.toString(newflags, newiflags);
                            if (f.length() > 0) // not sure how the == 0 case happens
                                flagstring = ("[" + f + "]");
                        }
                    } else
                        this.markEvent("continued-hidden", node);
                } else if (node.isHidden())
                    // NOT really a deprecation event!  Nice to know about though.
                    this.markEvent("not-deprecated/exposed", node);

                if (replacementId == null) {
                    replacementId = unode.id;
                    if (id.equals(replacementId))
                        replacementId = "=";
                    else
                        reason = "lumped";
                }
            }
            if (replacementId == null) replacementId = "*";
            String serious = "";
            if (important != null)
                if (important.inSynthesis)
                    serious = "synthesis";
                else
                    serious = "phylesystem";

            if (reason != null) {
                out.format("%s\t%s\t%s\t%s\t" + "%s\t%s\t%s\t" + "%s\t\n",
                           id,
                           node.count(),
                           node.name,
                           node.getSourceIdsString(),

                           reason + flagstring,
                           (witness == null ? "" : witness),
                           replacementId,

                           serious);
                this.markEvent(reason, node);
            }
		}
		out.close();

		Set<String> scrutinize = new HashSet<String>();
		for (String name : idsource.allNames())
			for (Node node : idsource.lookup(name))
				if (idsourceAlignment.getTaxon(node.taxon()) == null) {
					scrutinize.add(name);
					break;
				}
		return scrutinize;
	}

    boolean checkGenderChange(Taxon node) {
        if (node.rank != Rank.SPECIES_RANK)
            return false;
        String alt;
        if (node.name.endsWith("us"))
            alt = node.name.substring(0, node.name.length()-2) + "a";
        else if (node.name.endsWith("a"))
            alt = node.name.substring(0, node.name.length()-1) + "us";
        else
            return false;
        return this.lookup(alt) != null;
    }

	// Called on union taxonomy
	// scrutinize is a set of names of especial interest (e.g. deprecated)

	void dumpLog(String filename, Set<String> scrutinize) throws IOException {
        this.eventLogger.dumpLog(filename, scrutinize);
	}

	List<Conflict> conflicts = new ArrayList<Conflict>();

	void dumpConflicts(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
        Conflict.printHeader(out);
        System.out.format("%s conflicts\n", conflicts.size());
		for (Conflict conflict : this.conflicts)
            out.println(conflict.toString());
		out.close();
	}
    
    // ----- Methods meant to be called from jython (patches) -----

	public boolean same(Taxon node1, Taxon node2) {
		return sameness(node1, node2, true, true);
	}

	public boolean notSame(Taxon node1, Taxon node2) {
		return sameness(node1, node2, false, true);
	}

	public boolean sameness(Taxon node1, Taxon node2, boolean whether, boolean setp) {
        if (node1.taxonomy == node2.taxonomy)
            return whetherLumped(node1, node2, whether, setp);
		else {
			System.err.format("** Use Alignment.same to align these nodes: %s %s\n",
							  node1, node2);
			return false;
		}
	}

    // Usually the idspace is set before we get here, bu for debugging
    // it's handy to have the following 'logic'...

	void setIdspace(Taxonomy tax) {
		if (tax.idspace != null) return;
        idspace = tax.idspace;
		if (idspace == null) {
            List<Node> probe = tax.lookup("Caenorhabditis elegans");
            if (probe != null) {
                String id = probe.get(0).taxon().id;
                if (id.equals("6239")) idspace = "ncbi";
                else if (id.equals("2283683")) idspace = "gbif";
                else if (id.equals("395048")) idspace = "ott";
                else if (id.equals("100968828")) idspace = "aux"; // preottol
            }
        }
		// TEMPORARY KLUDGE
		if (idspace == null) {
			List<Node> probe2 = tax.lookup("Asterales");
			if (probe2 != null) {
				String id = probe2.get(0).taxon().id;
				if (id.equals("4209")) idspace = "ncbi";
				if (id.equals("414")) idspace = "gbif";
				if (id.equals("1042120")) idspace = "ott";
			}
		}
        if (idspace == null)
            idspace = "tax";
        tax.idspace = idspace;
	}

    public void watch(String name) {
        this.eventLogger.namesOfInterest.add(name);
    }

}
