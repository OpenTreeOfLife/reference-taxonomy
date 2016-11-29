/*
  The goal here is to be able to unify source nodes with target nodes.

  The unification is done by Taxon.unifyWith and has the effect of
  setting the 'mapped' field of the node.

  */


// How do we know with high confidence that a given pair of taxon
// references are coreferences?

// The goal is to check coreference across taxonomies, but any good rule
// will work *within* a single taxonomy.  That gives us one way to test.

// TBD: should take same list as input.


package org.opentreeoflife.smasher;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Rank;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;
import org.opentreeoflife.taxa.TaxonMap;
import org.opentreeoflife.taxa.Synonym;

public class Alignment implements TaxonMap {

	public Taxonomy source;
    public Taxonomy target;

    private Map<Taxon, Answer> mappings = new HashMap<Taxon, Answer>();
    private Map<Taxon, Taxon> comap = new HashMap<Taxon, Taxon>();
    private HashSet<Taxon> lumped = new HashSet<Taxon>();
    // TBD private Map<Taxon, Answer> sourceMrcas;

    public boolean internalp = false;

    Alignment(Taxonomy source, Taxonomy target) {
        this.source = source;
        this.target = target;
        start();
    }

    // clone an alignment, for comparison
    Alignment(Alignment a) {
        this.source = a.source;
        this.target = a.target;
        this.mappings = a.copyMappings();
        start();
    }

    public Taxon get(Taxon node) { // for TaxonMap interface
        return this.getTaxon(node);
    }
             

    public Set<Taxon> keySet() {
        return mappings.keySet();
    }

    private void start() {
        target.eventLogger.resetEvents();
        this.reset();          // depths, brackets
    }

    public Answer getAnswer(Taxon node) {
        return mappings.get(node);
    }

    public void setAnswer(Taxon node, Answer answer) {
        if (answer.target != null && answer.target.prunedp)
            System.err.format("** Pruned taxon found as mapping target: %s -> %s\n",
                              node, answer.target);
        else {
            Taxon have = getTaxon(node);
            if (have != null && have != answer.target)
                System.err.format("** Node %s is already aligned to %s, can't align it to %s\n",
                                  node, have, answer.target);
            else {
                if (answer.isYes()) {
                    Taxon rev = comap.get(answer.target);
                    if (rev != null) {
                        // Prefer bigger node
                        int z = node.count() - rev.count();
                        if (z == 0)
                            z = rev.compareTo(node);
                        if (z != 0) {
                            lumped.add(answer.target);
                            Answer a;
                            if (z < 0) {
                                // rev before node - keep rev, lump node
                                a = Answer.noinfo(node, answer.target, "lumped", rev.name);
                                mappings.put(node, a);
                            } else {
                                // rev before node - set node, lump rev
                                a = Answer.noinfo(rev, answer.target, "lumped", node.name);
                                mappings.put(rev, a);
                                mappings.put(node, answer);
                                comap.put(answer.target, node);
                            }
                            a.maybeLog();
                        }
                    } else {
                        mappings.put(node, answer);
                        comap.put(answer.target, node);
                    }
                } else
                    mappings.put(node, answer);
            }
        }
    }

    public Taxon getTaxon(Taxon node) {
        Answer a = mappings.get(node);
        if (a == null) return null;
        else if (a.isYes()) return a.target;
        else return null;
    }

    public Taxon invert(Taxon unode) {
        if (lumped.contains(unode))
            return null;
        else
            return comap.get(unode);
    }

    public final void align() {
        System.out.format("--- Aligning %s to %s ---\n", source.getTag(), target.getTag());
        this.reallyAlign();     // calls this.assignBrackets();

        target.eventLogger.eventsReport("| ");

        // Report on how well the merge went.
        this.alignmentReport();
    }

    // Should be overridden
    void reallyAlign() {
        alignQuasiTips();
    }

    Map<Taxon, Answer> copyMappings() {
        Map<Taxon, Answer> m = new HashMap<Taxon, Answer>();
        for (Taxon t : mappings.keySet())
            m.put(t, mappings.get(t));
        return m;
    }

    int size() {
        return this.mappings.size();
    }

    // ----- Aligning individual nodes -----

	// unode is a preexisting node in this taxonomy.

	public void alignWith(Taxon node, Taxon unode, String reason) {
        try {
            Answer answer = Answer.yes(node, unode, reason, null);
            this.alignWith(node, unode, answer);
            answer.maybeLog();
        } catch (Exception e) {
            System.err.format("** Exception in alignWith %s %s\n", node, unode);
            e.printStackTrace();
        }
    }

