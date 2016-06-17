/*

  This class is not used for taxonomy synthesis any more; synthesis is
  now coordinated by jython scripts.

  Open Tree Reference Taxonomy (OTT) taxonomy combiner.

  In jython, say:
	 from org.opentreeoflife.smasher import Smasher

*/

package org.opentreeoflife.smasher;

import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.TsvEdits;

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
/*
import org.python.util.PythonInterpreter;
import org.python.util.InteractiveInterpreter;
import org.python.util.InteractiveConsole;
import org.python.util.ReadlineConsole;
*/

public class Smasher {

    private static String defaultIdspace = "ott";

	public static void main(String argv[]) throws Exception {

		if (argv.length > 0) {

			Taxonomy tax = null;
			boolean anyfile = false;
			UnionTaxonomy.windyp = false;
			String outprefix = null;

			for (int i = 0; i < argv.length; ++i) {

				if (argv[i].startsWith("--")) {

					if (argv[i].equals("--version"))
						System.out.println("This is smasher, version 000.00.0.000");

					if (argv[i].equals("--jython"))
						jython(argv[++i]);

					else if (argv[i].equals("--diff")) {
						String name1 = argv[++i];
						String name2 = argv[++i];
						String filename = argv[++i];
						Taxonomy t1 = Taxonomy.getTaxonomy(name1, null);
						Taxonomy t2 = Taxonomy.getTaxonomy(name2, null);
						t2.dumpDifferences(t1, filename);
					}

					else if (argv[i].equals("--deforest")) {
						tax.deforestate();
					}

					else if (argv[i].equals("--chop")) {
						tax = tax.chop(300, 3000);
					}

					else if (argv[i].equals("--ids")) {
						// To smush or not to smush?
						UnionTaxonomy union = promote(tax); tax = union;
						SourceTaxonomy idsource = Taxonomy.getTaxonomy(argv[++i], defaultIdspace);
						union.assignIds(idsource, "additions");
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
						UnionTaxonomy union = promote(tax); tax = union;
						TsvEdits.edit(union, dirname);
					}

					//-----
					else if (argv[i].equals("--out")) {
						outprefix = argv[++i];	// see --aux
						tax.dump(outprefix, "\t|\t");
					}

                    // what to call this ??
					else if (argv[i].equals("--outt")) {
						outprefix = argv[++i];	// see --aux
						tax.dump(outprefix, "\t");
					}

					else if (argv[i].equals("--test"))
						test();

					else if (argv[i].equals("--start"))
						tax = Taxonomy.getTaxonomy(argv[++i], null);

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
						tax = Taxonomy.getTaxonomy(argv[i], null);
					else {
						UnionTaxonomy union = promote(tax);
						SourceTaxonomy source = Taxonomy.getTaxonomy(argv[i], null);
						if (source != null)
							union.align(source);
						tax = union;
					}
				}
			}
		} else
			jython("-");
	}

    static UnionTaxonomy promote(Taxonomy tax) {
        if (tax instanceof SourceTaxonomy) {
            UnionTaxonomy union = new UnionTaxonomy(defaultIdspace);
            union.align((SourceTaxonomy)tax);
            return union;
        } else if (tax instanceof UnionTaxonomy)
            return (UnionTaxonomy)tax;
        else
            throw new RuntimeException(String.format("promotion error: %s", tax));
    }

	static void jython(String source) {
    /*
     * This worked with jython 2.5.3, but is broken in jython 2.7.
		if (source.equals("-")) {
			System.out.format("Consider doing:\nfrom org.opentreeoflife.taxa import Taxonomy\n");
			org.python.util.InteractiveConsole j = new org.python.util.JLineConsole();
			j.interact();
		} else {
			PythonInterpreter j = new PythonInterpreter();
			j.execfile(source);
		}
    */
	}

	static void test() throws IOException {
		Taxonomy tax = Taxonomy.getTaxonomy("(a,b,(e,f)c)d", "z");
		for (Taxon node : tax.taxa())
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
