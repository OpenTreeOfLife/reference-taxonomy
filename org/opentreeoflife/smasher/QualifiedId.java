
package org.opentreeoflife.smasher;

import java.util.regex.Pattern;


public class QualifiedId {
	String prefix;
	String id;

	static Pattern colonPattern = Pattern.compile(":");

	QualifiedId(String prefix, String id) {
		this.prefix = prefix; this.id = id;
	}
	QualifiedId(String qid) {
        String[] foo = colonPattern.split(qid, 2);
        if (foo.length == 1)
            { this.prefix = foo[0]; this.id = null; }
        else if (foo.length != 2)
            throw new RuntimeException("ill-formed qualified id: " + qid);
        else if (foo[0].equals("http") || foo[0].equals("https"))
            { this.prefix = qid; this.id = null; }
        else
            { this.prefix = foo[0]; this.id = foo[1]; }
	}

    public String getPrefix() { return prefix; }
    public String getId() { return id; }
	public String toString() {
        if (id == null)
            return prefix;
        else
            return prefix + ":" + id;
	}
	public boolean equals(Object o) {
		if (o instanceof QualifiedId) {
			QualifiedId qid = (QualifiedId)o;
            if (!qid.prefix.equals(prefix)) return false;
            if ((id == null) != (qid.id == null)) return false;
            if (qid != null && !qid.id.equals(id)) return false;
            return true;
		} else
			return false;
	}
    public int hashCode() {
        return this.prefix.hashCode() + this.id.hashCode();
    }
}

