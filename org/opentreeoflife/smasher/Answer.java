package org.opentreeoflife.smasher;


// Values for 'answer'
//	 3	 good match - to the point of being uninteresting
//	 2	 yes  - some evidence in favor, maybe some evidence against
//	 1	 weak yes  - evidence from name only
//	 0	 no information
//	-1	 weak no - some evidence against
//	-2	  (not used)
//	-3	 no brainer - gotta be different

// Subject is in source taxonomy, target is in union taxonomy

class Answer {
	Taxon subject, target;					// The question is: Where should subject be mapped?
	int value;					// YES, NO, etc.
	String reason;
	String witness = null;
	//gate c14
	Answer(Taxon subject, Taxon target, int value, String reason, String witness) {
        if (subject == null)
            throw new RuntimeException("Subject of new Answer is null");
        if (!(subject.taxonomy instanceof SourceTaxonomy))
            throw new RuntimeException("Subject of new Answer is not in a source taxonomy");
        if (target != null && (target.taxonomy instanceof SourceTaxonomy))
            throw new RuntimeException("Target of new Answer is not in a union taxonomy");
		this.subject = subject; this.target = target;
		this.value = value;
		this.reason = reason;
		this.witness = witness;
	}

    private EventLogger eventlogger() {
        if (this.subject.taxonomy.eventlogger != null)
            return this.subject.taxonomy.eventlogger;
        else if (this.target != null
                 && this.target.taxonomy.eventlogger != null)
            return this.target.taxonomy.eventlogger;
        else
            return null;
    }

    // Tally this answer, and if it's interesting enough, log it
    boolean maybeLog() {
        EventLogger e = this.eventlogger();
        if (e != null)
            return maybeLog(e);
        else
            return false;
    }

    boolean maybeLog(Taxonomy tax) {
        if (tax.eventlogger != null)
            return maybeLog(tax.eventlogger);
        else
            return false;
    }
    
    boolean maybeLog(EventLogger eventlogger) {
        boolean infirstfew = eventlogger.markEvent(this.reason);
        // markEvent even if name is null
        if (subject.name != null) {
            if (infirstfew)
                eventlogger.namesOfInterest.add(subject.name); // watch it play out
            if (eventlogger.namesOfInterest.contains(subject.name) || infirstfew || this.subject.count() > 20000) {
                if (true)
                    // Log it for printing after we get ids
                    eventlogger.log(this);
                else 
                    // Print it immediately
                    System.out.println(this.dump());
                return true;
            }
        }
        return infirstfew;
    }

    void log() {
        EventLogger e = this.eventlogger();
        if (e != null) e.log(this);
    }

    void log(Taxonomy tax) {
        if (tax.eventlogger != null)
            tax.eventlogger.log(this);
        else
            this.log();
    }

    Answer() {
        this.subject = null;
        this.target = null;
        this.value = DUNNO;
        this.reason = "no-info";
    }

	static final int HECK_YES = 3;
	static final int YES = 2;
	static final int WEAK_YES = 1;
	static final int DUNNO = 0;
	static final int WEAK_NO = -1;
	static final int NO = -2;
	static final int HECK_NO = -3;

    boolean isYes() { return value > 0; }

	static Answer heckYes(Taxon subject, Taxon target, String reason, String witness) { // Uninteresting
		return new Answer(subject, target, HECK_YES, reason, witness);
	}

	static Answer yes(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, YES, reason, witness);
	}

	static Answer weakYes(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, WEAK_YES, reason, witness);
	}

	static Answer noinfo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, DUNNO, reason, witness);
	}

	static Answer weakNo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, WEAK_NO, reason, witness);
	}

	static Answer no(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, NO, reason, witness);
	}

	static Answer heckNo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, HECK_NO, reason, witness);
	}

	static Answer NOINFO = new Answer();

	// Does this determination warrant the display of the log entries
	// for this name?
	boolean isInteresting() {
		return (this.value < HECK_YES) && (this.value > HECK_NO) && (this.value != DUNNO);
	}

	// Cf. dumpLog()
	String dump() {
		return
			(((this.target != null ? this.target.name :
			   this.subject.name))
			 + "\t" +

			 this.subject.getQualifiedId().toString() + "\t" +

			 (this.value > DUNNO ?
			  "=>" :
			  (this.value < DUNNO ? "not=>" : "-")) + "\t" +

			 (this.target == null ? "?" : this.target.id) + "\t" +

			 this.reason + "\t" +

			 (this.witness == null ? "" : this.witness) );
	}
}
