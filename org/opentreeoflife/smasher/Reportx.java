
// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same / notSame list as input.


package org.opentreeoflife.smasher;

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
		for (String name : tax.nameIndex.keySet()) {
			Collection<Taxon> taxa = tax.nameIndex.get(name);
			if (taxa.size() <= 1) continue;
			if (name.startsWith("uncultured")) continue;
			for (Taxon taxon1 : taxa) {
				if (taxon1.isHidden()) continue;
				for (Taxon taxon2 : taxa) {
					if (taxon2.isHidden()) continue;
					if (taxon1.id.compareTo(taxon2.id) < 0) {
						if (taxon1.parent == taxon2.parent) continue;
						Taxon div[] = tax.divergence(taxon1, taxon2);
						// Skip sibling homonyms
						if (div != null) {
							String type;
							if (div[0] == taxon1 || div[1] == taxon2)
								type = "d-lineage"; // bad
							else if (taxon1.count() > 2 || taxon2.count() > 2)
								type = "c-internal"; // bad
							else if (!taxon1.name.equals(name) || !taxon2.name.equals(name))
								type = "b-tip-synonym";
							else
								type = "a-tip-true";
							Taxon t1, t2, x1, x2;
							if (div[0].count() >= div[1].count()) {
								x1 = taxon1; x2 = taxon2; t1 = div[0]; t2 = div[1];
							} else {
								x1 = taxon2; x2 = taxon1; t1 = div[1]; t2 = div[0];
							}
							out.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n", type, name,
									   getSourceName(x1), popularize(t1, x1).name, t1.count(),
									   getSourceName(x2), popularize(t2, x2).name, t2.count());
						}
					}
				}
			}
		}
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
			return node.taxonomy.tag;
	}
}
