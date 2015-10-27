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

	int properFlags = 0, inferredFlags = 0, rankAsInt = 0;

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

    boolean inSynthesis = false; // used only for final annotation


	static boolean windyp = true;

	Taxon(Taxonomy tax) {
		this.taxonomy = tax;
	}

    Taxon(Taxonomy tax, String name) {
        this(tax);
        this.setName(name);
    }

    public boolean isRoot() {
        return this.taxonomy.hasRoot(this);
    }

    public boolean isDetached() {
        return this.parent == null /* && !this.isRoot() */;
    }

    public boolean isPlaced() {
        return (this.properFlags & Taxonomy.INCERTAE_SEDIS_ANY) == 0;
    }

    public Taxon mapped() {
        if (this.mapped != null) return this.mapped;  // New nodes, etc.
        if (this.answer == null) return null;
        else if (this.answer.isYes()) return this.answer.target;
        else return null;
    }

	// Clear out temporary stuff from union nodes
	void resetComapped() {
		this.comapped = null;
		if (children != null)
			for (Taxon child : children)
				child.resetComapped();
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

	public void setName(String name) {
		if (name == null) {
            if (this.name != null)
                System.err.println("! Setting name to null not allowed: " + this);
            return;
        }
        if (name.equals(this.name))
            return;
        /* Formerly:  (this is silly)
		if (this.name != null) {
			List<Taxon> nodes = this.taxonomy.lookup(this.name);
			nodes.remove(this);
			if (nodes.size() == 0) {
				//System.out.println("Removing name from index: " + this.name);
				this.taxonomy.nameIndex.remove(this.name);
			}
		}
        */
		this.name = name;
        this.taxonomy.addToNameIndex(this, name);
	}

	public void clobberName(String name) {
		String oldname = this.name;
		if (!oldname.equals(name)) {
			Taxon existing = this.taxonomy.unique(name);
			if (existing != null && existing != this)
				System.err.format("** Warning: creating a homonym: %s\n", name);
			this.setName(name);
            this.taxonomy.removeFromNameIndex(this, oldname);
		}
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
        } else if ((this.properFlags & Taxonomy.FORMER_CONTAINER) != 0) {
            if (false)
                // There tens of thousands of these
                this.report(String.format("Attempt to add %s to ex-container %s - retrying with parent %s",
                                          child.name, this.name, this.parent.name),
                            child);
            else
                this.markEvent("move to parent of former container, instead of to container");
            Taxon target = this.lub;
            if (target == null) target = this.parent;
            target.addChild(child);
            if ((this.properFlags & Taxonomy.MERGED) == 0)
                // all the other types want some kind of unplaced, cheat a bit
                child.addFlag(Taxonomy.UNPLACED);
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
        this.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY; // ??? think about this
        this.addFlag(flags);
	}

    public void addFlag(int flags) {
        if (flags > 0
            && ((flags & Taxonomy.HIDDEN_FLAGS) > 0)  // not just 'edited'
            && this.name != null
            && ((this.count() > 50000
                 && flags != Taxonomy.HIDDEN)
                || (this.name.equals("Blattodea")))) {
            System.out.format("** Setting flags on %s, size %s\nFlags to add: ", this, this.count());
			Flag.printFlags(flags, 0, System.out);
            System.out.format("\n Preexisting flags: ");
			Flag.printFlags(this.properFlags, this.inferredFlags, System.out);
            System.out.format("\n Lineage: ");
            this.showLineage(this.taxonomy.forest);  // want to know the source
            //Taxon.backtrace();
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
		List<Taxon> alts = this.taxonomy.lookup(this.name);
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

    // This is used when the union node is NOT new

    void transferProperties() {
        Taxon unode = this.mapped;
        if (unode == null) return;

        if (this.name != null) {
            if (unode.name == null)
                unode.setName(this.name);
            else
                // ???
                unode.taxonomy.addSynonym(this.name, unode);
        }

		if ((unode.rank == Taxonomy.NO_RANK || unode.rank.equals("samples"))
            && (this.rank != Taxonomy.NO_RANK))
            unode.rank = this.rank;

        int flagsToAdd = (this.properFlags &
                          (Taxonomy.FORCED_VISIBLE | Taxonomy.EDITED |
                           Taxonomy.EXTINCT));
        // Song and dance related to Bivalvia, Blattodea and a few others
        if ((this.properFlags & Taxonomy.EXTINCT) != 0
            && (unode.properFlags & Taxonomy.EXTINCT) == 0
            && !this.name.equals(unode.name)) {
            if (this.markEvent("extinct-transfer-prevented"))
                System.out.format("** Preventing transfer of extinct flag from %s to %s\n", this, unode);
            flagsToAdd &= ~Taxonomy.EXTINCT;
        }
		unode.addFlag(flagsToAdd);

        // No change to hidden or incertae sedis flags.  Union node
        // has precedence.

        unode.addSource(this);
        if (this.sourceIds != null)
            for (QualifiedId id : this.sourceIds)
                unode.addSourceId(id);

        // ??? retains pointers to source taxonomy... may want to fix for gc purposes
        if (unode.answer == null)
            unode.answer = this.answer;
	}

        
    public Taxon alignWithNew(Taxonomy target, String reason) {
        Taxon newnode = this.dup(target, reason);
        this.mapped = newnode;
        newnode.comapped = this;
        this.answer = Answer.yes(this, newnode, reason, null);
        answer.maybeLog();
        return newnode;
    }

	// Duplicate single source node yielding a source or union node

	public Taxon dup(Taxonomy target, String reason) {

		Taxon newnode = new Taxon(target, this.name);

        // Compare this with transferProperties(newnode)
		newnode.rank = this.rank;

        // Retain placement flags, since the usual case is that we're
        // going to attach this in a pretty similar place
		newnode.properFlags = this.properFlags;

        if (this.sourceIds != null)
            // Unusual
            newnode.sourceIds = new ArrayList<QualifiedId>(this.sourceIds);

        // This might be the place to report on homonym creation

		return newnode;
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
			(this.children == null ? "" : "+" + ((Object)(this.children.size())).toString()) +
			" " + this.name +
			twinkie +				// tbd: indicate division top with "#" 
            (this.isDirectlyHidden() ? "?" : "") +
			")";
	}

	// Returns a string of the form prefix:id,prefix:id,...
	// Generally called on a union taxonomy node

	public String getSourceIdsString() {
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

	// Events - punt them to union taxonomy

	boolean markEvent(String note) {
        return false;
	}
    
	boolean report(String note, Taxon othernode) {
		return this.report(note, othernode, null);
	}

	boolean report(String note, Taxon othernode, String witness) {
		if (this.startReport(note)) {
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
		if (this.startReport(note)) {
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
		if (this.startReport(tag))
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

    boolean startReport(String tag) {
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

	// Number of species at or below this node, i.e. exclude infraspecific taxa.

	public int speciesCount() {
        if (this.rank != null && this.rank.equals("species"))
            return 1;
		else if (children == null)
            return 0;
		else {
			int count = 0;
			for (Taxon child: children)
				count += child.speciesCount();
			return count;
		}
	}

	public int binomialCount() {
        if (isBinomial(this.name))
            return 1;
		else if (children == null)
            return 0;
		else {
			int count = 0;
			for (Taxon child: children)
				count += child.binomialCount();
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

    public Taxon mrca(Taxon other) {
        if (other == null) return null; // Shouldn't happen, but...
        return this.mrca(other, this.getDepth(), other.getDepth());
    }

    Taxon mrca(Taxon other, int adepth, int bdepth) {
        if (this.taxonomy != other.taxonomy)
            throw new RuntimeException(String.format("Mrca across taxonomies: %s %s %s %s",
                                                     this, other, this.taxonomy, other.taxonomy));
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
                // seen twice during augment of worms.  ugh.
                // ought to measure the depths and loop around...
                Taxon.backtrace();
                return this.carefulMrca(other);
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
        Taxon a = this, b = other;
        if (a.taxonomy != b.taxonomy) {
            Taxon new_a = a.bridge();
            if (new_a == a)
                b = other.bridge();
            else
                a = new_a;
        }
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
        Taxon a = this;
        while (a.mapped == null) {
            if (a.parent == null)
                // No bridge!  Shouldn't happen
                // see uniqueName (of e.g. Trachelius) for example
                return this;
            a = a.parent;
        }
        return a.mapped;
    }

	// For cycle detection, etc.
	public boolean descendsFrom(Taxon b) {
		for (Taxon a = this; a != null; a = a.parent)
			if (a == b)
				return true;
		return false;
	}

	static Comparator<Taxon> compareNodes = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.name.compareTo(y.name);
		}
	};

	// Delete all of this node's descendants.
	public void trim() {
		if (this.children != null)
			for (Taxon child : new ArrayList<Taxon>(children))
				child.prune("trim");
	}

	// Delete this node and all of its descendants.
	public boolean prune(String reason) {
        this.detach();
        this.addFlag(Taxonomy.EDITED);
        this.setRemoved(reason);
        return true;
    }

    // Recursively set prunedp flag and remove from indexes
	public boolean setRemoved(String reason) {
		this.prunedp = true;
        this.mapped = null;
        if (this.answer == null && (this.taxonomy instanceof SourceTaxonomy)) {
            this.answer = Answer.no(this, null, reason, null);
            this.answer.maybeLog();
        }
		if (this.children != null)
			for (Taxon child : new ArrayList<Taxon>(children))
				child.setRemoved(reason);
        this.taxonomy.removeFromNameIndex(this, this.name);
		if (this.id != null)
			this.taxonomy.idIndex.remove(this.id);
        return true;
    }

	public String uniqueName() {
        String u = this.longUniqueName();
        if (u.equals(this.name))
            return "";          // abbreviation
        else
            return u;
    }

	public String longUniqueName() {
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
        String result;
		if (homonymp) {
			String thisrank = ((this.rank == null) ? "" : (this.rank + " "));
			if (unique == null || unique == this) {
				if (this.sourceIds != null)
					result = this.name + " (" + thisrank + this.sourceIds.get(0) + ")";
				else if (this.id != null)
					result = this.name + " (" + thisrank + this.id + ")";
				else
					/* No unique name, just leave it alone and pray */
					result = this.name;
			} else {
				String qrank = ((unique.rank == null) ? "" : (unique.rank + " "));
				String qname = unique.longUniqueName();
				result = this.name + " (" + thisrank + "in " + qrank + qname + ")";
			}
		} else
			result = this.name;

        // Surface complexity in TNRS
        if ((this.properFlags & Taxonomy.MERGED) != 0)
            result = String.format("%s (merged with %s)",
                                   result,
                                   this.parent.longUniqueName());
        else if ((this.properFlags & Taxonomy.INCONSISTENT) != 0)
            result = String.format("%s (inconsistent in %s)",
                                   result,
                                   this.parent.longUniqueName());

        return result;
	}

	static Comparator<Taxon> compareNodesBySize = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.count() - y.count();
		}
	};

	public boolean isHidden() {
		return (((this.properFlags | this.inferredFlags) &
                 Taxonomy.HIDDEN_FLAGS) != 0 &&
                ((this.properFlags & Taxonomy.FORCED_VISIBLE) == 0));
	}

	public boolean isDirectlyHidden() {
		return (((this.properFlags & Taxonomy.HIDDEN_FLAGS) != 0) &&
                ((this.properFlags & Taxonomy.FORCED_VISIBLE) == 0));
	}

	public boolean isAnnotatedHidden() {
		return ((this.properFlags | this.inferredFlags) &
				Taxonomy.HIDDEN) != 0;
    }

	public boolean isExtinct() {
		return ((this.properFlags | this.inferredFlags) &
				Taxonomy.EXTINCT) != 0;
    }

	public boolean isAnnotatedExtinct() { // blah, inconsistent naming
		return (this.properFlags & Taxonomy.EXTINCT) != 0;
    }

	// ----- Methods intended for use in jython scripts -----

    public boolean notCalled(String name) {
        if (this.name.equals(name)) {
            this.clobberName("Not " + name);
            return true;
        } else {
            this.taxonomy.removeFromNameIndex(this, name);
            return true;
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
			return true;
        } else if (newchild == this) {
			System.err.format("** A taxon cannot be its own parent: %s %s\n", newchild, this);
			return false;
        } else {
            // if (!newchild.isDetached()) newchild.detach();  - not needed given change to newTaxon.
            newchild.changeParent(this, 0);
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
				child.addFlag(Taxonomy.HIDDEN);
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
            this.clobberName(name);
			this.taxonomy.addSynonym(oldname, this);  // awkward, maybe wrong
		}
	}

    // Nonmonophyletic.

	public boolean elide() {
        return this.elide(true);
    }

	public boolean elide(boolean placedp) {
		if (this.children != null) {
            // Compare smush()
            if (this.parent.children.size() == 1)
                this.taxonomy.addSynonym(this.name, this.parent);
			for (Taxon child : new ArrayList<Taxon>(this.children))
				child.changeParent(this.parent, placedp ? 0 : Taxonomy.UNPLACED);
        }
        if (!placedp)
            this.addFlag(Taxonomy.WAS_CONTAINER);
		return this.prune("elide");
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
        // something about extinct flags here - extinct absorbing non-extinct
		this.taxonomy.addSynonym(other.name, this);	// Not sure this is a good idea
		other.prune("absorb");
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
		for (Taxon node = this; node != null; node = node.parent) {
			if ((node.properFlags & Taxonomy.EXTINCT) != 0) {
				if (node != this)
					System.err.format("** Changing ancestor %s of %s from extinct to extant\n", node, this);
			}
            this.properFlags &= ~Taxonomy.EXTINCT;
            this.inferredFlags &= ~Taxonomy.EXTINCT; // voodoo
        }
		return true;
	}

    public boolean isExtant() {
        return (((this.properFlags | this.inferredFlags) & Taxonomy.EXTINCT) == 0);
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
            List<Taxon> sorted = new ArrayList(this.children);
			java.util.Collections.sort(sorted, compareNodesBySize);
			int count = 0;
			for (Taxon child : sorted)
				if (++count < 10)
					System.out.format("	 %s(%s) %s\n", child.name, child.id, child.rank);
				else if (count == 10)
					System.out.format("	 ...\n");
		}
		// Very inefficient, but this is not in an inner loop
		for (String name : this.taxonomy.allNames())
			if (this.taxonomy.lookup(name).contains(this) && !name.equals(this.name))
				System.out.format("Synonym: %s\n", name);
		if (this.sourceIds != null) {
			if (this.sourceIds.size() == 1)
				System.out.format("Source: %s\n", this.getSourceIdsString());
			else
				System.out.format("Sources: %s\n", this.getSourceIdsString());
		}
		if (this.properFlags > 0 || this.inferredFlags > 0) {
			System.out.print("Flags: ");
			Flag.printFlags(this.properFlags, this.inferredFlags, System.out);
			System.out.println();
		}
	}

    // Used to explain why a cycle would be created

    void showLineage(Taxon stop) {
		int qount = 0;
		for (Taxon t = this; t != stop; t = t.parent) {
            if (t == null) break;
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

	static Pattern binomialPattern = Pattern.compile("^[\\p{Upper}][\\p{Lower}\\-]+ [\\p{Lower}\\-]{2,}+$");

    public static boolean isBinomial(String name) {
        return binomialPattern.matcher(name).find();
    }

}
