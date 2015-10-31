/*
  TBD:
  - treat infraspecific taxa specially
  - figure out what to do with unresolvable samples/exclusions
  - deal with root of tree
  - do 'best metadata match' in chooseTaxon as well?
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

    public int size() {
        return registrations.size();
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

    Registration newRegistrationForTaxon(Taxon node) {
        // (reuse ott id as registration id when possible ?)
        Registration reg = newRegistration();
        // Set metadata from node
        reg.name = node.name;
        if (node.sourceIds != null) {
            if (node.sourceIds.size() >= 2
                && node.sourceIds.get(0).id.equals("silva")
                && node.sourceIds.get(1).id.equals("ncbi"))
                reg.qid = node.sourceIds.get(1);
            else
                reg.qid = node.sourceIds.get(0);
        }
        if (node.name != null)
            reg.ottid = node.id;
        if (!node.isHidden()) ++reg.quality;
        return reg;
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
                Collections.sort(reg.samples, betterQuality);
            if (reg.exclusions != null)
                Collections.sort(reg.exclusions, betterQuality);
        }
        return registry;
    }

    public static Comparator<Registration> betterQuality = new Comparator<Registration>() {
            public int compare(Registration a, Registration b) {
                return b.quality - a.quality;
            }
        };

    // write registry

    public void dump(String filename) throws IOException {
        PrintStream out = Taxonomy.openw(filename);
        for (Registration reg : allRegistrations())
            reg.dump(out);
        out.close();
    }

    // entry from shell

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

class Registration {

    int id;                     // Positive
    String name = null;
    QualifiedId qid = null;
    String ottid = null;
    List<Registration> samples = null;
    List<Registration> exclusions = null;
    int quality = 0;

    Registration(int id) {
        this.id = id;
    }

    void dump(PrintStream out) throws IOException {
        out.format("%s,%s,%s,%s",
                   this.id,
                   (this.name == null ? "" : this.name),
                   (this.qid == null ? "" : this.qid.toString()),
                   (this.ottid == null ? "" : this.ottid));
        if (samples != null)
            for (Registration reg : samples)
                out.format(",%s", reg.id);
        if (exclusions != null)
            for (Registration reg : exclusions)
                out.format(",%s", -reg.id);
        out.println();
    }

    static Registration parse(String row, Registry registry) {
        String[] cells = row.split(",");
        int id = Integer.parseInt(cells[0]);
        String name = cells[1];
        String qid = cells[2];
        // TBD: error if we've already seen a row for this id
        Registration entry = registry.getRegistration(id);
        if (name.length() > 0) entry.name = name;
        if (qid.length() > 0) entry.qid = new QualifiedId(qid);
        entry.ottid = cells[3];
        List<Registration> samples = new ArrayList<Registration>();
        List<Registration> exclusions = new ArrayList<Registration>();
        for (int i = 4; i < cells.length; ++i)
            if (cells[i].length() != 0) {
                int n = Integer.parseInt(cells[i]);
                if (n < 0)
                    exclusions.add(registry.getRegistration(-n));
                else
                    samples.add(registry.getRegistration(n));
            }
        entry.samples = samples;
        entry.exclusions = exclusions;
        return entry;
    }

    public String toString() {
        if (this.samples != null) {
            return String.format("[%s %s %s ...]", this.id, this.name, this.ottid);
        } else
            return String.format("[%s %s %s]", this.id, this.name, this.ottid);
    }

}
