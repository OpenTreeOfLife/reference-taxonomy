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

	private Map<String, List<Answer>> logs = new HashMap<String, List<Answer>>();

    // this gets filled in by jython code ...
    public Set<String> namesOfInterest = new HashSet<String>();

    // Log if interesting.

    public boolean maybeLog(Answer answer) {
        boolean infirstfew = this.markEvent(answer.reason);
        boolean skiptarget = false;

        if (answer.subject != null) {
            maybeLog(answer, answer.subject, infirstfew);

            // Don't log the target if it has the same name
            if (answer.target == null ||
                answer.target.name == null ||
                answer.target.name.equals(answer.subject.name))
                return infirstfew;
        }

        if (answer.target != null)
            maybeLog(answer, answer.target, infirstfew);

        return infirstfew;
    }
    
    void maybeLog(Answer answer, Taxon abject, boolean infirstfew) {
        if (abject != null && abject.name != null) {
            if (infirstfew)
                this.namesOfInterest.add(abject.name); // watch it play out
            if (this.namesOfInterest.contains(abject.name) ||
                infirstfew ||
                abject.count() > 20000 ||
                answer.isInteresting()) {
                // Log it for printing after we get ids
                this.log(answer);
            }
        }
    }

    // markEvent has already been called at this point.

	private void log(Answer answer) {
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
		out.println("target_name\t" +
                    "source_name\t" +
					"source_qualified_id\t" +
					"parity\t" +
					"target_uid\t" +
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
        if (node.taxonomy instanceof SourceTaxonomy)
            return maybeLog(Answer.noinfo(node, null, tag, null));
        else
            return maybeLog(Answer.noinfo(null, node, tag, null));
    }

    boolean markEvent(String tag, Taxon node, Taxon unode) {
        // sort of a kludge
        return maybeLog(Answer.noinfo(node, unode, tag, null));
    }

    // Final summary report
	public void eventsReport(String prefix) {        // was printStats
        Collections.sort(this.eventStatNames);
		for (String tag : this.eventStatNames) {
			System.out.println(prefix + tag + ": " + this.eventStats.get(tag));
		}
		this.eventStats = new HashMap<String, Long>();
		this.eventStatNames = new ArrayList();
	}

	public void resetEvents() {         // was resetStats
        eventsReport(". ");
	}

}
