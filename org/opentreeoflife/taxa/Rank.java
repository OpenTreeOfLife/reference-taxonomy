package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;

public class Rank {

	public static String NO_RANK = null;

    public String name;
    public int level;

    Rank(String name, int level) {
        this.name = name;
        this.level = level;
    }

	static String[][] rankStrings = {
		{"domain",
		 "superkingdom",
		 "kingdom",
		 "subkingdom",
         "division",            // h2007
		 "infrakingdom",		// worms
		 "superphylum"},
		{"phylum",
		 "subphylum",
		 "infraphylum",			// worms
		 "subdivision",			// worms
		 "superclass"},
		{"class",
		 "subclass",
		 "infraclass",
		 "superorder"},
		{"order",
		 "suborder",
		 "infraorder",
		 "parvorder",
		 "section",				// worms
		 "subsection",			// worms
		 "superfamily"},
		{"family",
		 "subfamily",
		 "supertribe",			// worms
		 "tribe",
		 "subtribe"},
		{"genus",
		 "subgenus",
		 "species group",
		 "species subgroup"},
		{"species",
		 "infraspecificname",
		 "subspecies",
         "natio",               // worms
		 "variety",
		 "varietas",
		 "subvariety",
		 "forma",
		 "subform",
		 "samples"},
	};

	static Map<String, Rank> ranks = new HashMap<String, Rank>();
	public static Rank SPECIES_RANK; // ranks.get("species");

    static {
        for (int i = 0; i < rankStrings.length; ++i)
            for (int j = 0; j < rankStrings[i].length; ++j) {
                String name = rankStrings[i][j];
                ranks.put(name, new Rank(name, (i+1)*100 + j*10));
            }
        ranks.put("no rank", new Rank("no rank", -1));
        SPECIES_RANK = ranks.get("species");
    }

    public static Rank getRank(String rankstring) {
        return ranks.get(rankstring);
    }


}
