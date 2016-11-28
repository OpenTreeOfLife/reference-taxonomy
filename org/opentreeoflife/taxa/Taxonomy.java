/*

  Open Tree Reference Taxonomy (OTT) taxonomy class.

  Some people think having multiple classes in one file is terrible
  programming style...	I'll split this into multiple files when I'm
  ready to do so; currently it's much easier to work with in this
  form.

  In jython, say:
	 from org.opentreeoflife.smasher import Taxonomy

*/

package org.opentreeoflife.taxa;

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
import org.json.simple.JSONObject; 

public abstract class Taxonomy {
    private Map<String, List<Node>> nameIndex = new HashMap<String, List<Node>>();
	private Map<String, Taxon> idIndex = new HashMap<String, Taxon>();
    public Taxon forest = new Taxon(this, null);
	public String idspace = null; // "ncbi", "ott", etc.
	String[] header = null;

	public JSONObject properties = new JSONObject();
    public Taxon ingroup = null;    // for trees, not taxonomies

	private String tag = null;     // unique marker
	private int taxid = -1234;	   // kludge

    public EventLogger eventLogger = new EventLogger();

    Map<QualifiedId, Node> qidIndex = null;
    Set<QualifiedId> qidAmbiguous = null;

	public Taxonomy() {
    }

	public Taxonomy(String idspace) {
        this.idspace = idspace;
    }

	public String toString() {
		return "(taxonomy " + this.getTag() + ")";
	}

    public void setEventLogger(EventLogger eventLogger) {
        eventLogger.resetEvents();    // usually no output
        this.eventLogger = eventLogger;
    }

    // Every taxonomy defines a namespace, although not every node has a name.

	public List<Node> lookup(String name) {
        if (name == null)
            return null;
        else
            return this.nameIndex.get(name);
	}

    public int numberOfNames() {
        return this.nameIndex.size();
    }

    public Collection<String> allNames() {
        return this.nameIndex.keySet();
    }

    public Iterable<Node> allNamedNodes() {
        return new Iterable<Node>() {
            public Iterator<Node> iterator() {
                return new Iterator<Node>() {
                    private Iterator<String> names = Taxonomy.this.allNames().iterator();
                    private Iterator<Node> nodes = new ArrayList<Node>().iterator();

                    public boolean hasNext() {
                        return nodes.hasNext() || names.hasNext();
                    }
                    public Node next() {
                        if (!nodes.hasNext())
                            nodes = Taxonomy.this.lookup(names.next()).iterator();
                        return nodes.next();
                    }
                    public void remove() { throw new UnsupportedOperationException(); }
                };
            }
        };
    }

    // compare addSynonym
	void addToNameIndex(Node node, String name) {
        if (node.getTaxonomy() != this)
            throw new RuntimeException(String.format("attempt to put node %s in wrong taxonomy %s", node, this));
        if (name == null)
            throw new RuntimeException("bug " + node);
		List<Node> nodes = this.lookup(name);
		if (nodes == null) {
			nodes = new ArrayList<Node>(1); //default is 10
            nodes.add(node);
			this.nameIndex.put(name, nodes);
		} else if (!nodes.contains(node)) {
            nodes.add(node);
            if (nodes.size() == 75) {
                // should use eventLogger
                System.out.format("| %s is the 75th in %s to have the name '%s'\n",
                                  node,
                                  this.getTag(),
                                  name);
            }
        }
    }

    // delete a synonym
    public void removeFromNameIndex(Node node) {
		List<Node> nodes = this.lookup(node.name);
        // node.name is name for every node in nodes
        if (nodes != null) {
            nodes.remove(node);
            if (nodes.size() == 0)
                this.nameIndex.remove(node.name);
        }
        node.name = null;       // maintain invariant.
	}

    // Similar, there is an idspace, but not every node has an id.

    public Iterable<String> allIds() {
        return this.idIndex.keySet();
    }

    public void addId(Taxon node, String id) {
        if (node.prunedp)
            return;
        if (id != null) {
            Taxon existing = this.lookupId(id);
            if (existing != null) {
                if (existing != node)
                    System.err.format("** Id collision: %s wants id of %s\n", node, existing);
            } else {
                if (node.id == null)
                    node.id = id;
                this.idIndex.put(id, node);
            }
        }
    }

    public void removeFromIdIndex(Taxon node, String id) {
        // could check that lookupId(id) == node
        this.idIndex.remove(id);
    }

    public void removeFromQidIndex(Taxon node, QualifiedId qid) {
        // could check that lookupQid(id) == node
        if (this.qidIndex != null) {
            this.qidIndex.remove(qid);
            this.qidAmbiguous.remove(qid);
        }
    }

    // utility
	public Taxon unique(String designator) {
		List<Node> probe = this.lookup(designator);
		if (probe != null) {
            if (probe.size() == 1)
                return probe.get(0).taxon();
            else
                return null;
		} else 
			return this.lookupId(designator);
	}

    // Every taxonomy has an idspace, even if not every node has an id.

	public Taxon lookupId(String id) {
        Taxon t = this.idIndex.get(id);
        if (t == null)
            return t;
        if (t.prunedp)
            // System.err.format("** Pruned taxon found in id index: %s\n", t);
            return null;
        return t;
	}

    // Roots - always Taxons, never Synonyms.

    public Iterable<Taxon> roots() {
        if (forest.children == null)
            return noRoots;
        else
            return forest.children;
    }

    private static final Iterable<Taxon> noRoots = new ArrayList<Taxon>(0);

    public boolean hasRoot(Taxon node) {
        return node.parent == forest && node.taxonomy == this;
    }

    // For detached nodes.  Use Taxon.promoteToRoot if attached.
    // If you want to say it's disjoint from some other nodes, create a
    // new taxon, say 'life', and put them both all it.
    public void addRoot(Taxon node) {
        node.setParent(this.forest, "addRoot");
        if (node.name == null || !node.name.equals("life"))
            node.addFlag(Taxonomy.UNPLACED);
    }

    public int rootCount() {
        if (this.forest.children == null)
            return 0;
        else
            return this.forest.children.size();
    }

    // The hierarchicy (see Taxon methods)

	public int count() {
        return this.forest.count() - 1;
	}

	public int tipCount() {
        return this.forest.tipCount();
	}

	// Iterate over all nodes reachable from roots

    public Iterable<Taxon> taxa() {
        return this.forest.descendants(false);
    }

	static int globalTaxonomyIdCounter = 1;

	public String getTag() {
		if (this.tag == null) {
			if (this.taxid < 0)
				this.taxid = globalTaxonomyIdCounter++;
            this.tag = String.format("%s-%s", this.getIdspace(), taxid);
        }
        return this.tag;
	}

    public void setTag(String tag) {
        this.tag = tag;
    }

    // The 'idspace' string will show up as a prefix in QualifiedNames

	public String getIdspace() {
        return this.idspace;
	}

    public void startQidIndex() {
        this.qidIndex = new HashMap<QualifiedId, Node>();
        this.qidAmbiguous = new HashSet<QualifiedId>();
    }

    public Node lookupQid(QualifiedId qid) {
        if (qidIndex == null)
            return null;
        else {
            Node node = this.qidIndex.get(qid);
            if (node != null) {
                if (node.taxon().prunedp || this.qidAmbiguous.contains(qid))
                    return null;
            }
            return node;
        }
    }

    // index a single node by one qid
    public void indexByQid(Node node, QualifiedId qid) {
        if (this.qidIndex != null) {
            Node other = this.qidIndex.get(qid);
            if (other != null) {
                if (other != node)
                    this.qidAmbiguous.add(qid);
            } else
                this.qidIndex.put(qid, node);
        }
    }

    // index all nodes by all their qids
    public void indexByQid() {
        this.startQidIndex();
        for (Taxon taxon : this.taxa()) {
            if (taxon.sourceIds != null)
                for (QualifiedId qid : taxon.sourceIds)
                    indexByQid(taxon, qid);
            for (Synonym syn : taxon.getSynonyms())
                if (syn.sourceIds != null)
                    for (QualifiedId qid : syn.sourceIds)
                        indexByQid(syn, qid);
        }
    }


    /* Nodes with more children come before nodes with fewer children.
       Nodes with shorter ids come before nodes with longer ids.
       */

