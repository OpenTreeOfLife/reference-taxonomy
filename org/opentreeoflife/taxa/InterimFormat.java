/*
  Interim taxonomy file format
*/

package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.IOException;
import java.io.PrintStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;


public class InterimFormat {

	static Pattern tabVbarTab = Pattern.compile("\t\\|\t?");
	static Pattern tabOnly = Pattern.compile("\t");

    Taxonomy tax;

    InterimFormat(Taxonomy tax) {
        this.tax = tax;
    }

	// load | dump all taxonomy files

	public void loadTaxonomy(String dirname) throws IOException {
		this.loadMetadata(dirname + "about.json");
        String tname = dirname + "taxonomy.tsv";
        if (!new File(tname).exists())
            tname = dirname + "taxonomy";
        this.loadTaxonomyProper(tname);
        String sname = dirname + "synonyms.tsv";
        if (!new File(sname).exists())
            sname = dirname + "taxonomy";
		this.loadSynonyms(sname);
        this.loadForwards(dirname + "forwards.tsv");
        tax.purgeTemporaryIds();
	}

	// This gets overridden in a subclass.
    // Deforestate would have already been called, if it was going to be
	public void dump(String outprefix, String sep) throws IOException {
		new File(outprefix).mkdirs();
        tax.dumpExtras(outprefix);
		// obsolete dumpMetadata(outprefix + "about.json");
		dumpNodes(tax.roots(), outprefix, sep);
		dumpSynonyms(outprefix + "synonyms.tsv", sep);
        dumpForwards(outprefix + "forwards.tsv");
		// tax.dumpHidden(outprefix + "hidden.tsv");
	}

