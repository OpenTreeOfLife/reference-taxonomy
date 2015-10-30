/**
   A binary relation with both indexes.
*/

package org.opentreeoflife.registry;

import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.QualifiedId;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;

public class Correspondence {

    // Parameters
    static final int nsamples = 2;
    static final int nexclusions = 1;

    // State
    public Registry registry;
    public Taxonomy taxonomy;
    Map<QualifiedId, Taxon> qidIndex;
    Map<Registration, List<Taxon>> registrationToTaxa = new HashMap<Registration, List<Taxon>>();
    Map<Taxon, List<Registration>> taxonToRegistration = new HashMap<Taxon, List<Registration>>();

    // Constructor
    public Correspondence(Registry registry, Taxonomy taxonomy) {
        this.registry = registry;
        this.taxonomy = taxonomy;
        qidIndex = makeQidIndex(taxonomy);
    }

    // Methods
    public void add(Registration s, Taxon g) {
        List<Taxon> gs = registrationToTaxa.get(s);
        if (gs == null) {
            gs = new ArrayList<Taxon>();
            registrationToTaxa.put(s, gs);
        }
        gs.add(g);
        List<Registration> ss = taxonToRegistration.get(g);
        if (ss == null) {
            ss = new ArrayList<Registration>();
            taxonToRegistration.put(g, ss);
        }
        ss.add(s);
    }

    public Iterable registration() {
        return registrationToTaxa.keySet();
    }

    public Iterable taxa() {
        return taxonToRegistration.keySet();
    }

    public void put(Registration s, List<Taxon> gs) {
        // registrationToTaxa.put(s, gs);
        for (Taxon g : gs)
            add(s, g);
    }

    public List<Taxon> get(Registration s) {
        return registrationToTaxa.get(s);
    }

    public List<Registration> coget(Taxon g) {
        return taxonToRegistration.get(g);
    }

    public int size() { return registrationToTaxa.size(); }
    public int cosize() { return taxonToRegistration.size(); }

    // Find the compatible taxon (pathologically: taxa), if any, 
    // for every registration in the registry.
    // With this in hand, use chooseTaxon() to resolve a registration to a taxon,
    // and chooseRegistration() to find the registration assigned to a taxon.

    void assign() {
        clearEvents();
        Map<Registration, Taxon> byMetadata = resolveAllByMetadata(taxonomy, qidIndex);
        Correspondence result = this;

        // 1. Resolve sample/exclusion registrations using metadata (see chooseTaxon)
        // 2. Resolve metadata-only registrations using metadata

        for (Registration reg : registry.allRegistrations()) {
            if (reg.samples != null || reg.exclusions != null) {
                if (reg.samples != null)
                    for (Registration s : reg.samples)
                        result.byMetadataOnly(s, byMetadata);
                if (reg.exclusions != null)
                    for (Registration s : reg.exclusions)
                        result.byMetadataOnly(s, byMetadata);
            } else 
                result.byMetadataOnly(reg, byMetadata);
        }

        // 3. Resolve registrations that have topological constraints (using metadata to disambiguate)

        for (Registration reg : registry.allRegistrations()) {
            if (reg.samples != null || reg.exclusions != null) {
                try {
                    List<Taxon> taxa = findByTopology(reg, byMetadata, result);
                    if (taxa != null) {
                        result.put(reg, taxa);
                        if (taxa.size() == 1)
                            event("resolved uniquely by topology", reg, taxa.get(0));
                        else
                            event("resolved ambiguously by topology", reg, taxa);
                    } else
                        event("topological constraints are inconsistent", reg);
                } catch (ResolutionFailure fail) {
                    event("punt on topology, try metadata", reg, fail.registration);
                    if (result.byMetadataOnly(reg, byMetadata))
                        event("metadata worked where topology failed", reg);
                }
            }
        }
        reportEvents();
    }

