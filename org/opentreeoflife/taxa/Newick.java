package org.opentreeoflife.taxa;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.io.PushbackReader;
import java.io.IOException;

class Newick {

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
					if (d < 0 || d == ')') break;
					if (d != ',')
						System.out.println("shouldn't happen: " + d);
				}
			}
			Taxon node = newickToNode(in, dest); // get postfix name, x in (a,b)x
			if (node != null || children.size() > 0) {
				if (node == null) {
					// kludge
					node = new Taxon(dest, "");
				}
				for (Taxon child : children)
					if (child.name == null || !child.name.startsWith("null"))
						node.addChild(child);
				node.rank = (children.size() > 0) ? Taxonomy.NO_RANK : "species";
				return node;
			} else
				return null;
		} else {
            in.unread(c);
            String label = readLabel(in);
            skipBranchLength(in);

            Taxon node = new Taxon(dest); // no name
            initNewickNode(node, label);
            return node;
        }
    }
            
    static String readLabel(PushbackReader in) throws IOException {
        StringBuffer buf = new StringBuffer();
        int c = in.read();
        if (c == '\'')
            while (true) {
                c = in.read();
                if (c < 0)
                    break;
                else if (c == '\'') {
                    // Either end of label, or ''
                    c = in.read();
                    if (c < 0)
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
                if (c < 0)
                    break;
                else if (c == ')' || c == ',' || c == ';') {
                    in.unread(c);
                    break;
                } else {
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

    static Pattern ottidPattern = Pattern.compile("(.*)_ott([0-9]+)");

	// Specially hacked to support the Hibbett 2007 spreadsheet & synthesis output
	static void initNewickNode(Taxon node, String label) {

        if (label == null) {
            node.rank = Taxonomy.NO_RANK;
            return;
        }

        // Look for special synthesis output pattern e.g. Picomonas_judraskeda_ott4738960
        Matcher m = ottidPattern.matcher(label);
        if (m.matches()) {
            
            // int i = label.indexOf("_ott"); label.substring(i+4) label.substring(0,i)
            node.rank = Taxonomy.NO_RANK;
            node.setId(m.group(2));
            node.setName(spacify(m.group(1)));
            return;
        }

		// Ad hoc rank syntax Class=Amphibia
		int pos = label.indexOf('=');
		if (pos > 0) {
            String rank = label.substring(0,pos).toLowerCase();
            if (Taxonomy.ranks.get(rank) != null) {
                node.rank = rank;
                node.setName(spacify(label.substring(pos+1)));
            } else {
                System.out.format("** Unrecognized rank: %s\n", label);
                node.rank = Taxonomy.NO_RANK;
                node.setName(spacify(label));
            }

		} else {
			node.rank = Taxonomy.NO_RANK;
			node.setName(spacify(label));
		}
	}

    static private String spacify(String s) {
        String r = s.replaceAll("_", " ");
        return (r.length() == 0 ? null : r);
    }

    // ----- WRITE -----

	static void appendNewickTo(Taxon node, StringBuffer buf) {
		if (node.children != null) {
			buf.append("(");
			Collections.sort(node.children, Taxon.compareNodes);
			Taxon last = node.children.get(node.children.size()-1);
			for (Taxon child : node.children) {
				appendNewickTo(child, buf);
				if (child != last)
					buf.append(",");
			}
			buf.append(")");
		}
		if (node.name != null)
			buf.append(newickName(node.name, node.taxonomy.getTag(), node.id));
	}

    // Newick stuff copied from src/main/java/opentree/GeneralUtils.java
    // in treemachine repo.  Written by Joseph W. Brown and the other treemachine
    // developers.

    // All common non-alphanumeric chars except "_" and "-", for use when cleaning strings
    public static final Pattern newickIllegal =
        Pattern.compile(".*[\\Q:;/[]{}(),\\E]+.*");
	
	/**
	 * Make sure name conforms to valid newick usage
     * (http://evolution.genetics.washington.edu/phylip/newick_doc.html).
	 * 
	 * Replaces single quotes in `origName` with "''" and puts a pair of single quotes
     * around the entire string.
	 * Puts quotes around name if any illegal characters are present.
	 * 
	 * Author: Joseph W. Brown
	 *
	 * @param origName
	 * @return newickName
	 */
	public static String newickName(String origName, String tag, String id) {
		boolean needQuotes = false;
		String newickName = origName;
		
		// replace all spaces with underscore
		newickName = newickName.replaceAll(" ", "_");
		
		// replace ':' with '_'. a hack for working with older versions of
        // dendroscope e.g. 2.7.4
		newickName = newickName.replaceAll(":", "_");
		
		// newick standard way of dealing with single quotes in taxon names
		if (newickName.contains("'")) {
			newickName = newickName.replaceAll("'", "''");
			needQuotes = true;
        }
        // not sure about this one.  turn foo into foo_ott1234
		if (tag != null && id != null)
			newickName = String.format("%s_%s%s", newickName, tag, id);

		// if offending characters are present, quotes are needed
		if (newickIllegal.matcher(newickName).matches())
			needQuotes = true;
		if (needQuotes)
			newickName = "'" + newickName + "'";
		
		return newickName;
	}

}