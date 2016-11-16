package org.opentreeoflife.taxa;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.QualifiedId;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.io.Writer;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;

public class HomonymReport {

    public static void homonymReport(Taxonomy tax, String filename) throws IOException {
        Writer writer = openw(filename);
        CSVWriter cwriter = new CSVWriter(writer);
        cwriter.writeNext(new String[]{
                "name",
                "source 1",
                "size 1",
                "ancestor 1",
                "source 2",
                "size 2",
                "ancestor 2",
                "mrca name",
                "mrca size",
                "divided"});
        // for each name that's a homonym...
        //  for each taxon named by that name...
        //   for every *other* taxon named by that name...
        //    report name, MRCA, and size of MRCA of the two
        //     (& maybe subtract sizes of the two taxa)
        for (String name : tax.allNames())
			homonymReport(tax, name, cwriter);
        writer.close();
	}

    public static void homonymReport(Taxonomy tax, String name, CSVWriter cwriter) throws IOException {
        List<Node> nodes = tax.lookup(name);
        if (nodes.size() > 1) {
            for (Node n1node : nodes) {
                Taxon n1 = n1node.taxon();
                for (Node n2node : nodes) {
                    Taxon n2 = n2node.taxon();
                    if (n1 != n2
                        && Taxonomy.compareTaxa(n1, n2) < 0
                        && n1.name.equals(name)
                        && n2.name.equals(name)
                        && (n1.children == null || n2.children == null)
                        ) {
                        String sib1name = "", sib2name = "";
                        Taxon[] sibs = n1.divergence(n2);
                        Taxon mrca = null;
                        if (sibs != null) {
                            sib1name = sibs[0].name;
                            sib2name = sibs[1].name;
                            mrca = sibs[0].parent;
                        } else if (n1.parent != null && n1.parent == n2.parent)
                            mrca = n1.parent;
                        cwriter.writeNext(new String[]{
                                name,
                                putativeSourceRef(n1),
                                Integer.toString(n1.count()),
                                sib1name,
                                putativeSourceRef(n2),
                                Integer.toString(n2.count()),
                                sib2name,
                                mrca == null ? "" : mrca.name,
                                mrca == null ? "" : Integer.toString(mrca.count()),
                                (disjointDivisions(n1, n2) ?
                                 "1" :
                                 "0")});
                        // Only report one homonym pair, not n^2/2
                        break;
                    }
                }
            }
        }
    }

    static String putativeSourceRef(Taxon n) {
        if (n != null) {
            QualifiedId q = n.putativeSourceRef();
            if (q != null)
                return q.toString();
            else
                return "";
        } else
            return "";
    }

    static boolean disjointDivisions(Taxon n1, Taxon n2) {
        Taxon d1 = n1.getDivision(), d2 = n2.getDivision();
        if (d1 == d2) return false;
        if (d1 == null || d2 == null) return false;
        Taxon[] sibs = d1.divergence(d2);
        if (sibs == null) return false;
        return true;
    }

	// Plot histogram of homonyms as a function of mrca size

    public static void homonymDensityReport(Taxonomy tax, String filename) throws IOException {
        Writer writer = openw(filename);
        CSVWriter cwriter = new CSVWriter(writer);
		int count = tax.count();
		int nbuckets = 100;
		// Taxon sizes range from 1 to count, inclusive.
		// Need to compute ceiling here.
		int taxaPerBucket = (count + nbuckets - 1) / nbuckets;
		int[] vcounts = new int[nbuckets]; // visible
		int[] tcounts = new int[nbuckets]; // tips
		int[] primary = new int[nbuckets]; // homonyms not through synonym
		Taxon[] samples = new Taxon[nbuckets];
		Taxon[] divisions = new Taxon[nbuckets];

		for (Taxon node : tax.taxa()) {
			boolean tipp = ((node.rank == null) ?
							(node.children == null && !node.name.startsWith("uncultured")) :
							(node.rank.equals("species")));
			if (node.isHidden()) continue;
			List<Node> nodes = tax.lookup(node.name);
			for (Node homnode : nodes) {
                Taxon hom = homnode.taxon();
				if (!hom.isHidden() &&
					Taxonomy.compareTaxa(node, hom) < 0) {
					Taxon mrca = node.mrca(hom);
					Taxon div[] = node.divergence(hom);
					if (div != null && div[0].parent != null) {
						int c1 = div[0].count(), c2 = div[1].count();
						int score = (c1 < c2 ? c1 : c2);  //min
						int i = (score-1) / taxaPerBucket;
						++vcounts[i];
						if (hom.name.equals(node.name))
							++primary[i];
						if (tipp)
							++tcounts[i];
						if (tipp || samples[i] == null) {
							samples[i] = node;
							if (c1 < c2)
								divisions[i] = div[1];
							else
								divisions[i] = div[0];
						}
					}
				}
			}
		}
		for (int i = 0; i < nbuckets/2; ++i)
			cwriter.writeNext(new String[]{
						  Integer.toString(i*taxaPerBucket),
						  Integer.toString(primary[i]),
						  Integer.toString(vcounts[i]),
						  Integer.toString(tcounts[i]),
						  (samples[i] == null ? "" : samples[i].name),
						  (divisions[i] == null ? "" : divisions[i].name)});
        writer.close();
	}

