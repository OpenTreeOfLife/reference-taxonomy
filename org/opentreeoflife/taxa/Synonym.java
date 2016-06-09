package org.opentreeoflife.taxa;

public class Synonym extends Node {

    // name inherited from class Node

    Taxon taxon;
    String type;                 // synonym, authority, common name, etc.

    public Synonym(String name, String type, Taxon taxon) {
        super(taxon.taxonomy, name); // does addToNameIndex
        this.taxon = taxon;
        this.type = type;
    }

    public Taxon taxon() { return taxon; }

    public boolean taxonNameIs(String othername) {
        return taxon.name.equals(othername);
    }

    public String uniqueName() {
        String uniq = taxon.uniqueName();
        if (uniq.length() == 0) uniq = taxon.name;
        return String.format("%s (synonym for %s)", this.name, uniq);
    }

}
