
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

public class Alignment {

	Map<Taxon, Taxon> scaffold = new HashMap<Taxon, Taxon>();
	Map<Taxon, Taxon> mapping = new HashMap<Taxon, Taxon>();
	Map<Taxon, String> failures = new HashMap<Taxon, String>();

	// Invert the node->name map.
	Map<Taxon, Collection<String>> namesMap;

	// Need to know which union taxa are targets of mrcas (range of
	// scaffold or mapping)
	// Need to compute and store # of union tips under each mrca
	// Need to do this in both passes
	// scaffoldCounts, mappingCounts

	Map<Taxon, Collection<Taxon>> candidatesMap =
		new HashMap<Taxon, Collection<Taxon>>();

	Taxonomy source, union;

	Alignment(Taxonomy source, Taxonomy union) {
		this.source = source;
		this.union = union;
		namesMap = namesMap(source);

		// In pass 1, we identify tips, look for unequivocal matches, and
		// compute tentative MRCAs (the 'scaffold').
        for (Taxon n1 : source.roots)
			align(n1, scaffold, false);

		// In pass 2, we align as many tips as possible, using
		// scaffold MRCA sizes to distinguish homonyms from non-homonyms,
		// and compute real MRCAs.
        for (Taxon n1 : source.roots)
			align(n1, mapping, true);

		// Finally, we match
	}

	// Compute the inverse of the node->name map.
	static Map<Taxon, Collection<String>> namesMap(Taxonomy tax) {
		Map<Taxon, Collection<String>> namesMap = new HashMap<Taxon, Collection<String>>();
		for (String name : tax.nameIndex.keySet())
			for (Taxon node : tax.nameIndex.get(name)) {
				Collection<String> names = namesMap.get(node);  // of this node
				if (names == null) {
					names = new ArrayList(1);
					namesMap.put(node, names);
				}
				names.add(name);
			}
		return namesMap;
	}

	// Returns the smallest taxon in the union that is likely to
	// include the source taxon.

	public Taxon align(Taxon node, Map<Taxon, Taxon> mapping, boolean pass1p) {

		Taxon mrca = null;
		if (node.children != null)
			for (Taxon child : node.children) {
				Taxon target = align(child, mapping, pass1p);
				if (target != null) {
					if (mrca == null)
						mrca = target;
					else
						mrca = mrca.mrca(target);
				}
			}
		if (mrca != null) {
			// Internal node.  Mrcas might be higher on the second time around.
			mapping.put(node, mrca);
			return mrca;
		} else
			// Tip.
			// ***** WRONG, please set correct args here
			return alignTip(node, false, pass1p, PASS1_THRESHOLD);
	}

	// Align given tip to union.

	public Taxon alignTip(Taxon node, boolean lineagep, boolean mrcap, int threshold) {
		Taxon have = mapping.get(node);    // left over from pass 1
		if (have != null) return have;
		Pain closest = closest(node, lineagep, mrcap);

		// Maybe: put the Pains into a list, sort the list, show 50
		// just above threshold & 50 just below threshold

		if (closest != null && closest.distance < threshold) {
			mapping.put(node, closest.target);
			return closest.target;
		} else
			return null;
	}

	static final int FAR = Integer.MAX_VALUE / 8;

	// Compute distance between nodes just looking at their names.
	int nameDistance(Taxon node, Taxon unode /*, Map<Taxon, Collection<String>> namesMap */) {
		Pain closest = closest(node, false, false);
		if (closest != null) ;  // *****

		int distance = FAR;
		for (String name : namesMap.get(node)) {
			Collection<Taxon> unodes = unode.taxonomy.lookup(name);
			int d = 1;
			if (unodes != null) { //shouldn't happen
				if (!node.name.equals(name)) d += 2;
				for (Taxon unod : unodes)
					if (!unod.name.equals(name)) d += 4;
			}
			if (d < distance) distance = d;
		}
		return distance;
	}

