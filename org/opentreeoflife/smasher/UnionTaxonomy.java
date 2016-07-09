/*

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

  Some people think having multiple classes in one file is terrible
  programming style...	I'll split this into multiple files when I'm
  ready to do so; currently it's much easier to work with in this
  form.

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


public class UnionTaxonomy extends Taxonomy {

	List<SourceTaxonomy> sources = new ArrayList<SourceTaxonomy>();
	Alignment idsourceAlignment = null;
	// One log per name-string
    List<Answer> weakLog = new ArrayList<Answer>();

	static boolean windyp = true;

    // a new Alignment object for each source taxonomy being absorbed
    Alignment currentAlignment = null;

	UnionTaxonomy(String idspace) {
        super(idspace);
		this.setTag("union");
        this.eventlogger = new EventLogger();
        this.startQidIndex();
	}

	public static UnionTaxonomy newTaxonomy(String idspace) {
		return new UnionTaxonomy(idspace);
	}

    public void addSource(SourceTaxonomy source) {
        source.setEventLogger(this.eventlogger);
    }

	// ----- INITIALIZATION FOR EACH SOURCE -----

	// The 'division' field of a Taxon is always either null or a
	// taxon belonging to the skeleton taxonomy.

	public Alignment skeletonAlignment = null;

	// for jython - methods defined on class Taxonomy
	public void setSkeleton(SourceTaxonomy skel) {
		for (Taxon div : skel.taxa())
			div.setDivision(div);

		// Copy skeleton into union
		Alignment a = this.absorb(skel);
		for (Taxon div : skel.taxa()) {
            Taxon udiv = a.getTaxon(div);
            udiv.setId(div.id);
            udiv.setDivision(div);
        }
        this.skeletonAlignment = a;
	}

	// -----

    // Absorb a new source taxonomy

	public Alignment absorb(SourceTaxonomy source) { // called from jython
        Alignment a = alignment(source);
        this.absorb(source, a);
        return a;
    }

	public Alignment alignment(SourceTaxonomy source) { // called from jython
        if (source.idspace == null)
            setIdspace(source);
        this.addSource(source);
        return new AlignmentByName(source, this);
    }

	public void absorb(SourceTaxonomy source, Alignment a) { // called from jython
        try {
            this.align(source, a);
            this.sources.add(source);
            new MergeMachine(source, this, a).augment();
            copyMappedSynonymsFrom(this, a); // this = union
            this.check();
            UnionTaxonomy.windyp = true; //kludge
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
	}

	Alignment align(SourceTaxonomy source) {
        return align(source, this.alignment(source));
    }

	Alignment align(SourceTaxonomy source, Alignment n) {
        n.align();
        UnionTaxonomy union = this;
        n.cacheLubs();
        return n;
	}

	// Propogate synonyms from source taxonomy (= this) to union or selection.
	// Some names that are synonyms in the source might be primary names in the union,
	//	and vice versa.
	public void copyMappedSynonymsFrom(Taxonomy source, Alignment a) {
        Taxonomy targetTaxonomy = this;
		int count = 0;
        for (Taxon taxon : source.taxa()) {
            Taxon targetTaxon = a.getTaxon(taxon);
            if (targetTaxon == null) continue;
            count += taxon.copySynonymsTo(targetTaxon);
        }
		if (count > 0)
			System.err.println("| Added " + count + " synonyms");
	}


    // ----- Finish up -----

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void assignIds(SourceTaxonomy idsource, String amendmentsPath) {
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
		this.assignNewIds(amendmentsPath);
		// remember, this = union, idsource = previous version of ott
    }

	public void carryOverIds(SourceTaxonomy idsource) {
        // Align the taxonomies; generates report
		Alignment a = this.align(idsource);
		this.idsourceAlignment = a;

        // Reset event counters (because report was generated)
		this.eventlogger.resetEvents();

        // Last ditch effort - attempt to match by qid
        // alignByQid(idsource, a);

		// Copy ids using alignment
		this.transferIds(idsource, a);

        this.prepareMetadata();

        // Report event counts
		this.eventlogger.eventsReport("| ");		// Taxon id clash
	}

    // This should be moot given new logic in AlignmentByName
    public void alignByQid(SourceTaxonomy idsource, Alignment a) {
        int already = 0;
        int sameName = 0;
        int differentName = 0;
        int blocked = 0;
        for (Taxon taxon : idsource.taxa()) {
            if (a.getTaxon(taxon) == null && taxon.sourceIds != null)
                for (QualifiedId qid : taxon.sourceIds) {
                    Node unode = this.lookupQid(qid);
                    if (unode != null) {
                        Taxon utaxon = unode.taxon();
                        if (!utaxon.prunedp) {
                            if (taxon.name != null && taxon.name.equals(utaxon.name)) {
                                a.alignWith(taxon, utaxon, "qid-match-same-name");
                                ++sameName;
                            } else {
                                a.alignWith(taxon, utaxon, "qid-match-different-name");
                                ++differentName;
                            }
                        } else
                            ++already;
                        break;
                    }
                }
        }
        System.out.format("| %s new qid+name matches, %s qid-only, %s prior, %s ambiguous\n",
                          sameName, differentName, already, blocked);
    }

	public void assignNewIds(String amendmentsPath) {
        List<Taxon> nodes = new ArrayList<Taxon>();
        for (Taxon root: this.roots())
            findTaxaNeedingIds(root, nodes);

        // cross-check max id with what's stored
        long maxid = maxid(this);
        long sourcemax = maxid(this.idsourceAlignment.source);
        if (sourcemax > maxid) maxid = sourcemax;

        System.out.format("| %s taxa need ids; greatest id so far is %s\n", nodes.size(), maxid);

        if (nodes.size() > 0)
            Addition.assignNewIds(nodes, maxid, amendmentsPath);
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

	public void transferIds(SourceTaxonomy idsource, Alignment a) {
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

    SourceTaxonomy importantIds = null;

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

            node.setSourceIds(row[1]);
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
        scrutinize.addAll(this.eventlogger.namesOfInterest);
		if (this.idsourceAlignment != null)
			scrutinize.addAll(this.dumpDeprecated(this.idsourceAlignment, outprefix + "deprecated.tsv"));
        if (this.eventlogger.namesOfInterest.size() > 0)
            this.dumpLog(outprefix + "log.tsv", scrutinize);
        // this.dumpWeakLog(outprefix + "weaklog.csv");
		this.dumpConflicts(outprefix + "conflicts.tsv");
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

        this.eventlogger.resetEvents();
        System.out.format("| prepare union for dump deprecated\n");
        this.inferFlags();  // was done earlier, but why not again - for hidden
        System.out.format("| prepare idsource for dump deprecated\n");
        SourceTaxonomy idsource = idsourceAlignment.source;
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
                                witness = div1.name + "->" + div2.name;
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
                                Taxon target = node.lub;
                                if (target == null) target = unode.parent;
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
                    // NOt really a deprecation event!  Nice to know about though.
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
            if (important != null && important.inSynthesis)
                serious = "synthesis";

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

	// Called on union taxonomy
	// scrutinize is a set of names of especial interest (e.g. deprecated)

	void dumpLog(String filename, Set<String> scrutinize) throws IOException {
        this.eventlogger.dumpLog(filename, scrutinize);
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

    /*
	// N.b. this is in source taxonomy, match is in union
	boolean separationReport(String note, Taxon foo, Taxon match) {
		if (foo.startReport(note)) {
			System.out.println(note);

			Taxon nearestMapped = foo;			 // in source taxonomy
			Taxon nearestMappedMapped = foo;	 // in union taxonomy

			if (foo.taxonomy != match.taxonomy) {
				if (!(foo.taxonomy instanceof SourceTaxonomy) ||
					!(match.taxonomy instanceof UnionTaxonomy)) {
					foo.report("Type dysfunction", match);
					return true;
				}
				// Need to cross from source taxonomy over into the union one
				while (nearestMapped != null &&
                       (nearestMappedMapped = alignment.getTaxon(nearestMapped)) == null)
					nearestMapped = nearestMapped.parent;
				if (nearestMapped == null) {
					foo.report("No matches, can't compute mrca", match);
					return true;
				}
				if (nearestMappedMapped.taxonomy != match.taxonomy) {
					foo.report("Not in matched taxonomies", match);
					return true;
				}
			}

			Taxon mrca = match.carefulMrca(nearestMappedMapped); // in union tree
			if (mrca == null || mrca.noMrca()) {
				foo.report("In unconnected trees !?", match);
				return true;
			}

			// Number of steps in source tree before crossing over
			int d0 = foo.measureDepth() - nearestMapped.measureDepth();

			// Steps from source node up to mrca
            int dm = mrca.measureDepth();
			int d1 = d0 + (nearestMappedMapped.measureDepth() - dm);
			int d2 = match.measureDepth() - dm;
			int d3 = (d2 > d1 ? d2 : d1);
			String spaces = "                                                                ";
			Taxon n1 = foo;
			for (int i = d3 - d1; i <= d3; ++i) {
				if (n1 == nearestMapped)
					n1 = nearestMappedMapped;
				System.out.println("  " + spaces.substring(0, i) + n1.toString(match));
				n1 = n1.parent;
			}
			Taxon n2 = match;
			for (int i = d3 - d2; i <= d3; ++i) {
				System.out.println("  " + spaces.substring(0, i) + n2.toString(foo));
				n2 = n2.parent;
			}
			if (n1 != n2)
				System.err.println("Bug: " + n1 + " != " + n2);
			return true;
		}
		return false;
	}
    */
    
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

}


class Stat {
    String tag;
    int i = 0;
    int inc(Taxon x, Answer n, Answer m) { if (i<5) System.out.format("%s %s %s %s\n", tag, x, n, m); return ++i; }
    Stat(String tag) { this.tag = tag; }
    public String toString() { return "" + i + " " + tag; }
}

