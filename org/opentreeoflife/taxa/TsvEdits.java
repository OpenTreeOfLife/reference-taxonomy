package org.opentreeoflife.taxa;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;

public class TsvEdits {

	// ----- ORIGINAL PATCH SYSTEM -----

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
		String rankname = row[2].trim();
		String parentName = row[3].trim();
		String contextName = row[4].trim();
		String sourceInfo = row[5].trim();

		Taxon parent = tax.taxon(parentName, contextName, null, true);

		Taxon existing = tax.taxon(name, contextName, null, false);

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
				node.rank = Rank.getRank(rankname);
				node.setSourceIds(sourceInfo);
				parent.addChild(node, 0); // Not incertae sedis
				node.addFlag(Taxonomy.EDITED);
			}
        } else if (command.equals("synonym")) {
            if (parent != null)
                parent.addSynonym(name, "synonym");
        } else if (existing == null) {
            // Generate diagnostic message
            tax.taxon(name, contextName, null, true);
        } else if (command.equals("flag")) {
            existing.addFlag(Taxonomy.FORCED_VISIBLE);
        } else if (command.equals("incertae_sedis")) {
            existing.addFlag(Taxonomy.INCERTAE_SEDIS);
        } else if (command.equals("prune")) {
            existing.prune("edit/prune");
        } else if (parent == null) {
            ;
		} else if (command.equals("move")) {
            // TBD: CYCLE PREVENTION!
            existing.changeParent(parent, 0);
            existing.addFlag(Taxonomy.EDITED);
        } else if (command.equals("fold")) {
            if (existing.children != null) {
                ArrayList<Taxon> children = new ArrayList<Taxon>(existing.children);
		for (Taxon child: children)
                    child.changeParent(parent, 0);
	    }
            parent.addSynonym(name, "subsumed_by"); //  ????
            existing.prune("edit/fold");
        } else
            System.err.println("** Unrecognized edit command: " + command);
    }

}
