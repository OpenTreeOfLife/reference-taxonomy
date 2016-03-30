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
import java.text.Normalizer;
import java.text.Normalizer.Form;
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

public abstract class Taxonomy implements Iterable<Taxon> {
    private Map<String, List<Taxon>> nameIndex = new HashMap<String, List<Taxon>>();
	public Map<String, Taxon> idIndex = new HashMap<String, Taxon>();
    public Taxon forest = new Taxon(this, "");
	public String idspace = null; // "ncbi", "ott", etc.
	String[] header = null;

	Integer sourcecolumn = null;
	Integer sourceidcolumn = null;
	Integer infocolumn = null;
	Integer flagscolumn = null;
	public JSONObject metadata = null;
    public String ingroupId = null;    // for trees, not taxonomies

	private String tag = null;     // unique marker
	private int taxid = -1234;	   // kludge

    public EventLogger eventlogger = null;

	public Taxonomy() {
    }

	public Taxonomy(String idspace) {
        this.idspace = idspace;
    }

	public String toString() {
		return "(taxonomy " + this.getTag() + ")";
	}

    public void setEventLogger(EventLogger eventlogger) {
        this.eventlogger = eventlogger;
    }

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
        if (node.name == null || !node.name.equals("life"))
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

    public Iterable<Taxon> taxa() {
        return this.forest.descendants(false);
    }

	public Iterator<Taxon> iterator() {
        return this.taxa().iterator();
    }

	public Taxon lookupId(String id) {
        return this.idIndex.get(id);
	}

    // Names

	public List<Taxon> lookup(String name) {
		return this.nameIndex.get(name);
	}

    public int numberOfNames() {
        return this.nameIndex.size();
    }

    public Collection<String> allNames() {
        return this.nameIndex.keySet();
    }

	public Taxon unique(String name) {
		List<Taxon> probe = this.lookup(name);
		// TBD: Maybe rule out synonyms?
		if (probe != null && probe.size() == 1)
			return probe.get(0);
		else 
			return this.lookupId(name);
	}

    // compare addSynonym
	void addToNameIndex(Taxon node, String name) {
		List<Taxon> nodes = this.lookup(name);
		if (nodes == null) {
			nodes = new ArrayList<Taxon>(1); //default is 10
            nodes.add(node);
			this.nameIndex.put(name, nodes);
		} else if (!nodes.contains(node)) {
            nodes.add(node);
            if (nodes.size() == 75) {
                System.err.format("** %s is the 75th to have the name '%s'\n", node, name);
                // Taxon.backtrace();  - always happens in loadTaxonomyProper
            }
        }
    }

    // delete a synonym
    public void removeFromNameIndex(Taxon node, String name) {
		List<Taxon> nodes = node.taxonomy.lookup(name);
        if (nodes != null) {
            nodes.remove(node);
            if (nodes.size() == 0)
                node.taxonomy.nameIndex.remove(node.name);
        }
	}

	static int globalTaxonomyIdCounter = 1;

	public String getTag() {
		if (this.tag == null) {
			if (this.taxid < 0)
				this.taxid = globalTaxonomyIdCounter++;
            this.tag = this.getIdspace() + taxid;
        }
        return this.tag;
	}

    public void setTag(String tag) {
        this.tag = tag;
    }

    // The 'idspace' string will show up as a prefix in QualifiedNames

	public String getIdspace() {
		if (this.idspace == null)
            this.setIdspace();
        return this.idspace;
	}

	void setIdspace() {
		if (this.idspace != null) return;
        idspace = this.idspace;
		if (idspace == null) {
            List<Taxon> probe = this.lookup("Caenorhabditis elegans");
            if (probe != null) {
                String id = probe.get(0).id;
                if (id.equals("6239")) idspace = "ncbi";
                else if (id.equals("2283683")) idspace = "gbif";
                else if (id.equals("395048")) idspace = "ott";
                else if (id.equals("100968828")) idspace = "aux"; // preottol
            }
        }
		// TEMPORARY KLUDGE
		if (idspace == null) {
			List<Taxon> probe2 = this.lookup("Asterales");
			if (probe2 != null) {
				String id = probe2.get(0).id;
				if (id.equals("4209")) idspace = "ncbi";
				if (id.equals("414")) idspace = "gbif";
				if (id.equals("1042120")) idspace = "ott";
			}
		}
        if (idspace == null)
            idspace = "tax";
        this.idspace = idspace;
	}

