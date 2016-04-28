package org.opentreeoflife.taxa;

public abstract class Node {
	public String name;
	public Taxonomy taxonomy;			// For subsumption checks etc.

    public Node(Taxonomy tax, String name) {
        this.taxonomy = tax;
        this.name = name;
        this.taxonomy.addToNameIndex(this, name);
    }

    public abstract Taxon taxon();

    public abstract boolean taxonNameIs(String othername);

}