    private boolean byMetadataOnly(Registration reg, // little utility for above
                                   Map<Registration, Taxon> byMetadata) {
        if (this.get(reg) == null) {
            Taxon node = byMetadata.get(reg);
            if (node != null) {
                event("resolved uniquely by metadata", reg, node);
                this.add(reg, node);
                return true;
            } else {
                event("registration with no metadata-compatible nodes", reg);
                return false;
            }
        } else
            return false;
    }

    // Resolve every taxon using metadata, for all registrations.
    // Do we really need to reify resolveAllByMetadata as a Map ? - no,
    // could be done dynamically, but that would require a special
    // object to carry the taxonomy and qidIndex

    Map<Registration, Taxon> resolveAllByMetadata(Taxonomy taxonomy, Map<QualifiedId, Taxon> qidIndex) {
        Map<Registration, Taxon> byMetadata = new HashMap<Registration, Taxon>();
        for (Registration reg : registry.allRegistrations()) {
            Taxon node = resolveByMetadata(reg, taxonomy, qidIndex);
            if (node != null)
                byMetadata.put(reg, node);
        }
        return byMetadata;
    }

    // Find taxa, using metadata, for one registation.
    // (Not sure how to divide labor between this method and chooseRegistration.)
    // This is how the samples and exclusions get mapped to nodes.
    // This gets only the "best metadata matches" for the registration.

    Taxon resolveByMetadata(Registration reg, Taxonomy taxonomy, Map<QualifiedId, Taxon> qidIndex) {
        if (reg.qid != null) {
            // Collisions have already been winnowed out
            Taxon probe = qidIndex.get(reg.qid);
            if (probe != null)
                return probe;
        }
        if (reg.ottid != null) {
            Taxon probe = taxonomy.lookupId(reg.ottid);
            if (probe != null)
                return probe;
        }
        if (reg.name != null) {
            List<Taxon> nodes = taxonomy.lookup(reg.name);
            if (nodes != null) {
                // First consider exact name matches
                Taxon result = null;
                for (Taxon node : nodes)
                    if (node.name.equals(reg.name)) {
                        if (result != null) {
                            interesting("resolution failed due to name ambiguity", reg);
                            return null; // ambiguous
                        }
                        result = node;
                    }
                if (result != null) return result;
                // Next consider reg.name as a synonym
                if (nodes.size() > 1) {
                    interesting("resolution failed due to name mismatch", reg);
                    return null;
                }
                return nodes.get(0);
            }
        }
        return null;
    }

    // Index the taxonomy by qualified id (source taxonomy reference)

    public Map<QualifiedId, Taxon> makeQidIndex(Taxonomy taxonomy) {
        Map <QualifiedId, Taxon> qidIndex = new HashMap<QualifiedId, Taxon>();
        List<QualifiedId> ambiguous = new ArrayList<QualifiedId>();
        for (Taxon node : taxonomy) {
            if (node.sourceIds != null)
                for (QualifiedId qid : node.sourceIds) {
                    if (qidIndex.get(qid) != null)
                        // Should deal with silva/ncbi situation here
                        ambiguous.add(qid);
                    else
                        qidIndex.put(qid, node);
                }
        }
        for (QualifiedId qid : ambiguous)
            qidIndex.remove(qid);
        return qidIndex;
    }

    // Find all the taxa that match a single Registration.
    // When there is topological ambiguity, attempt to reduce it using clues from metadata.
    // If a sample or exclusion fails to resolve, throw a ResolutionFailure exception.

    List<Taxon> findByTopology(Registration reg,
                               Map<Registration, Taxon> byMetadata,
                               Correspondence registrationToTaxa)
    throws ResolutionFailure
    {
        List<Taxon> path = constrainedPath(reg, registrationToTaxa);
        if (path != null) {
            if (path.size() > 1) {
                // Ambiguous.  Attempt to clear it up using metadata... this may be the wrong place to do this
                Taxon node = byMetadata.get(reg);
                if (node != null && path.contains(node)) {
                    List<Taxon> candidates = new ArrayList<Taxon>();
                    candidates.add(node);
                    return candidates;
                }
                // fall through
            }
            return path;
        } else
            return null;
    }

