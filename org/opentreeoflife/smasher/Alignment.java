/*
  The goal here is to be able to unify source nodes with union nodes.

  The unification is done by Taxon.unifyWith and has the effect of
  setting the 'mapped' field of the node.

  */


// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same / notSame list as input.


package org.opentreeoflife.smasher;

public abstract class Alignment {

    abstract Answer answer(Taxon node);

    Taxon map(Taxon node) {
        Answer a = this.answer(node);
        if (a == null) return null;
        else if (a.isYes()) return a.target;
        else return null;
    }

    abstract void cacheInSourceNodes();

	// What was the fate of each of the nodes in this source taxonomy?

	static void alignmentReport(SourceTaxonomy source, UnionTaxonomy union) {

		if (Taxon.windyp) {

			int total = 0;
			int nonamematch = 0;
			int prevented = 0;
			int corroborated = 0;

			// Could do a breakdown of matches and nonmatches by reason

			for (Taxon node : source) {
				++total;
				if (union.lookup(node.name) == null)
					++nonamematch;
				else if (node.mapped == null)
					++prevented;
				else
					++corroborated;
			}

			System.out.println("| Of " + total + " nodes in " + source.getTag() + ": " +
							   (total-nonamematch) + " with name in common, of which " + 
							   corroborated + " matched with existing, " + 
							   prevented + " blocked");
		}
	}

}
