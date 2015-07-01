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
    static final String SKOS_BASE_URI = "http://purl.obolibrary.org/obo/OTT_";

    public Map<String, List<Taxon>> nameIndex = new HashMap<String, List<Taxon>>();
	public Map<String, Taxon> idIndex = new HashMap<String, Taxon>();
	Set<Taxon> roots = new HashSet<Taxon>(1);
	protected String tag = null;
	int nextSequenceNumber = 0;
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
		} else if (nodes.contains(node))
			return;
		nodes.add(node);
	}

	public int count() {
		int total = 0;
		for (Taxon root : this.roots)
			total += root.count();
		return total;
	}

	public int tipCount() {
		int total = 0;
		for (Taxon root : this.roots)
			total += root.tipCount();
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
			tax.roots.add(tax.newickToNode(designator));
		else {
			if (!designator.endsWith("/")) {
				System.err.println("Taxonomy designator should end in / but doesn't: " + designator);
				designator = designator + "/";
			}
			System.out.println("--- Reading " + designator + " ---");
			tax.loadTaxonomy(designator);
		}
		tax.elideDubiousIntermediateTaxa();
		tax.investigateHomonyms();
		//??? tax.analyzeContainers(); // incertae sedis and similar
		tax.assignNewIds(0);	// foo
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
		// Don't do this - barren and extinct lead to problems
		// this.setDerivedFlags();
		this.propagateFlags();
	}

	// This gets overridden in the UnionTaxonomy class
	public void dump(String outprefix, String sep) throws IOException {
		new File(outprefix).mkdirs();
		this.assignNewIds(0);
		this.analyze();
		this.dumpNodes(this.roots, outprefix, sep);
		this.dumpSynonyms(outprefix + "synonyms.tsv", sep);
		this.dumpMetadata(outprefix + "about.json");
		this.dumpHidden(outprefix + "hidden.tsv");
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

	// load | dump taxonomy proper

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
				String rawname = parts[2];

				String name = normalizeName(rawname);

				Taxon node = this.idIndex.get(id);
				if (node == null) {
					// node was created earlier because it's the parent of some other node.
					node = new Taxon(this);
					node.setId(id); // stores into this.idIndex
				} else {
					System.err.format("** Duplicate id definition: %s %s\n", node.id, node.name);
				}

				String parentId = parts[1];
				parentMap.put(node, parentId);

				node.setName(name);
				initTaxon(node, parts);
				if (!name.equals(rawname)) {
					addSynonym(rawname, node);
					++normalizations;
				}
				if (this.flagscolumn != null && parts[this.flagscolumn].length() > 0)
					// this.parseFlags(parts[this.flagscolumn], node);
					Flag.parseFlags(parts[this.flagscolumn], node);
			}
			++row;
			if (row % 500000 == 0)
				System.out.println(row);
		}
		br.close();
		if (normalizations > 0)
			System.out.format("| %s names normalized\n", normalizations);

		Set<String> losers = new HashSet<String>();
		for (Taxon node : parentMap.keySet()) {
			String parentId = parentMap.get(node);
			Taxon parent = this.idIndex.get(parentId);
			if (parent == null) {
				if (parentId.length() > 0 && !parentId.equals("null") && !parentId.equals("not found") &&
					!losers.contains(parentId)) {
					System.err.format("** Parent %s missing for %s %s\n", parentId, node.id, node.name);
					losers.add(parentId);
				}
				roots.add(node);
			} else if (parentId.equals(node.id)) {
				System.err.format("** Taxon is its own parent: %s %s\n", node.id, node.name);
			} else if (parent.descendsFrom(node)) {
				System.err.format("** Cycle detected in input taxonomy: %s %s\n", node, parent);
			} else {
				parent.addChild(node);
			}
		}

		for (Taxon node : this.idIndex.values())
			if (node.name == null) {
				System.err.println("** Identifier with no associated name, probably a missing parent: " + node.id);
				node.setName("undefined:" + node.id);
			}

		if (roots.size() == 0)
			System.err.println("** No root nodes!");
		else {
			if (roots.size() > 1)
				System.err.println("| There are " + roots.size() + " roots");
			int total = 0;
			for (Taxon root : roots)
				total += root.count();
			if (row != total)
				// Shouldn't happen
				System.err.println(this.getTag() + " is ill-formed: " +
								   row + " rows, but only " + 
								   total + " reachable from roots");
		}
	}

	// Populate fields of a Taxon object from fields of row of taxonomy file
	// parts = fields from row of dump file
	void initTaxon(Taxon node, String[] parts) {
		if (parts.length >= 4) {
			String rank = parts[3];
			if (rank.length() == 0 || rank.startsWith("no rank") ||
				rank.equals("terminal") || rank.equals("samples"))
				rank = Taxonomy.NO_RANK;
			else if (Taxonomy.ranks.get(rank) == null) {
				System.err.println("!! Unrecognized rank: " + rank + " " + node.id);
				rank = Taxonomy.NO_RANK;
			}
			node.rank = rank;
		}
		// TBD: map source+sourceId when present (deprecated),
		// parse sourceInfo when present

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
			List<QualifiedId> qids = new ArrayList<QualifiedId>(1);
			qids.add(new QualifiedId(parts[this.sourcecolumn],
									 parts[this.sourceidcolumn]));
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

	void elideDubiousIntermediateTaxa() {
		Set<Taxon> dubious = new HashSet<Taxon>();
		for (Taxon node : this) {
			boolean ic = incertae_sedisRegex.matcher(node.name).find();
			if (ic)
				node.properFlags |= INCERTAE_SEDIS; // kludge
			if (node.parent != null &&
				node.parent.children.size() == 1 &&
				node.children != null)
				// Do this for SILVA and for everything else
				if (ic)
					dubious.add(node);
				else if (node.parent.name.equals(node.name))
					dubious.add(node);
		}
		for (Taxon node: dubious) {
			System.out.format("! Eliding %s in %s, %s children\n",
							  node.name,
							  node.parent.name,
							  (node.children == null ?
							   "no" :
							   node.children.size())
							  );
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
				if (replacement != null && replacement != node.parent) {
					if (false)
					System.err.println("Post-smushing kludge: " +
									   node.parent.id + " => " +
									   replacement.id);
					node.parent = replacement;
				}
			}
	}

	void dumpNodes(Collection<Taxon> nodes, String outprefix, String sep) throws IOException {
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
            if(node.parent != null && node.parent.id != null) {
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
		out.print(((node.parent == null || rootp) ? "" : node.parent.id)  + sep);
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
		Flag.printFlags(node.properFlags, node.inheritedFlags, out);
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
					Taxon node = this.idIndex.get(id);
					if (type.equals("in-part")) continue;
					if (type.equals("includes")) continue;
					if (type.equals("type material")) continue;
					if (type.endsWith("common name")) continue;
					if (type.equals("authority")) continue;	   // keep?
					if (node == null) {
						if (++losers < 10)
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

	public boolean addSynonym(String syn, Taxon node) {
		if (node.taxonomy != this)
			System.err.println("!? Synonym for a node that's not in this taxonomy: " + syn + " " + node);
		List<Taxon> nodes = this.nameIndex.get(syn);
		if (nodes != null) {
			for (Taxon n : nodes)
				if (n == node || n.name.equals(syn))
					return false;	 //lots of these System.err.println("Redundant synonymy: " + id + " " + syn);
		} else {
			nodes = new ArrayList<Taxon>(1);
			this.nameIndex.put(syn, nodes);
		}
		nodes.add(node);
		return true;
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
                            if (node.parent != null) {
                                System.out.format("Synonym for node with no id: %s\n", node.name);
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

	// NCBI only (not SILVA)
	public void analyzeOTUs() {
		for (Taxon root : this.roots)
			analyzeOTUs(root);	// mutates the tree
	}

	// GBIF and IF only
	public void analyzeMajorRankConflicts() {
		for (Taxon root : this.roots)
			analyzeRankConflicts(root, true);
	}

	// Final analysis, after assembling entire taxonomy, before writing it out
	public void analyze() {
		for (Taxon root : this.roots)
			analyzeRankConflicts(root, false);  //SIBLING_LOWER and SIBLING_HIGHER
		setDerivedFlags();
	}

	public void analyzeContainers() {
		for (Taxon root : this.roots)
			analyzeContainers(root, 0);
	}

	public void setDerivedFlags() {
		for (Taxon root : this.roots)
			this.analyzeBarren(root);
		this.propagateFlags();
	}

	public void propagateFlags() {
		for (Taxon root : this.roots)
			this.propagateFlags(root, 0);
	}

	// Work in progress - don't laugh - will be converting flag set
	// representation from int to EnumSet<Flag>

	// Set during assembly
	static final int TATTERED			 = (1 << 0);	  // combine using |  ??
	static final int FORCED_VISIBLE		 = (1 << 1);	  // combine using |
	static final int EDITED				 = (1 << 2);			  // combine using |
	static final int EXTINCT			 = (1 << 3);	  // combine using |
	static final int HIDDEN				 = (1 << 4);	  // combine using &

	// NCBI - individually troublesome - not sticky - combine using &  ? no, | ?
	static final int NOT_OTU			 = (1 << 5);
	static final int HYBRID				 = (1 << 6);
	static final int VIRAL				 = (1 << 7);

	// Parent-dependent.  Retain value
	static final int MAJOR_RANK_CONFLICT = (1 << 8);

	// Final analysis...
	// Containers - unconditionally so.
	static final int UNCLASSIFIED		 = (1 << 9);
	static final int ENVIRONMENTAL		 = (1 << 10);
	static final int INCERTAE_SEDIS		 = (1 << 11);

	// Australopithecus
	static final int SIBLING_LOWER		 = (1 << 12);
	static final int SIBLING_HIGHER		 = (1 << 13);

	// Is below a 'species'
	// Unconditional ?
	static final int INFRASPECIFIC		 = (1 << 14);

	// Propagated upward
	static final int BARREN			     = (1 << 15);

	// FLUSH ME
	static Pattern commaPattern = Pattern.compile(",");
	void parseFlags(String flags, Taxon node) {
		// String[] tags = commaPattern.split(flags);
		if (flags.contains("extinct"))
			// kludge. could be _direct or _inherited
			node.properFlags |= Taxonomy.EXTINCT;
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
	//	 proper - applies particularly to this node
	//	 inherited - applies to this node because it applies to an ancestor
	//	   (where in some cases the ancestor may later be 'elided' so
	//	   not an ancestor any more)

	// analyzeOTUs: set taxon flags based on name, leading to dubious
	// taxa being hidden.
	// We use this for NCBI but not for SILVA.

	static void analyzeOTUs(Taxon node) {
		// Prepare for recursive descent
		if (notOtuRegex.matcher(node.name).find()) 
			node.properFlags |= NOT_OTU;
		if (hybridRegex.matcher(node.name).find()) 
			node.properFlags |= HYBRID;
		if (viralRegex.matcher(node.name).find()) 
			node.properFlags |= VIRAL;

		// Recursive descent
		if (node.children != null)
			for (Taxon child : node.children)
				analyzeOTUs(child);
	}

	// Flags to set for all taxonomies.	 Also elide container pseudo-taxa

	public static void analyzeContainers(Taxon node, int inheritedFlags) {
		// Before
		node.inheritedFlags |= inheritedFlags;

		int flag = 0;

		if (unclassifiedRegex.matcher(node.name).find()) // Rule 3+5
			flag = UNCLASSIFIED;  // includes uncultured
		if (environmentalRegex.matcher(node.name).find()) // Rule 3+5
			flag = ENVIRONMENTAL;
		if (incertae_sedisRegex.matcher(node.name).find()) // Rule 3+5
			flag = INCERTAE_SEDIS;

		// Recursive descent
		if (node.children != null) {
			int bequest = inheritedFlags | node.properFlags | flag;		// What the children inherit
			for (Taxon child : new ArrayList<Taxon>(node.children))
				analyzeContainers(child, bequest);
		}

		// After
		if (flag != 0) {
			// Splice the node out of the hierarchy, but leave it as a
			// residual terminal non-OTU node.
			if (node.children != null && node.parent != null)
				for (Taxon child : new ArrayList<Taxon>(node.children)) {
					child.properFlags |= flag;
					child.changeParent(node.parent);
				}
			node.properFlags |= NOT_OTU;
		}
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
					child.properFlags |= INFRASPECIFIC;
				else
					child.properFlags &= ~INFRASPECIFIC;
				analyzeBarren(child);
				if ((child.properFlags & BARREN) == 0) barren = false;
				if ((child.properFlags & EXTINCT) == 0) allextinct = false;
			}
			if (allextinct) {
				node.properFlags |= EXTINCT;
				if (node.sourceIds != null && node.sourceIds.get(0).prefix.equals("ncbi"))
					;//System.out.format("| Induced extinct: %s\n", node);
			}
			// We could do something similar for all of the hidden-type flags
		}
		if (barren)
			node.properFlags |= BARREN;
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
		Pattern.compile("\\bincertae sedis\\b|\\bIncertae sedis\\b|\\bIncertae Sedis\\b|" +
						"(unallocated)|\\bUnallocated\\b|\\bmitosporic\\b");

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
			tax2.roots.add(selectionRoot);
			this.copySelectedSynonyms(tax2);
			return tax2;
		} else {
			System.err.println("** Missing or ambiguous selection name");
			return null;
		}
	}

	// node is in source taxonomy, tax is the destination taxonomy ('union' or similar)
	Taxon select(Taxon node, Taxonomy tax) {
		Taxon sam = node.dup(tax);
		node.mapTo(sam);
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
			tax2.roots.add(selection);
			this.copySelectedSynonyms(tax2);
			return tax2;
		} else {
			System.err.println("** Missing or ambiguous name: " + sel);
			return null;
		}
	}

	Taxon selectToDepth(Taxon node, Taxonomy tax, int depth) {
		Taxon sam = node.dup(tax);
		node.mapTo(sam);
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
			Taxon selection = sel.selectVisible(tax2);
			System.out.println("| Selection has " + selection.count() + " taxa");
			tax2.roots.add(selection);
			this.copySelectedSynonyms(tax2);
			return tax2;
		} else
			return null;
	}

	// Select a proportional sample of the nodes in a tree

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
		for (Taxon r : this.roots) root = r;	//bad kludge. uniroot assumed
		Taxon newroot = chop(root, m, n, cuttings, tax);
		tax.roots.add(newroot);
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
		Taxon newnode = node.dup(tax);
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
		Taxon second = rootsList.get(1);
		int count2 = second.count();
		if (rootsList.size() >= 2 && count1 < count2*500)
			System.err.format("** Nontrivial forest: biggest is %s, 2nd biggest is %s\n", count1, count2);
		System.out.format("| Deforesting: keeping %s (%s), 2nd biggest is %s (%s)\n", biggest.name, count1, second.name, count2);
		int flushed = 0;
		for (Taxon root : new HashSet<Taxon>(rootsList))
			if (!root.equals(biggest)) {
				if (false)
					root.prune();
				else {
					root.changeParent(biggest); // removes from roots
					root.incertaeSedis();
				}
				++flushed;
			}
		System.out.format("| Removed %s smaller trees\n", flushed);
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

	public void assignNewIds(long sourcemax) {
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
				Taxon node = new Taxon(this);
				node.setName(name);
				node.rank = rank;
				node.setSourceIds(sourceInfo);
				parent.addChild(node);
				node.properFlags |= Taxonomy.EDITED;
			}
		} else if (command.equals("move")) {
			if (existing == null)
				System.err.println("! (move) No taxon to move: " + name);
			else {
				if (existing.parent == parent)
					System.err.println("! (move) Note: already in the right place: " + name);
				else {
					// TBD: CYCLE PREVENTION!
					existing.changeParent(parent);
					existing.properFlags |= Taxonomy.EDITED;
				}
			}
		} else if (command.equals("prune")) {
			if (existing == null)
				System.err.println("! (prune) No taxon to prune: " + name);
			else
				existing.prune();

		} else if (command.equals("fold")) {
			if (existing == null)
				System.err.println("! (fold) No taxon to fold: " + name);
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
			if (node.parent != null &&
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
									String shp = (sh.parent != null ? sh.parent.name : "(root)");
									String gtp = (gt.parent != null ? gt.parent.name : "(root)");
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
		return this.idIndex.get(name);
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
				if (node.parent != null && node.parent.name.equals(context))
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
				if (node.parent != null && node.parent.name.equals(name))
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

	public Taxon newTaxon(String name, String rank, String sourceIds) {
		if (this.lookup(name) != null)
			System.err.format("** Warning: A taxon by the name of %s already exists\n", name);
		Taxon t = new Taxon(this);
		t.setName(name);
		if (!rank.equals("no rank"))
			t.rank = rank;
        if (sourceIds != null && sourceIds.length() > 0)
            t.setSourceIds(sourceIds);
		this.roots.add(t);
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
		if (!(snode.taxonomy instanceof SourceTaxonomy)) {
			System.err.format("** One of the two nodes must come from a source taxonomy: %s %s\n", unode, snode);
			return;
		}
		if (polarity) {			// same
			if (snode.mapped != null) {
				if (snode.mapped != unode)
					System.err.format("** The taxa have already been determined to be different: %s\n", snode);
				return;
			}
			snode.unifyWith(unode);
		} else {				// notSame
			if (snode.mapped != null) {
				if (snode.mapped == unode)
					System.err.format("** The taxa have already been determined to be the same: %s\n", snode);
				return;
			}
			// Give the source node a place to go in the union that is
			// different from the union node it's different from
			Taxon evader = new Taxon(unode.taxonomy);
			snode.unifyWithNew(evader);
			evader.addSource(snode);
			// Now evader != unode, as desired.
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
			Taxon newnode = this.idIndex.get(node.id);
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
				if (newnode.parent == null && node.parent == null)
					;
				else if (newnode.parent == null && node.parent != null)
					reportDifference("raised-to-root", node, node.parent, null, out);
				else if (newnode.parent != null && node.parent == null)
					reportDifference("no-longer-root", node, null, newnode.parent, out);
				else if (!newnode.parent.id.equals(node.parent.id))
					reportDifference("moved", node, node.parent, newnode.parent, out);
				if (newnode.isHidden() && !node.isHidden())
					reportDifference("hidden", node, null, null, out);
				else if (!newnode.isHidden() && node.isHidden())
					reportDifference("exposed", node, null, null, out);
			}
		}
		for (Taxon newnode : this) {
			Taxon node = other.idIndex.get(newnode.id);
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
					 : target.idIndex.get(node.id));

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

	// Compute sibling taxa {a', b'} such that a' includes a and b' includes b

	public static Taxon[] divergence(Taxon a, Taxon b) {
		if (a.taxonomy != b.taxonomy) {
			if (a.taxonomy instanceof SourceTaxonomy) {
				while (a.mapped == null) {
					a = a.parent;
					if (a == null) return null;
				}
				a = a.mapped;
			}
			if (b.taxonomy instanceof SourceTaxonomy) {
				while (b.mapped == null) {
					b = b.parent;
					if (b == null) return null;
				}
				b = b.mapped;
			}
		}
		if (a.taxonomy != b.taxonomy) {
			System.err.format("*** incomparable: %s %s\n", a, b);
			return null;
		}
		int da = a.measureDepth();
		int db = b.measureDepth();
		while (db > da) {
			b = b.parent;
			--db;
		}
		while (da > db) {
			a = a.parent;
			--da;
		}
		while (a != null && a.parent != b.parent) {
			a = a.parent;
			b = b.parent;
			--da;
		}
		if (a != null && b != null && a.parent != null) {
			Taxon[] answer = {a, b};
			return answer;
		} else
			return null;
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

    Map<String, Integer> preferredIds = new HashMap<String, Integer>();

    // e.g. "ids-that-are-otus.tsv"
    public void loadPreferredIds(String filename) throws IOException {
		System.out.println("--- Loading preferred ids from " + filename + " ---");
		BufferedReader br = Taxonomy.fileReader(new File(filename));
		String str;
		while ((str = br.readLine()) != null) {
            String[] row = tabPattern.split(str);
            preferredIds.put(row[0], row[1].length());
        }
        br.close();
    }


	// Compute the inverse of the name->node map.
	public Map<Taxon, Collection<String>> makeNameMap() {
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


}

// end of class Taxonomy

    class Stat {
        String tag;
        int i = 0;
        int inc(Taxon x, Taxon n, Taxon m) { if (i<5) System.out.format("%s %s %s %s\n", tag, x, n, m); return ++i; }
        Stat(String tag) { this.tag = tag; }
        public String toString() { return "" + i + " " + tag; }
    }

class SourceTaxonomy extends Taxonomy {

	SourceTaxonomy() {
	}

	public UnionTaxonomy promote() {
		return new UnionTaxonomy(this);
	}

	void mapInto(UnionTaxonomy union) {
        Alignment n = new AlignmentByName(this, union);
        if (false)
            // This loop isn't needed, since AlignmentByName does the
            // unifyWith itself (for the time being)
            for (Taxon node : this) {
                Taxon unode = n.target(node);
                if (unode != null)
                    node.unifyWith(unode);
            }
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
                Taxon nnode = n.target(node);
                Taxon mnode = m.target(node);
                if (nnode == mnode) {
                    if (nnode != null)
                        s0.inc(node, nnode, mnode);
                    else
                        s1.inc(node, nnode, mnode);
                } else if (mnode == null) {
                    // maps by name but not by membership
                    if (mnode == Taxon.AMBIGUOUS)
                        s2a.inc(node, nnode, mnode);
                    else
                        s2.inc(node, nnode, mnode);
                } else if (nnode == null)
                    s3.inc(node, nnode, mnode); // good - maps by membership but not by name
                else
                    s4.inc(node, nnode, mnode); // bad - incompatible mappings
            }
            System.out.println(s0); System.out.println(s1); System.out.println(s2); System.out.println(s2a); 
            System.out.println(s3); System.out.println(s4); 
        }

        union.sources.add(this);
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

	// Overrides dumpMetadata in class Taxonomy
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
			for (Taxon div : this.skeleton.roots) // this = union
				this.markDivisions(div, source);
	}

	// Before every alignment pass (folding source taxonomy into
	// union), all 'division' taxa (from the skeleton) that occur in
	// either the union or the source taxonomy are identified and the
	// 'division' field of each one is set to the corresponding
	// division node from the union taxonomy.

	// unifyp = true if this is a source taxonomy, false if union.

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
				node.unifyWith(unode);
				node.setDivision(unode);
				node.reallyUnifyWith(unode); // voodoo, cf. getDivision? divergence?
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
					n1.unifyWith(div); // hmm.  TBD: move this out of here
				if (n1 != null || div != null)
					++count;
			}
		}
		if (count > 0)
			System.out.println("Pinned " + count + " out of " + pins.length);
	}

	// -----

	// Clear out comapped, brackets, etc. from previous merges
	public void reset() {
		this.nextSequenceNumber = 0;
		for (Taxon root: this.roots) {
			root.reset();
			// Prepare for subsumption checks
			root.assignBrackets();
		}
	}

	void mergeIn(SourceTaxonomy source) {
		source.mapInto(this);
		source.augment(this);
		source.copyMappedSynonyms(this); // this = union
		this.reset();			// ??? see Taxonomy.same()
		Taxon.windyp = true; //kludge
	}

	// Assign ids, harvested from idsource and new ones as needed, to nodes in union.

	public void assignIds(SourceTaxonomy idsource) {
		this.idsource = idsource;
		// idsource.tag = "ids";
		idsource.mapInto(this);

		// Phase 1: recycle previously assigned ids.
		this.transferIds(idsource);

		// Phase 2: give new ids to union nodes that didn't get them above.
		long sourcemax = idsource.maxid();
		this.assignNewIds(sourcemax);
		// remember, this = union, idsource = previous version of ott

		Taxon.printStats();		// Taxon id clash
	}

	public void transferIds(SourceTaxonomy idsource) {
		Taxon.resetStats();
		System.out.println("--- Assigning ids to union starting with " + idsource.getTag() + " ---");

        Map<Taxon, String> assignments = new HashMap<Taxon, String>();

		for (Taxon node : idsource) {
            // Consider using node.id as the id for the union node it maps to
			Taxon unode = node.mapped;
			if (unode != null &&
                unode.id == null &&
                this.idIndex.get(node.id) == null) {

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
        Integer p1 = this.preferredIds.get(id1);
        Integer p2 = this.preferredIds.get(id2);
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
			if (y.sourceIds == null) return Answer.NOINFO;	  //won't happen?
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

	// Overrides dump method in class Taxonomy.
	// outprefix should end with a / , but I guess . would work too

	public void dump(String outprefix, String sep) throws IOException {
		new File(outprefix).mkdirs();
		this.assignNewIds(0);	// If we've seen an idsource, maybe this has already been done
		this.analyze();
		this.dumpMetadata(outprefix + "about.json");

		Set<String> scrutinize = null;

		if (this.idsource != null) {
			scrutinize = this.dumpDeprecated(this.idsource, outprefix + "deprecated.tsv");
			scrutinize.add("Methanococcus maripaludis");
			this.dumpLog(outprefix + "log.tsv", scrutinize);
		}

		this.dumpNodes(this.roots, outprefix, sep);
		this.dumpSynonyms(outprefix + "synonyms.tsv", sep);
		this.dumpHidden(outprefix + "hidden.tsv");
		this.dumpConflicts(outprefix + "conflicts.tsv");
	}

	// Overrides method in Taxonomy class

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
		out.println("id\tname\tsourceinfo\treason\twitness\treplacement");

		for (String id : idsource.idIndex.keySet()) {
            if (this.idIndex.get(id) != null)
                continue;       // id is not deprecated
			Taxon node = idsource.idIndex.get(id);
			String reason = "?";
			String witness = "";
			String replacement = null;
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
            if (replacement == null && node.mapped != null)
                replacement = node.mapped.id;
            if (replacement == null)
                replacement = "*";
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
		Taxon.markEvent(answer.reason);
	}
	void logAndReport(Answer answer) {
		this.log(answer);
		answer.x.report(answer.reason, answer.y, answer.witness);
	}

	// 3799 conflicts as of 2014-04-12
	// unode.comapped.parent == fromparent
	void reportConflict(Taxon paraphyletic, Taxon unode) {
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

class Conflict {
	Taxon paraphyletic;				// in source taxonomy
	Taxon unode;					// child, in union taxonomy
	Conflict(Taxon paraphyletic, Taxon unode) {
		this.paraphyletic = paraphyletic; this.unode = unode;
	}
    static String formatString = "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s";
	public String toString() {
		// cf. Taxon.mrca
		Taxon[] div = Taxonomy.divergence(paraphyletic, unode);
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
								 (unode.parent == null ? "" :
                                  unode.parent.name),
								 (unode.parent == null ? "" :
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

// Assess a criterion for judging whether x <= y or not x <= y
// Positive means yes, negative no, zero I couldn't tell you
// x is source node, y is union node

abstract class Criterion {

	abstract Answer assess(Taxon x, Taxon y);

    // Horrible kludge to avoid having to rebuild or maintain the name index

	static Criterion prunedp =
		new Criterion() {
			public String toString() { return "prunedp"; }
			Answer assess(Taxon x, Taxon y) {
                if (x.prunedp || y.prunedp)
                    return Answer.no(x, y, "prunedp", null);
                else
                    return Answer.NOINFO;
            }
        };

	static Criterion division =
		new Criterion() {
			public String toString() { return "same-division"; }
			Answer assess(Taxon x, Taxon y) {
				Taxon xdiv = x.getDivision();
				Taxon ydiv = y.getDivision();
				if (xdiv == ydiv)
					return Answer.NOINFO;
				else if (xdiv != null) {
					if (ydiv == null) {
						//System.err.format("No half-sided division exclusion: %s %s\n", x, y);
						return Answer.NOINFO;
					} else if (!xdiv.descendsFrom(ydiv) && !ydiv.descendsFrom(xdiv))
						return Answer.heckNo(x, y, "different-division", xdiv.name);
					else
						return Answer.NOINFO;
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
					else if (y.children != null)		// bad
						// 13 ?
						return Answer.no(x, y, "incompatible-with", a.name);
                    else
						return Answer.NOINFO;
				}
			}
		};

	static Criterion sameSourceId =
		new Criterion() {
			public String toString() { return "same-source-id"; }
			Answer assess(Taxon x, Taxon y) {
				// x is source node, y is union node.
				QualifiedId xid = maybeQualifiedId(x);
				QualifiedId yid = maybeQualifiedId(y);
                if (xid != null && xid.equals(yid))
					return Answer.yes(x, y, "same-source-id", null);
				else
					return Answer.NOINFO;
			}
		};


	// Match NCBI or GBIF identifiers
	// This kicks in when we try to map the previous OTT to assign ids, after we've mapped GBIF.
	// x is a node in the old OTT.	y, the union node, is in the new OTT.
	static Criterion anySourceId =
		new Criterion() {
			public String toString() { return "any-source-id"; }
			Answer assess(Taxon x, Taxon y) {
				// x is source node, y is union node.
				// Two cases:
				// 1. Mapping x=NCBI to y=union(SILVA): y.sourceIds contains x.id
				// 2. Mapping x=idsource to y=union: x.sourceIds contains ncbi:123
				// compare x.id to y.sourcenode.id
				QualifiedId xid = maybeQualifiedId(x);
                if (xid == null) return Answer.NOINFO;
				Collection<QualifiedId> yids = y.sourceIds;
				if (yids == null)
					return Answer.NOINFO;
				for (QualifiedId ysourceid : yids)
					if (xid.equals(ysourceid))
						return Answer.yes(x, y, "any-source-id-1", null);
				if (x.sourceIds != null)
					for (QualifiedId xsourceid : x.sourceIds)
						for (QualifiedId ysourceid : yids)
							if (xsourceid.equals(ysourceid))
								return Answer.yes(x, y, "any-source-id-2", null);
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
			Answer assess(Taxon x, Taxon y) {
				Taxon xdiv = x.getDivision();
				Taxon ydiv = y.getDivision();
				if (xdiv != ydiv) // One might be null
					// Evidence of difference, good enough to prevent name-only matches
					return Answer.heckNo(x, y, "not-same-division-knowledge", x.divisionName());
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