    // Find compatible taxa, using topological constraints, for one registration.
    // Return value is {m, a} where a is an ancestor of m.  Compatible taxa are
    // m and its ancestors up to be not including a.

    List<Taxon> constrainedPath(Registration reg,
                                Correspondence registrationToTaxa)
    throws ResolutionFailure
    {
        ResolutionFailure fail = null;
        List<Taxon> result;
        Taxon m = null;
        for (Registration s : reg.samples) {
            Taxon snode = chooseTaxon(s, registrationToTaxa);
            if (snode != null) {
                if (m == null)
                    m = snode;
                else
                    m = m.mrca(snode);
            } else
                // Couldn't find sample in taxonomy
                fail = new ResolutionFailure(s);
        }
        if (m != null && !m.noMrca()) {
            if (reg.exclusions != null && reg.exclusions.size() > 0) {
                Taxon a = null;
                for (Registration s : reg.exclusions) {
                    Taxon snode = chooseTaxon(s, registrationToTaxa);
                    if (snode != null) {
                        Taxon b = m.mrca(snode);
                        if (a == null)
                            a = b;
                        else if (b.descendsFrom(a))
                            a = b;
                    } else
                        // Couldn't find exclusion in taxonomy
                        fail = new ResolutionFailure(s);
                }
                if (a != null && !a.noMrca()) {
                    if (!m.descendsFrom(a))
                        // We can sometimes prove there is no such node
                        // even if not all samples and exclusions resolve.
                        return null;
                    // Copy the path from m to a out of the taxonomy
                    result = new ArrayList<Taxon>();
                    for (Taxon n = m; n != a; n = n.parent)
                        result.add(n);
                } else result = null;
            } else {
                // This case should only happen for the root of the tree
                result = new ArrayList<Taxon>();
                Taxon a = m;
                while (!a.isRoot()) {
                    result.add(a);
                    a = a.parent;
                }
                result.add(a);              // add the root
            }
        } else
            // Should only happen in fail case (always at least one sample)
            result = null;
        if (fail != null) throw fail;
        return result;
    }

    class ResolutionFailure extends Exception {
        Registration registration;
        ResolutionFailure(Registration reg) {
            registration = reg;
        }
    }

    // Resolution: choose one taxon for the given registration, based
    // on compatibility relation.

    public Taxon chooseTaxon(Registration reg,
                             Correspondence registrationToTaxa) {
        List<Taxon> seen = registrationToTaxa.get(reg);
        if (seen != null && seen.size() == 1) // ** sort by goodness of match ??
            return seen.get(0);
        else
            return null;
    }

    // ------------------------------------------------------------
    // Assignment - inverse of resolution

    // Create registrations for any taxa that don't have them
    // uniquely.  Performs side effects on the taxon to registration
    // correspondence.
    // call this 'extend' ???

    void extend(Taxonomy tax,
                Correspondence registrationToTaxa) {
        clearEvents();
        Map<Taxon, Registration> needExclusions = new HashMap<Taxon, Registration>();
        int before = registrationToTaxa.size();
        // 1. Register all unregistered tips
        // 2. Register all unregistered internal nodes
        for (Taxon root : tax.roots())
            registerSubtree(root, registrationToTaxa, needExclusions);
        for (Taxon node : needExclusions.keySet())
            addExclusions(node, needExclusions, registrationToTaxa);
        reportEvents();
        checkRegistrations(tax, registrationToTaxa);
    }

    void checkRegistrations(Taxonomy tax, Correspondence registrationToTaxa) {
        int losers = 0;
        Set<Registration> seen = new HashSet<Registration>();
        for (Taxon node : tax) {
            Registration reg = chooseRegistration(node, registrationToTaxa);
            if (reg == null) {
                interesting("no registration for node", node);
                ++losers;
            } else if (seen.contains(reg)) {
                interesting("registration also assigned to a node other than this one", node);
                ++losers;
            } else
                seen.add(reg);
        }
        if (losers == 0)
            System.out.format("Success: unique registrations are assigned to all nodes\n");
        else
            System.out.format("Failure: %s nodes do not have registrations assigned\n", losers);
    }

