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

    AlignmentByName(Taxonomy source, Taxonomy target) {
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

    boolean experimentalp = false;

    // Treat all taxa equally - tips same as internal - random order

    public void alignByName() {
        if (experimentalp) { this.tryThisOut(); return; }
        assignBrackets();
        for (Taxon node : source.taxa())
            alignNode(node);
    }

    // Map unambiguous tips first, then retry ambiguous(?), then internal nodes

    void tryThisOut() {
        assignBrackets();
        Set<Taxon> tips = new HashSet<Taxon>();
        for (Taxon root : source.roots())
            mapTipsOnly(root, tips);
        System.out.format("| %s quasi-tips\n", tips.size());
        for (Taxon root : source.roots())
            mapInternal(root, tips);
    }

    // return true iff any quasi-tip under node
    boolean mapTipsOnly(Taxon node, Set<Taxon> tips) {
        boolean anyMapped = false;
        for (Taxon child : node.getChildren()) {
            if (mapTipsOnly(child, tips))
                anyMapped = true;
        }
        if (anyMapped)
            return true;
        // Potential quasi-tip
        Answer answer = alignNode(node);
        if (answer != null && answer.isYes()) {
            tips.add(answer.target);
            return true;
        }
        return false;
    }

    // align internal nodes (above quasi-tips)
    void mapInternal(Taxon node, Set<Taxon> tips) {
        if (tips.contains(node))
            ;
        else {
            for (Taxon child : node.getChildren())
                mapInternal(child, tips);
            alignNode(node);
        }
    }

    // Find answer for a single node, and put it in table
    Answer alignNode(Taxon node) {
        if (getTaxon(node) == null) {
            Answer a = findAlignment(node);
            if (a != null) {
                if (a.isYes())
                    alignWith(node, a.target, a);
                else
                    this.setAnswer(node, a);
            }
            return a;
        } else
            return null;
    }

    int cutoff = Answer.DUNNO;

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

            Answer start = Answer.noinfo(node, candidate, "candidate", candidateMap.get(candidate));
            lg.add(start);
        }

        Answer result = null;
        for (Criterion criterion : criteria) {
            List<Answer> answers = new ArrayList<Answer>(candidates.size());
            int max = -100;
            int count = 0;
            Answer anyAnswer = null;
            Taxon anyCandidate = null;
            for (Taxon cand : candidates) {
                Answer a = criterion.assess(node, cand);
                if (a.subject != null) lg.add(a);
                answers.add(a); // in parallel with candidates
                if (a.value >= cutoff) {
                    if (a.value > max) {
                        max = a.value;
                        count = 1;
                        anyAnswer = a;
                        anyCandidate = cand;
                    } else if (a.value == max)
                        count++;
                }
            }
            // If all bad, give up.
            if (count == 0) {
                result = new Answer(node, null, max, "all-candidates-rejected", null);
                lg.add(result);
                break;
            }

            // If unique, sieze it.
            if (count == 1) {
                if (anyAnswer.target == null) {
                    result = Answer.yes(node, anyCandidate, "elimination", null);
                    lg.add(result);
                } else
                    result = anyAnswer;
                break;
            }

            // If still ambiguous, winnow and try the next criterion.
            if (count < candidates.size()) {
                // This criterion eliminated some candidates.

                // Iterate over candidates and answers in parallel.
                Set<Taxon> winners = new HashSet<Taxon>();
                Iterator<Answer> aiter = answers.iterator();
                for (Taxon cand : candidates) {
                    Answer a = aiter.next();
                    if (a.value == max) {
                        winners.add(cand);
                    }
                }
                candidates = winners;
            }
        }

        if (result == null) {
            // Ambiguity (not none or singleton)
            if (node.getChildren().size() == 0) {
                // Avoid creating yet another homonym.  
                result = Answer.noinfo(node, null, "ambiguous", null);
                lg.add(result);
            } else {
                System.out.format("** Abominable ambiguity: %s %s\n", node, candidates);
            }
        }
        // Decide after the fact whether the dance was interesting enough to log
        if (initialCandidates.size() > 1 || result.isNo())
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

	static Criterion[] criteria = {
		Criterion.division,
        Criterion.ranks,                  // separate by rank
		Criterion.lineage,
        Criterion.subsumption,
        Criterion.byPrimaryName,
		Criterion.sameSourceId,
		Criterion.anySourceId,
        Criterion.weakDivision,
    };

}
