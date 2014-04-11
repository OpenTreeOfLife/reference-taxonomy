package org.opentreeoflife.smasher;

import java.util.HashMap;
import java.util.Map;

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
		for (Flag flag : Flag.values())
			lookupInheritedTable.put(flag.inheritedName, flag);
	}

	static Flag lookup(String name) {
		return lookupTable.get(name);
	}

	static Flag lookupInherited(String name) {
		return lookupInheritedTable.get(name);
	}

}
