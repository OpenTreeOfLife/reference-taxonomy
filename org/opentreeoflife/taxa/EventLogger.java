package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Collections;
import java.io.IOException;
import java.io.PrintStream;

public class EventLogger {

	Map<String, List<Answer>> logs = new HashMap<String, List<Answer>>();

    // this gets filled in by jython code ...
    public Set<String> namesOfInterest = new HashSet<String>();

	// See Answer.maybeLog().
    // markEvent has already been called.

	void log(Answer answer) {
		String name = null;
		if (answer.target != null) name = answer.target.name;
		if (name == null) name = answer.subject.name;	 //could be synonym
		if (name == null) return;					 // Hmmph.	No name to log it under.
		List<Answer> lg = this.logs.get(name);
		if (lg == null) {
			// Kludge! Why not other names as well?
			if (name.equals("environmental samples")) return; //3606 cohomonyms
			lg = new ArrayList<Answer>(1);
			this.logs.put(name, lg);
		}
		lg.add(answer);
	}

	// Called on union taxonomy
	// scrutinize is a set of names of especial interest (e.g. deprecated)

	public void dumpLog(String filename, Set<String> scrutinize) throws IOException {
		PrintStream out = Taxonomy.openw(filename);

		// Strongylidae	nem:3600	yes	same-parent/direct	3600	Strongyloidea	false
		out.println("name\t" +
					"source_qualified_id\t" +
					"parity\t" +
					"union_uid\t" +
					"reason\t" +
					"witness\t");

		// this.logs is indexed by taxon name
		if (false)
			for (List<Answer> answers : this.logs.values()) {
				boolean interestingp = false;
				for (Answer answer : answers)
					if (answer.isInteresting()) {interestingp = true; break;}
				if (interestingp)
					for (Answer answer : answers)
						out.println(answer.dump());
			}
        System.out.format("| Names to log: %s\n", scrutinize.size());
        for (String name : scrutinize) {
            List<Answer> answers = this.logs.get(name);
            if (answers != null)
                for (Answer answer : answers)
                    out.println(answer.dump());
            else
                // usually a silly synonym
                // System.out.format("No logging info for name %s\n", name);
                ;
        }

		out.close();
	}

    // Event logging
    // Eventually replace the static event logger in Taxon.java

	Map<String, Long> eventStats = new HashMap<String, Long>();
	List<String> eventStatNames = new ArrayList<String>();

	boolean markEvent(String tag) { // formerly startReport
		Long probe = this.eventStats.get(tag);
		long count;
		if (probe == null) {
			this.eventStatNames.add(tag);
			count = 0;
		} else
			count = probe;
		this.eventStats.put(tag, count+(long)1);
		if (count <= 10) {
			return true;
		} else
			return false;
	}

    boolean markEvent(String tag, Taxon node) {
        // sort of a kludge
        if (node.mapped != null)
            return Answer.noinfo(node, node.mapped, tag, null).maybeLog(this);
        else if (node.lub != null)
            return Answer.noinfo(node, node.lub, tag, null).maybeLog(this);
        else
            return Answer.noinfo(node, null, tag, null).maybeLog(this);
    }

    boolean markEvent(String tag, Taxon node, Taxon unode) {
        // sort of a kludge
        return Answer.noinfo(node, unode, tag, null).maybeLog(this);
    }

    // Final report
	public void eventsReport(String prefix) {        // was printStats
        Collections.sort(this.eventStatNames);
		for (String tag : this.eventStatNames) {
			System.out.println(prefix + tag + ": " + this.eventStats.get(tag));
		}
	}

	public void resetEvents() {         // was resetStats
		this.eventStats = new HashMap<String, Long>();
		this.eventStatNames = new ArrayList();
	}

}
