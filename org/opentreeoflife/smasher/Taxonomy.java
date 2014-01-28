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

public abstract class Taxonomy implements Iterable<Taxon> {
	Map<String, List<Taxon>> nameIndex = new HashMap<String, List<Taxon>>();
	Map<String, Taxon> idIndex = new HashMap<String, Taxon>();
	Set<Taxon> roots = new HashSet<Taxon>(1);
	protected String tag = null;
	int nextSequenceNumber = 0;
	String[] header = null;

	Integer sourcecolumn = null;
	Integer sourceidcolumn = null;
	Integer infocolumn = null;
	Integer flagscolumn = null;
	Integer preottolcolumn = null;
	JSONObject metadata = null;
    int taxid = -1234;    // kludge

	Taxonomy() { }

	public String toString() {
		return "(taxonomy " + this.getTag() + ")";
	}

	public abstract UnionTaxonomy promote();

	public List<Taxon> lookup(String name) {
		return this.nameIndex.get(name);
	}

	public Taxon unique(String name) {
		List<Taxon> probe = this.nameIndex.get(name);
		// TBD: Maybe rule out synonyms?
		if (probe != null && probe.size() == 1)
			return probe.get(0);
		else 
			return this.idIndex.get(name);
	}

	void addToIndex(Taxon node) {
		String name = node.name;
		List<Taxon> nodes = this.nameIndex.get(name);
		if (nodes == null) {
			nodes = new ArrayList<Taxon>(1); //default is 10
			this.nameIndex.put(name, nodes);
		}
		nodes.add(node);
	}

	int cachedCount = -1;

	public int count() {
		if (cachedCount > 0) return cachedCount;
		int total = 0;
		for (Taxon root : this.roots)
			total += root.count();
		cachedCount = total;
		return total;
	}

	// Iterate over all nodes reachable from roots