	public static int compareTaxa(Taxon n1, Taxon n2) {
		int q1 = (n1.children == null ? 0 : n1.children.size());
		int q2 = (n2.children == null ? 0 : n2.children.size());
		if (q1 != q2) return q2 - q1;

        if (n1.id != null) {
            if (n2.id != null) {
                // id might or might not look like an integer
                int z = n1.id.length() - n2.id.length();
                if (z != 0) return z;
                else return n1.id.compareTo(n2.id);
            } else
                // sort nodes with ids before nodes without ids
                return -1;
        } else if (n2.id != null)
            // sort nodes without ids after nodes with ids
            return 1;
        // Neither has an id
        else if (n1.name != null) {
            if (n2.name != null)
                // Both have names
                return n1.name.compareTo(n2.name);
            else
                return -1;
        } else if (n2.name != null)
            return 1;
        else
            // TBD: compare source list
            return 0;
	}

	// DWIMmish - taxonomy can be specified in any of the following ways:
    //   1. interim taxonomy format, if argument ends with '/'
    //   2. literal Newick string, if argument starts with '(',
    //   3. load Newick from file, if argument ends with '.tre'

	public static SourceTaxonomy getTaxonomy(String designator, String idspace) throws IOException {
        SourceTaxonomy tax = getRawTaxonomy(designator, idspace);
        tax.postLoadActions();
        return tax;
    }

	public static SourceTaxonomy getRawTaxonomy(String designator, String idspace) throws IOException {
		SourceTaxonomy tax = new SourceTaxonomy(idspace);
		if (designator.startsWith("(")) {
            Taxon root = Newick.newickToNode(designator, tax);
			tax.addRoot(root);
        } else if (designator.endsWith(".tre")) {
			System.out.println("--- Reading " + designator + " ---");
            tax.loadNewick(designator);
        } else {
			if (!designator.endsWith("/")) {
				System.out.println("* Taxonomy designator should end in / but doesn't: " + designator);
				designator = designator + "/";
			}
			System.out.println("--- Reading " + designator + " ---");
			new InterimFormat(tax).loadTaxonomy(designator);
            tax.purgeTemporaryIds();
		}
		return tax;
	}

    // ids that look like negative integers are temporary ids for
    // otherwise id-less nodes (e.g. from Newick)

	private static final Pattern NEGATIVE_NUMERAL = Pattern.compile("-[0-9]+");

    void purgeTemporaryIds() {
        List<Taxon> losers = new ArrayList<Taxon>();
        for (Taxon node : idIndex.values())
            if (node.id != null &&
                NEGATIVE_NUMERAL.matcher(node.id).matches())
                losers.add(node);
        if (losers.size() > 0)
            System.out.printf("| Removing %s temporary ids\n", losers.size());
        for (Taxon node : losers) {
            idIndex.remove(node);
            node.id = null;
        }
    }

    // Perform a variety of post-intake tasks, independent of which
    // parser was used

    public void postLoadActions() {
        // Get rid of diacritics and so on
        this.normalizeNames();

        // Flag changes - make sure inherited flags have something to inherit from
        this.fixFlags();

        this.placeBiggest(); // 'life' or biggest root is placed

        // Topology changes:

        // Elide incertae-sedis-like containers and set some flags
		this.elideContainers();

        // Foo
		this.handleParentChildHomonyms();

		if (this.rootCount() == 0)
			System.err.println("** No root nodes!");

        // do we really want to do this?  it's clutter...
        // this.inferFlags();

        // Some statistics & reports
		this.investigateHomonyms();
        int nroots = this.rootCount();
        int ntips = this.tipCount();
        int nnodes = this.count();
        int snodes = this.synonymCount();
        System.out.format("| %s roots + %s internal + %s tips = %s total, %s synonyms\n",
                          nroots, nnodes - (ntips + nroots), ntips, nnodes, snodes);
    }

    // Get rid of diacritics and similar marks.
    void normalizeNames() {
        int norm = 0;
        List<String[]> changes = new ArrayList<String[]>();
        for (String name : this.allNames()) {
            String newname = Taxon.normalizeName(name);
            if (!newname.equals(name) && this.lookup(newname) == null)
                changes.add(new String[]{name, newname});
        }
        for (String[] change : changes) {
            String name = change[0], newname = change[1];
            for (Node node : new ArrayList<Node>(this.lookup(name)))
                if (node instanceof Taxon)
                    ((Taxon)node).rename(newname, "spelling variant");
            ++norm;
        }
        if (norm > 0)
            System.out.format("| Normalized %s names\n", norm);
    }

    int synonymCount() {
        int i = 0;
        for (Node node : this.allNamedNodes())
            if (node instanceof Synonym)
                ++i;
        return i;
    }

    public void clearDivisions() {
        for (Taxon node : this.taxa())
            node.division = null;
        this.forest.division = null;
    }

    // ----- Standard topology manipulations! -----

    // Elide 'incertae sedis'-like containers, encoding the
    // incertaesedisness of their children in flags.
    // This gets applied to every taxonomy, not just NCBI, on ingest.

	public void elideContainers() {
        elideContainers(forest);
	}

	public static void elideContainers(Taxon node) {
		// Recursive descent
        
        for (Taxon child : node.getChildren())
            elideContainers(child);

        Collection<Taxon> children = node.getChildren();
        for (Taxon child : new ArrayList<Taxon>(children)) {
            int flag = 0;
            if (child.name != null) {
                if (incertae_sedisRegex.matcher(child.name).find()) // Rule 3+5
                    flag = INCERTAE_SEDIS;
                else if (environmentalRegex.matcher(child.name).find()) // Rule 3+5
                    flag = ENVIRONMENTAL;
            }
            if (flag != 0) {
                child.addFlag(WAS_CONTAINER); // probably should remove it entirely
                if (child.getChildren().size() == 1) {
                    child.markEvent("eliding only child because it's a container");
                    flag = 0;
                }
                for (Taxon grandchild : new ArrayList<Taxon>(child.getChildren()))
                    grandchild.changeParent(node, flag, "elideContainers");
            }
        }
	}

    // If parent and child have the same name, we elide the child

	void handleParentChildHomonyms() {
		Set<Taxon> losers = new HashSet<Taxon>();
		for (Taxon node : this.taxa())
			if (!node.isRoot()
                && node.name != null
                && node.name.equals(node.parent.name))
                // Parent-child homonym.
                losers.add(node);

        Rank subgenus = Rank.getRank("subgenus");
        int elisions = 0, subgenusCount = 0, nohope = 0;

		for (Taxon node : losers) {

            if (!node.name.equals(node.parent.name)) continue;  // race condition

            // If node is the only child, we can get rid of the parent.
            if (node.parent.children.size() == 1) {
                if (!node.isPlaced()) { // ???? why this conditiion ?
                    // could also test for same rank.
                    if (++elisions < 10)
                        System.out.format("| Eliding %s in %s, %s children\n",
                                          node.name,
                                          node.parent.name,
                                          (node.children == null ?
                                           "no" :
                                           node.children.size())
                                          );
                    else if (elisions == 10)
                        System.out.format("| ...\n");

                    // We can elide either the child or the parent.

                    if (node.rank == Rank.NO_RANK)
                        node.rank = node.parent.rank;
                    node.parent.elide();
                }

            } else if (node.rank == subgenus) {

                String newname = String.format("%s %s %s", node.name, node.rank.name, node.name);
                Taxon existing = this.unique(newname);
                if (existing != null && existing.parent == node.parent)
                    // May want to absorb recursively... cf. smush()
                    existing.absorb(node);
                else
                    node.clobberName(newname);
                ++subgenusCount;
            } else
                ++nohope;
        }
        if (elisions + subgenusCount + nohope > 0)
            System.out.format("| %s: elided %s nodes, fixed %s subgenus names, %s p/c homs not addressed\n",
                              this.tag, elisions, subgenusCount, nohope);
	}

	// Fold sibling homonyms together into single taxa.
	// Optional step.

	public void smush() {
        for (Taxon root : this.roots())
            smush(root);
    }

