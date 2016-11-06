package org.opentreeoflife.taxa;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.io.PushbackReader;
import java.io.IOException;

public class Newick {

    // ----- READ -----

	// Parse Newick yielding nodes

	static Taxon newickToNode(String newick, Taxonomy dest) {
		PushbackReader in = new PushbackReader(new java.io.StringReader(newick));
		try {
			return newickToNode(in, dest);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	// TO BE DONE: 
    //    Implement ; for reading forests
    //    Do some syntax checking / error reporting

	static Taxon newickToNode(PushbackReader in, Taxonomy dest) throws IOException {
		int c = in.read();
		if (c == '(') {
			List<Taxon> children = new ArrayList<Taxon>();
			{
				Taxon child;
				while ((child = newickToNode(in, dest)) != null) {
					children.add(child);
					int d = in.read();
					if (d < 0 || d >= 65535 || d == ')') break;
					if (d != ',') {
						System.out.format("Newick syntax error: expected comma or right paren: %s %s\n", d, children);
                        break;  // ???
                    }
				}
			}
			Taxon node = newickToNode(in, dest); // get postfix name, x in (a,b)x
			if (node != null || children.size() > 0) {
				if (node == null) {
					// kludge
					node = new Taxon(dest, null);
				}
				for (Taxon child : children)
					if (child.name == null || !child.name.startsWith("null"))
						node.addChild(child);
				node.rank = (children.size() > 0) ? Rank.NO_RANK : Rank.SPECIES_RANK;
				return node;
			} else
				return null;
		} else {
            in.unread(c);
            String label = readLabel(in);
            skipBranchLength(in);

            Taxon node = new Taxon(dest, null); // no name
            initNewickNode(node, label);
            return node;
        }
    }
            
    // http://evolution.genetics.washington.edu/phylip/newick_doc.html

    static String readLabel(PushbackReader in) throws IOException {
        StringBuilder buf = new StringBuilder();
        int c = in.read();
        if (c == '\'')
            while (true) {
                c = in.read();
                if (c < 0 || c >= 65535)
                    break;
                else if (c == '\'') {
                    // Either end of label, or ''
                    c = in.read();
                    if (c < 0 || c >= 65535)
                        break;
                    else if (c == '\'') {
                        buf.appendCodePoint(c);
                        // back to while(true)
                    } else {
                        // could be :, ), etc.
                        in.unread(c);
                        break;
                    }
                } else
                    buf.appendCodePoint(c);
            }
        else
            while (true) {
                if (c < 0 || c >= 65535)
                    break;
                else if (c == ')' || c == ',' || c == ';') {
                    in.unread(c);
                    break;
                } else {
                    if (c == '_') c = ' ';
                    buf.appendCodePoint(c);
                    c = in.read();
                }
            }
        if (buf.length() > 0)
            return buf.toString();
        else
            return null;
    }

    static void skipBranchLength(PushbackReader in) throws IOException {
        if (peek(in) == ':') {
            in.read();
            readLabel(in);
        }
    }

    static int peek(PushbackReader in) throws IOException {
        int c = in.read();
        in.unread(c);
        return c;
    }

    static private String synthid = "(ott[0-9]+|mrcaott[0-9]+ott[0-9]+)";
    static Pattern nameAndIdPattern = Pattern.compile("(.+)[_ ]" + synthid);
    static Pattern idPattern = Pattern.compile(synthid);

	// Specially hacked to support the Hibbett 2007 spreadsheet & synthesis output
	static void initNewickNode(Taxon node, String label) {
        node.rank = Rank.NO_RANK;

        if (label == null || label.length() == 0)
            return;

        // Look for special synthesis output pattern e.g. Picomonas_judraskeda_ott4738960
        Matcher m = nameAndIdPattern.matcher(label); // name id (name_id)
        if (m.matches()) {
            setId(node, m.group(2));
            node.setName(m.group(1));
            return;
        }
        Matcher m2 = idPattern.matcher(label); // id
        if (m2.matches()) {
            setId(node, label);
            return;
        }

		// Ad hoc rank syntax Class=Amphibia
		int pos = label.indexOf('=');
		if (pos > 0) {
            String rankname = label.substring(0,pos).toLowerCase();
            Rank rank = Rank.getRank(rankname);
            if (rank == null) {
                System.err.format("** Unrecognized rank: %s\n", label);
                rank = Rank.NO_RANK;
            }
            node.rank = rank;
            node.setName(label.substring(pos+1));
		} else
			node.setName(label);
	}

    static void setId(Taxon node, String id) {
        node.setId(id);
        /* don't know whether this would be useful.
        if (id.startsWith("ott"))
            node.addSourceId(new QualifiedId("ott", id.substring(3)));
        */
    }

    // ----- WRITE -----

    public final static int USE_IDS = 1;
    public final static int USE_NAMES = 2;
    public final static int USE_NAMES_AND_IDS = 3;

	public static String toNewick(Taxonomy tax, int mode) {
		StringBuilder buf = new StringBuilder();
		for (Taxon root: tax.roots()) {
			Newick.appendNewickTo(root, mode, buf);
			buf.append(";");
		}
		return buf.toString();
	}

	static void appendNewickTo(Taxon node, int mode, StringBuilder buf) {
		if (node.children != null) {
			buf.append("(");
            List<Taxon> sorted = new ArrayList<Taxon>(node.children);
			Collections.sort(sorted, Taxon.compareNodes);
			Taxon last = sorted.get(sorted.size()-1);
			for (Taxon child : sorted) {
				appendNewickTo(child, mode, buf);
				if (child != last)
					buf.append(",");
			}
			buf.append(")");
		}
        String label = newickLabel(node.name, mode, node.id);
        if (label != null)
            buf.append(label);
	}

    // Newick stuff copied from src/main/java/opentree/GeneralUtils.java
    // in treemachine repo.  Written by Joseph W. Brown and the other treemachine
    // developers.

    // All common non-alphanumeric chars except "_" and "-", for use when cleaning strings
    public static final Pattern newickIllegal =
        Pattern.compile("[\\Q:;/[]{}(),\\E'_]");
	
	/**
	 * Make sure name conforms to valid newick usage
     * (http://evolution.genetics.washington.edu/phylip/newick_doc.html).
	 * 
	 * Replaces single quotes in `origName` with "''" and puts a pair of single quotes
     * around the entire string.
	 * Puts quotes around name if any illegal characters are present.
	 * 
	 * Derives from code written by: Joseph W. Brown
	 *
	 * @param origName
	 * @return newick label, with quotes or _ if needed
	 */
	public static String newickLabel(String origName, int mode, String id) {
        String label = null;
        switch (mode) {
        case USE_IDS:
            label = id; break;
        case USE_NAMES:
            label = origName; break;
        case USE_NAMES_AND_IDS: 
            // turn foo into foo_ott1234
            if (origName != null && id != null)
                label = String.format("%s %s", origName, id);
            else if (id != null)
                label = id;
            else if (origName != null)
                label = origName;
            break;
        }
        if (label == null) return null;
        return newickQuote(label);
    }

    static String newickQuote(String label) {
		if (newickIllegal.matcher(label).find()) {
		
            // replace ':' with something else. a hack for working
            // with older versions of dendroscope e.g. 2.7.4
            label = label.replaceAll(":", "<colon>");
		
            // newick standard way of dealing with single quotes in taxon names
            if (label.contains("'"))
                label = label.replaceAll("'", "''");
            return String.format("'%s'", label);
        } else
            return label.replaceAll(" ", "_");
	}

}
