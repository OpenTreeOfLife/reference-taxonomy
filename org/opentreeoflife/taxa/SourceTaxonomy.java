package org.opentreeoflife.taxa;

import java.io.PrintStream;
import java.io.IOException;

public class SourceTaxonomy extends Taxonomy {

	public SourceTaxonomy() {
        super();
	}

	// This is the SourceTaxonomy version.
	// Overrides dumpMetadata in class Taxonomy.
	public void dumpMetadata(String filename)	throws IOException {
		if (this.metadata != null) {
			PrintStream out = Taxonomy.openw(filename);
			out.println(this.metadata); // JSON
			out.close();
		}
	}
}