    private void smush(Taxon node) {
        if (node.children == Taxon.NO_CHILDREN) return;

        Map<String, List<Taxon>> childrenByName = new HashMap<String, List<Taxon>>();

        // Collate children by name
        for (Taxon child : node.children) {
            List<Node> homs = this.lookup(child.name);
            if (homs.size() > 1) { // pessimization
                List<Taxon> l = childrenByName.get(child.name);
                if (l == null) {
                    l = new ArrayList<Taxon>();
                    childrenByName.put(child.name, l);
                }
                l.add(child);
            }
        }

        for (String name : childrenByName.keySet()) {
            List<Taxon> homs = childrenByName.get(name);
            if (homs.size() <= 1) continue;

            // Get smallest... that will become the 'right' one
            Taxon smallest = homs.get(0);
            for (Taxon other : homs)
                if (compareTaxa(other, smallest) < 0)
                    smallest = other;

            // Now absorb all the others into the smallest
            for (Taxon other : homs)
                if (other != smallest) {
                    this.markEvent("smushing");
                    if (other.isExtant()) // should transfer other flags too?
                        smallest.properFlags &= ~Taxonomy.EXTINCT;
                    smallest.absorb(other);
                    this.addId(smallest, other.id);
                }

        }

        for (Taxon child : node.children)
            smush(child);
	}

    // ----- this appears to be unused at present -----

    public void cleanRanks() {
        Rank genus = Rank.getRank("genus");
        Rank subspecies = Rank.getRank("subspecies");
        Rank variety = Rank.getRank("variety");
        for (Taxon node : this.taxa())
            if (node.rank == Rank.NO_RANK && node.name != null) {
                if (Taxon.isBinomial(node.name))
                    if (node.parent != null && node.parent.rank == genus) {
                        System.out.format("| Setting rank of %s to species\n", node);
                        /* Problems:
                           Bhanja serogroup
                           Ignatzschineria larvae
                           Euphorbia unplaced
                           Hypherpes complex
                           Mauremys hybrids
                           Sylvaemus group
                        */
                        if (!node.name.endsWith("group"))
                            node.rank = Rank.SPECIES_RANK;
                    }
                else if (node.name.contains(" subsp.") || node.name.contains(" subsp ")) {
                    System.out.format("| Setting rank of %s to subspecies\n", node);
                    node.rank = subspecies;
                }
                else if (node.name.contains(" var.")) {
                    System.out.format("| Setting rank of %s to variety\n", node);
                    node.rank = variety;
                }
            }
    }

    // ----- Flags -----

	// Each Taxon has two parallel sets of flags: 
	//	 proper - applies particularly to this node
	//	 inherited - applies to this node because it applies to an ancestor
	//	   (where in some cases the ancestor may later be 'elided' so
	//	   not an ancestor any more)

	// Work in progress - don't laugh - will in due course be
	// converting flag set representation from int to EnumSet<Flag>
    // Should move all these to Flag class

    // Former containers
    public
	static final int WAS_CONTAINER       = (1 <<  0);  // unclassified, environmental, ic, etc
    public
	static final int INCONSISTENT		 = (1 <<  1);  // paraphyletic taxa
    public
	static final int MERGED  			 = (1 <<  2);  // merge-compatible taxa
    public
	static final int FORMER_CONTAINER =
        (WAS_CONTAINER | INCONSISTENT | MERGED);

	// Varieties of incertae sedis
	static final int MAJOR_RANK_CONFLICT = (1 <<  3);
	static final int UNCLASSIFIED		 = (1 <<  4);
	static final int ENVIRONMENTAL		 = (1 <<  5);
	static final int INCERTAE_SEDIS		 = (1 <<  6);
    public
	static final int UNPLACED			 = (1 <<  7);   // children of paraphyletic taxa
	static final int TATTERED			 = (1 <<  8);   // children of paraphyletic taxa
    public
	static final int INCERTAE_SEDIS_ANY	 = 
        (MAJOR_RANK_CONFLICT | UNCLASSIFIED | ENVIRONMENTAL
         | INCERTAE_SEDIS | UNPLACED);

	// Individually troublesome - not sticky - combine using &  ? no, | ?
    public
	static final int NOT_OTU			 = (1 <<  9);
    public
	static final int HYBRID				 = (1 << 10);
	static final int VIRAL				 = (1 << 11);

	// Australopithecus
	static final int SIBLING_HIGHER		 = (1 << 13); // has a sibling with higher rank
	static final int SIBLING_LOWER		 = (1 << 14); // legacy, see Flag.java

	// Annotations set during assembly
	static final int HIDDEN				 = (1 << 16);	  // combine using &
	static final int EXTINCT			 = (1 << 17);	  // combine using |
	static final int FORCED_VISIBLE		 = (1 << 18);	  // combine using |
	static final int EDITED				 = (1 << 19);			  // combine using |

	// Inferred
    public
	static final int INFRASPECIFIC		 = (1 << 21);  // Is below a 'species'
    public
	static final int BARREN			     = (1 << 22);  // Does not contain a species?

    // Intended to match treemachine's list
	static final int SUPPRESSED_FLAGS =
                 (Taxonomy.HIDDEN |
                  Taxonomy.EXTINCT |
                  Taxonomy.BARREN |
                  Taxonomy.NOT_OTU |
                  Taxonomy.HYBRID |
                  Taxonomy.VIRAL |
                  Taxonomy.TATTERED | // deprecated
                  Taxonomy.FORMER_CONTAINER |
                  Taxonomy.INCERTAE_SEDIS_ANY);

    // ----- Flag inference -----

    // Clean up the flags from taxonomy.tsv file.  Putatively
    // inherited flags that aren't actually inherited need to be
    // promoted to properly asserted.

    void fixFlags() {
        int flaggers = 0;
        int counter = 0;

        int inferredOnly = (Taxonomy.SIBLING_HIGHER |
                            Taxonomy.SIBLING_LOWER |
                            Taxonomy.INFRASPECIFIC |
                            Taxonomy.BARREN);

        for (Taxon node : this.taxa()) {
            if (!node.isRoot()) {
                int wrongFlags = (node.inferredFlags &
                                  ~(node.properFlags | node.parent.properFlags | node.parent.inferredFlags));
                wrongFlags &= ~inferredOnly;
                if (wrongFlags != 0) {
                    node.addFlag(wrongFlags); // adds to properFlags
                    node.inferredFlags &= ~wrongFlags;
                    if (flaggers < 10)
                        System.out.format("| Fixed flags %s for %s in %s\n",
                                          Flag.flagsAsString(node),
                                          node,
                                          node.parent);
                    flaggers++;
                }
            }
        }
        if (flaggers > 0)
            System.out.format("| Flags fixed for %s nodes\n", flaggers);
    }

    // Propagate heritable flags from the top down.

	public void inferFlags() {
        int hcount = 0;
		for (Taxon root : this.roots())
			hcount += this.heritFlags(root, 0);
        System.out.format("| %s nodes inheriting flags\n", hcount);
        int bcount = 0;
		for (Taxon root : this.roots())
            bcount += this.analyzeBarren(root);
        System.out.format("| %s barren nodes\n", bcount);
        this.analyzeMinorRankConflicts();
        System.out.format("| finished inferring\n");
	}

	private int heritFlags(Taxon node, int inferredFlags) {
        int count = 0;
        boolean before = node.isHidden();
        if (node.inferredFlags != inferredFlags) {
            node.inferredFlags = inferredFlags;
            ++count;
        }
        if (node.name != null
            && node.name.equals("Ephedra gerardiana"))
            if (before != node.isHidden())
                System.out.format("* %s hidden %s -> %s\n", node, before, node.isHidden());

        int bequest = inferredFlags | node.properFlags;		// What the children inherit

		if (node.rank == Rank.SPECIES_RANK)
            bequest |= Taxonomy.INFRASPECIFIC;

		if (node.children != null) {
			for (Taxon child : node.children)
				count += heritFlags(child, bequest);
		}
        return count;
	}

    // Propagate flags from the bottom up.

	// 1. Set the (inferred) BARREN flag of any taxon that doesn't
	// contain anything at species rank or below.
	// 2. Propagate EXTINCT (inferred) upwards.

