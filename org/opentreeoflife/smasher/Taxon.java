package org.opentreeoflife.smasher;

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
import java.io.File;

public class Taxon {
	public String id = null;
	public Taxon parent = null;
	public String name, rank = null;
	public List<Taxon> children = null;
	public List<QualifiedId> sourceIds = null;
	public Taxonomy taxonomy;			// For subsumption checks etc.
	int count = -1;             // cache of # nodes at or below here
	int depth = -1;             // cache of distance from root
	boolean prunedp = false;    // for lazy removal from nameIndex

	int properFlags = 0, inheritedFlags = 0, rankAsInt = 0;

	Taxon division = null;

	// State during alignment
	public Taxon mapped = null;	// source node -> union node
	Taxon comapped = null;		// union node -> example source node
	Answer answer = null;  // source nodes only
    Taxon lub = null;                 // union node that is the lub of node's children

	// Cf. AlignmentByName.assignBrackets
	int seq = 0;		// Self
	int start = 0;	// First taxon included not including self
	int end = 0;		// Next taxon *not* included


	static boolean windyp = true;

	Taxon(Taxonomy tax) {
		this.taxonomy = tax;
	}

    boolean isRoot() {
        return this.taxonomy.hasRoot(this);
    }

    boolean isDetached() {
        return this.parent == null /* && !this.isRoot() */;
    }

    boolean isPlaced() {
        return (this.properFlags & Taxonomy.INCERTAE_SEDIS_ANY) == 0;
    }

    public Taxon mapped() {
        if (this.mapped != null) return this.mapped;  // New nodes, etc.
        if (this.answer == null) return null;
        else if (this.answer.isYes()) return this.answer.target;
        else return null;
    }

	// Clear out temporary stuff from union nodes
	void reset() {
		this.comapped = null;
		if (children != null)
			for (Taxon child : children)
				child.reset();
	}

	static Pattern commaPattern = Pattern.compile(",");

	void setSourceIds(String info) {
		if (info.equals("null")) return;	// glitch in OTT 2.2
		String[] ids = commaPattern.split(info);
		if (ids.length > 0) {
			this.sourceIds = new ArrayList(ids.length);
			for (String qid : ids)
				this.addSourceId(new QualifiedId(qid));
		}
	}

	void setName(String name) {
		if (this.name != null) {
			if (name.equals(this.name))
				return;
			List<Taxon> nodes = this.taxonomy.nameIndex.get(this.name);
			nodes.remove(this);
			if (nodes.size() == 0) {
				//System.out.println("Removing name from index: " + this.name);
				this.taxonomy.nameIndex.remove(this.name);
			}
		}
		this.name = name;
		if (name == null)
			System.err.println("! Setting name to null? " + this);
		else
			this.taxonomy.addToIndex(this);
	}

	public void setId(String id) {
		if (this.id == null) {
            Taxon existing = this.taxonomy.idIndex.get(id);
			if (existing != null && !existing.prunedp) {
                System.err.format("** Id collision: %s wants id of %s\n", this, existing);
                backtrace();
            } else {
                this.id = id;
                this.taxonomy.idIndex.put(id, this);
            }
		} else if (!this.id.equals(id))
			System.err.println("** Attempt to replace id " + this.id + " with " + id);
	}

	public Taxon getParent() {
		return parent;
	}

