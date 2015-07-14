/*

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

  Some people think having multiple classes in one file is terrible
  programming style...	I'll split this into multiple files when I'm
  ready to do so; currently it's much easier to work with in this
  form.

  In jython, say:
	 from org.opentreeoflife.smasher import Smasher

*/

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
import java.net.URI;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;
import org.semanticweb.skos.*;
import org.semanticweb.skosapibinding.SKOSManager;
import org.semanticweb.skosapibinding.SKOSFormatExt;

public abstract class Taxonomy implements Iterable<Taxon> {
    public Map<String, List<Taxon>> nameIndex = new HashMap<String, List<Taxon>>();
	public Map<String, Taxon> idIndex = new HashMap<String, Taxon>();
    Taxon forest = new Taxon(this, "");
	protected String tag = null;
	String[] header = null;

	Integer sourcecolumn = null;
	Integer sourceidcolumn = null;
	Integer infocolumn = null;
	Integer flagscolumn = null;
	JSONObject metadata = null;
	int taxid = -1234;	  // kludge

	Taxonomy() { }

	public String toString() {
		return "(taxonomy " + this.getTag() + ")";
	}

	public abstract UnionTaxonomy promote();

    public abstract UnionTaxonomy target();
    public abstract void setTarget(UnionTaxonomy target);