	static private int analyzeBarren(Taxon node) {
        int count = 0;
		boolean barren = true;      // No species?
		if (node.rank != null) {
			Rank rank = node.rank;
			if (rank != Rank.NO_RANK) {
				if (rank.level >= Rank.SPECIES_RANK.level)
					barren = false;
			}
		}
		if (node.rank == Rank.NO_RANK && !node.hasChildren())
			// The "no rank - terminal" case
			barren = false;
		if (node.hasChildren()) {
			boolean allextinct = true;	   // Any descendant is extant?
			for (Taxon child : node.children) {
				count += analyzeBarren(child);
				if ((child.inferredFlags & Taxonomy.BARREN) == 0) barren = false;
				if ((child.inferredFlags & Taxonomy.EXTINCT) == 0) allextinct = false;
			}
			if (allextinct) {
				node.inferredFlags |= EXTINCT;
				//if (node.sourceIds != null && node.sourceIds.get(0).prefix.equals("ncbi"))
					//;//System.out.format("| Induced extinct: %s\n", node);
			}
			// We could do something similar for all of the hidden-type flags
		}
		if (barren) {
			node.inferredFlags |= Taxonomy.BARREN;
            ++count;
		} else
			node.inferredFlags &= ~Taxonomy.BARREN;
        return count;
	}
	
	// NCBI only (not SILVA)
	public void analyzeOTUs() {
		for (Taxon root : this.roots())
			analyzeOTUs(root);	// mutates the tree
	}
	// analyzeOTUs: set taxon flags based on name, leading to dubious
	// taxa being hidden.
	// We use this for NCBI but not for SILVA.
    // (although SILVA still has to deal with INCERTAE_SEDIS, I believe).

	static void analyzeOTUs(Taxon node) {
		// Prepare for recursive descent
        if (node.name != null) {
            if (notOtuRegex.matcher(node.name).find()) 
                node.addFlag(NOT_OTU);
            if (hybridRegex.matcher(node.name).find()) 
                node.addFlag(HYBRID);
            if (viralRegex.matcher(node.name).find()) 
                node.addFlag(VIRAL);
        }

		// Recursive descent
		if (node.children != null)
			for (Taxon child : node.children)
				analyzeOTUs(child);
	}

	// Set SIBLING_HIGHER flags
	public void analyzeMinorRankConflicts() {
		for (Taxon root : this.roots())
			analyzeRankConflicts(root, false);  //SIBLING_HIGHER
	}

	// GBIF (and IF?) only
	public void analyzeMajorRankConflicts() {
        System.out.format("| looking for major rank conflicts\n");
		for (Taxon root : this.roots())
			analyzeRankConflicts(root, true);
        System.out.format("| ... done\n");
	}

	// Returns the node's rank (as an int).  In general the return
	// value should be >= parentRank, but occasionally ranks get out
	// of order when combinings taxonomies.

    // We need to do this for GBIF and IRMNG, but not for NCBI or SILVA.

	public static int analyzeRankConflicts(Taxon node, boolean majorp) {
		Integer m = -1;			// "no rank" = -1
		if (node.rank != Rank.NO_RANK)
            m = node.rank.level;
		int myrank = m;

		if (node.children != null) {

			int high = Integer.MAX_VALUE; // highest rank among all children
			int low = -1;
			Taxon highchild = null;

			// Preorder traversal
			// In the process, calculate rank of highest child
			for (Taxon child : node.children) {
				int rank = analyzeRankConflicts(child, majorp);
				if (rank >= 0) {  //  && !child.isHidden()  ??
					if (rank < high) { high = rank; highchild = child; }
					if (rank > low)	 low = rank;
				}
			}

            // e.g. low = 72, high = 23   (backwards... kingdom is 'higher' than order)

			if (high >= 0) {	// Any non-"no rank" children?

				// high is the highest (lowest-numbered) rank among all the children.
				// Similarly low.  If they're different we have a 'rank conflict'.
				// Some 'rank conflicts' are 'minor', others are 'major'.
				if (high < low) {
					// Suppose the parent is a class. We're looking at relative ranks of the children...
					// Two cases: order/family (minor), order/genus (major)
					for (Taxon child : node.children) {
						int chrank = child.rank.level;	   //e.g. family or genus
						if (chrank < 0) continue;		   // skip "no rank" children
						// we know chrank >= high
							//genus->genus, subfamily->genus
							if (majorp && (chrank / 100) > (high / 100)) {
                                // e.g. a genus that has a family [in an order] as a sibling
                                if (child.count() > 20000)
                                    System.out.format("!! %s %s sibling to %s %s\n",
                                                      child.rank.name, child, highchild.rank.name, highchild);
                                child.addFlag(MAJOR_RANK_CONFLICT);
                            } else if (chrank > high) {  // if lower rank than some sibling
								child.addFlag(SIBLING_HIGHER); //e.g. family with superfamily sibling
                            // tbd: should clear the flag when appropriate
						}
					}
				}
			}
			// Extra informational check.  See if ranks are inverted.
			if (majorp && high >= 0 && myrank > high)
				// The myrank == high case is weird too; there are about 200 of those.
				System.out.format("* Rank inversion : %s %s < %s %s\n",
								   highchild, highchild.rank.name,
								   node, node.rank.name);

		}
		return myrank;
	}

	static Pattern notOtuRegex =
		Pattern.compile(
						"\\buncultured\\b|" +
						"\\bunclassified\\b|" +
						"\\bunidentified\\b|" +
						"\\bunknown\\b|" +
						"\\bmetagenomes?\\b|" +	 // SILVA has a bunch of these
						"\\bother sequences\\b|" +
						"\\bartificial\\b|" +
						"\\blibraries\\b|" +
						"\\bbogus duplicates\\b|" +
						"\\bplasmids\\b|" +
						"\\binsertion sequences\\b|" +
						"\\bmidvariant sequence\\b|" +
						"\\btransposons\\b|" +
						"\\bunclassified sequences\\b|" +
						"\\bsp\\.$"
						);

    // × = U+00D7 MULTIPLICATION SIGN

	static Pattern hybridRegex = Pattern.compile(" x |\u00D7|\\bhybrid\\b");

	static Pattern viralRegex =
		Pattern.compile(
						"\\bviral\\b|" +
						"\\b[Vv]iroids\\b|" +
						"\\b[Vv]iruses\\b|" +
						"\\bvirus\\b"
						);

	static Pattern randomRegex =
		Pattern.compile(
                        // These never occur as containers
						"\\buncultured\\b|" +       // maybe these aren't OTUs!
						"\\bendophyte\\b|" +
						"\\bendophytic\\b" // word delimited
						);

    // Containers
	static Pattern environmentalRegex = Pattern.compile("\\bmycorrhizal samples\\b|" +
                                                        "\\benvironmental samples\\b");

    // Containers
	static Pattern incertae_sedisRegex =
		Pattern.compile("\\b[Ii]ncertae [Ss]edis\\b|" +
                        "\\b[Ii]ncertae[Ss]edis\\b|" +
						"\\b[Uu]nallocated\\b|" +
						"\\b[Uu]nclassified\\b|" +
                        "\\b[Mm]itosporic\\b");

    // ----- SELECTIONS -----

	// Select subtree rooted at a specified node

	public Taxonomy select(String designator) {
		return select(this.unique(designator));
	}
	
