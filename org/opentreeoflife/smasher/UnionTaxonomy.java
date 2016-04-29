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
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Collections;
import java.util.Collection;
import java.io.PrintStream;
import java.io.File;
import java.net.URI;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;
import org.semanticweb.skos.*;
import org.semanticweb.skosapibinding.SKOSManager;
import org.semanticweb.skosapibinding.SKOSFormatExt;


public class UnionTaxonomy extends Taxonomy {

	List<SourceTaxonomy> sources = new ArrayList<SourceTaxonomy>();
	SourceTaxonomy idsource = null;
	SourceTaxonomy auxsource = null;
	// One log per name
	Map<String, List<Answer>> logs = new HashMap<String, List<Answer>>();
    List<Answer> weakLog = new ArrayList<Answer>();

	static boolean windyp = true;

	UnionTaxonomy() {
        super();
		this.setTag("union");
        this.eventlogger = new EventLogger();
	}

	public static UnionTaxonomy newTaxonomy() {
		return new UnionTaxonomy();
	}

    public void addSource(SourceTaxonomy source) {
        source.setEventLogger(this.eventlogger);
    }

	// -----
	// The 'division' field of a Taxon is always either null or a
	// taxon belonging to the skeleton taxonomy.

	public SourceTaxonomy skeleton = null;

	// for jython - methods defined on class Taxonomy
	public void setSkeleton(SourceTaxonomy skel) {
		UnionTaxonomy union = (UnionTaxonomy)this;
		union.setSkeletonUnion(skel);
	}
	public void markDivisions(SourceTaxonomy source) {
		UnionTaxonomy union = (UnionTaxonomy)this;
		union.markDivisionsUnion(source);
	}

	// Method usually used on union taxonomies, I would think...
	public void setSkeletonUnion(SourceTaxonomy skel) {
		// Prepare
		for (Taxon div : skel.taxa())
			div.setDivision(div);

		UnionTaxonomy union = (UnionTaxonomy)this;
		union.skeleton = skel;

		// Copy skeleton into union
		union.absorb(skel);		// ?
		for (Taxon div : skel.taxa()) div.mapped.setId(div.id); // ??!!!
	}

	// this = a union taxonomy.
	// Set 'division' for division taxa in source
	public void markDivisionsUnion(Taxonomy source) {
		if (this.skeleton == null)
			this.pin(source);	// Obsolete code, for backward compatibility!
		else
            markDivisionsFromSkeleton(source, this.skeleton);
	}

	// Before every alignment pass (folding source taxonomy into
	// union), all 'division' taxa (from the skeleton) that occur in
	// either the union or the source taxonomy are identified and the
	// 'division' field of each one is set to the corresponding
	// division node from the union taxonomy.  Also the corresponding
	// division nodes are aligned.

	// This operation is idempotent.

    public void markDivisionsFromSkeleton(Taxonomy source, Taxonomy skel) {
        for (String name : skel.allNames()) {
            Taxon skelnode = highest(skel, name);
            Taxon node = highest(source, name);
            Taxon unode = highest(this, name);
            if (node != null)
                node.setDivision(skelnode);
            if (unode != null)
                unode.setDivision(skelnode);
            if (node != null && unode != null) {
                if (node.mapped != null) {
                    if (node.mapped != unode)
                        System.out.format("** Help!  Conflict over division mapping: %s %s %s\n",
                                          node, node.mapped, unode);
                } else
                    this.alignWith(node, unode, "same/by-division-name");
            }
        }
    }

    /*
	boolean checkDivisionsFromSkeleton(Taxon div, Taxonomy source) {
		Taxon unode = highest(this, div.name);
		if (div.children != null) {
			Taxon hit = null, miss = null;
			for (Taxon child : div.children) {
				if (checkDivisionsFromSkeleton(child, source))
					hit = child;
				else
					miss = child;
			}
			if (hit != null && miss != null)
				System.err.format("** Division %s was found but its sibling %s wasn't\n", hit.name, miss.name);
		}
		if (unode != null) {
			unode.setDivision(unode);
			Taxon node = highest(source, div.name);
			if (node != null &&
				(node.mapped == null || node.mapped == unode)) {   // Cf. notSame
				node.setDivision(unode);
				node.alignWith(unode, "same/division-name");
				return true;
			}
		}
		return false;
	}
    */

