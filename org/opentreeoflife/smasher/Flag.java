/*
 * Please don't laugh at the integer bit masks in this file... I'm in
 * the middle of a transition from using bit masks to using an enum,
 * and the process isn't complete yet.
 */

package org.opentreeoflife.smasher;

import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.io.PrintStream;
import java.util.regex.Pattern;

public enum Flag {

    // Emptied containers - these never have children
    WAS_CONTAINER        ("was_container", null, Taxonomy.WAS_CONTAINER), // class incertae sedis, etc.
    INCONSISTENT         ("inconsistent", "inconsistent_inherited", Taxonomy.INCONSISTENT),
    MERGED               ("merged", "merged_inherited", Taxonomy.INCONSISTENT), // new in 2.9

    // Various kinds of incertae sedis - former children of emptied containers
	INCERTAE_SEDIS		 ("incertae_sedis", "incertae_sedis_inherited", Taxonomy.INCERTAE_SEDIS),
	UNCLASSIFIED		 ("unclassified", "unclassified_inherited", Taxonomy.UNCLASSIFIED),
	ENVIRONMENTAL		 ("environmental", "environmental_inherited", Taxonomy.ENVIRONMENTAL),
	MAJOR_RANK_CONFLICT  ("major_rank_conflict",             // order that's sibling of a class
						  "major_rank_conflict_inherited",
						  Taxonomy.MAJOR_RANK_CONFLICT),     // Parent-dependent.  Retain value
    UNPLACED             ("unplaced", "unplaced_inherited", Taxonomy.UNPLACED),

	// Not suitable for use as OTUs
	NOT_OTU			     ("not_otu", "not_otu", Taxonomy.NOT_OTU),
	VIRAL				 ("viral", "viral", Taxonomy.VIRAL),      // NCBI
	HYBRID				 ("hybrid", "hybrid", Taxonomy.HYBRID),   // NCBI

    // Annotations
	SIBLING_HIGHER		 ("sibling_higher", null, Taxonomy.SIBLING_HIGHER), // Australopithecus
	SIBLING_LOWER		 ("sibling_lower", null, Taxonomy.SIBLING_LOWER), // deprecated
	HIDDEN				 ("hidden", "hidden_inherited", Taxonomy.HIDDEN),	  // combine using &
	EDITED				 ("edited", null, Taxonomy.EDITED),	  				  // combine using |
	FORCED_VISIBLE		 ("forced_visible", null, Taxonomy.FORCED_VISIBLE),   // combine using |
	EXTINCT			 	 ("extinct", "extinct_inherited", Taxonomy.EXTINCT),  // combine using |

	// Inferred only.
	INFRASPECIFIC		 (null, "infraspecific", Taxonomy.INFRASPECIFIC),  // Has a species as an ancestor?
	BARREN			     (null, "barren", Taxonomy.BARREN);  // Contains no species?

	String name, inheritedName;
	int bit;

	Flag(String name, String inheritedName, int bit) {
		this.name = name;
		this.inheritedName = inheritedName;
		this.bit = bit;
	}

	static final Map<String, Flag> lookupTable = new HashMap<String, Flag>();
	static final Map<String, Flag> lookupInheritedTable = new HashMap<String, Flag>();
	static {
		for (Flag flag : Flag.values()) {
			lookupTable.put(flag.name, flag);
			lookupInheritedTable.put(flag.inheritedName, flag);
        }

		// Container stubs - legacy
		lookupTable.put("incertae_sedis_direct", WAS_CONTAINER);
		lookupTable.put("unclassified_direct", WAS_CONTAINER);

        // Renamings - keep old names for legacy taxonomies
        lookupTable.put("major_rank_conflict_direct", MAJOR_RANK_CONFLICT);
        lookupTable.put("extinct_direct",	EXTINCT);

        // 'Tattered' replaced with 'inconsistent' in 2.9
        lookupTable.put("tattered",	UNPLACED);
        lookupInheritedTable.put("tattered_inherited",	UNPLACED);
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
        if ((iflags & Taxonomy.EXTINCT) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("extinct_inherited");
        }

        // Good annotations - not meaningfully heritable
        if ((flags & Taxonomy.EDITED) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("edited");
        }
        if ((flags & Taxonomy.FORCED_VISIBLE) != 0) {
            if (needComma) out.append(","); else needComma = true;
            out.append("forced_visible");
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
