package org.opentreeoflife.taxa;

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
import java.text.Normalizer;
import java.text.Normalizer.Form;

public class Taxon extends Node implements Comparable<Taxon> {
    // name and taxonomy are inherited from Node
	public String id = null;
	public Rank rank = Rank.NO_RANK;
    public static Collection<Taxon> NO_CHILDREN = null;
    private static Collection<Taxon> NO_CHILDREN_LIST = new ArrayList<Taxon>(0);
	public Collection<Taxon> children = NO_CHILDREN;
    public static Collection<Synonym> NO_SYNONYMS = new ArrayList<Synonym>(0);
	private Collection<Synonym> synonyms = NO_SYNONYMS;
	int count = -1;             // cache of # nodes at or below here
	int depth = -1;             // cache of distance from root
	public boolean prunedp = false;    // for lazy removal from nameIndex
	public int properFlags = 0, inferredFlags = 0;
	Taxon division = null;  // foo.  for Alignment
    public boolean inSynthesis = false; // used only for final annotation
    public boolean unsourced = false;
	public Taxonomy taxonomy;			// For subsumption checks etc.
    public String parentReason = null;

	// State during alignment - cf. Alignment.assignBrackets
	public int seq = 0;		// Self
	public int start = 0;	// First taxon included not including self
	public int end = 0;		// Next taxon *not* included

    public Taxon(Taxonomy tax, String name) {
        super(name);
        this.taxonomy = tax;
        if (name != null)
            tax.addToNameIndex(this, name);
    }

    public Taxon taxon() { return this; }

    public String getType() { return null; }

    public Taxonomy getTaxonomy() { return this.taxonomy; }

    public boolean taxonNameIs(String othername) {
        return this.name.equals(othername);
    }

    public Collection<Taxon> getChildren() {
        if (children == NO_CHILDREN)
            return NO_CHILDREN_LIST;
        else
            return children;
    }

    public boolean hasChildren() {
        return children != NO_CHILDREN;
    }

    public Collection<Synonym> getSynonyms() {
        return synonyms;
    }

    public Iterable<Taxon> descendants(final boolean includeSelf) {
        final Taxon node = this;
        return new Iterable<Taxon>() {
            public Iterator<Taxon> iterator() {

                final List<Iterator<Taxon>> stack = new ArrayList<Iterator<Taxon>>();
                final Taxon[] starting = new Taxon[1]; // locative
                if (includeSelf)
                    starting[0] = node;
                else if (node.children != NO_CHILDREN)
                    stack.add(node.children.iterator());

                return new Iterator<Taxon>() {
                    public boolean hasNext() {
                        if (starting[0] != null) return true;
                        while (true) {
                            if (stack.size() == 0) return false;
                            if (stack.get(0).hasNext()) return true;
                            else stack.remove(0);
                        }
                    }
                    public Taxon next() {
                        Taxon node = starting[0];
                        if (node != null)
                            starting[0] = null;
                        else
                            // Caller has previously called hasNext(), so we're good to go
                            // Was: .get(stack.size()-1)
                            node = stack.get(0).next();
                        if (node.children != NO_CHILDREN)
                            stack.add(node.children.iterator());
                        return node;
                    }
                    public void remove() { throw new UnsupportedOperationException(); }
                };
            }
        };
    }

    public boolean isRoot() {
        return this.taxonomy.hasRoot(this);
    }

    public boolean isDetached() {
        return this.parent == null /* && !this.isRoot() */;
    }

    public boolean isPlaced() {
        return !this.prunedp && (this.properFlags & Taxonomy.INCERTAE_SEDIS_ANY) == 0;
    }

	public Node setName(String name) {
        if (this.prunedp) {
            System.err.format("** Attempt to set name of a pruned taxon: %s %s\n", this, name);
            return null;
        }
        if (name == null) {
            System.err.format("** Null is not a valid name-string: %s\n", this);
            backtrace();
            return null;
        } else if (this.name == null) {
            this.name = name;
            this.taxonomy.addToNameIndex(this, name);
            return this;
        } else if (this.name.equals(name))
            return this;
        else {
            System.err.format("** This taxon already has a canonical name: %s %s\n",
                              this, name);
            backtrace();
            return null;
        }
    }

