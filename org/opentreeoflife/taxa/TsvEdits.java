package org.opentreeoflife.taxa;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;

public class TsvEdits {

	// ----- PATCH SYSTEM -----

	static Pattern tabPattern = Pattern.compile("\t");

	// Apply a set of edits to the union taxonomy

	public static void edit(Taxonomy tax, String dirname) throws IOException {
		File[] editfiles = new File(dirname).listFiles();
		if (editfiles == null) {
			System.err.println("No edit files in " + dirname);
			return;
		}
		for (File editfile : editfiles)
			if (editfile.isFile() &&
				!editfile.getName().endsWith(".hold") &&
				!editfile.getName().endsWith("~")) {
				applyEdits(tax, editfile);
			}
	}

	// Apply edits from one file
	public static void applyEdits(Taxonomy tax, String editfile) throws IOException {
		applyEdits(tax, new File(editfile));
	}
	public static void applyEdits(Taxonomy tax, File editfile) throws IOException {
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
						applyOneEdit(tax, row);
				}
			}
		}
		br.close();
	}

	//		command	name	rank	parent	context	sourceInfo
	// E.g. add	Acanthotrema frischii	species	Acanthotrema	Fungi	IF:516851

	static void applyOneEdit(Taxonomy tax, String[] row) {
		String command = row[0].trim();
		String name = row[1].trim();
		String rank = row[2].trim();
		String parentName = row[3].trim();
		String contextName = row[4].trim();
		String sourceInfo = row[5].trim();

		List<Taxon> parents = tax.filterByAncestor(parentName, contextName);
		if (parents == null) {
			System.err.format("! Parent name %s missing in context %s (for %s)\n",
                              parentName, contextName, name);
			return;
		}
		if (parents.size() > 1)
			System.err.format("? Ambiguous parent name %s for %s\n", parentName, name);
		Taxon parent = parents.get(0);	  //tax.taxon(parentName, contextName)

		if (!parent.name.equals(parentName))
			System.err.println("! Warning: parent taxon name is a synonym: " + parentName);

		List<Taxon> existings = tax.filterByAncestor(name, contextName);
		Taxon existing = null;
		if (existings != null) {
			if (existings.size() > 1)
				System.err.println("? Ambiguous taxon name: " + name);
			existing = existings.get(0);
		}

		if (command.equals("add")) {
			if (existing != null) {
                if (false) {
                    System.err.println("! (add) Warning: taxon already present: " + name);
                    if (existing.parent != parent)
                        System.err.println("! (add)	 ... with a different parent: " +
                                           existing.parent.name + " not " + parentName);
                }
			} else {
				Taxon node = new Taxon(tax, name);
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
				tax.addSynonym(name, parent, "subsumed_by"); //  ????
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
				tax.addSynonym(name, parent, "synonym");

		} else
			System.err.println("Unrecognized edit command: " + command);
	}

}
