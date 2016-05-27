/*

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

  Some people think having multiple classes in one file is terrible
  programming style...	I'll split this into multiple files when I'm
  ready to do so; currently it's much easier to work with in this
  form.

*/

package org.opentreeoflife.smasher;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;
import org.opentreeoflife.taxa.Flag;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.EventLogger;

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
import java.io.PrintStream;
import java.io.File;


public class UnionTaxonomy extends Taxonomy {

	List<SourceTaxonomy> sources = new ArrayList<SourceTaxonomy>();
	SourceTaxonomy idsource = null;
	// One log per name-string
	Map<String, List<Answer>> logs = new HashMap<String, List<Answer>>();
    List<Answer> weakLog = new ArrayList<Answer>();

	static boolean windyp = true;

    Alignment currentAlignment = null;

	UnionTaxonomy(String idspace) {
        super(idspace);
		this.setTag("union");
        this.eventlogger = new EventLogger();
	}

	public static UnionTaxonomy newTaxonomy() {
		return new UnionTaxonomy(null);
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

	public SourceTaxonomy skeleton = null;

	// for jython - methods defined on class Taxonomy
	public void setSkeleton(SourceTaxonomy skel) {
		for (Taxon div : skel.taxa())
			div.setDivision(div);

		this.skeleton = skel;

		// Copy skeleton into union
		this.absorb(skel);		// ?
		for (Taxon div : skel.taxa()) div.mapped.setId(div.id); // ??!!!
	}

	// -----

    // Absorb a new source taxonomy

	public void absorb(SourceTaxonomy tax) {
        try {
            if (tax.idspace == null)
                setIdspace(tax);
            this.mergeIn(tax);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
	}

	void mergeIn(SourceTaxonomy source) {
		Alignment a = this.align(source);
        new MergeMachine(source, this, a).augment();
		source.copyMappedSynonyms(this); // this = union
		UnionTaxonomy.windyp = true; //kludge
	}

	Alignment align(SourceTaxonomy source) {
        UnionTaxonomy union = this;
        union.addSource(source);
        Alignment n = new AlignmentByName(source, union);
        n.cacheInSourceNodes();
        if (false) {
            // For testing purposes, do both kinds of alignment and compare them
            // Code disabled for now - membership based alignment is still experimental
            Alignment m = new AlignmentByMembership(source, union);
            Stat s0 = new Stat("mapped the same by both");
            Stat s1 = new Stat("not mapped by either");
            Stat s2 = new Stat("mapped by name only");
            Stat s2a = new Stat("mapped by name only (ambiguous)");
            Stat s3 = new Stat("mapped by membership only");
            Stat s4 = new Stat("incompatible mappings");
            for (Taxon node : source.taxa()) {
                Answer nAnswer = n.answer(node);
                Answer mAnswer = m.answer(node);
                if (nAnswer == null && mAnswer == null)
                    s1.inc(node, nAnswer, mAnswer);
                else if (mAnswer == null)
                    s2.inc(node, nAnswer, mAnswer);
                else if (nAnswer == null)
                    s3.inc(node, nAnswer, mAnswer); // good - maps by membership but not by name
                else if (nAnswer.target == mAnswer.target)
                    s0.inc(node, nAnswer, mAnswer);
                else if (nAnswer.isYes() && mAnswer.isYes()) // maps by name but not by membership
                    s4.inc(node, nAnswer, mAnswer); // bad - incompatible mappings
                else 
                    s2a.inc(node, nAnswer, mAnswer);
            }
            System.out.println(s0); System.out.println(s1); System.out.println(s2); System.out.println(s2a); 
            System.out.println(s3); System.out.println(s4); 
        }

        union.sources.add(source);
        return n;
	}

    // ----- Aligning individual nodes -----

	// unode is a preexisting node in this taxonomy.

	public void alignWith(Taxon node, Taxon unode, String reason) {
        try {
            Answer answer = Answer.yes(node, unode, reason, null);
            this.alignWith(node, unode, answer);
            answer.maybeLog();
        } catch (Exception e) {
            System.err.format("** Exception in alignWith %s %s\n", node, unode);
            e.printStackTrace();
        }
    }

    // Set the 'mapped' property of this node, carefully
	public void alignWith(Taxon node, Taxon unode, Answer answer) {
		if (node.mapped == unode) return; // redundant
        if (!(unode.taxonomy == this)) {
            System.out.format("** Alignment target %s is not in a union taxonomy\n", node);
            Taxon.backtrace();
        } else if (node.taxonomy == this) {
            System.out.format("** Alignment source %s is not in a source taxonomy\n", unode);
            Taxon.backtrace();
        } else if (node.noMrca() != unode.noMrca()) {
            System.out.format("** attempt to unify forest %s with non-forest %s\n",
                              node, unode);
            Taxon.backtrace();
        } else if (node.mapped != null) {
			// Shouldn't happen - assigning a single source taxon to two
			//	different union taxa
			if (node.report("Already assigned to node in union:", unode))
				Taxon.backtrace();
		} else {
            node.mapped = unode;
            node.answer = answer;
            if (node.name != null && unode.name != null && !node.name.equals(unode.name))
                Answer.yes(node, unode, "synonym-match", node.name).maybeLog();
            if (unode.comapped != null) {
                // Union node has already been matched to, but synonyms are OK
                if (unode.comapped != node)
                    node.markEvent("lumped");
            } else
                unode.comapped = node;
        }
    }

    // ----- Finish up -----

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void assignIds(SourceTaxonomy idsource) {
		this.idsource = idsource;
        this.addSource(idsource);

        this.prepareMetadata();

		Alignment a = this.align(idsource);

        // Reset event counters
		this.eventlogger.resetEvents();

		// Phase 1: recycle previously assigned ids.
		this.transferIds(idsource, a);

		// Phase 2: give new ids to union nodes that didn't get them above.
		long sourcemax = idsource.maxid();
		this.assignNewIds(sourcemax);
		// remember, this = union, idsource = previous version of ott

        // Report event counts
		this.eventlogger.eventsReport("| ");		// Taxon id clash
	}

	public void transferIds(SourceTaxonomy idsource, Alignment a) {
		System.out.println("--- Assigning ids to union starting with " + idsource.getTag() + " ---");

        Map<Taxon, String> assignments = new HashMap<Taxon, String>();

		for (Taxon node : idsource.taxa()) {
            // Consider using node.id as the id for the union node it maps to
			Taxon unode = node.mapped;
			if (unode != null &&
                unode.id == null &&
                this.lookupId(node.id) == null) {

                String haveId = assignments.get(unode);
                if (haveId == null || compareIds(node.id, haveId) < 0)
                    assignments.put(unode, node.id);
			}
		}
        for(Taxon unode : assignments.keySet()) {
            unode.setId(assignments.get(unode));
        }
        System.out.format("| %s ids transferred\n", assignments.size());
	}

    // Return negative, zero, positive depending on whether id1 is better, same, worse than id2
    int compareIds(String id1, String id2) {
        Integer p1 = this.importantIdsImportance.get(id1);
        Integer p2 = this.importantIdsImportance.get(id2);
        if (p1 != null && p2 != null)
            // Longer study id list is better (ergo negative answer)
            return p2 - p1;
        if (p1 == null)
            return 1;
        if (p2 == null)
            return -1;
        try {
            // Smaller ids are better
            return Integer.parseInt(id1) - Integer.parseInt(id2);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ----- "Preferred" ids (those in phylesystem or synthesis) - for reporting -----

	private static Pattern tabPattern = Pattern.compile("\t");

    SourceTaxonomy importantIds = null;
    Map<String, Integer> importantIdsImportance = new HashMap<String, Integer>();

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

            // old
            importantIdsImportance.put(id, row[1].length());

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
		if (this.idsource != null)
			scrutinize.addAll(this.dumpDeprecated(this.idsource, outprefix + "deprecated.tsv"));
        if (this.eventlogger.namesOfInterest.size() > 0)
            this.dumpLog(outprefix + "log.tsv", scrutinize);
        this.dumpWeakLog(outprefix + "weaklog.csv");
		this.dumpConflicts(outprefix + "conflicts.tsv");
    }

    void dumpWeakLog(String filename) throws IOException {
        if (this.weakLog.size() == 0) return;
        PrintStream out = Taxonomy.openw(filename);
        for (Answer a : this.weakLog)
            if (a.subject.mapped != null) {
                Taxon[] div = a.subject.bridge().divergence(a.target);
                Taxon div1 = (div == null ? a.subject.getDivision() : div[0]);
                Taxon div2 = (div == null ? a.target.getDivision() : div[1]);
                out.format("%s,%s,%s,%s,%s,%s,%s\n",
                           a.subject, div1.name,
                           a.target, div2.name,
                           a.subject.mapped,
                           (a.subject.children == null ? 0 : a.subject.children.size()),
                           (a.target.children == null ? 0 : a.target.children.size())
                           );
            }
		out.close();
    }

	public void prepareMetadata() {
		List<Object> sourceMetas = new ArrayList<Object>();
		for (Taxonomy source : this.sources)
			if (source.properties != null)
				sourceMetas.add(source.properties);
			else
				sourceMetas.add(source.getTag());
		this.properties.put("inputs", sourceMetas);
	}

    // Overrides dumpForwards in class Taxonomy
    // *** TBD: also write out simple id aliases within the union taxonomy

    void dumpForwards(String filename) throws IOException {
        if (this.idsource == null) return;
		PrintStream out = Taxonomy.openw(filename);
		out.format("id\treplacement\n");
		for (String id : idsource.idIndex.keySet()) {
            Taxon node = idsource.lookupId(id);
            if (node.mapped != null) {
                Taxon unode = node.mapped;
                if (!id.equals(unode.id)) {
                    String node2name = "";
                    String node2ref = "";
                    String dname = "";
                    String d2name = "";
                    String pname = "";
                    Taxon node2 = idsource.lookupId(unode.id);
                    if (node2 != null) {
                        node2name = node2.name;
                        node2ref = node2.putativeSourceRef().toString();
                        Taxon[] div = node.divergence(node2);
                        if (div != null) {
                            dname = div[0].name;
                            d2name = div[1].name;
                            pname = (div[0].parent == null ? "" : div[0].parent.name);
                        }
                    }
                    out.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                               id, unode.id,
                               node.name, node.putativeSourceRef(), dname,
                               node2name, node2ref, d2name,
                               pname);
                }
            }
        }
        out.close();
    }

	Set<String> dumpDeprecated(SourceTaxonomy idsource, String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("id\tsize\tname\tsourceinfo\treason\twitness\treplacement" +
                    "\tstatus");

        this.eventlogger.resetEvents();
        System.out.format("| prepare union for dump deprecated\n");
        this.inferFlags();  // was done earlier, but why not again - for hidden
        System.out.format("| prepare idsource for dump deprecated\n");
        idsource.inferFlags();  // was done earlier, but why not again

		for (String id : idsource.idIndex.keySet()) {

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

            Taxon node = idsource.lookupId(id);

            if (node.mapped == null) {

                if (this.importantIds != null && important == null) {
                    this.markEvent("unimportant/not-mapped", node);
                    continue;
                }

                size = node.count();

                // definitely a problem, need to diagnose
                Taxon unode = this.lookupId(id);
                if (unode != null) {
                    // id not retired, used for something else
                    if (id.equals(unode.id)) {
                        // 16/55 in gone set
                        reason = "id-used-differently";
                        // replacementId = "?=";
                    } else {
                        // 0/55
                        reason = "smushing";
                        replacementId = "?" + unode.id;
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
                            Taxon[] div = node.divergence(unodes.get(0).taxon());
                            if (div != null)
                                witness = div[0].name + "->" + div[1].name;
                        } else
                            // 14/55
                            reason = "id-retired/incompatible-uses";

                        // this is useless, always shows same/...
                        //if (node.answer != null) witness = node.answer.reason;

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

                Taxon unode = node.mapped;
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
				if (node.taxon().mapped == null) {
					scrutinize.add(name);
					break;
				}
		return scrutinize;
	}

	// Called on union taxonomy
	// scrutinize is a set of names of especial interest (e.g. deprecated)

	void dumpLog(String filename, Set<String> scrutinize) throws IOException {
		PrintStream out = Taxonomy.openw(filename);

		// Strongylidae	nem:3600	yes	same-parent/direct	3600	Strongyloidea	false
		out.println("name\t" +
					"source_qualified_id\t" +
					"parity\t" +
					"union_uid\t" +
					"reason\t" +
					"witness\t +");

		// this.logs is indexed by taxon name
		if (false)
			for (List<Answer> answers : this.logs.values()) {
				boolean interestingp = false;
				for (Answer answer : answers)
					if (answer.isInteresting()) {interestingp = true; break;}
				if (interestingp)
					for (Answer answer : answers)
						out.println(answer.dump());
			}
        for (String name : scrutinize) {
            List<Answer> answers = this.logs.get(name);
            if (answers != null)
                for (Answer answer : answers)
                    out.println(answer.dump());
            else
                // usually a silly synonym
                // System.out.format("No logging info for name %s\n", name);
                ;
        }

		if (false) {
			Set<String> seen = new HashSet<String>();
			for (Taxon node : this.taxa())	// preorder
				if (!seen.contains(node.name)) {
					List<Answer> answers = this.logs.get(node.name);
					if (answers == null) continue; //shouldn't happen
					boolean interestingp = false;
					for (Answer answer : answers)
						if (answer.isInteresting()) {interestingp = true; break;}
					if (interestingp)
						for (Answer answer : answers)
							out.println(answer.dump());
					seen.add(node.name);
				}
			// might be missing some log entries for synonyms
		}

		out.close();
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
				while (nearestMapped != null && nearestMapped.mapped == null)
					nearestMapped = nearestMapped.parent;
				if (nearestMapped == null) {
					foo.report("No matches, can't compute mrca", match);
					return true;
				}
				nearestMappedMapped = nearestMapped.mapped;
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
    
    // ----- Methods meant to be called from jython (patches) -----

	public boolean same(Taxon node1, Taxon node2) {
		return sameness(node1, node2, true, true);
	}

	public boolean notSame(Taxon node1, Taxon node2) {
		return sameness(node1, node2, false, true);
	}

	public boolean sameness(Taxon node1, Taxon node2, boolean whether, boolean setp) {
		Taxon unode, snode;
		if (node1 == null || node2 == null) return false; // Error already reported?
		if (node1.taxonomy instanceof UnionTaxonomy) {
			unode = node1;
			snode = node2;
		} else if (node2.taxonomy instanceof UnionTaxonomy) {
			unode = node2;
			snode = node1;
		} else if (node1.mapped != null) {
			unode = node1.mapped;
			snode = node2;
		} else if (node2.mapped != null) {
			unode = node2.mapped;
			snode = node1;
		} else if (node1.taxonomy == node2.taxonomy) {
            return whetherLumped(node1, node2, whether, setp);
		} else {
			System.err.format("** One of the two nodes must be already mapped to the union taxonomy: %s %s\n",
							  node1, node2);
			return false;
		}
		if (!(snode.taxonomy instanceof SourceTaxonomy)) {
			System.err.format("** One of the two nodes must come from a source taxonomy: %s %s\n", unode, snode);
			return false;
		}
        // start logging this name
        if (snode.name != null && this.eventlogger != null)
            this.eventlogger.namesOfInterest.add(snode.name);
		if (whether) {			// same
			if (snode.mapped != null) {
				if (snode.mapped != unode) {
					System.err.format("** The taxa have already been determined to be different: %s\n", snode);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                this.alignWith(snode, unode, "same/ad-hoc");
                return true;
            } else return false;
		} else {				// notSame
			if (snode.mapped != null) {
				if (snode.mapped == unode) {
					System.err.format("** The taxa have already been determined to be the same: %s\n", snode);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                // Give the source node (snode) a place to go in the union that is
                // different from the union node it's different from
                Taxon evader = new Taxon(unode.taxonomy, unode.name);
                this.alignWith(snode, evader, "not-same/ad-hoc");

                unode.taxonomy.addRoot(evader);
                // Now evader != unode, as desired.
                return true;
            } else return false;
		}
	}

	// The image of a taxon under an alignment.
	public Taxon image(Taxon subject) {
		if (subject.taxonomy == this)
			return subject;
		Taxon m = subject.mapped;
		if (m == null)
			return null;
		if (m.taxonomy != this)
			return null;
		return m;
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

