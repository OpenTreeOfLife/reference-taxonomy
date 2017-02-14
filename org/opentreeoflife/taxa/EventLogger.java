package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.io.IOException;
import java.io.PrintStream;

public class EventLogger {

	private Map<String, List<Answer>> sublogs = new HashMap<String, List<Answer>>();
	private List<Answer> currentLog = null;

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
                answer.isInteresting() ||
                sublogs.get(abject.name) != null) {
                // Log it for printing after we get ids
                this.log(answer);
            }
        }
    }

    public void log(Answer answer) {
        List<Answer> answers = new ArrayList<Answer>(1);
        answers.add(answer);
        log(answers);
    }


    // markEvent has already been called at this point.

    // Log a sequence of answers.  The sequence contains many names.
    // Find or create an appropriate sublog.  Choose a sublog
    // associated with one of the names occuring in the answer
    // sequence, if there is one.  Otherwise, create a sublog for the
    // first name occurring in the answer sequence, and use it.

    public void log(Collection<Answer> answers) {
        List<Answer> lg = null;
        for (Answer answer : answers) {
            lg = getLog(answer);
            if (lg != null) break;
        }
        if (lg == null) {
            String name = null;
            for (Answer answer : answers) {
                name = nodeName(answer.target);
                if (name != null) break;
                name = nodeName(answer.subject);
                if (name != null) break;
            }
            if (name != null) {
                lg = new ArrayList<Answer>(1);
                this.sublogs.put(name, lg);
            }
        }
        if (lg != null)
            for (Answer answer : answers)
                addAnswer(lg, answer);
    }

    private void addAnswer(List<Answer> lg, Answer answer) {
        lg.add(answer);
        String name = nodeName(answer.target);
        if (name != null)
            if (this.sublogs.get(name) == null)
                this.sublogs.put(name, lg);
        name = nodeName(answer.subject);
        if (this.sublogs.get(name) == null)
            this.sublogs.put(name, lg);
    }

    // Obtain a log appropriate to this answer, if there is one
	private List<Answer> getLog(Answer answer) {
        List<Answer> lg = getLog(answer.target);
        if (lg == null)
            lg = getLog(answer.subject);
        return lg;
    }

    // Obtain a log appropriate to this node, if there is one
    private List<Answer> getLog(Taxon node) {
        if (node == null) return null;
        String name = nodeName(node);
        List<Answer> lg = this.sublogs.get(name);
        if (lg == null) {
            for (Synonym syn : node.getSynonyms()) {
                lg = this.sublogs.get(syn.name);
                if (lg != null)
                    break;
            }
        }
        return lg;
    }

    private String nodeName(Taxon node) {
        if (node == null) return null;
        if (node.name == null) return null;
        if (node.name.equals("environmental samples")) return null; //3606 cohomonyms
        return node.name;
    }



	// Called on union taxonomy
	// scrutinize is a set of names of especial interest (e.g. deprecated)

	public void dumpLog(String filename, Set<String> scrutinize) throws IOException {
		PrintStream out = Taxonomy.openw(filename);

		// Strongylidae	nem:3600	yes	same-parent/direct	3600	Strongyloidea	false
		out.println(Answer.header);

		// this.sublogs is indexed by taxon name
		if (false)
			for (List<Answer> answers : this.sublogs.values()) {
				boolean interestingp = false;
				for (Answer answer : answers)
					if (answer.isInteresting()) {interestingp = true; break;}
				if (interestingp)
					for (Answer answer : answers)
						out.println(answer.dump());
			}
        System.out.format("| Names to log: %s\n", scrutinize.size());
        for (String name : scrutinize) {
            List<Answer> answers = this.sublogs.get(name);
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

    // Final summary report
	public void eventsReport(String prefix) {        // was printStats
        Collections.sort(this.eventStatNames);
		for (String tag : this.eventStatNames) {
			System.out.println(prefix + tag + ": " + this.eventStats.get(tag));
		}
		this.eventStats = new HashMap<String, Long>();
		this.eventStatNames = new ArrayList<String>();
	}

	public void resetEvents() {         // was resetStats
        eventsReport(". ");
	}

}