    // Nodes

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
        this.forest.addChild(node);
        node.addFlag(Taxonomy.UNPLACED);
    }

    public int rootCount() {
        if (this.forest.children == null)
            return 0;
        else
            return this.forest.children.size();
    }

	public int count() {
        return this.forest.count() - 1;
	}

	public int tipCount() {
        return this.forest.tipCount();
	}

	// Iterate over all nodes reachable from roots

	public Iterator<Taxon> iterator() {
		final List<Iterator<Taxon>> its = new ArrayList<Iterator<Taxon>>();
		its.add(this.roots().iterator());
		final Taxon[] current = new Taxon[1]; // locative
		current[0] = null;

		return new Iterator<Taxon>() {
			public boolean hasNext() {
				if (current[0] != null) return true;
				while (true) {
					if (its.size() == 0) return false;
					if (its.get(0).hasNext()) return true;
					else its.remove(0);
				}
			}
			public Taxon next() {
				Taxon node = current[0];
				if (node != null)
					current[0] = null;
				else
					// Caller has previously called hasNext(), so we're good to go
					// Was: .get(its.size()-1)
					node = its.get(0).next();
				if (node.children != null)
					its.add(node.children.iterator());
				return node;
			}
			public void remove() { throw new UnsupportedOperationException(); }
		};
	}

	public Taxon lookupId(String id) {
        return this.idIndex.get(id);
	}

    // Names

	public List<Taxon> lookup(String name) {
		return this.nameIndex.get(name);
	}

	public Taxon unique(String name) {
		List<Taxon> probe = this.nameIndex.get(name);
		// TBD: Maybe rule out synonyms?
		if (probe != null && probe.size() == 1)
			return probe.get(0);
		else 
			return this.lookupId(name);
	}

    // compare addSynonym
	void addToIndex(Taxon node) {
		String name = node.name;
		List<Taxon> nodes = this.nameIndex.get(name);
		if (nodes == null) {
			nodes = new ArrayList<Taxon>(1); //default is 10
			this.nameIndex.put(name, nodes);
		} else if (nodes.contains(node))
			return;
		nodes.add(node);
        if (nodes.size() == 75) {
            System.err.format("** %s is the 75th to have the name %s\n", node, name);
            // Taxon.backtrace();  - always happens in loadTaxonomyProper
        }
	}

	static int globalTaxonomyIdCounter = 1;

	public String getTag() {
		if (this.tag == null) this.setTag();
		if (this.tag == null) {
			if (this.taxid < 0)
				this.taxid = globalTaxonomyIdCounter++;
			return "tax" + taxid;
		} else
			return this.tag;
	}

	void setTag() {
		if (this.tag != null) return;
		List<Taxon> probe = this.lookup("Caenorhabditis elegans");
		if (probe != null) {
			String id = probe.get(0).id;
			if (id.equals("6239")) this.tag = "ncbi";
			else if (id.equals("2283683")) this.tag = "gbif";
			else if (id.equals("395048")) this.tag = "ott";
			else if (id.equals("100968828")) this.tag = "aux"; // preottol
			else if (id.equals("4722")) this.tag = "nem"; // testing
		}
		// TEMPORARY KLUDGE
		if (this.tag == null) {
			List<Taxon> probe2 = this.lookup("Asterales");
			if (probe2 != null) {
				String id = probe2.get(0).id;
				if (id.equals("4209")) this.tag = "ncbi";
				if (id.equals("414")) this.tag = "gbif";
				if (id.equals("1042120")) this.tag = "ott";
			}
		}
	}

	// Most rootward node in this taxonomy having a given name
	Taxon highest(String name) { // See pin()
		List<Taxon> l = this.lookup(name);
		if (l == null) return null;
		Taxon best = null, otherbest = null;
		int depth = 1 << 30;
		for (Taxon node : l) {
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
		}
		return best;
	}

	void investigateHomonyms() {
		int homs = 0;
		int sibhoms = 0;
		int cousinhoms = 0;
		for (String name : nameIndex.keySet()) {
			List<Taxon> nodes = this.nameIndex.get(name);
			if (nodes.size() > 1) {
				boolean homsp = false;
				boolean sibhomsp = false;
				boolean cuzhomsp = false;
				for (Taxon n1: nodes)
					for (Taxon n2: nodes) {
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

    /* Nodes with more children come before nodes with fewer children.
       Nodes with shorter ids come before nodes with longer ids.
       */

	static int compareTaxa(Taxon n1, Taxon n2) {
		int q1 = (n1.children == null ? 0 : n1.children.size());
		int q2 = (n2.children == null ? 0 : n2.children.size());
		if (q1 != q2) return q2 - q1;
        if (n1.id == null) return (n2.id == null ? 0 : 1);
        if (n2.id == null) return -1;
        // id might or might not look like an integer
        int z = n1.id.length() - n2.id.length();
        if (z != 0) return z;
        else return n1.id.compareTo(n2.id);
	}

	static Pattern tabVbarTab = Pattern.compile("\t\\|\t?");
	static Pattern tabOnly = Pattern.compile("\t");

	// DWIMmish - does Newick if string starts with paren, otherwise
	// loads from directory

	public static SourceTaxonomy getTaxonomy(String designator) throws IOException {
		SourceTaxonomy tax = new SourceTaxonomy();
		if (designator.startsWith("("))
			tax.addRoot(tax.newickToNode(designator));
		else {
			if (!designator.endsWith("/")) {
				System.err.println("Taxonomy designator should end in / but doesn't: " + designator);
				designator = designator + "/";
			}
			System.out.println("--- Reading " + designator + " ---");
			tax.loadTaxonomy(designator);
		}
        tax.postProcessTaxonomy();
		return tax;
	}

    // Perform a variety of post-intake tasks, independent of which
    // parser was used

    public void postProcessTaxonomy() {
        // Fix up the flags - putatively inherited flags that aren't need to get fixed
        int flaggers = 0;
        for (Taxon node: this)
            if (!node.isRoot()) {
                // this isn't right
                int wrongFlags = node.inheritedFlags & ~node.parent.properFlags;
                if (wrongFlags != 0) {
                    node.addFlag(wrongFlags);
                    node.inheritedFlags &= ~wrongFlags;
                    flaggers++;
                }
            }
        if (flaggers > 0)
            System.out.format("| Flags fixed for %s nodes\n", flaggers);

		if (this.rootCount() == 0)
			System.err.println("** No root nodes!");

        // Elide incertae-sedis-like containers
		this.analyzeContainers();

        // Foo
		this.elideRedundantIntermediateTaxa();

		// Don't do setDerivedFlags() - barren and extinct lead to problems

        // Some statistics & reports
		this.investigateHomonyms();
        int nroots = this.rootCount();
        int ntips = this.tipCount();
        int nnodes = this.count();
        System.out.format("| %s roots + %s internal + %s tips = %s total\n",
                          nroots, nnodes - (ntips + nroots), ntips, nnodes);

    }

	public static SourceTaxonomy getTaxonomy(String designator, String tag)
		throws IOException {
		SourceTaxonomy tax = getTaxonomy(designator);
		tax.tag = tag;
		return tax;
	}

	public static SourceTaxonomy getNewick(String filename) throws IOException {
		SourceTaxonomy tax = new SourceTaxonomy();
		BufferedReader br = Taxonomy.fileReader(filename);
        Taxon root = tax.newickToNode(new java.io.PushbackReader(br));
		tax.addRoot(root);
        root.properFlags = 0;   // not unplaced
		tax.investigateHomonyms();
		tax.assignNewIds(0);	// foo
		return tax;
	}

	public static SourceTaxonomy getNewick(String filename, String tag) throws IOException {
		SourceTaxonomy tax = getNewick(filename);
		tax.tag = tag;
		return tax;
	}

	// load | dump all taxonomy files

	public void loadTaxonomy(String dirname) throws IOException {
		this.loadMetadata(dirname + "about.json");
		this.loadTaxonomyProper(dirname + "taxonomy.tsv");
		this.loadSynonyms(dirname + "synonyms.tsv");
	}

	// This gets overridden in the UnionTaxonomy class
    // Deforestate would have already been called, if it was going to be
	public void dump(String outprefix, String sep) throws IOException {
		new File(outprefix).mkdirs();
        this.prepareForDump(outprefix, sep);
		this.dumpNodes(this.roots(), outprefix, sep);
		this.dumpSynonyms(outprefix + "synonyms.tsv", sep);
		this.dumpMetadata(outprefix + "about.json");
		this.dumpHidden(outprefix + "hidden.tsv");
	}

    void prepareForDump(String outprefix, String sep) throws IOException {
		this.assignNewIds(0);
        this.reset();
		this.analyze();         // set some flags
        this.placeBiggest();    // End of topology modifications
        this.setDerivedFlags(); // barren - end of proper flag settings
        Taxon biggest = this.forest.children.get(0);
        System.out.format("| Root is %s %s\n", biggest.name, biggest.properFlags);
        this.propagateFlags();  // set inherited flags
    }

	public void dump(String outprefix) throws IOException {
        this.dump(outprefix, "\t|\t");  //backward compatible
    }

	// load | dump metadata

	void loadMetadata(String filename) throws IOException {
		BufferedReader fr;
		try {
			fr = Taxonomy.fileReader(filename);
		} catch (java.io.FileNotFoundException e) {
			return;
		}
		JSONParser parser = new JSONParser();
		try {
			Object obj = parser.parse(fr);
			JSONObject jsonObject = (JSONObject) obj;
			if (jsonObject == null)
				System.err.println("!! Opened file " + filename + " but no contents?");
			else {
				this.metadata = jsonObject;

				Object prefix = jsonObject.get("prefix");
				if (prefix != null) {
					System.out.println("prefix is " + prefix);
					this.tag = (String)prefix;
				}
			}

		} catch (ParseException e) {
			System.err.println(e);
		}
		fr.close();
	}

	static String NO_RANK = null;

	abstract void dumpMetadata(String filename) throws IOException;

	// load | dump taxonomy file

	void loadTaxonomyProper(String filename) throws IOException {
		BufferedReader br = Taxonomy.fileReader(filename);
		String str;
		int row = 0;
		int normalizations = 0;

        Pattern pat = null;
		String suffix = null;	// Java loses somehow.  I don't get it

		Map<Taxon, String> parentMap = new HashMap<Taxon, String>();

		while ((str = br.readLine()) != null) {
            if (pat == null) {
                String[] parts = tabOnly.split(str + "\t!");	 // Java loses
                if (parts[1].equals("|")) {
                    pat = tabVbarTab;
					suffix = "\t|\t!";
                } else {
                    pat = tabOnly;
					suffix = "\t!";
				}
            }
			String[] parts = pat.split(str + suffix);	 // Java loses
			if (parts.length < 3) {
				System.out.println("Bad row: " + row + " has only " + parts.length + " parts");
			} else {
				if (row == 0) {
					if (parts[0].equals("uid")) {
						Map<String, Integer> headerx = new HashMap<String, Integer>();
						for (int i = 0; i < parts.length; ++i)
							headerx.put(parts[i], i);
						// id | parentid | name | rank | ...
						this.header = parts; // Stow it just in case...
						this.sourcecolumn = headerx.get("source");
						this.sourceidcolumn = headerx.get("sourceid");
						this.infocolumn = headerx.get("sourceinfo");
						this.flagscolumn = headerx.get("flags");
						// this.preottolcolumn = headerx.get("preottol_id");
						continue;
					} else
						System.out.println("! No header row - saw " + parts[0]);
				}
				String lastone = parts[parts.length - 1];
				// The following is residue from an annoying bug that
				// I never tracked down and that seems to have fixed
				// itself
				if (lastone.endsWith("!") && (parts.length < 4 ||
											  lastone.length() > 1))
					System.err.println("I don't get it: [" + lastone + "]");

				String id = parts[0];
				Taxon oldnode = this.lookupId(id);
				if (oldnode != null) {
					System.err.format("** Duplicate id definition: %s %s\n", id, oldnode.name);
                } else if (parts.length <= 4) {
                    System.err.format("** Too few columns in row: id = %s\n", id);
                } else {
                    String rawname = parts[2];
                    String name = normalizeName(rawname);
					Taxon node = new Taxon(this, name);
                    initTaxon(node,
                              id,
                              name,
                              parts[3], // rank
                              (this.flagscolumn != null ? parts[this.flagscolumn] : ""),
                              parts);
                    if (!name.equals(rawname)) {
                        addSynonym(rawname, node);
                        ++normalizations;
                    }

                    // Delay until after all ids are defined
                    String parentId = parts[1];
                    if (parentId.equals("null") || parentId.equals("not found"))
                        parentId = "";
                    parentMap.put(node, parentId);
                }
            }
			++row;
			if (row % 500000 == 0)
				System.out.println(row);
		}
		br.close();
		if (normalizations > 0)
			System.out.format("| %s names normalized\n", normalizations);

        // Look up all the parent ids and store parent pointers in the nodes

		Set<String> seen = new HashSet<String>();
        int orphans = 0;
		for (Taxon node : parentMap.keySet()) {
			String parentId = parentMap.get(node);
            if (parentId.length() == 0)
                this.addRoot(node);
            else {
                Taxon parent = this.lookupId(parentId);
                if (parent == null) {
                    if (!seen.contains(parentId)) {
                        ++orphans;
                        seen.add(parentId);
                    }
                    this.addRoot(node);
                } else if (parent == node) {
                    System.err.format("** Taxon is its own parent: %s %s\n", node.id, node.name);
                    this.addRoot(node);
                } else if (parent.descendsFrom(node)) {
                    System.err.format("** Cycle detected in input taxonomy: %s %s\n", node, parent);
                    this.addRoot(node);
                } else {
                    parent.addChild(node);
                }
            }
		}
        if (orphans > 0)
			System.out.format("| %s unrecognized parent ids, %s nodes that have them\n",
                              seen.size(), orphans);

        Taxon life = this.unique("life");
        if (life != null) life.properFlags = 0;  // not unplaced

        int total = this.count();
        if (row != total)
            // Shouldn't happen
            System.err.println(this.getTag() + " is ill-formed: " +
                               row + " rows, but only " + 
                               total + " reachable from roots");
	}

	// Populate fields of a Taxon object from fields of row of taxonomy file
	// parts = fields from row of dump file
	void initTaxon(Taxon node, String id, String name, String rank, String flags, String[] parts) {
        node.setId(id); // stores into this.idIndex
        node.setName(name);

        if (flags.length() > 0)
            Flag.parseFlags(flags, node);

        if (rank.length() == 0 || rank.startsWith("no rank") ||
            rank.equals("terminal") || rank.equals("samples"))
            rank = Taxonomy.NO_RANK;
        else if (Taxonomy.ranks.get(rank) == null) {
            System.err.println("!! Unrecognized rank: " + rank + " " + node.id);
            rank = Taxonomy.NO_RANK;
        }
        node.rank = rank;

		if (this.infocolumn != null) {
			if (parts.length <= this.infocolumn)
				System.err.println("Missing sourceinfo column: " + node.id);
			else {
				String info = parts[this.infocolumn];
				if (info != null && info.length() > 0)
					node.setSourceIds(info);
			}
		}

		else if (this.sourcecolumn != null &&
			this.sourceidcolumn != null) {
            // Legacy of OTT 1.0 days
            String sourceTag = parts[this.sourcecolumn];
            String idInSource = parts[this.sourceidcolumn];
            if (sourceTag.length() > 0 && idInSource.length() > 0)
                node.addSourceId(new QualifiedId(sourceTag, idInSource));
		}
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
	private static String normalizeName(String str) {
		str = Normalizer.normalize(str, Normalizer.Form.NFD);
		str = DIACRITICS_AND_FRIENDS.matcher(str).replaceAll("");
		return str;
	}
	/*
	void dumpName(String name) {
		for (int i = 0; i < name.length(); ++i)
			System.out.format("%4x ", (int)(name.charAt(i)));
		System.out.println();
	}
	*/

	void elideRedundantIntermediateTaxa() {
		Set<Taxon> dubious = new HashSet<Taxon>();
		for (Taxon node : this) {
			if (!node.isRoot()
				&& node.parent.children.size() == 1
				&& node.children != null)
				if (!node.isPlaced()
                    || node.parent.name.equals(node.name))
					dubious.add(node);
		}
        int i = 0;
		for (Taxon node: dubious) {
            if (++i < 10)
                System.out.format("| Eliding %s in %s, %s children\n",
                                  node.name,
                                  node.parent.name,
                                  (node.children == null ?
                                   "no" :
                                   node.children.size())
                                  );
            else if (i == 10)
                System.out.format("...\n");
			node.elide();
		}
	}

	// Fold sibling homonyms together into single taxa.
	// Optional step.

	public void smush() {
		List<Runnable> todo = new ArrayList<Runnable>();

		// Smush taxa that differ only in id.
		int siblingHomonymCount = 0;
		for (final Taxon node : this.idIndex.values())

			if (node.name != null && node.isRoot())

				// Search for a homonym node that can replace node
				for (final Taxon other : this.lookup(node.name)) {

					if (other.name.equals(node.name) &&
						node.parent == other.parent &&
						(node.rank == Taxonomy.NO_RANK ?
						 other.rank == Taxonomy.NO_RANK :
						 node.rank.equals(other.rank)) &&
						compareTaxa(other, node) < 0) {

						// node and other are sibling homonyms.
						// deprecate node, replace it with other.

						if (++siblingHomonymCount < 10)
							System.err.println("| Smushing" +
											   " sibling homonym " + node.id +
											   " => " + other.id +
											   ", name = " + node.name);
						else if (siblingHomonymCount == 10)
							System.err.println("...");

						// There might be references to this id from the synonyms file
						final Taxonomy tax = this;
						todo.add(new Runnable() //ConcurrentModificationException
							{
								public void run() {
									if (node.children != null)
										for (Taxon child : new ArrayList<Taxon>(node.children))
											// might create new sibling homonyms...
											child.changeParent(other);
									node.prune("smush");  // removes name from index
									tax.idIndex.put(node.id, other);
									other.addSource(node);
								}});
						// No need to keep searching for appropriate homonym, node
						// has been flushed, try next homonym in the set.
						break;
					}
				}

		for (Runnable r : todo) r.run();

		if (siblingHomonymCount > 0)
			System.err.println("" + siblingHomonymCount + " sibling homonyms folded");

		// Fix parent pointers when they point to pruned sibling homonyms

		for (Taxon node : this.idIndex.values())
			if (!node.isRoot()) {
				Taxon replacement = this.lookupId(node.parent.id);
				if (replacement != null && replacement != node.parent) {
					if (false)
					System.err.println("Post-smushing kludge: " +
									   node.parent.id + " => " +
									   replacement.id);
                    node.changeParent(replacement);
				}
			}
	}

	void dumpNodes(Iterable<Taxon> nodes, String outprefix, String sep) throws IOException {
		PrintStream out = Taxonomy.openw(outprefix + "taxonomy.tsv");

		out.format("uid%sparent_uid%sname%srank%ssourceinfo%suniqname%sflags%s\n",
				   // 0  1		     2	   3     4	   		 5		   6
                   sep, sep, sep, sep, sep, sep, sep
					);

		for (Taxon node : nodes)
			if (!node.prunedp)
				dumpNode(node, out, true, sep);
		out.close();
	}

    static final String SKOS_BASE_URI = "http://purl.obolibrary.org/obo/OTT_";

	// From Ryan Scherle at a NESCent Informatics hack day -
	void dumpNodesSkos(Collection<Taxon> nodes, String outprefix) throws IOException {
            try {
                SKOSManager manager = new SKOSManager();
                SKOSDataset dataset = manager.createSKOSDataset(URI.create(SKOS_BASE_URI));
                SKOSDataFactory df = manager.getSKOSDataFactory();
                SKOSConceptScheme conceptScheme = df.getSKOSConceptScheme(URI.create(SKOS_BASE_URI));
                SKOSEntityAssertion entityAssertion1 = df.getSKOSEntityAssertion(conceptScheme);
                
                List<SKOSChange> addAssertions = new ArrayList<SKOSChange>();
                addAssertions.add (new AddAssertion(dataset, entityAssertion1));
                
                manager.applyChanges(addAssertions);
                
                
                for (Taxon node : nodes) {
                    if (node == null) {
                        System.err.println("null in nodes list!?" );
                    }
                    else if (!node.prunedp) {
                        dumpNodeSkos(node, manager, dataset, true);
                    }
                }
                
                // save the dataset to a file in RDF/XML format
                File outFile = new File(outprefix + "taxonomy.skos");
                System.err.println("Writing " + outFile.getPath());
                manager.save(dataset, SKOSFormatExt.RDFXML, URI.create("file:" + outFile.getAbsolutePath()));
                
            } catch (SKOSCreationException e) {
                e.printStackTrace();
            } catch (SKOSChangeException e) {
                e.printStackTrace(); 
            } catch (SKOSStorageException e) {
                e.printStackTrace(); 
            }
        }


	// Recursive!
	void dumpNodeSkos(Taxon node, SKOSManager manager, SKOSDataset dataset, boolean rootp) throws SKOSChangeException {
            SKOSDataFactory df = manager.getSKOSDataFactory();
            SKOSConceptScheme conceptScheme = df.getSKOSConceptScheme(URI.create(SKOS_BASE_URI));
            List<SKOSChange> addAssertions = new ArrayList<SKOSChange>();
            
            // node concept
            SKOSConcept nodeConcept = df.getSKOSConcept(URI.create(SKOS_BASE_URI + "#" + node.id));
            SKOSEntityAssertion nodeAssertion = df.getSKOSEntityAssertion(nodeConcept);
            addAssertions.add (new AddAssertion(dataset, nodeAssertion));

            // node is in scheme
            SKOSObjectRelationAssertion nodeRelation = df.getSKOSObjectRelationAssertion(nodeConcept, df.getSKOSInSchemeProperty(), conceptScheme);
            addAssertions.add (new AddAssertion(dataset, nodeRelation));

            // node parent
            if(!node.isRoot() && node.parent.id != null) {
                SKOSConcept nodeParentConcept = df.getSKOSConcept(URI.create(SKOS_BASE_URI + "#" + node.parent.id));
                nodeRelation = df.getSKOSObjectRelationAssertion(nodeConcept, df.getSKOSBroaderProperty(), nodeParentConcept);
                addAssertions.add (new AddAssertion(dataset, nodeRelation));
            }
            
            // node name 
            /*
              /// commented out because there were problems creating the literal value for the name
              
                SKOSEntity a = nodeConcept;
                SKOSAnnotation b = df.getSKOSAnnotation(df.getSKOSPrefLabelProperty().getURI(), df.getSKOSUntypedConstant(node.name, "en"));
                SKOSAnnotationAssertion nodeLabelAnnotation = df.getSKOSAnnotationAssertion(a, b);
                addAssertions.add (new AddAssertion(dataset, nodeLabelAnnotation));
            }
            */


            /*
              // other items from dumpNode that need to be output as SKOS
              
		// 3. rank:
		out.print((node.rank == Taxonomy.NO_RANK ? "no rank" : node.rank) + "\t|\t");
                
		// 4. source information
		// comma-separated list of URI-or-CURIE
		out.print(node.getSourceIdsString() + "\t|\t");
                
		// 5. uniqname
		out.print(node.uniqueName() + "\t|\t");
                
		// 6. flags
		// (node.mode == null ? "" : node.mode)
		Taxonomy.printFlags(node, out);
            */

            
            // add all assertions to the document
            manager.applyChanges(addAssertions);
            
            
            if (node.children != null) {
                for (Taxon child : node.children) {
                    if (child == null)
                        System.err.println("null in children list!? " + node);
                    else
                        dumpNodeSkos(child, manager, dataset, false);
                }
            }
	}
        
	// Recursive!
	void dumpNode(Taxon node, PrintStream out, boolean rootp, String sep) {
		// 0. uid:
		out.print((node.id == null ? "?" : node.id) + sep);
		// 1. parent_uid:
		out.print(((node.taxonomy.hasRoot(node) || rootp) ? "" : node.parent.id)  + sep);
		// 2. name:
		out.print((node.name == null ? "?" : node.name)
				  + sep);
		// 3. rank:
		out.print((node.rank == Taxonomy.NO_RANK ?
				   (node.children == null ?
					"no rank - terminal" :
					"no rank") :
				   node.rank) + sep);

		// 4. source information
		// comma-separated list of URI-or-CURIE
		out.print(node.getSourceIdsString() + sep);

		// 5. uniqname
		out.print(node.uniqueName() + sep);

		// 6. flags
		// (node.mode == null ? "" : node.mode)
		Flag.printFlags(node.properFlags,
                        node.inheritedFlags,
                        out);
		out.print(sep);
		// was: out.print(((node.flags != null) ? node.flags : "") + sep);

		out.println();

		if (node.children != null)
			for (Taxon child : node.children) {
				if (child == null)
					System.err.println("null in children list!? " + node);
				else
					dumpNode(child, out, false, sep);
			}
	}

	// load | dump synonyms

	void loadSynonyms(String filename) throws IOException {
		BufferedReader fr;
		try {
			fr = fileReader(filename);
		} catch (java.io.FileNotFoundException e) {
			fr = null;
		}
		if (fr != null) {
			// BufferedReader br = new BufferedReader(fr);
			BufferedReader br = fr;
			int count = 0;
			String str;
			int syn_column = 1;
			int id_column = 0;
			int type_column = Integer.MAX_VALUE;
			int row = 0;
			int losers = 0;
            Pattern pat = null;
			while ((str = br.readLine()) != null) {

				if (pat == null) {
					String[] parts = tabOnly.split(str + "!");	 // Java loses
					if (parts[1].equals("|"))
						pat = tabVbarTab;
					else
						pat = tabOnly;
				}

				String[] parts = pat.split(str);
				// uid | name | type | ? |
				// 36602	|	Sorbus alnifolia	|	synonym	|	|	
				if (parts.length >= 2) {
					if (row == 0) {
						Map<String, Integer> headerx = new HashMap<String, Integer>();
						for (int i = 0; i < parts.length; ++i)
							headerx.put(parts[i], i);
						Integer o2 = headerx.get("uid");
						if (o2 == null) o2 = headerx.get("id");
						if (o2 != null) {
							id_column = o2;
							Integer o1 = headerx.get("name");
							if (o1 != null) syn_column = o1;
							Integer o3 = headerx.get("type");
							if (o3 != null) type_column = o3;
							continue;
						}
					}
					String id = parts[id_column];
					String syn = parts[syn_column];
					String type = (type_column < parts.length ?
								   parts[type_column] :
								   "");
					Taxon node = this.lookupId(id);
					if (type.equals("in-part")) continue;
					if (type.equals("includes")) continue;
					if (type.equals("type material")) continue;
					if (type.endsWith("common name")) continue;
					if (type.equals("authority")) continue;	   // keep?
					if (node == null) {
						if (false && ++losers < 10)
							System.err.println("Identifier " + id + " unrecognized for synonym " + syn);
						else if (losers == 10)
							System.err.println("...");
						continue;
					}
					if (!node.name.equals(syn)) {
						addSynonym(syn, node);
						++count;
					}
				}
			}
			br.close();
			if (count > 0)
				System.out.println("| " + count + " synonyms");
		}
	}

	// Returns true if a change was made
    // compare addToIndex

	public boolean addSynonym(String syn, Taxon node) {
        if (node.name.equals(syn))
            return true;
		if (node.taxonomy != this)
			System.err.println("!? Synonym for a node that's not in this taxonomy: " + syn + " " + node);
		List<Taxon> nodes = this.nameIndex.get(syn);
		if (nodes == null) {
			nodes = new ArrayList<Taxon>(1);
			this.nameIndex.put(syn, nodes);
            nodes.add(node);
            return true;
		} else {
            // We don't want to create a homonym.
			for (Taxon n : nodes)
                if (n == node)
                    return true; // already present (shouldn't happen)
            if (false)
                // There are gazillions of these warnings.
                System.err.format("Making %s a synonym of %s would create a homonym\n", syn, node);
            return false;       // shouldn't happen
		}
	}

	void dumpSynonyms(String filename, String sep) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("name\t|\tuid\t|\ttype\t|\tuniqname\t|\t");
		for (String name : this.nameIndex.keySet()) {
            boolean primaryp = false;
            boolean synonymp = false;
			for (Taxon node : this.nameIndex.get(name)) {
				if (!node.prunedp)
                    if (node.name.equals(name))
                        // Never emit a synonym when the name is the primary name of something
                        primaryp = true;
                    else {
                        synonymp = true;
                        String uniq = node.uniqueName();
                        if (uniq.length() == 0) uniq = node.name;
                        if (node.id == null) {
                            if (!node.isRoot()) {
                                System.out.format("** Synonym for node with no id: %s\n", node.name);
                                node.show();
                            }
                        } else
                            out.println(name + sep +
                                        node.id + sep +
                                        "" + sep + // type, could be "synonym" etc.
                                        name + " (synonym for " + uniq + ")" +
                                        sep);
                    }
                }
            if (false && primaryp && synonymp)
                System.err.println("** Synonym in parallel with primary: " + name);
            }
		out.close();
	}

	void dumpHidden(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		int count = 0;
		for (Taxon node : this) {
			if (node.isHidden()) {
				++count;
				out.format("%s\t%s\t%s\t%s\t", node.id, node.name, node.getSourceIdsString(),
						   node.divisionName());
				Flag.printFlags(node.properFlags, node.inheritedFlags, out);
				out.println();
			}
		}
		out.close();
		System.out.format("| %s hidden taxa\n", count);
	}

	/*
	   flags are:

	   nototu # these are non-taxonomic entities that will never be made available for mapping to input tree nodes. we retain them so we can inform users if a tip is matched to one of these names
	   unclassified # these are "dubious" taxa that will be made available for mapping but will not be included in synthesis unless they exist in a mapped source tree
	   incertaesedis # these are (supposed to be) recognized taxa whose position is uncertain. they are generally mapped to some ancestral taxon, with the implication that a more precise placement is not possible (yet). shown in the synthesis tree whether they are mapped to a source tree or not
	   hybrid # these are hybrids
	   viral # these are viruses

	   rules listed below, followed by keywords for that rule.
	   rules should be applied to any names matching any keywords for that rule.
	   flags are inherited (conservative approach), except for "incertaesedis", which is a taxonomically explicit case that we can confine to the exact relationship (hopefully).

	   # removed keywords
	   scgc # many of these are within unclassified groups, and will be treated accordingly. however there are some "scgc" taxa that are within recognized groups. e.g. http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?mode=Undef&id=939181&lvl=3&srchmode=2&keep=1&unlock . these should be left in. so i advocate removing this name and force-flagging all children of unclassified groups.

	   ==== rules

	   # rule 1: flag taxa and their descendents `nototu`
	   # note: many of these are children of the "other sequences" container, but if we treat the cases individually then we will also catch any instances that may occur elsewhere (for some bizarre reason).
	   # note: any taxa flagged `nototu` need not be otherwise flagged.
	   other sequences
	   metagenome
	   artificial
	   libraries
	   bogus duplicates
	   plasmids
	   insertion sequences
	   midvariant sequence
	   transposons
	   unknown
	   unidentified
	   unclassified sequences
	   * .sp # apply this rule to "* .sp" taxa as well

	   # rule 6: flag taxa and their descendents `hybrid`
	   x

	   # rule 7: flag taxa and their descendents `viral`
	   viral
	   viroids
	   Viruses
	   viruses
	   virus

	   # rule 3+5: if the taxon has descendents, 
	   #			 flag descendents `unclassified` and elide,
	   #			 else flag taxon `unclassified`.
	   # (elide = move children to their grandparent and mark as 'not_otu')
	   mycorrhizal samples
	   uncultured
	   unclassified
	   endophyte
	   endophytic

	   # rule 2: if the taxon has descendents, 
	   #			 flag descendents `unclassified` and elide,
	   #			 else flag taxon 'not_otu'.
	   environmental

	   # rule 4: flag direct children `incertae_sedis` and elide taxon.
	   incertae sedis
	*/

	// Each Taxon has two parallel sets of flags: 
	//	 proper - applies particularly to this node
	//	 inherited - applies to this node because it applies to an ancestor
	//	   (where in some cases the ancestor may later be 'elided' so
	//	   not an ancestor any more)

	// NCBI only (not SILVA)
	public void analyzeOTUs() {
		for (Taxon root : this.roots())
			analyzeOTUs(root);	// mutates the tree
	}

	// Find and elide incertae sedis containers (move their children up one level)
	public void analyze() {
		for (Taxon root : this.roots())
			analyzeRankConflicts(root, false);  //SIBLING_HIGHER
	}

	// GBIF (and IF?) only
	public void analyzeMajorRankConflicts() {
		for (Taxon root : this.roots())
			analyzeRankConflicts(root, true);
	}

	public void setDerivedFlags() {
		for (Taxon root : this.roots())
			this.analyzeBarren(root);
	}

	// Work in progress - don't laugh - will be converting flag set
	// representation from int to EnumSet<Flag>

	// Set during assembly
	static final int FORCED_VISIBLE		 = (1 << 1);	  // combine using |
	static final int EDITED				 = (1 << 2);			  // combine using |
	static final int EXTINCT			 = (1 << 3);	  // combine using |
	static final int HIDDEN				 = (1 << 4);	  // combine using &

	// NCBI - individually troublesome - not sticky - combine using &  ? no, | ?
	static final int HYBRID				 = (1 << 6);
	static final int VIRAL				 = (1 << 7);

	// Varieties of incertae sedis
	static final int MAJOR_RANK_CONFLICT = (1 << 8);
	static final int UNCLASSIFIED		 = (1 << 9);
	static final int ENVIRONMENTAL		 = (1 << 10);
	static final int INCERTAE_SEDIS		 = (1 << 11);
	static final int UNPLACED			 = (1 << 0);   // children of paraphyletic taxa
	static final int INCONSISTENT		 = (1 << 16);  // paraphyletic taxa
	static final int MERGED  			 = (1 << 15);  // merge-compatible taxa
	static final int NOT_OTU			 = (1 << 5);
	static final int INCERTAE_SEDIS_ANY	 = 
        (MAJOR_RANK_CONFLICT | UNCLASSIFIED | ENVIRONMENTAL |
         INCERTAE_SEDIS | UNPLACED | INCONSISTENT | MERGED | NOT_OTU);


	// Australopithecus
	static final int SIBLING_HIGHER		 = (1 << 13);
	static final int SIBLING_LOWER		 = (1 << 12);  // deprecated

	// Is below a 'species'
	// Unconditional ?
	static final int INFRASPECIFIC		 = (1 << 14);

	// Propagated upward
	static final int BARREN			     = (1 << 17);

	static final int SERIOUS		     = (1 << 18);

    // Intended to match treemachine's list
	static final int HIDDEN_FLAGS =
                 (Taxonomy.HIDDEN |
                  Taxonomy.EXTINCT |
                  Taxonomy.BARREN |
                  Taxonomy.NOT_OTU |
                  Taxonomy.HYBRID |
                  Taxonomy.VIRAL |
                  Taxonomy.INCERTAE_SEDIS_ANY);

	// FLUSH ME
	static Pattern commaPattern = Pattern.compile(",");
	void parseFlags(String flags, Taxon node) {
		if (flags.contains("extinct"))
			// kludge. could be _direct or _inherited
			node.addFlag(Taxonomy.EXTINCT);
	}

	// Returns the node's rank (as an int).  In general the return
	// value should be >= parentRank, but occasionally ranks get out
	// of order when combinings taxonomies.

	static int analyzeRankConflicts(Taxon node, boolean majorp) {
		Integer m = -1;			// "no rank" = -1
		if (node.rank != null) {
			m = ranks.get(node.rank);
			if (m == null) {
				System.err.println("Unrecognized rank: " + node);
				m = -1;
			}
		}
		int myrank = m;
		node.rankAsInt = myrank;

		if (node.children != null) {

			int highrank = Integer.MAX_VALUE; // highest rank among all children
			int lowrank = -1;
			Taxon highchild = null;

			// Preorder traversal
			// In the process, calculate rank of highest child
			for (Taxon child : node.children) {
				int rank = analyzeRankConflicts(child, majorp);
				if (rank >= 0) {  //  && !child.isHidden()  ??
					if (rank < highrank) { highrank = rank; highchild = child; }
					if (rank > lowrank)	 lowrank = rank;
				}
			}

			if (lowrank >= 0) {	// Any non-"no rank" children?

				// highrank is the highest (lowest-numbered) rank among all the children.
				// Similarly lowrank.  If they're different we have a 'rank conflict'.
				// Some 'rank conflicts' are 'minor', others are 'major'.
				if (highrank < lowrank) {
					// Suppose the parent is a class. We're looking at relative ranks of the children...
					// Two cases: order/family (minor), order/genus (major)
					int x = highrank / 100;		  //e.g. order
					for (Taxon child : node.children) {
						int sibrank = child.rankAsInt;	   //e.g. family or genus
						if (sibrank < 0) continue;		   // skip "no rank" children
						// we know sibrank >= highrank
						if (sibrank < lowrank)	// if child is higher rank than some sibling...
							// a family that has a sibling that's a genus
							// SIBLING_LOWER means 'has a sibling with lower rank'
                            // deprecated
							if (!majorp)
								child.addFlag(SIBLING_LOWER); //e.g. family with genus sibling
						if (sibrank > highrank) {  // if lower rank than some sibling
							int y = (sibrank + 99) / 100; //genus->genus, subfamily->genus
							if (y > x && majorp)
								// e.g. a genus that has a family [in an order] as a sibling
								child.addFlag(MAJOR_RANK_CONFLICT);
							if (!majorp)
								child.addFlag(SIBLING_HIGHER); //e.g. genus with family sibling
						}
					}
				}
			}
			// Extra informational check.  See if ranks are inverted.
			if (majorp && highrank >= 0 && myrank > highrank)
				// The myrank == highrank case is weird too; there are about 200 of those.
				System.err.println("** Ranks out of order: " +
								   node + " " + node.rank + " has child " +
								   highchild + " " + highchild.rank);
		}
		return myrank;
	}

	// analyzeOTUs: set taxon flags based on name, leading to dubious
	// taxa being hidden.
	// We use this for NCBI but not for SILVA.

	static void analyzeOTUs(Taxon node) {
		// Prepare for recursive descent
		if (notOtuRegex.matcher(node.name).find()) 
			node.addFlag(NOT_OTU);
		if (hybridRegex.matcher(node.name).find()) 
			node.addFlag(HYBRID);
		if (viralRegex.matcher(node.name).find()) 
			node.addFlag(VIRAL);

		// Recursive descent
		if (node.children != null)
			for (Taxon child : node.children)
				analyzeOTUs(child);
	}

    // Elide 'incertae sedis'-like containers, encoding the
    // incertaesedisness of their children in flags.
    // This gets called for every taxonomy, not just NCBI, on ingest.

	public void analyzeContainers() {
        analyzeContainers(forest);
	}

	public static void analyzeContainers(Taxon node) {

		// Recursive descent
		if (node.children != null)
			for (Taxon child : new ArrayList<Taxon>(node.children))
				analyzeContainers(child);

		int flag = 0;
		if (unclassifiedRegex.matcher(node.name).find()) // Rule 3+5
			flag = UNCLASSIFIED;  // includes uncultured
		else if (environmentalRegex.matcher(node.name).find()) // Rule 3+5
			flag = ENVIRONMENTAL;
		else if (incertae_sedisRegex.matcher(node.name).find()) // Rule 3+5
			flag = INCERTAE_SEDIS;

		// After (compare elide())
		if (flag != 0) {
			// Splice the node out of the hierarchy, but leave it as a
			// residual terminal non-OTU node.
			if (node.children != null && !node.isRoot())
				for (Taxon child : new ArrayList<Taxon>(node.children))
                    // changeParent sets properFlags
					child.changeParent(node.parent, flag);
            // Splice the node out of the tree
			node.addFlag(NOT_OTU);
            node.prune("not_otu");
		}
	}

    // Propagate heritable flags tipward.

	public void propagateFlags() {
		for (Taxon root : this.roots())
			this.propagateFlags(root, 0);
	}

	static void propagateFlags(Taxon node, int inheritedFlags) {
		node.inheritedFlags = inheritedFlags;
		if (node.children != null) {
			int bequest = inheritedFlags | node.properFlags;		// What the children inherit
			for (Taxon child : node.children)
				propagateFlags(child, bequest);
		}
	}

	// 1. Set the INFRASPECIFIC flag of any taxon that is a species or has
	// one below it.  
	// 2. Set the BARREN flag of any taxon that doesn't
	// contain anything at species rank or below.
	// 3. Propagate EXTINCT upwards.

	static void analyzeBarren(Taxon node) {
		boolean infraspecific = false;
		boolean barren = true;      // No species?
		if (node.rank != null) {
			Integer rank = ranks.get(node.rank);
			if (rank != null) {
				if (rank == SPECIES_RANK)
					infraspecific = true;
				if (rank >= SPECIES_RANK)
					barren = false;
			}
		}
		if (node.rank == null && node.children == null)
			// The "no rank - terminal" case
			barren = false;
		if (node.children != null) {
			boolean allextinct = true;	   // Any descendant is extant?
			for (Taxon child : node.children) {
				if (infraspecific)
					child.addFlag(INFRASPECIFIC);
				else
					child.properFlags &= ~INFRASPECIFIC;
				analyzeBarren(child);
				if ((child.properFlags & BARREN) == 0) barren = false;
				if ((child.properFlags & EXTINCT) == 0) allextinct = false;
			}
			if (allextinct) {
				node.addFlag(EXTINCT);
				if (node.sourceIds != null && node.sourceIds.get(0).prefix.equals("ncbi"))
					;//System.out.format("| Induced extinct: %s\n", node);
			}
			// We could do something similar for all of the hidden-type flags
		}
		if (barren)
			node.addFlag(BARREN);
		else
			node.properFlags &= ~BARREN;
	}
	
	static Pattern notOtuRegex =
		Pattern.compile(
						"\\bunidentified\\b|" +
						"\\bunknown\\b|" +
						"\\bmetagenome\\b|" +	 // SILVA has a bunch of these
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

	static Pattern hybridRegex = Pattern.compile("\\bx\\b|\\bhybrid\\b");

	static Pattern viralRegex =
		Pattern.compile(
						"\\bviral\\b|" +
						"\\b[Vv]iroids\\b|" +
						"\\b[Vv]iruses\\b|" +
						"\\bvirus\\b"
						);

	static Pattern unclassifiedRegex =
		Pattern.compile(
						"\\bmycorrhizal samples\\b|" +
						"\\buncultured\\b|" +
						"\\b[Uu]nclassified\\b|" +
						"\\bendophyte\\b|" +
						"\\bendophytic\\b"
						);

	static Pattern environmentalRegex = Pattern.compile("\\benvironmental\\b");

	static Pattern incertae_sedisRegex =
		Pattern.compile("\\b[Ii]ncertae [Ss]edis\\b|" +
                        "\\b[Ii]ncertae[Ss]edis\\b|" +
						"[Uu]nallocated|\\b[Mm]itosporic\\b");

	static String[][] rankStrings = {
		{"domain",
		 "superkingdom",
		 "kingdom",
		 "subkingdom",
		 "infrakingdom",		// worms
		 "superphylum"},
		{"phylum",
		 "subphylum",
		 "infraphylum",			// worms
		 "subdivision",			// worms
		 "superclass"},
		{"class",
		 "subclass",
		 "infraclass",
		 "superorder"},
		{"order",
		 "suborder",
		 "infraorder",
		 "parvorder",
		 "section",				// worms
		 "subsection",			// worms
		 "superfamily"},
		{"family",
		 "subfamily",
		 "supertribe",			// worms
		 "tribe",
		 "subtribe"},
		{"genus",
		 "subgenus",
		 "species group",
		 "species subgroup"},
		{"species",
		 "infraspecificname",
		 "subspecies",
		 "variety",
		 "varietas",
		 "subvariety",
		 "forma",
		 "subform",
		 "samples"},
	};

	static Map<String, Integer> ranks = new HashMap<String, Integer>();

	static void initRanks() {
		for (int i = 0; i < rankStrings.length; ++i) {
			for (int j = 0; j < rankStrings[i].length; ++j)
				ranks.put(rankStrings[i][j], (i+1)*100 + j*10);
		}
		ranks.put("no rank", -1);
		SPECIES_RANK = ranks.get("species");
	}

	static int SPECIES_RANK = -1; // ranks.get("species");

	// Select subtree rooted at a specified node

	public Taxonomy select(String designator) {
		return select(this.unique(designator));
	}
	
	public Taxonomy select(Taxon sel) {
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			tax2.tag = this.tag; // ???
			Taxon selectionRoot = this.select(sel, tax2);
			System.out.println("| Selection has " + selectionRoot.count() + " taxa");
			tax2.addRoot(selectionRoot);
			this.copySelectedSynonyms(tax2);
			return tax2;
		} else {
			System.err.println("** Missing or ambiguous selection name");
			return null;
		}
	}

	// node is in source taxonomy, tax is the destination taxonomy ('union' or similar)
	static Taxon select(Taxon node, Taxonomy tax) {
		Taxon sam = node.dup(tax, "select");
		if (node.children != null)
			for (Taxon child : node.children) {
				Taxon c = select(child, tax);
				sam.addChild(c);
			}
		return sam;
	}

	// Select subtree rooted at a specified node, down to given depth

	public Taxonomy selectToDepth(String designator, int depth) {
		return selectToDepth(this.unique(designator), depth);
	}
	
	public Taxonomy selectToDepth(Taxon sel, int depth) {
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			tax2.tag = this.tag; // ???
			Taxon selection = selectToDepth(sel, tax2, depth);
			System.out.println("| Selection has " + selection.count() + " taxa");
			tax2.addRoot(selection);
			this.copySelectedSynonyms(tax2);
			return tax2;
		} else {
			System.err.println("** Missing or ambiguous name: " + sel);
			return null;
		}
	}

	Taxon selectToDepth(Taxon node, Taxonomy tax, int depth) {
		Taxon sam = node.dup(tax, "select");
		if (node.children != null && depth > 0)
			for (Taxon child : node.children) {
				Taxon c = selectToDepth(child, tax, depth-1);
				sam.addChild(c);
			}
		return sam;
	}

	// Select subtree, but only those nodes that are visible

	public Taxonomy selectVisible(String designator) {
		Taxon sel = this.taxon(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			Taxon selection = selectVisible(sel, tax2);
			System.out.println("| Selection has " + selection.count() + " taxa");
			tax2.addRoot(selection);
			this.copySelectedSynonyms(tax2);
			return tax2;
		} else
			return null;
	}

	// Copy only visible (non-hidden) nodes

	public Taxon selectVisible(Taxon node, Taxonomy tax) {
		if (node.isHidden()) return null;
		Taxon sam = null;
		if (node.children == null)
			sam = node.dup(tax, "select");
		else
			for (Taxon child : node.children) {
				Taxon c = selectVisible(child, tax);
				if (c != null) {
					if (sam == null)
						sam = node.dup(tax, "select");
					sam.addChild(c);
				}
			}
		return sam;
	}

	// Select a proportional sample of the nodes in a tree

	public Taxonomy sample(String designator, int count) {
		Taxon sel = this.unique(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			Taxon sample = sample(sel, count, tax2);
			System.out.println("| Sample has " + sample.count() + " taxa");
			tax2.addRoot(sample);
			// TBD: synonyms ?
			return tax2;
		} else {
			System.err.println("Missing or ambiguous name: " + designator);
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
			java.util.Collections.sort(node.children, Taxon.compareNodesBySize);
			for (Taxon child : node.children) {

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

		Taxon sam = node.dup(tax, "select");
		for (Taxon c : newChildren)
			sam.addChild(c);
		return sam;
	}

	public Taxonomy chop(int m, int n) throws java.io.IOException {
		Taxonomy tax = new SourceTaxonomy();
		List<Taxon> cuttings = new ArrayList<Taxon>();
		Taxon root = null;
		for (Taxon r : this.roots()) root = r;	//bad kludge. uniroot assumed
		Taxon newroot = chop(root, m, n, cuttings, tax);
		tax.addRoot(newroot);
		System.err.format("Cuttings: %s Residue: %s\n", cuttings.size(), newroot.count());

		// Temp kludge ... ought to be able to specify the file name
		String outprefix = "chop/";
		new File(outprefix).mkdirs();
		for (Taxon cutting : cuttings) {
			PrintStream out = 
				openw(outprefix + cutting.name.replaceAll(" ", "_") + ".tre");
			StringBuffer buf = new StringBuffer();
			cutting.appendNewickTo(buf);
			out.print(buf.toString());
			out.close();
		}
		return tax;
	}

	// List of nodes for which N/3 < size <= N < parent size

	Taxon chop(Taxon node, int m, int n, List<Taxon> chopped, Taxonomy tax) {
		int c = node.count();
		Taxon newnode = node.dup(tax, "select");
		if (m < c && c <= n) {
			newnode.setName(newnode.name + " (" + node.count() + ")");
			chopped.add(node);
		} else if (node.children != null)
			for (Taxon child : node.children) {
				Taxon newchild = chop(child, m, n, chopped, tax);
				newnode.addChild(newchild);
			}
		return newnode;
	}

    // Idempotent
	public void deforestate() {
        this.normalizeRoots();
        List<Taxon> rootsList = this.forest.children;
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
                        root.changeParent(biggest, Taxonomy.UNPLACED); // removes from roots
                    ++flushed;
                }
            System.out.format("| Removed %s smaller trees\n", flushed);
        }
	}

    // Sort by size and make sure the biggest one is placed.  This is idempotent.
    public void normalizeRoots() {
        List<Taxon> rootsList = this.forest.children;
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
                System.err.format("** Nontrivial forest: biggest is %s, 2nd biggest is %s\n", count1, count2);
        }
    }

    public void placeBiggest() {
        this.normalizeRoots();  // sort the roots list
        Taxon biggest = this.forest.children.get(0);
        biggest.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY;
    }

	// -------------------- Newick stuff --------------------
	// Render this taxonomy as a Newick string.
	// This feature is very primitive and only for debugging purposes!

	public String toNewick() {
		StringBuffer buf = new StringBuffer();
		for (Taxon root: this.roots()) {
			root.appendNewickTo(buf);
			buf.append(";");
		}
		return buf.toString();
	}

	public void dumpNewick(String outfile) throws java.io.IOException {
		PrintStream out = openw(outfile);
		out.print(this.toNewick());
		out.close();
	}

	// Parse Newick yielding nodes

	Taxon newickToNode(String newick) {
		java.io.PushbackReader in = new java.io.PushbackReader(new java.io.StringReader(newick));
		try {
			return this.newickToNode(in);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// TO BE DONE: Implement ; for reading forests

	Taxon newickToNode(java.io.PushbackReader in) throws java.io.IOException {
		int c = in.read();
		if (c == '(') {
			List<Taxon> children = new ArrayList<Taxon>();
			{
				Taxon child;
				while ((child = newickToNode(in)) != null) {
					children.add(child);
					int d = in.read();
					if (d < 0 || d == ')') break;
					if (d != ',')
						System.out.println("shouldn't happen: " + d);
				}
			}
			Taxon node = newickToNode(in); // get postfix name, x in (a,b)x
			if (node != null || children.size() > 0) {
				if (node == null) {
					// kludge
					node = new Taxon(this, "");
				}
				for (Taxon child : children)
					if (!child.name.startsWith("null"))
						node.addChild(child);
				node.rank = (children.size() > 0) ? Taxonomy.NO_RANK : "species";
				return node;
			} else
				return null;
		} else {
			StringBuffer buf = new StringBuffer();
			while (true) {
				if (c < 0 || c == ')' || c == ',' || c == ';') {
					if (c >= 0) in.unread(c);
					if (buf.length() > 0) {
						Taxon node = new Taxon(this); // no name
						initNewickNode(node, buf.toString());
						return node;
					} else return null;
				} else {
					buf.appendCodePoint(c);
					c = in.read();
				}
			}
		}
	}

	// Specially hacked to support the Hibbett 2007 spreadsheet
	void initNewickNode(Taxon node, String label) {
		// Drop the branch length, if present
		int pos = label.indexOf(':');
		if (pos >= 0) label = label.substring(0, pos);
		// Strip quotes put there by Mesquite
		if (label.startsWith("'"))
			label = label.substring(1);
		if (label.endsWith("'"))
			label = label.substring(0,label.length()-1);
		// Ad hoc rank syntax Class=Amphibia
		pos = label.indexOf('=');
		if (pos > 0) {
			node.rank = label.substring(0,pos).toLowerCase();
			node.setName(label.substring(pos+1));
		} else {
			node.rank = Taxonomy.NO_RANK;
			node.setName(label);
		}
	}

	static PrintStream openw(String filename) throws IOException {
		PrintStream out;
		if (filename.equals("-")) {
			out = System.out;
			System.err.println("Writing to standard output");
		} else {
			out = new java.io.PrintStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(filename)),
										  false,
										  "UTF-8");

			// PrintStream(OutputStream out, boolean autoFlush, String encoding)

			// PrintStream(new OutputStream(new FileOutputStream(filename, "UTF-8")))

			System.err.println("Writing " + filename);
		}
		return out;
	}

    // The id of the node in the taxonomy that has highest numbered id.

	long maxid() {
		long id = -1;
		for (Taxon node : this) {
            if (node.id != null) {
                try {
                    long idAsLong = Long.parseLong(node.id);
                    if (idAsLong > id) id = idAsLong;
                } catch (NumberFormatException e) {
                    ;
                }
            }
		}
		return id;
	}

	public void assignNewIds(long sourcemax) {
		long maxid = this.maxid();
		if (sourcemax > maxid) maxid = sourcemax;
        long start = maxid;
		for (Taxon node : this)
			if (node.id == null) {
				node.setId(Long.toString(++maxid));
				node.markEvent("new-id");
			}
        if (maxid > start)
            System.out.format("| Highest id before: %s after: %s\n", start, maxid);
	}

	public static SourceTaxonomy parseNewick(String newick) {
		SourceTaxonomy tax = new SourceTaxonomy();
		tax.addRoot(tax.newickToNode(newick));
		return tax;
	}

	// ----- PATCH SYSTEM -----

	static Pattern tabPattern = Pattern.compile("\t");

	// Apply a set of edits to the union taxonomy

	public void edit(String dirname) throws IOException {
		File[] editfiles = new File(dirname).listFiles();
		if (editfiles == null) {
			System.err.println("No edit files in " + dirname);
			return;
		}
		for (File editfile : editfiles)
			if (editfile.isFile() &&
				!editfile.getName().endsWith(".hold") &&
				!editfile.getName().endsWith("~")) {
				applyEdits(editfile);
			}
	}

	// Apply edits from one file
	public void applyEdits(String editfile) throws IOException {
		applyEdits(new File(editfile));
	}
	public void applyEdits(File editfile) throws IOException {
		System.out.println("--- Applying edits from " + editfile + " ---");
		BufferedReader br = Taxonomy.fileReader(editfile);
		String str;
		while ((str = br.readLine()) != null) {
			if (!(str.length()==0) && !str.startsWith("#")) {
				String[] row = tabPattern.split(str);
				if (row.length > 0 &&
					!row[0].equals("command")) { // header row!
					if (row.length != 6)
						System.err.println("** Ill-formed command: " + str);
					else
						applyOneEdit(row);
				}
			}
		}
		br.close();
	}

	//		command	name	rank	parent	context	sourceInfo
	// E.g. add	Acanthotrema frischii	species	Acanthotrema	Fungi	IF:516851

	void applyOneEdit(String[] row) {
		String command = row[0].trim();
		String name = row[1].trim();
		String rank = row[2].trim();
		String parentName = row[3].trim();
		String contextName = row[4].trim();
		String sourceInfo = row[5].trim();

		List<Taxon> parents = filterByAncestor(parentName, contextName);
		if (parents == null) {
			System.err.println("! Parent name " + parentName
							   + " missing in context " + contextName);
			return;
		}
		if (parents.size() > 1)
			System.err.println("? Ambiguous parent name: " + parentName);
		Taxon parent = parents.get(0);	  //this.taxon(parentName, contextName)

		if (!parent.name.equals(parentName))
			System.err.println("! Warning: parent taxon name is a synonym: " + parentName);

		List<Taxon> existings = filterByAncestor(name, contextName);
		Taxon existing = null;
		if (existings != null) {
			if (existings.size() > 1)
				System.err.println("? Ambiguous taxon name: " + name);
			existing = existings.get(0);
		}

		if (command.equals("add")) {
			if (existing != null) {
				System.err.println("! (add) Warning: taxon already present: " + name);
				if (existing.parent != parent)
					System.err.println("! (add)	 ... with a different parent: " +
									   existing.parent.name + " not " + parentName);
			} else {
				Taxon node = new Taxon(this, name);
				node.rank = rank;
				node.setSourceIds(sourceInfo);
				parent.addChild(node, 0); // Not incertae sedis
				node.addFlag(Taxonomy.EDITED);
			}
		} else if (command.equals("move")) {
			if (existing == null)
				System.err.println("! (move) No taxon to move: " + name);
			else {
				if (existing.parent == parent)
					System.err.println("! (move) Note: already in the right place: " + name);
				else {
					// TBD: CYCLE PREVENTION!
					existing.changeParent(parent, 0);
					existing.addFlag(Taxonomy.EDITED);
				}
			}
		} else if (command.equals("prune")) {
			if (existing == null)
				System.err.println("! (prune) No taxon to prune: " + name);
			else
				existing.prune("edit/prune");

		} else if (command.equals("fold")) {
			if (existing == null)
				System.err.println("! (fold) No taxon to fold: " + name);
			else {
				if (existing.children != null)
					for (Taxon child: existing.children)
						child.changeParent(parent, 0);
				addSynonym(name, parent); //  ????
				existing.prune("edit/fold");
			}

		} else if (command.equals("flag")) {
			if (existing == null)
				System.err.println("(flag) No taxon to flag: " + name);
			else
				existing.addFlag(Taxonomy.FORCED_VISIBLE);

		} else if (command.equals("incertae_sedis")) {
			if (existing == null)
				System.err.println("(flag) No taxon to flag: " + name);
			else
				existing.addFlag(Taxonomy.INCERTAE_SEDIS);

		} else if (command.equals("synonym")) {
			// TBD: error checking
			if (existing != null)
				System.err.println("Synonym already known: " + name);
			else
				addSynonym(name, parent);

		} else
			System.err.println("Unrecognized edit command: " + command);
	}

	// Test case: Valsa
	List<Taxon> filterByAncestor(String taxonName, String contextName) {
		List<Taxon> nodes = this.lookup(taxonName);
		if (nodes == null) return null;
		List<Taxon> fnodes = new ArrayList<Taxon>(1);
		for (Taxon node : nodes) {
			// Follow ancestor chain to see whether this node is in the context
			for (Taxon chain = node; chain != null; chain = chain.parent)
				if (chain.name.equals(contextName)) {
					fnodes.add(node);
					break;
				}
		}
		if (fnodes.size() == 0) return null;
		if (fnodes.size() == 1) return fnodes;
		List<Taxon> gnodes = new ArrayList<Taxon>(1);
		for (Taxon fnode : fnodes)
			if (fnode.name.equals(taxonName))
				gnodes.add(fnode);
		if (gnodes.size() >= 1) return gnodes;
		return fnodes;
	}

	List<Taxon> filterByDescendant(String taxonName, String descendantName) {
		List<Taxon> nodes = this.lookup(descendantName);
		if (nodes == null) return null;
		List<Taxon> fnodes = new ArrayList<Taxon>(1);
		for (Taxon node : nodes) {
			if (!node.name.equals(descendantName)) continue;
			// Follow ancestor chain to see whether this node is an ancestor
			for (Taxon chain = node; chain != null; chain = chain.parent)
				if (chain.name.equals(taxonName)) {
					fnodes.add(chain);
					break;
				}
		}
		return fnodes.size() == 0 ? null : fnodes;
	}

	public void parentChildHomonymReport() {
		for (Taxon node : this)
			if (!node.isRoot() &&
				node.parent.name.equals(node.name))
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
		for (String name : this.nameIndex.keySet())
			if (name.endsWith("ae")) {
				List<Taxon> sheep = this.lookup(name);
				if (sheep == null) continue;
				{	boolean win = false;
					for (Taxon n : sheep) if (n.name.equals(name)) win = true;
					if (!win) continue;	  }

				String namea = name.substring(0,name.length()-2) + "ea";
				List<Taxon> goats = this.lookup(namea);
				if (goats == null) continue;
				{	boolean win = false;
					for (Taxon n : goats) if (n.name.equals(namea)) win = true;
					if (!win) continue;	  }

				if (sheep != null && goats != null) {
					for (Taxon sh : sheep)
						if (sh.name.equals(name))
							for (Taxon gt : goats)
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

	// ----- Methods for use in jython scripts -----

	public static Taxonomy newTaxonomy() {
		return new UnionTaxonomy();
	}

	public static UnionTaxonomy unite(List<Taxonomy> taxos) {
		UnionTaxonomy union = new UnionTaxonomy();
		for (Taxonomy tax: taxos)
			if (tax instanceof SourceTaxonomy)
				union.mergeIn((SourceTaxonomy)tax);
			else
				System.err.println("** Expected a source taxonomy: " + tax);
		return union;
	}

	// Merge a source taxonomy into this taxonomy
	// Deprecated
	public void absorb(SourceTaxonomy tax, String tag) {
		tax.tag = tag;
		this.absorb(tax);
	}

	// Not deprecated (prefix now passed to getTaxonomy)
	public void absorb(SourceTaxonomy tax) {
        try {
            ((UnionTaxonomy)this).mergeIn(tax);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
	}

	// Overridden in class UnionTaxonomy
	public void assignIds(SourceTaxonomy idsource) {
		((UnionTaxonomy)this).assignIds(idsource);
	}

	public Taxon taxon(String name) {
		Taxon probe = maybeTaxon(name);
		if (probe == null)
			System.err.format("** No unique taxon found with this name: %s\n", name);
		return probe;
	}

	// Look up a taxon by name or unique id.  Name must be unique in the taxonomy.
	public Taxon maybeTaxon(String name) {
		List<Taxon> probe = this.nameIndex.get(name);
		if (probe != null) {
			if (probe.size() > 1) {
				// This is extremely ad hoc.  Need a more general theory.
				List<Taxon> replacement = new ArrayList<Taxon>();
				for (Taxon node : probe)
					if (node.name.equals(name))
						replacement.add(node);
				if (replacement.size() == 1) probe = replacement;
			}
			if (probe.size() == 1)
				return probe.get(0);
			else {
				System.err.format("** Ambiguous taxon name: %s\n", name);
				for (Taxon alt : probe) {
					String u = alt.uniqueName();
					if (u.equals("")) {
						if (alt.name.equals(name))
							System.err.format("**   %s %s\n", alt.id, name);
						else
							System.err.format("**   %s %s (synonym for %s)\n", alt.id, name, alt.name);
					} else
						System.err.format("**   %s %s\n", alt.id, u);
				}
				return null;
			}
		}
		return this.lookupId(name);
	}

	public Taxon taxon(String name, String context) {
		Taxon probe = maybeTaxon(name, context);
		if (probe == null)
			System.err.format("** No unique taxon found with name %s in context %s\n", name, context);
		return probe;
	}

	public Taxon maybeTaxon(String name, String context) {
		List<Taxon> nodes = filterByAncestor(name, context);
		if (nodes == null) {
			if (this.lookup(context) == null) {
				Taxon probe = this.maybeTaxon(name);
				if (probe != null)
					System.err.format("| Found %s but there is no context %s\n", name, context);
				return probe;
			} else
				return null;
		} else if (nodes.size() == 1)
			return nodes.get(0);
		else {
			// Still ambiguous even in context.
			Taxon candidate = null;
			Taxon otherCandidate = null;
			// Chaetognatha
			for (Taxon node : nodes)
				if (!node.isRoot() && node.parent.name.equals(context))
					if (candidate == null)
						candidate = node;
					else {
						otherCandidate = node;
						break;
					}
			if (otherCandidate == null)
				return candidate;
			else {
				System.err.format("** Ancestor %s of %s does not disambiguate %s and %s\n",
								  context, name, candidate.id, otherCandidate.id);
				return null;
			}
		}

	}

	public Taxon taxonThatContains(String name, String descendant) {
		List<Taxon> nodes = filterByDescendant(name, descendant);
		if (nodes == null) {
			System.err.format("** No taxon with name %s with ancestor %s\n", descendant, name);
			return null;
		} else if (nodes.size() == 1)
			return nodes.get(0);
		else {
			Taxon candidate = null;
			Taxon otherCandidate = null;
			// Chaetognatha
			for (Taxon node : nodes)
				if (!node.isRoot() && node.parent.name.equals(name))
					if (candidate == null)
						candidate = node.parent;
					else {
						otherCandidate = node.parent;
						break;
					}
			if (otherCandidate == null)
				return candidate;
			else {
				System.err.format("** Descendant %s of %s does not disambiguate between %s and %s\n",
								  descendant, name, candidate.id, otherCandidate.id);
				return null;
			}
		}

	}

    // For use from jython code.  Result is added as a root.
	public Taxon newTaxon(String name, String rank, String sourceIds) {
		if (this.lookup(name) != null)
			System.err.format("** Warning: A taxon by the name of %s already exists\n", name);
		Taxon t = new Taxon(this, name);
		if (!rank.equals("no rank"))
			t.rank = rank;
        if (sourceIds != null && sourceIds.length() > 0)
            t.setSourceIds(sourceIds);
        t.taxonomy.addRoot(t);
		return t;
	}

	public boolean same(Taxon node1, Taxon node2) {
		return sameness(node1, node2, true, true);
	}

	public boolean notSame(Taxon node1, Taxon node2) {
		return sameness(node1, node2, false, true);
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
                System.out.format("** Cannot un-lump %s\n", node1);
                return false;
            }
            else
                return false;
        }
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
		if (whether) {			// same
			if (snode.mapped != null) {
				if (snode.mapped != unode) {
					System.err.format("** The taxa have already been determined to be different: %s\n", snode);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                snode.alignWith(unode, Answer.yes(snode, unode, "same/ad-hoc", null));
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
                snode.alignWith(evader, Answer.yes(snode, evader, "not-same/ad-hoc", null));

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

	public void describe() {
		System.out.format("%s ids, %s roots, %s names\n",
						  this.idIndex.size(),
						  this.rootCount(),
						  this.nameIndex.size());
	}

	// ----- Utilities -----

	static BufferedReader fileReader(File filename) throws IOException {
		return
			new BufferedReader(new InputStreamReader(new FileInputStream(filename),
													 "UTF-8"));
	}

	static BufferedReader fileReader(String filename) throws IOException {
		return
			new BufferedReader(new InputStreamReader(new FileInputStream(filename),
													 "UTF-8"));
	}

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
		for (Taxon node : other) {
			Taxon newnode = this.lookupId(node.id);
			if (newnode == null) {
				List<Taxon> newnodes = this.lookup(node.name);
				if (newnodes == null)
					reportDifference("removed", node, null, null, out);
				else if (newnodes.size() != 1)
					reportDifference("multiple-replacements", node, null, null, out);
				else {
					newnode = newnodes.get(0);
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
		for (Taxon newnode : this) {
			Taxon node = other.lookupId(newnode.id);
			if (node == null) {
				if (other.lookup(newnode.name) != null)
					reportDifference("changed-id?", newnode, null, null, out);
				else {
					List<Taxon> found = this.lookup(newnode.name);
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
	
	void copySelectedSynonyms(Taxonomy target) {
		copySynonyms(target, false);
	}

	void copyMappedSynonyms(Taxonomy target) {
		copySynonyms(target, true);
	}

	// Propogate synonyms from source taxonomy (= this) to union.
	// Some names that are synonyms in the source might be primary names in the union,
	//	and vice versa.
	void copySynonyms(Taxonomy target, boolean mappedp) {
		int count = 0;

		// For each name in source taxonomy...
		for (String syn : this.nameIndex.keySet()) {

			// For each node that the name names...
			for (Taxon node : this.nameIndex.get(syn)) {
				Taxon other =
					(mappedp
					 ? node.mapped
					 : target.lookupId(node.id));

				// If that node maps to a union node with a different name....
				if (other != null && !other.name.equals(syn)) {
					// then the name is a synonym of the union node too
					if (other.taxonomy != target)
						System.err.format("** copySynonyms logic error %s %s\n",
										  other.taxonomy, target);
					else if (target.addSynonym(syn, other))
						++count;
				}
			}
		}
		if (count > 0)
			System.err.println("| Added " + count + " synonyms");
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

	// for jython
	public void setSkeleton(SourceTaxonomy skel) {
		UnionTaxonomy union = (UnionTaxonomy)this;
		union.setSkeletonx(skel);
	}
	public void markDivisions(SourceTaxonomy source) {
		UnionTaxonomy union = (UnionTaxonomy)this;
		union.markDivisionsx(source);
	}

    SourceTaxonomy importantIds = null;
    Map<String, Integer> importantIdsFoo = new HashMap<String, Integer>();

    // e.g. "ids-that-are-otus.tsv", False
    // e.g. "ids-in-synthesis.tsv", True
    public SourceTaxonomy loadPreferredIds(String filename, boolean seriousp)
           throws IOException {
		System.out.println("--- Loading list of important taxon from " + filename + " ---");
        if (this.importantIds == null)
            this.importantIds = new SourceTaxonomy();
		BufferedReader br = Taxonomy.fileReader(new File(filename));
		String str;
		while ((str = br.readLine()) != null) {
            String[] row = tabPattern.split(str);

            String id = row[0];
            Taxon node = this.importantIds.lookupId(id);
            if (node == null) {
                node = new Taxon(this.importantIds);
                this.importantIds.addRoot(node);
            }
            node.setId(id);

            // old
            importantIdsFoo.put(id, row[1].length());

            // new - row[1] is list of studies
            if (row.length > 2)
                node.setName(row[2]);
            node.setSourceIds(row[1]);
            if (seriousp)
                node.addFlag(Taxonomy.SERIOUS);
        }
        br.close();
        return importantIds;
    }

	// Compute the inverse of the name->node map.
	public Map<Taxon, Collection<String>> makeSynonymIndex() {
		Map<Taxon, Collection<String>> nameMap = new HashMap<Taxon, Collection<String>>();
		for (String name : this.nameIndex.keySet())
			for (Taxon node : this.nameIndex.get(name)) {
				Collection<String> names = nameMap.get(node);  // of this node
				if (names == null) {
					names = new ArrayList(1);
					nameMap.put(node, names);
				}
				names.add(name);
			}
		return nameMap;
	}

    // Called just before alignment
	public void reset() {
		for (Taxon root: this.roots()) {
			root.reset();
			// Prepare for subsumption checks
            root.resetDepths();
		}
        this.propagateFlags(); 
	}

    // Event logging
    // Eventually replace the static event logger in Taxon.java

	Map<String, Long> eventStats = new HashMap<String, Long>();
	List<String> eventStatNames = new ArrayList<String>();

	boolean markEvent(String note) {
		Long probe = this.eventStats.get(note);
		long count;
		if (probe == null) {
			this.eventStatNames.add(note);
			count = 0;
		} else
			count = probe;
		this.eventStats.put(note, count+(long)1);
		if (count <= 10) {
			return true;
		} else
			return false;
	}

	void eventReport(String prefix) {        // was printStats
        Collections.sort(this.eventStatNames);
		for (String note : this.eventStatNames) {
			System.out.println(prefix + note + ": " + this.eventStats.get(note));
		}
	}

	void resetEvents() {         // was resetStats
		this.eventStats = new HashMap<String, Long>();
		this.eventStatNames = new ArrayList();
	}


}

// end of class Taxonomy

class Stat {
    String tag;
    int i = 0;
    int inc(Taxon x, Answer n, Answer m) { if (i<5) System.out.format("%s %s %s %s\n", tag, x, n, m); return ++i; }
    Stat(String tag) { this.tag = tag; }
    public String toString() { return "" + i + " " + tag; }
}

class SourceTaxonomy extends Taxonomy {

    private UnionTaxonomy target = null; // see claim.py

	SourceTaxonomy() {
	}

	public UnionTaxonomy promote() {
		return new UnionTaxonomy(this);
	}

    public UnionTaxonomy target() {
        return target;
    }
    public void setTarget(UnionTaxonomy union) {
        this.target = union;
    }

	Alignment alignTo(UnionTaxonomy union) {

        this.target = union;
        Alignment n = new AlignmentByName(this, union);
        n.cacheInSourceNodes();
        if (false) {
            // Code disabled for now - membership based alignment is still experimental
            // For testing purposes, do both kinds of alignment and compare them
            Alignment m = new AlignmentByMembership(this, union);
            Stat s0 = new Stat("mapped the same by both");
            Stat s1 = new Stat("not mapped by either");
            Stat s2 = new Stat("mapped by name only");
            Stat s2a = new Stat("mapped by name only (ambiguous)");
            Stat s3 = new Stat("mapped by membership only");
            Stat s4 = new Stat("incompatible mappings");
            for (Taxon node : this) {
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

        union.sources.add(this);
        return n;
	}

    void transferProperties() {
        for (Taxon node : this)
            node.transferProperties();
    }

	// This is the SourceTaxonomy version.
	// Overrides dumpMetadata in class Taxonomy.
	void dumpMetadata(String filename)	throws IOException {
		if (this.metadata != null) {
			PrintStream out = Taxonomy.openw(filename);
			out.println(this.metadata);
			out.close();
		}
	}

}

class UnionTaxonomy extends Taxonomy {

	List<SourceTaxonomy> sources = new ArrayList<SourceTaxonomy>();
	SourceTaxonomy idsource = null;
	SourceTaxonomy auxsource = null;
	// One log per name
	Map<String, List<Answer>> logs = new HashMap<String, List<Answer>>();

	UnionTaxonomy() {
		this.tag = "union";
	}

	UnionTaxonomy(SourceTaxonomy source) {
		this.tag = "union";
		this.mergeIn(source);
	}

	public UnionTaxonomy promote() {
		return this;
	}

    public UnionTaxonomy target() {
        return null;
    }

    public void setTarget(UnionTaxonomy union) {
        System.err.format("** setTarget\n");
    }

	// -----
	// The 'division' field of a Taxon is always either null or a
	// taxon belonging to the skeleton taxonomy.

	public SourceTaxonomy skeleton = null;

	// Method usually used on union taxonomies, I would think...
	public void setSkeletonx(SourceTaxonomy skel) {
		// Prepare
		for (Taxon div : skel)
			div.setDivision(div);

		UnionTaxonomy union = (UnionTaxonomy)this;
		union.skeleton = skel;

		// Copy skeleton into union
		union.absorb(skel);		// ?
		for (Taxon div : skel) div.mapped.setId(div.id); // ??!!!
	}

	// this = a union taxonomy.
	// Set 'division' for division taxa in source
	public void markDivisionsx(Taxonomy source) {
		if (this.skeleton == null)
			this.pin(source);	// Backward compatibility!
		else
			for (Taxon div : this.skeleton.roots()) // this = union
				this.markDivisions(div, source);
	}

	// Before every alignment pass (folding source taxonomy into
	// union), all 'division' taxa (from the skeleton) that occur in
	// either the union or the source taxonomy are identified and the
	// 'division' field of each one is set to the corresponding
	// division node from the union taxonomy.

	// This operation is idempotent.

	boolean markDivisions(Taxon div, Taxonomy source) {
		Taxon unode = this.highest(div.name);
		if (div.children != null) {
			Taxon hit = null, miss = null;
			for (Taxon child : div.children) {
				if (markDivisions(child, source))
					hit = child;
				else
					miss = child;
			}
			if (hit != null && miss != null)
				System.err.format("** Division %s was found but its sibling %s wasn't\n", hit.name, miss.name);
		}
		if (unode != null) {
			unode.setDivision(unode);
			Taxon node = source.highest(div.name);
			if (node != null &&
				(node.mapped == null || node.mapped == unode)) {   // Cf. notSame
				node.alignWith(unode, "same/division-name");
				node.setDivision(unode);
				node.alignWith(unode, node.answer); // voodoo, cf. getDivision? divergence?
				return true;
			}
		}
		return false;
	}

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
				Taxon m1 = source.highest(name);
				if (m1 != null) n1 = m1;
				Taxon m2 = this.highest(name);
				if (m2 != null) div = m2;
			}
			if (div != null) {
				div.setDivision(div);
				if (n1 != null)
					n1.setDivision(div);
				if (n1 != null && div != null)
					n1.alignWith(div, "same/pinned"); // hmm.  TBD: move this out of here
				if (n1 != null || div != null)
					++count;
			}
		}
		if (count > 0)
			System.out.println("Pinned " + count + " out of " + pins.length);
	}

	// -----

    // Absorb a new source taxonomy

	void mergeIn(SourceTaxonomy source) {
		Alignment a = source.alignTo(this);
        new MergeMachine(source, this).augment(a);
		source.copyMappedSynonyms(this); // this = union
		Taxon.windyp = true; //kludge
	}

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void assignIds(SourceTaxonomy idsource) {
		this.idsource = idsource;
		// idsource.tag = "ids";
		Alignment a = idsource.alignTo(this);

		// Phase 1: recycle previously assigned ids.
		this.transferIds(idsource, a);

		// Phase 2: give new ids to union nodes that didn't get them above.
		long sourcemax = idsource.maxid();
		this.assignNewIds(sourcemax);
		// remember, this = union, idsource = previous version of ott

		Taxon.printStats();		// Taxon id clash
	}

	public void transferIds(SourceTaxonomy idsource, Alignment a) {
		Taxon.resetStats();
		System.out.println("--- Assigning ids to union starting with " + idsource.getTag() + " ---");

        Map<Taxon, String> assignments = new HashMap<Taxon, String>();

		for (Taxon node : idsource) {
            // Consider using node.id as the id for the union node it maps to
			Taxon unode = node.mapped;
			if (unode != null &&
                unode.id == null &&
                this.lookupId(node.id) == null) {

                String haveId = assignments.get(unode);
                if (haveId == null || compareIds(node.id, haveId) > 0)
                    assignments.put(unode, node.id);
			}
		}
        for(Taxon unode : assignments.keySet()) {
            unode.setId(assignments.get(unode));
        }
        System.out.format("| %s ids transferred\n", assignments.size());
	}

    // Return negative, zero, positive depending on whether id1 is worse, same, better than id2
    int compareIds(String id1, String id2) {
        Integer p1 = this.importantIdsFoo.get(id1);
        Integer p2 = this.importantIdsFoo.get(id2);
        if (p1 != null && p2 != null)
            return p1 - p2;
        if (p1 == null)
            return -1;
        if (p2 == null)
            return 1;
        try {
            // Smaller ids are better
            return Integer.parseInt(id2) - Integer.parseInt(id1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

	// Cf. assignIds()
	// x is a source node drawn from the idsource taxonomy file.
	// target is the union node it might or might not map to.

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

	// x.getQualifiedId()

	// Overrides dump method in class Taxonomy.
	// outprefix should end with a / , but I guess . would work too

	public void dump(String outprefix, String sep) throws IOException {
		new File(outprefix).mkdirs();
        this.prepareForDump(outprefix, sep);

		this.dumpMetadata(outprefix + "about.json");

		Set<String> scrutinize = null;
		if (this.idsource != null) {
			scrutinize = this.dumpDeprecated(this.idsource, outprefix + "deprecated.tsv");
			scrutinize.add("Methanococcus maripaludis");
			this.dumpLog(outprefix + "log.tsv", scrutinize);
		}

		this.dumpNodes(this.roots(), outprefix, sep);
		this.dumpSynonyms(outprefix + "synonyms.tsv", sep);
		this.dumpHidden(outprefix + "hidden.tsv");
		this.dumpConflicts(outprefix + "conflicts.tsv");
	}

	// This is the UnionTaxonomy version.  Overrides method in Taxonomy class.

	void dumpMetadata(String filename)	throws IOException {
		this.metadata = new JSONObject();
		List<Object> sourceMetas = new ArrayList<Object>();
		this.metadata.put("inputs", sourceMetas);
		for (Taxonomy source : this.sources)
			if (source.metadata != null)
				sourceMetas.add(source.metadata);
			else
				sourceMetas.add(source.tag);
		// this.metadata.put("prefix", "ott");
		PrintStream out = Taxonomy.openw(filename);
		out.println(this.metadata);
		out.close();
	}

	Set<String> dumpDeprecated(SourceTaxonomy idsource, String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("id\tname\tsourceinfo\treason\twitness\treplacement" +
                    "\tstatus");

        idsource.resetEvents();

		for (String id : idsource.idIndex.keySet()) {

			String reason = null; // report on it iff this is nonnull
			String replacementId = null;
			String witness = null;
            String flagstring = "";

            // Don't report on ids that aren't used as OTUs - too overwhelming to
            // look at everything.
            // The difference is between about 200 and about 60,000 ids to look at.
            Taxon important = null;
            if (this.importantIds != null)
                important = this.importantIds.lookupId(id);

            Taxon node = idsource.lookupId(id);

            if (node.mapped == null) {

                if (this.importantIds != null && important == null) {
                    idsource.markEvent("unimportant/not-mapped");
                    continue;
                }

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
                    List<Taxon> nodes = idsource.lookup(node.name);
                    List<Taxon> unodes = this.lookup(node.name);
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
                        else
                            // 14/55
                            reason = "id-retired/incompatible-uses";

                        if (unodes.size() == 1) {
                            Taxon div1 = node.getDivision();
                            Taxon div2 = unodes.get(0).getDivision();
                            if (div1 != null && div1.mapped != null && div1.mapped != div2)
                                reason = "id-retired/changed-divisions";
                            replacementId = "!" + unodes.get(0).id;
                        }
                    }
                }
            } else {

                if (this.importantIds != null && important == null) {
                    idsource.markEvent("unimportant/mapped");
                    continue;
                }

                Taxon unode = node.mapped;

                if (unode.id == null)
                    // why would this happen?
                    reason = "id-changed/old-id-retired";
                else if (!id.equals(unode.id)) {
                    // 143/199
                    reason = "id-changed/lump-or-split"; // mapped elsewhere, maybe split?
                    replacementId = '=' + unode.id;
                }
                
                if (unode.isHidden()) {
                    if (!node.isHidden())
                        reason = "newly-hidden";

                    if (((unode.properFlags & Taxonomy.INCERTAE_SEDIS_ANY) != 0)
                        && !unode.parent.isHidden()) {
                        replacementId = "~" + unode.parent.id;
                        reason = "forwarded";
                    }
                } else if (node.isHidden())
                    idsource.markEvent("exposed");
                int newflags  = (unode.properFlags & ~node.properFlags);
                int newiflags = (unode.inheritedFlags & ~node.inheritedFlags);
                if (newflags != 0)
                    flagstring = ("[" + Flag.toString(newflags, newiflags) + "]");

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
            if (important != null && (important.properFlags & Taxonomy.SERIOUS) != 0)
                serious = "synthesis";

            if (reason != null) {
                out.format("%s\t%s\t%s\t" + "%s\t%s\t%s\t" + "%s\t\n",
                           id,
                           node.name,
                           node.getSourceIdsString(),

                           reason + flagstring,
                           (witness == null ? "" : witness),
                           replacementId,

                           serious);
                idsource.markEvent(reason);
            }
		}
		out.close();

		Set<String> scrutinize = new HashSet<String>();
		for (String name : idsource.nameIndex.keySet())
			for (Taxon node : idsource.nameIndex.get(name))
				if (node.mapped == null) {
					scrutinize.add(name);
					break;
				}
        idsource.eventReport(".  ");
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
		else if (scrutinize != null)
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
			for (Taxon node : this)	// preorder
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

	// this is a union taxonomy ...

	void log(Answer answer) {
		String name = null;
		if (answer.target != null) name = answer.target.name;
		if (name == null) name = answer.subject.name;	 //could be synonym
		if (name == null) return;					 // Hmmph.	No name to log it under.
		List<Answer> lg = this.logs.get(name);
		if (lg == null) {
			// Kludge! Why not other names as well?
			if (name.equals("environmental samples")) return; //3606 cohomonyms
			lg = new ArrayList<Answer>(1);
			this.logs.put(name, lg);
		}
		lg.add(answer);
	}
	void logAndMark(Answer answer) {
		this.log(answer);
        // this.markEvent(answer.reason);
		Taxon.markEvent(answer.reason);
	}
	void logAndReport(Answer answer) {
		this.log(answer);
		answer.subject.report(answer.reason, answer.target, answer.witness);
	}

	// 3799 conflicts as of 2014-04-12
	// unode.comapped.parent == fromparent
	void reportConflict(Taxon paraphyletic) {
        Taxon unode = null;
        for (Taxon child: paraphyletic.children)
            if (child.mapped != null && !child.mapped.isDetached() && child.mapped.isPlaced()) {
                // "old"
                unode = child;
                break;
            }
		conflicts.add(new Conflict(paraphyletic, unode));
	}

	List<Conflict> conflicts = new ArrayList<Conflict>();
	void dumpConflicts(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
				   "depth", "para", "para_id",
				   "eg", "eg_id",
				   "eg_parent", "egp_id",
				   "para_anc", "pa_id",
				   "eg_anc", "ega_id");
		for (Conflict conflict : this.conflicts)
			if (!conflict.paraphyletic.isHidden())
				out.println(conflict.toString());
		out.close();
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

    void markEvent(String note) {
        source.markEvent(note);
    }

	void augment(Alignment a) {
        // a is unused for the time being - alignment is cached in .mapped

        source.resetEvents();

        boolean windyp = (union.count() > 1);

        // Add heretofore unmapped nodes to union
        if (windyp)
            System.out.println("--- Augmenting union with new nodes from " + source.getTag() + " ---");
        int startroots = union.rootCount();
        int startcount = union.count();

        for (Taxon root : source.roots()) {
            this.augment(root, union.forest);
            Taxon newroot = root.mapped;
            if (newroot != null && newroot.isDetached() && !newroot.noMrca())
                union.addRoot(newroot);
        }

        source.transferProperties();

        if (Taxon.windyp) {
            report(source, startroots, startcount);
            if (union.count() == 0)
                source.forest.show();
        }
        if (union.nameIndex.size() < 10)
            System.out.println(" -> " + union.toNewick());
	}

    Map<String, Integer> reasonCounts = new HashMap<String, Integer>();
    List<String> reasons = new ArrayList<String>();

    void report(SourceTaxonomy source, int startroots, int startcount) {
        System.out.format("| Started with: %s roots, %s taxa + %s source roots, %s source taxa\n",
                          startroots,
                          startcount,
                          source.rootCount(),
                          source.count());
        for (Taxon node : source) {
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
        source.eventReport("|   ");
        System.out.format("| Ended with: %s roots, %s taxa\n",
                          union.rootCount(), union.count());
    }

	// Method called for every node in the source taxonomy.
	// Input is node in source taxonomy.  Returns node in union taxonomy, or null.
    // Result can be detached (meaning caller must attach it) or not
    // (meaning it's already connected to the union taxonomy).
	Taxon augment(Taxon node, Taxon sink) {

		if (node.children == null) {
            if (node.mapped != null)
                accept(node, "mapped/tip");
			else if (node.answer == null ||
                     node.answer.value <= Answer.HECK_NO)
                // Don't create homonym if it's too close a match
                // (weak no) or ambiguous (noinfo)
                // NOINFO > NO > HECK_NO  (sorry)
				acceptNew(node, "new/tip");

		} else {

            if (node.mapped != null)
                sink = node.mapped;

			for (Taxon child: node.children)
                augment(child, sink);

            if (node.mapped != null) {
                takeOn(node, node.mapped, 0);
                accept(node, "mapped/internal");

            } else if (node.lub == null) {
                // new & unplaced old children only... copying stuff over to union.
                Taxon newnode = acceptNew(node, "new/graft");
                if (false && node.answer.reason.equals("not-same/disjoint"))
                    newnode.name = String.format("%s sensu %s",
                                                 node.name,
                                                 node.taxonomy.getTag().toUpperCase());
                takeOn(node, newnode, 0);

            } else if (consistentp(node)) {
                if (refinementp(node)) {
                    Taxon newnode = acceptNew(node, "new/insertion");
                    takeOld(node, newnode);
                    takeOn(node, newnode, 0);
                } else {
                    // Monophyletic but not a refinement - plain merge.
                    // In OTT 2.8 these were treated as insertions.
                    Taxon unode = node.lub;
                    takeOn(node, unode, 0);
                    reject(node,
                           (carriesOver(node)
                            ? "same/merge-compatible"
                            : "same/mooted"),
                           unode,
                           Taxonomy.MERGED);
                }
            } else {
                // Paraphyletic / conflicted.
                // Put the new children unplaced under the mrca of the placed children.
                union.reportConflict(node);
                Taxon target = chooseTarget(node.lub, sink);
                takeOn(node, target, Taxonomy.UNPLACED);
                reject(node,
                       (carriesOver(node)
                        ? "reject/dispersed"
                        : "reject/conflicted"),
                       target,
                       Taxonomy.INCONSISTENT);
            }
        }

        return node.mapped;
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
            node.answer = Answer.no(node, newnode, reason, null); //replace
            replacement.addChild(newnode);
            newnode.addFlag(flag);
        } else {
            node.answer = Answer.no(node, null, reason, null);
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
        Taxon newnode = node.alignWithNew(union, reason);
        newnode.addSource(node);
        return newnode;
	}

    void checkRejection(Taxon node, String reason) {
        if (union != null && union.importantIds != null) {
            List<Taxon> probe = union.importantIds.lookup(node.name);
            if (probe != null) {
                System.out.format("** Rejecting apparent OTU %s (ott:%s) because %s\n", node, node.id, reason);
                // this.show();
            }
        }
    }

    static boolean USE_LUB = false;

    Taxon chooseTarget(Taxon lub, Taxon sink) {
        if (USE_LUB)
            return lub;
        else if (lub.descendsFrom(sink)) // ??
            return lub;
        else
            return sink;
    }

    // implement a refinement
    void takeOld(Taxon node, Taxon newnode) {
        for (Taxon child: node.children)
            if (child.mapped != null && !child.mapped.isDetached() && child.mapped.isPlaced())
                child.mapped.changeParent(newnode);
    }

    // Does it have any children that need to go somewhere?
    boolean carriesOver(Taxon node) {
        for (Taxon child: node.children) {
            Taxon augChild = child.mapped;
            if (augChild == null)
                ;
            else if (augChild.isDetached())
                return true;
        }
        return false;
    }

    boolean consistentp(Taxon node) {
        // In this context 'consistent' simply means that all old children of
        // the new node have the same parent.  (the placed ones at least.)
        for (Taxon child : node.children) {
            Taxon m = child.mapped;
            if (m != null && !m.isDetached() && m.isPlaced() && m.parent != node.lub)
                return false;
        }
        return true;
    }

	// If all of the old children have node.lub as their parent,
	// then we can add the old children to a new union taxon,
	// which (if all goes well) will get inserted back into the union tree
	// under node.lub.

	// This is a cheat because some of the old children's siblings
	// might be more correctly classified as belonging to the new
	// taxon, rather than being siblings.  So we might want to
	// further qualify this.

	// (This feature is essential for mapping NCBI onto Silva.)

	// Caution: See https://github.com/OpenTreeOfLife/opentree/issues/73 ...
	// family as child of subfamily is confusing.
	// ranks.get(node1.rank) <= ranks.get(node2.rank) ....

	boolean refinementp(Taxon node) {
        if (node.lub.children == null) return false;
        int oldCount = 0, lubCount = 0;
        Taxon trouble = null;
        for (Taxon child : node.children)
            if (child.mapped != null && !child.mapped.isDetached()) {
                Taxon old = child.mapped;
                if (old.isPlaced()) {
                    if (old.parent != node.lub) {
                        this.markEvent("not-refinement/not-consistent");
                        return false;
                    }
                    ++oldCount;
                }
            }
        for (Taxon child : node.lub.children) {
            if (child.isPlaced()) {
                ++lubCount;
                if (child.comapped == null) trouble = child;
            }
        }
        // Since every placed old child has node.lub as its parent,
        // we know that oldCount <= lubCount.
        if (oldCount == 0) {
            this.markEvent("not-refinement/no-ingroup");
            return false;
        }
        if (oldCount == lubCount) {
            this.markEvent("not-refinement/no-outgroup");
            return false;
        }
        if (node.isAnnotatedHidden()) {
            // Prevent non-priority inner taxa from entering in Index Fungorum
            this.markEvent("not-refinement/hidden");
            return false;
        }
        int count = oldCount + (carriesOver(node) ? 1 : 0);
        if (trouble != null) {
            // If we do decide to allow these, we
            // ought to flag the siblings somehow.
            this.markEvent("not-refinement/trouble");
            return false;
        } else if (count == 1) {
            this.markEvent("not-refinement/single");
            return false;
        } else {
            this.markEvent("refinement/perfect");
            return true;
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
                Taxon nu = augChild;
                if (target == nu)
                    ;               // Lacrymaria Morganella etc. - shouldn't happen
                else if (target.descendsFrom(nu)) {
                    System.out.format("** Adoption would create a cycle\n");
                    System.out.format("%s ?< ", nu);
                    target.showLineage(nu.parent);

                    // Need to do something with it
                    reject(nu, "reject/cycle", union.forest, Taxonomy.UNPLACED);
                    target.taxonomy.addRoot(nu);
                } else {
                    //if (??child??.isRoot())
                    //    this.markEvent("placed-former-root");
                    target.addChild(nu); // if unplaced, stay unplaced
                    nu.addFlag(flags);
                }
            } else if (!augChild.isPlaced() && child.isPlaced()) {
                Taxon p = augChild;
                // is target a better (more specific) placement for p than p's current parent?
                if (!target.descendsFrom(p.parent))
                    this.markEvent("placeable-does-not-descend");
                else if (target == p.parent) {
                    // A placement here promotes an unplaced taxon in union to placed...
                    // sort of dangerous, because later taxonomies (e.g. worms) tend to be unreliable
                    if (flags > 0) {
                        this.markEvent("not-placed/already-unplaced");
                    } else {
                        // System.out.format("| %s not placed because %s goes to %s\n", p, source, p.parent);
                        this.markEvent("not-placed/same-taxon");
                        // p.properFlags &= ~Taxonomy.INCERTAE_SEDIS_ANY
                    }
                } else if (target.descendsFrom(p)) {
                    if (false)
                        System.out.format("| Moving %s from %s to %s (says %s) would lose information\n",
                                          p, p.parent, target, source);
                    this.markEvent("not-placed/would-lose-information");
                } else if (p.isRoot()) {
                    System.out.format("| %s moved from root to %s because %s\n", p, target, source);
                    this.markEvent("promoted/from-root");
                    p.changeParent(target, 0);
                } else {
                    //System.out.format("| %s moved to %s because %s, was under %s\n", p, target, source, p.parent);
                    this.markEvent("promoted/internal");
                    p.changeParent(target, flags);
                }
            }
        }
        return target;
    }

}

class Conflict {
	Taxon paraphyletic;				// in source taxonomy
	Taxon unode;					// child, in union taxonomy
	Conflict(Taxon paraphyletic, Taxon unode) {
		this.paraphyletic = paraphyletic; this.unode = unode;
	}
    static String formatString = "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s";
	public String toString() {
		// cf. Taxon.mrca
		Taxon[] div = paraphyletic.divergence(unode);
        try {
		if (div != null) {
			Taxon a = div[0];
			Taxon b = div[1];
			int da = a.getDepth();
			return String.format(formatString,
								 da,
								 paraphyletic.name, paraphyletic.getQualifiedId(),
								 unode.name, unode.putativeSourceRef(),
								 unode.parent.name, unode.parent.putativeSourceRef(),
								 a.name, a.putativeSourceRef(),
								 b.name, b.putativeSourceRef());
		} else
			return String.format(formatString,
								 "?",
								 paraphyletic.name, paraphyletic.getQualifiedId(),
								 unode.name, unode.putativeSourceRef(),
								 (unode.isRoot() ? "" :
                                  unode.parent.name),
								 (unode.isRoot() ? "" :
                                  unode.parent.putativeSourceRef()),
                                 "", "",
								 "", "");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.format("*** info: %s %s %s\n", paraphyletic, unode, div);
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

	static Criterion division =
		new Criterion() {
			public String toString() { return "same-division"; }
			Answer assess(Taxon subject, Taxon target) {
				Taxon xdiv = subject.getDivision();
				Taxon ydiv = target.getDivision();
				if (xdiv == ydiv || xdiv.noMrca() || ydiv.noMrca())
					return Answer.NOINFO;
				else if (xdiv.descendsFrom(ydiv) || ydiv.descendsFrom(xdiv))
                    // maybe this should be a 'no' or a provisional 'no'
                    return Answer.NOINFO;
                else
                    return Answer.heckNo(subject, target, "not-same/division", xdiv.name);
			}
		};

	static Criterion eschewTattered =
		new Criterion() {
			public String toString() { return "eschew-tattered"; }
			Answer assess(Taxon x, Taxon target) {
				if (!target.isPlaced() //from a previous merge
					&& target.isHomonym())  
					return Answer.weakNo(x, target, "not-same/unplaced", null);
				else
					return Answer.NOINFO;
			}
		};

	// x is source node, target is union node

	static Criterion lineage =
		new Criterion() {
			public String toString() { return "same-ancestor"; }
			Answer assess(Taxon x, Taxon target) {
				Taxon y0 = target.scan(x.taxonomy);	  // ignore names not known in both taxonomies
				Taxon x0 = x.scan(target.taxonomy);
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

	static boolean online(String name, Taxon node) {
		for ( ; node != null; node = node.parent)
			if (node.name.equals(name)) return true;
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
					 (x.rank != Taxonomy.NO_RANK &&
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
		byRank, byPrimaryName, elimination };

    boolean metBy(Taxon node, Taxon unode) {
        return this.assess(node, unode).value > Answer.DUNNO;
    }

}

// Values for 'answer'
//	 3	 good match - to the point of being uninteresting
//	 2	 yes  - some evidence in favor, maybe some evidence against
//	 1	 weak yes  - evidence from name only
//	 0	 no information
//	-1	 weak no - some evidence against
//	-2	  (not used)
//	-3	 no brainer - gotta be different

// Subject is in source taxonomy, target is in union taxonomy

class Answer {
	Taxon subject, target;					// The question is: Should subject be mapped to target?
	int value;					// YES, NO, etc.
	String reason;
	String witness = null;
	//gate c14
	Answer(Taxon subject, Taxon target, int value, String reason, String witness) {
        if (subject == null)
            throw new RuntimeException("Subject of new Answer is null");
		this.subject = subject; this.target = target;
		this.value = value;
		this.reason = reason;
		this.witness = witness;
	}

    Answer() {
        this.subject = null;
        this.target = null;
        this.value = DUNNO;
        this.reason = "no-info";
    }

	static final int HECK_YES = 3;
	static final int YES = 2;
	static final int WEAK_YES = 1;
	static final int DUNNO = 0;
	static final int WEAK_NO = -1;
	static final int NO = -2;
	static final int HECK_NO = -3;

    boolean isYes() { return value > 0; }

	static Answer heckYes(Taxon subject, Taxon target, String reason, String witness) { // Uninteresting
		return new Answer(subject, target, HECK_YES, reason, witness);
	}

	static Answer yes(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, YES, reason, witness);
	}

	static Answer weakYes(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, WEAK_YES, reason, witness);
	}

	static Answer noinfo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, DUNNO, reason, witness);
	}

	static Answer weakNo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, WEAK_NO, reason, witness);
	}

	static Answer no(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, NO, reason, witness);
	}

	static Answer heckNo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, HECK_NO, reason, witness);
	}

	static Answer NOINFO = new Answer();

	// Does this determination warrant the display of the log entries
	// for this name?
	boolean isInteresting() {
		return (this.value < HECK_YES) && (this.value > HECK_NO) && (this.value != DUNNO);
	}

	// Cf. dumpLog()
	String dump() {
		return
			(((this.target != null ? this.target.name :
			   this.subject.name))
			 + "\t" +

			 this.subject.getQualifiedId().toString() + "\t" +

			 (this.value > DUNNO ?
			  "=>" :
			  (this.value < DUNNO ? "not=>" : "-")) + "\t" +

			 (this.target == null ? "?" : this.target.id) + "\t" +

			 this.reason + "\t" +

			 (this.witness == null ? "" : this.witness) );
	}
}



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
