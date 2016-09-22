package org.opentreeoflife.taxa;

// Values for 'answer'
//	 3	 good match - to the point of being uninteresting
//	 2	 yes  - some evidence in favor, maybe some evidence against
//	 1	 weak yes  - evidence from name only
//	 0	 no information
//	-1	 weak no - some evidence against
//	-2	  (not used)
//	-3	 no brainer - gotta be different

// Subject is in source taxonomy, target is in union taxonomy

public class Answer {
	public Taxon subject, target;					// The question is: Where should subject be mapped?
	public int value;					// YES, NO, etc.
	public String reason;
	public String witness = null;
	//gate c14

    private static final boolean alwaysLog = false;

	public Answer(Taxon subject, Taxon target, int value, String reason, String witness) {
        if (subject != null &&
            !(subject.taxonomy instanceof SourceTaxonomy))
            throw new RuntimeException(String.format("Subject %s of new Answer is not in a source taxonomy",
                                                     subject));
		this.subject = subject;
        this.target = target;
		this.value = value;
		this.reason = reason;
		this.witness = witness;
        if (alwaysLog)
            this.maybeLog(this.getEventLogger());
	}

    // Tally this answer, and if it's interesting enough, log it
    public boolean maybeLog() {
        if (alwaysLog)
            // has already been logged, don't repeat
            return false;
        return maybeLog(this.getEventLogger());
    }

    // Used usually when tax is a union taxonomy
    public boolean maybeLog(Taxonomy tax) {
        if (alwaysLog)
            // has already been logged, probably
            if (this.getEventLogger() != null)
                // except when it hasn't
                return false;
        return maybeLog(tax.eventLogger);
    }
    
    private boolean maybeLog(EventLogger eventLogger) {
        if (eventLogger == null)
            return false;
        else
            return eventLogger.maybeLog(this);
    }

    // Find the appropriate event logger
    private EventLogger getEventLogger() {
        if (this.target != null
            && this.target.taxonomy.eventLogger != null)
            return this.target.taxonomy.eventLogger;
        else if (this.subject != null
                 && this.subject.taxonomy.eventLogger != null)
            return this.subject.taxonomy.eventLogger;
        else
            return null;
    }

    private Answer() {
        this.subject = null;
        this.target = null;
        this.value = DUNNO;
        this.reason = "no-info";
    }

	static final int HECK_YES = 3;
	static final int YES = 2;
	static final int WEAK_YES = 1;
    public
	static final int DUNNO = 0;
	static final int WEAK_NO = -1;
	static final int NO = -2;
    public
	static final int HECK_NO = -3;

    public boolean isYes() { return value > DUNNO; }
    public boolean isNo() { return value < DUNNO; }

	public static Answer heckYes(Taxon subject, Taxon target, String reason, String witness) { // Uninteresting
		return new Answer(subject, target, HECK_YES, reason, witness);
	}

	public static Answer yes(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, YES, reason, witness);
	}

	public static Answer weakYes(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, WEAK_YES, reason, witness);
	}

	public static Answer noinfo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, DUNNO, reason, witness);
	}

	public static Answer weakNo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, WEAK_NO, reason, witness);
	}

	public static Answer no(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, NO, reason, witness);
	}

	public static Answer heckNo(Taxon subject, Taxon target, String reason, String witness) {
		return new Answer(subject, target, HECK_NO, reason, witness);
	}

	public static Answer NOINFO = new Answer();

	// Does this determination warrant the display of the log entries
	// for this name?
	public boolean isInteresting() {
		return (this.value < HECK_YES) && (this.value > HECK_NO) && (this.value != DUNNO);
	}

	// Cf. smasher dumpLog()
	public String dump() {
		return String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s",
                             (this.subject != null ? this.subject.name : ""),
                             (this.subject != null ? 
                              this.subject.getQualifiedId().toString() :
                              ""),
                             (this.value > DUNNO ?
                              "=>" :
                              (this.value < DUNNO ? "not=>" : "-")),
                             (this.target != null ? this.target.name : ""),
                             (this.target == null ? "-" : this.target.id),
                             this.reason,
                             (this.witness == null ? "" : this.witness) );
	}


    public String toString() {
        return String.format("(%s %s)", this.value, this.reason);
    }
}