	// Returns best tip match...
	// Threshold should be 9, for pass 1, or 
	Pain closest(Taxon node, boolean lineagep, boolean mrcap
				 /*, Map<Taxon, Collection<String>> namesMap*/) {
		Collection<String> names = namesMap.get(node);
		Taxon best = null, next = null;
		int dbest = FAR, dnext = FAR;
		for (String name : names) {
			// *** ? check
			Collection<Taxon> unodes = node.taxonomy.lookup(name);
			boolean synonymp = !node.name.equals(name);
			if (unodes != null)   // shouldn't happen
				for (Taxon unode : unodes) {
					int d;
					if (lineagep && checkLineage(node, unode))
						d = 0;
					else
						d = (PASS1_THRESHOLD + 1);
					if (synonymp) d += 1;
					if (!unode.name.equals(name)) d += 2;
					// TBD: compare ids, ranks
					if (mrcap) {
						// Pass 2.  Scan to deepest ancestor node that has a mrca
						Taxon found = null;
						Taxon hunt;
						for (hunt = node; hunt != null; hunt = hunt.parent) {
							Taxon probe = scaffold.get(hunt);
							if (probe != null) {
								found = probe;
								break;
							}
						}
						if (found != null) {
							Taxon mrca = found.mrca(unode);
							if (mrca != null) d += mrca.count();
						}
					}
					if (d < dbest) {
						if (unode == best)
							dbest = d;
						else {
							next = best; dnext = dbest;
							best = unode; dbest = d;
						}
					}
				}
		}
		if (best == null)
			return null;
		// **** 'mrcap' is wrong in the following
		if (next != null && (dnext == dbest) && mrcap) {
			System.err.format("** Ambiguity: %s, %s at %s, %s at %s\n", node, best, dbest, next, dnext);
			// enter node into a 'blocked' table
			return null;
		} else
			return new Pain(best, dbest);
	}

	class Pain {
		Taxon target; int distance;
		Pain(Taxon target, int distance) {
			this.target = target; this.distance = distance;
		}
	}

	static final int PASS1_THRESHOLD = 9;
	static final int PASS2_THRESHOLD = 30000;

	// Are these two references coreferences?

	// this duplicates code in Criterion.lineage - clean up later

	boolean checkLineage(Taxon x, Taxon y) {
		Taxon y0 = y.scan(x.taxonomy);	  // ignore names not known in both taxonomies
		Taxon x0 = x.scan(y.taxonomy);
		if (x0 == null && y0 == null)
			return true;
		if (x0 == null || y0 == null)
			return false;
		return (online(x0, y0) || online(y0, x0));
	}

	// Is a node with the given name on the given node's lineage?
	boolean online(Taxon n1, Taxon n2) {
		for (Taxon node = n1; node != null; node = node.parent)
			if (nameDistance(n1, n2) < 100) return true;
		return false;
	}

	// Find most leafward node on node's lineage having a shared name.
	Taxon scan(Taxon node, Taxonomy other) {
		Taxon up = node.parent;

		// Skip over genus - cf. informative() method
		// Without the following, we get ambiguities when the taxon is a species
		while (up != null && up.name != null && node.name.startsWith(up.name))
			up = up.parent;

		// Look at parent, grandparent, etc. until an ancestor is
		// found whose name is in both taxonomies
		// Not checking synonyms of up - should we be?
		// closest(up, ??)
		while (up != null && other.lookup(up.name) != null)
			up = up.parent;

		return up;
	}

	// -------------------- REPORTS --------------------

	public void report() throws IOException {
		this.report(System.out);
	}
	public void report(String filename) throws IOException {
		PrintStream out = Taxonomy.openw(filename);
		report(out);
		out.close();
	}
	public void report(PrintStream out) throws IOException {
		out.format("Source taxonomy size: %s\n", mapping.size() + failures.size());
		out.format("Mappings: %s\n", mapping.size());
		out.format("Failures: %s\n", failures.size());
		int countdown = 20;
		for (Taxon node : failures.keySet()) {
			if (--countdown < 0) break;
			out.format("%s\t%s\n", node.name, failures.get(node));
		}
	}

	// Plot histogram of homonyms as a function of mrca size

