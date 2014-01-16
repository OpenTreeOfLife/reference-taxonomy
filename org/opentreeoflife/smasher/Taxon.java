package org.opentreeoflife.smasher;

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
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

public class Taxon {
	String id = null;
	Taxon parent = null;
	String name, rank = null;
	List<Taxon> children = null;
	Taxonomy taxonomy;			// For subsumption checks etc.
	String auxids = null;		// preottol id from taxonomy file, if any
	int size = -1;
	List<QualifiedId> sourceIds = null;
	Answer deprecationReason = null;
	Answer blockedp = null;
	boolean prunedp = false;

	int properFlags = 0, inheritedFlags = 0, rankAsInt = 0;

	// State during merge operation
	Taxon mapped = null;			// source node -> union node
	Taxon comapped = null;		// union node -> source node
	boolean novelp = false;     // added to union in last round?
	private String division = null;

	static boolean windyp = true;

	Taxon(Taxonomy tax) {
		this.taxonomy = tax;
		this.novelp = true;
	}

	// Clear out temporary stuff from union nodes
	void reset() {
		// this.mapped = null;    // always null anyhow
		this.comapped = null;
		this.division = null;
		this.novelp = false;
		resetBrackets();
		if (children != null)
			for (Taxon child : children)
				child.reset();
	}


	// parts = fields from row of dump file
	// uid	|	parent_uid	|	name	|	rank	|	source	|	sourceid
	//		|	sourcepid	|	uniqname	|	preottol_id	|	
	void init(String[] parts) {
		this.setName(parts[2]);
		if (parts.length >= 4) {
			this.rank = parts[3];
			if (this.rank.length() == 0 || this.rank.equals("no rank"))
				this.rank = Taxonomy.NO_RANK;
			else if (Taxonomy.ranks.get(this.rank) == null)
				System.err.println("!! Unrecognized rank: " + this.rank + " " + this.id);
		}
		// TBD: map source+sourceId when present (deprecated),
		// parse sourceInfo when present

		if (this.taxonomy.infocolumn != null) {
			if (parts.length <= this.taxonomy.infocolumn)
				System.err.println("Missing sourceinfo column: " + this.id);
			else {
				String info = parts[this.taxonomy.infocolumn];
				if (info != null && info.length() > 0)
					this.setSourceIds(info);
			}
		}

		else if (this.taxonomy.sourcecolumn != null &&
			this.taxonomy.sourceidcolumn != null) {
			List<QualifiedId> qids = new ArrayList<QualifiedId>(1);
			qids.add(new QualifiedId(parts[this.taxonomy.sourcecolumn],
									 parts[this.taxonomy.sourceidcolumn]));
		}

		if (this.taxonomy.preottolcolumn != null)
			this.auxids = parts[this.taxonomy.preottolcolumn];

	}

	static Pattern commaPattern = Pattern.compile(",");

	void setSourceIds(String info) {
		if (info.equals("null")) return;    // glitch in OTT 2.2
		String[] ids = commaPattern.split(info);
		if (ids.length > 0) {
			this.sourceIds = new ArrayList(ids.length);
			for (String qid : ids)
				this.addSourceId(new QualifiedId(qid));
		}
	}

	void setName(String name) {
		if (this.name != null) {
            if (name.equals(this.name)) return;
            List<Taxon> nodes = this.taxonomy.nameIndex.get(this.name);
            nodes.remove(name);
            if (nodes.size() == 0) {
                System.out.println("Removing name from index: " + name);
                this.taxonomy.nameIndex.remove(name);
            }
        }
		this.name = name;
		if (name == null)
			System.err.println("! Setting name to null? " + this);
		else
			this.taxonomy.addToIndex(this);
	}

	void setId(String id) {
		if (this.id == null) {
			this.id = id;
			this.taxonomy.idIndex.put(id, this);
		} else
			System.err.println("Attempt to replace id " + this.id + " with " + id);
	}

	Taxon getParent() {
		return parent;
	}

	void addChild(Taxon child) {
		if (child.taxonomy != this.taxonomy) {
			this.report("Attempt to add child in different taxonomy", child);
			Taxon.backtrace();
		} else if (child.parent != null) {
			if (this.report("Attempt to steal child !!??", child))
				Taxon.backtrace();
		} else if (child == this) {
			if (this.report("Attempt to create self-loop !!??", child))
				Taxon.backtrace();
		} else {
			child.parent = this;
			if (this.children == null)
				this.children = new ArrayList<Taxon>();
			this.children.add(child);
		}
	}