	public Iterator<Taxon> iterator() {
		final List<Iterator<Taxon>> its = new ArrayList<Iterator<Taxon>>();
		its.add(this.roots.iterator());
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

	Taxon highest(String name) {
		Taxon best = null;
		List<Taxon> l = this.lookup(name);
		if (l != null) {
			int depth = 1 << 30;
			for (Taxon node : l)
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
			List<Taxon> nodes = this.nameIndex.get(name);
			if (nodes.size() > 1) {
				boolean homsp = false;
				boolean sibhomsp = false;
				boolean cuzhomsp = false;
				for (Taxon n1: nodes)
					for (Taxon n2: nodes) {
						if (compareTaxa(n1, n2) < 0 &&
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

    int compareTaxa(Taxon n1, Taxon n2) {
        if (n1.id == null || n2.id == null) return 0;
        int q1 = (n1.children == null ? 0 : n1.children.size());
        int q2 = (n2.children == null ? 0 : n2.children.size());
        if (q1 != q2) return q1 - q2;
        int c = n1.id.length() - n2.id.length();
        if (c != 0) return c;
        return n1.id.compareTo(n2.id);
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

	public static SourceTaxonomy getTaxonomy(String designator, String tag)
        throws IOException {
        SourceTaxonomy tax = getTaxonomy(designator);
        tax.tag = tag;
        return tax;
    }

	public static SourceTaxonomy getNewick(String filename) throws IOException {
		SourceTaxonomy tax = new SourceTaxonomy();
		BufferedReader br = Taxonomy.fileReader(filename);
        tax.roots.add(tax.newickToNode(new java.io.PushbackReader(br)));
		tax.investigateHomonyms();
        tax.assignNewIds(0);    // foo
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
		int normalizations = 0;

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
						this.flagscolumn = headerx.get("flags");
						this.preottolcolumn = headerx.get("preottol_id");
						continue;
					} else
						System.out.println("! No header row");
				}
				String id = parts[0];
				String rawname = parts[2];
				String rank = parts[3];
				if (rank.length() == 0 || rank.equals("no rank"))
					rank = NO_RANK;

				String name = normalizeName(rawname);

				String parentId = parts[1];
				if (parentId.equals("null")) parentId = "";  // Index Fungorum
				if (parentId.equals(id)) {
					System.err.println("!! Taxon is its own parent: " + id);
					parentId = "";
				}

				Taxon node = this.idIndex.get(id);
				if (node == null) {
					// node was created earlier because it's the parent of some other node.
					node = new Taxon(this);
					node.setId(id); // stores into this.idIndex
				}

				if (parentId.length() > 0) {
					Taxon parent = this.idIndex.get(parentId);
					if (parent == null) {
						parent = new Taxon(this);	 //don't know parent's name yet
						parent.setId(parentId);
					}
					parent.addChild(node);
				} else
					roots.add(node);
				node.setName(name);
				node.init(parts); // does setName
				if (!name.equals(rawname)) {
					addSynonym(rawname, node);
					++normalizations;
				}
				if (this.flagscolumn != null) {
					if (parts[this.flagscolumn].contains("extinct"))
						// kludge. could be _direct or _inherited
						node.properFlags |= Taxonomy.EXTINCT;
				}
			}
			++row;
			if (row % 500000 == 0)
				System.out.println(row);
		}
		br.close();
		if (normalizations > 0)
			System.out.format("| %s names normalized\n", normalizations);

		for (Taxon node : this.idIndex.values())
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
			for (Taxon root : roots)
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

	// TO BE DONE:
	//   Umlaut letters of German origin (not diaresis) need to have an added 'e'
	//     ... but there's no way to determine this automatically.
	//	   Xestoleberis yüchiae is not of Germanic origin.
	//   Convert upper case letters to lower case
 	//      e.g. genus Pechuel-Loeschea  -- but these are all barren.
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

    // Fold sibling homonyms together into single taxa.
    // Optional step.

    public void smush() {
		List<Runnable> todo = new ArrayList<Runnable>();

		// Smush taxa that differ only in id.
		int siblingHomonymCount = 0;
		for (final Taxon node : this.idIndex.values())

			if (node.name != null && node.parent != null)

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
									node.prune();  // removes name from index
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
			// if (node.parent == null && !roots.contains(node)) ...
			if (node.parent != null) {
				Taxon replacement = this.idIndex.get(node.parent.id);
				if (replacement != node.parent) {
					System.err.println("Post-smushing kludge: " + node.parent.id + " => " + replacement.id);
					node.parent = replacement;
				}
			}
    }

	void dumpNodes(Collection<Taxon> nodes, String outprefix) throws IOException {
		PrintStream out = Taxonomy.openw(outprefix + "taxonomy.tsv");

		out.println("uid\t|\tparent_uid\t|\tname\t|\trank\t|\tsourceinfo\t|\tuniqname\t|\tflags\t|\t"
					// 0	 1				2		 3		  4				 5             6
					);

		for (Taxon node : nodes) {
			if (node == null)
				System.err.println("null in nodes list!?" );
			else if (!node.prunedp)
				dumpNode(node, out, true);
		}
		out.close();
	}

	// Recursive!
	void dumpNode(Taxon node, PrintStream out, boolean rootp) {
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
			for (Taxon child : node.children) {
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
					Taxon node = this.idIndex.get(id);
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

	public boolean addSynonym(String syn, Taxon node) {
		if (node.taxonomy != this)
			System.err.println("!? Synonym for a node that's not in this taxonomy: " + syn + " " + node);
		List<Taxon> nodes = this.nameIndex.get(syn);
		if (nodes != null) {
			if (nodes.contains(node))
				return false;    //lots of these System.err.println("Redundant synonymy: " + id + " " + syn);
		} else {
			nodes = new ArrayList<Taxon>(1);
			this.nameIndex.put(syn, nodes);
		}
		nodes.add(node);
		return true;
	}

	void dumpSynonyms(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("name\t|\tuid\t|\ttype\t|\tuniqname\t|\t");
		for (String name : this.nameIndex.keySet())
			for (Taxon node : this.nameIndex.get(name))
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
		for (Taxon root : this.roots)
			analyzeOTUs(root, 0);	// mutates the tree
    }

    // GBIF and IF only
    public void analyzeMajorRankConflicts() {
		for (Taxon root : this.roots)
			analyzeRankConflicts(root, true);
    }

	// Final analysis, after assembling entire taxonomy, before writing it out
	void analyze() {
		for (Taxon root : this.roots)
			analyzeRankConflicts(root, false);
		for (Taxon root : this.roots)
			analyzeContainers(root, 0);
		for (Taxon root : this.roots)
            analyzeBarren(root);
	}

	// Set during assembly
	static final int TATTERED 			 =   4 * 1024;    // combine using |
	static final int FORCED_VISIBLE		 =  16 * 1024;	  // combine using |
	static final int EDITED     	     = 128; 		  // combine using |
	static final int EXTINCT     	     =  64 * 1024;	  // combine using |
	static final int HIDDEN		      	 =  32 * 1024;    // combine using &

	// Parent-dependent.  Retain value
	static final int MAJOR_RANK_CONFLICT =   2 * 1024;

	// NCBI - individually troublesome - not sticky - combine using &
	static final int NOT_OTU             =    1;
	static final int HYBRID          	 =    2;
	static final int VIRAL          	 =    4;

	// Final analysis...
	// Containers - unconditionally so.
	static final int UNCLASSIFIED 	  	 =    8;
	static final int ENVIRONMENTAL 	  	 =   16;
	static final int INCERTAE_SEDIS 	 =   32;

	// Australopithecus
	static final int SIBLING_LOWER       =  512;
	static final int SIBLING_HIGHER      =   1 * 1024;

	// Is 'species' or lower rank ('infraspecific' when inherited)
	// Unconditional ?
	static final int SPECIFIC     	     =   64;

	// Opposite of 'barren' - propagated upward
	static final int ANYSPECIES			 =   8 * 1024;

	// Returns the node's rank (as an int).  In general the return
	// value should be >= parentRank, but conceivably funny things
	// could happen when combinings taxonomies.

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
					for (Taxon child : node.children) {
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
			if (majorp && highrank >= 0 && myrank > highrank)
				// The myrank == highrank case is weird too; there are about 200 of those.
				System.err.println("** Ranks out of order: " +
								   node + " " + node.rank + " has child " +
								   highchild + " " + highchild.rank);
		}
		return myrank;
	}

	// Each Taxon has two parallel sets of flags: 
	//   proper - applies particularly to this node
	//   inherited - applies to this node because it applies to an ancestor
	//     (where in some cases the ancestor may later be 'elided' so
	//     not an ancestor any more)

	// analyzeOTUs: set taxon flags based on name, leading to dubious
	// taxa being hidden.
    // We use this for NCBI but not for SILVA.

	static void analyzeOTUs(Taxon node, int inheritedFlags) {
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
			for (Taxon child : node.children)
				analyzeOTUs(child, bequest);
	}

    // Flags to set for all taxonomies.  Also elide container pseudo-taxa

	static void analyzeContainers(Taxon node, int inheritedFlags) {
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
			for (Taxon child : new ArrayList<Taxon>(node.children))
				analyzeContainers(child, bequest);

		// After
		if (elidep) {
            // Splice the node out of the hierarchy, but leave it as a
            // residual terminal non-OTU node.
            if (node.children != null && node.parent != null)
                for (Taxon child : new ArrayList<Taxon>(node.children))
                    child.changeParent(node.parent);
            node.properFlags |= NOT_OTU;
        }
	}

    // Set the ANYSPECIES flag of any taxon that is a species or has
    // one below it

	static void analyzeBarren(Taxon node) {
		boolean anyspeciesp = false;     // Any descendant is a species?
		if (node.rank != null) {
			Integer rank = ranks.get(node.rank);
			if (rank != null && rank >= SPECIES_RANK) {
				node.properFlags |= SPECIFIC;
				anyspeciesp = true;
			}
		}
        if (node.children != null)
			for (Taxon child : node.children) {
				analyzeBarren(child);
				if ((child.properFlags & ANYSPECIES) != 0) anyspeciesp = true;
			}
		else if (node.rank == Taxonomy.NO_RANK)
			anyspeciesp = true;
		if (anyspeciesp) node.properFlags |= ANYSPECIES;
	}

	static void printFlags(Taxon node, PrintStream out) {
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
		if ((node.properFlags & HIDDEN) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("hidden");
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
		if ((node.inheritedFlags & TATTERED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("tattered_inherited");
		}

		if ((node.properFlags & EDITED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("edited");
		}

		if ((node.properFlags & EXTINCT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("extinct_direct");
		}
		if ((node.inheritedFlags & EXTINCT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("extinct_inherited");
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
	void select1(Taxon node, String outprefix) throws IOException {
		System.out.println("| Selecting " + node.name);
		List<Taxon> it = new ArrayList<Taxon>(1);
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
        return select(this.unique(designator));
    }
    
	public Taxonomy select(Taxon sel) {
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			Taxon selection = sel.select(tax2);
			System.out.println("| Selection has " + selection.count() + " taxa");
			tax2.roots.add(selection);

			// Synonyms
			int in = 0, out = 0;
			for (String name : this.nameIndex.keySet())
				for (Taxon node : this.nameIndex.get(name)) {
					if (node.mapped != null) {
						tax2.addSynonym(name, node.mapped);
						++in;
					} else
						++out;
				}
			System.out.println("| Nyms in: " + in + " out: " + out);

			return tax2;
		} else {
			System.err.println("Missing or ambiguous name: " + sel);
			return null;
		}
	}

	public Taxonomy sample(String designator, int count) {
		Taxon sel = this.unique(designator);
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			Taxon sample = sel.sample(count, tax2);
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
        List<Taxon> cuttings = new ArrayList<Taxon>();
        Taxon root = null;
        for (Taxon r : this.roots) root = r;    //bad kludge. uniroot assumed
        Taxon newroot = chop(root, m, n, cuttings, tax);
        tax.roots.add(newroot);
        System.err.format("Cuttings: %s Residue: %s\n", cuttings.size(), newroot.size());

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
        Taxon newnode = node.dup(tax);
        if (m < c && c <= n) {
            newnode.setName(newnode.name + " (" + node.size() + ")");
            chopped.add(node);
        } else if (node.children != null)
            for (Taxon child : node.children) {
                Taxon newchild = chop(child, m, n, chopped, tax);
                newnode.addChild(newchild);
            }
        return newnode;
    }

	public void deforestate() {
		List<Taxon> rootsList = new ArrayList<Taxon>(this.roots);
		if (rootsList.size() <= 1) return;
		Collections.sort(rootsList, new Comparator<Taxon>() {
				public int compare(Taxon x, Taxon y) {
					return y.count() - x.count();
				}
			});
		Taxon biggest = rootsList.get(0);
		int count1 = biggest.count();
		int count2 = rootsList.get(1).count();
		if (rootsList.size() >= 2 && count1 < count2*1000)
			System.err.format("*** Nontrivial forest: biggest is %s, 2nd biggest is %s\n", count1, count2);
		else
			System.out.format("| Deforesting: keeping biggest (%s), 2nd biggest is %s\n", count1, count2);
		for (Taxon root : rootsList)
			if (!root.equals(biggest))
				root.prune();
		this.roots = new HashSet<Taxon>(1);
		this.roots.add(biggest);
		System.out.format("| Removed %s smaller trees\n", rootsList.size()-1);
	}

	// -------------------- Newick stuff --------------------
	// Render this taxonomy as a Newick string.
	// This feature is very primitive and only for debugging purposes!

	public String toNewick() {
		StringBuffer buf = new StringBuffer();
		for (Taxon root: this.roots) {
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
					node = new Taxon(this);
					// kludge
					node.setName("");
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
						Taxon node = new Taxon(this);
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

	long maxid() {
		long id = -1;
		for (Taxon node : this) {
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
		for (Taxon node : this)
			if (node.id == null) {
				node.setId(Long.toString(++maxid));
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

		List<Taxon> parents = filterByContext(parentName, contextName);
		if (parents == null) {
			System.err.println("(add) Parent name " + parentName
							   + " missing in context " + contextName);
			return;
		}
		if (parents.size() > 1)
			System.err.println("? Ambiguous parent name: " + parentName);
        Taxon parent = parents.get(0);

		if (!parent.name.equals(parentName))
			System.err.println("(add) Warning: parent taxon name is a synonym: " + parentName);

		List<Taxon> existings = filterByContext(name, contextName);
        Taxon existing = null;
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
				Taxon node = new Taxon(this);
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
					for (Taxon child: existing.children)
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

	List<Taxon> filterByContext(String taxonName, String contextName) {
		List<Taxon> nodes = this.lookup(taxonName);
		if (nodes == null) return null;
		List<Taxon> fnodes = new ArrayList<Taxon>(1);
		for (Taxon node : nodes) {
			if (!node.name.equals(taxonName)) continue;
			// Follow ancestor chain to see whether this node is in the context
			for (Taxon chain = node; chain != null; chain = chain.parent)
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

    // Merge a source taxonomy into this taxonomy
    // Deprecated
    public void absorb(SourceTaxonomy tax, String tag) {
        tax.tag = tag;
        this.absorb(tax);
    }

    // Not deprecated (prefix now passed to getTaxonomy)
    public void absorb(SourceTaxonomy tax) {
        ((UnionTaxonomy)this).mergeIn(tax);
    }

    // Overridden in class UnionTaxonomy
	public void assignIds(SourceTaxonomy idsource) {
        ((UnionTaxonomy)this).assignIds(idsource);
    }

    // Look up a taxon by name or unique id.  Name must be unique in the taxonomy.
    public Taxon taxon(String name) {
		List<Taxon> probe = this.nameIndex.get(name);
		// TBD: Maybe rule out synonyms?
		if (probe != null) {
			if (probe.size() == 1)
				return probe.get(0);
			else {
				System.err.format("** Ambiguous taxon name: %s\n", name);
				return null;
			}
		}
		Taxon byId = this.idIndex.get(name);
        if (byId == null)
            System.err.format("** No taxon found with this name: %s\n", name);
        return byId;
    }

    public Taxon taxon(String name, String context) {
        List<Taxon> nodes = filterByContext(name, context);
        if (nodes == null) {
            System.err.format("** No taxon with name %s in context %s\n", name, context);
            return null;
        } else if (nodes.size() > 1) {
            System.err.format("** Ambiguous taxon name %s in context %s\n", name, context);
            return null;
        }
        return nodes.get(0);
    }

    public Taxon newTaxon(String name, String rank, String sourceIds) {
		if (this.lookup(name) != null)
			System.err.format("** Warning: A taxon by the name of %s already exists\n", name);
        Taxon t = new Taxon(this);
        t.setName(name);
        t.rank = rank;
        t.setSourceIds(sourceIds);
        return t;
    }

    public void same(Taxon node1, Taxon node2) {
		sameness(node1, node2, true);
	}

	public void notSame(Taxon node1, Taxon node2) {
		sameness(node1, node2, false);
	}

	public void sameness(Taxon node1, Taxon node2, boolean polarity) {
        Taxon unode, snode;
        if (node1 == null || node2 == null) return; // Error already reported?
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
        if (snode.mapped != null) {
            System.err.format("** The source is already mapped: %s\n", snode);
            return;
        }
		if (polarity)
			snode.unifyWith(unode);
		else {
			Taxon evader = new Taxon(unode.taxonomy);
			snode.unifyWithNew(evader);
			evader.addSource(snode);
		}
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

			Taxon.resetStats();
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
			for (Taxon node : this)
				if (!seen.contains(node.name)) {
					List<Taxon> unodes = union.nameIndex.get(node.name);
					if (unodes != null)
						for (Taxon unode : unodes)
							if (unode.name.equals(node.name))
								{ seen.add(node.name); todo.add(node.name); break; }
				}
			// true / synonym
			for (Taxon node : union)
				if (this.nameIndex.get(node.name) != null &&
					!seen.contains(node.name))
					{ seen.add(node.name); todo.add(node.name); }
			// synonym / true
			for (Taxon node : this)
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
				List<Taxon> unodes = union.nameIndex.get(name);
				if (unodes != null) {
					++incommon;
					List<Taxon> nodes = this.nameIndex.get(name);
					if (false &&
						(((nodes.size() > 1 || unodes.size() > 1) && (++homcount % 1000 == 0)) || painful))
						System.out.format("| Mapping: %s %s*%s (name #%s)\n", name, nodes.size(), unodes.size(), incommon);
					new Matrix(name, nodes, unodes).run(criteria);
				}
			}
			System.out.println("| Names in common: " + incommon);

			Taxon.printStats();

			// Report on how well the merge went.
			this.mappingReport(union);
		}
	}

	// What was the fate of each of the nodes in this source taxonomy?

	void mappingReport(UnionTaxonomy union) {

		if (Taxon.windyp) {

			int total = 0;
			int nonamematch = 0;
			int prevented = 0;
			int added = 0;
			int corroborated = 0;

			// Could do a breakdown of matches and nonmatches by reason

			for (Taxon node : this) {
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
			Taxon n1 = null, n2 = null;
			// For each pinnable name, look for it in both taxonomies
			// under all possible synonyms
			for (int j = 0; j < names.length; ++j) {
				String name = names[j];
				Taxon m1 = this.highest(name);
				if (m1 != null) n1 = m1;
				Taxon m2 = union.highest(name);
				if (m2 != null) n2 = m2;
			}
			if (n1 != null && n2 != null) {
				n1.setDivision(names[0]);
				n2.setDivision(names[0]);
				n1.unifyWith(n2); // hmm.  TBD: move this out of here
				++count;
			}
		}
		if (count > 0)
			System.out.println("Pinned " + count + " out of " + pins.length);
	}

	void augment(UnionTaxonomy union) {
		if (this.roots.size() > 0) {

			// Add heretofore unmapped nodes to union
			if (Taxon.windyp)
				System.out.println("--- Augmenting union with new nodes from " + this.getTag() + " ---");
			int startcount = union.count();
			int startroots = union.roots.size();

			for (Taxon root : this.roots) {

				// 'augment' always returns a node in the union tree, or null
				Taxon newroot = root.augment(union);

				if (newroot != null && newroot.parent == null && !union.roots.contains(newroot))
					union.roots.add(newroot);
			}

			int tidied = 0;

			// Tidy up the root set:
			List<Taxon> losers = new ArrayList<Taxon>();
			for (Taxon root : union.roots)
				if (root.parent != null) {
					losers.add(root);
					if (++tidied < 10)
						System.out.println("| No longer a root: " + root);
					else if (tidied == 10)
						System.out.println("| ...");
				}
			for (Taxon loser : losers)
				union.roots.remove(loser);

			// Sanity check:
			for (Taxon unode : union)
				if (unode.parent == null && !union.roots.contains(unode))
					System.err.println("| Missing root: " + unode);

			if (Taxon.windyp) {
				System.out.println("| Started with:		 " +
								   startroots + " trees, " + startcount + " taxa");
				Taxon.augmentationReport();
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
			for (Taxon node : this.nameIndex.get(syn))

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
        for (Taxon root: this.roots) {
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
		Taxon.windyp = true; //kludge
	}

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void assignIds(SourceTaxonomy idsource) {
		this.idsource = idsource;
		// idsource.tag = "ids";
		idsource.mapInto(this, Criterion.idCriteria);

		Taxon.resetStats();
		System.out.println("--- Assigning ids to union starting with " + idsource.getTag() + " ---");

		// Phase 1: recycle previously assigned ids.
		for (Taxon node : idsource) {
			Taxon unode = node.mapped;
			if (unode != null) {
				if (unode.comapped != node)
					System.err.println("Map/comap don't commute: " + node + " " + unode);
				Answer answer = assessSource(node, unode);
				if (answer.value >= Answer.DUNNO)
					Taxon.markEvent("keeping-id");
				else
					this.logAndMark(answer);
				unode.setId(node.id);
			}
		}

		// Phase 2: give new ids to union nodes that didn't get them above.
		long sourcemax = idsource.maxid();
		this.assignNewIds(sourcemax);
		// remember, this = union, idsource = previous version of ott

		Taxon.printStats();		// Taxon id clash
	}

	// Cf. assignIds()
	// x is a source node drawn from the idsource taxonomy file.
	// y is the union node it might or might not map to.

	static Answer assessSource(Taxon x, Taxon y) {
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
		Taxon.resetStats();		// Taxon id clash
		PrintStream out = Taxonomy.openw(filename);
		Set<String> seen = new HashSet<String>();
		for (Taxon idnode : idsource) 
			if (idnode.mapped != null) {
				String idstringfield = idnode.auxids;
				if (idstringfield.length() == 0) continue;
				for (String idstring : idstringfield.split(",")) {
					Taxon auxnode = aux.idIndex.get(idstring);
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
					Taxon.markEvent("reason");
					seen.add(idstring);
				}
			}
		
		for (Taxon auxnode : aux) {
			if (auxnode.mapped != null && !seen.contains(auxnode.id))
				out.print("" + auxnode.id
						  + "\t" +
						  // Can be invoked in either of two ways... see Makefile
						  (auxnode.mapped.id != null?
						   auxnode.mapped.id :
						   auxnode.mapped.getSourceIdsString())
						  + "\t" +
						  "new" + "\n");
			Taxon.markEvent("new-aux-mapping");
		}
		Taxon.printStats();
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
			Taxon node = idsource.idIndex.get(id);
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
            for (Taxon node : idsource.nameIndex.get(name))
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
		Taxon.markEvent(answer.reason);
	}
	void logAndReport(Answer answer) {
		this.log(answer);
		answer.x.report(answer.reason, answer.y, answer.witness);
	}

}

// For each source node, consider all possible union nodes it might map to
// TBD: Exclude nodes that have 'prunedp' flag set

class Matrix {

	String name;
	List<Taxon> nodes;
	List<Taxon> unodes;
	int m;
	int n;
	Answer[][] suppressp;

	Matrix(String name, List<Taxon> nodes, List<Taxon> unodes) {
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
			for (Taxon node : nodes)
				if (!node.name.equals(name)) {
					Taxon unode = unodes.get(0);
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
			Taxon x = nodes.get(i);
			for (int j = 0; j < n; ++j) {  // Find a union node to map it to...
				if (suppressp[i][j] != null) continue;
				Taxon y = unodes.get(j);
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
					Taxon x = nodes.get(i); // == uuniq[j]
					Taxon y = unodes.get(j);

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
			Taxon node = nodes.get(i);
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
							Taxon rival = nodes.get(ii);    // in source taxonomy or idsource
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
							Taxon candidate = unodes.get(j);    // in union taxonomy
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

	abstract Answer assess(Taxon x, Taxon y);

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
			Answer assess(Taxon x, Taxon y) {
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

	// Failure case: matching where there should be no match: Buchnera, Burkea
	// Taxon y is protozoan, taxon x is a plant

	static Criterion division =
		new Criterion() {
			public String toString() { return "same-division"; }
			Answer assess(Taxon x, Taxon y) {
				String xdiv = x.getDivision();
				String ydiv = y.getDivision();
				if (xdiv == ydiv)
					return Answer.NOINFO;
				else if (xdiv != null) {
					if (ydiv == null) {
						//System.err.format("No half-sided division exclusion: %s %s\n", x, y);
						return Answer.NOINFO;
					}
					return Answer.heckNo(x, y, "different-division", xdiv);
				} else
					return Answer.NOINFO;
			}
		};

	static Criterion eschewTattered =
		new Criterion() {
			public String toString() { return "eschew-tattered"; }
			Answer assess(Taxon x, Taxon y) {
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
			Answer assess(Taxon x, Taxon y) {
				Taxon y0 = y.scan(x.taxonomy);	  // ignore names not known in both taxonomies
				Taxon x0 = x.scan(y.taxonomy);
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

	static boolean online(String name, Taxon node) {
		for ( ; node != null; node = node.parent)
			if (node.name.equals(name)) return true;
		return false;
	}

	static Criterion subsumption =
		new Criterion() {
			public String toString() { return "overlaps"; }
			Answer assess(Taxon x, Taxon y) {
				Taxon a = x.antiwitness(y);
				Taxon b = x.witness(y);
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
			Answer assess(Taxon x, Taxon y) {
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
			Answer assess(Taxon x, Taxon y) {
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
			Answer assess(Taxon x, Taxon y) {
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
			Answer assess(Taxon x, Taxon y) {
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
			Answer assess(Taxon x, Taxon y) {
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
	Taxon x, y;					// The question is: Should x be mapped to y?
	int value;					// YES, NO, etc.
	String reason;
	String witness;
	//gate c14
	Answer(Taxon x, Taxon y, int value, String reason, String witness) {
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

	static Answer heckYes(Taxon x, Taxon y, String reason, String witness) { // Uninteresting
		return new Answer(x, y, HECK_YES, reason, witness);
	}

	static Answer yes(Taxon x, Taxon y, String reason, String witness) {
		return new Answer(x, y, YES, reason, witness);
	}

	static Answer weakYes(Taxon x, Taxon y, String reason, String witness) {
		return new Answer(x, y, WEAK_YES, reason, witness);
	}

	static Answer noinfo(Taxon x, Taxon y, String reason, String witness) {
		return new Answer(x, y, DUNNO, reason, witness);
	}

	static Answer weakNo(Taxon x, Taxon y, String reason, String witness) {
		return new Answer(x, y, WEAK_NO, reason, witness);
	}

	static Answer no(Taxon x, Taxon y, String reason, String witness) {
		return new Answer(x, y, NO, reason, witness);
	}

	static Answer heckNo(Taxon x, Taxon y, String reason, String witness) {
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
	int lossage(Taxon node) {
		int n = 1;
		if (node.children != null)
			for (Taxon child : node.children)
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