    // Set the 'mapped' property of this node, carefully
	public void alignWith(Taxon node, Taxon unode, Answer answer) {
		if (getTaxon(node) == unode) return; // redundant
        if (!(unode.taxonomy == this.target)) {
            System.err.format("** Alignment target %s is not in the target taxonomy\n", node);
            Taxon.backtrace();
        } else if (!(node.taxonomy == this.source)) {
            System.err.format("** Alignment source %s is not in the source taxonomy\n", unode);
            Taxon.backtrace();
        } else if (node.noMrca() != unode.noMrca()) {
            System.err.format("** attempt to unify forest %s with non-forest %s\n",
                              node, unode);
            Taxon.backtrace();
        } else if (getTaxon(node) != null) {
			// Shouldn't happen - assigning a single source taxon to two
			//	different target taxa
			if (node.report("Already assigned to node in target:", unode))
				Taxon.backtrace();
		} else if (unode.prunedp) {
            System.err.format("** attempt to map %s to pruned node %s\n",
                              node, unode);
            Taxon.backtrace();
		} else
            this.setAnswer(node, answer);
    }

	public boolean same(Taxon node1, Taxon node2) {
		return sameness(node1, node2, true, true);
	}

	public boolean sameness(Taxon node, Taxon unode, boolean whether, boolean setp) {
        if (node == null || unode == null) return false;
        if (node.taxonomy != source) {
            System.err.format("** node1 %s not in source taxonomy\n", node);
            return false;
        }
        if (unode.taxonomy != target) {
            System.err.format("** node2 %s not in target taxonomy\n", unode);
            return false;
        }
        // start logging this name
        if (node.name != null && target.eventLogger != null)
            target.eventLogger.namesOfInterest.add(node.name);
		if (whether) {			// same
			if (getTaxon(node) != null) {
				if (getTaxon(node) != unode) {
					System.err.format("** Attempt to unify distinct taxa: %s\n", node);
                    return false;
                } else
                    return true;
			}
            if (setp) {
                this.alignWith(node, unode, "same/curated");
                return true;
            } else return false;
		} else {				// notSame - no longer used.
            System.err.format("** notSame is no longer supported: %s\n", node);
            return false;
		}
	}


	// What was the fate of each of the nodes in this source taxonomy?

	void alignmentReport() {

        int total = 0;
        int nonamematch = 0;
        int prevented = 0;
        int corroborated = 0;

        // Could do a breakdown of matches and nonmatches by reason

        for (Taxon node : source.taxa()) {
            ++total;
            if (target.lookup(node.name) == null)
                ++nonamematch;
            else if (getTaxon(node) == null)
                ++prevented;
            else
                ++corroborated;
        }

        System.out.println("| Of " + total + " nodes in " + source.getTag() + ": " +
                           (total-nonamematch) + " with name in common, of which " + 
                           corroborated + " matched with existing, " + 
                           prevented + " blocked");
    }

    // this = after, other = before

    void compareAlignments(Alignment other, String greeting) {

        System.out.format("| (%s)\n", greeting);

        // Compare this mapping to other mapping
        int count = 0, gained = 0, lost = 0, changed = 0;
        for (Taxon node : source.taxa()) {
            Answer ol = other.getAnswer(node); // old
            Answer nu = this.getAnswer(node);     // new
            if (ol != null || nu != null) {
                if (ol == null) ol = Answer.no(node, null, "compare/no-old", null);
                if (nu == null) nu = Answer.no(node, null, "compare/no-new", null); // shouldn't happen

                if (ol.isYes()) {
                    if (nu.isYes()) {
                        ++count;
                        if (ol.target == nu.target)
                            continue;
                        else
                            ++changed;
                    } else
                        ++lost;
                } else {
                    if (nu.isYes()) {
                        // New but no old
                        ++count;
                        ++gained;
                        continue;
                    } else
                        // Neither new nor old
                        continue;
                }
                // A case that's interesting enough to report.
                if (!nu.reason.equals("same/primary-name")) //too many
                    System.out.format("+ %s new-%s> %s %s, old-%s> %s %s\n",
                                      node,
                                      (nu.isYes() ? "" : "/"), nu.target, nu.reason,
                                      (ol.isYes() ? "" : "/"), ol.target, ol.reason);
            }
        }
        System.out.format("| Before %s, gained %s, lost %s, changed %s, after %s\n",
                          other.count(), gained, lost, changed, count);
    }