    // type must be nonnull
	public Synonym addSynonym(String name, String type) {
        if (name == null) {
            System.err.format("** Null is not a valid name-string: %s\n", this);
            backtrace();
            return null;
        } else if (name.equals(this.name)) {
            return null;
        } else {
            for (Synonym syn : this.synonyms)
                if (syn.name.equals(name) && syn.type.equals(type))
                    return syn;
            Synonym syn = new Synonym(name, type, this); // does addToNameIndex
            if (this.synonyms == NO_SYNONYMS)
                this.synonyms = new ArrayList<Synonym>();
            this.synonyms.add(syn);
            return syn;
        }
	}

    // this is union node, node is source node, syn is also in union
    Synonym addSynonym(Node node, String type) {
        Synonym syn = this.addSynonym(node.name, type);
        if (syn != null) {
            Taxon source = node.taxon();
            if (!source.taxonomy.getIdspace().equals("skel")) //KLUDGE!!!
                syn.addSourceId(source.getQualifiedId());
        }
        return syn;
    }

    // Ensure that every name (synonym or not) is a name of the target
    // taxon (either synonym or not).
    // Returns number of synonyms created.

    public int copySynonymsTo(Taxon targetTaxon) {
        int count = 0;
        if (this.name != null)
            if (targetTaxon.addSynonym(this, "synonym") != null)
                ++count;
        for (Synonym syn : this.getSynonyms())
            if (targetTaxon.addSynonym(syn, syn.type) != null)
                ++count;
        return count;
    }

    // Remove name as any kind of name of this node
    public boolean notCalled(String name) {
        if (this.name.equals(name))
            this.taxonomy.removeFromNameIndex(this);
        List<Synonym> losers = new ArrayList<Synonym>();
        for (Synonym syn : this.synonyms) {
            if (syn.name.equals(name))
                losers.add(syn);
        }
        for (Synonym syn : losers) {
            this.taxonomy.removeFromNameIndex(syn);
            this.synonyms.remove(syn);
            if (this.synonyms.size() == 0)
                this.synonyms = NO_SYNONYMS;
        }
        return true;
    }

	public void clobberName(String newname) {
        if (newname.equals(this.name))
            return;
        Taxon existing = this.taxonomy.unique(newname);
        if (existing != null && existing != this)
            System.out.format("* Warning in clobberName: creating a polysemy: %s %s->%s\n",
                              existing, this, newname);
		String oldname = this.name;
        if (oldname != null)
            this.notCalled(oldname);
        this.setName(newname);
	}

	public void setId(String id) {
        if (this.prunedp) {
            System.err.format("** Attempt to set id of a pruned taxon: %s %s\n", this, id);
            return;
        }
		if (id == null)
            return;
        if (this.id == null)
            this.addId(id);
        else if (!this.id.equals(id)) {
            String wasid = this.id;
            this.id = null;
            this.addId(id);
            this.addId(wasid);
        }
	}

    public void addId(String id) {
        this.taxonomy.addId(this, id);
    }

	public Taxon getParent() {
		return parent;
	}

