/*
  TBD:
    - fix exclusion selection logic (to be more 'fair')
*/


package org.opentreeoflife.registry;

import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.QualifiedId;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;

import org.python.util.PythonInterpreter;
import org.python.util.InteractiveConsole;
import org.python.util.JLineConsole;

public class Registry {

    // State

    private Map<Integer, Registration> registrations = new HashMap<Integer, Registration>();

    public Registry() {
    }

    public Iterable<Registration> allRegistrations() {
        return registrations.values();
    }

    // Create Registration records on demand

    public Registration getRegistration(int n) {
        Registration probe = registrations.get(n);
        if (probe != null) return probe;
        Registration reg = new Registration(n);
        registrations.put(n, reg);
        if (n >= nextid)
            nextid = n + 1;
        return reg;
    }

    int nextid = 1;

    public Registration newRegistration() {
        return getRegistration(nextid++);
    }

    // jython convenience
    public static boolean isTerminalTaxon(Taxon node) {
        return Terminal.isTerminalTaxon(node);
    }

    // ------------------------------------------------------------

    // Load registry from file

    public static Registry load(String filename) throws IOException {
        BufferedReader br = Taxonomy.fileReader(filename);
        String row;
        Registry registry = new Registry();
        while ((row = br.readLine()) != null) {
            Registration reg = Registration.parse(row, registry);
            registry.registrations.put(reg.id, reg);
        }
        System.out.format("%s registrations loaded\n", registry.registrations.size());
        br.close();
        for (Registration reg : registry.allRegistrations()) {
            if (reg.samples != null)
                Collections.sort(reg.samples, Terminal.betterQuality);
            if (reg.exclusions != null)
                Collections.sort(reg.exclusions, Terminal.betterQuality);
        }
        return registry;
    }

    // write registry

    public void dump(String filename) throws IOException {
        PrintStream out = Taxonomy.openw(filename);
        for (Registration reg : allRegistrations())
            reg.dump(out);
        out.close();
    }

    // entry from shell.  not needed, use main method from org.python.util.jython

	public static void main(String argv[]) throws Exception {
		if (argv.length > 0) {
			PythonInterpreter j = new PythonInterpreter();
            for (String source : argv)
                j.execfile(source);
        } else {
			System.out.format("Consider doing:\n" +
                              "from org.opentreeoflife.taxa import Taxonomy\n" +
                              "from org.opentreeoflife.registry import Registry\n");
			InteractiveConsole j = new JLineConsole();
			j.interact();
        }
    }

}

// Registration record for an internal node
// Consider renaming this to 'split'

class Registration {

    int id;                     // Positive
    List<Terminal> samples = null;
    List<Terminal> exclusions = null;

    Registration(int id) {
        this.id = id;
    }

    void dump(PrintStream out) throws IOException {
        out.format("%s",
                   this.id);
        if (samples != null)
            for (Terminal term : samples)
                out.format(",%s", term.id);
        if (exclusions != null)
            for (Terminal term : exclusions)
                out.format(",-%s", term.id);
        out.println();
    }

    static Registration parse(String row, Registry registry) {
        String[] cells = row.split(",");
        int id = Integer.parseInt(cells[0]);
        // TBD: error if we've already seen a row for this id
        Registration entry = registry.getRegistration(id);
        List<Terminal> samples = new ArrayList<Terminal>();
        List<Terminal> exclusions = new ArrayList<Terminal>();
        for (int i = 1; i < cells.length; ++i) {
            String sid = cells[i];
            if (sid.length() != 0) {
                if (sid.startsWith("-"))
                    exclusions.add(Terminal.getTerminal(sid.substring(1)));
                else
                    samples.add(Terminal.getTerminal(sid));
            }
        }
        entry.samples = samples;
        entry.exclusions = exclusions;
        return entry;
    }

    public String toString() {
        String ii = "";
        String ee = "";
        if (this.samples != null && this.samples.size() > 0)
            ii = String.format("%s ...", this.samples.get(0));
        if (this.exclusions != null && this.exclusions.size() > 0)
            ee = String.format("%s ...", this.exclusions.get(0));
        return String.format("[%s = %s | %s]", this.id, ii, ee);
    }

}

// Information from OTT

class Terminal {

    String id;                     // taxon id from taxonomy
    String name = null;
    QualifiedId qid = null;
    int quality = 0;

    static Map<String, Terminal> terminals = new HashMap<String, Terminal>();

    private Terminal(String id) {
        this.id = id;
    }

    public static boolean isTerminalTaxon(Taxon node) {
        if (node.children == null)
            return true;
        if (node.rank != null && node.rank.equals("species"))
            return true;
        if (node.isInfraspecific())
            return true;
        return false;
    }

    static Terminal getTerminal(String id) {
        Terminal probe = terminals.get(id);
        if (probe != null) return probe;
        Terminal term = new Terminal(id);
        terminals.put(id, term);
        return term;
    }

    static Terminal getTerminal(Taxon node) {
        Terminal term = getTerminal(node.id);
        // TBD: Detect alterations
        term.name = node.name;
        if (node.sourceIds != null) {
            if (node.sourceIds.size() >= 2
                && node.sourceIds.get(0).id.equals("silva")
                && node.sourceIds.get(1).id.equals("ncbi"))
                term.qid = node.sourceIds.get(1);
            else
                term.qid = node.sourceIds.get(0);
        }
        if (!node.isHidden()) ++term.quality;
        terminals.put(node.id, term);
        return term;
    }

    public static Comparator<Terminal> betterQuality = new Comparator<Terminal>() {
            public int compare(Terminal a, Terminal b) {
                return b.quality - a.quality;
            }
        };

    public String toString() {
        return String.format("[%s %s=%s]", this.name, this.id, this.qid);
    }

}
