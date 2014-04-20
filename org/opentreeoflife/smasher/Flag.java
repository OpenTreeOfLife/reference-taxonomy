package org.opentreeoflife.smasher;

import java.util.HashMap;
import java.util.Map;
import java.io.PrintStream;
import java.util.regex.Pattern;

public enum Flag {

	// NCBI - individually troublesome - not sticky - combine using &
	NOT_OTU			     ("not_otu", "not_otu", Taxonomy.NOT_OTU),
	VIRAL				 ("viral", "viral", Taxonomy.VIRAL),
	HYBRID				 ("hybrid", "hybrid", Taxonomy.HYBRID),

	// Final analysis...
	// Containers - unconditionally so.
	INCERTAE_SEDIS		 ("incertae_sedis_direct", "incertae_sedis_inherited", Taxonomy.INCERTAE_SEDIS),
	UNCLASSIFIED		 ("unclassified_direct", "unclassified_inherited", Taxonomy.UNCLASSIFIED),
	ENVIRONMENTAL		 ("environmental", "environmental_inherited", Taxonomy.ENVIRONMENTAL),

	// Set during assembly
	HIDDEN				 ("hidden", "hidden_inherited", Taxonomy.HIDDEN),	  // combine using &
	MAJOR_RANK_CONFLICT  ("major_rank_conflict_direct", "major_rank_conflict_inherited", Taxonomy.MAJOR_RANK_CONFLICT),     // Parent-dependent.  Retain value

	// Australopithecus
	SIBLING_HIGHER		 ("sibling_higher", null, Taxonomy.SIBLING_HIGHER),
	SIBLING_LOWER		 ("sibling_lower", null, Taxonomy.SIBLING_LOWER),

	TATTERED			 ("tattered", "tattered_inherited", Taxonomy.TATTERED),  // combine using |
	EDITED				 ("edited", null, Taxonomy.EDITED),	  				  // combine using |
	FORCED_VISIBLE		 ("forced_visible", null, Taxonomy.FORCED_VISIBLE),	  		  // combine using |
	EXTINCT			 	 ("extinct_direct", "extinct_inherited", Taxonomy.EXTINCT),	  // combine using |

	// Is 'species' or lower rank ('infraspecific' when inherited)
	// Unconditional ?
	SPECIFIC			 (null, "infraspecific", Taxonomy.SPECIFIC),

	// Opposite of 'barren' - propagated upward
	ANYSPECIES			 (null, null, Taxonomy.ANYSPECIES);

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
		for (Flag flag : Flag.values())
			lookupTable.put(flag.name, flag);
		lookupTable.put("extinct", EXTINCT); // hack for IF and IRMNG
		for (Flag flag : Flag.values())
			lookupInheritedTable.put(flag.inheritedName, flag);
	}

	static Flag lookup(String name) {
		return lookupTable.get(name);
	}

	static Flag lookupInherited(String name) {
		return lookupInheritedTable.get(name);
	}

	static Pattern commaPattern = Pattern.compile(",");

	static int parseFlags(String flags) {
		int f = Taxonomy.ANYSPECIES;
		for (String name : commaPattern.split(flags)) {
			Flag flag = Flag.lookup(name);
			if (flag != null)
				f |= flag.bit;
			else {
				Flag iflag = Flag.lookupInherited(name);
				if (iflag != null)
					f |= iflag.bit; // Temporary kludge for containers
				else if (name.equals("barren"))
					f &= ~Taxonomy.ANYSPECIES;
				else
					System.err.format("** Unrecognized flag: %s\n", name);
			}
		}
		return f;
	}

	public static void printFlags(int flags, int iflags, PrintStream out) {
		// for (Flag flag : Flag.values()) { ...} 

		boolean needComma = false;
		if ((((flags | iflags) & Taxonomy.NOT_OTU) != 0)
			|| ((iflags & Taxonomy.ENVIRONMENTAL) != 0)) {
			if (needComma) out.print(","); else needComma = true;
			out.print("not_otu");
		}
		if (((flags | iflags) & Taxonomy.VIRAL) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("viral");
		}
		if (((flags | iflags) & Taxonomy.HYBRID) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("hybrid");
		}

		// Containers
		if ((flags & Taxonomy.INCERTAE_SEDIS) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("incertae_sedis_direct");
		}
		if ((iflags & Taxonomy.INCERTAE_SEDIS) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("incertae_sedis_inherited");
		}

		if ((flags & Taxonomy.UNCLASSIFIED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("unclassified_direct");  // JAR prefers 'unclassified'
		}
		if ((iflags & Taxonomy.UNCLASSIFIED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("unclassified_inherited"); // JAR prefers 'unclassified_indirect' ?
		}

		if ((flags & Taxonomy.ENVIRONMENTAL) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("environmental");
		}
		if ((flags & Taxonomy.HIDDEN) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("hidden");
		}
		else if ((iflags & Taxonomy.HIDDEN) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("hidden_inherited");
		}

		if ((flags & Taxonomy.MAJOR_RANK_CONFLICT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("major_rank_conflict_direct");
		}
		else if ((flags & Taxonomy.SIBLING_HIGHER) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("sibling_higher");
		}
		if ((flags & Taxonomy.SIBLING_LOWER) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("sibling_lower");
		}

		if ((iflags & Taxonomy.MAJOR_RANK_CONFLICT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("major_rank_conflict_inherited");
		}

		// Misc
		if ((flags & Taxonomy.TATTERED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("tattered");
		}
		if ((iflags & Taxonomy.TATTERED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("tattered_inherited");
		}

		if ((flags & Taxonomy.EDITED) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("edited");
		}

		if ((flags & Taxonomy.EXTINCT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("extinct_direct");
		}
		if ((iflags & Taxonomy.EXTINCT) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("extinct_inherited");
		}

		if ((flags & Taxonomy.FORCED_VISIBLE) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("forced_visible");
		}

		if ((iflags & Taxonomy.SPECIFIC) != 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("infraspecific");
		} else if ((flags & Taxonomy.ANYSPECIES) == 0) {
			if (needComma) out.print(","); else needComma = true;
			out.print("barren");
		}
	}
}