	public static void backtrace() {
		try {
			throw new Exception("Backtrace");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    // This is for detached nodes (*not* in roots list)

	public void setParent(Taxon parent, String reason) {
        Taxon child = this;
		if (child.taxonomy != parent.taxonomy) {
			parent.report("Attempt to add child that belongs to a different taxonomy", child);
			Taxon.backtrace();
		} else if (!child.isDetached()) {
			if (parent.report("Attempt to steal child !!??", child))
				Taxon.backtrace();
		} else if (child == parent) {
			if (parent.report("Attempt to create self-loop !!??", child))
				Taxon.backtrace();
        } else if (child.noMrca()) {
			parent.report("Attempt to take on the forest !!??", parent);
            Taxon.backtrace();
        } else if (parent.descendsFrom(child)) {
            // you'd think the descendsFrom check would slow things
            // down noticeably, but it doesn't
			parent.report("Attempt to create a cycle !!??", child);
            parent.showLineage(child.parent);
            System.out.format("%s", child);
            Taxon.backtrace();
        } else if ((parent.properFlags & Taxonomy.FORMER_CONTAINER) != 0 && (parent.parent != null)) {
            child.markEvent("move to parent of former container, instead of to container");
            Taxon target = parent.parent;
            child.setParent(target, reason + " container");
            if ((parent.properFlags & Taxonomy.MERGED) == 0)
                // all the other types want some kind of unplaced, cheat a bit
                child.addFlag(Taxonomy.UNPLACED);
        } else {
            if (child.rank != Rank.NO_RANK && parent.rank != Rank.NO_RANK &&
                child.rank.level < parent.rank.level &&
                child.isPlaced()) {
                child.markEvent("rank inversion");
				//Taxon.backtrace();
            }
			child.parent = parent;
            child.parentReason = reason;
			if (parent.children == NO_CHILDREN)
				parent.children = new ArrayList<Taxon>();
            else if (parent.children.contains(child))
                System.err.format("** Adding child %s redundantly to %s\n", child, parent);
			parent.children.add(child);
			parent.resetCount();	//force recalculation
		}
	}

	public void addChild(Taxon child, int flags, String reason) {
        child.setParent(this, reason);
        child.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY;
        this.addFlag(flags);
    }

    // Removes it from the tree - undoes setParent

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

    // Detach followed by setParent

	public void changeParent(Taxon newparent, String reason) {
        this.detach();
        this.setParent(newparent, reason);
    }
    
    // flags are to be set on the child (this)

	public void changeParent(Taxon newparent, int flags, String reason) {
        if (flags == 0
            && !this.isPlaced()
            && ((this.name.hashCode() % 100) == 17)) // sample - full list is too long
            System.out.format("| Placing previously unplaced %s in %s\n", this, newparent);
        this.changeParent(newparent, reason);
        this.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY; // ??? think about this
        this.addFlag(flags);
	}

    public void addFlag(int flags) {
        if (flags > 0
            && ((flags & Taxonomy.SUPPRESSED_FLAGS) > 0)  // not just 'edited'
            && this.name != null
            && ((this.count() > 50000
                 && flags != Taxonomy.HIDDEN))) {
            System.out.format("* Setting flags on %s, size %s\nFlags to add: ", this, this.count());
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
	public Taxon getDivision() {
		if (this.division == null) {
			if (this.parent != null)
				this.division = this.parent.getDivision();
            else {
                // System.out.format("## No barrier taxonomy / %s\n", this);
                return null;     // forest
            }
		}
		return this.division;
	}

    public Taxon getDivisionProper() {
        return division;
    }

	public String divisionName() {
		Taxon d = this.getDivision();
        if (d == null) return "";
		return (d.noMrca() ? "" : d.name);
	}

    // public because smasher
	public void setDivision(Taxon division) {
        if (division.name == null && (division.parent != null || this.parent != null))
            System.err.format("## Anonymous division !? %s in %s\n",
                              division, division.parent);
        else {
            if (this.division != null && this.division != division)
                // Could do a tree walk setting division to null...
                this.report("!? changing divisions doesn't work");
            this.division = division;
        }
	}

	// Nearest ancestor having a name that's not a prefix of ours... and isn't also a homonym
	Taxon informative() {
		Taxon up = this.parent;
		while (up != null &&
			   (this.name.startsWith(up.name) || up.taxonomy.lookup(up.name).size() > 1))
			up = up.parent;
		return up;
	}

	//out.println("uid\t|\tparent_uid\t|\tname\t|\trank\t|\t" +
	//			"source\t|\tsourceid\t|\tsourcepid\t|\tuniqname\t|\tpreottol_id\t|\t");

	public void addSource(Taxon source) {
        if (!source.unsourced)
			this.addSourceId(source.getQualifiedId());
	}

	public QualifiedId getQualifiedId() {
        String space = this.taxonomy.getIdspace();
		if (this.id != null) {
			return new QualifiedId(space, this.id);
        } else if (this.noMrca()) {
            // Shouldn't happen
			System.out.println("* [getQualifiedId] Forest");
			return new QualifiedId(space, "<forest>");
        } else if (this.parent == null) {
            // Shouldn't happen
			System.out.format("* [getQualifiedId] %s is detached\n", this);
            return new QualifiedId(space, "<detached>");
        } else if (this.name != null) {
            // e.g. h2007
			// System.out.format("* [getQualifiedId] Taxon has no id, using name: %s:%s\n", space, this.name);
			return new QualifiedId(space, this.name);
        } else {
			// What if from a Newick string?
			System.out.println("* [getQualifiedId] Nondescript");
            return new QualifiedId(space, "<nondescript>");
        }
	}

	// Mainly for debugging

	public String toString() {
		return this.toString(null);
	}

	public String toString(Taxon other) {
		String twinkie = "";
		if (other != null &&
				 other.taxonomy != this.taxonomy &&
				 other.taxonomy.lookup(this.name) != null)
			twinkie = "+";		// Name in common

		String ids;
        if (this.sourceIds != null) {
            QualifiedId ref = this.putativeSourceRef();
            if (ref != null)		// this is from idsource
                ids = (this.id == null ? "" : this.id) + "=" + ref;
            else
                ids = this.getSourceIdsString();
        } else if (this.taxonomy.getIdspace() != null) {
            if (this.id != null)
                ids = this.getQualifiedId().toString();
            else if (this.noMrca())
                ids = this.taxonomy.getIdspace() + ":<forest>";
            else if (this.parent == null)
                ids = this.taxonomy.getIdspace() + ":<detached>";
            else
                ids = this.taxonomy.getIdspace() + ":";
        } else if (this.id != null)
            ids = this.id;
        else
            ids = "-";

		return 
			"(" +
            this.name +
			" " +
            ids +
			(this.children == NO_CHILDREN ? "" : "+" + ((Object)(this.children.size())).toString()) +
			twinkie +				// tbd: indicate division top with "#" 
            (this.isDirectlyHidden() ? "?" : "") +
            (this.prunedp ? " pruned" : "") +
			")";
	}

	// Events - punt them to union taxonomy

	public boolean markEvent(String note) {
        return this.taxonomy.markEvent(note, this);
	}
    
	public boolean report(String note, Taxon othernode) {
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

	public boolean report(String note, List<Taxon> others) {
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
			if (++i < 4)
				output += " " + n.toString(other);
			else if (i == 4)
				output += " ...";
		}
		System.out.println(" " + tag + " " + output);
	}

    public boolean startReport(String tag) {
        // this doesn't seem right somehow
        return false;
    }

	String elaboratedString(Taxonomy tax) {
        return this.toString();
	}

	// Number of child-less nodes at and below this node.

	public int count() {
		if (this.count < 1) {
			this.count = 1;
			if (children != NO_CHILDREN)
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
		if (children == NO_CHILDREN)
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
        if (this.rank == Rank.SPECIES_RANK)
            return 1;
		else if (children == NO_CHILDREN)
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
		else if (children == NO_CHILDREN)
            return 0;
		else {
			int count = 0;
			for (Taxon child: children)
				count += child.binomialCount();
			return count;
		}
	}

	// Use getDepth() only after the tree is in its final form
	public int getDepth() {
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
        if (this.children != NO_CHILDREN)
            for (Taxon child : this.children)
                child.resetDepths();
    }

	// Does not use cache - depths may change during merge
	public int measureDepth() {		// Robust in presence of insertions
		if (this.parent == null)
            return 0;
		else
			return this.parent.measureDepth() + 1;
	}

    // Test the return value of mrca() to see if the two nodes are in separate trees
    public boolean noMrca() {
        return this == this.taxonomy.forest; // forest
    }

    public Taxon carefulMrca(Taxon other) {
        if (other == null) return null; // Shouldn't happen, but...
        return this.mrca(other, this.measureDepth(), other.measureDepth());
    }

    public Taxon mrca(Taxon other) {
        if (other == null) return null; // Shouldn't happen, but...
        return this.mrca(other, this.getDepth(), other.getDepth());
    }

    // Always returns non-null (but possibly the noMrca node)
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
                System.err.format("** shouldn't happen: %s %s?=%s / %s %s?=%s\n",
                                  this, this.getDepth(), this.measureDepth(), other, other.getDepth(), other.measureDepth());
                // seen twice during augment of worms.  ugh.
                // measure the depths and loop around...
                Taxon.backtrace();
                return this.carefulMrca(other);
            }
            a = a.parent;
            b = b.parent;
        }
        return a;
    }
 
	// Compute sibling taxa {a', b'} such that a' includes a and b' includes b.
    // Returns null if one includes the other, or if in different trees.
    // Could return the 'forest' pseudo-taxon.
    // Except, if no way to get to union from source, return null.

	public Taxon[] divergence(Taxon other) {
        Taxon a = this, b = other;
        if (a.taxonomy != b.taxonomy) {
            System.err.format("** Not in the same taxonomy: %s %s\n", a, b);
            return null;
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
        if (a.parent == null) {
            System.err.format("** %s and %s are in different trees\n", this, other);
            Taxon.backtrace();
        }
        Taxon[] result = {a, b};
        return result;
	}

	// For cycle detection, etc.
	public boolean descendsFrom(Taxon b) {
		for (Taxon a = this; a != null; a = a.parent)
			if (a == b)
				return true;
		return false;
	}

	public static Comparator<Taxon> compareNodes = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
            return x.compareTo(y);
		}
	};

    public int compareTo(Taxon that) {
        int z;
        if (this.id != null &&
            that.id != null &&
            ((z = this.id.compareTo(that.id)) != 0))
            return z;
        if (this.name != null &&
            that.name != null &&
            ((z = this.name.compareTo(that.name)) != 0))
            return z;

        // sort nodes with ids before ones without
        if (this.id == null && that.id != null) return 1;
        if (that.id == null && this.id != null) return -1;

        // sort named nodes before unnamed ones
        if (this.name == null && that.name != null) return 1;
        if (that.name == null && this.name != null) return -1;

        // grasp at straws
        return this.getChildren().size() - that.getChildren().size();
    }


	// Delete all of this node's descendants.
	public void trim() {
		if (this.children != NO_CHILDREN)
			for (Taxon child : new ArrayList<Taxon>(children))
				child.prune("trim");
	}

	public boolean prune() {
        return this.prune("");
    }

	// Delete this node and all of its descendants.
	public boolean prune(String reason) {
        this.detach();
        return this.setRemoved(reason);
    }

    // Recursively set prunedp flag and remove from indexes
	public boolean setRemoved(String reason) {
		this.prunedp = true;
		if (this.children != NO_CHILDREN)
			for (Taxon child : new ArrayList<Taxon>(children))
				child.setRemoved(reason);
        for (Synonym syn : this.synonyms)
            this.taxonomy.removeFromNameIndex(syn);
        this.taxonomy.removeFromNameIndex(this);
		if (this.id != null) {
            this.taxonomy.removeFromIdIndex(this, this.id);
            this.id = null;
        }
        if (this.sourceIds != null) {
            for (QualifiedId qid : this.sourceIds)
                this.taxonomy.removeFromQidIndex(this, qid);
            this.sourceIds = null;
        }
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
        if (this.name == null) {
            if (this.id == null)
                return "(anonymous taxon)";
            else
                return String.format("(anonymous taxon with id %s)", this.id);
        }

		List<Node> nodes = this.taxonomy.lookup(this.name);
		if (nodes == null) {
            System.err.format("** Not in name index: %s\n", this);
            return "(this shouldn't happen)";
        }
                
        if (nodes.size() == 1)
            return this.name;

		boolean homonymp = false;

		// Ancestor that distinguishes this taxon from all others with same name
		Taxon ancestor = null;
		for (Node othernode : nodes) {
			if (othernode instanceof Taxon && othernode != this) {  //  && other.name.equals(this.name)
                Taxon other = othernode.taxon();
				homonymp = true;
				if (ancestor != this) {
					Taxon[] div = this.divergence(other);
					if (div != null) {
						if (ancestor == null)
							ancestor = div[0];
						else if (div[0].descendsFrom(ancestor))
							ancestor = div[0];
					}
				}
			}
        }
        String result;
		if (homonymp) {
			String thisrank = ((this.rank == Rank.NO_RANK) ? "" : (this.rank.name + " "));
			if (ancestor == null || ancestor == this) {
				if (this.sourceIds != null)
					result = this.name + " (" + thisrank + this.sourceIds.get(0) + ")";
				else if (this.id != null)
					result = this.name + " (" + thisrank + this.id + ")";
				else
					/* No unique name, just leave it alone and pray */
					result = this.name;
			} else {
				String qrank = ((ancestor.rank == Rank.NO_RANK) ? "" : (ancestor.rank.name + " "));
				String qname = ancestor.longUniqueName();
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
            int h = (x.isHidden() ? 1 : 0) - (y.isHidden() ? 1 : 0);
            if (h != 0) return h;
			return x.count() - y.count();
		}
	};

	public boolean isHidden() {
		return (((this.properFlags | this.inferredFlags) &
                 Taxonomy.SUPPRESSED_FLAGS) != 0 &&
                ((this.properFlags & Taxonomy.FORCED_VISIBLE) == 0));
	}

	public boolean isDirectlyHidden() {
		return (((this.properFlags & Taxonomy.SUPPRESSED_FLAGS) != 0) &&
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

    public boolean isInfraspecific() {
		return ((this.properFlags | this.inferredFlags) &
				Taxonomy.INFRASPECIFIC) != 0;
    }

    // The following needs work.  The EXTINCT flag should only be set
    // on the union if *all* taxa mapping to it taxon are EXTINCT.

    public int flagsToAdd(Taxon unode) {
        int flagsToAdd = (this.properFlags &
                          (Taxonomy.FORCED_VISIBLE | Taxonomy.EDITED |
                           Taxonomy.EXTINCT));
        // Song and dance related to Bivalvia, Blattodea and a few others
        if ((this.properFlags & Taxonomy.EXTINCT) != 0
            && (unode.properFlags & Taxonomy.EXTINCT) == 0
            && !this.name.equals(unode.name)) {
            if (this.markEvent("extinct-transfer-prevented"))
                System.out.format("| Preventing transfer of extinct flag from %s to %s\n", this, unode);
            flagsToAdd &= ~Taxonomy.EXTINCT;
        }
        return flagsToAdd;
    }

	// ----- Methods intended for use in jython scripts -----

	public boolean take(Taxon newchild) {
		if (newchild == null)
			return false;				// Error already reported.
		else if (this.taxonomy != newchild.taxonomy) {
			System.err.format("** %s and %s aren't in the same taxonomy\n", newchild, this);
			return false;
		} else if (this.children != NO_CHILDREN && this.children.contains(newchild)) {
			// System.err.format("| %s is already a child of %s\n", newchild, this);
			return true;
        } else if (newchild == this) {
			System.err.format("** A taxon cannot be its own parent: %s %s\n", newchild, this);
			return false;
        } else if (newchild.parent == this) {
            // System.err.format("* Note: %s is already a child of %s\n", newchild, this);
            return true;
        } else {
            // if (!newchild.isDetached()) newchild.detach();  - not needed given change to newTaxon.
            if (newchild.descendsFrom(this))
                System.out.format("* Note: moving %s from %s out to %s\n",
                                  newchild, newchild.parent, this);
            newchild.changeParent(this, 0, "take");
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
        for (Taxon a = this; !a.isRoot(); a = a.parent) {
            if (a.isDirectlyHidden()) {
                a.properFlags &= ~Taxonomy.HIDDEN;
                if (a.isDirectlyHidden()) {
                    if (!a.isExtinct())
                        System.err.format("** %s will remain hidden until %s [%s] is exposed\n",
                                          this,
                                          a,
                                          Flag.toString(a.properFlags, 0));
                    success = false;
                }
            }
        }
        return true;
	}

	public boolean hideDescendants() {
		if (this.children != NO_CHILDREN)
			for (Taxon child : this.children) {
				child.addFlag(Taxonomy.HIDDEN);
				child.hideDescendants();
			}
        return true;
	}

	// Hide up to but not including the given rank
	public boolean hideDescendantsToRank(String rankname) {
        return hideDescendantsToRank(Rank.getRank(rankname));
    }

	public boolean hideDescendantsToRank(Rank rank) {
		if (this.children != NO_CHILDREN)
			for (Taxon child : this.children) {
				if (child.rank == rank) {
					child.addFlag(Taxonomy.HIDDEN);
					child.hideDescendantsToRank(rank);
				}
			}
        return true;
	}

    public boolean hasName(String name) {
        List<Node> nodes = this.taxonomy.lookup(name);
        if (nodes == null) return false;
        for (Node node : nodes)
            if (node.taxon() == this)
                return true;
        return false;
    }

	public void synonym(String name) {
        this.synonym(name, "synonym");
	}

	public void synonym(String name, String typ) {
		if (this.addSynonym(name, typ) == null)
			System.out.format("| Synonym already present: %s %s\n", this, name);
	}

	public boolean rename(String name) {
        return this.rename(name, "synonym");
    }

	public boolean rename(String name, String typ) {
		String oldname = this.name;
		if (name.equals(oldname)) return true;

        // Check for a sibling node with the target name
        Collection<Node> nodes = this.taxonomy.lookup(name);
        if (nodes != null) {
            for (Node node : nodes) {
                Taxon taxon = node.taxon();
                if (taxon == node) {  // only consider non-synonyms
                    if (taxon == this)
                        // Primary name is already the right name
                        return true;
                    if (taxon.parent == this.parent) {
                        System.err.format("* rename: absorbing %s into %s\n",
                                          this, taxon);
                        taxon.absorb(this);
                        return false;
                    }
                }
            }
        }

        // Change primary name
        this.clobberName(name);

        // Add old name as a synonym
        this.addSynonym(oldname, typ);

        return true;
	}

    // Nonmonophyletic.

	public boolean elide() {
        return this.parent.absorb(this);
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
        if (this.rank != Rank.SPECIES_RANK && other.rank == Rank.SPECIES_RANK)
            System.err.format("** Losing species %s in absorb (to %s)\n", other, this);
		if (other.children != NO_CHILDREN)
			for (Taxon child : new ArrayList<Taxon>(other.children))
				// beware concurrent modification
				child.changeParent(this, "absorb");
        // something about extinct flags here - extinct absorbing non-extinct means ... ?
        // Not sure about the order of the following two, but if the
        // synonym comes before the prune, then it might be suppressed
        // by the presence in the name index of the deprecated taxon
		this.addSynonym(other.name, "proparte synonym");	// Not sure this is a good idea
		other.prune("absorb");
        // copy sources from other to syn?
        return true;
	}

	public void incertaeSedis() {
		this.addFlag(Taxonomy.INCERTAE_SEDIS);
	}

	public void unplaced() {
		this.addFlag(Taxonomy.UNPLACED);
	}

	public boolean extinct() {
		this.addFlag(Taxonomy.EXTINCT);
        return true;
	}

	public boolean extant() {
		for (Taxon node = this; node != null; node = node.parent) {
			if ((node.properFlags & Taxonomy.EXTINCT) != 0) {
				if (node != this)
					System.out.format("* Changing ancestor %s of %s from extinct to extant\n", node, this);
			}
            this.properFlags &= ~Taxonomy.EXTINCT;
            this.inferredFlags &= ~Taxonomy.EXTINCT; // voodoo
        }
		return true;
	}

    public boolean isExtant() {
        return (((this.properFlags | this.inferredFlags) & Taxonomy.EXTINCT) == 0);
    }

    public boolean setRank(String rankstring) {
        if (rankstring == null) {
            this.rank = Rank.NO_RANK;
            return true;
        }
        Rank r = Rank.getRank(rankstring);
        if (r != null) {
            this.rank = r;
            return true;
        } else
            return false;
    }

    public String getRank() {
        return this.rank.name;
    }

    public boolean whetherMonophyletic(boolean whether, boolean setp) {
        if (whether) {
            System.err.format("** Checking and setting monophyly are not yet implemented\n", this);
            return whether;
        } else
            if (setp)
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
                } else
                    return false;
            } else if (this.hasName(name)) {
                return true;
            } else if (setp) {
                this.addSynonym(name, "synonym");
                return true;
            } else
                return false;
        } else {
            if (this.name == null)
                return true;    // yes, it does not have that name
            else if (this.name.equals(name)) {
                if (setp) {
                    this.name = this.name + " NOT"; // or maybe null
                    return true;                    // yes, it does not have this name.
                } else
                    return false;
            } else {
                List<Node> nodes = this.taxonomy.lookup(name);
                if (nodes == null)
                    return true;
                else if (setp) {
                    Node syn = null;
                    for (Node node : nodes)
                        if (node.taxon() == this) {
                            syn = node;
                            break;
                        }
                    nodes.remove(syn);
                    return true;
                } else
                    return false;
            }
        }
    }


	// add a tree to the forest?

	// For interactive debugging

	public void show() {
		System.out.format("%s(%s)\n %s size:%s\n	", this.name, this.id, this.rank.name, this.count());
        this.showLineage(this.taxonomy.forest);
		if (this.children != NO_CHILDREN) {
            List<Taxon> sorted = new ArrayList<Taxon>(this.children);
			Collections.sort(sorted, compareNodesBySize);
			int count = 0;
			for (Taxon child : sorted)
				if (++count < 10)
					System.out.format("	 %s %s\n", child, child.rank.name);
				else if (count == 10)
					System.out.format("	 ...\n");
		}
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
        for (Synonym syn : this.synonyms)
            System.out.format("Synonym: %s %s %s\n", syn.name, syn.type, syn.getSourceIdsString());
	}

    // Used to explain why a cycle would be created

    public void showLineage(Taxon stop) {
		int qount = 0;
		for (Taxon t = this; t != stop; t = t.parent) {
            if (t == null || t == this.taxonomy.forest) break;
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

	// From stackoverflow
	public static final Pattern DIACRITICS_AND_FRIENDS
		= Pattern.compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");

	// TO BE DONE:
	//	 Umlaut letters of German origin (not diaresis) need to have an added 'e'
	//	   ... but there's no way to determine this automatically.
	//	   Xestoleberis y\u00FCchiae is not of Germanic origin.
	//	 Convert upper case letters to lower case
	//		e.g. genus Pechuel-Loeschea	 -- but these are all barren.
	public static String normalizeName(String str) {
        if (str.length() == 0) return null;
		str = Normalizer.normalize(str, Normalizer.Form.NFD);
		str = DIACRITICS_AND_FRIENDS.matcher(str).replaceAll("");
		return str;
	}

}