    // Returns a registration that would uniquely select the given node.
    // Side effect: create registrations, as needed, for the node and its descendants,
    // and store in the registrationToTaxa table.

    Registration registerSubtree(Taxon node,
                                 Correspondence registrationToTaxa,
                                 Map<Taxon, Registration> needExclusions) {
        // ? Exclude nodes that have names that are homonyms ?
        List<List<Registration>> childSamples = new ArrayList<List<Registration>>();
        // Preorder traversal
        Registration probe = chooseRegistration(node, registrationToTaxa);
        if (node.children == null) {
            // Look to see if we already have a (tip?) registration for this node
            // ****
            if (probe != null) {
                event("found registration assigned to tip", node, probe);
                return probe;
            }
            Registration reg = registry.registrationForTaxon(node);
            if (!node.isHidden()) ++reg.quality;
            // Tip.  Add to correspondence so that it can be used as a sample.
            registrationToTaxa.add(reg, node);
            Registration check = chooseRegistration(node, registrationToTaxa);
            if (check != reg)
                // this is not good.
                interesting("** registrationToTaxa.add(%s, %s) failed (bug)", reg, node, check);
            else
                event("new registration created for tip", node, reg);
            return reg;
        } else if (probe != null) {
            event("found registration assigned to internal node", node, probe);
            for (Taxon child : node.children)
                registerSubtree(child, registrationToTaxa, needExclusions);
            return probe;
        } else {
            // Persistent side effect to list of children in that node!
            // (the order will be assumed when finding exclusions, as well)
            Collections.sort(node.children, betterSampleSource);
            for (Taxon child : node.children) {
                Registration reg = registerSubtree(child, registrationToTaxa, needExclusions);
                if (reg.samples != null)
                    childSamples.add(reg.samples);
                else {
                    List<Registration> single = new ArrayList<Registration>();
                    single.add(reg);
                    childSamples.add(single);
                }
            }
            // Novel taxon, or multiple registrations are compatible
            // with it.  Make up a new registration.
            List<Registration> samples = new ArrayList<Registration>();
            for (int i = 0; ; ++i) {
                boolean gotOne = false;
                for (List<Registration> regs : childSamples) {
                    if (i < regs.size()) {
                        gotOne = true;
                        samples.add(regs.get(i));
                        if (samples.size() >= nsamples) break;
                    }
                }
                if (samples.size() >= nsamples) break;
                if (!gotOne) break;
            }
            // there will always be at least one sample (because this is an internal node)
            Registration reg = registry.registrationForTaxon(node);
            Collections.sort(samples, betterQuality);
            reg.samples = samples;
            for (Registration s : samples)
                if (s.quality > 0) ++reg.quality;
            // To do on second pass: exclusions
            needExclusions.put(node, reg);
            event("new registration created for internal node", node, reg);
            return reg;
        }
    }

    public static Comparator<Registration> betterQuality = new Comparator<Registration>() {
            public int compare(Registration a, Registration b) {
                return b.quality - a.quality;
            }
        };

    // Second pass after registrations have been created with inclusions