    int count() {
        int n = 0;
        for (Taxon node : mappings.keySet())
            if (mappings.get(node).isYes()) ++n;
        return n;
    }

    // Map source taxon to nearest available target taxon
    public Taxon bridge(Taxon node) {
        Taxon unode;
        while ((unode = getTaxon(node)) == null) {
            if (node.parent == null)
                // No bridge!  Shouldn't happen
                return node;
            node = node.parent;
        }
        return unode;
    }

    // --- to be called from jython ---
    public Taxon image(Taxon node) {
        return this.getTaxon(node);
    }

    // Trying to phase out the following - we shouldn't be caching information
    // in Taxons

	void reset() {
        this.source.reset();    // depths
        this.target.reset();
    }


    // Generic by-name alignment logic.

    void alignQuasiTips() {
        for (Taxon root : source.roots())
            alignQuasiTips(root);
        System.out.format("| %s quasi-tips\n", tipMappings);
    }

    int tipMappings = 0;

    // return true iff any quasi-tip under node
    boolean alignQuasiTips(Taxon node) {
        boolean anyMapped = false;
        for (Taxon child : node.getChildren()) {
            if (alignQuasiTips(child))
                anyMapped = true;
        }
        if (anyMapped)
            return true;
        // Potential quasi-tip
        Answer answer = alignTaxon(node, heuristics);
        if (answer != null && answer.isYes()) {
            tipMappings++;
            return true;
        }
        return false;
    }

    public Answer alignTaxon(Taxon node) {
        return alignTaxon(node, heuristics);
    }

    Answer alignTaxon(Taxon node, Heuristic[] heuristics) {
        Answer a = getAnswer(node);
        if (a == null) {
            a = findAlignment(node, heuristics);
            if (a != null) {
                if (a.isYes()) {
                    alignWith(node, a.target, a);
                    // logDivisions(a);  -- too much noise now.
                } else
                    this.setAnswer(node, a);
            }
        }
        return a;
    }

    void logDivisions(Answer a) {
        Taxon node = a.subject;
        Taxon unode = a.target;
        Taxon xdiv = node.getDivision();
        Taxon ydiv = unode.getDivision();
        if (xdiv != ydiv && xdiv != null && ydiv != null && !xdiv.noMrca() && !ydiv.noMrca())
            System.out.format("* Weakly divided: %s %s|%s %s because %s\n",
                              node, xdiv.name, ydiv.name, unode, a.reason);
    }

    // Find answer for a single node, and put it in table
    public Answer findAlignment(Taxon node) {
        return findAlignment(node, heuristics);
    }

    // Alignment - single source taxon -> target taxon or 'no'