	public void dump(String outprefix) throws IOException {
        tax.dump(outprefix, "\t|\t");  //backward compatible
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
				System.err.println("** Opened file " + filename + " but no contents?");
			else {
				tax.properties = jsonObject;

				Object prefix = jsonObject.get("prefix");
				if (prefix != null)
					tax.idspace = (String)prefix;
			}

		} catch (ParseException e) {
			System.err.println(e);
		}
		fr.close();
	}

	// This is the UnionTaxonomy version.  Overrides method in Taxonomy class.

	public void dumpMetadata(String filename)	throws IOException {
		PrintStream out = Taxonomy.openw(filename);
        // obsolete tax.properties.put("version", tax.version);
		out.println(tax.properties);
		out.close();
	}

    public static void writeAsJson(Map m, File file) throws IOException {
        FileOutputStream stream = new FileOutputStream(file);
        Writer out
            = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));
        JSONObject.writeJSONString(m, out);
        out.close();
    }

	// load | dump taxonomy file

	void loadTaxonomyProper(String filename) throws IOException {
		BufferedReader br = Taxonomy.fileReader(filename);
		String str;
		int row = 0;

        Pattern pat = null;
		String suffix = null;	// Java loses somehow.  I don't get it

        boolean headerProcessed = false;
        int idcolumn = Integer.MAX_VALUE;
        int parentcolumn = Integer.MAX_VALUE;
        int namecolumn = Integer.MAX_VALUE;
        int rankcolumn = Integer.MAX_VALUE;
        int sourcecolumn = Integer.MAX_VALUE;
        int sourceidcolumn = Integer.MAX_VALUE;
        int infocolumn = Integer.MAX_VALUE;
        int flagscolumn = Integer.MAX_VALUE;

		while ((str = br.readLine()) != null) {

            if (pat == null) {
                if (str.contains("\t|\t"))
                    pat = tabVbarTab;
                else
                    pat = tabOnly;
            }

			String[] parts = pat.split(str);

            if (!headerProcessed) {
                headerProcessed = true;
                // First row in file.  Test it out as a potential
                // header row.
                Map<String, Integer> headerx = new HashMap<String, Integer>();
                for (int i = 0; i < parts.length; ++i)
                    headerx.put(parts[i], i);
                Integer h = headerx.get("uid");
                if (h == null) {
                    System.out.println("! No header row - saw " + str);
                    idcolumn = 0;
                    parentcolumn = 1;
                    namecolumn = 2;
                    rankcolumn = 3;
                } else {
                    idcolumn = h;
                    // uid | parentid | name | rank | ...
                    h = headerx.get("parent_uid");
                    if (h != null)
                        parentcolumn = h;
                    else
                        throw new RuntimeException("taxonomy header missing parent_uid");
                    h = headerx.get("name");
                    if (h != null)
                        namecolumn = h;
                    else
                        throw new RuntimeException("taxonomy header missing name");
                    h = headerx.get("rank");
                    if (h != null) rankcolumn = h;
                    h = headerx.get("source");
                    if (h != null) sourcecolumn = h;
                    h = headerx.get("sourceid");
                    if (h != null) sourceidcolumn = h;
                    h = headerx.get("sourceinfo");
                    if (h != null) infocolumn = h;
                    h = headerx.get("flags");
                    if (h != null) flagscolumn = h;
                    continue;
                }
            }

			++row;
			if (row % 250000 == 0) System.out.println(row);

            int len = parts.length;
            if (len < 3) {
                System.err.format("** Too few columns in row: %s\n", str);
                continue;
            }

            String id = parts[idcolumn];
            if (id.length() == 0) {
                System.err.format("** No id: %s\n", str);
                continue;
            }
            String parentId = parts[parentcolumn];
            String name = (namecolumn < len ? parts[namecolumn] : "");
            String rank = (rankcolumn < len ? parts[rankcolumn] : "");
            String flags = (flagscolumn < len ? parts[flagscolumn] : "");
            String info = (infocolumn < len ? parts[infocolumn] : "");
            String source = (sourcecolumn < len ? parts[sourcecolumn] : "");
            String sourceid = (sourceidcolumn < len ? parts[sourceidcolumn] : "");

            Taxon node = getTaxon(tax, id);

            if (node.parent != null) {
                System.err.format("** More than one row for this id: %s %s\n", id, name);
                continue;
            } else {
                initTaxon(node, name, rank,
                          flags, info, source, sourceid);

                // Delay until after all ids are defined
                if (parentId.equals("null") ||
                    parentId.equals("not found") ||
                    parentId.equals(""))
                    // root
                    tax.addRoot(node);
                else
                    getTaxon(tax, parentId).addChild(node);
            }
		}
		br.close();

        checkForMissingParents(tax);

        Taxon life = tax.unique("life");
        if (life != null) life.properFlags = 0;  // not unplaced

        int total = tax.count();
        if (row != total)
            // Shouldn't happen
            System.err.println(tax.getTag() + " is ill-formed: " +
                               row + " rows, but only " + 
                               total + " reachable from roots");
	}

    void checkForMissingParents(Taxonomy tax) {
        // Look for parents that lack their own records
        int count = 0;
		for (String id : tax.allIds()) {
            Taxon node = tax.lookupId(id);
            if (node.parent == null) {
                ++count;
                if (count <= 10)
                    System.err.format("** No record for parent %s\n", node);
                else if (count == 11)
                    System.err.format("** ...\n");
                node.setName(tax.getIdspace() + ":" + node.id);
                tax.addRoot(node);
            }
		}
    }

    Taxon getTaxon(Taxonomy tax, String id) {
        Taxon node = tax.lookupId(id);
        if (node == null) {
            node = new Taxon(tax, null);
            tax.initId(node, id);
        }
        return node;
    }

	// Populate fields of a Taxon object from fields of row of taxonomy file
	// parts = fields from row of dump file
	void initTaxon(Taxon node, String name, String rankname, 
                   String flags, String info, String source, String sourceid) {
        if (name != null)
            node.setName(name);

        if (flags.length() > 0)
            Flag.parseFlags(flags, node);

        if (rankname.length() == 0 || rankname.startsWith("no rank") ||
            rankname.equals("terminal") || rankname.equals("samples"))
            node.rank = Rank.NO_RANK;
        else {
            Rank rank = Rank.getRank(rankname);
            if (rank == null) {
                System.err.println("** Unrecognized rank: " + rankname +
                                   " for node " + node.id);
                node.rank = Rank.NO_RANK;
            } else if (rank == Rank.GENUS_RANK && name.endsWith("ae")) {
                node.markEvent("rank=genus, but name does not look like a genus");
                // System.out.format("* Does not look like a %s: %s\n", rankname, name);
                // do not set rank.  E.g. NCBI Dichelesthiidae, Pontosphaeraceae,
                // GBIF Calycanthaceae, Chimaeridae, Tettigoniidae, Astropectinidae
            } else if (rank == Rank.FAMILY_RANK && !name.endsWith("ae")) {
                node.markEvent("rank=family, but name does not look like a family");
                // System.out.format("* Does not look like a %s: %s\n", rankname, name);
                // NCBI Labyrithula, Sporonauta, GBIF Leptodactyla, etc.
            } else
                node.rank = rank;
        }

        if (info != null && info.length() > 0)
            node.setSourceIds(info);

        if (source.length() > 0 && sourceid.length() > 0)
            node.addSourceId(new QualifiedId(source, sourceid));
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
            else
                System.err.format("** Prunedp taxon in taxonomy: %s\n", node);
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
		out.print((node.rank == Rank.NO_RANK ?
				   (node.children == null ?
					"no rank - terminal" :
					"no rank") :
				   node.rank.name) + sep);

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
					System.err.println("** null in children list!? " + node);
				else
					dumpNode(child, out, false, sep);
			}
	}

    // load forwarding pointers

	void loadForwards(String filename) throws IOException {
		BufferedReader fr;
		try {
			fr = Taxonomy.fileReader(filename);
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
                Taxon target = tax.lookupId(truth);
                if (target != null && tax.lookupId(alias) == null) {
                    tax.addId(target, alias);
                    ++count;
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
			fr = Taxonomy.fileReader(filename);
		} catch (java.io.FileNotFoundException e) {
			fr = null;
		}
		if (fr != null) {
			// BufferedReader br = new BufferedReader(fr);
			BufferedReader br = fr;
			int count = 0;
			int zcount = 0;
			String str;
			int id_column = 0;
			int name_column = 1;
			int type_column = Integer.MAX_VALUE;
			int info_column = Integer.MAX_VALUE;
            int sid_column = Integer.MAX_VALUE;
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
				// uid | name | type | source |
				// 36602	|	Sorbus alnifolia	|	synonym	|	|	
				if (parts.length >= 2) {
					if (row++ == 0) {
						Map<String, Integer> headerx = new HashMap<String, Integer>();
						for (int i = 0; i < parts.length; ++i)
							headerx.put(parts[i], i);
						Integer o2 = headerx.get("uid");
						if (o2 == null) o2 = headerx.get("id");
						if (o2 != null) {
							id_column = o2;
							Integer o1 = headerx.get("name");
							if (o1 != null) name_column = o1;
							Integer o3 = headerx.get("type");
							if (o3 != null) type_column = o3;
							Integer o4 = headerx.get("sourceinfo");
							if (o4 != null) info_column = o4;
							Integer o5 = headerx.get("sid");
							if (o5 != null) sid_column = o5;
							continue;
						}
					}
					String id = parts[id_column];
					String name = parts[name_column];
					String type = (type_column < parts.length ?
								   parts[type_column] :
								   "synonym");
                    String sourceinfo = (info_column < parts.length ?
                                         parts[info_column] :
                                         "");
                    String sid = (sid_column < parts.length ?
                                  parts[sid_column] :
                                  null);
					Taxon node = tax.lookupId(id);
					if (node == null) {
						if (false && ++losers < 10)
							System.err.println("** Identifier " + id + " unrecognized for synonym " + name);
						else if (losers == 10)
							System.err.println("...");
						continue;
					}

				    // Synonym types from NCBI:
                    // synonym
				    // equivalent name  - usually misspelling or spelling variant
				    // misspelling
				    // authority	 - always extends scientific name
				    // type material	 - bacterial strain as type for prokaryotic species ??
				    // common name
				    // genbank common name
				    // blast name   - 247 of them - a kind of common name
				    // in-part (e.g. Bacteria in-part: Monera)
				    // includes (what polarity?)

                    if (type.equals("type material")) // NCBI
                        continue;
                    else if (type.equals("authority")) // NCBI
                        continue;
                    else if (type.endsWith("common name")) { // NCBI
                        if (!type.equals("common name")) // NCBI
                            continue;
                    } else if (type.equals("blast name")) // NCBI
                        continue;
                    else if (type.equals("in-part")) // NCBI
                        continue;
                    else if (type.equals("valid")) // IRMNG - redundant - ?
                        continue;
                    else if (type.equals("") || type.equals("None"))
                        type = "synonym";

                    Node syn = node.addSynonym(name, type, sid);
                    if (syn != null) {
                        syn.setSourceIds(sourceinfo);
                        if (sid != null)
                            syn.setId(sid);
                        if (syn != node) {
                            ++count;
                        } else
                            ++zcount;
                    }
				}
			}
			br.close();
            System.out.format("| %s synonym rows, %s synonyms, %s patched\n", row-1, count, zcount);
		}
	}


	public void dumpSynonyms(String filename, String sep) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		out.format("name\t|\tuid\t|\ttype\t|\tuniqname\t|\tsourceinfo\t|\t\n");
		String format = "%s\t|\t%s\t|\t%s\t|\t%s\t|\t%s\t|\t\n";
        for (Taxon taxon : tax.taxa()) { // deterministic order
            for (Synonym syn : taxon.getSynonyms()) {
                if (taxon.prunedp) {
                    System.err.format("** Prunedp taxon for synonym: %s %s\n", syn, taxon);
                    continue;
                }
                if (taxon.id == null) {
                    // E.g. Populus tremuloides
                    if (!taxon.isRoot()) {
                        System.err.format("** Synonym for node with no id: %s %s %s\n",
                                          syn.name, taxon, taxon.parent);
                        //taxon.show();
                    }
                } else {
                    out.format(format,
                               syn.name,
                               taxon.id,
                               syn.type,
                               syn.uniqueName(),
                               syn.getSourceIdsString());
                }
            }
        }
		out.close();
	}

    void dumpForwards(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
        out.format("id\treplacement\n");
        int count = 0;
        for (String id : tax.allIds()) {
            Node node = tax.getNodeById(id);
            if (node != null && !node.isPruned()) {
                if (!node.id.equals(id)) {
                    out.format("%s\t%s\n", id, node.id);
                    ++count;
                }
            }
        }
        System.out.format("| %s id aliases\n", count);
        out.close();
    }

	void dumpHidden(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		int count = 0;
		for (Taxon node : tax.taxa()) {
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

}