	// List determined manually and empirically
	// @deprecated
	void pin(Taxonomy source) {
		String[][] pins = {
			// Stephen's list
			{"Fungi"},
			{"Bacteria"},
			{"Alveolata"},
			// {"Rhodophyta"},	creates duplicate of Cyanidiales
			{"Glaucophyta", "Glaucocystophyceae"},
			{"Haptophyta", "Haptophyceae"},
			{"Choanoflagellida"},
			{"Metazoa", "Animalia"},
			{"Chloroplastida", "Viridiplantae", "Plantae"},
			// JAR's list
			{"Mollusca"},
			{"Arthropoda"},		// Tetrapoda, Theria
			{"Chordata"},
			// {"Eukaryota"},		// doesn't occur in gbif, but useful for ncbi/ncbi test merge
			// {"Archaea"},			// ambiguous in ncbi
			{"Viruses"},
		};
		int count = 0;
		for (int i = 0; i < pins.length; ++i) {
			String names[] = pins[i];
			Taxon n1 = null, div = null;
			// The division (div) is in the union taxonomy.
			// For each pinnable name, look for it in both taxonomies
			// under all possible synonyms
			for (int j = 0; j < names.length; ++j) {
				String name = names[j];
				Taxon m1 = highest(source, name);
				if (m1 != null) n1 = m1;
				Taxon m2 = highest(this, name);
				if (m2 != null) div = m2;
			}
			if (div != null) {
				div.setDivision(div);
				if (n1 != null)
					n1.setDivision(div);
				if (n1 != null && div != null)
					this.alignWith(n1, div, "same/pinned"); // hmm.  TBD: move this out of here
				if (n1 != null || div != null)
					++count;
			}
		}
		if (count > 0)
			System.out.println("Pinned " + count + " out of " + pins.length);
	}

