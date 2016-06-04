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

public class Taxon extends Node {
    // name and taxonomy are inherited from Node
	public String id = null;
	public Taxon parent = null;
	public String rank = null;
	public Collection<Taxon> children = null;
	public List<QualifiedId> sourceIds = null;
	int count = -1;             // cache of # nodes at or below here
	int depth = -1;             // cache of distance from root
	public boolean prunedp = false;    // for lazy removal from nameIndex
	public int properFlags = 0, inferredFlags = 0;
	int rankAsInt = 0;
	Taxon division = null;

	// State during alignment
	public Taxon mapped = null;	// source node -> union node
	public Taxon comapped = null;		// union node -> example source node
	public Answer answer = null;  // source nodes only
    public Taxon lub = null;                 // union node that is the lub of node's children
	// Cf. AlignmentByName.assignBrackets
	public int seq = 0;		// Self
	public int start = 0;	// First taxon included not including self
	public int end = 0;		// Next taxon *not* included
    public boolean inSynthesis = false; // used only for final annotation

    public Taxon(Taxonomy tax, String name) {
        super(tax, name);       // does addToNameIndex
    }

    public Taxon taxon() { return this; }

    public boolean taxonNameIs(String othername) {
        return this.name.equals(othername);
    }

    public Iterable<Taxon> descendants(final boolean includeSelf) {
        final Taxon node = this;
        return new Iterable<Taxon>() {
            public Iterator<Taxon> iterator() {

                final List<Iterator<Taxon>> stack = new ArrayList<Iterator<Taxon>>();
                final Taxon[] starting = new Taxon[1]; // locative
                if (includeSelf)
                    starting[0] = node;
                else if (node.children != null)
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
                        if (node.children != null)
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

	public void setSourceIds(String info) {
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
		this.name = name;
        this.taxonomy.addToNameIndex(this, name);
	}

	public Synonym newSynonym(String name, String type) {
        if (this.name == null || name == null)
            return null;        // No synonyms for anonymous nodes
		if (this.name.equals(name)) {
            return null;        // No self-synonyms
        } else {
            List<Node> nodes = this.taxonomy.lookup(name);
            if (nodes == null) {
                Synonym syn = new Synonym(name, type, this); // does addToNameIndex
                // this.taxonomy.addToNameIndex(syn, name);
                return syn;
            } else {
                // We don't want to create a homonym.
                return null;
            }
        }
	}

	public void clobberName(String name) {
		String oldname = this.name;
		if (!oldname.equals(name)) {
            this.taxonomy.removeFromNameIndex(this, oldname);
			Taxon existing = this.taxonomy.unique(name);
			if (existing != null && existing != this)
				System.err.format("** Warning: creating a homonym: %s\n", name);
			this.setName(name); // adds to index
		}
	}

	public void setId(String id) {
		if (id != null && this.id == null) {
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

	public static void backtrace() {
		try {
			throw new Exception("Backtrace");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    // This is for detached nodes (*not* in roots list)

	public void addChild(Taxon child) {
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

	public void addChild(Taxon child, int flags) {
        this.addChild(child);
        child.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY;
        this.addFlag(flags);
    }

    // Removes it from the tree - undoes addChild

	public void detach() {
        if (this.name != null && this.name.equals("Salicaceae")) {
            System.out.println("** detaching Salicaceae");
            backtrace();
        }
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

    // public because smasher
	public void setDivision(Taxon division) {
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

	//out.println("uid\t|\tparent_uid\t|\tname\t|\trank\t|\t" +
	//			"source\t|\tsourceid\t|\tsourcepid\t|\tuniqname\t|\tpreottol_id\t|\t");

	// Note: There can be multiple sources, separated by commas.
	// However, the first one in the list is the original source.
	// The others are merely inferred to be identical.

	public QualifiedId putativeSourceRef() {
		if (this.sourceIds != null)
			return this.sourceIds.get(0);
		else
			return null;
	}

	// Add most of the otherwise unmapped nodes to the union taxonomy,
	// either as new names, fragmented taxa, or (occasionally)
	// new homonyms, or vertical insertions.

	public void addSourceId(QualifiedId qid) {
		if (this.sourceIds == null)
			this.sourceIds = new ArrayList<QualifiedId>(1);
		if (!this.sourceIds.contains(qid))
			this.sourceIds.add(qid);
	}

	public void addSource(Taxon source) {
		if (!source.taxonomy.getIdspace().equals("skel")) //KLUDGE!!!
			addSourceId(source.getQualifiedId());
		// Accumulate ...
		if (false && source.sourceIds != null)
			for (QualifiedId qid : source.sourceIds)
				addSourceId(qid);
	}

	public QualifiedId getQualifiedId() {
        String space = this.taxonomy.getIdspace();
		if (this.id != null) {
			return new QualifiedId(space, this.id);
        } else if (this.noMrca()) {
            // Shouldn't happen
			System.err.println("| [getQualifiedId] Forest");
			return new QualifiedId(space, "<forest>");
        } else if (this.parent == null) {
            // Shouldn't happen
			System.err.println("| [getQualifiedId] Detached");
            return new QualifiedId(space, "<detached>");
        } else if (this.name != null) {
            // e.g. h2007
			// System.err.format("| [getQualifiedId] Taxon has no id, using name: %s:%s\n", space, this.name);
			return new QualifiedId(space, this.name);
        } else {
			// What if from a Newick string?
			System.err.println("| [getQualifiedId] Nondescript");
            return new QualifiedId(space, "<nondescript>");
        }
	}

	// Mainly for debugging

	public String toString() {
		return this.toString(null);
	}

	public String toString(Taxon other) {
		String twinkie = "";
		if (this.mapped != null || this.comapped != null)
			twinkie = "*";
		else if (other != null &&
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
                ids = "~";
        } else if (this.id != null)
            ids = this.id;
        else
            ids = "-";

		return 
			"(" +
            this.name +
			" " +
            ids +
			(this.children == null ? "" : "+" + ((Object)(this.children.size())).toString()) +
			twinkie +				// tbd: indicate division top with "#" 
            (this.isDirectlyHidden() ? "?" : "") +
            (this.prunedp ? " pruned" : "") +
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

	public boolean markEvent(String note) {
        if (this.taxonomy instanceof SourceTaxonomy)
            return this.taxonomy.markEvent(note, this);
        else
            return false;
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

    public boolean startReport(String tag) {
        // this doesn't seem right somehow
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
        if (this.children != null)
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
                System.out.format("** shouldn't happen: %s %s?=%s / %s %s?=%s\n",
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
        if (a.parent == null) {
            System.err.format("** %s and %s are in different trees\n", this, other);
            Taxon.backtrace();
        }
        Taxon[] answer = {a, b};
        return answer;
	}

    // Map source taxon to nearest available union taxon
    public Taxon bridge() {
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
            if (x.name != null && y.name != null)
                return x.name.compareTo(y.name);
            else if (x.id != null && y.id != null)
                return x.id.compareTo(y.id);
            else
                return 0;
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
        return this.setRemoved(reason);
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
        if (this.name == null) {
            if (this.id == null)
                return "(anonymous taxon)";
            else
                return String.format("(anonymous taxon with id %s)", this.id);
        }

		List<Node> nodes = this.taxonomy.lookup(this.name);
		if (nodes == null) {
            System.out.format("** Not in name index: %s\n", this);
            return "(this shouldn't happen)";
        }
                
		boolean homonymp = false;

		// Ancestor that distinguishes this taxon from all others with same name
		Taxon unique = null;
		for (Node othernode : nodes) {
            Taxon other = othernode.taxon();
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
            int h = (x.isHidden() ? 1 : 0) - (y.isHidden() ? 1 : 0);
            if (h != 0) return h;
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

    public boolean isInfraspecific() {
		return ((this.properFlags | this.inferredFlags) &
				Taxonomy.INFRASPECIFIC) != 0;
    }

    public int flagsToAdd(Taxon unode) {
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
        return flagsToAdd;
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
        } else if (newchild.parent == this) {
            System.err.format("* Note: %s is already a child of %s\n", newchild, this);
            return true;
        } else {
            // if (!newchild.isDetached()) newchild.detach();  - not needed given change to newTaxon.
            if (newchild.descendsFrom(this))
                System.err.format("* Note: moving %s from %s to %s\n",
                                  newchild, newchild.parent, this);
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

    public boolean hasName(String name) {
        List<Node> nodes = this.taxonomy.lookup(name);
        if (nodes == null) return false;
        for (Node node : nodes)
            if (node.taxon() == this)
                return true;
        return false;
    }

	public void synonym(String name) {
		if (!this.taxonomy.addSynonym(name, this, "synonym"))
			System.err.format("| Synonym already present: %s %s\n", this, name);
	}

	public void rename(String name) {
		String oldname = this.name;
		if (!oldname.equals(name)) {
            this.clobberName(name);
			this.taxonomy.addSynonym(oldname, this, "synonym");  // awkward, maybe wrong
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
                this.taxonomy.addSynonym(this.name, this.parent, "subsumed_by");
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
				child.changeParent(this);
        // something about extinct flags here - extinct absorbing non-extinct
		this.taxonomy.addSynonym(other.name, this, "subsumed_by");	// Not sure this is a good idea
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
                } else
                    return false;
            } else if (this.hasName(name)) {
                return true;
            } else if (setp) {
                this.taxonomy.addSynonym(name, this, "synonym");
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
		System.out.format("%s(%s)\n %s size:%s\n	", this.name, this.id, this.rank, this.count());
        this.showLineage(this.taxonomy.forest);
		if (this.children != null) {
            List<Taxon> sorted = new ArrayList(this.children);
			java.util.Collections.sort(sorted, compareNodesBySize);
			int count = 0;
			for (Taxon child : sorted)
				if (++count < 10)
					System.out.format("	 %s %s\n", child, child.rank);
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
		// Very inefficient, but this is not in an inner loop
		for (String name : this.taxonomy.allNames())
			if (this.hasName(name) && !name.equals(this.name))
				System.out.format("Synonym: %s\n", name);
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
