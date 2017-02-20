/*
 * Please don't laugh at the integer bit masks in this file... I'm in
 * the middle of a transition from using bit masks to using an enum,
 * and the process isn't complete yet.
 */

package org.opentreeoflife.taxa;

import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.io.PrintStream;
import java.util.regex.Pattern;

public enum Flag {

    // Emptied containers - these never have children
    WAS_CONTAINER        ("was_container", null, Taxonomy.WAS_CONTAINER), // class incertae sedis, etc.
    INCONSISTENT         ("inconsistent", "inconsistent_inherited", Taxonomy.INCONSISTENT),
    MERGED               ("merged", "merged_inherited", Taxonomy.MERGED), // new in 2.9

    // Various kinds of incertae sedis - former children of emptied containers
	INCERTAE_SEDIS		 ("incertae_sedis", "incertae_sedis_inherited", Taxonomy.INCERTAE_SEDIS),
	UNCLASSIFIED		 ("unclassified", "unclassified_inherited", Taxonomy.UNCLASSIFIED),
	ENVIRONMENTAL		 ("environmental", "environmental_inherited", Taxonomy.ENVIRONMENTAL),
	MAJOR_RANK_CONFLICT  ("major_rank_conflict",             // order that's sibling of a class
						  "major_rank_conflict_inherited",
						  Taxonomy.MAJOR_RANK_CONFLICT),     // Parent-dependent.  Retain value
    UNPLACED             ("unplaced", "unplaced_inherited", Taxonomy.UNPLACED),
    TATTERED             ("tattered", "tattered_inherited", Taxonomy.TATTERED), // DEPRECATED
       // don't remove, because it's nice to be able to read old taxonomy files

	// Not suitable for use as OTUs
	NOT_OTU			     ("not_otu", "not_otu", Taxonomy.NOT_OTU),
	VIRAL				 ("viral", "viral", Taxonomy.VIRAL),      // NCBI
	HYBRID				 ("hybrid", "hybrid", Taxonomy.HYBRID),   // NCBI

    // Annotations
	HIDDEN				 ("hidden", "hidden_inherited", Taxonomy.HIDDEN),	  // combine using &
	EDITED				 ("edited", null, Taxonomy.EDITED),	  				  // combine using |
	FORCED_VISIBLE		 ("forced_visible", null, Taxonomy.FORCED_VISIBLE),   // combine using |
	EXTINCT			 	 ("extinct", "extinct_inherited", Taxonomy.EXTINCT),  // combine using |

	// Inferred only.
	SIBLING_HIGHER		 (null, "sibling_higher", Taxonomy.SIBLING_HIGHER), // Australopithecus
	SIBLING_LOWER		 (null, "sibling_lower", Taxonomy.SIBLING_LOWER), // deprecated
	INFRASPECIFIC		 (null, "infraspecific", Taxonomy.INFRASPECIFIC),  // Has a species as an ancestor?
	BARREN			     (null, "barren", Taxonomy.BARREN);  // Contains no species?

	String name, inheritedName;
	int bit;

	Flag(String name, String inheritedName, int mask) {
		this.name = name;
		this.inheritedName = inheritedName;
		this.bit = mask;
	}

	static final Map<String, Flag> lookupTable = new HashMap<String, Flag>();
	static final Map<String, Flag> lookupInheritedTable = new HashMap<String, Flag>();
	static {
		for (Flag flag : Flag.values()) {
			lookupTable.put(flag.name, flag);
			lookupInheritedTable.put(flag.inheritedName, flag);
        }

        // For reading OTT 2.1 (very old)
		lookupTable.put("D", HIDDEN);

		// Container stubs - legacy
		lookupTable.put("incertae_sedis_direct", WAS_CONTAINER);
		lookupTable.put("unclassified_direct", WAS_CONTAINER);

        // Renamings - keep old names for legacy taxonomies
        lookupTable.put("major_rank_conflict_direct", MAJOR_RANK_CONFLICT);
        lookupTable.put("extinct_direct",	EXTINCT);
	}

	static Flag lookup(String name) {
		return lookupTable.get(name);
	}

	static Flag lookupInherited(String name) {
		return lookupInheritedTable.get(name);
	}

	static Pattern commaPattern = Pattern.compile(",");

	//static EnumSet<Flag> derived = 
	//	EnumSet<Flag>.of(BARREN, INFRASPECIFIC, SIBLING_HIGHER, SIBLING_LOWER);

	static void parseFlags(String flags, Taxon node) {
		int f = 0;
		int g = 0;
		for (String name : commaPattern.split(flags)) {
			Flag flag = Flag.lookup(name);
			if (flag != null) {
				f |= flag.bit;
			} else {
				Flag iflag = Flag.lookupInherited(name);
				if (iflag != null)
					g |= iflag.bit;
				else if (name.equals(""))
					;
				else
					System.err.format("** Unrecognized flag: %s\n", name);
			}
		}
		node.properFlags = f;
		node.inferredFlags = g;
	}

	public static void printFlags(int flags, int iflags, PrintStream out) {
        out.print(toString(flags, iflags));
    }

    public static String flagsAsString(Taxon node) {
        return toString(node.properFlags, node.inferredFlags);
    }

