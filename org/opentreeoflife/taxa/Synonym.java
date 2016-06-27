package org.opentreeoflife.taxa;

public class Synonym extends Node {

    // name inherited from class Node

    String type;                 // synonym, authority, common name, etc.

    public Synonym(String name, String type, Taxon taxon) {
        super(taxon.taxonomy, name); // does addToNameIndex
        this.parent = taxon;
        this.type = type;
    }

    public Taxon taxon() { return parent; }

    public boolean taxonNameIs(String othername) {
        return parent.name.equals(othername);
    }

    public String uniqueName() {
        String uniq = parent.uniqueName();
        if (uniq.length() == 0) uniq = parent.name;
        return String.format("%s (synonym for %s)", this.name, uniq);
    }

}