	static void backtrace() {
		try {
			throw new Exception("Backtrace");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void changeParent(Taxon newparent) {
		Taxon p = this.parent;
		this.parent = null;
		p.children.remove(this);
		if (p.children.size() == 0)
			p.children = null;
		newparent.addChild(this);
	}

	// Go upwards and cache on the way back down
	String getDivision() {
		if (this.division == null) {
			if (this.parent == null)
				this.division = null;
			else
				this.division = this.parent.getDivision();
		}
		return this.division;
	}

	void setDivision(String division) {
		if (this.division != null)
			this.report("!? changing divisions doesn't work");
		this.division = division;
	}

	// Nearest ancestor having a name that's not a prefix of ours... and isn't also a homonym
	Taxon informative() {
		Taxon up = this.parent;
		while (up != null &&
			   (this.name.startsWith(up.name) || up.taxonomy.lookup(up.name).size() > 1))
			up = up.parent;
		return up;
	}

	// Homonym discounting synonyms
	boolean isHomonym() {
		List<Taxon> alts = this.taxonomy.nameIndex.get(this.name);
		if (alts == null) {
			System.err.println("Name not indexed !? " + this.name);
			return false;
		}
		for (Taxon alt : alts)
			if (alt != this && alt.name.equals(this.name))
				return true;
		return false;
	}

	//out.println("uid\t|\tparent_uid\t|\tname\t|\trank\t|\t" +
	//			"source\t|\tsourceid\t|\tsourcepid\t|\tuniqname\t|\tpreottol_id\t|\t");

	// Note: There can be multiple sources, separated by commas.
	// However, the first one in the list is the original source.
	// The others are merely inferred to be identical.

	QualifiedId putativeSourceRef() {
		if (this.sourceIds != null)
			return this.sourceIds.get(0);
		else
			return null;
	}

	// unode is a node in the union taxonomy, possibly fresh

	void unifyWith(Taxon unode) {
		if (this.mapped == unode) return; // redundant
		if (this.mapped != null) {
			// Shouldn't happen - assigning single source taxon to two
			//	different union taxa
			if (this.report("Already assigned to node in union:", unode))
				Taxon.backtrace();
			return;
		}
		if (unode.comapped != null) {
			// Union node has already been matched to, but synonyms are OK
			this.report("Union node already mapped tog, creating synonym", unode);
		}
		this.mapped = unode;

		if (unode.name == null) unode.setName(this.name);
		if (this.rank != Taxonomy.NO_RANK)
			if (unode.rank == Taxonomy.NO_RANK || unode.rank.equals("samples"))
				unode.rank = this.rank;
		unode.comapped = this;

		if (this.comment != null) { // cf. deprecate()
			//unode.addComment(this.comment);
			this.comment = null;
		}
        unode.properFlags &= this.properFlags;
	}

	// Recursive descent over source taxonomy

	static void augmentationReport() {
		if (Taxon.windyp)
			Taxon.printStats();
	}

	// Add most of the otherwise unmapped nodes to the union taxonomy,
	// either as new names, fragmented taxa, or (occasionally)
	// new homonyms, or vertical insertions.

	// This node is already mapped, but it may get new children.

	String comment = null;	   // the reason(s) that it is what it is (or isn't)

	void addComment(String comment) {
		if (this.mapped != null)
			this.mapped.addComment(comment);
		if (this.comment == null)
			this.comment = comment;
		else if (this.comment != null)
			this.comment = this.comment + " " + comment;
	}

	// Best if node is *not* from the union... the ids don't display well

	void addComment(String comment, Taxon node) {
		this.addComment(comment + "(" + node.getQualifiedId() + ")");
	}

	void addComment(String comment, String name) {
		if (name == null)		// witness
			this.addComment(comment);
		else
			this.addComment(comment + "(" + name + ")");
	}

	void addSourceId(QualifiedId qid) {
		if (this.sourceIds == null)
			this.sourceIds = new ArrayList<QualifiedId>(1);
		if (!this.sourceIds.contains(qid))
			this.sourceIds.add(qid);
	}

	void addSource(Taxon source) {
		addSourceId(source.getQualifiedId());
		// Accumulate ...
		if (source.sourceIds != null)
			for (QualifiedId qid : source.sourceIds)
				addSourceId(qid);
	}

	QualifiedId getQualifiedId() {
		if (this.id != null)
			return new QualifiedId(this.taxonomy.getTag(), this.id);
		else {
            // What if from a Newick string?
			System.err.println("!? [getQualifiedId] Taxon has no id: " + this.name);
			return new QualifiedId("?", this.name);
		}
	}

	// Method on Node, called for every node in the source taxonomy
	Taxon augment(UnionTaxonomy union) {

		Taxon newnode = null;
        int newflags = 0;

		if (this.children == null) {
			if (this.mapped != null) {
				newnode = this.mapped;
				Taxon.markEvent("mapped/tip");
			} else if (this.deprecationReason != null &&
					   // Create homonym iff it's an unquestionably bad match
					   this.deprecationReason.value > Answer.HECK_NO) {
				union.logAndMark(Answer.no(this, null, "blocked/tip", null));
			} else {
				newnode = new Taxon(union);
				// heckYes is uninteresting
				union.logAndMark(Answer.heckYes(this, newnode, "new/tip", null));
			}
		} else {

			// The children fall into four classes
			//	A. Those that map to the "right" place (e.g. 'winner')
			//	B. Those that map to the "wrong" place (e.g. 'loser')
			//	C. Those that don't map - new additions to the union tree
			//  D. Those that get dropped for some reason ('mooted')
			// oldChildren includes both A and B
			// newChildren = C
			// The affinities of those in C might be divided between A and B...
			// thus if B is nonempty (there is a 'loser') class C is called
			// 'ambiguous'

			List<Taxon> oldChildren = new ArrayList<Taxon>();  //parent != null
			List<Taxon> newChildren = new ArrayList<Taxon>();  //parent == null
			// Recursion step
			for (Taxon child: this.children) {
				Taxon augChild = child.augment(union);
				if (augChild != null) {
					if (augChild.parent == null)
						newChildren.add(augChild);
					else
						oldChildren.add(augChild);
				}
			}

			if (this.mapped != null) {
				for (Taxon augChild : newChildren)
					// *** This is where the Protozoa/Chromista trouble arises. ***
					// *** They are imported, then set as children of 'life'. ***
					this.mapped.addChild(augChild);
				this.reportOnMapping(union, (newChildren.size() == 0));
				newnode = this.mapped;

			} else if (oldChildren.size() == 0) {
				// New children only... just copying new stuff to union
				if (newChildren.size() > 0) {
					newnode = new Taxon(union);
					for (Taxon augChild: newChildren)
						newnode.addChild(augChild);    // ????
					union.logAndMark(Answer.heckYes(this, newnode, "new/internal", null));
				} else
					union.logAndMark(Answer.no(this, null, "lose/mooted", null));
				// fall through

			} else if (this.refinementp(oldChildren, newChildren)) {

					// Move the new internal node over to union taxonomy.
					// It will end up becoming a descendent of oldParent.
					newnode = new Taxon(union);
					for (Taxon nu : newChildren) newnode.addChild(nu);
					for (Taxon old : oldChildren) old.changeParent(newnode);   // Detach!!
					// 'yes' is interesting, 'heckYes' isn't
					union.logAndMark(Answer.yes(this, null, "new/insertion", null));
					// fall through

			} else if (newChildren.size() > 0) {
				// Paraphyletic.
				// Leave the old children where they are.
				// Put the new children in a "tattered" incertae-sedis-like container.
				newnode = new Taxon(union);
				for (Taxon augChild: newChildren)
					newnode.addChild(augChild);

                newflags |= Taxonomy.TATTERED;
				union.logAndMark(Answer.yes(this, null, "new/tattered", null));
				// fall through

			} else if (false && newChildren.size() == 0) {   // && oldChildren.size() == 0 ?
				// If there are oldchildren and so on, we have an insertion opportunity (Silva??)
				if (this.deprecationReason != null &&
					this.deprecationReason.value > Answer.HECK_NO)
					union.logAndMark(Answer.no(this, null, "blocked/internal", null));
				else
					union.logAndMark(Answer.no(this, null, "lose/mooted2", null));

			} else {
				// >= 1 old children, 0 new children
				// something funny's happening here... maybe the parent should be marked incertae sedis??
				union.logAndMark(Answer.no(this, null, "lose/dispersed", null));
			}
		}

		if (newnode != null) {
			if (this.mapped == null) {

				// Report on homonymy.
				// Either this is a name not before occurring in the union,
				//	 or the corresponding node(s) in union has been rejected
				//	 as a match.
				// Do this check before the unifyWith call, for prettier diagnostics.
				List<Taxon> losers = union.lookup(this.name);
				if (losers != null)
					for (Taxon loser : losers) {
						if (loser.name.equals(this.name)) {
							if (this.getDivision() == loser.getDivision())   //double check
								union.logAndMark(Answer.no(this, loser, "new-homonym/in-division",
														   this.getDivision()));
							else
								union.logAndMark(Answer.no(this, loser, "new-homonym/out-division",
														   this.getDivision() + "=>" + loser.getDivision()));
							break;
						}
					}
                newnode.properFlags = -1;
				this.unifyWith(newnode);	   // sets name and properFlag
                newnode.properFlags |= newflags;
			} else if (this.mapped != newnode)
				System.out.println("Whazza? " + this + " ... " + newnode);
			newnode.addSource(this);
		}

		return newnode;						 // = this.mapped
	}

	// If all of the old children have the same parent,
	// AND that parent is the nearest old ancestor of all the new children,
	// then we can add the old children to the new taxon,
	// which (if all goes well) will get inserted back into the union tree
	// under the old parent.

	// This is a cheat because some of the old children's siblings
	// might be more correctly classified as belonging to the new
	// taxon, rather than being siblings.  So we might want to
	// further qualify this.  (Rule is essential for mapping NCBI
	// onto Silva.)

	// Caution: See https://github.com/OpenTreeOfLife/opentree/issues/73 ...
	// family as child of subfamily is confusing.
	// ranks.get(node1.rank) <= ranks.get(node2.rank) ....

	boolean refinementp(List<Taxon> oldChildren, List<Taxon> newChildren) {
		Taxon oldParent = null;
		for (Taxon old : oldChildren) {
			// old has a nonnull parent, by contruction
			if (oldParent == null) oldParent = old.parent;
			else if (old.parent != oldParent) return false;
		}
		for (Taxon nu : newChildren) {
			// alternatively, could do some kind of MRCA
			Taxon anc = this.parent;
			while (anc != null && anc.mapped == null)
				anc = anc.parent;
			if (anc == null)     // ran past root of tree
				return false;
			else if (anc.mapped != oldParent)
				return false;    	// Paraphyletic
		}
		return true;
	}

	// pulled out of previous method to make it easier to read
	void reportOnMapping(UnionTaxonomy union, boolean newp) {
		Taxon newnode = null;

		// --- Classify & report on what has just happened ---
		// TBD: Maybe decorate the newChildren with info about the match?...
		Taxon loser = this.antiwitness(this.mapped);
		Taxon winner = this.witness(this.mapped);
		if (winner != null) {
			// Evidence of sameness [maybe parent agreement, or not]
			if (loser == null)
				// No evidence of differentness
				// cf. "is-subsumed-by" - compatible extension
				// (35,351)
				// heckYes = uninteresting
				union.logAndMark(Answer.heckYes(this, newnode, "mapped/coherent", null));
			else {
				// Evidence of differentness
				// cf. "overlaps" ("type 1")
				// (1,482)
				union.logAndMark(Answer.yes(this, newnode, "mapped/incoherent", winner.name));
				//if (newnode != null)   // This seems wrong somehow
				//	newnode.mode = "incoherent"; // or "paraphyletic" ?
			}
		} else {
			// No evidence of sameness [except maybe parent agreement]
			if (loser == null) {
				if (newp)
					Taxon.markEvent("mapped/internal"); // Exact topology match
				else
					// No evidence of differentness
					// cf. "by-elimination" - could actually be a homonym
					// (7,093 occurrences, as of 2013-04-24, of which 571 'essential')
					// (all but 94 of which have shared parents...)
					union.logAndMark(Answer.noinfo(this, newnode, "mapped/neutral", null));
			} else
				// Evidence of differentness
				// This case is rare, because it's ruled out in
				// Criterion.subsumption, cf. "incompatible-with" ("type 2")
				// (52 times, as of 2013-04-24, + 13 unmapped)
				// Still arises when agreement on parent
				union.logAndMark(Answer.no(this, newnode, "mapped/incompatible", null));
		}
		// --- End classify & report ---
	}

	// Mainly for debugging

	public String toString() {
		return this.toString(null);
	}

	String toString(Taxon other) {
		String twinkie = "";
		if (this.mapped != null || this.comapped != null)
			twinkie = "*";
		else if (other != null &&
				 other.taxonomy != this.taxonomy &&
				 other.taxonomy.lookup(this.name) != null)
			twinkie = "+";		// Name in common

		String ids;
		QualifiedId ref = this.putativeSourceRef();
		if (ref != null)		// this is from idsource
			ids = this.id + "=" + ref;
		else {
			ids = this.getSourceIdsString();
			if (ids.length() == 0)
				ids = this.getQualifiedId().toString();
			else				// this is from union
				ids = "{" + ids + "}";
		}

		return 
			"(" + ids +
			(this.children == null ? "." : "") +
			" " + this.name +
			twinkie +				// tbd: indicate division top with "#" 
			(this.comment != null ? (" " + this.comment) : "") +
			")";
	}

	// Returns a string of the form prefix:id,prefix:id,...
	// Generally called on a union taxonomy node

	String getSourceIdsString() {
		String answer = null;
		List<QualifiedId> qids = this.sourceIds;
		if (qids != null) {
			for (QualifiedId qid : qids) {
				if (answer == null)
					answer = qid.toString();
				else
					answer = answer + "," + qid.toString();
			}
		}
		// else answer = getQualifiedId().toString() ... ?
		if (answer != null)
			return answer;
		else
			// callers expect non-null
			return "";
	}

	// Event monitoring

	static Map<String, Long> eventStats = new HashMap<String, Long>();
	static List<String> eventStatNames = new ArrayList<String>();

	static boolean startReport(String note) {
		Long probe = eventStats.get(note);
		long count;
		if (probe == null) {
			eventStatNames.add(note);
			count = 0;
		} else
			count = probe;
		eventStats.put(note, count+(long)1);
		if (count <= 10) {
			return true;
		} else
			return false;
	}

	static void printStats() {
		for (String note : eventStatNames) { // In order added
			System.out.println("| " + note + ": " + eventStats.get(note));
		}
		// Reset...
		Taxon.resetStats();
	}

	static void resetStats() {
		eventStats = new HashMap<String, Long>();
		eventStatNames = new ArrayList();
	}

	// convenience variants

	static boolean markEvent(String note) {
		return startReport(note);
	}

	boolean report(String note, Taxon othernode) {
		return this.report(note, othernode, null);
	}

	boolean report(String note, Taxon othernode, String witness) {
		if (startReport(note)) {
			System.out.println("| " + note);
			this.report1("", othernode);
			if (othernode != null)
				othernode.report1("", this);
			if (witness != null)
				System.out.println("| " + witness);
			System.out.println();
			return true;
		}
		return false;
	}

	boolean report(String note, List<Taxon> others) {
		if (startReport(note)) {
			System.out.println("| " + note);
			this.report1("", null);
			for (Taxon othernode : others)
				othernode.report1("", others.get(0));
			System.out.println();
			return true;
		}
		return false;
	}

	void report(String tag) {
		if (startReport(tag))
			report1(tag, null);
	}

	void report1(String tag, Taxon other) {
		String output = "";
		int i = 0;
		boolean seenmapped = false;
		for (Taxon n = this; n != null; n = n.parent) {
			if (++i < 4 || (!seenmapped && (n.mapped != null || n.comapped != null))) {
				if (n.mapped != null || n.comapped != null)
					seenmapped = true;
				output += " " + n.toString(other);
			}
			else if (i == 4)
				output += " ...";
		}
		System.out.println(" " + tag + " " + output);
	}

	// N.b. this is in source taxonomy, match is in union
	boolean separationReport(String note, Taxon match) {
		if (startReport(note)) {
			System.out.println(note);

			Taxon nearestMapped = this;			 // in source taxonomy
			Taxon nearestMappedMapped = this;	 // in union taxonomy

			if (this.taxonomy != match.taxonomy) {
				if (!(this.taxonomy instanceof SourceTaxonomy) ||
					!(match.taxonomy instanceof UnionTaxonomy)) {
					this.report("Type dysfunction", match);
					return true;
				}
				// Need to cross from source taxonomy over into the union one
				while (nearestMapped != null && nearestMapped.mapped == null)
					nearestMapped = nearestMapped.parent;
				if (nearestMapped == null) {
					this.report("No matches, can't compute mrca", match);
					return true;
				}
				nearestMappedMapped = nearestMapped.mapped;
				if (nearestMappedMapped.taxonomy != match.taxonomy) {
					this.report("Not in matched taxonomies", match);
					return true;
				}
			}

			Taxon mrca = match.mrca(nearestMappedMapped); // in union tree
			if (mrca == null) {
				this.report("In unconnected trees !?", match);
				return true;
			}

			// Number of steps in source tree before crossing over
			int d0 = this.measureDepth() - nearestMapped.measureDepth();

			// Steps from source node up to mrca
			int d1 = d0 + (nearestMappedMapped.measureDepth() - mrca.measureDepth());
			int d2 = match.measureDepth() - mrca.measureDepth();
			int d3 = (d2 > d1 ? d2 : d1);
			String spaces = "															 ";
			Taxon n1 = this;
			for (int i = d3 - d1; i <= d3; ++i) {
				if (n1 == nearestMapped)
					n1 = nearestMappedMapped;
				System.out.println("  " + spaces.substring(0, i) + n1.toString(match));
				n1 = n1.parent;
			}
			Taxon n2 = match;
			for (int i = d3 - d2; i <= d3; ++i) {
				System.out.println("  " + spaces.substring(0, i) + n2.toString(this));
				n2 = n2.parent;
			}
			if (n1 != n2)
				System.err.println("Bug: " + n1 + " != " + n2);
			return true;
		}
		return false;
	}

	String elaboratedString(Taxonomy tax) {
		if (this.mapped != null)
			return this.toString();
		else {
			boolean h = (tax.unique(this.name) != null);
			return this.toString() +  (h ? "?" : "");
		}
	}

	// Number of child-less nodes at and below this node.

	int size() {
		if (size < 1) {
			size = 1;
			if (children != null)
				for (Taxon child: children)
					size += child.size();
		}
		return size;
	}

	// Brute force count of nodes (more reliable than size() in presence of change)
	int count() {
		int count = 1;
		if (this.children != null)
			for (Taxon child : this.children)
				count += child.count();
		return count;
	}

	static final int NOT_SET = -7; // for source nodes

	int seq = NOT_SET;		// Self
	int start = NOT_SET;	// First taxon included not including self
	int end = NOT_SET;		// Next taxon *not* included

	void resetBrackets() {			  // for union nodes
		this.seq = NOT_SET;			  // Self
		this.start = NOT_SET;	// First taxon included not including self
		this.end = NOT_SET;					   // Next taxon *not* included
	}

	// Applied to a union node
	void assignBrackets() {
		// Only consider names in common ???
		this.seq = this.taxonomy.nextSequenceNumber++;
		this.start = this.taxonomy.nextSequenceNumber;
		if (this.children != null)
			for (Taxon child : this.children)
				child.assignBrackets();
		this.end = this.taxonomy.nextSequenceNumber;
	}

	// Applied to a source node
	void getBracket(Taxonomy union) {
		if (this.end == NOT_SET) {
			Taxon unode = union.unique(this.name);
			if (unode != null)
				this.seq = unode.seq; // Else leave seq as NOT_SET
			if (this.children != null) {
				int start = Integer.MAX_VALUE;
				int end = -1;
				for (Taxon child : this.children) {
					child.getBracket(union);
					if (child.start < start) start = child.start;
					if (child.end > end) end = child.end;
					if (child.seq != NOT_SET) {
						if (child.seq < start) start = child.seq;
						if (child.seq > end) end = child.seq+1;
					}
				}
				this.start = start;
				this.end = end;
			}
		}
	}

	// Cheaper test, without seeking a witness
	boolean isNotSubsumedBy(Taxon unode) {
		this.getBracket(unode.taxonomy);
		return this.start < unode.start || this.end > unode.end; // spills out?
	}

	// Look for a member of this source taxon that's not a member of the union taxon,
	// but is a member of some other union taxon.
	Taxon antiwitness(Taxon unode) {
		getBracket(unode.taxonomy);
		if (this.start >= unode.start && this.end <= unode.end)
			return null;
		else if (this.children != null) { // it *will* be nonnull actually
			for (Taxon child : this.children)
				if (child.seq != NOT_SET && (child.seq < unode.start || child.seq >= unode.end))
					return child;
				else {
					Taxon a = child.antiwitness(unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

	// Look for a member of the source taxon that's also a member of the union taxon.
	Taxon witness(Taxon unode) { // assumes is subsumed by unode
		getBracket(unode.taxonomy);
		if (this.start >= unode.end || this.end <= unode.start) // Nonoverlapping => lose
			return null;
		else if (this.children != null) { // it *will* be nonnull actually
			for (Taxon child : this.children)
				if (child.seq != NOT_SET && (child.seq >= unode.start && child.seq < unode.end))
					return child;
				else {
					Taxon a = child.witness(unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

	// Find a near-ancestor (parent, grandparent, etc) node that's in
	// common with the other taxonomy
	Taxon scan(Taxonomy other) {
		Taxon up = this.parent;

		// Cf. informative() method
		// Without this we get ambiguities when the taxon is a species
		while (up != null && up.name != null && this.name.startsWith(up.name))
			up = up.parent;

		while (up != null && up.name != null && other.lookup(up.name) == null)
			up = up.parent;

		if (up != null && up.name == null) {
			System.err.println("!? Null name: " + up + " ancestor of " + this);
			Taxon u = this;
			while (u != null) {
				System.err.println(u);
				u = u.parent;
			}
		}
		return up;
	}

	int depth = -1;
	int getDepth() {
		if (this.depth < 0) {
			if (this.parent == null)
				this.depth = 0;
			else
				this.depth = this.parent.getDepth() + 1;
		}
		return this.depth;
	}
	int measureDepth() {		// Robust in presence of insertions
		if (this.parent == null)
			this.depth = 0;
		else
			this.depth = this.parent.measureDepth() + 1;
		return this.depth;
	}

	Taxon mrca(Taxon b) {
		if (b == null) return null; // Shouldn't happen, but...
		else {
			Taxon a = this;
			while (a.getDepth() > b.getDepth())
				a = a.parent;
			while (b.getDepth() > a.getDepth())
				b = b.parent;
			while (a != b) {
				a = a.parent;
				b = b.parent;
			}
			return a;
		}
	}

	void appendNewickTo(StringBuffer buf) {
		if (this.children != null) {
			buf.append("(");
			Collections.sort(this.children, compareNodes);
			Taxon last = children.get(this.children.size()-1);
			for (Taxon child : children) {
				child.appendNewickTo(buf);
				if (child != last)
					buf.append(",");
			}
			buf.append(")");
		}
		if (this.name != null)
			buf.append(name.replace('(','[').replace(')',']').replace(':','?'));
	}

	static Comparator<Taxon> compareNodes = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.name.compareTo(y.name);
		}
	};

	// Delete this node and all of its descendents.
	public void prune() {
		if (this.children != null) {
			for (Taxon child : new ArrayList<Taxon>(children))
				child.prune();
			this.children = null;
		}
		if (this.parent != null) {
			this.parent.children.remove(this);
			this.parent.properFlags |= Taxonomy.EDITED;
			this.parent = null;
		}
		List nodes = this.taxonomy.nameIndex.get(this.name);
		nodes.remove(this);
		if (nodes.size() == 0)
			this.taxonomy.nameIndex.remove(this.name);
		if (this.id != null)
			this.taxonomy.idIndex.remove(this.id);
		this.prunedp = true;  // kludge for indexes
	}

	String uniqueName() {
		List<Taxon> nodes = this.taxonomy.lookup(this.name);
		if (nodes == null) {
			System.err.format("!? Confused: %s in %s\n", this.name,
							  this.parent == null ? "(roots)" : this.parent.name);
			return "?";
		}
		for (Taxon other : nodes)
			if (other != this && other.name.equals(this.name)) {
				Taxon i = this.informative();
				if ((i != other.informative() &&
					 i != null &&
					 !this.name.endsWith(" sp."))) {
					String urank = "";
					if (this.rank != null) urank = this.rank + " ";
					String irank = "";
					if (i.rank != null) irank = i.rank + " ";
					return this.name + " (" + urank + "in " + irank + i.name + ")";
				} else
					return this.name + " (" + this.getSourceIdsString() + ")";
			}
		return "";
	}

	static Comparator<Taxon> compareNodesBySize = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.count() - y.count();
		}
	};

    // Duplicate single node

    Taxon dup(Taxonomy tax) {
		Taxon dup = new Taxon(tax);
		dup.setName(this.name);
		dup.setId(this.id);
		dup.rank = this.rank;
		dup.sourceIds = this.sourceIds;
        return dup;
    }

	// Copy subtree

    Taxon select(Taxonomy tax) {
		Taxon sam = this.dup(tax);
		this.mapped = sam;
		if (this.children != null)
			for (Taxon child : this.children) {
				Taxon c = child.select(tax);
				sam.addChild(c);
			}
		return sam;
	}

	// The nodes of the resulting tree are a subset of size k of the
	// nodes from the input tree, sampled proportionally.

    Taxon sample(int k, Taxonomy tax) {
		if (k <= 0) return null;
		Taxon sam = this.dup(tax);

		// Assume k <= n.
		// We want to select k descendents out of the n that are available.

		int n = this.count() ;
	
		// n1 ranges from 0 up to n-1    (the 1 is for the taxon itself)
		// k1 ranges from 0 up to k-1    (the 1 is for sam)
		// k1 : n1 :: k2 : n2 :: k : n
		// k2 = (k*n) / n2
		int n1 = 1;
		int k1 = 1;

		if (this.children != null) {
			java.util.Collections.sort(this.children, compareNodesBySize);
			for (Taxon child : this.children) {

				if (k1 >= k) break;    // redundant?

				int n2 = n1 + child.count();

				// Number of children we want to have after sampling
				// From k2 : n2 :: k : n
				int k2 = (n2 == n ? k : (k * n2) / n);
				if (false)  //this.name.contains("cellular life")
					System.out.println("? " + 
									   k1 + " : " + k2 + " : " + k + " :: " +
									   n1 + " : " + n2 + " : " + n + " " + child.name);
				int dk = k2 - k1;

				// Number of children to request
				Taxon c = child.sample(dk, tax);
				if (c != null) {
					sam.addChild(c);
					k1 += c.count();
				}
				n1 = n2;

			}
		}

		return sam;
	}

    // ----- Methods intended for use in jython scripts -----

    // Patch system commands are add, move, synonym, prune, fold, flag

    // tax.add(newTaxon("Bos bos", "species", "data:,foo"))

    public void add(Taxon newchild) {
        if (newchild == null)
            return;             // Error already reported.
        else if (this.children != null && this.children.contains(newchild))
            System.err.format("%s is already a daughter of %s\n", newchild, this);
        else if (newchild.parent != null)
            System.err.format("%s can't be added because it is already in the tree\n", newchild, this);
        else {
            newchild.properFlags |= Taxonomy.EDITED;
            this.addChild(newchild);
        }
    }

    public void steal(Taxon newchild) {
        if (newchild == null)
            return;             // Error already reported.
        else if (this.children != null && this.children.contains(newchild))
            System.err.format("%s is already a daughter of %s\n", newchild, this);
        else {
            newchild.properFlags |= Taxonomy.EDITED;
            newchild.changeParent(this);
        }
    }

    public void synonym(String name) {
        this.taxonomy.addSynonym(name, this);
    }

    public void rename(String name) {
        String oldname = this.name;
        this.setName(name);
        this.taxonomy.addSynonym(oldname, this);  // awkward
    }

    // prune() is elsewhere

    public void elide() {
        if (this.children != null)
            System.err.format("%s has no children\n", this);
        else if (this.parent == null)
            System.err.format("%s is a root\n", this);
        else {
            for (Taxon child : new ArrayList<Taxon>(this.children))
                child.changeParent(this.parent);
            this.prune();
        }
    }

    // Move all of B's children to A, and make B a synonym of A

    public void absorb(Taxon other) {
        if (other.children != null)
            for (Taxon child: other.children)
                child.changeParent(this.parent);
        this.synonym(other.name);   // Not sure this is a good idea
        other.prune();
    }

    public void forceVisible() {
        this.properFlags |= Taxonomy.FORCED_VISIBLE;
    }

    public void incertaeSedis() {
        this.properFlags |= Taxonomy.INCERTAE_SEDIS;
    }

    // add a tree to the forest?

}
