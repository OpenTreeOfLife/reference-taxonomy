/**
   A binary relation with both indexes.

   For previous version with lots of logic for metadata, see
   commit f39b0cc855d910a112e13ffefdc86ec7f584a2de
*/

/*
  Pathological situations:
  A tip becomes an internal node (by acquiring descendants, or merging).
  An internal node becomes a tip (by losing all of its descendants).
  A tip goes away entirely (left no forwarding address).
  Two (formerly) distinct tips merge.
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
    int nsamples = 2;
    int nexclusions = nsamples - 1;

    enum Status { UNSATISFIABLE, UNRESOLVED_SAMPLE, PATH };
    enum Clue { AMBIGUITY, PATH };

    // State
    public Registry registry;
    public Taxonomy taxonomy;
    Map<Registration, Taxon> resolutionMap = new HashMap<Registration, Taxon>();
    Map<Registration, List<Taxon>> paths = new HashMap<Registration, List<Taxon>>();
    Map<Registration, Status> statuses = new HashMap<Registration, Status>();

    Map<Taxon, Registration> assignments = new HashMap<Taxon, Registration>();
    Map<Taxon, Clue> clues = new HashMap<Taxon, Clue>();

    // Constructor
    public Correspondence(Registry registry, Taxonomy taxonomy) {
        this.registry = registry;
        this.taxonomy = taxonomy;
    }

    public void setNumberOfInclusions(int n) {
        nsamples = n;
        nexclusions = n - 1;
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
        clearEvents();
        for (Registration reg : registry.allRegistrations()) {
            List<Taxon> taxa = findNodes(reg);
            if (taxa != null) {
                if (taxa.size() == 1) {
                    setResolution(reg, taxa.get(0));
                    event("resolved uniquely by topology", reg, taxa.get(0));
                } else if (taxa.size() == 0) {
                    statuses.put(reg, Status.UNSATISFIABLE);
                    event("topological constraints are inconsistent", reg);
                } else {
                    paths.put(reg, taxa);
                    setClue(taxa, Clue.PATH);
                    statuses.put(reg, Status.PATH);
                    event("registration is ambiguous along path", reg, taxa);
                }
            } else {
                // interesting("resolution failed due to unresolved sample(s)", reg);
                statuses.put(reg, Status.UNRESOLVED_SAMPLE);
            }
        }
        reportEvents();
        taxonomyReport();
    }

    // Find compatible taxa, using topological constraints, for one
    // registration.  Return value is the set of nodes from m
    // (inclusive) to a (exclusive), where m is the mrca of the
    // inclusions and a is the mrca of m with the 'nearest' exclusion.
    // Return value of empty list means that no node simultaneously
    // satisfies all the constraints.
    // Returns null if a sample needed to calculate the answer failed
    // to resolve.

    public List<Taxon> findNodes(Registration reg) {
        Taxon m = null;
        Terminal loser = null;  // resolution failure
        List<Taxon> result = new ArrayList<Taxon>();
        for (Terminal s : reg.samples) {
            Taxon snode = resolve(s);
            if (snode != null) {
                if (m == null)
                    m = snode;
                else {
                    m = m.mrca(snode);
                    if (m == null || m.noMrca()) {
                        // I don't see how the m == null case can happen, but it did
                        interesting("different trees, no mrca - shouldn't happen", reg, m, snode);
                        return result; // different trees, no way to satisfy
                    }
                }
            } else
                // Couldn't find sample in taxonomy
                loser = s;
        }
        Taxon a = null;  // start at root of tree and get progressively closer to m
        if (m != null) {
            if (reg.exclusions != null && reg.exclusions.size() > 0) {
                // Find the exclusion that is nearest to m
                for (Terminal s : reg.exclusions) {
                    Taxon snode = resolve(s);
                    if (snode != null) {
                        Taxon e = snode.mrca(m);
                        if (a == null)
                            a = e;
                        else {
                            // if e descends from a, then e is closer to m. ratchet it in
                            // e descends from a iff mrca(e,a) = a
                            if (e.mrca(a) == a)
                                a = e;
                        }
                        if (a == m)  // = a.descendsFrom(m)
                            // Inconsistent constraints!
                            return result;
                    } else
                        // Couldn't find this exclusion in tree
                        loser = s;
                }
            }
        }
        if (m == null) {
            if (loser != null) {
                interesting("no inclusions resolved", reg, loser);
                return null;
            }
            interesting("no inclusions - shouldn't happen", reg);
        } else {
            if (a != null) {
                // Copy the path from m to a out of the taxonomy
                for (Taxon n = m; n != a; n = n.parent)
                    result.add(n);
            } else {
                // This case should only happen for the root of the tree
                Taxon n;
                // We know m is nonnull; this was checked above
                for (n = m; !n.isRoot(); n = n.parent)
                    result.add(n);
                result.add(n);              // add the root
            }
        }
        if (loser != null) {
            if (result.size() == 1) {
                interesting("unique resolution in spite of missing sample(s)", reg, loser);
                // return null;
            } else {
                interesting("path ambiguity possibly due to missing sample(s)", reg);
                return null;
            }
        }
        return result;
    }

    // Resolution: choose one taxon for the given registration, based
    // on compatibility relation.

    public Taxon resolve(Registration reg) {
        return resolutionMap.get(reg);
    }

    public Taxon resolve(Terminal term) {
        return taxonomy.lookupId(term.id);
    }

    // Assignment: Choose one best (most specific) registration from
    // among several that resolve to the node.
    // This code looks all wrong to me.

    public Registration assignedRegistration(Taxon node) {
        return assignments.get(node);
    }

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
            if (!Terminal.isTerminalTaxon(node)) {
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
        if (Terminal.isTerminalTaxon(node))
            return null;

        for (Taxon child : node.children)
            registerSubtree(child, needExclusions);

        // Return registration if already there
        Registration probe = assignedRegistration(node);
        if (probe != null) {
            if (node.name == null)
                interesting("anonymous internal node is assigned a registration", node, probe);
            else
                event("internal node is assigned a registration", node, probe);
            return probe;
        }

        // Create new registration
        Registration reg = registry.newRegistration();

        // Persistent side effect to list of children in that node!
        // (the order will be assumed when finding exclusions, as well)
        List<Taxon> children = orderChildrenByPreference(node);

        List<List<Terminal>> childSamples = new ArrayList<List<Terminal>>();
        for (Taxon child : children) {
            if (Terminal.isTerminalTaxon(child)) {
                List<Terminal> single = new ArrayList<Terminal>();
                single.add(Terminal.getTerminal(child));
                childSamples.add(single);
            } else {
                Registration chreg = assignedRegistration(child);
                // if (chreg == null) ... shouldn't happen
                if (chreg.samples != null)
                    childSamples.add(chreg.samples);
            }
        }
        // Novel taxon, or multiple registrations are compatible
        // with it.  Make up a new registration.
        List<Terminal> samples = new ArrayList<Terminal>(nsamples);
        if (children.size() == 1) { // Monotypic
            Terminal monotypic = Terminal.getTerminal(node);
            monotypic.quality -= 10;
            samples.add(monotypic);
            event("monotypic taxon", node, monotypic);
        }
        for (int i = 0; ; ++i) {
            boolean gotOne = false;
            for (List<Terminal> regs : childSamples) {
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
        Collections.sort(samples, Terminal.betterQuality);
        reg.samples = samples;
        // To do on second pass: exclusions
        needExclusions.put(node, reg);
        if (clues.get(node) == Clue.PATH) {
            if (node.name == null)
                event("new registration created for anonymous node in path ambiguity", node, reg);
            else
                event("new registration created for named node in path ambiguity", node, reg);
        } else
            event("new registration created for internal node", node, reg);
        setResolution(reg, node);
        return reg;
    }

    // Second pass (after registrations have been created with inclusions only)

    void addExclusions(Taxon node,
                       Map<Taxon, Registration> needExclusions) {
        // Add the new registration to those already there for this taxon
        Registration reg = needExclusions.get(node);
        List<Terminal> exclusions = new ArrayList<Terminal>();
        if (!node.isRoot() && node.parent.children.size() == 1) { // Monotypic parent
            Terminal monotypic = Terminal.getTerminal(node.parent);
            monotypic.quality -= 10;
            exclusions.add(monotypic);
            event("child of monotypic taxon", node, monotypic);
        }
        for (Taxon n = node; !n.isRoot(); n = n.parent) {
            if (exclusions.size() >= nexclusions) break;
            Taxon p = n.parent;
            if (p.children.size() >= 2) {
                for (Taxon sib : p.children) {
                    if (exclusions.size() >= nexclusions) break;
                    if (sib != n) {
                        if (sib.isHidden() && !node.isHidden())
                            event("hidden exclusion", node, reg);
                        if (Terminal.isTerminalTaxon(sib))
                            exclusions.add(Terminal.getTerminal(sib));
                        else {
                            Registration sib_reg = assignedRegistration(sib);
                            if (sib_reg != null)
                                exclusions.add(sib_reg.samples.get(0));
                        }
                    }
                }
            }
        }
        if (exclusions.size() == 0)
            System.out.format("No exclusions for %s <-> %s\n", node, reg);
        reg.exclusions = exclusions;
        // Sanity check
        if (false) {
            List<Taxon> taxa = findNodes(reg);
            if (taxa == null)
                interesting("newly created registration has unresolved sample", node);
            else if (taxa.size() == 1)
                ;
            else if (taxa.size() == 0)
                interesting("newly created registration has unresolved sample", node);
            else if (node.children.size() == 1)
                event("monotypic taxon", node);
            else if (node.parent.children.size() == 1)
                event("child of monotypic taxon", node);
            else {
                // Shoudn't happen!
                interesting("** newly created registration is ambiguous (shouldn't happen)", node, taxa);
                goryDetail(reg, taxa);
            }
        }
    }

    void goryDetail(Registration reg, List<Taxon> taxa) {
        for (Terminal inc : reg.samples) {
            System.out.print("+ ");
            Taxon node = resolve(inc);
            if (node != null)
                node.showLineage(null);
            else
                System.out.print(inc);
        }
        for (Terminal exc : reg.exclusions) {
            System.out.print("- ");
            Taxon node = resolve(exc);
            if (node != null)
                node.showLineage(null);
            else
                System.out.print(exc);
        }
        for (Taxon node : taxa) {
            System.out.print("? ");
            node.showLineage(null);
        }
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

                int asize = (Terminal.isTerminalTaxon(a) ? 1 : a.count());
                int bsize = (Terminal.isTerminalTaxon(b) ? 1 : b.count());
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
                    event("found typish child", node, child);
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
            for (Terminal sreg : reg.samples) {
                Taxon sample = resolve(sreg);
                if (sample == null)
                    return String.format("sample %s does not resolve", sreg);
                if (!sample.descendsFrom(node))
                    return String.format("sample %s=%s does not descend from %s", sreg, sample, node);
            }
        if (reg.exclusions != null)
            for (Terminal xreg : reg.exclusions) {
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
            case UNRESOLVED_SAMPLE:
                return "a sample is unresolved"; // covered above
            case PATH:
                return String.format("registration is a path ambiguity among %s", findNodes(reg));
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
            if (Terminal.isTerminalTaxon(node)) {
                ++tips;
            } else {
                ++internal;
                if (assignedRegistration(node) != null)
                    ++mappedInternal;
            }
        System.out.format("%s terminal nodes, %s/%s internal nodes with registrations\n",
                          tips,
                          mappedInternal, internal);
    }
}
