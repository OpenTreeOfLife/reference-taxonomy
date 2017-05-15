package org.opentreeoflife.taxa;

public class Synonym extends Node {

    // name inherited from class Node

    String type;                 // synonym, authority, common name, etc.

    public Synonym(String name, String kind, Taxon taxon) {
        super(name);
        if (name == null)
            System.err.format("** Null name for synonym of %s\n", taxon);
        this.parent = taxon;
        this.type = kind;
        taxon.taxonomy.addToNameIndex(this, name);
    }

    public Taxon taxon() { return parent; }

    public String getType() { return type; }

    public Taxonomy getTaxonomy() { return parent.taxonomy; }

    public boolean taxonNameIs(String othername) {
        return parent.name.equals(othername);
    }

    public String uniqueName() {
        String uniq = parent.uniqueName();
        if (uniq.length() == 0) uniq = parent.name;
        return String.format("%s (synonym for %s)", this.name, uniq);
    }

    public boolean isPruned() {    // foo
        return parent.prunedp;
    }

    public String toString() {
        return String.format("(%s %s %s)", type, name, taxon());
    }

}
