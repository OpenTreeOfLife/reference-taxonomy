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
import java.io.PrintStream;
import java.io.IOException;

import org.opentreeoflife.taxa.Node;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Synonym;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.SourceTaxonomy;
import org.opentreeoflife.taxa.Answer;
import org.opentreeoflife.taxa.QualifiedId;

public class AlignmentByName extends Alignment {

    AlignmentByName(SourceTaxonomy source, Taxonomy target) {
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

        this.newAlign();

        if (debug >= 1) {
            this.compareAlignments(old, "comparing old to new");
            if (debug >= 2) {
                neu2.newAlign();
                neu2.compareAlignments(this, "comparing new to new");
            }
        }
    }

    public void newAlign() {
        for (Taxon node : source.taxa()) {
            if (getTaxon(node) == null) {
                Answer a = newAlign(node);
                if (a != null) {
                    if (a.isYes())
                        alignWith(node, a.target, a);
                    else
                        this.setAnswer(node, a);
                }
            }
        }
    }

    // Alignment - new method

    public Answer newAlign(Taxon node) {
        Map<Taxon, String> candidateMap = getCandidates(node);
        Set<Taxon> candidates = new HashSet<Taxon>(candidateMap.keySet());

        if (candidates.size() == 0)
            return null;
        if (candidates.size() > 1)
            target.eventlogger.namesOfInterest.add(node.name);
        for (Taxon candidate : candidates)
            Answer.noinfo(node, candidate, "candidate", candidateMap.get(candidate));

        int max;
        Answer anyAnswer = null;
        Taxon anyCandidate = null;
        String deciding = "no-choice";
        for (Criterion criterion : Criterion.criteria) {
            Set<Taxon> winners = new HashSet<Taxon>();
            max = -100;
            for (Taxon unode : candidates) {
                Answer a = criterion.assess(node, unode);
                a.maybeLog(target);
                if (a.value >= max) {
                    if (a.value > max) {
                        max = a.value;
                        winners = new HashSet<Taxon>();
                    }
                    winners.add(unode);
                    anyCandidate = unode;
                    anyAnswer = a;
                }
            }
            // Accept a yes answer
            if (max > Answer.DUNNO && winners.size() == 1)
                return anyAnswer;

            // Accept a no answer
            if (max < Answer.DUNNO) {
                if (winners.size() == 1)
                    return anyAnswer;
                else {
                    Answer no = new Answer(node, null, max, "all-candidates-rejected", null);
                    no.maybeLog(target);
                    return no;
                }
            }

            // NOINFO
            if (winners.size() == 1 && candidates.size() > 1)
                deciding = criterion.toString();

            candidates = winners;
        }
        // No yeses, no nos
        if (candidates.size() == 1) {
            Answer yes = Answer.yes(node, anyCandidate, deciding, null);
            yes.maybeLog(target);
            return yes;
        } else {

            // Dump candidates & mode info
            System.out.format("? %s -> ", node);
            for (Taxon c : candidates)
                System.err.format("** %s[%s]", c, candidateMap.get(c));
            System.out.println();

            // avoid creating yet another homonym
            Answer no = Answer.no(node, null, "ambiguous", null);
            no.maybeLog(target);
            return no;
        }
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

}
