package org.opentreeoflife.taxa;

public class Synonym extends Node {

    // name inherited from class Node

    Taxon taxon;
    String type;                // synonym, authority, common name, etc.
    String source = null;       // idspace: ncbi, gbif, etc. (for union only)

    public Synonym(String name, String type, Taxon taxon) {
        super(taxon.taxonomy, name);
        this.taxon = taxon;
        this.type = type;
    }

    public Taxon taxon() { return taxon; }

    public boolean taxonNameIs(String othername) {
        return taxon.name.equals(othername);
    }

}
