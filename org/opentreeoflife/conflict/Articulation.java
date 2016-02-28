
// the way Java makes you create lots of separate little files that do practically nothing is really stupid.

package org.opentreeoflife.conflict;

import org.opentreeoflife.taxa.Taxon;

public class Articulation {
    public Disposition disposition;
    public Taxon witness;

    Articulation(Disposition disposition, Taxon witness) {
        this.disposition = disposition;
        this.witness = witness;
    }

    public String toString() {
        return String.format("(%s %s)", disposition, witness);
    }

}