	// Most rootward node in this taxonomy having a given name
	public Taxon highest(Taxonomy tax, String name) { // See pin()
		List<Node> l = tax.lookup(name);
		if (l == null) return null;
		Taxon best = null, otherbest = null;
		int depth = 1 << 30;
		for (Node nodenode : l) {
            Taxon node = nodenode.taxon();
			int d = node.measureDepth();
			if (d < depth) {
				depth = d;
				best = node;
				otherbest = null;
			} else if (d == depth)
				otherbest = node;
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

    // -----

	// unode is a preexisting node in this taxonomy.

	public void alignWith(Taxon node, Taxon unode, String reason) {
        try {
            Answer answer = Answer.yes(node, unode, reason, null);
            this.alignWith(node, unode, answer);
            answer.maybeLog();
        } catch (Exception e) {
            System.err.format("Exception in alignWith\n");
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
        new MergeMachine(source, this).augment(a);
		source.copyMappedSynonyms(this); // this = union
		UnionTaxonomy.windyp = true; //kludge
	}

	Alignment align(SourceTaxonomy source) {
        UnionTaxonomy union = this;
        union.addSource(source);
        Alignment n = new AlignmentByName(source, union);
        n.cacheInSourceNodes();
        if (false) {
            // Code disabled for now - membership based alignment is still experimental
            // For testing purposes, do both kinds of alignment and compare them
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
        Integer p1 = this.importantIdsFoo.get(id1);
        Integer p2 = this.importantIdsFoo.get(id2);
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

	private static Pattern tabPattern = Pattern.compile("\t");

    SourceTaxonomy importantIds = null;
    Map<String, Integer> importantIdsFoo = new HashMap<String, Integer>();

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
                this.importantIds.addRoot(node);
            }
            node.setId(id);

            // old
            importantIdsFoo.put(id, row[1].length());

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
        this.inferFlags();  // was done earlier, but why not again
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
    
    // Methods meant to be called from jython (patches)

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

// Create new nodes in the union taxonomy, when source nodes don't
// match, and hook them up appropriately.

class MergeMachine {

    UnionTaxonomy union;
    SourceTaxonomy source;

    MergeMachine(SourceTaxonomy source, UnionTaxonomy union) {
        this.source = source;
        this.union = union;
    }

	void augment(Alignment a) {
        // a is unused for the time being - alignment is cached in .mapped

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

    public Taxon alignWithNew(Taxon node, Taxonomy target, String reason) {
        Taxon newnode = target.dupWithoutId(node, reason);
        node.mapped = newnode;
        newnode.comapped = node;
        node.answer = Answer.yes(node, newnode, reason, null);
        node.answer.maybeLog();
        return newnode;
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

	// Method called for every node in the source taxonomy.
	// Input is node in source taxonomy.  Returns node in union taxonomy, or null.
    // Result can be detached (meaning caller must attach it) or not
    // (meaning it's already connected to the union taxonomy).
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
                // Examine parents of mapped siblings
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
                        System.out.format("!! Parent of %s's children's images, %s, does not descend from %s\n",
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
            Taxon augChild = child.mapped;
            if (augChild == null)
                ;
            else if (augChild.noMrca())
                ;
            else if (augChild.isDetached()) {
                // "new" child
                Taxon nu = augChild;
                if (target == nu)
                    ;               // Lacrymaria Morganella etc. - shouldn't happen
                else if (target.descendsFrom(nu)) {
                    System.out.format("** Adoption would create a cycle\n");
                    System.out.format("%s ?< ", nu);
                    target.showLineage(nu.parent);

                    // Need to do something with it
                    reject(child, "reject/cycle", union.forest, Taxonomy.UNPLACED);
                    target.taxonomy.addRoot(nu);
                } else {
                    //if (??child??.isRoot())
                    //    child.markEvent("placed-former-root");
                    target.addChild(nu); // if unplaced, stay unplaced
                    nu.addFlag(flags);
                }
            } else if (!augChild.isPlaced()) {
                // "old" child placed not well placed in union.  consider moving it
                Taxon p = augChild;
                // is target a better (more specific) placement for p than p's current parent?
                if (!target.descendsFrom(p.parent))
                    child.markEvent("not-placed/does-not-descend");
                else if (target == p.parent) {
                    // A placement here could promote an unplaced taxon in union to placed...
                    // sort of dangerous, because later taxonomies (e.g. worms) tend to be unreliable
                    if (flags > 0) {
                        child.markEvent("not-placed/already-unplaced");
                    } else {
                        // System.out.format("| %s not placed because %s goes to %s\n", p, source, p.parent);
                        child.markEvent("not-placed/same-taxon");
                    }
                } else if (target.descendsFrom(p)) {
                    if (false)
                        System.out.format("| Moving %s from %s to %s (says %s) would lose information\n",
                                          p, p.parent, target, source);
                    child.markEvent("not-placed/would-lose-information");
                } else if (p.isRoot() && child.isPlaced()) {
                    System.out.format("| %s moved from root to %s because %s\n", p, target, source);
                    Answer.noinfo(child, null, "promoted/from-root", target.name).maybeLog(union);
                    p.changeParent(target, 0);
                } else {
                    //System.out.format("| %s moved to %s because %s, was under %s\n", p, target, source, p.parent);
                    Answer.noinfo(child, null, "promoted/internal", target.name).maybeLog(union);
                    p.changeParent(target, flags | (child.properFlags & Taxonomy.INCERTAE_SEDIS_ANY));
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
            union.conflicts.add(new Conflict(node, alice, bob, node.isHidden()));
            if (union.markEvent("reported conflict"))
                System.out.format("%s %s\n", union.conflicts.size(), node);
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

// Assess a criterion for judging whether x <= target or not x <= target
// Positive means yes, negative no, zero I couldn't tell you
// x is source node, target is union node

abstract class Criterion {

	abstract Answer assess(Taxon x, Taxon target);

    // Horrible kludge to avoid having to rebuild or maintain the name index

	static Criterion prunedp =
		new Criterion() {
			public String toString() { return "prunedp"; }
			Answer assess(Taxon x, Taxon target) {
                if (x.prunedp || target.prunedp)
                    return Answer.no(x, target, "not-same/prunedp", null);
                else
                    return Answer.NOINFO;
            }
        };

    static boolean HALF_DIVISION_EXCLUSION = true;

    static int kludge = 0;
    static int kludgeLimit = 100;

	static Criterion division =
		new Criterion() {
			public String toString() { return "same-division"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv || xdiv.noMrca() || ydiv.noMrca())
					return Answer.NOINFO;
				else if (xdiv.descendsFrom(ydiv))
                    return Answer.NOINFO;
				else if (ydiv.descendsFrom(xdiv))
                    return Answer.NOINFO;
                else
                    return Answer.heckNo(subject, target, "not-same/division", xdiv.name);
			}
		};

	static Criterion weakDivision =
		new Criterion() {
			public String toString() { return "same-division-weak"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv)
					return Answer.weakYes(subject, target, "same/division", xdiv.name);
				else if (xdiv.noMrca() || ydiv.noMrca())
					return Answer.NOINFO;
				else if (true)
                    // about 17,000 of these... that's too many
                    return Answer.weakNo(subject, target, "not-same/weak-division", xdiv.name);
                else
					return Answer.NOINFO;
			}
		};

	static Criterion eschewTattered =
		new Criterion() {
			public String toString() { return "eschew-tattered"; }
			Answer assess(Taxon x, Taxon target) {
				if (!target.isPlaced() //from a previous merge
					&& isHomonym(target))  
					return Answer.weakNo(x, target, "not-same/unplaced", null);
				else
					return Answer.NOINFO;
			}
		};

	// Homonym discounting synonyms
	static boolean isHomonym(Taxon taxon) {
		List<Node> alts = taxon.taxonomy.lookup(taxon.name);
		if (alts == null) {
			System.err.println("Name not indexed !? " + taxon.name);
			return false;
		}
		for (Node alt : alts)
			if (alt != taxon && alt.taxonNameIs(taxon.name))
				return true;
		return false;
	}

	// x is source node, target is union node

	static Criterion lineage =
		new Criterion() {
			public String toString() { return "same-ancestor"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon y0 = scan(target, x.taxonomy);	  // ignore names not known in both taxonomies
				Taxon x0 = scan(x, target.taxonomy);
				if (x0 == null || y0 == null)
					return Answer.NOINFO;

				if (x0.name == null)
					System.err.println("! No name? 1 " + x0 + "..." + y0);
				if (y0.name == null)
					System.err.println("! No name? 2 " + x0 + "..." + y0);

				if (x0.name.equals(y0.name))
					return Answer.heckYes(x, target, "same/parent+parent", x0.name);
				else if (online(x0.name, y0))
					// differentiating the two levels
					// helps to deal with the Nitrospira situation (7 instances)
					return Answer.heckYes(x, target, "same/ancestor+parent", x0.name);
				else if (online(y0.name, x0))
					return Answer.heckYes(x, target, "same/parent+ancestor", y0.name);
				else
					// Incompatible parents.  Who knows what to do.
					return Answer.NOINFO;
			}
		};

	// Find a near-ancestor (parent, grandparent, etc) node that's in
	// common with the other taxonomy
	Taxon scan(Taxon node, Taxonomy other) {
		Taxon up = node.parent;

		// Cf. informative() method
		// Without this we get ambiguities when the taxon is a species
		while (up != null && up.name != null && node.name.startsWith(up.name))
			up = up.parent;

		while (up != null && up.name != null && other.lookup(up.name) == null)
			up = up.parent;

		if (up != null && up.name == null) {
			System.err.println("!? Null name: " + up + " ancestor of " + node);
			Taxon u = node;
			while (u != null) {
				System.err.println(u);
				u = u.parent;
			}
		}
		return up;
	}

	static boolean online(String name, Taxon node) {
		for ( ; node != null; node = node.parent)
			if (node.name.equals(name)) return !node.noMrca(); // kludge
		return false;
	}

	static Criterion subsumption =
		new Criterion() {
			public String toString() { return "overlaps"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon a = AlignmentByName.antiwitness(x, target);
				Taxon b = AlignmentByName.witness(x, target);
				if (b != null) { // good
					if (a == null)	// good
						// 2859
						return Answer.heckYes(x, target, "same/is-subsumed-by", b.name);
					else
						// 94
						return Answer.yes(x, target, "same/overlaps", b.name);
				} else {
					if (a == null)
						// ?
						return Answer.NOINFO;
					else if (target.children != null)		// bad
						// 13 ?
						return Answer.no(x, target, "not-same/incompatible", a.name);
                    else
						return Answer.NOINFO;
				}
			}
		};

	static Criterion sameSourceId =
		new Criterion() {
			public String toString() { return "same-source-id"; }
			Answer assess(Taxon x, Taxon target) {
				// x is source node, target is union node.
				QualifiedId xid = maybeQualifiedId(x);
				QualifiedId yid = maybeQualifiedId(target);
                if (xid != null && xid.equals(yid))
					return Answer.yes(x, target, "same/source-id", null);
				else
					return Answer.NOINFO;
			}
		};


	// Match NCBI or GBIF identifiers
	// This kicks in when we try to map the previous OTT to assign ids, after we've mapped GBIF.
	// x is a node in the old OTT.	target, the union node, is in the new OTT.
	static Criterion anySourceId =
		new Criterion() {
			public String toString() { return "any-source-id"; }
			Answer assess(Taxon x, Taxon target) {
				// x is source node, target is union node.
				// Two cases:
				// 1. Mapping x=NCBI to target=union(SILVA): target.sourceIds contains x.id
				// 2. Mapping x=idsource to target=union: x.sourceIds contains ncbi:123
				// compare x.id to target.sourcenode.id
				QualifiedId xid = maybeQualifiedId(x);
                if (xid == null) return Answer.NOINFO;
				Collection<QualifiedId> yids = target.sourceIds;
				if (yids == null)
					return Answer.NOINFO;
				for (QualifiedId ysourceid : yids)
					if (xid.equals(ysourceid))
						return Answer.yes(x, target, "same/any-source-id-1", null);
				if (x.sourceIds != null)
					for (QualifiedId xsourceid : x.sourceIds)
						for (QualifiedId ysourceid : yids)
							if (xsourceid.equals(ysourceid))
								return Answer.yes(x, target, "same/any-source-id-2", null);
				return Answer.NOINFO;
			}
		};

    static QualifiedId maybeQualifiedId(Taxon node) {
        QualifiedId qid = node.putativeSourceRef();
        if (qid != null) return qid;
        if (node.id != null) return node.getQualifiedId();
        else return null;
    }

	// Buchnera in Silva and 713
	static Criterion knowDivision =
		new Criterion() {
			public String toString() { return "same-division-knowledge"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon xdiv = x.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv != ydiv) // One might be null
					// Evidence of difference, good enough to prevent name-only matches
					return Answer.heckNo(x, target, "not-same/division-knowledge", x.divisionName());
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Steganina, Tripylina in NCBI - they're distinguishable by their ranks
	static Criterion byRank =
		new Criterion() {
			public String toString() { return "same-rank"; }
			Answer assess(Taxon x, Taxon target) {
				if ((x == null ?
					 x == target :
					 (x.rank != Rank.NO_RANK &&
					  x.rank.equals(target.rank))))
					// Evidence of difference, but not good enough to overturn name evidence
					return Answer.weakYes(x, target, "same/rank", x.rank);
				else
					return Answer.NOINFO;
			}
		};

	static Criterion byPrimaryName =
		new Criterion() {
			public String toString() { return "same-primary-name"; }
			Answer assess(Taxon x, Taxon target) {
				if (x.name.equals(target.name))
					return Answer.weakYes(x, target, "same/primary-name", x.name);
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Paraphelenchus
	// E.g. Steganina in NCBI - distinguishable by their ranks
	static Criterion elimination =
		new Criterion() {
			public String toString() { return "name-in-common"; }
			Answer assess(Taxon x, Taxon target) {
				return Answer.weakYes(x, target, "same/name-in-common", null);
			}
		};

	static Criterion[] criteria = {
        prunedp,
		division,
		// eschewTattered,
		lineage, subsumption,
		sameSourceId,
		anySourceId,
		// knowDivision,
        weakDivision,
		byRank,
        byPrimaryName,
        elimination,
    };

    boolean metBy(Taxon node, Taxon unode) {
        return this.assess(node, unode).isYes();
    }
}


class Stat {
    String tag;
    int i = 0;
    int inc(Taxon x, Answer n, Answer m) { if (i<5) System.out.format("%s %s %s %s\n", tag, x, n, m); return ++i; }
    Stat(String tag) { this.tag = tag; }
    public String toString() { return "" + i + " " + tag; }
}