    void addExclusions(Taxon node,
                       Map<Taxon, Registration> needExclusions,
                       Correspondence registrationToTaxa) {
        // Add the new registration to those already there for this taxon
        Registration reg = needExclusions.get(node);
        if (reg.samples != null) {
            for (Taxon n = node; n.parent != null; n = n.parent) {
                Taxon p = n.parent;
                if (p.children.size() >= 2) {
                    Taxon sib = p.children.get(0);
                    if (sib == n) sib = p.children.get(1);
                    if (sib.isHidden() && !node.isHidden())
                        interesting("hidden exclusion", node, reg);
                    Registration sib_reg = needExclusions.get(sib);
                    if (sib_reg == null) sib_reg = chooseRegistration(sib, registrationToTaxa);
                    if (sib_reg != null) {
                        Registration ex = sib_reg;
                        if (sib_reg.samples != null) ex = sib_reg.samples.get(0);
                        if (reg.exclusions == null)
                            reg.exclusions = new ArrayList<Registration>(1);
                        reg.exclusions.add(ex);
                        break;
                    }
                }
            }
        }
        if (reg.exclusions == null)
            System.out.format("No exclusions for %s <-> %s\n", node, reg);
        else
            for (Registration s : reg.exclusions)
                if (s.quality > 0) ++reg.quality;

        registrationToTaxa.add(reg, node);
        Registration check = chooseRegistration(node, registrationToTaxa);
        if (check == null)
            // this is not good.
            interesting("** registrationToTaxa.add failed #2 (bug)", reg, node);
    }

    // Returns negative if a is a better place than b to get samples.
    // Prefer larger taxa to smaller ones.
    // Prefer less homonymic names.
    // Prefer larger number of source taxonomies.
    // Try to ignore anything below species rank.

    public Comparator<Taxon> betterSampleSource = new Comparator<Taxon>() {
            public int compare(Taxon a, Taxon b) {
                int avis = (a.isHidden() ? 1 : 0);
                int bvis = (b.isHidden() ? 1 : 0);
                if (avis != bvis) return avis - bvis;
                int asize = ((a.rank != null && a.rank.equals("species")) ? 1 : a.count());
                int bsize = ((b.rank != null && b.rank.equals("species")) ? 1 : b.count());
                if (asize != bsize) return asize - bsize;
                int ahom = (a.name != null ? a.taxonomy.lookup(a.name).size() : 2);
                int bhom = (b.name != null ? b.taxonomy.lookup(b.name).size() : 2);
                if (ahom != bhom) return bhom - ahom;
                int asrc = (a.sourceIds != null ? a.sourceIds.size() : 0);
                int bsrc = (b.sourceIds != null ? b.sourceIds.size() : 0);
                return asrc - bsrc;
            }
        };

    // Assignment: Choose one best (most specific) registration from
    // among several that resolve to the node.
    // This code looks all wrong to me.