    public Answer findAlignment(Taxon node, Heuristic[] heuristics) {
        List<Answer> lg = new ArrayList<Answer>();

        Map<Taxon, String> candidateMap = getCandidates(node);
        Set<Taxon> initialCandidates = new HashSet<Taxon>(candidateMap.keySet());
        Set<Taxon> candidates = initialCandidates;

        if (candidates.size() == 0) {
            return null;
        } else if (candidates.size() > 1)
            target.eventLogger.namesOfInterest.add(node.name);
        for (Taxon candidate : candidates) {
            if (candidate == null)
                System.err.format("** No taxon !? %s\n", node);
            /*
            Answer start = Answer.noinfo(node, candidate, "candidate", candidateMap.get(candidate));
            lg.add(start);
            */
        }

        Taxon anyCandidate = null;  // kludge for by-elimination
        int count = 0;  // number of candidates that have the max value
        String reason = null;
        int score = -100;

        // Second loop variable: candidates

        for (Heuristic heuristic : heuristics) {
            List<Answer> answers = new ArrayList<Answer>(candidates.size());
            score = -100;
            for (Taxon cand : candidates) {
                Answer a = heuristic.assess(node, cand);
                // if (a.subject != null) lg.add(a);
                answers.add(a); // in parallel with candidates
                if (a.value > score) {
                    score = a.value;
                    count = 1;
                    anyCandidate = cand;
                } else if (a.value == score) {
                    count++;
                    if (cand.compareTo(anyCandidate) < 0)
                        anyCandidate = cand;
                }
            }

            // count is positive

            if (count < candidates.size()) {
                // This heuristic eliminated some candidates.
                target.markEvent(heuristic.informative);

                // Winnow the candidate set for the next heuristic.
                // Iterate over candidates and answers in parallel.
                Set<Taxon> winners = new HashSet<Taxon>();
                Iterator<Answer> aiter = answers.iterator();
                for (Taxon cand : candidates) {
                    Answer a = aiter.next();
                    if (a.value == score) {
                        winners.add(cand);
                    } else
                        if (a.subject == null)
                            lg.add(Answer.noinfo(node, cand, heuristic.toString(), null));
                        else
                            lg.add(a);
                }
                candidates = winners;
                // at this point, count == candidates.size()

                reason = heuristic.toString();
            }

            // If negative, or unique positive, no point in going further.
            if (score < Answer.DUNNO || (count == 1 && score > Answer.DUNNO))
                break;

            // Loop: Try the next heuristic.
        }

        Answer result;
        if (score < Answer.DUNNO) {
            if (reason == null)
                reason = "rejected";
        } else if (count == 1) {
            // reason should be nonnull iff at least one heuristic made a discrimination.
            if (reason == null) {
                if (score > Answer.DUNNO)
                    reason = "confirmed";
                else
                    reason = "by elimination";
            }
            if (score == Answer.DUNNO) score = Answer.WEAK_YES; // turn noinfo into yes
        } else {                                                // ambiguous
            score = Answer.DUNNO; // turn yes into noinfo
            if (!node.hasChildren())
                reason = "ambiguous tip";
            else
                reason = "ambiguous internal";
        }
        result = new Answer(node, anyCandidate, score, reason,
                            String.format("%s/%s %s",
                                          count,
                                          initialCandidates.size(),
                                          candidateMap.get(anyCandidate)));

        lg.add(result);

        // Decide after the fact whether the dance was interesting enough to log
        if (initialCandidates.size() > 1 ||
            result.isNo() ||
            target.eventLogger.namesOfInterest.contains(node.name))
            target.eventLogger.log(lg);
        return result;
    }

    // Given a source taxonomy return, return a set of target
    // taxonomy nodes that it might plausibly match.
    // The string tells you the synonym path by which the candidate was found...

    Map<Taxon, String> getCandidates(Taxon node) {
        Map<Taxon, String> candidateMap = new HashMap<Taxon, String>();
        getCandidatesViaName(node, "A", candidateMap); // 'accepted'
        for (Synonym syn : node.getSynonyms())
            getCandidatesViaName(syn, "S", candidateMap);
        return candidateMap;
    }

    Map<Taxon, String> getCandidatesViaName(Node node, String mode, Map<Taxon, String> candidateMap) {
        if (node.name != null) {
            Collection<Node> unodes = target.lookup(node.name);
            if (unodes != null)
                for (Node unode : unodes)
                    addCandidate(candidateMap, unode, mode);
        }
        // Add nodes that share a qid with this one (for idsource alignment)
        if (node.sourceIds != null) {
            String mode2 = mode + "I";
            for (QualifiedId qid : node.sourceIds) {
                Node unode = target.lookupQid(qid);
                if (unode != null) {
                    Taxon utaxon = unode.taxon();
                    if (!utaxon.prunedp) {
                        String xmode = mode2 + ((utaxon.sourceIds != null &&
                                                 utaxon.sourceIds.get(0).equals(qid)) ?
                                                "I" : "J");
                        addCandidate(candidateMap, unode, xmode);
                    }
                }
                mode2 = mode + "J";
            }
        }
        return candidateMap;
    }

    void addCandidate(Map<Taxon, String> candidateMap, Node unode, String mode) {
        Taxon utaxon = unode.taxon();

        mode = mode + (unode instanceof Taxon ? "C" : "S");

        String modes = candidateMap.get(utaxon);
        if (modes == null)
            modes = mode;
        else
            modes = modes + " " + mode;
        candidateMap.put(utaxon, modes);
    }

	static Heuristic[] heuristics = {
		Heuristic.disjointDivisions,      // fail if disjoint divisions
        Heuristic.disparateRanks,         // fail if incompatible ranks
		Heuristic.lineage,                // prefer shared lineage
        Heuristic.sameDivisionPreferred,  // prefer same division
        Heuristic.byPrimaryName,          // prefer same name
		Heuristic.sameSourceId,           // prefer same source id
		Heuristic.anySourceId,            // prefer candidate with any source id the same
    };
}