	public Taxonomy select(Taxon sel) {
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy(this.idspace);
            return finishSelection(tax2, this.select(sel, tax2));
		} else {
			System.err.println("** Missing or ambiguous selection name");
			return null;
		}
	}

    // Recursion
	// node is in source taxonomy, tax is the destination taxonomy ('union' or similar)
	static Taxon select(Taxon node, Taxonomy tax) {
		Taxon sam = tax.dupThoroughly(node, "select");
		if (node.children != null)
			for (Taxon child : node.children) {
				Taxon c = select(child, tax);
				c.setParent(sam, "select");
			}
		return sam;
	}

    Taxonomy finishSelection(Taxonomy tax2, Taxon selection) {
        System.out.println("| Selection has " + selection.count() + " taxa");
        tax2.addRoot(selection);
        this.copySelectedIds(tax2);
        // copy flags and sourceids
        for (Taxon node : tax2.taxa())
            if (node.id != null) {
                Taxon probe = selection.taxonomy.lookupId(node.id);
                if (probe != null) {
                    node.properFlags = probe.properFlags;
                }
            }
        // tax2.inferFlags();
        return tax2;
    }

    // Copy aliased ids from larger taxonomy to selected subtaxonomy, as appropriate
    // tax2 = the selection, this = where it came from

    void copySelectedIds(Taxonomy tax2) {
        int count = 0;
        for (String id : this.allIds()) {
            Taxon node = this.lookupId(id);
            if (!node.id.equals(id) && tax2.lookupId(node.id) != null) {
                tax2.addId(node, id);
                ++count;
            }
        }
        System.out.format("| copied %s id aliases\n", count);
    }

	// Select subtree rooted at a specified node, down to given depth

	public Taxonomy selectToDepth(String designator, int depth) {
		return selectToDepth(this.unique(designator), depth);
	}
	
	public Taxonomy selectToDepth(Taxon sel, int depth) {
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy(this.idspace);
            return finishSelection(tax2, selectToDepth(sel, tax2, depth));
		} else {
			System.err.println("** Missing or ambiguous name: " + sel);
			return null;
		}
	}

	Taxon selectToDepth(Taxon node, Taxonomy tax, int depth) {
		Taxon sam = tax.dupThoroughly(node, "selectToDepth");
		if (node.children != null && depth > 0)
			for (Taxon child : node.children) {
				Taxon c = selectToDepth(child, tax, depth-1);
				c.setParent(sam, "selectToDepth");
			}
		return sam;
	}

	// Select subtree, but only those nodes that are visible

	public Taxonomy selectVisible(String designator) {
		Taxon sel = this.taxon(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy(this.idspace);
			return finishSelection(tax2, selectVisible(sel, tax2));
		} else
			return null;
	}

	// Copy only visible (non-hidden) nodes

	public Taxon selectVisible(Taxon node, Taxonomy tax) {
		if (node.isHidden()) return null;
		Taxon sam = null;
		if (node.children == null)
			sam = tax.dupThoroughly(node, "selectVisible");
		else
			for (Taxon child : node.children) {
				Taxon c = selectVisible(child, tax);
				if (c != null) {
					if (sam == null)
						sam = tax.dupThoroughly(node, "selectVisible");
					c.setParent(sam, "selectVisible");
				}
			}
		return sam;
	}

	// Select a proportional sample of the nodes in a tree

	public Taxonomy sample(String designator, int count) {
		Taxon sel = this.unique(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy(this.idspace);
			Taxon sample = sample(sel, count, tax2);
			System.out.println("| Sample has " + sample.count() + " taxa");
			tax2.addRoot(sample);
			// TBD: synonyms ?
			return tax2;
		} else {
			System.err.println("** Missing or ambiguous name: " + designator);
			return null;
		}
	}

	// The nodes of the resulting tree are a subset of size k of the
	// nodes from the input tree, sampled proportionally.

	Taxon sample(Taxon node, int k, Taxonomy tax) {
		if (k <= 0) return null;

		// Assume k <= n.
		// We want to select k descendents out of the n that are available.

		int n = node.count() ;
	
		// n1 ranges from 0 up to n-1	 (the 1 is for the taxon itself)
		// k1 ranges from 0 up to k-1	 (the 1 is for sam)
		// k1 : n1 :: k2 : n2 :: k : n
		// k2 = (k*n) / n2
		int n1 = 1;
		int k1 = 1;

		List<Taxon> newChildren = new ArrayList<Taxon>();

		if (node.children != null) {
            List<Taxon> sorted = new ArrayList<Taxon>(node.children);
			java.util.Collections.sort(sorted, Taxon.compareNodesBySize);
			for (Taxon child : sorted) {

				if (k1 >= k) break;	   // redundant?

				int n2 = n1 + child.count();

				// Number of children we want to have after sampling
				// From k2 : n2 :: k : n
				int k2 = (n2 == n ? k : (k * n2) / n);
				if (false)	//node.name.contains("cellular life")
					System.out.println("? " + 
									   k1 + " : " + k2 + " : " + k + " :: " +
									   n1 + " : " + n2 + " : " + n + " " + child.name);
				int dk = k2 - k1;

				// Number of children to request
				Taxon c = sample(child, dk, tax);
				if (c != null) {
					newChildren.add(c);
					k1 += c.count();
				}
				n1 = n2;

			}
		}

		// Remove "knuckles"
		if (newChildren.size() == 1)
			return newChildren.get(0);

		Taxon sam = tax.dupThoroughly(node, "sample");
		for (Taxon c : newChildren)
			c.setParent(sam, "sample");
		return sam;
	}

	public Taxonomy chop(int m, int n) throws java.io.IOException {
		Taxonomy tax = new SourceTaxonomy(this.idspace);
		List<Taxon> cuttings = new ArrayList<Taxon>();
		Taxon root = null;
		for (Taxon r : this.roots()) root = r;	//bad kludge. uniroot assumed
		Taxon newroot = chop(root, m, n, cuttings, tax);
		tax.addRoot(newroot);
		System.out.format("| Cuttings: %s Residue: %s\n", cuttings.size(), newroot.count());

		// Temp kludge ... ought to be able to specify the file name
		String outprefix = "chop/";
		new File(outprefix).mkdirs();
		for (Taxon cutting : cuttings) {
			PrintStream out = 
				openw(outprefix + cutting.name.replaceAll(" ", "_") + ".tre");
			StringBuilder buf = new StringBuilder();
			Newick.appendNewickTo(cutting, Newick.USE_NAMES_AND_IDS, buf);
			out.print(buf.toString());
			out.close();
		}
		return tax;
	}

	// List of nodes for which N/3 < size <= N < parent size

	Taxon chop(Taxon node, int m, int n, List<Taxon> chopped, Taxonomy tax) {
		int c = node.count();
		Taxon newnode = tax.dupThoroughly(node, "sample");
		if (m < c && c <= n) {
			newnode.clobberName(newnode.name + " (" + node.count() + ")");
			chopped.add(node);
		} else if (node.children != null)
			for (Taxon child : node.children) {
				Taxon newchild = chop(child, m, n, chopped, tax);
				newchild.setParent(newnode, "chop");
			}
		return newnode;
	}

    // Make a duplicate here of a node in another taxonomy

    public Taxon dup(Taxon node, String reason) {
        Taxon newnode = dupWithoutId(node, reason);
        if (node.id != null)
            newnode.setId(node.id);
        node.copySynonymsTo(newnode);
        return newnode;
    }

    Taxon dupThoroughly(Taxon node, String reason) {
        Taxon newnode = dup(node, reason);
        node.copySourceIdsTo(newnode);
        newnode.properFlags = node.properFlags;
        return newnode;
    }

	// Duplicate single source node yielding a selection or union node

	public Taxon dupWithoutId(Taxon node, String reason) {

		Taxon newnode = new Taxon(this, node.name);

        // Compare this with transferProperties(newnode)
		newnode.rank = node.rank;

        // Retain placement flags, since the usual case is that we're
        // going to attach this in a pretty similar place
		newnode.properFlags = node.properFlags;

        // https://github.com/OpenTreeOfLife/reference-taxonomy/issues/36
        if (false && node.sourceIds != null)
            newnode.sourceIds = new ArrayList<QualifiedId>(node.sourceIds);

        // This might be the place to report on homonym creation

		return newnode;
	}

    // ----- FINISH UP BEFORE WRITING -----

    public void prepareForDump() throws IOException {
        Taxonomy tax = this;
        tax.placeBiggest();         // End of topology modifications
		tax.assignDummyIds();
        tax.reset();                // maybe unnecessary; depths
        Taxon biggest = tax.normalizeRoots().get(0);
        System.out.format("| prepare flags for dump\n");
        tax.inferFlags();           // infer BARREN & INFRASPECIFIC, and herit
        System.out.format("| Root is %s %s\n", biggest.name, Flag.toString(biggest.properFlags, 0));
    }

    // Idempotent
	public void deforestate() {
        List<Taxon> rootsList = this.normalizeRoots();
        if (rootsList.size() > 1) {
            Taxon biggest = rootsList.get(0);
            Taxon second = rootsList.get(1);
            System.out.format("| Deforesting: keeping %s (%s), 2nd biggest is %s (%s)\n",
                              biggest.name, biggest.count(), second, second.count());
            int flushed = 0;
            for (Taxon root : new HashSet<Taxon>(rootsList))
                if (!root.equals(biggest)) {
                    if (false)
                        root.prune("deforestate");
                    else
                        root.changeParent(biggest, Taxonomy.UNPLACED, "deforestate"); // removes from roots
                    ++flushed;
                }
            System.out.format("| Removed %s smaller trees\n", flushed);
        }
	}

    // Sort by size and make sure the biggest one is placed.  This is idempotent.
    public List<Taxon> normalizeRoots() {
        List<Taxon> rootsList = new ArrayList<Taxon>(this.forest.children);
		Collections.sort(rootsList, new Comparator<Taxon>() {
				public int compare(Taxon source, Taxon target) {
                    int foo = ((source.name != null && source.name.equals("life") ? 0 : 1) -
                               (target.name != null && target.name.equals("life") ? 0 : 1));
                    if (foo != 0)
                        return foo;
                    else
                        return target.count() - source.count();
				}
			});
        // Reporting
        if (rootsList.size() > 1) {
            Taxon biggest = rootsList.get(0);
            int count1 = biggest.count();
            Taxon second = rootsList.get(1);
            int count2 = second.count();
            if (rootsList.size() >= 2 && count1 < count2*500)
                System.out.format("* Nontrivial forest: biggest is %s, 2nd biggest is %s\n", count1, count2);
        }
        return rootsList;
    }

    public void placeBiggest() {
        Taxon biggest = this.unique("life");
        if (biggest == null || !biggest.isRoot())
            biggest = this.normalizeRoots().get(0);  // sort the roots list
        biggest.properFlags &= ~Taxonomy.SUPPRESSED_FLAGS;
    }

    // ----- Id assignment -----

    void assignDummyIds() {
        long minid = -1L;
		for (Taxon node : this.taxa())
			if (node.id == null) {
                String id;
                do {
                    id = Long.toString(minid--);
                } while (lookupId(id) != null);
				node.setId(id);
			}
    }

	// ----- NEWICK STUFF -----

	public void loadNewick(String filename) throws IOException {
		BufferedReader br = Taxonomy.fileReader(filename);
        Taxon root = Newick.newickToNode(new java.io.PushbackReader(br), this);
		this.addRoot(root);
        root.properFlags = 0;   // not unplaced
	}

	// Render this taxonomy as a Newick string.
	// This feature is very primitive and only intended for debugging purposes!

	public String toNewick() {
        return this.toNewick(Newick.USE_NAMES_AND_IDS);
    }

	public String toNewick(boolean useIds) {
        return toNewick(useIds ? Newick.USE_NAMES_AND_IDS : Newick.USE_NAMES);
    }

	public String toNewick(int mode) {
		StringBuilder buf = new StringBuilder();
		for (Taxon root: this.roots()) {
			Newick.appendNewickTo(root, mode, buf);
			buf.append(";");
		}
		return buf.toString();
	}

	public void dumpNewick(String outfile) throws java.io.IOException {
		PrintStream out = openw(outfile);
		out.print(this.toNewick());
		out.close();
	}

    // ----- REPORTS -----

    // random reporting

	void investigateHomonyms() {
		int homs = 0;
		int sibhoms = 0;
		int cousinhoms = 0;
		for (String name : this.allNames()) {
			List<Node> nodes = this.lookup(name);
            if (nodes == null)
                throw new RuntimeException(String.format("bug %s %s", name, this));
			if (nodes.size() > 1) {
				boolean homsp = false;
				boolean sibhomsp = false;
				boolean cuzhomsp = false;
				for (Node n1node: nodes) {
                    Taxon n1 = n1node.taxon();
					for (Node n2node: nodes) {
                        Taxon n2 = n2node.taxon();
						if (compareTaxa(n1, n2) < 0 &&
                            n1.name != null &&
                            n2.name != null &&
							n1.name.equals(name) &&
							n2.name.equals(name)) {
							homsp = true;
							if (n1.parent == n2.parent)
								sibhomsp = true;
							else if (!n1.isRoot() && !n2.isRoot() &&
                                     n1.parent != null &&
                                     n2.parent != null &&
									 n1.parent.parent == n2.parent.parent)
								cuzhomsp = true;
							break;
						}
					}
                }
				if (sibhomsp) ++sibhoms;
				if (cuzhomsp) ++cousinhoms;
				if (homsp) ++homs;
			}
		}
		if (homs > 0) {
			System.out.println("| " + homs + " homonyms, of which " +
							   cousinhoms + " name cousin taxa, " +
							   sibhoms + " name sibling taxa");
		}
	}

	public void parentChildHomonymReport() {
		for (Taxon node : this.taxa())
			if (!node.isRoot()
                && node.parent.name != null
				&& node.parent.name.equals(node.name))
				System.out.format("%s\t%s%s\n",
								  node.name,
								  node.getSourceIdsString(),
								  (node.children == null ?
								   "\tNO CHILDREN" :
								   (node.children.size() == 1 ?
									"\tONE CHILD" :
									(node.parent.children.size() == 1 ?
									 // This is the important case
									 "\tNO COUSINS" : "")))
								  );
	}

	// ea vs. ae
	// ioideae vs. oidae
	// ceae vs. cae
	// deae vs. dae

	public void spellingVariationReport() {
		for (String name : this.allNames())
			if (name != null
                && name.endsWith("ae")) {
				List<Node> sheep = this.lookup(name);
				if (sheep == null) continue;
				{	boolean win = false;
					for (Node n : sheep) if (n.taxonNameIs(name)) win = true;
					if (!win) continue;	  }

				String namea = name.substring(0,name.length()-2) + "ea";
				List<Node> goats = this.lookup(namea);
				if (goats == null) continue;
				{	boolean win = false;
					for (Node n : goats) if (n.taxonNameIs(namea)) win = true;
					if (!win) continue;	  }

				if (sheep != null && goats != null) {
					for (Node shnode : sheep) {
                        Taxon sh = shnode.taxon();
						if (sh.name.equals(name)) {
							for (Node gtnode : goats) {
                                Taxon gt = gtnode.taxon();
								if (gt.name.equals(namea)) {
									String shp = (!sh.isRoot() ? sh.parent.name : "(root)");
									String gtp = (!gt.isRoot() ? gt.parent.name : "(root)");
									System.out.format("%s in %s from %s ",
													  sh.name,
													  shp,
													  sh.getSourceIdsString());
									System.out.format(" %s%s in %s from %s\n",
													  (shp.equals(gtp) ? "* " : ""),
													  gt.name,
													  gtp,
													  gt.getSourceIdsString());
								}
                            }
                        }
                    }
				}
			}
	}

    // Show differences between two taxonomies!

	public void dumpDifferences(Taxonomy other, String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		reportDifferences(other, out);
		out.close();
	}

	public void reportDifferences(Taxonomy other) {
		reportDifferences(other, System.out);
	}

	// other would typically be an older version of the same taxonomy.
	public void reportDifferences(Taxonomy other, PrintStream out) {
		out.format("uid\twhat\tname\tsource\tfrom\tto\n");
		for (Taxon node : other.taxa()) {
			Taxon newnode = this.lookupId(node.id);
			if (newnode == null) {
				List<Node> newnodes = this.lookup(node.name);
				if (newnodes == null)
					reportDifference("removed", node, null, null, out);
				else if (newnodes.size() != 1)
					reportDifference("multiple-replacements", node, null, null, out);
				else {
					newnode = newnodes.get(0).taxon();
					if (newnode.name.equals(node.name))
						reportDifference("changed-id-?", node, null, null, out);
					else
						reportDifference("synonymized", node, null, newnode, out);
				}
			} else {
				if (!newnode.name.equals(node.name)) {
					// Does the new taxonomy retain the old name as a synonym?
					Taxon retained = this.unique(node.name);
					if (retained != null && retained.id.equals(newnode.id))
						reportDifference("renamed-keeping-synonym", node, null, newnode, out);
					else
						reportDifference("renamed", node, null, newnode, out);
				}
				if (newnode.isRoot() != node.isRoot()) {
                    if (newnode.isRoot() && !node.isRoot())
                        reportDifference("raised-to-root", node, node.parent, null, out);
                    else if (!newnode.isRoot() && node.isRoot())
                        reportDifference("no-longer-root", node, null, newnode.parent, out);
                } else if (!newnode.isRoot() && !newnode.parent.id.equals(node.parent.id))
                    reportDifference("moved", node, node.parent, newnode.parent, out);
				if (newnode.isHidden() && !node.isHidden())
					reportDifference("hidden", node, null, null, out);
				else if (!newnode.isHidden() && node.isHidden())
					reportDifference("exposed", node, null, null, out);
			}
		}
		for (Taxon newnode : this.taxa()) {
			Taxon node = other.lookupId(newnode.id);
			if (node == null) {
				if (other.lookup(newnode.name) != null)
					reportDifference("changed-id?", newnode, null, null, out);
				else {
					List<Node> found = this.lookup(newnode.name);
					if (found != null && found.size() > 1)
						reportDifference("added-homonym", newnode, null, null, out);
					else
						reportDifference("added", newnode, null, null, out);
				}
			}
		}

	}

	void reportDifference(String what, Taxon node, Taxon oldParent, Taxon newParent, PrintStream out) {
		out.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\n", node.id, what, node.name,
				   node.getSourceIdsString(), 
				   (oldParent == null ? "" : oldParent.name),
				   (newParent == null ? "" : newParent.name),
				   node.divisionName());
	}

	static Pattern binomialPattern = Pattern.compile("^[A-Z][a-z]+ [a-z]+$");
	static Pattern monomialPattern = Pattern.compile("^[A-Z][a-z]*$");

	public void adHocReport() {
		adHocReport1(this.taxon("cellular organisms"));
		adHocReport1(this.taxon("Bacteria", "cellular organisms"));
		adHocReport1(this.taxon("Archaea", "cellular organisms"));
		adHocReport1(this.taxon("Bacillariophyceae"));
	}

	interface Filter {
		boolean passes(Taxon x);
	}

	public void adHocReport1(Taxon node) {
		System.out.format("%s\n", node.name);
		System.out.format("Taxa: %s\n", node.count());
		int tips = tipCount(node, new Filter() {
				public boolean passes(Taxon node) { return true; }
			});
		System.out.format("Tips: %s\n", tips);
		int hidden = tipCount(node, new Filter() {
				public boolean passes(Taxon node) { return node.isHidden(); }
			});
		System.out.format(" Visible: %s, hidden: %s\n", tips - hidden, hidden);
		int binomial = tipCount(node, new Filter() {
				public boolean passes(Taxon node) { return binomialPattern.matcher(node.name).find(); }
			});
		int monomial = tipCount(node, new Filter() {
				public boolean passes(Taxon node) { return monomialPattern.matcher(node.name).find(); }
			});
		System.out.format(" Binomial: %s, monomial: %s, other: %s\n\n",
						  binomial, monomial, tips - (binomial + monomial));
	}

	int tipCount(Taxon node, Filter filter) {
		if (node.children == null)
			return filter.passes(node) ? 1 : 0;
		else {
			int total = 0;
			for (Taxon child : node.children)
				total += tipCount(child, filter);
			return total;
		}
	}

	// ----- METHODS FOR USE IN JYTHON SCRIPTS -----

	public Taxon taxon(String name) {
		return taxon(name, null, null, true);
	}

	// Look up a taxon by name or unique id.  Name must be unique in the taxonomy.
	public Taxon maybeTaxon(String name) {
        return taxon(name, null, null, false);
    }

	public Taxon taxonThatContains(String name, String descendant) {
        return taxon(name, null, descendant, true);
    }

	// Look up a taxon by name or unique id.  Name must be unique in the taxonomy.
	public Taxon maybeTaxonThatContains(String name, String descendant) {
        return taxon(name, null, descendant, false);
    }

    List<Taxon> nodesToTaxa(List<Node> nodes) {
        List<Taxon> taxa = new ArrayList<Taxon>();
        for (Node node : nodes) {
            Taxon taxon = node.taxon();
            if (!taxa.contains(taxon) && ((taxon.properFlags & Taxonomy.FORMER_CONTAINER) == 0))
                taxa.add(taxon);
        }
        return taxa;
    }

	public Taxon taxon(String name, String ancestor, String descendant, boolean windy) {
		List<Node> nodes = this.lookup(name);
        if (nodes != null) {
            List<Taxon> candidates = nodesToTaxa(nodes);

            if (ancestor != null)
                candidates = filterByAncestor(candidates, ancestor);
            if (descendant != null)
                candidates = filterByDescendant(candidates, descendant);

			if (candidates == null || candidates.size() == 0) {
                if (windy) {
                    if (ancestor != null)
                        System.err.format("** No such taxon: %s in %s (%s)\n", name, ancestor, this.idspace);
                    else if (descendant != null)
                        System.err.format("** No such taxon: %s containing %s (%s)\n", name, descendant, this.idspace);
                    else
                        System.err.format("** No such taxon: %s (in %s)\n", name, this.idspace);
                }
                return null;
            }

			if (candidates.size() == 1) {
                Taxon result = candidates.get(0);
                if (!result.name.equals(name))
                    System.out.format("| Warning: taxon %s was referenced using synonym %s (%s)\n", result, name, this.idspace);
				return result;
            }

            // Try filtering out synonyms
            List<Taxon> candidates2 = new ArrayList<Taxon>();
            for (Taxon cand : candidates)
                if (cand.name.equals(name))
                    candidates2.add(cand);
			if (candidates2.size() == 1)
				return candidates2.get(0);

            // That didn't work.
            if (windy) {
                System.err.format("** Ambiguous taxon name: %s (%s)\n", name, this.idspace);
                for (Taxon taxon : candidates) {
                    String uniq = taxon.uniqueName();
                    if (uniq.equals("")) uniq = taxon.name;
                    System.err.format("**   %s = %s in %s\n",
                                      uniq,
                                      taxon,
                                      (taxon.getDivision() == null ?
                                       "-" :
                                       taxon.getDivision().name));
                }
            }
            return null;
		} else {
            Taxon probe = this.lookupId(name);
            if (windy && probe == null)
                System.err.format("** No taxon found in %s with this name or id: %s\n", this.idspace, name);
            return probe;
        }
	}

	public Taxon taxon(String name, String context) {
        return taxon(name, context, null, true);
	}

	public Taxon maybeTaxon(String name, String context) {
        return taxon(name, context, null, false);
	}

	// Test case: Valsa
	public List<Taxon> filterByAncestor(String taxonName, String ancestorName) {
		List<Node> smallNodes = this.lookup(taxonName);
		if (smallNodes == null) return null;
        List<Taxon> result = filterByAncestor(nodesToTaxa(smallNodes), ancestorName);
        return result.size() == 0 ? null : result;
    }

	List<Taxon> filterByAncestor(List<Taxon> smallTaxa, String ancestorName) {
        if (smallTaxa == null) return null;
		List<Node> bigNodes = this.lookup(ancestorName);
		if (bigNodes == null) return null;
		List<Taxon> bigTaxa = nodesToTaxa(bigNodes);

		List<Taxon> result = new ArrayList<Taxon>(1);
		for (Taxon smallTaxon : smallTaxa) {
			// Follow ancestor chain to see whether this node is an ancestor
			for (Taxon chain = smallTaxon.parent; chain != null; chain = chain.parent)
				if (bigTaxa.contains(chain)) {
					result.add(smallTaxon);
					break;
				}
		}
		return result;
	}

	public List<Taxon> filterByDescendant(String taxonName, String descendantName) {
		List<Node> bigNodes = this.lookup(taxonName);
		if (bigNodes == null) return null;
        List<Taxon> result = filterByDescendant(nodesToTaxa(bigNodes), descendantName);
		return result.size() == 0 ? null : result;
    }

	List<Taxon> filterByDescendant(List<Taxon> bigTaxa, String descendantName) {
        if (bigTaxa == null) return null;
		List<Node> smallNodes = this.lookup(descendantName);
		if (smallNodes == null) return null;

		List<Taxon> result = new ArrayList<Taxon>(1);
		for (Node smallNode : smallNodes) {
			// Follow ancestor chain to see whether this node is an ancestor
			for (Taxon chain = smallNode.taxon().parent; chain != null; chain = chain.parent)
				if (bigTaxa.contains(chain)) {
                    if (!result.contains(chain))
                        result.add(chain);
					break;
				}
		}
		return result;
	}

    // For use from jython code.  Result is added as a root.
	public Taxon newTaxon(String name, String rankname, String sourceIds) {
		if (this.lookup(name) != null)
			System.out.format("* Warning: A taxon by the name of %s already exists\n", name);
		Taxon t = new Taxon(this, name);
		if (rankname != null && !rankname.equals("no rank"))
			t.rank = Rank.getRank(rankname);
        if (sourceIds != null && sourceIds.length() > 0)
            t.setSourceIds(sourceIds);
        t.taxonomy.addRoot(t);
		return t;
	}

	public void describe() {
		System.out.format("%s ids, %s roots, %s names\n",
						  this.idIndex.size(),
						  this.rootCount(),
						  this.nameIndex.size());
	}

    // Check invariants that are supposed to hold for Taxonomy objects.
    // Useful for debugging.

    public void check() {
        Set<Taxon> all = new HashSet<Taxon>();
        for (Taxon taxon : this.taxa()) {
            if (taxon.prunedp)
                System.err.format("** check: Pruned taxon found in hierarchy: %s in %s\n", taxon, taxon.parent);
            else
                all.add(taxon);
        }

        // Ensure every named node is reachable...
        for (Node node : allNamedNodes()) {
            Taxon taxon = node.taxon();
            if (!all.contains(taxon)) {
                if (node == taxon)
                    System.err.format("** check: Named taxon not in hierarchy: %s in %s\n", taxon, taxon.parent);
                else if (taxon.prunedp)
                    System.err.format("** check: Pruned taxon found in name index: %s = %s in %s\n",
                                      node.name, taxon, taxon.parent);
                else
                    System.err.format("** check: Synonym %s taxon not in hierarchy: %s in %s\n",
                                      node.name, taxon, taxon.parent);
                all.add(taxon);
            }
        }

        // Ensure every identified node is reachable...
        for (Taxon taxon : idIndex.values()) {
            if (!all.contains(taxon)) {
                if (taxon.prunedp) {
                    if (taxon.id != null)
                        // foo. we don't have a list of ids for use in removeFromIdIndex.
                        System.err.format("** check: Pruned taxon is in identifier index: %s\n", taxon);
                } else {
                    System.err.format("** check: Identified taxon not in hierarchy: %s\n", taxon);
                    all.add(taxon);
                }
            }
        }

        // Ensure all nodes are indexed ...
        for (Taxon node : all) {
            if (node.name != null && !node.prunedp) {
                Collection<Node> nodes = this.lookup(node.name);
                if (nodes == null)
                    System.err.format("** check: Named node not in name index: %s\n", node);
                else if (!nodes.contains(node))
                    System.err.format("** check: Named node is not in name index: %s\n", node);
            }
            if (node.id != null) {
                Taxon taxon = lookupId(node.id);
                if (taxon == null)
                    System.err.format("** check: Identified node not in id index: %s\n", node);
                else if (taxon != node)
                    System.err.format("** check: Identified node collision with id index: %s %s\n", node,  taxon);
            }
        }

        // Check parent/child links...
        for (Taxon node : all) {
            if (node.parent == null) {
                if (node != forest && !node.prunedp)
                    System.err.format("** check: null parent %s\n", node);
            } else if (!node.parent.children.contains(node))
                System.err.format("** check: not in parent's children list: %s %s\n",
                                  node, node.parent);
            if (node.hasChildren()) {
                boolean gotit = false;
                for (Taxon child : node.children)
                    if (child.isPlaced()) {
                        gotit = true;
                        break;
                    }
                if (!gotit)
                    System.err.format("** check: no placed children: %s\n", node);
            }
        }
    }

	// ----- Utilities -----

    // public because Registry
	public static BufferedReader fileReader(File filename) throws IOException {
		return
			new BufferedReader(new InputStreamReader(new FileInputStream(filename),
													 "UTF-8"));
	}

	public static BufferedReader fileReader(String filename) throws IOException {
		return
			new BufferedReader(new InputStreamReader(new FileInputStream(filename),
													 "UTF-8"));
	}

    // public because Registry
	public static PrintStream openw(String filename) throws IOException {
		PrintStream out;
		if (filename.equals("-")) {
			out = System.out;
			System.out.println("Writing to standard output");
		} else {
			out = new java.io.PrintStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(filename)),
										  false,
										  "UTF-8");

			// PrintStream(OutputStream out, boolean autoFlush, String encoding)

			// PrintStream(new OutputStream(new FileOutputStream(filename, "UTF-8")))

			System.out.println("Writing " + filename);
		}
		return out;
	}

	// Compute the inverse of the name->node map.
	public Map<Taxon, Collection<String>> makeSynonymIndex() {
		Map<Taxon, Collection<String>> nameMap = new HashMap<Taxon, Collection<String>>();
		for (String name : this.allNames())
			for (Node nodenode : this.lookup(name)) {
                Taxon node = nodenode.taxon();
				Collection<String> names = nameMap.get(node);  // of this node
				if (names == null) {
					names = new ArrayList<String>(1);
					nameMap.put(node, names);
				}
				names.add(name);
			}
		return nameMap;
	}

    // Called just before alignment.  Clears depth cache.
    // Prepare for subsumption checks
	public void reset() {
		for (Taxon root: this.roots())
            root.resetDepths();
	}

    // Overridden in subclass

	public boolean sameness(Taxon node1, Taxon node2, boolean whether, boolean setp) {
		Taxon unode, snode;
        if (node1.taxonomy != this || node2.taxonomy != this) {
			System.err.format("** One of the two nodes must be already mapped to the union taxonomy: %s %s\n",
							  node1, node2);
			return false;
        } else
            return whetherLumped(node1, node2, whether, setp);
    }

    public boolean whetherLumped(Taxon node1, Taxon node2, boolean whether, boolean setp) {
        if (whether) {
            if (node1 == node2)
                return true;
            if (setp)
                return node1.absorb(node2); // adds synonym, too
            else
                return false;
        } else {
            if (node1 != node2)
                return true;
            if (setp) {
                System.err.format("** Cannot un-lump %s\n", node1);
                return false;
            }
            else
                return false;
        }
    }

    // Event logging

	public boolean markEvent(String tag) { // formerly startReport
        if (this.eventLogger != null)
            return this.eventLogger.markEvent(tag);
        else return false;
	}

    public boolean markEvent(String tag, Taxon node) {
        if (this.eventLogger != null)
            return this.eventLogger.markEvent(tag, node);
        else return false;
    }

	// This gets overridden in a subclass.
    // Deforestate would have already been called, if it was going to be
	public void dump(String outprefix, String sep) throws IOException {
        System.out.format("-- Dumping to %s\n", outprefix);
        this.prepareForDump();
        new InterimFormat(this).dump(outprefix, sep);
	}

	public void dump(String outprefix) throws IOException {
        dump(outprefix, "\t|\t");
    }

    // Overridden by UnionTaxonomy
    public void dumpExtras(String outprefix) throws IOException {
        ;
    }

}
// end of class Taxonomy

