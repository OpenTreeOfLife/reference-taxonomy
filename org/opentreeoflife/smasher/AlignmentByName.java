package org.opentreeoflife.smasher;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.io.PrintStream;
import java.io.IOException;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Synonym;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

public class AlignmentByName extends Alignment {

    public AlignmentByName(Taxonomy source, Taxonomy target) {
        super(source, target);
    }

    AlignmentByName(Alignment a) {
        super(a);
    }

    private int debug = 0;

    // this is pretty gross. needs to be moved & rewritten.
    // method is invoked from python.

    public void reallyAlign() {
        this.alignByName();
    }

    // Treat all taxa equally - tips same as internal - random order

    public void alignByName() {
        if (EXPERIMENTALP) {
            this.tryThisOut();
            return;
        } else {
            assignBrackets();
            for (Taxon node : source.taxa())
                alignTaxon(node);
        }
    }

    // Map unambiguous tips first(?), then retry ambiguous(?), then internal nodes

    int tipMappings = 0;

    void tryThisOut() {
        for (Taxon root : source.roots())
            alignTipsOnly(root);
        System.out.format("| %s quasi-tips\n", tipMappings);
        this.USE_ALIGNMENT = true;
        assignBrackets();
        for (Taxon root : source.roots())
            alignInternal(root);
    }

    // return true iff any quasi-tip under node
    boolean alignTipsOnly(Taxon node) {
        boolean anyMapped = false;
        for (Taxon child : node.getChildren()) {
            if (alignTipsOnly(child))
                anyMapped = true;
        }
        if (anyMapped)
            return true;
        // Potential quasi-tip
        Answer answer = alignTaxon(node);
        if (answer != null && answer.isYes()) {
            tipMappings++;
            return true;
        }
        return false;
    }

    // align internal nodes (above quasi-tips)
    void alignInternal(Taxon node) {
        for (Taxon child : node.getChildren())
            alignInternal(child);
        // Cf. computeLubs
        alignTaxon(node);
    }

    // Find answer for a single node, and put it in table
    public Answer alignTaxon(Taxon node) {
        Answer a = getAnswer(node);
        if (a == null) {
            a = findAlignment(node);
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

    // Alignment - single source taxon -> target taxon or 'no'

    public Answer findAlignment(Taxon node) {
        Map<Taxon, String> candidateMap = getCandidates(node);
        List<Taxon> initialCandidates = new ArrayList<Taxon>(candidateMap.keySet());
        if (initialCandidates.size() == 0)
            return null;

        Collections.sort(initialCandidates);

        // For logging.  Ugly names, redundant data structures, needs cleaning up.
        Map<Taxon, Answer> answerx = new HashMap<Taxon, Answer>();
        List<Taxon> order = new ArrayList<Taxon>();
        for (Taxon cand : initialCandidates) order.add(cand);

        // Loop state
        Taxon anyCandidate = null;  // kludge for by-elimination
        int count = 0;  // number of candidates that have the max value
        Answer result = null;
        Heuristic decider = null;
        int score = -100;
        ArrayList<Taxon> candidates = initialCandidates;

        // Precondition: at least one candidate

        for (Heuristic heuristic : criteria) {
            List<Answer> answers = new ArrayList<Answer>(candidates.size());
            score = -100;
            for (Taxon cand : candidates) {
                Answer a = heuristic.assess(node, cand);
                if (a.target != cand) a = new Answer(node, cand, a.value, a.reason, a.witness);
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

            List<Taxon> winners = new ArrayList<Taxon>();
            Iterator<Answer> aiter = answers.iterator();
            for (Taxon cand : candidates) {
                Answer a = aiter.next();
                if (a.value == score && score >= Answer.DUNNO)
                    winners.add(cand);
                else
                    order.add(cand);
            }

            if (winners.size() == 0) {
                result = new Answer(node, null, score, "rejected", null);
                order.add(winners);
                break;
            }

            if (score > Answer.DUNNO && winners.size() == 1) {
                result = new Answer(node, anyCandidate, score, heuristic.toString(), null);
                order.add(winners);
                break;
            }

            candidates = winners;
        }

        if (result == null) {
            // fell through with all noinfo or multiple yes

            if (candidates.size() == 1)
                result = Answer.yes(node, anyCandidate, "by elimination", null);
            else {
                String r = node.hasChildren() ? "ambiguous internal" : "ambiguous tip";
                result = Answer.noinfo(node, anyCandidate, r, null);
            }
            order.add(winners);
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
                    answerx.put(cand, a); // can overwrite
                    if (a.value == score && score >= Answer.DUNNO)
                        winners.add(cand);
                    else
                        order.add(cand);
                }
                candidates = winners;
                decider = heuristic;
                // at this point, count == candidates.size()
            }

            // Tangled logic.  Needs rewriting

            // If narrowed to a single yes, no point in going further.
            if (score > Answer.DUNNO && count == 1) {
                result = answerx.get(anyCandidate);
                if (result == null) {
                    String r = (decider != null) ? decider.toString() : "confirmed";
                    result = new Answer(node, anyCandidate, score, r, null);
                }
                break;
            }

            // If all candidates are disqualified, no point in going further.
            if (score < Answer.DUNNO) {
                result = new Answer(node, anyCandidate, score, "rejected", null);
                break;
            }

            // Loop: Try the next heuristic.
        }

        if (result == null) {
            if (count == 1) {
                result = Answer.yes(node, anyCandidate, "by elimination", null);
            } else {
                String r = node.hasChildren() ? "ambiguous internal" : "ambiguous tip";
                result = Answer.noinfo(node, anyCandidate, r, null);
            }
        }

        // Decide after the fact whether the dance was interesting enough to log
        if (initialCandidates.size() > 1 ||
            result.isNo() ||
            target.eventLogger.namesOfInterest.contains(node.name)) {

            target.eventLogger.namesOfInterest.add(node.name);

            List<Answer> tolog = new ArrayList<Answer>();
            for (Taxon cand : order) {
                Answer a = answerx.get(cand);
                if (a == null)
                    // elimination or ambiguous
                    a = Answer.noinfo(node, cand, "noinfo/left-over", null);
                else if (a.subject == null)
                    a = Answer.noinfo(node, cand, "noinfo", null);
                tolog.add(a);
            }
            target.eventLogger.log(tolog);
        }
        return result;
    }

    private static boolean allowSynonymSynonymMatches = false;

    // Given a source taxonomy return, return a set of target
    // taxonomy nodes that it might plausibly match

    Map<Taxon, String> getCandidates(Taxon node) {
        Map<Taxon, String> candidateMap = new HashMap<Taxon, String>();
        getCandidatesViaName(node, "C", candidateMap); // canonical
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

	static Heuristic[] criteria = {
		Heuristic.division,               // fail if disjoint divisions
        Heuristic.ranks,                  // fail if incompatible ranks
		Heuristic.lineage,                // prefer shared lineage
        Heuristic.subsumption,            // prefer shared membership
        Heuristic.sameDivisionPreferred,  // prefer same division
        Heuristic.byPrimaryName,          // prefer same name
		Heuristic.sameSourceId,           // prefer same source id
		Heuristic.anySourceId,            // prefer candidate with any source id the same
    };

}
