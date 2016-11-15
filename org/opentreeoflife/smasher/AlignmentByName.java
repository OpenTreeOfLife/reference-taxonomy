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

        Alignment old = null;
        AlignmentByName neu2 = null;

        if (debug >= 1) {
            Alignment basis = new Alignment(this);    // copy ad-hoc mappings
            System.out.format("| Basis: %s mappings\n", basis.size());
            old = new ComplicatedAlignment(basis);
            old.align();
            if (debug >= 2) {
                Alignment old2 = new ComplicatedAlignment(basis);
                old2.align();
                old2.compareAlignments(old, "comparing old to old");
                neu2 = new AlignmentByName(basis);
            }
        }

        this.alignByName();

        if (debug >= 1) {
            this.compareAlignments(old, "comparing old to new");
            if (debug >= 2) {
                neu2.alignByName();
                neu2.compareAlignments(this, "comparing new to new");
            }
        }
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
    Answer alignTaxon(Taxon node) {
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

    // Alignment - new method

    public Answer findAlignment(Taxon node) {
        List<Answer> lg = new ArrayList<Answer>();

        Map<Taxon, String> candidateMap = getCandidates(node);
        Set<Taxon> initialCandidates = new HashSet<Taxon>(candidateMap.keySet());
        Set<Taxon> candidates = initialCandidates;

        if (candidates.size() == 0) {
            node.markEvent("no candidates");
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

        Answer anyAnswer = null;
        Taxon anyCandidate = null;  // kludge for by-elimination
        Answer result = null;
        int count = 0;  // number of candidates that have the max value

        // Second loop variable: candidates

        for (Heuristic criterion : criteria) {
            List<Answer> answers = new ArrayList<Answer>(candidates.size());
            int max = -100;
            for (Taxon cand : candidates) {
                Answer a = criterion.assess(node, cand);
                // if (a.subject != null) lg.add(a);
                answers.add(a); // in parallel with candidates
                if (a.value > max) {
                    max = a.value;
                    count = 1;
                    anyAnswer = a;
                    anyCandidate = cand;
                } else if (a.value == max)
                    count++;
            }

            // count is positive

            // If negative, no point in going further.
            if (max < Answer.DUNNO) {
                result = anyAnswer; // no
                break;
            }

            // If unique and affirmative, seize it.
            if (count == 1 && max > Answer.DUNNO) {
                result = anyAnswer; // yes
                break;
            }

            if (count < candidates.size()) {
                // This criterion eliminated some candidates.
                target.markEvent(criterion.informative);

                // Winnow the candidate set for the next heuristic.
                // Iterate over candidates and answers in parallel.
                Set<Taxon> winners = new HashSet<Taxon>();
                Iterator<Answer> aiter = answers.iterator();
                for (Taxon cand : candidates) {
                    Answer a = aiter.next();
                    if (a.value == max) {
                        winners.add(cand);
                    } else
                        if (a.subject == null)
                            lg.add(Answer.noinfo(node, cand, "noinfo", null));
                        else
                            lg.add(a);
                }
                candidates = winners;
                // at this point, count == candidates.size()
            }

            // Loop: Try the next criterion.
        }

        if (result == null) {
            // Finished the loop, ended up with noinfo or with ambiguous yes.
            if (count == 1) {
                // Singleton - convert noinfo to Yes - there was only one
                // candidate in the first place
                if (initialCandidates.size() > 1)
                    result = Answer.yes(node, anyCandidate, "by-elimination", null);
                else
                    result = Answer.yes(node, anyCandidate, "single-option", null);
            } else if (!node.hasChildren())
                // Ambiguous.  Avoid creating yet another homonym.  
                result = Answer.noinfo(node, null, "ambiguous-tip", null);
            else
                result = Answer.noinfo(node, null, "ambiguous-internal", null);
            lg.add(result);
        }
        // Decide after the fact whether the dance was interesting enough to log
        if (initialCandidates.size() > 1 ||
            result.isNo() ||
            target.eventLogger.namesOfInterest.contains(node.name))
            target.eventLogger.log(lg);
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