	public static String toString(int flags, int iflags) {
		boolean needComma = false;

        StringBuilder out = new StringBuilder();

		if (false)
			for (Flag flag : Flag.values()) {
				;
			} 

        // Now the various flavors of incertae sedis

		// Disposition relative to parent (formerly inside of containers)
		// The direct form means incertae sedis
		if ((flags & Taxonomy.INCERTAE_SEDIS) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("incertae_sedis");
		}
		if ((iflags & Taxonomy.INCERTAE_SEDIS) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("incertae_sedis_inherited");
		}
		if ((flags & Taxonomy.UNCLASSIFIED) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("unclassified");
		}
		if ((iflags & Taxonomy.UNCLASSIFIED) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("unclassified_inherited");
		}
		if ((flags & Taxonomy.ENVIRONMENTAL) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("environmental");
		}
		if ((iflags & Taxonomy.ENVIRONMENTAL) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("environmental_inherited");
		}
		if ((flags & Taxonomy.UNPLACED) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("unplaced");
		}
		if ((iflags & Taxonomy.UNPLACED) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("unplaced_inherited");
		}
		if ((flags & Taxonomy.MAJOR_RANK_CONFLICT) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("major_rank_conflict");
		}
		else if ((flags & Taxonomy.SIBLING_HIGHER) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("sibling_higher");
		}
		if ((iflags & Taxonomy.MAJOR_RANK_CONFLICT) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("major_rank_conflict_inherited");
		}

        // Former containers (empty, not inherited)
		if ((flags & Taxonomy.MERGED) != 0) { // never inherited
			if (needComma) out.append(","); else needComma = true;
			out.append("merged");
		}
		if ((flags & Taxonomy.INCONSISTENT) != 0) { // never inherited
			if (needComma) out.append(","); else needComma = true;
			out.append("inconsistent");
		}
		if ((flags & Taxonomy.WAS_CONTAINER) != 0) {
			if (needComma) out.append(","); else needComma = true;
			out.append("was_container");
		}

        // Bad annotations, heritable (not distinguishing _inherited from otherwise)
		if ((((flags | iflags) & Taxonomy.NOT_OTU) != 0)) {
			if (needComma) out.append(","); else needComma = true;
			out.append("not_otu");
        }
        if (((flags | iflags) & Taxonomy.VIRAL) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("viral");
        }
        if (((flags | iflags) & Taxonomy.HYBRID) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("hybrid");
        }

        // Good annotations - heritable
        if ((flags & Taxonomy.HIDDEN) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("hidden");
        }
        else if ((iflags & Taxonomy.HIDDEN) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("hidden_inherited");
        }
        if ((flags & Taxonomy.EXTINCT) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("extinct");
        }
        else if ((iflags & Taxonomy.EXTINCT) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("extinct_inherited");
        }

        // Inferred based on actual content
        if (((flags | iflags) & Taxonomy.INFRASPECIFIC) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("infraspecific");
        } else if (((flags | iflags) & Taxonomy.BARREN) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("barren");
        }

        return out.toString();
    }
}


	/*
       Original email from Cody follows:

	   flags are:

	   nototu # these are non-taxonomic entities that will never be made available for mapping to input tree nodes. we retain them so we can inform users if a tip is matched to one of these names
	   unclassified # these are "dubious" taxa that will be made available for mapping but will not be included in synthesis unless they exist in a mapped source tree
	   incertaesedis # these are (supposed to be) recognized taxa whose position is uncertain. they are generally mapped to some ancestral taxon, with the implication that a more precise placement is not possible (yet). shown in the synthesis tree whether they are mapped to a source tree or not
	   hybrid # these are hybrids
	   viral # these are viruses

	   rules listed below, followed by keywords for that rule.
	   rules should be applied to any names matching any keywords for that rule.
	   flags are inherited (conservative approach), except for "incertaesedis", which is a taxonomically explicit case that we can confine to the exact relationship (hopefully).

	   # removed keywords
	   scgc # many of these are within unclassified groups, and will be treated accordingly. however there are some "scgc" taxa that are within recognized groups. e.g. http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?mode=Undef&id=939181&lvl=3&srchmode=2&keep=1&unlock . these should be left in. so i advocate removing this name and force-flagging all children of unclassified groups.

	   ==== rules

	   # rule 1: flag taxa and their descendents `nototu`
	   # note: many of these are children of the "other sequences" container, but if we treat the cases individually then we will also catch any instances that may occur elsewhere (for some bizarre reason).
	   # note: any taxa flagged `nototu` need not be otherwise flagged.
	   other sequences
	   metagenome
	   artificial
	   libraries
	   bogus duplicates
	   plasmids
	   insertion sequences
	   midvariant sequence
	   transposons
	   unknown
	   unidentified
	   unclassified sequences
	   * .sp # apply this rule to "* .sp" taxa as well

	   # rule 6: flag taxa and their descendents `hybrid`
	   x

	   # rule 7: flag taxa and their descendents `viral`
	   viral
	   viroids
	   Viruses
	   viruses
	   virus

	   # rule 3+5: if the taxon has descendents, 
	   #			 flag descendents `unclassified` and elide,
	   #			 else flag taxon `unclassified`.
	   # (elide = move children to their grandparent and mark as 'not_otu')
	   mycorrhizal samples
	   uncultured
	   unclassified
	   endophyte
	   endophytic

	   # rule 2: if the taxon has descendents, 
	   #			 flag descendents `unclassified` and elide,
	   #			 else flag taxon 'not_otu'.
	   environmental

	   # rule 4: flag direct children `incertae_sedis` and elide taxon.
	   incertae sedis
	*/

