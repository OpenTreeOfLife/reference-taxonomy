/*

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

  In jython, say:
     from org.opentreeoflife.smasher import Smasher

*/

package org.opentreeoflife.smasher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.PrintStream;
import java.io.File;
import org.python.util.InteractiveConsole;

public class Smasher {

    static InteractiveConsole j = null;

	public static void main(String argv[]) throws Exception {

		Taxonomy.initRanks();

		if (argv.length > 0) {

			Taxonomy tax = null;
			boolean anyfile = false;
			Node.windyp = false;
			String outprefix = null;

			for (int i = 0; i < argv.length; ++i) {

				if (argv[i].startsWith("--")) {

					if (argv[i].equals("--version"))
						System.out.println("This is smasher, version 000.00.0.000");

					if (argv[i].equals("--jython"))
                        jython(argv[++i]);

					else if (argv[i].equals("--jscheme")) {
						String[] jargs = {};
						jscheme.REPL.main(jargs);
					}

					else if (argv[i].equals("--deforest")) {
						tax.deforestate();
					}

					else if (argv[i].equals("--chop")) {
						tax = tax.chop(300, 3000);
					}

					else if (argv[i].equals("--ids")) {
						// To smush or not to smush?
						UnionTaxonomy union = tax.promote(); tax = union;
						SourceTaxonomy idsource = Taxonomy.getTaxonomy(argv[++i]);
						union.assignIds(idsource);
					}

					else if (argv[i].equals("--aux")) { // preottol
						UnionTaxonomy union = tax.promote(); tax = union;
						SourceTaxonomy auxsource = Taxonomy.getTaxonomy(argv[++i]);
						union.loadAuxIds(auxsource);
                        union.dumpAuxIds(outprefix);
					}

					// Deprecated
					else if (argv[i].equals("--select") || argv[i].equals("--select1")) {
						String name = argv[++i];
						Node root = tax.unique(name);
						if (root != null) {
							tax.analyze();    // otherwise they all show up as 'barren'
							tax.select1(root, argv[++i]);
						}
					}

					else if (argv[i].equals("--select2")) {
						String name = argv[++i];
						tax = tax.select(name);
					}

					else if (argv[i].equals("--sample")) {
						String name = argv[++i];
						int count = Integer.parseInt(argv[++i]);
						tax = tax.sample(name, count);
					}

					else if (argv[i].equals("--edits")) {
						String dirname = argv[++i];
						UnionTaxonomy union = tax.promote(); tax = union;
						union.edit(dirname);
					}

					//-----
					else if (argv[i].equals("--out")) {
						outprefix = argv[++i];  // see --aux
						tax.dump(outprefix);
					}

					else if (argv[i].equals("--test"))
						test();

					else if (argv[i].equals("--start"))
						tax = Taxonomy.getTaxonomy(argv[++i]);

					// Write a .tre (Newick) file
					else if (argv[i].equals("--tre")) {
						String outfile = argv[++i];
						tax.dumpNewick(outfile);
					}

					else if (argv[i].equals("--newick")) {
						System.out.println(" -> " + tax.toNewick());
					}

					// Utility
					else if (argv[i].equals("--join")) {
						String afile = argv[++i];
						String bfile = argv[++i];
						join(afile, bfile);
					}

					else System.err.println("Unrecognized directive: " + argv[i]);
				}

				else {
					if (tax == null) 
						tax = Taxonomy.getTaxonomy(argv[i]);
					else {
						UnionTaxonomy union = tax.promote();
						SourceTaxonomy source = Taxonomy.getTaxonomy(argv[i]);
						if (source != null)
							union.mergeIn(source);
						tax = union;
					}
				}
			}
		} else
            jython("-");
	}

    static void jython(String source) {
        if (j == null) j = new InteractiveConsole();
        System.out.println("Consider doing: from org.opentreeoflife.smasher import Taxonomy");
        // was: String[] jargs = {}; org.python.util.jython.main(jargs);
        if (source.equals("-"))
            j.interact();
        else
            j.execfile(source);
    }

	static void test() {
		Taxonomy tax = SourceTaxonomy.parseNewick("(a,b,(e,f)c)d");
		for (Node node : tax)
			System.out.println(node);
	}

	static void join(String afile, String bfile) throws IOException {
		PrintStream out = System.out;
		Map<String, String[]> a = readTable(afile);
		Map<String, String[]> b = readTable(bfile);
		for (String id : a.keySet()) {
			String[] brow = b.get(id);
			if (brow != null) {
				boolean first = true;
				String[] arow = a.get(id);
				for (int i = 0; i < arow.length; ++i) {
					if (!first) out.print("\t"); first = false;
					out.print(arow[i]);
				}
				for (int j = 1; j < brow.length; ++j) {
					if (!first) out.print("\t"); first = false;
					out.print(brow[j]);
				}
				out.println();
			}
		}
	}

	static Pattern tabPattern = Pattern.compile("\t");

	static Map<String, String[]> readTable(String filename) throws IOException {
		BufferedReader br = Taxonomy.fileReader(filename);
		String str;
		Map<String, String[]> rows = new HashMap<String, String[]>();
		while ((str = br.readLine()) != null) {
			String[] parts = tabPattern.split(str);
			rows.put(parts[0], parts);
		}
		br.close();
		return rows;
	}
}
