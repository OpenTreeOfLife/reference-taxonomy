
package org.opentreeoflife.taxa;

import java.util.regex.Pattern;


public class QualifiedId {
	public String prefix;
	public String id;

	private static Pattern colonPattern = Pattern.compile(":");

	public QualifiedId(String prefix, String id) {
        if (prefix == null)
            throw new IllegalArgumentException("wanted non-null qid prefix");
		this.prefix = prefix; this.id = id;
	}
	public QualifiedId(String qid) {
        String[] foo = colonPattern.split(qid, 2);
        if (foo.length != 2)
            throw new RuntimeException("ill-formed CURIEorIRI: " + qid);
        else if (foo[1].startsWith("//"))
            // IRI
            { this.prefix = qid; this.id = null; }
        else if (foo[1].length() == 0)
            // CURIE
            { this.prefix = foo[0]; this.id = null; }
        else
            // CURIE
            { this.prefix = foo[0]; this.id = foo[1]; }
	}

    public String getPrefix() { return prefix; }
    public String getId() { return id; }
	public String toString() {
        if (id == null) {
            if (prefix.indexOf("//") > 0)
                // IRI
                return prefix;
            else
                // CURIE
                return prefix + ":";
        } else
            // CURIE
            return prefix + ":" + id;
	}
	public boolean equals(Object o) {
        if (this == o)
            return true;
		else if (o instanceof QualifiedId) {
			QualifiedId qid = (QualifiedId)o;
            if (!qid.prefix.equals(this.prefix)) return false;
            if (qid.id == null)
                return (this.id == null);
            if (!qid.id.equals(this.id)) return false;
            return true;
		} else
			return false;
	}
    public int hashCode() {
        if (this.id == null)
            return this.prefix.hashCode() + 1;
        else
            return this.prefix.hashCode() + this.id.hashCode();
    }
}

