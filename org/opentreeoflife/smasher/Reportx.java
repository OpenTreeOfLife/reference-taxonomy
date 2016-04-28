
// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same / notSame list as input.


package org.opentreeoflife.smasher;

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
import java.io.PrintStream;
import java.io.IOException;

public class Reportx {

	// Precede with
	//   ott = Taxonomy.newTaxonomy()
	//   skel = Taxonomy.getTaxonomy('tax/skel/', 'skel')
	//   ott.absorb(skel)
	//   ott.setSkeleton(skel)
	public static void bogotypes(Taxonomy tax) {
		Map<Taxon, Taxon> bogotypes = new HashMap<Taxon, Taxon>();
		for (Taxon node : tax) {
			Taxon div = node.getDivision();
			if (div != null) {
				Taxon have = bogotypes.get(div);
				if (node.name.equals(div.name)) // exclude uninformative
					continue;
				if (tax.lookup(node.name).size() > 1) // exclude homonyms
					continue;
				if (node.rank == null || !node.rank.equals("species"))
					continue;
				if (have != null) {
					// More soures is better
					int z = ((node.sourceIds == null ? 0 : node.sourceIds.size()) -
							 (have.sourceIds == null ? 0 : have.sourceIds.size()));
					if (z < 0)
						continue; // fewer sources = not as good
					if (z == 0) {
						int z3 = node.name.length() - have.name.length();
						if (z3 >= 0)
							continue;
					}
				}
				System.err.format("Upgrading %s from %s to %s\n", div, have, node);
				bogotypes.put(div, node);
			}
		}
		for (Taxon div : bogotypes.keySet()) {
			if (div == null) System.err.format("can't happen 1 %s\n, div");
			Taxon bogo = bogotypes.get(div);
			if (bogo == null) System.err.format("can't happen 2 %s\n, div");
			System.err.format("%s\t%s\n", div.name, bogo.name);
		}
	}

	public static void report(Taxonomy tax) throws IOException {
		report(tax, System.out);
	}
	public static void report(Taxonomy tax, String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		report(tax, out);
		System.err.println("Wrote " + filename);
		out.close();
	}
	public static void report(Taxonomy tax, PrintStream out) throws IOException {
		int primaryPrimaryCount = 0;
		int primarySynCount = 0;
		int synSynCount = 0;
		int siblingsCount = 0;
		int divisionCount = 0;
		final String formatString = "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n";
		out.format(formatString, "type", "name",
				   "src1", "branch1", "count1",
				   "src2", "branch2", "count2");

		for (String name : tax.allNames()) {
			Collection<Node> nodes = tax.lookup(name);
			if (nodes.size() <= 1) continue;
			if (name.startsWith("uncultured")) continue;
			for (Node node1 : nodes) {
                Taxon taxon1 = node1.taxon();
				boolean primary1 = taxon1.name.equals(name);
				if (taxon1.isHidden()) continue;
				for (Node node2 : nodes) {
                    Taxon taxon2 = node2.taxon();
					if (taxon2.isHidden()) continue;
					if (taxon1.id.compareTo(taxon2.id) < 0) {
						if (taxon1.parent == taxon2.parent) {
							// Skip sibling homonyms
							++siblingsCount;
							continue;
						}

						boolean primary2 = taxon2.name.equals(name);
						boolean ps; // primary + synonym
						if (!primary1 && !primary2) {
							++synSynCount;
							continue;
						} else if (!primary1 || !primary2) {
							++primarySynCount;
							ps = true;
						} else {
							++primaryPrimaryCount;
							ps = false;
						}

						Taxon d1 = taxon1.getDivision(), d2 = taxon2.getDivision();
						if (d1 != null && d2 != null) {
							Taxon dmrca = d1.mrca(d2);
							if (d1 != dmrca && d2 != dmrca) {
								++divisionCount;
								continue;
							}
						}
						Taxon div[] = taxon1.divergence(taxon2);
						if (div != null) {
							int count1 = div[0].count();
							int count2 = div[1].count();
							String type;
							if (div[0] == taxon1 || div[1] == taxon2)
								type = "40lineage"; // bad
							else if (taxon1.count() > 2 || taxon2.count() > 2)
								type = "30internal"; // bad
							else if (!taxon1.name.equals(name) || !taxon2.name.equals(name))
								type = "20tip-synonym";
							else if (count1 + count2 < 10000)
								type = "12tip-duplicate";
							else if (count1 + count2 < 100000)
								type = "10tip-suspect";
							else
								type = "18tip-true";
							Taxon t1, t2, x1, x2; int c1, c2;
							if (div[0].count() >= div[1].count()) {
								x1 = taxon1; x2 = taxon2; t1 = div[0]; t2 = div[1]; c1 = count1; c2 = count2;
							} else {
								x1 = taxon2; x2 = taxon1; t1 = div[1]; t2 = div[0]; c1 = count2; c2 = count1;
							}
							out.format(formatString, type, name,
									   getSourceName(x1), t1.name, c1,
									   getSourceName(x2), t2.name, c2);
							// popularize(t1, x1) popularize(t2, x2)
						}
					}
				}
			}
		}
		System.out.format("Primary/primary pairs: %s\n", primaryPrimaryCount);
		System.out.format("Primary/synonym pairs: %s\n", primarySynCount);
		System.out.format("Synonym/synonym pairs skipped: %s\n", synSynCount);
		System.out.format("Cross-division pairs skipped: %s\n", divisionCount);
		System.out.format("Sibling pairs skipped: %s\n", siblingsCount);
	}

	// Find a popular ancestor of x under t.
	public static Taxon popularize(Taxon t, Taxon x) {
		if (x == t) return t;

		int popularity = -1;
		Taxon p = null;

		// Run y from x rootward to t
		Taxon y = x.parent;
		while (y != null) {
			int pop = (y.sourceIds == null ? 0 : y.sourceIds.size());
			if (pop >= popularity) {
				p = y;
				popularity = pop;
			}
			if (y == t) break;
			y = y.parent;
		}
		if (p == null)			// shouldn't happen
			return t;
		else
			return p;
	}

	static String getSourceName(Taxon node) {
		List<QualifiedId> sourceIds = node.sourceIds;
		if (sourceIds != null)
			return sourceIds.get(0).prefix;
		else
			return node.taxonomy.getTag();
	}
}
