package org.opentreeoflife.taxa;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

public abstract class Node {
	public Taxonomy taxonomy;			// For subsumption checks etc.
	public String name;
	public Taxon parent = null;
	public String id = null;
	public List<QualifiedId> sourceIds = null;

    public Node(String name) {
        this.name = name;
    }

    public abstract Taxon taxon();

    public abstract String getType();

    public abstract Taxonomy getTaxonomy();

    public abstract boolean taxonNameIs(String othername);

    public abstract String uniqueName();

	public String getId() {
        return id;
    }

	public void setId(String id) {
		if (id == null)
            return;
        if (this.id == null)
            this.addId(id);
        else if (!this.id.equals(id)) {
            String wasid = this.id;
            this.id = null;
            this.addId(id);
            this.addId(wasid);
        }
	}

    public void addId(String id) {
        this.taxonomy.addId(this, id);
    }

    public QualifiedId originId() {
        if (this.sourceIds != null)
            return this.sourceIds.get(0);
        else
            return null;
    }

	public QualifiedId getQualifiedId() {
        String space = this.taxonomy.getIdspace();
		if (this.id != null) {
			return new QualifiedId(space, this.id);
        } else if (this.parent == null) {
            // Shouldn't happen
			System.out.format("* [getQualifiedId] %s is detached\n", this);
            return new QualifiedId(space, "<detached>");
        } else if (this.name != null) {
            // e.g. h2007
			// System.out.format("* [getQualifiedId] Taxon has no id, using name: %s:%s\n", space, this.name);
			return new QualifiedId(space, this.name);
        } else {
			// What if from a Newick string?
			System.out.println("* [getQualifiedId] Nondescript");
            return new QualifiedId(space, "<nondescript>");
        }
	}

	// Add most of the otherwise unmapped nodes to the union taxonomy,
	// either as new names, fragmented taxa, or (occasionally)
	// new homonyms, or vertical insertions.

	public void addSourceId(QualifiedId qid) {
		if (this.sourceIds == null)
			this.sourceIds = new ArrayList<QualifiedId>(1);
		if (!this.sourceIds.contains(qid)) {
			this.sourceIds.add(qid);
            this.getTaxonomy().indexByQid(this, qid);
        }
	}

	// Note: There can be multiple sources, separated by commas.
	// However, the first one in the list is the original source.

	public QualifiedId putativeSourceRef() {
		if (this.sourceIds != null)
			return this.sourceIds.get(0);
		else
			return null;
	}

	static Pattern commaPattern = Pattern.compile(",");

	public void setSourceIds(String info) {
		if (info.equals("null")) return;	// glitch in OTT 2.2
		String[] ids = commaPattern.split(info);
		if (ids.length > 0) {
			this.sourceIds = new ArrayList<QualifiedId>(ids.length);
			for (String qid : ids) {
                if (qid.length() > 0)
                    this.addSourceId(new QualifiedId(qid));
            }
		}
	}

	// Returns a string of the form prefix:id,prefix:id,...
	// Generally called on a union taxonomy node

	public String getSourceIdsString() {
		String answer = null;
		List<QualifiedId> qids = this.sourceIds;
		if (qids != null) {
			for (QualifiedId qid : qids) {
				if (answer == null)
					answer = qid.toString();
				else
					answer = answer + "," + qid.toString();
			}
		}
		// else answer = getQualifiedId().toString() ... ?
		if (answer != null)
			return answer;
		else
			// callers expect non-null
			return "";
	}

    // hmm, I think node is always a taxon
    public void copySourceIdsFrom(Node node) {
        if (node.sourceIds != null) {
            this.sourceIds = new ArrayList<QualifiedId>(node.sourceIds);
            Taxonomy tax = this.getTaxonomy();
            for (QualifiedId qid : this.sourceIds)
                tax.indexByQid(this, qid);
        }
    }

}
