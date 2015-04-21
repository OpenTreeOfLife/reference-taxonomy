
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
		if (foo.length != 2)
			throw new RuntimeException("ill-formed qualified id: " + qid);
		this.prefix = foo[0]; this.id = foo[1];
	}
    public String getPrefix() { return prefix; }
    public String getId() { return id; }
	public String toString() {
		return prefix + ":" + id;
	}
	public boolean equals(Object o) {
		if (o instanceof QualifiedId) {
			QualifiedId qid = (QualifiedId)o;
			return (qid.id.equals(id) &&
					qid.prefix.equals(prefix));
		} else
			return false;
	}
}