    public Registration chooseRegistration(Taxon node,
                                           Correspondence registrationToTaxa) {
        List<Registration> regs = registrationToTaxa.coget(node);
        if (regs == null) return null;
        if (regs.size() == 1) return regs.get(0);

        Registration answer = null;

        if (node.sourceIds != null) {
            // Check primary source id (e.g. ncbi:1234)
            QualifiedId nodeQid = node.sourceIds.get(0);
            for (Registration reg : regs) {
                List<Taxon> probe = registrationToTaxa.get(reg);
                if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                    // keep going to see if there's more than one
                    // in which case try using metadata to filter
                    if (nodeQid.equals(reg.qid)) {
                        if (answer != null) {
                            System.out.format("ambiguous (1): %s %s %s qid=%s\n", node, reg, answer, nodeQid);
                            answer = null;
                            break;
                        } else
                            answer = reg;
                    }
                }
            }
            if (answer != null) return answer;

            // Check other source ids (e.g. match gbid:5678 to [ncbi:1234, gbif:5678]
            for (Registration reg : regs) {
                List<Taxon> probe = registrationToTaxa.get(reg);
                if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                    if (node.sourceIds.contains(reg.qid)) {
                        if (answer != null) {
                            System.out.format("ambiguous (2): %s %s %s qid=%s\n", node, reg, answer, reg.qid);
                            answer = null;
                            break;
                        } else
                            answer = reg;
                    }
                }
            }
            if (answer != null) return answer;
        }

        // Check OTT id
        // Nameless nodes do not have OTT ids; and id present is fake
        if (node.id != null && node.name != null) {
            for (Registration reg : regs) {
                List<Taxon> probe = registrationToTaxa.get(reg);
                if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                    if (node.id.equals(reg.ottid)) {
                        if (answer != null) {
                            System.out.format("ambiguous (3): %s has same id %s as both %s and %s\n", node, node.id, reg, answer);
                            answer = null;
                            break;
                        } else
                            answer = reg;
                    }
                }
            }
            if (answer != null) return answer;
        }

        // Check primary name
        if (node.name != null) {
            for (Registration reg : regs) {
                List<Taxon> probe = registrationToTaxa.get(reg);
                if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                    if (node.name.equals(reg.name)) {
                        if (answer != null) {
                            System.out.format("ambiguous (4): %s -> %s or %s\n", node, reg, answer);
                            answer = null;
                            break;
                        } else
                            answer = reg;
                    }
                }
            }
            if (answer != null) return answer;

            // Consider looking at synonyms - node.taxonomy.lookup(reg.name).contains(node)
        }

        // None of the candidate nodes have metadata, or all metadata is ambiguous.
        interesting("node equally metadata-compatible with multiple registrations", node, regs.get(0), regs.get(1));
        return null;      // FAIL
    }

    // Explains why reg is not assigned to node / reg does not resolve to node

    public String explain(Taxon node, Registration reg, Correspondence registrationToTaxa) {
        if (reg.samples != null)
            for (Registration sreg : reg.samples) {
                if (registrationToTaxa.get(sreg) == null)
                    return String.format("no taxon compatible with sample %s", sreg);
                Taxon sample = chooseTaxon(sreg, registrationToTaxa);
                if (sample == null)
                    return String.format("no taxon chosen for sample %s", sreg);
                if (!sample.descendsFrom(node))
                    return String.format("sample %s does not descend from %s", sample, node);
            }
        if (reg.exclusions != null)
            for (Registration xreg : reg.exclusions) {
                if (registrationToTaxa.get(xreg) == null)
                    return String.format("no taxon compatible with exclusion %s", xreg);
                Taxon exclusion = chooseTaxon(xreg, registrationToTaxa);
                if (exclusion == null)
                    return String.format("no taxon chosen for exclusion %s", xreg);
                if (exclusion.descendsFrom(node))
                    return String.format("exclusion %s descends from %s", exclusion, node);
            }

        // Should look at metadata...

        if (registrationToTaxa.get(reg) == null)
            return "no taxa for registration";

        if (!registrationToTaxa.get(reg).contains(node))
            return "taxon not compatible with registration";

        if (chooseRegistration(node, registrationToTaxa) == null)
            return "chooseRegistration failed, probably ambiguous";

        if (registrationToTaxa.coget(node) == null)
            return "no registrations for taxon";

        if (!registrationToTaxa.coget(node).contains(reg))
            return "taxon not compatible with registration"; // redundant

        if (chooseTaxon(reg, registrationToTaxa) == null)
            return "chooseTaxon failed";

        return "looks OK";
    }

    // instrumentation

    Map<String, Integer> eventCounts = new HashMap<String, Integer>();

    void event(String tag, Object... args) {
        countEvent(tag, args);
        }

    int countEvent(String tag, Object[] args) {
		Integer probe = eventCounts.get(tag);
		int count;
		if (probe == null)
			count = 0;
		else
			count = probe;
        ++count;
		eventCounts.put(tag, count);
        return count;
    }

    void interesting(String tag, Object... args) {
        int count = countEvent(tag, args);
		if (count <= 5) {
			System.out.format("| %s", tag);
            for (Object arg : args)
                System.out.format(" %s", arg);
            System.out.println();
		}
    }

    void clearEvents() {
        eventCounts = new HashMap<String, Integer>();
    }

    void reportEvents() {
		for (String tag : eventCounts.keySet())
			System.out.format(". %s: %s\n", tag, eventCounts.get(tag));
        clearEvents();
	}

}