	// Rank homonym pairs by how easy it is to decide
    /// (tips/species only)

    public static void homonymUncertaintyReport(Taxonomy tax, String filename) throws IOException {
        Writer writer = openw(filename);
        CSVWriter cwriter = new CSVWriter(writer);

		final List<Record> records = new ArrayList<Record>();
		for (Taxon node : tax.taxa()) {
			boolean tipp = ((node.rank == null) ?
							(node.children == null && !node.name.startsWith("uncultured")) :
							(node.rank.equals("species")));
			if (!tipp || node.isHidden()) continue;
			List<Node> nodes = tax.lookup(node.name);
			for (Node homnode : nodes) {
                Taxon hom = homnode.taxon();
				if (!hom.isHidden() &&
					Taxonomy.compareTaxa(node, hom) < 0) {
					Taxon div[] = node.divergence(hom);
					if (div != null && div[0].parent != null) {
						Taxon mrca = div[0].parent;
						int high = mrca.count();
						Taxon loser;
						int low;
						int c1 = div[0].count(), c2 = div[1].count();
						if (c1 < c2) { loser = div[0]; low = c1; }
						else   	 	 { loser = div[1]; low = c2; }
						records.add(new Record(node.name, low, high, loser, mrca));
					}
				}
			}
		}
		System.out.format("%s homonym pairs\n", records.size());

		Collections.sort(records, new Comparator<Record>() {
				public int compare(Record r1, Record r2) {
					// Sort highest uncertainty to lowest.
					// Smaller clade means more certain of match, so sort last.
					if (r2.low != r1.low)
						return r2.low - r1.low;
					else
						return r1.high - r2.high;
				}
			});
		{
			int i = 0;
			for (Record r : records) r.u1 = i++;
		}
		Collections.sort(records, new Comparator<Record>() {
				public int compare(Record r1, Record r2) {
					// Sort highest uncertainty to lowest.
					// Larger smallest clade means more certain of mismatch.
					if (r1.high != r2.high)
						return r1.high - r2.high;
					else
						return r2.low - r1.low;
				}
			});
		{
			int i = 0;
			for (Record r : records) r.u2 = i++;
		}

		Collections.sort(records, new Comparator<Record>() {
				public int compare(Record r1, Record r2) {
					// Combine uncertainty ranks.
					return (r1.u1 + r1.u2) - (r2.u1 + r2.u2);
				}
			});
		for (Record r : records) r.writeNext(cwriter);
		writer.close();
	}

	static class Record {
		String name; int low, high, u1, u2; Taxon loser, mrca;
		Record(String name, int low, int high, Taxon loser, Taxon mrca) {
			this.name = name; this.low = low; this.high = high; this.loser = loser; this.mrca = mrca;
			this.u1 = this.u2 = 50;
		}
		public void writeNext(CSVWriter cwriter) throws IOException {
            cwriter.writeNext(new String[]{
                    Integer.toString(this.low),
                    Integer.toString(this.high),
                    this.name,
                    this.loser.name,
                    this.mrca.name});
		}
	}

	static Writer openw(String filename) throws IOException {
		Writer out;
		if (filename.equals("-")) {
			out = new java.io.OutputStreamWriter(System.out);
			System.out.println("Writing to standard output");
		} else {

            out = new PrintWriter(new BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(filename),
                                                                                    "UTF-8")));
			System.out.println("Writing " + filename);
		}
		return out;
	}

}