	// Most rootward node in this taxonomy having a given name
    // Maybe this should be moved to smasher
	public Taxon highest(String name) { // See pin()
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
            return null;
		}
		return best;
	}

	void investigateHomonyms() {
		int homs = 0;
		int sibhoms = 0;
		int cousinhoms = 0;
		for (String name : allNames()) {
			List<Taxon> nodes = this.lookup(name);
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

	public static int compareTaxa(Taxon n1, Taxon n2) {
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
		if (designator.startsWith("(")) {
            Taxon root = Newick.newickToNode(designator, tax);
			tax.addRoot(root);
        } else if (designator.endsWith(".tre")) {
			System.out.println("--- Reading " + designator + " ---");
            return getNewick(designator); // calls postProcessTaxonomy
        } else {
			if (!designator.endsWith("/")) {
				System.err.println("Taxonomy designator should end in / but doesn't: " + designator);
				designator = designator + "/";
			}
			System.out.println("--- Reading " + designator + " ---");
			tax.loadTaxonomy(designator);
            tax.purgeTemporaryIds();
		}
        tax.postProcessTaxonomy();
		return tax;
	}

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

    public void postProcessTaxonomy() {
        this.placeBiggest(); // 'life' or biggest is placed

        // Topology changes:

        // Elide incertae-sedis-like containers and set some flags
		this.analyzeContainers();

        // Foo
		this.elideRedundantIntermediateTaxa();

		if (this.rootCount() == 0)
			System.err.println("** No root nodes!");

        // Flag changes:

        // Fix up the flags - putatively inherited flags that aren't, need to be promoted to original
        int flaggers = 0;
        for (Taxon node: this)
            if (!node.isRoot()) {
                int wrongFlags = node.inferredFlags & ~(node.parent.properFlags | node.parent.inferredFlags);
                if (wrongFlags != 0) {
                    node.addFlag(wrongFlags);
                    flaggers++;
                }
            }
        if (flaggers > 0)
            System.out.format("| Flags fixed for %s nodes\n", flaggers);

        // do we really want to do this?  it's clutter...
        this.inferFlags();

        // Some statistics & reports
		this.investigateHomonyms();
        int nroots = this.rootCount();
        int ntips = this.tipCount();
        int nnodes = this.count();
        System.out.format("| %s roots + %s internal + %s tips = %s total\n",
                          nroots, nnodes - (ntips + nroots), ntips, nnodes);

        // Optional step after this: smush()
    }

	public static SourceTaxonomy getTaxonomy(String designator, String idspace)
		throws IOException {
		SourceTaxonomy tax = getTaxonomy(designator);
		tax.idspace = idspace;
		return tax;
	}

	public static SourceTaxonomy getNewick(String filename) throws IOException {
		BufferedReader br = Taxonomy.fileReader(filename);
		SourceTaxonomy tax = new SourceTaxonomy();
        Taxon root = Newick.newickToNode(new java.io.PushbackReader(br), tax);
		tax.addRoot(root);
        root.properFlags = 0;   // not unplaced
        tax.postProcessTaxonomy();
		return tax;
	}

	public static SourceTaxonomy getNewick(String filename, String idspace) throws IOException {
		SourceTaxonomy tax = getNewick(filename);
		tax.idspace = idspace;
		return tax;
	}

	// load | dump all taxonomy files

	public void loadTaxonomy(String dirname) throws IOException {
		this.loadMetadata(dirname + "about.json");
		this.loadTaxonomyProper(dirname + "taxonomy.tsv");
		this.loadSynonyms(dirname + "synonyms.tsv");
        this.loadForwards(dirname + "forwards.tsv");
        this.purgeTemporaryIds();
	}

	// This gets overridden in a subclass.
    // Deforestate would have already been called, if it was going to be
	public void dump(String outprefix, String sep) throws IOException {
		new File(outprefix).mkdirs();
        this.prepareForDump(outprefix, sep);
		this.dumpMetadata(outprefix + "about.json");

		this.dumpNodes(this.roots(), outprefix, sep);
		this.dumpSynonyms(outprefix + "synonyms.tsv", sep);
        this.dumpForwards(outprefix + "new-forwards.tsv");
		// this.dumpHidden(outprefix + "hidden.tsv");
	}

    public void prepareForDump(String outprefix, String sep) throws IOException {
        this.placeBiggest();         // End of topology modifications
		this.assignDummyIds();
        this.reset();                // maybe unnecessary; depths and comapped
		this.analyzeRankConflicts(); // set SIBLING_HIGHER
        this.inferFlags();           // infer BARREN & INFRASPECIFIC, and herit
        Taxon biggest = this.normalizeRoots().get(0);
        System.out.format("| Root is %s %s\n", biggest.name, Flag.toString(biggest.properFlags, 0));
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
				if (prefix != null)
					this.idspace = (String)prefix;
			}

		} catch (ParseException e) {
			System.err.println(e);
		}
		fr.close();
	}

	public static String NO_RANK = null;

	public abstract void dumpMetadata(String filename) throws IOException;

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
                    if (name != null && !name.equals(rawname)) {
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
        if (id.length() == 0)
            System.err.format("!! Null id: %s\n", name);
        else
            node.setId(id);
        if (name != null)
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
        if (str.length() == 0) return null;
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
		Set<Taxon> knuckles = new HashSet<Taxon>();
		for (Taxon node : this) {
			if (!node.isRoot()
				&& node.parent.children.size() == 1
				&& node.children != null)
				if (!node.isPlaced()
                    || (node.parent.name != null
                        && node.parent.name.equals(node.name)))
					knuckles.add(node);
		}
        int i = 0;
		for (Taxon node: knuckles) {
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
		List<List<Taxon>> smushlist = new ArrayList<List<Taxon>>();

        for (String name : this.allNames()) {
            List<Taxon> t1 = this.lookup(name);

            // First, collate by parent
            Map<Taxon, List<Taxon>> childrenWithThisName = new HashMap<Taxon, List<Taxon>>();
            for (Taxon node : t1) {
                List<Taxon> c = childrenWithThisName.get(node.parent);
                if (c == null) {
                    c = new ArrayList<Taxon>(1);
                    childrenWithThisName.put(node.parent, c);
                }
                c.add(node);
            }
                
            // Add to smush list if more than one child
            for (List<Taxon> c : childrenWithThisName.values())
                if (c.size() > 1)
                    smushlist.add(c);
        }

        if (smushlist.size() > 0)
            System.out.format("| Smushing %s node sets\n", smushlist.size());

        for (List<Taxon> nodes : smushlist) {

            // Get smallest... that will become the 'right' one
            Taxon smallest = nodes.get(0);
            for (Taxon other : nodes)
                if (compareTaxa(other, smallest) < 0)
                    smallest = other;

            if (smallest.name != null && smallest.name.equals("Oncideres cingulatus"))
                System.out.format("| Smushing Oncideres cingulatus %s\n", nodes.size());

            for (Taxon other : nodes)
                if (other != smallest) {
                    smallest.absorb(other);
                    this.idIndex.put(other.id, smallest);
                }
        }
	}

    public void cleanRanks() {
        for (Taxon node : this)
            if (node.rank == null && node.name != null) {
                if (Taxon.isBinomial(node.name))
                    if (node.parent != null && node.parent.rank != null && node.parent.rank.equals("genus")) {
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
                            node.rank = "species";
                    }
                else if (node.name.contains(" subsp.") || node.name.contains(" subsp ")) {
                    System.out.format("| Setting rank of %s to subspecies\n", node);
                    node.rank = "subspecies";
                }
                else if (node.name.contains(" var.")) {
                    System.out.format("| Setting rank of %s to variety\n", node);
                    node.rank = "variety";
                }
            }
    }

	public void dumpNodes(Iterable<Taxon> nodes, String outprefix, String sep) throws IOException {
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

	// Recursive!
	void dumpNode(Taxon node, PrintStream out, boolean rootp, String sep) {
		// 0. uid:
		out.print((node.id == null ? "?" : node.id) + sep);
		// 1. parent_uid:
		out.print(((node.taxonomy.hasRoot(node) || rootp) ? "" : node.parent.id)  + sep);
		// 2. name:
		out.print((node.name == null ? "" : node.name)
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
                        node.inferredFlags,
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

    // load forwarding pointers

	void loadForwards(String filename) throws IOException {
		BufferedReader fr;
		try {
			fr = fileReader(filename);
		} catch (java.io.FileNotFoundException e) {
			fr = null;
		}
		if (fr != null) {
            int count = 0;
            fr.readLine();      // header row
            String str;
			while ((str = fr.readLine()) != null) {
                String[] parts = tabOnly.split(str);
                String alias = parts[0].trim();
                String truth = parts[1].trim();
                Taxon dest = lookupId(truth);
                if (dest != null) {
                    Taxon probe = lookupId(alias);
                    if (probe == null) {
                        this.idIndex.put(alias, dest);
                        ++count;
                    }
                }
            }
            System.out.format("| %s id aliases\n", count);
            fr.close();
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
                    if (node.name == null) {
                        node.setName(syn);
						++count;
					} else if (!node.name.equals(syn)) {
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
    // compare addToNameIndex

	public boolean addSynonym(String syn, Taxon node) {
        if (node.name != null && node.name.equals(syn))
            return true;
		if (node.taxonomy != this)
			System.err.println("!? Synonym for a node that's not in this taxonomy: " + syn + " " + node);
		List<Taxon> nodes = this.lookup(syn);
		if (nodes == null) {
			nodes = new ArrayList<Taxon>(1);
			this.nameIndex.put(syn, nodes);
            nodes.add(node);
            return true;
		} else {
            // We don't want to create a homonym.
            return nodes.contains(node);
            /*
			for (Taxon n : nodes)
                if (n == node)
                    return true; // already present (shouldn't happen)
            if (false)
                // There are gazillions of these warnings.
                System.err.format("Making %s a synonym of %s would create a homonym\n", syn, node);
            return false;       // shouldn't happen
            */
		}
	}

	public void dumpSynonyms(String filename, String sep) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.println("name\t|\tuid\t|\ttype\t|\tuniqname\t|\t");
		for (String name : this.allNames()) {
            boolean primaryp = false;
            boolean synonymp = false;
			for (Taxon node : this.lookup(name)) {
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

    void dumpForwards(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
        out.format("id\treplacement\n");
        int count = 0;
        for (String id : idIndex.keySet()) {
            Taxon node = idIndex.get(id);
            if (!node.id.equals(id)) {
                out.format("%s\t%s\n", id, node.id);
                ++count;
            }
        }
        System.out.format("| %s id aliases\n", count);
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
				Flag.printFlags(node.properFlags, node.inferredFlags, out);
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

	// Set SIBLING_HIGHER flags
	public void analyzeRankConflicts() {
		for (Taxon root : this.roots())
			analyzeRankConflicts(root, false);  //SIBLING_HIGHER
	}

	// GBIF (and IF?) only
	public void analyzeMajorRankConflicts() {
		for (Taxon root : this.roots())
			analyzeRankConflicts(root, true);
	}

	// Work in progress - don't laugh - will in due course be
	// converting flag set representation from int to EnumSet<Flag>

    // Former containers
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
         | INCERTAE_SEDIS | UNPLACED
         | TATTERED  // deprecated
         | FORMER_CONTAINER);   // kludge, review

	// Individually troublesome - not sticky - combine using &  ? no, | ?
	static final int NOT_OTU			 = (1 <<  9);
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
	static final int INFRASPECIFIC		 = (1 << 21);  // Is below a 'species'
	static final int BARREN			     = (1 << 22);  // Does not contain a species?

    // Intended to match treemachine's list
	static final int HIDDEN_FLAGS =
                 (Taxonomy.HIDDEN |
                  Taxonomy.EXTINCT |
                  Taxonomy.BARREN |
                  Taxonomy.NOT_OTU |
                  Taxonomy.HYBRID |
                  Taxonomy.VIRAL |
                  Taxonomy.INCERTAE_SEDIS_ANY);

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

            // e.g. lowrank = 72, highrank = 23   (backwards... kingdom is 'higher' than order)

			if (highrank >= 0) {	// Any non-"no rank" children?

				// highrank is the highest (lowest-numbered) rank among all the children.
				// Similarly lowrank.  If they're different we have a 'rank conflict'.
				// Some 'rank conflicts' are 'minor', others are 'major'.
				if (highrank < lowrank) {
					// Suppose the parent is a class. We're looking at relative ranks of the children...
					// Two cases: order/family (minor), order/genus (major)
					int high = highrank / 100;		  //e.g. order
					for (Taxon child : node.children) {
						int chrank = child.rankAsInt;	   //e.g. family or genus
						if (chrank < 0) continue;		   // skip "no rank" children
						// we know chrank >= highrank
							//genus->genus, subfamily->genus
							if (majorp && (chrank / 100) > high) {
                                // e.g. a genus that has a family [in an order] as a sibling
                                if (child.count() > 20000)
                                    System.out.format("!! %s %s sibling to %s %s\n",
                                                      child.rank, child, highchild.rank, highchild);
                                child.addFlag(MAJOR_RANK_CONFLICT);
                            } else if (chrank > highrank) {  // if lower rank than some sibling
								child.addFlag(SIBLING_HIGHER); //e.g. family with superfamily sibling
                            // tbd: should clear the flag when appropriate
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
        if (node.name != null) {
            if (notOtuRegex.matcher(node.name).find()) 
                node.addFlag(NOT_OTU);
            if (hybridRegex.matcher(node.name).find()) 
                node.addFlag(HYBRID);
            if (viralRegex.matcher(node.name).find()) 
                node.addFlag(VIRAL);

            if (unclassifiedRegex.matcher(node.name).find()) // Rule 3+5
                // sometimes happens in NCBI, e.g. 
                // unclassified Peltospiridae Taxonomy ID: 1037581
                node.addFlag(UNCLASSIFIED);
            else if (environmentalRegex.matcher(node.name).find()) // Rule 3+5
                // doesn't happen
                node.addFlag(ENVIRONMENTAL);
            else if (incertae_sedisRegex.matcher(node.name).find()) // Rule 3+5
                // doesn't happen (I believe)
                node.addFlag(INCERTAE_SEDIS);
        }

		// Recursive descent
		if (node.children != null)
			for (Taxon child : node.children)
				analyzeOTUs(child);
	}

    // Elide 'incertae sedis'-like containers, encoding the
    // incertaesedisness of their children in flags.
    // This gets applied to every taxonomy, not just NCBI, on ingest.

	public void analyzeContainers() {
        analyzeContainers(forest);
	}

	public static void analyzeContainers(Taxon node) {
		// Recursive descent
		if (node.children != null) {
			for (Taxon child : new ArrayList<Taxon>(node.children))
				analyzeContainers(child);

            int flag = 0;
            if (node.name != null) {
                if (unclassifiedRegex.matcher(node.name).find()) // Rule 3+5
                    flag = UNCLASSIFIED;
                else if (environmentalRegex.matcher(node.name).find()) // Rule 3+5
                    flag = ENVIRONMENTAL;
                else if (incertae_sedisRegex.matcher(node.name).find()) // Rule 3+5
                    flag = INCERTAE_SEDIS;
            }
            if (flag != 0) {
                node.addFlag(flag);
                // After (compare elide())
                // Splice the node out of the hierarchy, but leave it as a
                // residual terminal non-OTU node.
                if (!node.isRoot()) {
                    for (Taxon child : new ArrayList<Taxon>(node.children))
                        // changeParent sets properFlags
                        child.changeParent(node.parent, flag);
                    node.addFlag(WAS_CONTAINER);
                }
            }
        }
	}

    // Propagate heritable flags tipward.

	public void inferFlags() {
		for (Taxon root : this.roots()) {
			this.heritFlags(root, 0);
			this.analyzeBarren(root);
        }
	}

	private void heritFlags(Taxon node, int inferredFlags) {
        boolean before = node.isHidden();
		node.inferredFlags = inferredFlags;
        if (node.name != null
            && node.name.equals("Ephedra gerardiana"))
            if (before != node.isHidden())
                System.out.format("* %s hidden %s -> %s\n", node, before, node.isHidden());

		if (node.rank != null && node.children != null && node.rank.equals("species"))
			for (Taxon child : node.children)
                child.inferredFlags |= Taxonomy.INFRASPECIFIC;

		if (node.children != null) {
			int bequest = inferredFlags | node.properFlags;		// What the children inherit
			for (Taxon child : node.children)
				heritFlags(child, bequest);
		}
	}

	// 1. Set the (inferred) INFRASPECIFIC flag of any taxon that is a species or has
	// one below it.  
	// 2. Set the (inferred) BARREN flag of any taxon that doesn't
	// contain anything at species rank or below.
	// 3. Propagate EXTINCT (inferred) upwards.

	static void analyzeBarren(Taxon node) {
		boolean specific = false;
		boolean barren = true;      // No species?
		if (node.rank != null) {
			Integer rank = ranks.get(node.rank);
			if (rank != null) {
				if (rank == SPECIES_RANK)
					specific = true;
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
                if (false)
                    if (specific)
                        child.properFlags |= Taxonomy.INFRASPECIFIC;
                    else
                        child.properFlags &= ~Taxonomy.INFRASPECIFIC;
				analyzeBarren(child);
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
		if (barren)
			node.inferredFlags |= Taxonomy.BARREN;
		else
			node.inferredFlags &= ~Taxonomy.BARREN;
	}
	
	static Pattern notOtuRegex =
		Pattern.compile(
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

	static Pattern hybridRegex = Pattern.compile(" x |\\bhybrid\\b");

	static Pattern viralRegex =
		Pattern.compile(
						"\\bviral\\b|" +
						"\\b[Vv]iroids\\b|" +
						"\\b[Vv]iruses\\b|" +
						"\\bvirus\\b"
						);

	static Pattern unclassifiedRegex =
		Pattern.compile(
                        // Always container
						"\\bmycorrhizal samples\\b|" +
                        // Sometimes container, sometimes not
						"\\b[Uu]nclassified\\b|" +
                        // These never occur as containers
						"\\buncultured\\b|" +       // maybe these aren't OTUs!
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
         "division",            // h2007
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
         "natio",               // worms
		 "variety",
		 "varietas",
		 "subvariety",
		 "forma",
		 "subform",
		 "samples"},
	};

	static Map<String, Integer> ranks = null;

	static void initRanks() {
        if (ranks == null) {    // do only once.  could use static { ... }
            ranks = new HashMap<String, Integer>();
            for (int i = 0; i < rankStrings.length; ++i) {
                for (int j = 0; j < rankStrings[i].length; ++j)
                    ranks.put(rankStrings[i][j], (i+1)*100 + j*10);
            }
            ranks.put("no rank", -1);
            SPECIES_RANK = ranks.get("species");
        }
	}

    static { initRanks(); }

	static int SPECIES_RANK = -1; // ranks.get("species");

	// Select subtree rooted at a specified node

	public Taxonomy select(String designator) {
		return select(this.unique(designator));
	}
	
	public Taxonomy select(Taxon sel) {
		if (sel != null) {
			Taxonomy tax2 = new SourceTaxonomy();
			tax2.idspace = this.idspace; // ???
            return finishSelection(tax2, this.select(sel, tax2));
		} else {
			System.err.println("** Missing or ambiguous selection name");
			return null;
		}
	}

    // Recursion
	// node is in source taxonomy, tax is the destination taxonomy ('union' or similar)
	static Taxon select(Taxon node, Taxonomy tax) {
		Taxon sam = dup(node, tax, "select");
		if (node.children != null)
			for (Taxon child : node.children) {
				Taxon c = select(child, tax);
				sam.addChild(c);
			}
		return sam;
	}

    Taxonomy finishSelection(Taxonomy tax2, Taxon selection) {
        System.out.println("| Selection has " + selection.count() + " taxa");
        tax2.addRoot(selection);
        this.copySelectedSynonyms(tax2);
        this.copySelectedIds(tax2);
        tax2.inferFlags();
        return tax2;
    }

    // Copy aliased ids from larger taxonomy to selected subtaxonomy, as appropriate
    // tax2 = the selection, this = where it came from

    void copySelectedIds(Taxonomy tax2) {
        int count = 0;
        for (String id : this.idIndex.keySet()) {
            Taxon node = this.idIndex.get(id);
            if (!node.id.equals(id) && tax2.idIndex.get(node.id) != null) {
                tax2.idIndex.put(id, node);
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
			Taxonomy tax2 = new SourceTaxonomy();
			tax2.idspace = this.idspace; // ???
            return finishSelection(tax2, selectToDepth(sel, tax2, depth));
		} else {
			System.err.println("** Missing or ambiguous name: " + sel);
			return null;
		}
	}

	Taxon selectToDepth(Taxon node, Taxonomy tax, int depth) {
		Taxon sam = dup(node, tax, "selectToDepth");
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
			return finishSelection(tax2, selectVisible(sel, tax2));
		} else
			return null;
	}

	// Copy only visible (non-hidden) nodes

	public Taxon selectVisible(Taxon node, Taxonomy tax) {
		if (node.isHidden()) return null;
		Taxon sam = null;
		if (node.children == null)
			sam = dup(node, tax, "selectVisible");
		else
			for (Taxon child : node.children) {
				Taxon c = selectVisible(child, tax);
				if (c != null) {
					if (sam == null)
						sam = dup(node, tax, "selectVisible");
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

		Taxon sam = dup(node, tax, "sample");
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
			Newick.appendNewickTo(cutting, true, buf);
			out.print(buf.toString());
			out.close();
		}
		return tax;
	}

	// List of nodes for which N/3 < size <= N < parent size

	Taxon chop(Taxon node, int m, int n, List<Taxon> chopped, Taxonomy tax) {
		int c = node.count();
		Taxon newnode = dup(node, tax, "sample");
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

    static Taxon dup(Taxon node, Taxonomy tax, String reason) {
        Taxon newnode = node.dup(tax, reason);
        if (node.id != null)
            newnode.setId(node.id);
        return newnode;
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
                        root.changeParent(biggest, Taxonomy.UNPLACED); // removes from roots
                    ++flushed;
                }
            System.out.format("| Removed %s smaller trees\n", flushed);
        }
	}

    // Sort by size and make sure the biggest one is placed.  This is idempotent.
    public List<Taxon> normalizeRoots() {
        List<Taxon> rootsList = new ArrayList(this.forest.children);
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
        return rootsList;
    }

    public void placeBiggest() {
        Taxon biggest = this.unique("life");
        if (biggest == null || !biggest.isRoot())
            biggest = this.normalizeRoots().get(0);  // sort the roots list
        biggest.properFlags &= ~Taxonomy.HIDDEN_FLAGS;
    }

	// -------------------- Newick stuff --------------------
	// Render this taxonomy as a Newick string.
	// This feature is very primitive and only for debugging purposes!

	public String toNewick() {
        return this.toNewick(true);
    }

	public String toNewick(boolean useIds) {
		StringBuffer buf = new StringBuffer();
		for (Taxon root: this.roots()) {
			Newick.appendNewickTo(root, useIds, buf);
			buf.append(";");
		}
		return buf.toString();
	}

	public void dumpNewick(String outfile) throws java.io.IOException {
		PrintStream out = openw(outfile);
		out.print(this.toNewick());
		out.close();
	}

    // The id of the node in the taxonomy that has highest numbered id.

	public long maxid() {
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

	public void assignNewIds() {
        assignNewIds(0);
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

    void assignDummyIds() {
        long minid = -1L;
		for (Taxon node : this)
			if (node.id == null) {
                String id;
                do {
                    id = Long.toString(minid--);
                } while (lookupId(id) != null);
				node.setId(id);
				node.markEvent("no-id");
			}
    }

	public static SourceTaxonomy parseNewick(String newick) {
		SourceTaxonomy tax = new SourceTaxonomy();
        Taxon root = Newick.newickToNode(newick, tax);
		tax.addRoot(root);
        root.properFlags = 0;   // not unplaced
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

	public Taxon taxon(String name) {
		Taxon probe = maybeTaxon(name);
		if (probe == null)
			System.err.format("** No unique taxon found with this name: %s\n", name);
		return probe;
	}

	// Look up a taxon by name or unique id.  Name must be unique in the taxonomy.
	public Taxon maybeTaxon(String name) {
		List<Taxon> probe = this.lookup(name);
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
				if (!node.isRoot()
                    && node.parent.name != null
                    && node.parent.name.equals(context))
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
				if (!node.isRoot()
                    && node.parent.name != null
                    && node.parent.name.equals(name))
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
		if (rank != null && !rank.equals("no rank"))
			t.rank = rank;
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

    // ----

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

	public void copyMappedSynonyms(Taxonomy target) {
		copySynonyms(target, true);
	}

	// Propogate synonyms from source taxonomy (= this) to union.
	// Some names that are synonyms in the source might be primary names in the union,
	//	and vice versa.
	void copySynonyms(Taxonomy target, boolean mappedp) {
		int count = 0;

		// For each name in source taxonomy...
		for (String syn : this.allNames()) {

			// For each node that the name names...
			for (Taxon node : this.lookup(syn)) {
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

	// Compute the inverse of the name->node map.
	public Map<Taxon, Collection<String>> makeSynonymIndex() {
		Map<Taxon, Collection<String>> nameMap = new HashMap<Taxon, Collection<String>>();
		for (String name : this.allNames())
			for (Taxon node : this.lookup(name)) {
				Collection<String> names = nameMap.get(node);  // of this node
				if (names == null) {
					names = new ArrayList(1);
					nameMap.put(node, names);
				}
				names.add(name);
			}
		return nameMap;
	}

    // Called just before alignment.  Clears depth cache and comapped.
	public void reset() {
		for (Taxon root: this.roots()) {
            // Set comapped to null
			root.resetComapped();
			// Prepare for subsumption checks
            root.resetDepths();
		}
	}

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
                System.out.format("** Cannot un-lump %s\n", node1);
                return false;
            }
            else
                return false;
        }
    }

    // Event logging

	public boolean markEvent(String tag) { // formerly startReport
        if (this.eventlogger != null)
            return this.eventlogger.markEvent(tag);
        else return false;
	}

    public boolean markEvent(String tag, Taxon node) {
        if (this.eventlogger != null)
            return this.eventlogger.markEvent(tag, node);
        else return false;
    }

    public boolean markEvent(String tag, Taxon node, Taxon unode) {
        // sort of a kludge
        if (this.eventlogger != null)
            return this.eventlogger.markEvent(tag, node, unode);
        else return false;
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
