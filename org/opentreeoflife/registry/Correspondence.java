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

    enum Status { UNSATISFIABLE, UNRESOLVED, PATH, AMBIGUOUS, OTTIDS_DIFFER, QIDS_DIFFER, NO_CANDIDATES };
    enum Clue { AMBIGUITY, PATH };

    // State
    public Registry registry;
    public Taxonomy taxonomy;
    Map<QualifiedId, Taxon> qidIndex;
    Map<Registration, Taxon> resolutionMap = new HashMap<Registration, Taxon>();
    Map<Registration, List<Taxon>> paths = new HashMap<Registration, List<Taxon>>();
    Map<Registration, Status> statuses = new HashMap<Registration, Status>();

    Map<Taxon, Registration> assignments = new HashMap<Taxon, Registration>();
    Map<Taxon, Clue> clues = new HashMap<Taxon, Clue>();

    // Constructor
    public Correspondence(Registry registry, Taxonomy taxonomy) {
        this.registry = registry;
        this.taxonomy = taxonomy;
        qidIndex = makeQidIndex(taxonomy);
    }

    void setClue(List<Taxon> taxa, Clue clue) {
        for (Taxon node : taxa)
            clues.put(node, clue);
    }

    public void setResolution(Registration reg, Taxon node) {
        Taxon prev = resolutionMap.get(reg);
        if (prev != null && prev != node)
            interesting("changing resolution (SHOULDN'T HAPPEN)", reg, prev, node);
        resolutionMap.put(reg, node);
        Registration verp = assignments.get(node);
        if (verp != null && verp != reg) {
            interesting("multiple registrations resolve to this node (probably OK)", node, verp, reg);
            if (reg.id > verp.id) // Assign the most recently registered registration
                assignments.put(node, reg);
        } else
            assignments.put(node, reg);
    }

    // Find the compatible taxon (pathologically: taxa), if any, 
    // for every registration in the registry.
    // With this in hand, use resolve() to resolve a registration to a taxon,
    // and assignedRegistration() to find the registration assigned to a taxon.

    void resolve() {
        resolve(false);
    }
    void resolve(boolean rescuep) {
        clearEvents();

        // 1. Resolve sample/exclusion registrations using metadata (see resolve)
        // 2. Resolve metadata-only registrations using metadata

        for (Registration reg : registry.allRegistrations()) {
            if (reg.samples != null) {
                for (Registration s : reg.samples)
                    byMetadataOnly(s);
                if (reg.exclusions != null)
                    for (Registration s : reg.exclusions)
                        byMetadataOnly(s);
            } else 
                byMetadataOnly(reg);
        }

        // 3. Resolve registrations that have topological constraints (using metadata to disambiguate)

        for (Registration reg : registry.allRegistrations()) {
            if (reg.samples != null)
                try {
                    List<Taxon> taxa = findByTopologyAndMaybeMetadata(reg, rescuep);
                    if (taxa != null) {
                        if (taxa.size() == 1) {
                            setResolution(reg, taxa.get(0));
                        } else {
                            paths.put(reg, taxa);
                            setClue(taxa, Clue.PATH);
                            statuses.put(reg, Status.PATH);
                        }
                    } else {
                        event("topological constraints are inconsistent", reg);
                        statuses.put(reg, Status.UNSATISFIABLE);
                    }
                } catch (ResolutionFailure fail) {
                    event("punt on topology, try metadata", reg, fail.registration);
                    if (byMetadataOnly(reg))
                        interesting("metadata worked where topology failed", reg);
                    else {
                        interesting("resolution failed due to unresolved sample(s)", reg, fail.registration);
                        statuses.put(reg, Status.UNRESOLVED);
                    }
                }
        }
        reportEvents();
        taxonomyReport();
    }

    // little utility for above
    private boolean byMetadataOnly(Registration reg) {
        if (resolutionMap.get(reg) == null) {
            Taxon node = resolveByMetadata(reg);
            if (node != null) {
                setResolution(reg, node);
                event("resolved uniquely by metadata", reg, node);
                return true;
            } else {
                event("registration with no metadata-compatible nodes", reg);
                return false;
            }
        } else
            // was computed previously
            return false;
    }

    // Find taxa, using metadata, for one registation.
    // (Not sure how to divide labor between this method and assignedRegistration.)
    // This is how the samples and exclusions get mapped to nodes.
    // This gets only the "best metadata matches" for the registration.
    // Side effect: when result is null, enter status in status table.

    Taxon resolveByMetadata(Registration reg) {
        if (reg.ottid != null) {
            Taxon probe = taxonomy.lookupId(reg.ottid);
            if (probe != null && probe.name != null)
                return probe;
        }
        if (reg.qid != null) {
            // Collisions have already been winnowed out
            Taxon probe = qidIndex.get(reg.qid);
            if (probe != null)
                if (probe.id != null && probe.name != null) {
                    // If it has an OTT, it's the wrong one
                    statuses.put(reg, Status.OTTIDS_DIFFER);
                    interesting("resolution failed, qids match but ottids don't", reg, probe);
                    return null;
                } else
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
                            statuses.put(reg, Status.AMBIGUOUS);
                            interesting("resolution failed due to name ambiguity", reg, node, result);
                            return null; // ambiguous
                        }
                        result = node;
                    }
                if (result == null) {
                    // Next consider reg.name as a synonym
                    if (nodes.size() > 1) {
                        statuses.put(reg, Status.AMBIGUOUS);
                        setClue(nodes, Clue.AMBIGUITY);
                        interesting("resolution failed due to name mismatch", reg);
                        return null;
                    }
                    result = nodes.get(0);
                }
                if (reg.ottid != null && result.id != null && result.name != null) {
                    statuses.put(reg, Status.OTTIDS_DIFFER);
                    interesting("resolution failed, names match but ottids don't", reg, result);
                    return null;
                } else if (reg.qid != null && result.sourceIds != null) {
                    statuses.put(reg, Status.QIDS_DIFFER);
                    interesting("resolution failed, names and OTT ids match but qids don't", reg, result);
                    return null;
                } else 
                    return result;
            }
        }
        if (statuses.get(reg) == null)
            statuses.put(reg, Status.NO_CANDIDATES);
        return null;
    }

    // Find all the taxa that match a single Registration.
    // When there is topological ambiguity, attempt to reduce it using clues from metadata.
    // If a sample or exclusion fails to resolve, throw a ResolutionFailure exception.

    List<Taxon> findByTopologyAndMaybeMetadata(Registration reg, boolean rescuep)
        throws ResolutionFailure
    {
        List<Taxon> path = findByTopology(reg);
        if (path != null)
            if (path.size() == 1)
                event("resolved uniquely by topology", reg, path.get(0));
            else {
                if (rescuep) {
                    // Ambiguous.  Attempt to clear it up using metadata... this may be the wrong place to do this
                    Taxon node = resolveByMetadata(reg);
                    if (node != null && path.contains(node)) {
                        List<Taxon> candidates = new ArrayList<Taxon>();
                        candidates.add(node);
                        event("resolved uniquely by topology combined with metadata", reg, node);
                        return candidates;
                    }
                }
                event("registration is ambiguous along path", reg, path);
                // fall through
            }
        return path;
    }

    // Find compatible taxa, using topological constraints, for one registration.
    // Return value is {m, a} where a is an ancestor of m.  Compatible taxa are
    // m and its ancestors up to be not including a.

    List<Taxon> findByTopology(Registration reg)
    throws ResolutionFailure
    {
        ResolutionFailure fail = null;
        List<Taxon> result;
        Taxon m = null;
        for (Registration s : reg.samples) {
            Taxon snode = resolve(s);
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
                    Taxon snode = resolve(s);
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

    public Taxon resolve(Registration reg) {
        return resolutionMap.get(reg);
    }

    // Assignment: Choose one best (most specific) registration from
    // among several that resolve to the node.
    // This code looks all wrong to me.

    public Registration assignedRegistration(Taxon node) {
        return assignments.get(node);
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
        for (QualifiedId qid : ambiguous) {
            qidIndex.remove(qid);
            interesting("ambiguous qualified id", qid);
        }
        return qidIndex;
    }

    // ------------------------------------------------------------
    // Assignment - inverse of resolution

    // Create registrations for any taxa that don't have them
    // uniquely.  Performs side effects on the taxon-to-registration
    // correspondence.

    void extend() {
        clearEvents();
        Map<Taxon, Registration> needExclusions = new HashMap<Taxon, Registration>();
        // 1. Register all unregistered tips
        // 2. Register all unregistered internal nodes
        for (Taxon root : taxonomy.roots())
            registerSubtree(root, needExclusions);
        for (Taxon node : needExclusions.keySet())
            addExclusions(node, needExclusions);
        reportEvents();
        checkRegistrations();
        taxonomyReport();
    }

    void checkRegistrations() {
        int losers = 0;
        Set<Registration> seen = new HashSet<Registration>();
        for (Taxon node : taxonomy) {
            Registration reg = assignedRegistration(node);
            if (reg == null) {
                interesting("no registration for node", node);
                ++losers;
            } else if (seen.contains(reg)) {
                interesting("registration also assigned to a node other than this one", reg, node);
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
                                 Map<Taxon, Registration> needExclusions) {
        // Preorder traversal
        if (node.children != null)
            for (Taxon child : node.children)
                registerSubtree(child, needExclusions);

        // Return registration if already there
        Registration probe = assignedRegistration(node);
        if (probe != null) {
            if (node.children == null)
                event("registration resolves to tip node", node, probe);
            else if (node.name == null)
                interesting("registration resolves to anonymous internal node", node, probe);
            else
                event("registration resolves to named internal node", node, probe);
            return probe;
        }

        Registration reg;

        // Create new registration
        if (node.children == null || speciesp(node)) {
            // Look at clues.  If a registration maps
            // equally well to this node and another node, then
            // creating a new registration isn't going to help.
            reg = registry.newRegistrationForTaxon(node);
            if (clues.get(node) == Clue.AMBIGUITY)
                interesting("creating new registration for ambiguity; not likely to help", node, reg);
            else if (node.children == null)
                event("new registration created for tip", node, reg);
            else
                event("new registration created for non-tip species", node, reg);
            // Tip.  Add to correspondence so that it can be used as a sample.
        } else {
            // Persistent side effect to list of children in that node!
            // (the order will be assumed when finding exclusions, as well)
            List<Taxon> children = orderChildrenByPreference(node);

            // ? Exclude nodes that have names that are homonyms ?
            List<List<Registration>> childSamples = new ArrayList<List<Registration>>();
            for (Taxon child : children) {
                Registration chreg = assignedRegistration(child);
                // if (chreg == null) ... shouldn't happen
                if (chreg.samples != null)
                    childSamples.add(chreg.samples);
                else {
                    List<Registration> single = new ArrayList<Registration>();
                    single.add(chreg);
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
            reg = registry.newRegistrationForTaxon(node);
            Collections.sort(samples, Registry.betterQuality);
            reg.samples = samples;
            for (Registration s : samples)
                if (s.quality > 0) ++reg.quality;
            // To do on second pass: exclusions
            needExclusions.put(node, reg);
            if (clues.get(node) == Clue.PATH) {
                if (node.name == null)
                    event("new registration created for anonymous node in path ambiguity", node, reg);
                else
                    event("new registration created for node in path ambiguity", node, reg);
            } else
                event("new registration created for internal node", node, reg);
        }
        setResolution(reg, node);
        return reg;
    }

    boolean speciesp(Taxon node) {
        return node.rank != null && node.rank.equals("species");
    }

    // Second pass (after registrations have been created with inclusions only)

    void addExclusions(Taxon node,
                       Map<Taxon, Registration> needExclusions) {
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
                    if (sib_reg == null) sib_reg = assignedRegistration(sib);
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
    }

    List<Taxon> orderChildrenByPreference(Taxon node) {
        List<Taxon> children = node.children;
        Collections.sort(children, betterSampleSource);
        Taxon typ = findType(node);
        if (typ != null && typ != children.get(0)) {
            children.remove(typ);
            children.add(0, typ);
        }
        return children;
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

                int apri = encodeSourcePriority(a);
                int bpri = encodeSourcePriority(b);
                if (apri != bpri) return bpri - apri; // higher priority is better

                int asize = ((a.rank != null && a.rank.equals("species")) ? 1 : a.count());
                int bsize = ((b.rank != null && b.rank.equals("species")) ? 1 : b.count());
                if (asize != bsize) return bsize - asize; // bigger is better

                int ahom = (a.name != null ? a.taxonomy.lookup(a.name).size() : 2);
                int bhom = (b.name != null ? b.taxonomy.lookup(b.name).size() : 2);
                if (ahom != bhom) return ahom - bhom; // smaller is better

                return 0;
            }
        };

    // Search list of children to find one that looks like a 'type',
    // i.e. shares a stem with the parent.
    public Taxon findType(Taxon node) {
        String name = node.name;
        if (name == null) return null;

        // In a binomial, look only at the epithet
        String lastpart = name;
        int e = name.lastIndexOf(' ');
        if (e > 0) lastpart = name.substring(e+1);

        String stem = getStem(lastpart);

        for (Taxon child : node.children) {
            if (child.isHidden() != node.isHidden()) return null;
            if (child.name != null) {
                String childlastpart = child.name;
                int f = childlastpart.lastIndexOf(' ');
                if (f > 0) childlastpart = childlastpart.substring(f+1);
                if (childlastpart.startsWith(stem)) {
                    interesting("found typish child", node, child);
                    return child;
                }
            }
        }
        return null;
    }

    // This is a kludge but (a) it seems to mostly work, (b) it
    // doesn't matter a whole lot if occasionally it doesn't.
    String getStem(String name) {
        int z = name.length() - 5;
        if (z < 3) {
            z = 3;
            if (z > name.length())
                z = name.length();
        }
        return name.substring(0, z);
    }

    public int encodeSourcePriority(Taxon node) {
        if (node.sourceIds == null)
            return 0;
        int answer = 0;
        for (QualifiedId qid : node.sourceIds) {
            Integer mask = sourcePriority.get(qid.prefix);
            if (mask != null)
                answer |= mask.intValue();
        }
        return answer;
    }

    static Map<String, Integer> sourcePriority = new HashMap<String, Integer>();
    static {
        String[] sources = {"h2007", "if", "worms", "ncbi", "silva", "gbif", "irmng"};
        int q = 1 << sources.length;
        for (String source : sources) {
            sourcePriority.put(source, q);
            q = q >> 1;
        }
    }


    // Explains why reg is not assigned to node

    public String explain(Taxon node, Registration reg) {
        if (reg == null) {
            Clue clue = clues.get(node);
            if (clue != null) {
                switch(clue) {
                case AMBIGUITY:
                    return "a would-be registration is ambiguous";
                case PATH:
                    return "taxon is part of a path ambiguity";
                }
            }
            return "no registration is assigned";
        }



        if (reg.samples != null)
            for (Registration sreg : reg.samples) {
                Taxon sample = resolve(sreg);
                if (sample == null)
                    return String.format("sample %s does not resolve", sreg);
                if (!sample.descendsFrom(node))
                    return String.format("sample %s=%s does not descend from %s", sreg, sample, node);
            }
        if (reg.exclusions != null)
            for (Registration xreg : reg.exclusions) {
                Taxon exclusion = resolve(xreg);
                if (exclusion == null)
                    return String.format("exclusion %s does not resolve", xreg);
                if (exclusion.descendsFrom(node))
                    return String.format("exclusion %s=%s descends from %s", xreg, exclusion, node);
            }

        // Should look at metadata...

        Status status = statuses.get(reg);
        if (status != null) {
            switch(status) {
            case UNSATISFIABLE:
                return "registration's topological constraints are unsatisfiable";
            case UNRESOLVED:
                return "a sample is unresolved"; // covered above
            case PATH:
                try {
                    return String.format("registration is a path ambiguity among %s", findByTopology(reg));
                } catch (ResolutionFailure e) {
                    return String.format("registration is a path ambiguity");
                }
            case AMBIGUOUS:
                return "registration's metadata is ambiguous, no single best taxon";
            case OTTIDS_DIFFER:
                return String.format("no taxon with same OTT id %s as registration", reg.ottid);
            case QIDS_DIFFER:
                return String.format("no taxon with same source reference %s as registration", reg.qid);
            case NO_CANDIDATES:
                // a synonym in the old taxonomy matched a taxon in the new taxonomy?
                // and then the synonym wasn't carried over?
                return "registration has no candidate resolutions by metadata";
            }
        }

        List<Taxon> path = paths.get(reg);
        if (path != null)
            return "registration is a path ambiguity";

        // Answer should have been found via constraints, paths, status, etc., but wasn't.

        Taxon other = resolve(reg);

        if (other == null)
            return "registration does not resolve";

        if (other != node)
            return String.format("registration resolves to %s", other);

        Registration otherreg = assignedRegistration(node);

        if (otherreg == null)
            return "assignedRegistration failed, probably ambiguous";

        if (otherreg == reg)
            return "looks OK";

        List<Taxon> taxa = taxonomy.lookup(otherreg.name);
        if (taxa != null) {
            for (Taxon foo : taxa)
                if (foo.name.equals(reg.name))
                    return String.format("registration names are synonyms, probable merge");
        }

        return String.format("registration %s is assigned to this node", otherreg);
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

    void taxonomyReport() {
        int tips = 0, mappedTips = 0;
        int internal = 0, mappedInternal = 0;
        for (Taxon node : taxonomy)
            if (node.children == null) {
                ++tips;
                if (assignedRegistration(node) != null)
                    ++mappedTips;
            } else {
                ++internal;
                if (assignedRegistration(node) != null)
                    ++mappedInternal;
            }
        System.out.format("%s/%s tips with registrations, %s/%s internal nodes with registrations, %s/%s total\n",
                          mappedTips, tips, mappedInternal, internal,
                          (mappedTips + mappedInternal),
                          (tips + internal));
    }
}
