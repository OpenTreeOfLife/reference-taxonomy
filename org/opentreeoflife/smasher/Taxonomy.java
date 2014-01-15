/*

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

  Some people think having multiple classes in one file is terrible
  programming style...  I'll split this into multiple files when I'm
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
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

public abstract class Taxonomy implements Iterable<Node> {
	Map<String, List<Node>> nameIndex = new HashMap<String, List<Node>>();
	Map<String, Node> idIndex = new HashMap<String, Node>();
	Set<Node> roots = new HashSet<Node>(1);
	protected String tag = null;
	int nextSequenceNumber = 0;
	String[] header = null;

	Integer sourcecolumn = null;
	Integer sourceidcolumn = null;
	Integer infocolumn = null;
	Integer preottolcolumn = null;
	JSONObject metadata = null;
    int taxid = -1234;    // kludge

	boolean smushp = false;

	Taxonomy() { }

	public String toString() {
		return "(taxonomy " + this.getTag() + ")";
	}

	public abstract UnionTaxonomy promote();

	public List<Node> lookup(String name) {
		return this.nameIndex.get(name);
	}

	public Node unique(String name) {
		List<Node> probe = this.nameIndex.get(name);
		// TBD: Maybe rule out synonyms?
		if (probe != null && probe.size() == 1)
			return probe.get(0);
		else 
			return this.idIndex.get(name);
	}

	void addToIndex(Node node) {
		String name = node.name;
		List<Node> nodes = this.nameIndex.get(name);
		if (nodes == null) {
			nodes = new ArrayList<Node>(1); //default is 10
			this.nameIndex.put(name, nodes);
		}
		nodes.add(node);
	}

	int cachedCount = -1;

	public int count() {
		if (cachedCount > 0) return cachedCount;
		int total = 0;
		for (Node root : this.roots)
			total += root.count();
		cachedCount = total;
		return total;
	}

	// Iterate over all nodes reachable from roots

	public Iterator<Node> iterator() {
		final List<Iterator<Node>> its = new ArrayList<Iterator<Node>>();
		its.add(this.roots.iterator());
		final Node[] current = new Node[1]; // locative
		current[0] = null;

		return new Iterator<Node>() {
			public boolean hasNext() {
				if (current[0] != null) return true;
				while (true) {
					if (its.size() == 0) return false;
					if (its.get(0).hasNext()) return true;
					else its.remove(0);
				}
			}
			public Node next() {
				Node node = current[0];
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

	static int globalTaxonomyIdCounter = 1;

	String getTag() {
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
		List<Node> probe = this.lookup("Caenorhabditis elegans");
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
            List<Node> probe2 = this.lookup("Asterales");
            if (probe2 != null) {
                String id = probe2.get(0).id;
                if (id.equals("4209")) this.tag = "ncbi";
                if (id.equals("414")) this.tag = "gbif";
                if (id.equals("1042120")) this.tag = "ott";
            }
        }
	}

	Node highest(String name) {
		Node best = null;
		List<Node> l = this.lookup(name);
		if (l != null) {
			int depth = 1 << 30;
			for (Node node : l)
				if (node.getDepth() < depth) {
					depth = node.getDepth();
					best = node;
				}
		}
		return best;
	}

	void investigateHomonyms() {
		int homs = 0;
		int sibhoms = 0;
		int cousinhoms = 0;
		for (String name : nameIndex.keySet()) {
			List<Node> nodes = this.nameIndex.get(name);
			if (nodes.size() > 1) {
				boolean homsp = false;
				boolean sibhomsp = false;
				boolean cuzhomsp = false;
				for (Node n1: nodes)
					for (Node n2: nodes) {
						int c = n1.id.length() - n2.id.length();
						if ((c < 0 || (c == 0 && n1.id.compareTo(n2.id) < 0)) &&
							n1.name.equals(name) &&
							n2.name.equals(name)) {
							homsp = true;
							if (n1.parent == n2.parent)
								sibhomsp = true;
							else if (n1.parent != null && n2.parent != null &&
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

	static Pattern tabVbarTab = Pattern.compile("\t\\|\t?");

    // DWIMmish - does Newick is strings starts with paren, otherwise
    // loads from directory

	public static SourceTaxonomy getTaxonomy(String designator) throws IOException {
		SourceTaxonomy tax = new SourceTaxonomy();
		if (designator.startsWith("("))
			tax.roots.add(tax.newickToNode(designator));
		else {
            if (!designator.endsWith("/")) {
                System.err.println("Taxonomy designator should end in / but doesn't: " + designator);
                designator = designator + "/";
            }
			System.out.println("--- Reading " + designator + " ---");
			tax.loadTaxonomy(designator);
		}
		tax.investigateHomonyms();
        return tax;
	}

    // load | dump all taxonomy files

	public void loadTaxonomy(String dirname) throws IOException {
		this.loadMetadata(dirname + "about.json");
		this.loadTaxonomyProper(dirname + "taxonomy.tsv");
        this.smush();
		this.loadSynonyms(dirname + "synonyms.tsv");
	}

	// This gets overridden in the UnionTaxonomy class
	public void dump(String outprefix) throws IOException {
        new File(outprefix).mkdirs();
		this.assignNewIds(0);
		this.analyze();
		this.dumpNodes(this.roots, outprefix);
		this.dumpSynonyms(outprefix + "synonyms.tsv");
		this.dumpMetadata(outprefix + "about.json");
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

				Object smushp = ((Map)obj).get("smush");
				if (smushp != null) {
					System.out.println("smushp is " + smushp);
					this.smushp = smushp.equals("yes") || smushp == Boolean.TRUE;
				}
			}

		} catch (ParseException e) {
			System.err.println(e);
		}
		fr.close();
	}

	static String NO_RANK = null;

	abstract void dumpMetadata(String filename) throws IOException;

    // load | dump taxonomy proper

	void loadTaxonomyProper(String filename) throws IOException {
		BufferedReader br = Taxonomy.fileReader(filename);
		String str;
		int row = 0;

		// how to get this right?
		// Map<String,String> idReplacements = new HashMap<String,String>();

		while ((str = br.readLine()) != null) {
			String[] parts = tabVbarTab.split(str + "!");    // Java loses
			if (parts.length < 3) {
				System.out.println("Bad row: " + row + " has " + parts.length + " parts");
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
						this.preottolcolumn = headerx.get("preottol_id");
						continue;
					} else
						System.out.println("! No header row");
				}
				String id = parts[0];
				String name = parts[2];
				String rank = parts[3];
				if (rank.length() == 0 || rank.equals("no rank"))
					rank = NO_RANK;

				String parentId = parts[1];
				if (parentId.equals("null")) parentId = "";  // Index Fungorum
				if (parentId.equals(id)) {
					System.err.println("!! Taxon is its own parent: " + id);
					parentId = "";
				}

				Node node = this.idIndex.get(id);
				if (node == null) {
					// node was created earlier because it's the parent of some other node.
					node = new Node(this);
					node.setId(id); // stores into this.idIndex
				}

				if (parentId.length() > 0) {
					Node parent = this.idIndex.get(parentId);
					if (parent == null) {
						parent = new Node(this);	 //don't know parent's name yet
						parent.setId(parentId);
					}
					parent.addChild(node);
				} else
					roots.add(node);
				node.init(parts); // does setName
			}
			++row;
			if (row % 500000 == 0)
				System.out.println(row);
		}
		br.close();

		for (Node node : this.idIndex.values())
			if (node.name == null) {
				System.err.println("!! Identifier with no associated name, probably a missing parent: " + node.id);
				node.setName("undefined:" + node.id);
			}

		if (roots.size() == 0)
			System.err.println("*** No root nodes!");
		else {
			if (roots.size() > 1)
				System.err.println("There are " + roots.size() + " roots");
			int total = 0;
			for (Node root : roots)
				total += root.count();
			if (row != total)
				System.err.println(this.getTag() + " is ill-formed: " +
								   row + " rows, but only " + 
								   total + " reachable from roots");
		}
	}

    // From stackoverflow
    public static final Pattern DIACRITICS_AND_FRIENDS
        = Pattern.compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");
    private static String stripDiacritics(String str) {
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

    // Fold sibling homonyms together into single taxa.
    // Optional step.

    public void smush() {
		List<Runnable> todo = new ArrayList<Runnable>();

        // Maps name without diacritics to name with diacritics
        Map<String,String> uncritical = new HashMap<String,String>();

        // 1. Find all diacritics
		for (final Node node : this.idIndex.values()) {
            String without = stripDiacritics(node.name);
            if (!without.equals(node.name)) {
                // System.err.println("Diacritics in " + node.name + " but not in " + without);
                // dumpName(node.name); dumpName(without);
                uncritical.put(without, node.name);
            }
        }
        if (uncritical.size() > 0)
            System.out.format("| %s names with diacritics\n", uncritical.size());
        // 2. When a node's name is missing diacritics but could have them,
        // add them by clobbering the name.  This may create sibling homonyms.
		int ncc = 0;
		for (final Node node : this.idIndex.values()) {
            String probe = uncritical.get(node.name);
            if (probe != null) {
                // node.name is without diacritics, probe.name is with
				if (++ncc < 10)
					System.err.format("Changing name: %s -> %s\n", node.name, probe);
                node.setName(probe);
			}
        }
        // 3. Add non-diacritic name as synonym of with-diacritic node
        // See below, following smushing!

		// Smush taxa that differ only in id.
		int siblingHomonymCount = 0;
		for (final Node node : this.idIndex.values())

			if (node.name != null && node.parent != null)

				// Search for a homonym node that can replace node
				for (final Node other : this.lookup(node.name)) {

					int c = other.id.length() - node.id.length();
					if ((c < 0 || (c == 0 && other.id.compareTo(node.id) < 0)) &&
						node.parent == other.parent &&
						(node.rank == Taxonomy.NO_RANK ?
						 other.rank == Taxonomy.NO_RANK :
						 node.rank.equals(other.rank))) {

						// node and other are sibling homonyms.
						// deprecate node, replace it with other.

						if (++siblingHomonymCount < 10)
							System.err.println((smushp ?
												"Smushing" :
												"Tolerating") +
											   " sibling homonym " + node.id +
											   " => " + other.id +
											   ", name = " + node.name);
						else if (siblingHomonymCount == 10)
							System.err.println("...");

						if (smushp) {
							// There might be references to this id from the synonyms file
							final Taxonomy tax = this;
							todo.add(new Runnable() //ConcurrentModificationException
								{
									public void run() {
										if (node.children != null)
											for (Node child : new ArrayList<Node>(node.children))
												// might create new sibling homonyms...
												child.changeParent(other);
										node.prune();  // removes name from index
										tax.idIndex.put(node.id, other);
										other.addSource(node);
									}});
						}
						// No need to keep searching for appropriate homonym, node
						// has been flushed, try next homonym in the set.
						break;
					}
                }

		for (Runnable r : todo) r.run();

		if (siblingHomonymCount > 0)
			System.err.println("" + siblingHomonymCount + " sibling homonyms");

		for (Node node : this.idIndex.values())
			// if (node.parent == null && !roots.contains(node)) ...
			if (node.parent != null) {
				Node replacement = this.idIndex.get(node.parent.id);
				if (replacement != node.parent) {
					System.err.println("Post-smushing kludge: " + node.parent.id + " => " + replacement.id);
					node.parent = replacement;
				}
			}

        int critcount = 0;

        // 3. Add non-diacritic name as synonym of with-diacritic node
        for (String without : uncritical.keySet()) {
            String with = uncritical.get(without);
            List<Node> nodes = this.nameIndex.get(with);
            if (nodes != null)
                for (Node node : nodes) {
                    // Almost always only one node!!
                    if (++critcount <= 1 || nodes.size() > 1)
                        System.out.format("Adding %s as synonym for %s\n", without, node);
                    addSynonym(without, node);
                }
        }

    }

	void dumpNodes(Collection<Node> nodes, String outprefix) throws IOException {
		PrintStream out = Taxonomy.openw(outprefix + "taxonomy.tsv");

		out.println("uid\t|\tparent_uid\t|\tname\t|\trank\t|\tsourceinfo\t|\tuniqname\t|\tflags\t|\t"
					// 0	 1				2		 3		  4				 5             6
					);

		for (Node node : nodes) {
			if (node == null)
				System.err.println("null in nodes list!?" );
			else if (!node.prunedp)
				dumpNode(node, out, true);
		}
		out.close();
	}

	// Recursive!
	void dumpNode(Node node, PrintStream out, boolean rootp) {
		// 0. uid:
		out.print((node.id == null ? "?" : node.id) + "\t|\t");
		// 1. parent_uid:
		out.print(((node.parent == null || rootp) ? "" : node.parent.id)  + "\t|\t");
		// 2. name:
		out.print((node.name == null ? "?" : node.name)
				  + "\t|\t");
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
		out.print("\t|\t");
		// was: out.print(((node.flags != null) ? node.flags : "") + "\t|\t");

		out.println();

		if (node.children != null)
			for (Node child : node.children) {
				if (child == null)
					System.err.println("null in children list!? " + node);
				else
					dumpNode(child, out, false);
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
			while ((str = br.readLine()) != null) {
				String[] parts = tabVbarTab.split(str);
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
					String type = (parts.length >= type_column ?
								   parts[type_column] :
								   "");
					Node node = this.idIndex.get(id);
					if (type.equals("in-part")) continue;
					if (type.equals("includes")) continue;
					if (type.equals("type material")) continue;
					if (type.equals("authority")) continue;    // keep?
					if (node == null) {
						if (++losers < 20)
							System.err.println("Identifier " + id + " unrecognized for synonym " + syn);
						else if (losers == 20)
							System.err.println("...");
						continue;
					}
					if (node.name.equals(syn)) {
						if (++losers < 20)
							System.err.println("Putative synonym " + syn + " is the primary name of " + id);
						else if (losers == 20)
							System.err.println("...");
						continue;
					}
					addSynonym(syn, node);
					++count;
				}
			}
			br.close();
			if (count > 0)
				System.out.println("| " + count + " synonyms");
		}
	}

	// Returns true if a change was made

	public boolean addSynonym(String syn, Node node) {
		if (node.taxonomy != this)
			System.err.println("!? Synonym for a node that's not in this taxonomy: " + syn + " " + node);
		List<Node> nodes = this.nameIndex.get(syn);
		if (nodes != null) {
			if (nodes.contains(node))
				return false;    //lots of these System.err.println("Redundant synonymy: " + id + " " + syn);
		} else {
			nodes = new ArrayList<Node>(1);
			this.nameIndex.put(syn, nodes);
		}
		nodes.add(node);
		return true;
	}

	void dumpSynonyms(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("name\t|\tuid\t|\ttype\t|\tuniqname\t|\t");
		for (String name : this.nameIndex.keySet())
			for (Node node : this.nameIndex.get(name))
				if (!node.prunedp && !node.name.equals(name)) {
					String uniq = node.uniqueName();
					if (uniq.length() == 0) uniq = node.name;
					out.println(name + "\t|\t" +
								node.id + "\t|\t" +
								"" + "\t|\t" + // type, could be "synonym" etc.
								name + " (synonym for " + uniq + ")" +
								"\t|\t");
				}
		out.close();
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
	   #             flag descendents `unclassified` and elide,
	   #    		 else flag taxon `unclassified`.
	   # (elide = move children to their grandparent and mark as 'not_otu')
	   mycorrhizal samples
	   uncultured
	   unclassified
	   endophyte
	   endophytic

	   # rule 2: if the taxon has descendents, 
	   #             flag descendents `unclassified` and elide,
 	   # 			 else flag taxon 'not_otu'.
	   environmental

	   # rule 4: flag direct children `incertae_sedis` and elide taxon.
	   incertae sedis
	*/

    // NCBI only (not SILVA)
    public void analyzeOTUs() {
		for (Node root : this.roots)
			analyzeOTUs(root, 0);	// mutates the tree
    }

    // GBIF and IF only
    public void analyzeMajorRankConflicts() {
		for (Node root : this.roots)
			analyzeRankConflicts(root, true);
    }

	void analyze() {
		if (false) {
			// NCBI only
			for (Node root : this.roots)
				analyzeOTUs(root, 0);	// mutates the tree
			// GBIF and IF only
			for (Node root : this.roots)
				analyzeRankConflicts(root, true);
		}
        // All
		for (Node root : this.roots)
			analyzeRankConflicts(root, false);
        // All
		for (Node root : this.roots)
			analyzeContainers(root, 0);
        // All
		for (Node root : this.roots)
            analyzeBarren(root);
	}

	static final int NOT_OTU             =    1;
	static final int HYBRID          	 =    2;
	static final int VIRAL          	 =    4;
	static final int UNCLASSIFIED 	  	 =    8;
	static final int ENVIRONMENTAL 	  	 =   16;
	static final int INCERTAE_SEDIS 	 =   32;
	static final int SPECIFIC     	     =   64;
	static final int EDITED     	     =  128;
	static final int SIBLING_LOWER       =  512;
	static final int SIBLING_HIGHER      =   1 * 1024;
	static final int MAJOR_RANK_CONFLICT =   2 * 1024;
	static final int TATTERED 			 =   4 * 1024;
	static final int ANYSPECIES			 =   8 * 1024;
	static final int FORCED_VISIBLE		 =  16 * 1024;

	// Returns the node's rank (as an int).  In general the return
	// value should be >= parentRank, but conceivably funny things
	// could happen when combinings taxonomies.

	static int analyzeRankConflicts(Node node, boolean majorp) {
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
			Node highchild = null;

			// Preorder traversal
			// In the process, calculate rank of highest child
			for (Node child : node.children) {
				int rank = analyzeRankConflicts(child, majorp);
				if (rank >= 0) {
					if (rank < highrank) { highrank = rank; highchild = child; }
					if (rank > lowrank)  lowrank = rank;
				}
			}

			if (lowrank >= 0) {	// Any non-"no rank" children?

				// highrank is the highest (lowest-numbered) rank among all the children.
				// Similarly lowrank.  If they're different we have a 'rank conflict'.
				// Some 'rank conflicts' are 'minor', others are 'major'.
				if (highrank < lowrank) {
					// Suppose the parent is a class. We're looking at relative ranks of the children...
					// Two cases: order/family (minor), order/genus (major)
					int x = highrank / 100;       //e.g. order
					for (Node child : node.children) {
						int sibrank = child.rankAsInt;     //e.g. family or genus
						if (sibrank < 0) continue;		   // skip "no rank" children
						// we know sibrank >= highrank
						if (sibrank < lowrank)  // if child is higher rank than some sibling...
							// a family that has a sibling that's a genus
							// SIBLING_LOWER means 'has a sibling with lower rank'
                            if (!majorp)
                                child.properFlags |= SIBLING_LOWER; //e.g. family with genus sibling
						if (sibrank > highrank) {  // if lower rank than some sibling
							int y = (sibrank + 99) / 100; //genus->genus, subfamily->genus
							if (y > x+1 && majorp)
								// e.g. a genus that has an order as a sibling
								child.properFlags |= MAJOR_RANK_CONFLICT;
                            if (!majorp)
                                child.properFlags |= SIBLING_HIGHER; //e.g. genus with family sibling
						}
					}
				}
			}
			// Extra informational check.  See if ranks are inverted.
			if (highrank >= 0 && myrank > highrank)
				// The myrank == highrank case is weird too; there are about 200 of those.
				System.err.println("** Ranks out of order: " +
								   node + " " + node.rank + " has child " +
								   highchild + " " + highchild.rank);
		}
		return myrank;
	}

	// Each Node has two parallel sets of flags: 
	//   proper - applies particularly to this node
	//   inherited - applies to this node because it applies to an ancestor
	//     (where in some cases the ancestor may later be 'elided' so
	//     not an ancestor any more)

    // Flags to set for NCBI but not SILVA

	static void analyzeOTUs(Node node, int inheritedFlags) {
		// Before
		node.inheritedFlags |= inheritedFlags;

		// Prepare for recursive descent
		if (notOtuRegex.matcher(node.name).find()) 
			node.properFlags |= NOT_OTU;
		if (hybridRegex.matcher(node.name).find()) 
			node.properFlags |= HYBRID;
		if (viralRegex.matcher(node.name).find()) 
			node.properFlags |= VIRAL;

		int bequest = inheritedFlags | node.properFlags;		// What the children inherit

		// Recursive descent
		if (node.children != null)
			for (Node child : node.children)
				analyzeOTUs(child, bequest);
	}

    // Flags to set for all taxonomies.  Also elide container pseudo-taxa

	static void analyzeContainers(Node node, int inheritedFlags) {
		// Before
		node.inheritedFlags |= inheritedFlags;
		boolean elidep = false;

		if (node.children != null) {

			if (unclassifiedRegex.matcher(node.name).find()) {// Rule 3+5
				node.properFlags |= UNCLASSIFIED;
				elidep = true;
			}
			if (environmentalRegex.matcher(node.name).find()) {// Rule 3+5
				node.properFlags |= ENVIRONMENTAL;
				elidep = true;
			}
			if (incertae_sedisRegex.matcher(node.name).find()) {// Rule 3+5
				node.properFlags |= INCERTAE_SEDIS;
				elidep = true;
			}
		}

		int bequest = inheritedFlags | node.properFlags;		// What the children inherit

		// Recursive descent
		if (node.children != null)
			for (Node child : new ArrayList<Node>(node.children))
				analyzeContainers(child, bequest);

		// After
		if (elidep) {
            // Splice the node out of the hierarchy, but leave it as a
            // residual terminal non-OTU node.
            if (node.children != null && node.parent != null)
                for (Node child : new ArrayList<Node>(node.children))
                    child.changeParent(node.parent);
            node.properFlags |= NOT_OTU;
        }
	}

    // Set the ANYSPECIES flag of any taxon that is a species or has
    // one below it

	static void analyzeBarren(Node node) {
		boolean anyspeciesp = false;     // Any descendant is a species?
		if (node.rank != null) {
			Integer rank = ranks.get(node.rank);
			if (rank != null && rank >= SPECIES_RANK) {
				node.properFlags |= SPECIFIC;
				anyspeciesp = true;
			}
		}
        if (node.children != null)
			for (Node child : node.children) {
				analyzeBarren(child);
				if ((child.properFlags & ANYSPECIES) != 0) anyspeciesp = true;
			}
		if (anyspeciesp) node.properFlags |= ANYSPECIES;
	}

	static void printFlags(Node node, PrintStream out) {
		boolean needComma = false;
		if ((((node.properFlags | node.inheritedFlags) & NOT_OTU) != 0)
			|| ((node.inheritedFlags & ENVIRONMENTAL) != 0)) {
			if (needComma) out.print(","); else needComma = true;
			out.print("not_otu");
		}
		if (((node.properFlags | node.inheritedFlags) & VIRAL) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("viral");
		}
		if (((node.properFlags | node.inheritedFlags) & HYBRID) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("hybrid");
		}

        // Containers
		if ((node.properFlags & INCERTAE_SEDIS) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("incertae_sedis_direct");
		}
		if ((node.inheritedFlags & INCERTAE_SEDIS) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("incertae_sedis_inherited");
		}

		if ((node.properFlags & UNCLASSIFIED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("unclassified_direct");  // JAR prefers 'unclassified'
		}
		if ((node.inheritedFlags & UNCLASSIFIED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("unclassified_inherited"); // JAR prefers 'unclassified_indirect' ?
		}

		if ((node.properFlags & ENVIRONMENTAL) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("environmental");
		}

		if ((node.properFlags & MAJOR_RANK_CONFLICT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("major_rank_conflict_direct");
		}
		else if ((node.properFlags & SIBLING_HIGHER) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("sibling_higher");
		}
		if ((node.properFlags & SIBLING_LOWER) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("sibling_lower");
		}

		if ((node.inheritedFlags & MAJOR_RANK_CONFLICT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("major_rank_conflict_inherited");
		}

        // Misc
		if ((node.properFlags & TATTERED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("tattered");
		}

		if ((node.properFlags & EDITED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("edited");
		}

		if ((node.properFlags & FORCED_VISIBLE) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("forced_visible");
		}

		if ((node.inheritedFlags & SPECIFIC) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("infraspecific");
		} else if ((node.properFlags & ANYSPECIES) == 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("barren");
		}
	}
	
	static Pattern notOtuRegex =
		Pattern.compile(
						"\\bunidentified\\b|" +
						"\\bunknown\\b|" +
						"\\bmetagenome\\b|" +    // SILVA has a bunch of these
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
						"\\bviroids\\b|" +
						"\\bViruses\\b|" +
						"\\bviruses\\b|" +
						"\\bvirus\\b"
						);

	static Pattern unclassifiedRegex =
		Pattern.compile(
						"\\bmycorrhizal samples\\b|" +
						"\\buncultured\\b|" +
						"\\bunclassified\\b|" +
						"\\bendophyte\\b|" +
						"\\bendophytic\\b"
						);

	static Pattern environmentalRegex = Pattern.compile("\\benvironmental\\b");

	static Pattern incertae_sedisRegex = Pattern.compile("\\bincertae sedis\\b|\\bIncertae sedis\\b|\\bIncertae Sedis\\b");

	static String[][] rankStrings = {
		{"domain",
		 "superkingdom",
		 "kingdom",
		 "subkingdom",
		 "superphylum"},
		{"phylum",
		 "subphylum",
		 "superclass"},
		{"class",
		 "subclass",
		 "infraclass",
		 "superorder"},
		{"order",
		 "suborder",
		 "infraorder",
		 "parvorder",
		 "superfamily"},
		{"family",
		 "subfamily",
		 "tribe",
		 "subtribe"},
		{"genus",
		 "subgenus",
		 "species group",
		 "species subgroup"},
		{"species",
		 "infraspecificname",
		 "subspecies",
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

	// Called from --select1
	// TBD: synonyms and about file
	void select1(Node node, String outprefix) throws IOException {
		System.out.println("| Selecting " + node.name);
		List<Node> it = new ArrayList<Node>(1);
		it.add(node);
		this.dumpNodes(it, outprefix);

		if (this.metadata != null) {
			PrintStream out = Taxonomy.openw(outprefix + "about.json");
			out.println(this.metadata);
			out.close();
		}
	}

	// Select subtree rooted at a specified node

	public Taxonomy select(String designator) {
		Node sel = this.unique(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			Node selection = sel.select(tax2);
			System.out.println("| Selection has " + selection.count() + " taxa");
			tax2.roots.add(selection);

			// Synonyms
			int in = 0, out = 0;
			for (String name : this.nameIndex.keySet())
				for (Node node : this.nameIndex.get(name)) {
					if (node.mapped != null) {
						tax2.addSynonym(name, node.mapped);
						++in;
					} else
						++out;
				}
			System.out.println("| Nyms in: " + in + " out: " + out);

			return tax2;
		} else {
			System.err.println("Missing or ambiguous name: " + designator);
			return null;
		}
	}

	public Taxonomy sample(String designator, int count) {
		Node sel = this.unique(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			Node sample = sel.sample(count, tax2);
			System.out.println("| Sample has " + sample.count() + " taxa");
			tax2.roots.add(sample);
			// TBD: synonyms ?
			return tax2;
		} else {
			System.err.println("Missing or ambiguous name: " + designator);
			return null;
		}
	}

    public Taxonomy chop(int m, int n) throws java.io.IOException {
        Taxonomy tax = new SourceTaxonomy();
        List<Node> cuttings = new ArrayList<Node>();
        Node root = null;
        for (Node r : this.roots) root = r;    //bad kludge. uniroot assumed
        Node newroot = chop(root, m, n, cuttings, tax);
        tax.roots.add(newroot);
        System.err.format("Cuttings: %s Residue: %s\n", cuttings.size(), newroot.size());

        // Temp kludge ... ought to be able to specify the file name
        String outprefix = "chop/";
        new File(outprefix).mkdirs();
        for (Node cutting : cuttings) {
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

    Node chop(Node node, int m, int n, List<Node> chopped, Taxonomy tax) {
        int c = node.count();
        Node newnode = node.dup(tax);
        if (m < c && c <= n) {
            newnode.setName(newnode.name + " (" + node.size() + ")");
            chopped.add(node);
        } else if (node.children != null)
            for (Node child : node.children) {
                Node newchild = chop(child, m, n, chopped, tax);
                newnode.addChild(newchild);
            }
        return newnode;
    }

	public void deforestate() {
		List<Node> rootsList = new ArrayList<Node>(this.roots);
		if (rootsList.size() <= 1) return;
		Collections.sort(rootsList, new Comparator<Node>() {
				public int compare(Node x, Node y) {
					return y.count() - x.count();
				}
			});
		Node biggest = rootsList.get(0);
		int count1 = biggest.count();
		int count2 = rootsList.get(1).count();
		if (rootsList.size() >= 2 && count1 < count2*1000)
			System.err.format("*** Nontrivial forest: biggest is %s, 2nd biggest is %s\n", count1, count2);
		else
			System.out.format("| Deforesting: keeping biggest (%s), 2nd biggest is %s\n", count1, count2);
		for (Node root : rootsList)
			if (!root.equals(biggest))
				root.prune();
		this.roots = new HashSet<Node>(1);
		this.roots.add(biggest);
		System.out.format("| Removed %s smaller trees\n", rootsList.size()-1);
	}

	// -------------------- Newick stuff --------------------
	// Render this taxonomy as a Newick string.
	// This feature is very primitive and only for debugging purposes!

	public String toNewick() {
		StringBuffer buf = new StringBuffer();
		for (Node root: this.roots) {
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

	Node newickToNode(String newick) {
		java.io.PushbackReader in = new java.io.PushbackReader(new java.io.StringReader(newick));
		try {
			return this.newickToNode(in);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// TO BE DONE: Implement ; for reading forests

	Node newickToNode(java.io.PushbackReader in) throws java.io.IOException {
		int c = in.read();
		if (c == '(') {
			List<Node> children = new ArrayList<Node>();
			{
				Node child;
				while ((child = newickToNode(in)) != null) {
					children.add(child);
					int d = in.read();
					if (d < 0 || d == ')') break;
					if (d != ',')
						System.out.println("shouldn't happen: " + d);
				}
			}
			Node node = newickToNode(in); // get postfix name, x in (a,b)x
			if (node != null || children.size() > 0) {
				if (node == null) {
					node = new Node(this);
					// kludge
					node.setName("");
				}
				for (Node child : children)
					node.addChild(child);
				node.rank = (children.size() > 0) ? Taxonomy.NO_RANK : "species";
				return node;
			} else
				return null;
		} else {
			StringBuffer buf = new StringBuffer();
			while (true) {
				if (c < 0 || c == ')' || c == ',') {
					if (c >= 0) in.unread(c);
					if (buf.length() > 0) {
						Node node = new Node(this);
						node.rank = "species";
						node.setName(buf.toString());
						return node;
					} else return null;
				} else {
					buf.appendCodePoint(c);
					c = in.read();
				}
			}
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

	long maxid() {
		long id = -1;
		for (Node node : this) {
			long idAsLong;
			try {
				idAsLong = Long.parseLong(node.id);
				if (idAsLong > id) id = idAsLong;
			} catch (NumberFormatException e) {
				;
			}
		}
		return id;
	}

	void assignNewIds(long sourcemax) {
		long maxid = this.maxid();
		if (sourcemax > maxid) maxid = sourcemax;
		System.out.println("| Highest id before: " + maxid);
		for (Node node : this)
			if (node.id == null) {
				node.setId(Long.toString(++maxid));
				//node.addComment("new");
				node.markEvent("new-id");
			}
		System.out.println("| Highest id after: " + maxid);
	}

	public static SourceTaxonomy parseNewick(String newick) {
		SourceTaxonomy tax = new SourceTaxonomy();
		tax.roots.add(tax.newickToNode(newick));
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
			if (!editfile.getName().endsWith("~")) {
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
                        System.err.println("Ill-formed command: " + str);
                    else
                        applyOneEdit(row);
                }
            }
        }
        br.close();
    }

    //      command	name	rank	parent	context	sourceInfo
	// E.g. add	Acanthotrema frischii	species	Acanthotrema	Fungi	IF:516851

	void applyOneEdit(String[] row) {
		String command = row[0];
		String name = row[1];
		String rank = row[2];
		String parentName = row[3];
		String contextName = row[4];
		String sourceInfo = row[5];

		List<Node> parents = filterByContext(parentName, contextName);
		if (parents == null) {
			System.err.println("(add) Parent name " + parentName
							   + " missing in context " + contextName);
			return;
		}
		if (parents.size() > 1)
			System.err.println("? Ambiguous parent name: " + parentName);
        Node parent = parents.get(0);

		if (!parent.name.equals(parentName))
			System.err.println("(add) Warning: parent taxon name is a synonym: " + parentName);

		List<Node> existings = filterByContext(name, contextName);
        Node existing = null;
		if (existings != null) {
            if (existings.size() > 1)
                System.err.println("? Ambiguous taxon name: " + name);
            existing = existings.get(0);
        }

		if (command.equals("add")) {
			if (existing != null) {
				System.err.println("(add) Warning: taxon already present: " + name);
				if (existing.parent != parent)
					System.err.println("(add)  ... with a different parent: " +
									   existing.parent.name + " not " + parentName);
			} else {
				Node node = new Node(this);
				node.setName(name);
				node.rank = rank;
				node.setSourceIds(sourceInfo);
				parent.addChild(node);
				node.properFlags |= Taxonomy.EDITED;
			}
		} else if (command.equals("move")) {
			if (existing == null)
				System.err.println("(move) No taxon to move: " + name);
			else {
				if (existing.parent == parent)
					System.err.println("(move) Note: already in the right place: " + name);
				else {
					// TBD: CYCLE PREVENTION!
					existing.changeParent(parent);
					existing.properFlags |= Taxonomy.EDITED;
				}
			}
		} else if (command.equals("prune")) {
			if (existing == null)
				System.err.println("(prune) No taxon to prune: " + name);
			else
				existing.prune();

		} else if (command.equals("fold")) {
			if (existing == null)
				System.err.println("(fold) No taxon to fold: " + name);
			else {
				if (existing.children != null)
					for (Node child: existing.children)
						child.changeParent(parent);
				addSynonym(name, parent);
				existing.prune();
			}

		} else if (command.equals("flag")) {
			if (existing == null)
				System.err.println("(flag) No taxon to flag: " + name);
			else
				existing.properFlags |= Taxonomy.FORCED_VISIBLE;

		} else if (command.equals("incertae_sedis")) {
			if (existing == null)
				System.err.println("(flag) No taxon to flag: " + name);
			else
				existing.properFlags |= Taxonomy.INCERTAE_SEDIS;

		} else if (command.equals("synonym")) {
			// TBD: error checking
			if (existing != null)
				System.err.println("Synonym already known: " + name);
			else
				addSynonym(name, parent);

		} else
			System.err.println("Unrecognized edit command: " + command);
	}

	List<Node> filterByContext(String taxonName, String contextName) {
		List<Node> nodes = this.lookup(taxonName);
		if (nodes == null) return null;
		List<Node> fnodes = new ArrayList<Node>(1);
		for (Node node : nodes) {
			if (!node.name.equals(taxonName)) continue;
			// Follow ancestor chain to see whether this node is in the context
			for (Node chain = node; chain != null; chain = chain.parent)
				if (chain.name.equals(contextName)) {
					fnodes.add(node);
					break;
				}
		}
		return fnodes.size() == 0 ? null : fnodes;
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

    public void absorb(SourceTaxonomy tax, String tag) {
        tax.tag = tag;
        ((UnionTaxonomy)this).mergeIn(tax);
    }

    // Overridden in class UnionTaxonomy
	public void assignIds(SourceTaxonomy idsource) {
        ((UnionTaxonomy)this).assignIds(idsource);
    }

    // Look up a taxon by name or unique id.  Name must be unique in the taxonomy.
    public Node taxon(String name) {
        Node node = this.unique(name);
        if (node == null)
            System.err.format("Missing or ambiguous taxon: %s\n", name);
        return node;
    }

    public Node taxon(String name, String context) {
        List<Node> nodes = filterByContext(name, context);
        if (nodes == null) {
            System.err.format("Missing taxon: %s in context %s\n", name, context);
            return null;
        } else if (nodes.size() > 1) {
            System.err.format("Ambiguous taxon: %s in context %s\n", name, context);
            return null;
        }
        return nodes.get(0);
    }

    public void same(Node node1, Node node2) {
        Node unode, snode;
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
        } else {
            System.err.format("** One of the two nodes must be already mapped to the union taxonomy: %s %s\n",
                              node1, node2);
            return;
        }
        if (snode.mapped == unode) return;    // Already equated
        if (!(snode.taxonomy instanceof SourceTaxonomy)) {
            System.err.format("** One of the two nodes must come from a source taxonomy: %s %s\n", unode, snode);
            return;
        }
        if (unode.comapped != null) {    // see reset() - should never happen
            System.err.format("** The union node already has something mapped to it: %s\n", unode);
            return;
        }
        if (snode.mapped != null) {
            System.err.format("** The source is already mapped: %s\n", snode);
            return;
        }
        snode.unifyWith(unode);
    }

    public void describe() {
        System.out.format("%s ids, %s roots, %s names\n",
                          this.idIndex.size(),
                          this.roots.size(),
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

}  // End of class Taxonomy

class SourceTaxonomy extends Taxonomy {

	SourceTaxonomy() {
	}

	public UnionTaxonomy promote() {
		return new UnionTaxonomy(this);
	}

	void mapInto(UnionTaxonomy union, Criterion[] criteria) {

		if (this.roots.size() > 0) {

			Node.resetStats();
			System.out.println("--- Mapping " + this.getTag() + " into union ---");

			union.sources.add(this);

			int beforeCount = union.nameIndex.size();

            // this.reset();

			this.pin(union);

			// Consider all matches where names coincide.
			// When matching P homs to Q homs, we get PQ choices of which
			// possibility to attempt first.
			// Treat each name separately.

			// Be careful about the order in which names are
			// processed, so as to make the 'races' come out the right
			// way.  This is a kludge.

			Set<String> seen = new HashSet<String>();
			List<String> todo = new ArrayList<String>();
			// true / true
			for (Node node : this)
				if (!seen.contains(node.name)) {
					List<Node> unodes = union.nameIndex.get(node.name);
					if (unodes != null)
						for (Node unode : unodes)
							if (unode.name.equals(node.name))
								{ seen.add(node.name); todo.add(node.name); break; }
				}
			// true / synonym
			for (Node node : union)
				if (this.nameIndex.get(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// synonym / true
			for (Node node : this)
				if (union.nameIndex.get(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// synonym / synonym
			for (String name : this.nameIndex.keySet())
				if (union.nameIndex.get(name) != null &&
					!seen.contains(name))
					{ seen.add(name); todo.add(name); }

			int incommon = 0;
			int homcount = 0;
			for (String name : todo) {
				boolean painful = name.equals("Nematoda");
				List<Node> unodes = union.nameIndex.get(name);
				if (unodes != null) {
					++incommon;
					List<Node> nodes = this.nameIndex.get(name);
					if (false &&
						(((nodes.size() > 1 || unodes.size() > 1) && (++homcount % 1000 == 0)) || painful))
						System.out.format("| Mapping: %s %s*%s (name #%s)\n", name, nodes.size(), unodes.size(), incommon);
					new Matrix(name, nodes, unodes).run(criteria);
				}
			}
			System.out.println("| Names in common: " + incommon);

			Node.printStats();

			// Report on how well the merge went.
			this.mappingReport(union);
		}
	}

	// What was the fate of each of the nodes in this source taxonomy?

	void mappingReport(UnionTaxonomy union) {

		if (Node.windyp) {

			int total = 0;
			int nonamematch = 0;
			int prevented = 0;
			int added = 0;
			int corroborated = 0;

			// Could do a breakdown of matches and nonmatches by reason

			for (Node node : this) {
				++total;
				if (union.lookup(node.name) == null)
					++nonamematch;
				else if (node.mapped == null)
					++prevented;
				else if (node.mapped.novelp)
					++added;
				else
					++corroborated;
			}

			System.out.println("| Of " + total + " nodes in " + this.getTag() + ": " +
							   (total-nonamematch) + " with name in common, of which " + 
							   corroborated + " matched with existing, " + 
							   // added + " added, " +	  -- this hasn't happened yet
							   prevented + " blocked");
		}
	}

	// List determined manually and empirically
	void pin(UnionTaxonomy union) {
		String[][] pins = {
			// Stephen's list
			{"Fungi"},
			{"Bacteria"},
			{"Alveolata"},
			// {"Rhodophyta"},  creates duplicate of Cyanidiales
			{"Glaucocystophyceae"},
			{"Haptophyceae"},
			{"Choanoflagellida"},
			{"Metazoa", "Animalia"},
			{"Viridiplantae", "Plantae", "Chloroplastida"},
			// JAR's list
			{"Mollusca"},
			{"Arthropoda"},		// Tetrapoda, Theria
			{"Chordata"},
			// {"Eukaryota"},		// doesn't occur in gbif, but useful for ncbi/ncbi test merge
			// {"Archaea"},			// ambiguous in ncbi
		};
		int count = 0;
		for (int i = 0; i < pins.length; ++i) {
			String names[] = pins[i];
			Node n1 = null, n2 = null;
			// For each pinnable name, look for it in both taxonomies
			// under all possible synonyms
			for (int j = 0; j < names.length; ++j) {
				String name = names[j];
				Node m1 = this.highest(name);
				if (m1 != null) n1 = m1;
				Node m2 = union.highest(name);
				if (m2 != null) n2 = m2;
			}
			if (n1 != null && n2 != null) {
				n1.setDivision(names[0]);
				n2.setDivision(names[0]);
				n1.unifyWith(n2); // hmm.  TBD: move this out of here
				//n2.addComment("is-division", n1);
				++count;
			}
		}
		if (count > 0)
			System.out.println("Pinned " + count + " out of " + pins.length);
	}

	void augment(UnionTaxonomy union) {
		if (this.roots.size() > 0) {

			// Add heretofore unmapped nodes to union
			if (Node.windyp)
				System.out.println("--- Augmenting union with new nodes from " + this.getTag() + " ---");
			int startcount = union.count();
			int startroots = union.roots.size();

			for (Node root : this.roots) {

				// 'augment' always returns a node in the union tree, or null
				Node newroot = root.augment(union);

				if (newroot != null && newroot.parent == null && !union.roots.contains(newroot))
					union.roots.add(newroot);
			}

			int tidied = 0;

			// Tidy up the root set:
			List<Node> losers = new ArrayList<Node>();
			for (Node root : union.roots)
				if (root.parent != null) {
					losers.add(root);
					if (++tidied < 10)
						System.out.println("| No longer a root: " + root);
					else if (tidied == 10)
						System.out.println("| ...");
				}
			for (Node loser : losers)
				union.roots.remove(loser);

			// Sanity check:
			for (Node unode : union)
				if (unode.parent == null && !union.roots.contains(unode))
					System.err.println("| Missing root: " + unode);

			if (Node.windyp) {
				System.out.println("| Started with:		 " +
								   startroots + " trees, " + startcount + " taxa");
				Node.augmentationReport();
				System.out.println("| Ended with:		 " +
								   union.roots.size() + " trees, " + union.count() + " taxa");
			}
			if (union.nameIndex.size() < 10)
				System.out.println(" -> " + union.toNewick());
		}
	}
	
	// Propogate synonyms from source taxonomy to union.
	// Some names that are synonyms in the source might be primary names in the union,
	//  and vice versa.
	void copySynonyms(UnionTaxonomy union) {
		int count = 0;

		// For each name in source taxonomy...
		for (String syn : this.nameIndex.keySet()) {

			// For each node that the name names...
			for (Node node : this.nameIndex.get(syn))

				// If that node maps to a union node with a different name....
				if (node.mapped != null && !node.mapped.name.equals(syn)) {
					// then the name is a synonym of the union node too
					if (union.addSynonym(syn, node.mapped))
						++count;
                }
		}
		if (count > 0)
			System.err.println("| Added " + count + " synonyms to union");
	}

    // Overrides dumpMetadata in class Taxonomy
	void dumpMetadata(String filename)  throws IOException {
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

    void reset() {
        this.nextSequenceNumber = 0;
        for (Node root: this.roots) {
            // Clear out gumminess from previous merges
            root.reset();
            // Prepare for subsumption checks
            root.assignBrackets();
        }
    }

	void mergeIn(SourceTaxonomy source) {
		source.mapInto(this, Criterion.criteria);
		source.augment(this);
		source.copySynonyms(this);
        this.reset();           // ??? see Taxonomy.same()
		Node.windyp = true; //kludge
	}

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void assignIds(SourceTaxonomy idsource) {
		this.idsource = idsource;
		// idsource.tag = "ids";
		idsource.mapInto(this, Criterion.idCriteria);

		Node.resetStats();
		System.out.println("--- Assigning ids to union starting with " + idsource.getTag() + " ---");

		// Phase 1: recycle previously assigned ids.
		for (Node node : idsource) {
			Node unode = node.mapped;
			if (unode != null) {
				if (unode.comapped != node)
					System.err.println("Map/comap don't commute: " + node + " " + unode);
				Answer answer = assessSource(node, unode);
				if (answer.value >= Answer.DUNNO)
					Node.markEvent("keeping-id");
				else
					this.logAndMark(answer);
				unode.setId(node.id);
			}
		}

		// Phase 2: give new ids to union nodes that didn't get them above.
		long sourcemax = idsource.maxid();
		this.assignNewIds(sourcemax);
		// remember, this = union, idsource = previous version of ott

		Node.printStats();		// Taxon id clash
	}

	// Cf. assignIds()
	// x is a source node drawn from the idsource taxonomy file.
	// y is the union node it might or might not map to.

	static Answer assessSource(Node x, Node y) {
		QualifiedId ref = x.putativeSourceRef();
		if (ref != null) {
			String putativeSourceTag = ref.prefix;
			String putativeId = ref.id;

			// Find source node in putative source taxonomy, if any
			QualifiedId sourceThere = null;
			// Every union node should have at least one source node
			// ... except those added through the patch facility ...
			// FIX ME
			if (y.sourceIds == null) return Answer.NOINFO;    //won't happen?
			for (QualifiedId source : y.sourceIds)
				if (source.prefix.equals(putativeSourceTag)) {
					sourceThere = source;
					break;
				}

			if (sourceThere == null)
				return Answer.no(x, y, "note/different-source",
								 ref
								 + "->" +
								 y.getSourceIdsString());
			if (!putativeId.equals(sourceThere.id))
				return Answer.no(x, y, "note/different-source-id",
								 ref
								 + "->" +
								 sourceThere.toString());
			else
				return Answer.NOINFO;
		} else
			return Answer.NOINFO;
	}

	// x.getQualifiedId()

	void loadAuxIds(SourceTaxonomy aux) {
		this.auxsource = aux;
		aux.mapInto(this, Criterion.idCriteria);
	}

	void explainAuxIds(SourceTaxonomy aux, SourceTaxonomy idsource, String filename)
		throws IOException
	{
		System.out.println("--- Comparing new auxiliary id mappings with old ones ---");
		Node.resetStats();		// Taxon id clash
		PrintStream out = Taxonomy.openw(filename);
		Set<String> seen = new HashSet<String>();
		for (Node idnode : idsource) 
			if (idnode.mapped != null) {
				String idstringfield = idnode.auxids;
				if (idstringfield.length() == 0) continue;
				for (String idstring : idstringfield.split(",")) {
					Node auxnode = aux.idIndex.get(idstring);
					String reason;
					if (auxnode == null)
						reason = "not-found-in-aux-source";
					else if (auxnode.mapped == null)
						reason = "not-resolved-to-union";  //, auxnode, idstring
					else if (idnode.mapped == null)
						reason = "not-mapped";
					else if (auxnode.mapped != idnode.mapped)
						reason = "mapped-differently";	 // , auxnode.mapped, idstring
					else
						reason = "ok";	 // "Aux id in idsource mapped to union" // 107,576
					out.print(idstring
							  + "\t" +
							  ((auxnode == null || auxnode.mapped == null) ? "" : auxnode.mapped.id)
							  + "\t" +
							  reason + "\n");
					Node.markEvent("reason");
					seen.add(idstring);
				}
			}
		
		for (Node auxnode : aux) {
			if (auxnode.mapped != null && !seen.contains(auxnode.id))
				out.print("" + auxnode.id
						  + "\t" +
						  // Can be invoked in either of two ways... see Makefile
						  (auxnode.mapped.id != null?
						   auxnode.mapped.id :
						   auxnode.mapped.getSourceIdsString())
						  + "\t" +
						  "new" + "\n");
			Node.markEvent("new-aux-mapping");
		}
		Node.printStats();
		out.close();
	}

    // Method on union taxonomy.
    void dumpAuxIds(String outprefix) throws java.io.IOException {
        // TBD: Should be done as a separate operation
		if (this.auxsource != null)
			this.explainAuxIds(this.auxsource,
							   this.idsource,
							   outprefix + "aux.tsv");
    }

	// Overrides dump method in class Taxonomy.
	// outprefix should end with a / , but I guess . would work too

	public void dump(String outprefix) throws IOException {
        new File(outprefix).mkdirs();
		this.assignNewIds(0);	// If we've seen an idsource, maybe this has already been done
		this.analyze();
		this.dumpMetadata(outprefix + "about.json");

        Set<String> scrutinize = null;
		if (this.idsource != null) 
			scrutinize = this.dumpDeprecated(this.idsource, outprefix + "deprecated.tsv");
		this.dumpLog(outprefix + "log.tsv", scrutinize);

		this.dumpNodes(this.roots, outprefix);
		this.dumpSynonyms(outprefix + "synonyms.tsv");
	}

    // Overrides method in Taxonomy class

	void dumpMetadata(String filename)  throws IOException {
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
		out.println("id\tname\tsourceinfo\treason\twitness\treplacement");

		for (String id : idsource.idIndex.keySet()) {
			Node node = idsource.idIndex.get(id);
			if (node.mapped != null) continue;
			String reason = "?";
			String witness = "";
			String replacement = "*";
			Answer answer = node.deprecationReason;
			if (!node.id.equals(id)) {
				reason = "smushed";
			} else if (answer != null) {
				// assert answer.x == node
				reason = answer.reason;
				if (answer.y != null && answer.value > Answer.DUNNO)
					replacement = answer.y.id;
				if (answer.witness != null)
					witness = answer.witness;
			}
			out.println(id + "\t" +
						node.name + "\t" +
						node.getSourceIdsString() + "\t" +
						reason + "\t" +
						witness + "\t" +
						replacement);
		}
		out.close();

        Set<String> scrutinize = new HashSet<String>();
        for (String name : idsource.nameIndex.keySet())
            for (Node node : idsource.nameIndex.get(name))
                if (node.mapped == null) {
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
					"witness");

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
        else
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
			for (Node node : this)	// preorder
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
		if (answer.y != null) name = answer.y.name;
		if (name == null && answer.x != null) name = answer.x.name;	 //could be synonym
		if (name == null) return;					 // Hmmph.  No name to log it under.
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
		Node.markEvent(answer.reason);
	}
	void logAndReport(Answer answer) {
		this.log(answer);
		answer.x.report(answer.reason, answer.y, answer.witness);
	}

}

// or, Taxon

class Node {
	String id = null;
	Node parent = null;
	String name, rank = null;
	List<Node> children = null;
	Taxonomy taxonomy;			// For subsumption checks etc.
	String auxids = null;		// preottol id from taxonomy file, if any
	int size = -1;
	List<QualifiedId> sourceIds = null;
	Answer deprecationReason = null;
	Answer blockedp = null;
	boolean prunedp = false;

	int properFlags = 0, inheritedFlags = 0, rankAsInt = 0;

	// State during merge operation
	Node mapped = null;			// source node -> union node
	Node comapped = null;		// union node -> source node
	boolean novelp = false;     // added to union in last round?
	private String division = null;

	static boolean windyp = true;

	Node(Taxonomy tax) {
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
			for (Node child : children)
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
            List<Node> nodes = this.taxonomy.nameIndex.get(this.name);
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

	Node getParent() {
		return parent;
	}

	void addChild(Node child) {
		if (child.taxonomy != this.taxonomy) {
			this.report("Attempt to add child in different taxonomy", child);
			Node.backtrace();
		} else if (child.parent != null) {
			if (this.report("Attempt to steal child !!??", child))
				Node.backtrace();
		} else if (child == this) {
			if (this.report("Attempt to create self-loop !!??", child))
				Node.backtrace();
		} else {
			child.parent = this;
			if (this.children == null)
				this.children = new ArrayList<Node>();
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

	void changeParent(Node newparent) {
		Node p = this.parent;
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
	Node informative() {
		Node up = this.parent;
		while (up != null &&
			   (this.name.startsWith(up.name) || up.taxonomy.lookup(up.name).size() > 1))
			up = up.parent;
		return up;
	}

	// Homonym discounting synonyms
	boolean isHomonym() {
		List<Node> alts = this.taxonomy.nameIndex.get(this.name);
		if (alts == null) {
			System.err.println("Name not indexed !? " + this.name);
			return false;
		}
		for (Node alt : alts)
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

	void unifyWith(Node unode) {
		if (this.mapped == unode) return; // redundant
		if (this.mapped != null) {
			// Shouldn't happen - assigning single source taxon to two
			//	different union taxa
			if (this.report("Already assigned to node in union:", unode))
				Node.backtrace();
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
		if (Node.windyp)
			Node.printStats();
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

	void addComment(String comment, Node node) {
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

	void addSource(Node source) {
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
			System.err.println("!? [getQualifiedId] Node has no id: " + this.name);
			return new QualifiedId("?", this.name);
		}
	}

	// Method on Node, called for every node in the source taxonomy
	Node augment(UnionTaxonomy union) {

		Node newnode = null;
        int newflags = 0;

		if (this.children == null) {
			if (this.mapped != null) {
				newnode = this.mapped;
				Node.markEvent("mapped/tip");
			} else if (this.deprecationReason != null &&
					   // Create homonym iff it's an unquestionably bad match
					   this.deprecationReason.value > Answer.HECK_NO) {
				union.logAndMark(Answer.no(this, null, "blocked/tip", null));
			} else {
				newnode = new Node(union);
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

			List<Node> oldChildren = new ArrayList<Node>();  //parent != null
			List<Node> newChildren = new ArrayList<Node>();  //parent == null
			// Recursion step
			for (Node child: this.children) {
				Node augChild = child.augment(union);
				if (augChild != null) {
					if (augChild.parent == null)
						newChildren.add(augChild);
					else
						oldChildren.add(augChild);
				}
			}

			if (this.mapped != null) {
				for (Node augChild : newChildren)
					// *** This is where the Protozoa/Chromista trouble arises. ***
					// *** They are imported, then set as children of 'life'. ***
					this.mapped.addChild(augChild);
				this.reportOnMapping(union, (newChildren.size() == 0));
				newnode = this.mapped;

			} else if (oldChildren.size() == 0) {
				// New children only... just copying new stuff to union
				if (newChildren.size() > 0) {
					newnode = new Node(union);
					for (Node augChild: newChildren)
						newnode.addChild(augChild);    // ????
					union.logAndMark(Answer.heckYes(this, newnode, "new/internal", null));
				} else
					union.logAndMark(Answer.no(this, null, "lose/mooted", null));
				// fall through

			} else if (this.refinementp(oldChildren, newChildren)) {

					// Move the new internal node over to union taxonomy.
					// It will end up becoming a descendent of oldParent.
					newnode = new Node(union);
					for (Node nu : newChildren) newnode.addChild(nu);
					for (Node old : oldChildren) old.changeParent(newnode);   // Detach!!
					// 'yes' is interesting, 'heckYes' isn't
					union.logAndMark(Answer.yes(this, null, "new/insertion", null));
					// fall through

			} else if (newChildren.size() > 0) {
				// Paraphyletic.
				// Leave the old children where they are.
				// Put the new children in a "tattered" incertae-sedis-like container.
				newnode = new Node(union);
				for (Node augChild: newChildren)
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
				List<Node> losers = union.lookup(this.name);
				if (losers != null)
					for (Node loser : losers) {
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

	boolean refinementp(List<Node> oldChildren, List<Node> newChildren) {
		Node oldParent = null;
		for (Node old : oldChildren) {
			// old has a nonnull parent, by contruction
			if (oldParent == null) oldParent = old.parent;
			else if (old.parent != oldParent) return false;
		}
		for (Node nu : newChildren) {
			// alternatively, could do some kind of MRCA
			Node anc = this.parent;
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
		Node newnode = null;

		// --- Classify & report on what has just happened ---
		// TBD: Maybe decorate the newChildren with info about the match?...
		Node loser = this.antiwitness(this.mapped);
		Node winner = this.witness(this.mapped);
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
					Node.markEvent("mapped/internal"); // Exact topology match
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

	String toString(Node other) {
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
		Node.resetStats();
	}

	static void resetStats() {
		eventStats = new HashMap<String, Long>();
		eventStatNames = new ArrayList();
	}

	// convenience variants

	static boolean markEvent(String note) {
		return startReport(note);
	}

	boolean report(String note, Node othernode) {
		return this.report(note, othernode, null);
	}

	boolean report(String note, Node othernode, String witness) {
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

	boolean report(String note, List<Node> others) {
		if (startReport(note)) {
			System.out.println("| " + note);
			this.report1("", null);
			for (Node othernode : others)
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

	void report1(String tag, Node other) {
		String output = "";
		int i = 0;
		boolean seenmapped = false;
		for (Node n = this; n != null; n = n.parent) {
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
	boolean separationReport(String note, Node match) {
		if (startReport(note)) {
			System.out.println(note);

			Node nearestMapped = this;			 // in source taxonomy
			Node nearestMappedMapped = this;	 // in union taxonomy

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

			Node mrca = match.mrca(nearestMappedMapped); // in union tree
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
			Node n1 = this;
			for (int i = d3 - d1; i <= d3; ++i) {
				if (n1 == nearestMapped)
					n1 = nearestMappedMapped;
				System.out.println("  " + spaces.substring(0, i) + n1.toString(match));
				n1 = n1.parent;
			}
			Node n2 = match;
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
				for (Node child: children)
					size += child.size();
		}
		return size;
	}

	// Brute force count of nodes (more reliable than size() in presence of change)
	int count() {
		int count = 1;
		if (this.children != null)
			for (Node child : this.children)
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
			for (Node child : this.children)
				child.assignBrackets();
		this.end = this.taxonomy.nextSequenceNumber;
	}

	// Applied to a source node
	void getBracket(Taxonomy union) {
		if (this.end == NOT_SET) {
			Node unode = union.unique(this.name);
			if (unode != null)
				this.seq = unode.seq; // Else leave seq as NOT_SET
			if (this.children != null) {
				int start = Integer.MAX_VALUE;
				int end = -1;
				for (Node child : this.children) {
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
	boolean isNotSubsumedBy(Node unode) {
		this.getBracket(unode.taxonomy);
		return this.start < unode.start || this.end > unode.end; // spills out?
	}

	// Look for a member of this source taxon that's not a member of the union taxon,
	// but is a member of some other union taxon.
	Node antiwitness(Node unode) {
		getBracket(unode.taxonomy);
		if (this.start >= unode.start && this.end <= unode.end)
			return null;
		else if (this.children != null) { // it *will* be nonnull actually
			for (Node child : this.children)
				if (child.seq != NOT_SET && (child.seq < unode.start || child.seq >= unode.end))
					return child;
				else {
					Node a = child.antiwitness(unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

	// Look for a member of the source taxon that's also a member of the union taxon.
	Node witness(Node unode) { // assumes is subsumed by unode
		getBracket(unode.taxonomy);
		if (this.start >= unode.end || this.end <= unode.start) // Nonoverlapping => lose
			return null;
		else if (this.children != null) { // it *will* be nonnull actually
			for (Node child : this.children)
				if (child.seq != NOT_SET && (child.seq >= unode.start && child.seq < unode.end))
					return child;
				else {
					Node a = child.witness(unode);
					if (a != null) return a;
				}
		}
		return null;			// Shouldn't happen
	}

	// Find a near-ancestor (parent, grandparent, etc) node that's in
	// common with the other taxonomy
	Node scan(Taxonomy other) {
		Node up = this.parent;

		// Cf. informative() method
		// Without this we get ambiguities when the taxon is a species
		while (up != null && up.name != null && this.name.startsWith(up.name))
			up = up.parent;

		while (up != null && up.name != null && other.lookup(up.name) == null)
			up = up.parent;

		if (up != null && up.name == null) {
			System.err.println("!? Null name: " + up + " ancestor of " + this);
			Node u = this;
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

	Node mrca(Node b) {
		if (b == null) return null; // Shouldn't happen, but...
		else {
			Node a = this;
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
			Node last = children.get(this.children.size()-1);
			for (Node child : children) {
				child.appendNewickTo(buf);
				if (child != last)
					buf.append(",");
			}
			buf.append(")");
		}
		if (this.name != null)
			buf.append(name.replace('(','[').replace(')',']').replace(':','?'));
	}

	static Comparator<Node> compareNodes = new Comparator<Node>() {
		public int compare(Node x, Node y) {
			return x.name.compareTo(y.name);
		}
	};

	// Delete this node and all of its descendents.
	void prune() {
		if (this.children != null) {
			for (Node child : new ArrayList<Node>(children))
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
		List<Node> nodes = this.taxonomy.lookup(this.name);
		if (nodes == null) {
			System.err.format("!? Confused: %s in %s\n", this.name,
							  this.parent == null ? "(roots)" : this.parent.name);
			return "?";
		}
		for (Node other : nodes)
			if (other != this && other.name.equals(this.name)) {
				Node i = this.informative();
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

	static Comparator<Node> compareNodesBySize = new Comparator<Node>() {
		public int compare(Node x, Node y) {
			return x.count() - y.count();
		}
	};

    // Duplicate single node

    Node dup(Taxonomy tax) {
		Node dup = new Node(tax);
		dup.setName(this.name);
		dup.setId(this.id);
		dup.rank = this.rank;
		dup.sourceIds = this.sourceIds;
        return dup;
    }

	// Copy subtree

    Node select(Taxonomy tax) {
		Node sam = this.dup(tax);
		this.mapped = sam;
		if (this.children != null)
			for (Node child : this.children) {
				Node c = child.select(tax);
				sam.addChild(c);
			}
		return sam;
	}

	// The nodes of the resulting tree are a subset of size k of the
	// nodes from the input tree, sampled proportionally.

    Node sample(int k, Taxonomy tax) {
		if (k <= 0) return null;
		Node sam = this.dup(tax);

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
			for (Node child : this.children) {

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
				Node c = child.sample(dk, tax);
				if (c != null) {
					sam.addChild(c);
					k1 += c.count();
				}
				n1 = n2;

			}
		}

		return sam;
	}
}

// For each source node, consider all possible union nodes it might map to
// TBD: Exclude nodes that have 'prunedp' flag set

class Matrix {

	String name;
	List<Node> nodes;
	List<Node> unodes;
	int m;
	int n;
	Answer[][] suppressp;

	Matrix(String name, List<Node> nodes, List<Node> unodes) {
		this.name = name;
		this.nodes = nodes;
		this.unodes = unodes;
		m = nodes.size();
		n = unodes.size();
		if (m*n > 100)
		    System.out.format("!! Badly homonymic: %s %s*%s\n", name, m, n);
	}

	void clear() {
		suppressp = new Answer[m][];
		for (int i = 0; i < m; ++i)
			suppressp[i] = new Answer[n];
	}

	// Compare every node to every other node, according to a list of criteria.
	void run(Criterion[] criteria) {

		clear();

		// Log the fact that there are synonyms involved in these comparisons
		if (false)
			for (Node node : nodes)
				if (!node.name.equals(name)) {
					Node unode = unodes.get(0);
					((UnionTaxonomy)unode.taxonomy).logAndMark(Answer.noinfo(node, unode, "synonym(s)", node.name));
					break;
				}

		for (Criterion criterion : criteria)
			run(criterion);

		// see if any source node remains unassigned (ties or blockage)
		postmortem();
		suppressp = null;  //GC
	}

    // i, m,  node
    // j, n, unode

	void run(Criterion criterion) {
		int m = nodes.size();
		int n = unodes.size();
		int[] uniq = new int[m];	// union nodes uniquely assigned to each source node
		for (int i = 0; i < m; ++i) uniq[i] = -1;
		int[] uuniq = new int[n];	// source nodes uniquely assigned to each union node
		for (int j = 0; j < n; ++j) uuniq[j] = -1;
		Answer[] answer = new Answer[m];
		Answer[] uanswer = new Answer[n];

		for (int i = 0; i < m; ++i) { // For each source node...
			Node x = nodes.get(i);
			for (int j = 0; j < n; ++j) {  // Find a union node to map it to...
				if (suppressp[i][j] != null) continue;
				Node y = unodes.get(j);
				Answer z = criterion.assess(x, y);
				if (z.value == Answer.DUNNO)
					continue;
				((UnionTaxonomy)y.taxonomy).log(z);
				if (z.value < Answer.DUNNO) {
					suppressp[i][j] = z;
					continue;
				}
				if (answer[i] == null || z.value > answer[i].value) {
					uniq[i] = j;
					answer[i] = z;
				} else if (z.value == answer[i].value)
					uniq[i] = -2;

				if (uanswer[j] == null || z.value > uanswer[j].value) {
					uuniq[j] = i;
					uanswer[j] = z;
				} else if (z.value == uanswer[j].value)
					uuniq[j] = -2;
			}
		}
		for (int i = 0; i < m; ++i) // iterate over source nodes
			// Don't assign a single source node to two union nodes...
			if (uniq[i] >= 0) {
				int j = uniq[i];
				// Avoid assigning two source nodes to the same union node (synonym creation)...
				if (uuniq[j] >= 0 && suppressp[i][j] == null) {
					Node x = nodes.get(i); // == uuniq[j]
					Node y = unodes.get(j);

					// Block out column, to prevent other source nodes from mapping to the same union node
					for (int ii = 0; ii < m; ++ii)
						if (ii != i && suppressp[ii][j] == null)
							suppressp[ii][j] = Answer.no(nodes.get(ii),
														 y,
														 "excluded(" + criterion.toString() +")",
														 x.getQualifiedId().toString());
					// Block out row, to prevent this source node from mapping to multiple union nodes (!!??)
					for (int jj = 0; jj < n; ++jj)
						if (jj != j && suppressp[i][jj] == null)
							suppressp[i][jj] = Answer.no(x,
														 unodes.get(jj),
														 "coexcluded(" + criterion.toString() + ")",
														 null);

					Answer a = answer[i];
					if (x.mapped == y)
						;
					// Did someone else get there first?
					else if (y.comapped != null) {
						x.deprecationReason = a;
						a = Answer.no(x, y,
									  "lost-race-to-union(" + criterion.toString() + ")",
									  ("lost to " +
									   y.comapped.getQualifiedId().toString()));
					} else if (x.mapped != null) {
						x.deprecationReason = a;
						a = Answer.no(x, y, "lost-race-to-source(" + criterion.toString() + ")",
									  (y.getSourceIdsString() + " lost to " +
									   x.mapped.getSourceIdsString()));
					} else
						x.unifyWith(y);
					suppressp[i][j] = a;
				}
			}
	}

	// in x[i][j] i specifies the row and j specifies the column

	// Record reasons for mapping failure - for each unmapped source node, why didn't it map?
	void postmortem() {
		for (int i = 0; i < m; ++i) {
			Node node = nodes.get(i);
			// Suppress synonyms
			if (node.mapped == null) {
				int alts = 0;    // how many union nodes might we have gone to?
				int altj = -1;
				for (int j = 0; j < n; ++j)
					if (suppressp[i][j] == null
						// && unodes.get(j).comapped == null
						) { ++alts; altj = j; }
				UnionTaxonomy union = (UnionTaxonomy)unodes.get(0).taxonomy;
				Answer explanation;
				if (alts == 1) {
					// There must be multiple source nodes i1, i2, ... competing
					// for this one union node.  Merging them is (probably) fine.
					String w = null;
					for (int ii = 0; ii < m; ++ii)
						if (suppressp[ii][altj] == null) {
							Node rival = nodes.get(ii);    // in source taxonomy or idsource
							if (rival == node) continue;
							// if (rival.mapped == null) continue;  // ???
							QualifiedId qid = rival.getQualifiedId();
							if (w == null) w = qid.toString();
							else w += ("," + qid.toString());
						}
					explanation = Answer.noinfo(node, unodes.get(altj), "unresolved/contentious", w);
				} else if (alts > 1) {
					// Multiple union nodes to which this source can map... no way to tell
					// ids have not been assigned yet
					//    for (int j = 0; j < n; ++j) others.add(unodes.get(j).id);
					String w = null;
					for (int j = 0; j < n; ++j)
						if (suppressp[i][j] == null) {
							Node candidate = unodes.get(j);    // in union taxonomy
							// if (candidate.comapped == null) continue;  // ???
							if (candidate.sourceIds == null)
								System.err.println("?!! No source ids: " + candidate);
							QualifiedId qid = candidate.sourceIds.get(0);
							if (w == null) w = qid.toString();
							else w += ("," + qid.toString());
						}
					explanation = Answer.noinfo(node, null, "unresolved/ambiguous", w);
				} else {
					// Important case, mapping blocked, give gory details.
					// Iterate through the union nodes for this name that we didn't map to
					// and collect all the reasons.
					if (n == 1)
						explanation = suppressp[i][0];
					else {
						for (int j = 0; j < n; ++j)
							if (suppressp[i][j] != null) // how does this happen?
								union.log(suppressp[i][j]);
						String kludge = null;
						int badness = -100;
						for (int j = 0; j < n; ++j) {
							Answer a = suppressp[i][j];
							if (a == null)
								continue;
							if (a.value > badness)
								badness = a.value;
							if (kludge == null)
								kludge = a.reason;
							else if (j < 5)
								kludge = kludge + "," + a.reason;
							else if (j == 5)
								kludge = kludge + ",...";
						}
						if (kludge == null) {
							System.err.println("!? No reasons: " + node);
							explanation = Answer.NOINFO;
						} else
							explanation = new Answer(node, null, badness, "unresolved/blocked", kludge);
					}
				}
				union.logAndMark(explanation);
				// remember, source could be either gbif or idsource
				if (node.deprecationReason == null)
					node.deprecationReason = explanation;  
			}
		}
	}
}

// Assess a criterion for judging whether x <= y or not x <= y
// Positive means yes, negative no, zero I couldn't tell you
// x is source node, y is union node

abstract class Criterion {

	abstract Answer assess(Node x, Node y);

	// Ciliophora = ncbi:5878 = gbif:10 != gbif:3269382
	static QualifiedId[][] exceptions = {
		{new QualifiedId("ncbi","5878"),
		 new QualifiedId("gbif","10"),
		 new QualifiedId("gbif","3269382")},	// Ciliophora
		{new QualifiedId("ncbi","29178"),
		 new QualifiedId("gbif","389"),
		 new QualifiedId("gbif","4983431")}};	// Foraminifera

	static QualifiedId loser =
		new QualifiedId("silva", "AB033773/#6");   // != 713:83

	// This is obviously a horrible kludge, awaiting a rewrite
	// Foraminifera seems to have been fixed somehow
	static Criterion adHoc =
		new Criterion() {
			public String toString() { return "ad-hoc"; }
			Answer assess(Node x, Node y) {
				String xtag = x.taxonomy.getTag();
				for (QualifiedId[] exception : exceptions) {
					// x is from gbif, y is union
					if (xtag.equals(exception[1].prefix) &&
						x.id.equals(exception[1].id)) {
						System.out.println("| Trying ad-hoc match rule: " + x);
						if (y.sourceIds.contains(exception[0]))
							return Answer.yes(x, y, "ad-hoc", null);
					} else if (xtag.equals(exception[2].prefix) &&
							   x.id.equals(exception[2].id)) {
						System.out.println("| Trying ad-hoc mismatch rule: " + x);
						return Answer.no(x, y, "ad-hoc-not", null);
					}
				}
				if (false && x.name.equals("Buchnera")) {
					System.out.println("| Checking Buchnera: " + x + " " + y + " " + y.sourceIds);
					if (xtag.equals("study713") &&
						y.sourceIds.contains(loser)) {
						System.out.println("| Distinguishing silva:Buchnera from 713:Buchnera: " + x);
						return Answer.no(x, y, "Buchnera", null);
					}
				}
				return Answer.NOINFO;
			}
		};

	static Criterion division =
		new Criterion() {
			public String toString() { return "same-division"; }
			Answer assess(Node x, Node y) {
				String xdiv = x.getDivision();
				String ydiv = y.getDivision();
				if (xdiv == ydiv)
					return Answer.NOINFO;
				else if (xdiv != null && ydiv != null) {
					Answer a = Answer.heckNo(x, y, "different-division", xdiv);
					return a;
				} else
					return Answer.NOINFO;
			}
		};

	static Criterion eschewTattered =
		new Criterion() {
			public String toString() { return "eschew-tattered"; }
			Answer assess(Node x, Node y) {
				if ((y.properFlags & Taxonomy.TATTERED) != 0 //from a previous merge
					&& y.isHomonym()
					)  
					return Answer.weakNo(x, y, "eschew-tattered", null);
				else
					return Answer.NOINFO;
			}
		};

	// x is source node, y is union node

	static Criterion lineage =
		new Criterion() {
			public String toString() { return "same-ancestor"; }
			Answer assess(Node x, Node y) {
				Node y0 = y.scan(x.taxonomy);	  // ignore names not known in both taxonomies
				Node x0 = x.scan(y.taxonomy);
				if (x0 == null || y0 == null)
					return Answer.NOINFO;

				if (x0.name == null)
					System.err.println("! No name? 1 " + x0 + "..." + y0);
				if (y0.name == null)
					System.err.println("! No name? 2 " + x0 + "..." + y0);

				if (x0.name.equals(y0.name))
					return Answer.heckYes(x, y, "same-parent/direct", x0.name);
				else if (online(x0.name, y0))
					// differentiating the two levels
					// helps to deal with the Nitrospira situation (7 instances)
					return Answer.heckYes(x, y, "same-parent/extended-l", x0.name);
				else if (online(y0.name, x0))
					return Answer.heckYes(x, y, "same-parent/extended-r", y0.name);
				else
					// Incompatible parents.  Who knows what to do.
					return Answer.NOINFO;
			}
		};

	static boolean online(String name, Node node) {
		for ( ; node != null; node = node.parent)
			if (node.name.equals(name)) return true;
		return false;
	}

	static Criterion subsumption =
		new Criterion() {
			public String toString() { return "overlaps"; }
			Answer assess(Node x, Node y) {
				Node a = x.antiwitness(y);
				Node b = x.witness(y);
				if (b != null) { // good
					if (a == null)	// good
						// 2859
						return Answer.heckYes(x, y, "is-subsumed-by", b.name);
					else
						// 94
						return Answer.yes(x, y, "overlaps", b.name);
				} else {
					if (a == null)
						// ?
						return Answer.NOINFO;
					else		// bad
						// 13 ?
						return Answer.no(x, y, "incompatible-with", a.name);
				}
			}
		};

	// Match NCBI or GBIF identifiers
	// This kicks in when we try to map the previous OTT to assign ids, after we've mapped GBIF.
	// x is a node in the old OTT.  y, the union node, is in the new OTT.
	static Criterion compareSourceIds =
		new Criterion() {
			public String toString() { return "same-qualified-id"; }
			Answer assess(Node x, Node y) {
				// x is source node, y is union node.
				// Two cases:
				// 1. Mapping x=NCBI to y=union(SILVA): y.sourceIds contains x.id
				// 2. Mapping idsource to union: x.sourceIds contains ncbi:123
				// compare x.id to y.sourcenode.id
				QualifiedId xid = x.getQualifiedId();
				for (QualifiedId ysourceid : y.sourceIds)
					if (xid.equals(ysourceid))
						return Answer.yes(x, y, "same-qualified-id-1", null);
				if (x.sourceIds != null)
					for (QualifiedId xsourceid : x.sourceIds)
						for (QualifiedId ysourceid : y.sourceIds)
							if (xsourceid.equals(ysourceid))
								return Answer.yes(x, y, "same-qualified-id-2", null);
				return Answer.NOINFO;
			}
		};

	// Buchnera in Silva and 713
	static Criterion knowDivision =
		new Criterion() {
			public String toString() { return "same-division-knowledge"; }
			Answer assess(Node x, Node y) {
				String xdiv = x.getDivision();
				String ydiv = y.getDivision();
				if (xdiv != ydiv) // One might be null
					// Evidence of difference, good enough to prevent name-only matches
					return Answer.heckNo(x, y, "not-same-division-knowledge", xdiv);
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Steganina, Tripylina in NCBI - they're distinguishable by their ranks
	static Criterion byRank =
		new Criterion() {
			public String toString() { return "same-rank"; }
			Answer assess(Node x, Node y) {
				if ((x == null ?
					 x == y :
					 (x.rank != Taxonomy.NO_RANK &&
					  x.rank.equals(y.rank))))
					// Evidence of difference, but not good enough to overturn name evidence
					return Answer.weakYes(x, y, "same-rank", x.rank);
				else
					return Answer.NOINFO;
			}
		};

	static Criterion byPrimaryName =
		new Criterion() {
			public String toString() { return "same-primary-name"; }
			Answer assess(Node x, Node y) {
				if (x.name.equals(y.name))
					return Answer.weakYes(x, y, "same-primary-name", x.name);
				else
					return Answer.NOINFO;
			}
		};

	// E.g. Paraphelenchus
	// E.g. Steganina in NCBI - distinguishable by their ranks
	static Criterion elimination =
		new Criterion() {
			public String toString() { return "name-in-common"; }
			Answer assess(Node x, Node y) {
				return Answer.weakYes(x, y, "name-in-common", null);
			}
		};

	static Criterion[] criteria = { adHoc, division,
									eschewTattered,
									lineage, subsumption,
									compareSourceIds,
									// knowDivision,
									byRank, byPrimaryName, elimination };

	static Criterion[] idCriteria = criteria;

}

// Values for 'answer'
//	 3	 good match - to the point of being uninteresting
//	 2	 yes  - some evidence in favor, maybe some evidence against
//	 1	 weak yes  - evidence from name only
//	 0	 no information
//	-1	 weak no - some evidence against
//	-2	  (not used)
//	-3	 no brainer - gotta be different


class Answer {
	Node x, y;					// The question is: Should x be mapped to y?
	int value;					// YES, NO, etc.
	String reason;
	String witness;
	//gate c14
	Answer(Node x, Node y, int value, String reason, String witness) {
		this.x = x; this.y = y;
		this.value = value;
		this.reason = reason;
		this.witness = witness;
	}

	static final int HECK_YES = 3;
	static final int YES = 2;
	static final int WEAK_YES = 1;
	static final int DUNNO = 0;
	static final int WEAK_NO = -1;
	static final int NO = -2;
	static final int HECK_NO = -3;

	static Answer heckYes(Node x, Node y, String reason, String witness) { // Uninteresting
		return new Answer(x, y, HECK_YES, reason, witness);
	}

	static Answer yes(Node x, Node y, String reason, String witness) {
		return new Answer(x, y, YES, reason, witness);
	}

	static Answer weakYes(Node x, Node y, String reason, String witness) {
		return new Answer(x, y, WEAK_YES, reason, witness);
	}

	static Answer noinfo(Node x, Node y, String reason, String witness) {
		return new Answer(x, y, DUNNO, reason, witness);
	}

	static Answer weakNo(Node x, Node y, String reason, String witness) {
		return new Answer(x, y, WEAK_NO, reason, witness);
	}

	static Answer no(Node x, Node y, String reason, String witness) {
		return new Answer(x, y, NO, reason, witness);
	}

	static Answer heckNo(Node x, Node y, String reason, String witness) {
		return new Answer(x, y, HECK_NO, reason, witness);
	}

	static Answer NOINFO = new Answer(null, null, DUNNO, "no-info", null);

	// Does this determination warrant the display of the log entries
	// for this name?
	boolean isInteresting() {
		return (this.value < HECK_YES) && (this.value > HECK_NO) && (this.value != DUNNO);
	}

	// Cf. dumpLog()
	String dump() {
		return
			(((this.y != null ? this.y.name :
			   (this.x != null ? this.x.name : "?")))
			 + "\t" +

			 (this.x != null ? this.x.getQualifiedId().toString() : "?") + "\t" +

			 (this.value > DUNNO ?
			  "=>" :
			  (this.value < DUNNO ? "not=>" : "-")) + "\t" +

			 (this.y == null ? "?" : this.y.id) + "\t" +

			 this.reason + "\t" +

			 (this.witness == null ? "" : this.witness) );
	}

	// How many taxa would we lose if we didn't import this part of the tree?
	int lossage(Node node) {
		int n = 1;
		if (node.children != null)
			for (Node child : node.children)
				if (child.mapped == null || child.mapped.novelp)
					n += lossage(child);
		return n;
	}
}

class QualifiedId {
	String prefix;
	String id;
	QualifiedId(String prefix, String id) {
		this.prefix = prefix; this.id = id;
	}
	QualifiedId(String qid) {
		String[] foo = qid.split(":", 2);
		if (foo.length != 2)
			throw new RuntimeException("ill-formed qualified id: " + qid);
		this.prefix = foo[0]; this.id = foo[1];
	}
	public String toString() {
		return prefix + ":" + id;
	}
	public boolean equals(Object o) {
		if (o instanceof QualifiedId) {
			QualifiedId qid = (QualifiedId)o;
			return (qid.id.equals(id) &&
					qid.prefix.equals(prefix));
		}
		return false;
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
