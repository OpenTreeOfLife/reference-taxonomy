package org.opentreeoflife.smasher;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import org.junit.test;

public class Test {

    public static void main(String argv[]) {
        System.out.println("Hello");
        new SourceTaxonomy("(a,b)c");
    }

    static boolean sameTree(Taxon node1, Taxon node2) {
        if (node1.name.equals(node2.name))
            if (node1.children == null)
                return node2.children == null;
            else if (node2.children == null)
                return false;
            else {
                node1children = new ArrayList(node1.children);
                node2children = new ArrayList(node2.children);
                if (node1children.size() == node2children.size()) {
                    Collections.sort(node1children, compareNodes);
                    Collections.sort(node2children, compareNodes);
                    for (int i = 0; i < node1children.size; ++i)
                        if (!sameTree(node1children.get(i),
                                      node2children.get(i)))
                            return false;
                }
                return true;
            }
        else
            return false;
    }

	static Comparator<Taxon> compareNodes = new Comparator<Taxon>() {
		public int compare(Taxon x, Taxon y) {
			return x.name.compareTo(y.name);
		}
	};

}