	static void backtrace() {
		try {
			throw new Exception("Backtrace");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    // This is for detached nodes (*not* in roots list)

	void addChild(Taxon child) {
		if (child.taxonomy != this.taxonomy) {
			this.report("Attempt to add child that belongs to a different taxonomy", child);
			Taxon.backtrace();
		} else if (!child.isDetached()) {
			if (this.report("Attempt to steal child !!??", child))
				Taxon.backtrace();
		} else if (child == this) {
			if (this.report("Attempt to create self-loop !!??", child))
				Taxon.backtrace();
        } else if (child.noMrca()) {
			this.report("Attempt to take on the forest !!??", this);
            Taxon.backtrace();
        } else if (this.descendsFrom(child)) {
            // you'd think the descendsFrom check would slow things
            // down noticeably, but it doesn't
			this.report("Attempt to create a cycle !!??", child);
            this.showLineage(child.parent);
            System.out.format("%s", child);
            Taxon.backtrace();
        } else {
			child.parent = this;
			if (this.children == null)
				this.children = new ArrayList<Taxon>();
            else if (this.children.contains(child))
                System.err.format("** Adding child %s redundantly to %s\n", child, this);
			this.children.add(child);
			this.resetCount();	//force recalculation
		}
	}

    static int stealcount = 0;

	void addChild(Taxon child, int flags) {
        this.addChild(child);
        child.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY;
        this.addFlag(flags);
    }

    // Removes it from the tree - undoes addChild

	public void detach() {
		Taxon p = this.parent;
        if (p == null) return;  // already detached
        this.parent = null;
        // Think about this
        if (p.children.remove(this)) {
            if (p.children.size() == 0)
                p.children = null;
        } else
            System.err.format("** Detach didn't remove %s from children list\n", this);
        p.resetCount();
	}

    // Detach followed by addChild

	public void changeParent(Taxon newparent) {
        this.detach();
        newparent.addChild(this);
    }
    
	public void changeParent(Taxon newparent, int flags) {
        if (flags == 0
            && !this.isPlaced()
            && ((this.name.hashCode() % 100) == 17))
            System.err.format("| Placing previously unplaced %s in %s\n", this, newparent);
        changeParent(newparent);
        this.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY;
        this.addFlag(flags);
	}

    public void addFlag(int flags) {
        if (flags > 0
            && this.name != null
            && (this.name.equals("Brassaiopsis shweliensis")
                || this.name.equals("eudicotyledons"))) {
            System.out.format("** Setting flags on %s in %s\n", this, this.parent);
			Flag.printFlags(flags, 0, System.out);
            System.out.format("\n");
			Flag.printFlags(this.properFlags, this.inheritedFlags, System.out);
            System.out.format("\n");
            this.showLineage(this.taxonomy.forest);  // want to know the source
            Taxon.backtrace();
        }
        this.properFlags |= flags;
    }

	// Go upwards and cache on the way back down
    // NOTE: now returns forest instead of null
	public Taxon getDivision() {
		if (this.division == null) {
            Taxon div;
			if (this.mapped != null)
				div = this.mapped.getDivision();
			else if (this.parent != null)
				div = this.parent.getDivision();
            else
                div = this;     // forest
            this.division = div; // cache it
		}
		return this.division;
	}

	public String divisionName() {
		Taxon d = this.getDivision();
		return (d.noMrca() ? "(no division)" : d.name);
	}

	void setDivision(Taxon division) {
		if (this.division != null && this.division != division)
            // Could do a tree walk setting division to null...
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

	// unode is a preexisting node in the union taxonomy.

    // Is unification really the right relationship? ... I don't think
    // so, it's already a many-to-one mapping, shouldn't be called
    // unification.

	void alignWith(Taxon unode, String reason) {
        try {
            alignWith(unode, Answer.yes(this, unode, reason, null));
        } catch (Exception e) {
            System.err.format("Exception in alignWith\n");
            e.printStackTrace();
        }
    }

    // Set the 'mapped' property of this node, carefully
	void alignWith(Taxon unode, Answer answer) {
		if (this.mapped == unode) return; // redundant
        if (!(unode.taxonomy instanceof UnionTaxonomy)) {
            System.out.format("** Alignment target %s is not in a union taxonomy\n", this);
            Taxon.backtrace();
        } else if (this.taxonomy instanceof UnionTaxonomy) {
            System.out.format("** Alignment source %s is not in a source taxonomy\n", unode);
            Taxon.backtrace();
        } else if (this.noMrca() != unode.noMrca()) {
            System.out.format("** attempt to unify forest %s with non-forest %s\n",
                              this, unode);
            Taxon.backtrace();
        } else if (this.mapped != null) {
			// Shouldn't happen - assigning a single source taxon to two
			//	different union taxa
			if (this.report("Already assigned to node in union:", unode))
				Taxon.backtrace();
		} else {
            this.mapped = unode;
            this.answer = answer;
            if (unode.comapped != null) {
                // Union node has already been matched to, but synonyms are OK
                if (unode.comapped != this)
                    Taxon.markEvent("lumped");
                // System.out.format("| Lumping %s and %s -> %s\n", unode.comapped, this, unode);
            } else
                unode.comapped = this;
        }
    }

    // This is used when the union node is not new

    void transferProperties() {
        Taxon unode = this.mapped;
        if (unode == null) return;

        if (unode.name == null)
            unode.setName(this.name);

		if ((unode.rank == Taxonomy.NO_RANK || unode.rank.equals("samples"))
            && (this.rank != Taxonomy.NO_RANK))
            unode.rank = this.rank;

		// Annotations are combined using |
		unode.properFlags |=
			(this.properFlags &
			 (Taxonomy.FORCED_VISIBLE | 
			  Taxonomy.EDITED |
			  Taxonomy.EXTINCT));

        // No change to hidden or incertae sedis flags.  Union node
        // has precedence.

        if (this.sourceIds != null)
            for (QualifiedId id : this.sourceIds)
                unode.addSourceId(id);

        unode.addSource(this);
	}

        

	// Duplicate single source node yielding a source or union node

	public Taxon dup(Taxonomy target, String reason) {
		Taxon dup = new Taxon(target);
        if (target instanceof SourceTaxonomy)
            dup.setId(this.id); // kludge for select

        // Compare this with transferProperties(dup)
		dup.setName(this.name);
		dup.rank = this.rank;

        // Retain placement flags, since the usual case is that we're
        // going to attach this in a pretty similar place
		dup.properFlags = this.properFlags;

        if (this.sourceIds != null)
            // Unusual
            dup.sourceIds = new ArrayList<QualifiedId>(this.sourceIds);

        // This might be the place to report on homonym creation

		return dup;
	}

	// Add most of the otherwise unmapped nodes to the union taxonomy,
	// either as new names, fragmented taxa, or (occasionally)
	// new homonyms, or vertical insertions.

	void addSourceId(QualifiedId qid) {
		if (this.sourceIds == null)
			this.sourceIds = new ArrayList<QualifiedId>(1);
		if (!this.sourceIds.contains(qid))
			this.sourceIds.add(qid);
	}

	void addSource(Taxon source) {
		if (source.id != null &&
			!source.taxonomy.getTag().equals("skel")) //KLUDGE!!!
			addSourceId(source.getQualifiedId());
		// Accumulate ...
		if (source.sourceIds != null)
			for (QualifiedId qid : source.sourceIds)
				addSourceId(qid);
	}

	QualifiedId getQualifiedId() {
		if (this.id != null)
			return new QualifiedId(this.taxonomy.getTag(), this.id);
        else if (this.name != null) {
			System.err.println("| [getQualifiedId] Taxon has no id, using name: " + this.name);
			return new QualifiedId(this.taxonomy.getTag(), this.name);
        } else if (this.noMrca()) {
            // Shouldn't happen
			System.err.println("| [getQualifiedId] Forest");
			return new QualifiedId(this.taxonomy.getTag(), "<forest>");
        } else if (this.parent == null) {
            // Shouldn't happen
			System.err.println("| [getQualifiedId] Detached");
            return new QualifiedId(this.taxonomy.getTag(), "<detached>");
        } else {
			// What if from a Newick string?
			System.err.println("| [getQualifiedId] Nondescript");
            return new QualifiedId(this.taxonomy.getTag(), "<nondescript>");
        }
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
			ids = (this.id == null ? "" : this.id) + "=" + ref;
		else {
			ids = this.getSourceIdsString();
			if (ids.length() == 0) {
                if (this.id == null)
                    ids = "-";
                else
                    ids = this.getQualifiedId().toString();
			} else				// this is from union
				ids = "{" + ids + "}";
		}

		return 
			"(" + ids +
			(this.children == null ? "." : "") +
			" " + this.name +
			twinkie +				// tbd: indicate division top with "#" 
            (this.isDirectlyHidden() ? "?" : "") +
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

	// Recursive descent over source taxonomy

	static void augmentationReport() {
		if (Taxon.windyp)
			Taxon.printStats();
	}
	static void printStats() {
        Collections.sort(eventStatNames);
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

			Taxon mrca = match.carefulMrca(nearestMappedMapped); // in union tree
			if (mrca == null || mrca.noMrca()) {
				this.report("In unconnected trees !?", match);
				return true;
			}

			// Number of steps in source tree before crossing over
			int d0 = this.measureDepth() - nearestMapped.measureDepth();

			// Steps from source node up to mrca
            int dm = mrca.measureDepth();
			int d1 = d0 + (nearestMappedMapped.measureDepth() - dm);
			int d2 = match.measureDepth() - dm;
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

	public int count() {
		if (this.count < 1) {
			this.count = 1;
			if (children != null)
				for (Taxon child: children)
					this.count += child.count();
		}
		return this.count;
	}

	void resetCount() {
		for (Taxon n = this; n != null && n.count > 0; n = n.parent)
			n.count = -1;
	}

	// Number of tips at or below this node.

	public int tipCount() {
		if (children == null)
			return 1;
		else {
			int count = 0;
			for (Taxon child: children)
				count += child.tipCount();
			return count;
		}
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

	// Use getDepth() only after the tree is in its final form
	int getDepth() {
		if (this.depth < 0) {
			if (this.parent == null)
				this.depth = 0;
			else
				this.depth = this.parent.getDepth() + 1;
		}
		return this.depth;
	}

    void resetDepths() {
        this.depth = -1;
        if (this.children != null)
            for (Taxon child : this.children)
                child.resetDepths();
    }

	// Does not use cache - depths may change during merge
	int measureDepth() {		// Robust in presence of insertions
		if (this.parent == null)
            return 0;
		else
			return this.parent.measureDepth() + 1;
	}

    boolean noMrca() {
        return this == this.taxonomy.forest; // forest
    }

    Taxon carefulMrca(Taxon other) {
        if (other == null) return null; // Shouldn't happen, but...
        return this.mrca(other, this.measureDepth(), other.measureDepth());
    }

    Taxon mrca(Taxon other) {
        if (other == null) return null; // Shouldn't happen, but...
        return this.mrca(other, this.getDepth(), other.getDepth());
    }

    Taxon mrca(Taxon other, int adepth, int bdepth) {
        if (this.taxonomy != other.taxonomy)
            throw new RuntimeException(String.format("Mrca across taxonomies: %s %s", this, other));
        Taxon a = this, b = other;
        while (adepth > bdepth) {
            a = a.parent;
            --adepth;
        }
        while (bdepth > adepth) {
            b = b.parent;
            --bdepth;
        }
        while (a != b) {
            if (a == null || b == null) {
                System.out.format("** shouldn't happen: %s %s?=%s / %s %s?=%s\n",
                                  this, this.getDepth(), this.measureDepth(), other, other.getDepth(), other.measureDepth());
                return this.taxonomy.forest;
            }
            a = a.parent;
            b = b.parent;
        }
        return a;
    }
 
	// Compute sibling taxa {a', b'} such that a' includes a and b' includes b.
    // Returns null if one includes the other.
    // Could return the 'forest' pseudo-taxon.
    // Except, if no way to get to union from source, return null.

	public Taxon[] divergence(Taxon other) {
        Taxon a = this.bridge(), b = other.bridge();
        if (a == null || b == null) return null;
		if (a.taxonomy != b.taxonomy)
            throw new RuntimeException(String.format("Can't bridge different union taxonomies %s %s", this, other));
		int da = a.measureDepth();
		int db = b.measureDepth();
		while (db > da) {
			b = b.parent;
			--db;
		}
        if (a == b) return null;
		while (da > db) {
			a = a.parent;
			--da;
		}
        if (a == b) return null;
		while (a.parent != b.parent) {
			a = a.parent;
			b = b.parent;
		}
        Taxon[] answer = {a, b};
        return answer;
	}

    // Map source taxon to nearest available union taxon
    Taxon bridge() {
        if (this.taxonomy instanceof UnionTaxonomy)
            return this;
        else {
            Taxon a = this;
            while (a.mapped == null) {
                if (a.parent == null)
                    // No bridge!  Shouldn't happen
                    throw new RuntimeException(String.format("No bridge from %s", this));
                a = a.parent;
            }
            if (!(a.mapped.taxonomy instanceof UnionTaxonomy))
                throw new RuntimeException(String.format("Taxon %s mapped to non-union %s", a, a.mapped));
            return a.mapped;
        }
    }

	// For cycle detection
	public boolean descendsFrom(Taxon b) {
		for (Taxon a = this; a != null; a = a.parent)
			if (a == b)
				return true;
		return false;
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
			buf.append(newickName(this.name, this.taxonomy.getTag(), this.id));
	}

	static Comparator<Taxon> compareNodes = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.name.compareTo(y.name);
		}
	};

	// Delete this node and all of its descendants.
	public boolean prune() {
        this.detach();
        this.addFlag(Taxonomy.EDITED);
        this.setRemoved();
        return true;
    }

	// Delete all of this node's descendants.
	public void trim() {
		if (this.children != null)
			for (Taxon child : new ArrayList<Taxon>(children))
				child.prune();
	}

    // Recursively set prunedp flag and remove from indexes
	public boolean setRemoved() {
        this.mapped = null;
        this.answer = Answer.no(this, null, "pruned", null);
		if (this.children != null)
			for (Taxon child : new ArrayList<Taxon>(children))
				child.setRemoved();

		List<Taxon> nodes = this.taxonomy.nameIndex.get(this.name);
		nodes.remove(this);
		if (nodes.size() == 0)
			this.taxonomy.nameIndex.remove(this.name);
		if (this.id != null)
			this.taxonomy.idIndex.remove(this.id);
		this.prunedp = true;  // kludge ...

        return true;
	}

	// TBD: Currently the code selects the smallest containing disambiguating taxon.
	// In future maybe it should select the *largest* disambiguating taxon.

	public String uniqueName() {
		List<Taxon> nodes = this.taxonomy.lookup(this.name);
		if (nodes == null)
            return this.getQualifiedId().toString();
		boolean homonymp = false;

		// Ancestor that distinguishes this taxon from all others with same name
		Taxon unique = null;
		for (Taxon other : nodes)
			if (other != this) {  //  && other.name.equals(this.name)
				homonymp = true;
				if (unique != this) {
					Taxon[] div = this.divergence(other);
					if (div != null) {
						if (unique == null)
							unique = div[0];
						else if (div[0].descendsFrom(unique))
							unique = div[0];
					}
				}
			}
		if (homonymp) {
			String thisrank = ((this.rank == null) ? "" : (this.rank + " "));
			if (unique == null || unique == this) {
				if (this.sourceIds != null)
					return this.name + " (" + thisrank + this.sourceIds.get(0) + ")";
				else
					/* No unique name, just leave it alone and pray */
					return this.name;
			} else {
				String qrank = ((unique.rank == null) ? "" : (unique.rank + " "));
				String qname = unique.uniqueName();
				if (qname.length() == 0) qname = unique.name;
				return this.name + " (" + thisrank + "in " + qrank + qname + ")";
			}
		} else
			return "";
	}

	static Comparator<Taxon> compareNodesBySize = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.count() - y.count();
		}
	};

	public boolean isHidden() {
		return (((this.properFlags | this.inheritedFlags) &
                 Taxonomy.HIDDEN_FLAGS) != 0 ||
                ((this.properFlags & Taxonomy.FORCED_VISIBLE) == 0));
	}

	public boolean isDirectlyHidden() {
		return (((this.properFlags & Taxonomy.HIDDEN_FLAGS) != 0) ||
                ((this.properFlags & Taxonomy.FORCED_VISIBLE) == 0));
	}

	public boolean isAnnotatedHidden() {
		return ((this.properFlags | this.inheritedFlags) &
				Taxonomy.HIDDEN) != 0;
    }

	public boolean isExtinct() {
		return ((this.properFlags | this.inheritedFlags) &
				Taxonomy.EXTINCT) != 0;
    }

	// ----- Methods intended for use in jython scripts -----

	// Patch system commands are add, move, synonym, prune, fold, flag

	// tax.add(newTaxon("Bos bos", "species", "data:,foo"))

	public void add(Taxon newchild) {
		if (newchild == null)
			return;				// Error already reported.
		else if (this.taxonomy != newchild.taxonomy)
			System.err.format("** %s and %s aren't in the same taxonomy\n", newchild, this);
		else if (this.children != null && this.children.contains(newchild))
			System.err.format("| %s is already a child of %s\n", newchild, this);
		else {
            if (!newchild.isDetached()) newchild.detach();
			this.addChild(newchild);
            // TBD: get rid of incertae_sedis flags ??
			newchild.addFlag(Taxonomy.EDITED);
		}
	}

	public boolean take(Taxon newchild) {
		if (newchild == null)
			return false;				// Error already reported.
		else if (this.taxonomy != newchild.taxonomy) {
			System.err.format("** %s and %s aren't in the same taxonomy\n", newchild, this);
			return false;
		} else if (this.children != null && this.children.contains(newchild)) {
			System.err.format("| %s is already a child of %s\n", newchild, this);
			return false;
        } else if (newchild == this) {
			System.err.format("** A taxon cannot be its own parent: %s %s\n", newchild, this);
			return false;
        } else {
            if (!newchild.isDetached()) newchild.detach();
			this.addChild(newchild, 0);
			this.addFlag(Taxonomy.EDITED);
            return true;
		}
	}

	public boolean hide() {
		this.addFlag(Taxonomy.HIDDEN);
		this.hideDescendants();
        return true;
	}

	public boolean unhide() {
        this.addFlag(Taxonomy.FORCED_VISIBLE);
        boolean success = true;
        for (Taxon t = this; !t.isRoot(); t = t.parent) {
            if (t.isDirectlyHidden()) {
                t.properFlags &= ~Taxonomy.HIDDEN;
                if (t.isDirectlyHidden()) {
                    System.out.format("** %s will remain hidden until problems with %s are fixed\n", t, this);
                    success = false;
                }
            }
        }
        return true;
	}

	public boolean hideDescendants() {
		if (this.children != null)
			for (Taxon child : this.children) {
				child.properFlags = Taxonomy.HIDDEN;
				child.hideDescendants();
			}
        return true;
	}

	// Hide up to but not including the given rank
	public boolean hideDescendantsToRank(String rank) {
		if (this.children != null)
			for (Taxon child : this.children) {
				if (child.rank != null && !child.rank.equals(rank)) {
					child.addFlag(Taxonomy.HIDDEN);
					child.hideDescendantsToRank(rank);
				}
			}
        return true;
	}

	public void synonym(String name) {
		if (!this.taxonomy.addSynonym(name, this))
			System.err.format("| Synonym already present: %s %s\n", this, name);
	}

	public void rename(String name) {
		String oldname = this.name;
		if (!oldname.equals(name)) {
			Taxon existing = this.taxonomy.unique(name);
			if (existing != null && existing != this)
				System.err.format("** Warning: creating a homonym: %s\n", name);
			this.setName(name);
			this.taxonomy.addSynonym(oldname, this);  // awkward, maybe wrong
		}
	}

    // Nonmonophyletic.

	public boolean elide() {
		if (this.children != null) {
            // Compare smush()
            if (this.parent.children.size() == 1)
                this.taxonomy.addSynonym(this.name, this.parent);
			for (Taxon child : new ArrayList<Taxon>(this.children))
				child.changeParent(this.parent, Taxonomy.UNPLACED);
        }
		return this.prune();
	}

	// Move all of B's children to A, and make B a synonym of A
	// Same as take + elide ?

	public boolean absorb(Taxon other) {
		if (other == null) return false; //error already reported
		if (other == this) return true;
		if (this.taxonomy != other.taxonomy) {
			System.err.format("** %s and %s aren't in the same taxonomy\n", other, this);
			return false;
		}
		if (other.children != null)
			for (Taxon child : new ArrayList<Taxon>(other.children))
				// beware concurrent modification
				child.changeParent(this.parent);
		this.taxonomy.addSynonym(other.name, this);	// Not sure this is a good idea
		other.prune();
        return true;
	}

	public void incertaeSedis() {
		this.addFlag(Taxonomy.INCERTAE_SEDIS);
	}

	public boolean extinct() {
		this.addFlag(Taxonomy.EXTINCT);
        return true;
	}

	public boolean extant() {
		boolean wasExtant = true;
		for (Taxon node = this; node != null; node = node.parent)
			if ((this.properFlags & Taxonomy.EXTINCT) != 0) {
				this.properFlags &= ~Taxonomy.EXTINCT;
				if (node != this)
					System.err.format("** Ancestor %s of %s was marked extinct\n", node, this);
				wasExtant = false;
			}
		return true;
	}

    public boolean isExtant() {
        return (((this.properFlags | this.inheritedFlags) & Taxonomy.EXTINCT) == 0);
    }

    public boolean whetherMonophyletic(boolean whether, boolean setp) {
        if (whether) {
            System.out.format("** Checking and setting monophyly are not yet implemented\n", this);
            return whether;
        } else
            if (setp)
                // Alternatively, set this.mapped = Answer.no(...)
                return this.elide();
            else
                return false;
    }

    // return true on success (the node does or doesn't have the name)

    public boolean whetherHasName(String name, boolean whether, boolean setp) {
        if (whether) {
            // Make sure the node has this name
            if (this.name == null) {
                if (setp) {
                    this.setName(name);
                    return true;
                }
            } else if (this.name.equals(name)) {
                return true;
            } else {
                List<Taxon> nodes = this.taxonomy.lookup(name);
                if (nodes == null) {
                    if (setp) {
                        this.taxonomy.addSynonym(name, this);
                        return true;
                    }
                } else {
                    if (nodes.contains(this)) {
                        return true;
                    } else if (setp) {
                        return this.taxonomy.addSynonym(name, this);
                    }
                }
            }
        } else {
            if (this.name == null)
                return true;    // yes, it does not have that name
            else if (this.name.equals(name)) {
                if (setp) {
                    this.name = this.name + " NOT"; // or maybe null
                    return true;                    // yes, it does not have this name.
                }
            } else {
                List<Taxon> nodes = this.taxonomy.lookup(name);
                if (nodes == null)
                    return true;
                else if (setp) {
                    nodes.remove(this);
                    return true;
                }
            }
        }
        return false;
    }


	// add a tree to the forest?

	// For interactive debugging

	public void show() {
		System.out.format("%s(%s)\n %s size:%s\n	", this.name, this.id, this.rank, this.count());
        this.showLineage(this.taxonomy.forest);
		if (this.children != null) {
			java.util.Collections.sort(this.children, compareNodesBySize);
			int count = 0;
			for (Taxon child : this.children)
				if (++count < 10)
					System.out.format("	 %s(%s) %s\n", child.name, child.id, child.rank);
				else if (count == 10)
					System.out.format("	 ...\n");
		}
		// Very inefficient, but this is not in an inner loop
		for (String name : this.taxonomy.nameIndex.keySet())
			if (this.taxonomy.nameIndex.get(name).contains(this) && !name.equals(this.name))
				System.out.format("Synonym: %s\n", name);
		if (this.sourceIds != null) {
			if (this.sourceIds.size() == 1)
				System.out.format("Source: %s\n", this.getSourceIdsString());
			else
				System.out.format("Sources: %s\n", this.getSourceIdsString());
		}
		if (this.properFlags > 0 || this.inheritedFlags > 0) {
			System.out.print("Flags: ");
			Flag.printFlags(this.properFlags, this.inheritedFlags, System.out);
			System.out.println();
		}
	}

    // Used to explain why a cycle would be created

    void showLineage(Taxon stop) {
		int qount = 0;
		for (Taxon t = this; t != stop; t = t.parent) {
            System.out.print(t.toString());
            if (t.parent != stop) {
                if (++qount < 10) {
                    if (t.isPlaced())
                        System.out.print(" < ");
                    else
                        System.out.print(" << ");
                } else if (qount == 10) {
                    System.out.print(" ...");
                    break;
                }
            }
		}
		System.out.println();
    }

    // Newick stuff copied from src/main/java/opentree/GeneralUtils.java
    // in treemachine repo.  Written by Joseph W. Brown and the other treemachine
    // developers.

    // All common non-alphanumeric chars except "_" and "-", for use when cleaning strings
    public static final Pattern newickIllegal =
        Pattern.compile(".*[\\Q:;/[]{}(),\\E]+.*");
	
	/**
	 * Make sure name conforms to valid newick usage
     * (http://evolution.genetics.washington.edu/phylip/newick_doc.html).
	 * 
	 * Replaces single quotes in `origName` with "''" and puts a pair of single quotes
     * around the entire string.
	 * Puts quotes around name if any illegal characters are present.
	 * 
	 * Author: Joseph W. Brown
	 *
	 * @param origName
	 * @return newickName
	 */
	public static String newickName(String origName, String tag, String id) {
		boolean needQuotes = false;
		String newickName = origName;
		
		// replace all spaces with underscore
		newickName = newickName.replaceAll(" ", "_");
		
		// replace ':' with '_'. a hack for working with older versions of
        // dendroscope e.g. 2.7.4
		newickName = newickName.replaceAll(":", "_");
		
		// newick standard way of dealing with single quotes in taxon names
		if (newickName.contains("'")) {
			newickName = newickName.replaceAll("'", "''");
			needQuotes = true;
        }
		if (tag != null && id != null)
			newickName = String.format("%s_%s%s", newickName, tag, id);

		// if offending characters are present, quotes are needed
		if (newickIllegal.matcher(newickName).matches())
			needQuotes = true;
		if (needQuotes)
			newickName = "'" + newickName + "'";
		
		return newickName;
	}
}