/*
  -----

  Following are notes collected just before this program was written.
  They are no longer current.

   Stephen's instructions
	https://github.com/OpenTreeOfLife/taxomachine/wiki/Loading-the-OTToL-working-taxonomy
	  addtax
		TaxonomyLoader.addDisconnectedTaxonomyToGraph
	  graftbycomp
		TaxonomyComparator.compareGraftTaxonomyToDominant
		  search for matching nodes is bottom up

   NCBI
	python ../../taxomachine/data/process_ncbi_taxonomy_taxdump.py F \
		   ../../taxomachine/data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP ncbi.processed
	  ~/Downloads/taxdump.tar.gz   25054982 = 25,054,982 bytes
	  data/nodes.dmp  etc.
	  data/ncbi.processed  (34M)
	  1 minute 9 seconds

   GBIF
	~/Downloads/gbif/taxon.txt
	python ../../taxomachine/data/process_gbif_taxonomy.py \
		   ~/Downloads/gbif/taxon.txt \
		   ../../taxomachine/data/gbif/ignore.txt \
		   gbif.processed
	  4 minutes 55 seconds

   OTTOL
   https://bitbucket.org/blackrim/avatol-taxonomies/downloads#download-155949
   ~/Downloads/ottol/ottol_dump_w_uniquenames_preottol_ids	(158M)
					 ottol_dump.synonyms			
	 header line:
		uid	|	parent_uid	|	name	|	rank	|	source	|	sourceid
	 |	sourcepid	|	uniqname	|	preottol_id	|	
	 source = ncbi or gbif

   PREOTTOL
   ~/a/NESCent/preottol/preottol-20121112.processed
*/