    public static void homonymDensityReport(Taxonomy tax, String filename) throws IOException {
        PrintStream stream = Taxonomy.openw(filename);
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

		for (Taxon node : tax) {
			boolean tipp = ((node.rank == null) ?
							(node.children == null && !node.name.startsWith("uncultured")) :
							(node.rank.equals("species")));
			if (node.isHidden()) continue;
			List<Taxon> nodes = tax.nameIndex.get(node.name);
			for (Taxon hom : nodes) {
				if (!hom.isHidden() &&
					Taxonomy.compareTaxa(node, hom) < 0) {
					Taxon mrca = node.mrca(hom);
					Taxon div[] = Taxonomy.divergence(node, hom);
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
			stream.format("%s\t%s\t%s\t%s\t%s\t%s\n",
						  i*taxaPerBucket,
						  primary[i],
						  vcounts[i],
						  tcounts[i],
						  (samples[i] == null ? "" : samples[i].name),
						  (divisions[i] == null ? "" : divisions[i].name));
        stream.close();
	}

	// Rank homonym pairs by how easy it is to decide

    public static void homonymUncertaintyReport(Taxonomy tax, String filename) throws IOException {
        PrintStream stream = Taxonomy.openw(filename);

		final List<Record> records = new ArrayList<Record>();
		for (Taxon node : tax) {
			boolean tipp = ((node.rank == null) ?
							(node.children == null && !node.name.startsWith("uncultured")) :
							(node.rank.equals("species")));
			if (!tipp || node.isHidden()) continue;
			List<Taxon> nodes = tax.nameIndex.get(node.name);
			for (Taxon hom : nodes) {
				if (!hom.isHidden() &&
					Taxonomy.compareTaxa(node, hom) < 0) {
					Taxon div[] = Taxonomy.divergence(node, hom);
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
		for (Record r : records) stream.println(r);
		stream.close();
	}

	static class Record {
		String name; int low, high, u1, u2; Taxon loser, mrca;
		Record(String name, int low, int high, Taxon loser, Taxon mrca) {
			this.name = name; this.low = low; this.high = high; this.loser = loser; this.mrca = mrca;
			this.u1 = this.u2 = 50;
		}
		public String toString() {
			return String.format("%s\t%s\t%s\t%s\t%s",
								 this.low,
								 this.high,
								 this.name,
								 this.loser.name,
								 this.mrca.name);
		}
	}

    public static void homonymReport(Taxonomy tax, String filename) throws IOException {
        PrintStream stream = Taxonomy.openw(filename);
        // for each name that's a homonym...
        //  for each taxon named by that name...
        //   for every *other* taxon named by that name...
        //    report name, MRCA, and size of MRCA of the two
        //     (& maybe subtract sizes of the two taxa)
        for (Taxon n1 : tax.roots)
			homonymReport(tax, n1, stream);
        stream.close();
	}

	// Returns true if at least one coreference was detected at or below this node

    public static boolean homonymReport(Taxonomy tax, Taxon n1, PrintStream stream) throws IOException {
		boolean anycoref = false;
		if (n1.children != null)
			for (Taxon c : n1.children)
				anycoref = anycoref || homonymReport(tax, c, stream);
		if (!anycoref) {
			List<Taxon> nodes = tax.nameIndex.get(n1.name);
			if (nodes.size() > 1)
				for (Taxon n2 : nodes)
					if (Taxonomy.compareTaxa(n1, n2) < 0 /*&& corefer(n1, n2)*/) {
						anycoref = true;
						String divinfo = "\t\t\t", mrcainfo = "\t";
						Taxon[] div = Taxonomy.divergence(n1, n2);
						if (div != null) {
							divinfo = String.format("%s\t%s\t%s\t%s", 
													n1.count(), div[0].name,
													n2.count(), div[1].name);
							Taxon mrca = div[0].parent;
							if (mrca != null)
								mrcainfo = String.format("%s\t%s",
														 mrca.count(), mrca.name);
						}
						stream.format("%s\t%s\t%s\n",
									  n1.name,
									  divinfo,
									  mrcainfo);
					}
		}
		return anycoref;
	}
}
	/*

	  Some transitional cases in OTT 2.8 - for determining threshold.
	  (looks like it should be somewhere above 5,000 and below 400,000)
	  Column 1: size of smaller of the two 'divisions' (children of MRCA)
	  Column 2: size of MRCA
	  Column 3: name of the homonymic taxa
	  Column 4: name of smaller of the two 'divisions' (children of MRCA)
	  Column 5: name of MRCA
	  
	  If the threshold is too high, we get false positive matches,
	  which means 'lumping'.
	  If too low, false negatives or 'splitting', which will usually
	  lead to a 'hidden' taxon.

1       22037   Bacillus cereus Bacillus cereus Bacillus
3125    26792   Talaroneis      Coscinodiscophytina     Bacillariophyta
4118    42935   Paenibacillus timonensis        Paenibacillaceae        Bacillales
9799    52771   Enterococcus dispar     Lactobacillales Bacilli
19669   150418  Rhodobacter sphaeroides Betaproteobacteria      Proteobacteria
41596   150418  Enterobacter ludwigii   Alphaproteobacteria     Proteobacteria
896     358928  43F-1404R       Acidobacteria sup.      Bacteria
896     358928  marine group    Acidobacteria sup.      Bacteria
896     358928  marine group    Acidobacteria sup.      Bacteria
54695   358928  [Brevibacterium] halotolerans   Actinobacteria sup.     Bacteria
150418  358928  Proteobacteria  Proteobacteria  Bacteria
216209  1713683 Prionosoma podopioides  Lophotrochozoa  Protostomia
216209  1713683 Bullacta exarata        Lophotrochozoa  Protostomia
361816  2298053 Nidularia pulvinata     Nucletmycea     Opisthokonta


	*/