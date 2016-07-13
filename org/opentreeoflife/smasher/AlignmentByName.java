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

    int cutoff = Answer.HECK_NO;

    // Alignment - new method

    public Answer findAlignment(Taxon node) {
        List<Answer> lg = new ArrayList<Answer>();

        Map<Taxon, String> candidateMap = getCandidates(node);
        Set<Taxon> initialCandidates = new HashSet<Taxon>(candidateMap.keySet());
        Set<Taxon> candidates = initialCandidates;

        if (candidates.size() == 0)
            return null;
        if (candidates.size() > 1)
            target.eventLogger.namesOfInterest.add(node.name);
        for (Taxon candidate : candidates) {
                if (candidate == null)
                    System.err.format("** No taxon !? %s\n", node);

            Answer start = Answer.noinfo(node, candidate, "candidate", candidateMap.get(candidate));
            lg.add(start);
        }

        int max = -100;                // maximum "value" among Answers
        Answer anyAnswer = null;
        Taxon anyCandidate = null;
        for (Criterion criterion : criteria) {
            List<Answer> answers = new ArrayList<Answer>(candidates.size());
            max = -100;
            int count = 0;
            for (Taxon unode : candidates) {
                if (unode == null)
                    System.err.format("** No taxon 2 !? %s\n", node);

                Answer a = criterion.assess(node, unode);
                answers.add(a);
                if (a.value > max) {
                    max = a.value;
                    count = 1;
                    anyAnswer = a;
                    anyCandidate = unode;
                } else if (a.value == max)
                    count++;
            }
            if (count < candidates.size()) {
                // Informative
                for (Answer a : answers)
                    if (a.subject != null)
                        lg.add(a);

                if (max <= cutoff)
                    break;

                Set<Taxon> winners = new HashSet<Taxon>();
                Iterator<Answer> aiter = answers.iterator();
                for (Taxon unode : candidates) {
                    Answer a = aiter.next();
                    if (a.value == max) {
                        winners.add(unode);
                    }
                }
                candidates = winners;
            }
        }

        // Deal with a no answer
        if (max <= cutoff) {
            anyAnswer = new Answer(node, null, max, "all-candidates-rejected", null);
            lg.add(anyAnswer);
        }

        // Accept a unique yes or noinfo answer
        else {
            if (candidates.size() > 1) {
                if (false) {
                    // Dump candidates & mode info
                    System.out.format("? %s ->", node);
                    for (Taxon c : candidates)
                        System.err.format(" %s[%s]", c, candidateMap.get(c));
                    System.out.println();
                }

                // avoid creating yet another homonym
                anyAnswer = Answer.noinfo(node, null, "ambiguous", null);
                lg.add(anyAnswer);
            } else if (max <= Answer.DUNNO) {
                // By process of elimination
                anyAnswer = Answer.yes(node, anyCandidate, "elimination", null);
                lg.add(anyAnswer);
            }
        }
        // Decide after the fact whether it was interesting enough to log
        if (initialCandidates.size() > 1 || candidates.size() != 1 || anyAnswer.isNo())
            target.eventLogger.log(lg);
        return anyAnswer;
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
