/*
  TBD:
  - treat infraspecific taxa specially
  - figure out what to do with unresolvable samples/exclusions
  - deal with root of tree
  - do 'best metadata match' in chooseTaxon as well?
*/


package org.opentreeoflife.registry;

import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.QualifiedId;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;

import org.python.util.PythonInterpreter;
import org.python.util.InteractiveConsole;
import org.python.util.JLineConsole;

public class Registry {

    // State

    private Map<Integer, Registration> registrations = new HashMap<Integer, Registration>();

    static final int nsamples = 2;
    static final int nexclusions = 1;

    public Registry() {
    }

    public Iterable<Registration> allRegistrations() {
        return registrations.values();
    }

    public int size() {
        return registrations.size();
    }

    // Find the compatible taxon (pathologically: taxa), if any, 
    // for every registration in the registry.
    // With this in hand, use chooseTaxon() to resolve a registration to a taxon,
    // and chooseRegistration() to find the registration assigned to a taxon.

    public Correspondence<Registration, Taxon> assign(Taxonomy taxonomy) {
        clearEvents();
        Map<QualifiedId, Taxon> qidIndex = makeQidIndex(taxonomy);
        Correspondence<Registration, Taxon> byMetadata = mapTaxaByMetadata(taxonomy, qidIndex);
        Correspondence<Registration, Taxon> result = new Correspondence<Registration, Taxon>();

        // 1. Resolve sample/exclusion registrations using metadata (see chooseTaxon)
        // 2. Resolve metadata-only registrations using metadata

        for (Registration reg : this.allRegistrations()) {
            if (reg.samples != null || reg.exclusions != null) {
                if (reg.samples != null)
                    for (Registration s : reg.samples)
                        byMetadataOnly(s, byMetadata, result);
                if (reg.exclusions != null)
                    for (Registration s : reg.exclusions)
                        byMetadataOnly(s, byMetadata, result);
            } else 
                byMetadataOnly(reg, byMetadata, result);
        }

        // 3. Resolve registrations that have topological constraints (using metadata to disambiguate)

        for (Registration reg : this.allRegistrations()) {
            if (reg.samples != null || reg.exclusions != null) {
                try {
                    List<Taxon> taxa = this.findByTopology(reg, byMetadata, result);
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
                    if (byMetadataOnly(reg, byMetadata, result))
                        event("metadata worked where topology failed", reg);
                }
            }
        }
        reportEvents();
        return result;
    }

    private boolean byMetadataOnly(Registration reg, // little utility for above
                                   Correspondence<Registration, Taxon> byMetadata,
                                   Correspondence<Registration, Taxon> result) {
        if (result.get(reg) == null) {
            List<Taxon> taxa = byMetadata.get(reg);
            if (taxa != null) {
                if (taxa.size() == 1)
                    event("resolved uniquely by metadata", reg, taxa.get(0));
                else
                    event("resolved ambiguously by metadata", reg, taxa);
                result.put(reg, taxa);
                return true;
            } else {
                event("metadata-only registration with no compatible nodes", reg);
                return false;
            }
        } else
            return false;
    }

    // Find compatible taxa using metadata, for all registrations.
    // Do we really need to reify taxaByMetadata as a Map ?

    Correspondence<Registration, Taxon> mapTaxaByMetadata(Taxonomy taxonomy, Map<QualifiedId, Taxon> qidIndex) {
        Correspondence<Registration, Taxon> byMetadata = new Correspondence<Registration, Taxon>();
        for (Registration reg : this.allRegistrations()) {
            List<Taxon> taxa = taxaByMetadata(reg, taxonomy, qidIndex);
            if (taxa != null && taxa.size() > 0)
                byMetadata.put(reg, taxa);
        }
        return byMetadata;
    }

    // Find taxa, using metadata, for one registation.
    // (Not sure how to divide labor between this method and chooseRegistration.)
    // This is how the samples and exclusions get mapped to nodes.
    // This gets only the "best metadata matches" for the registration.

    List<Taxon> taxaByMetadata(Registration reg, Taxonomy taxonomy, Map<QualifiedId, Taxon> qidIndex) {
        List<Taxon> result = new ArrayList<Taxon>(1);
        if (reg.qid != null) {
            // Collisions have already been winnowed out
            Taxon probe = qidIndex.get(reg.qid);
            if (probe != null) {
                // Sanity check
                List<Taxon> taxa = taxonomy.lookup(reg.name);
                if (taxa != null) {
                    boolean found = false;
                    for (Taxon node : taxa)
                        if (node == probe)
                            { found = true; break; }
                    if (!found)
                        System.out.format("source id / name mismatch: %s %s %s %s\n", reg.qid, reg.name, probe, taxa);
                }
                result.add(probe);
                return result;
            }
        }
        if (reg.ottid != null) {
            Taxon probe = taxonomy.lookupId(reg.ottid);
            if (probe != null) {
                result.add(probe);
                return result;
            }
        }
        if (reg.name != null) {
            List<Taxon> nodes = taxonomy.lookup(reg.name);
            if (nodes != null) {
                if (result.size() == 0) return nodes;
                // First consider exact name matches
                boolean gotOne = false;
                for (Taxon node : nodes)
                    if (node.name.equals(reg.name)) {
                        result.add(node);
                        gotOne = true;
                    }
                if (gotOne) return result;
                // Next consider reg.name as a synonym
                for (Taxon node : nodes)
                    result.add(node);
            }
        }
        return result;
    }

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

    List<Taxon> findByTopology(Registration reg,
                               Correspondence<Registration, Taxon> byMetadata,
                               Correspondence<Registration, Taxon> registrationToTaxa)
    throws ResolutionFailure
    {
        Taxon[] path = constrainedPath(reg, registrationToTaxa);
        if (path != null) {
            Taxon m = path[0], a = path[1]; // mrca, ancestor
            if (m.parent != a) {
                // Ambiguous.  Attempt to clear it up using metadata... this may be the wrong place to do this
                List<Taxon> mets = byMetadata.get(reg);
                if (mets != null) {
                    List<Taxon> candidates = new ArrayList<Taxon>();
                    for (Taxon n = m; n != a; n = n.parent)
                        if (mets.contains(n))
                            candidates.add(n);
                    if (candidates.size() > 0)
                        return candidates;
                    // fall through
                }
                // fall through
            }
            List<Taxon> candidates = new ArrayList<Taxon>();
            for (Taxon n = m; n != a; n = n.parent)
                candidates.add(n);
            return candidates;
        } else
            return null;
    }

    // Find compatible taxa, using topological constraints, for one registration.
    // Return value is {m, a} where a is an ancestor of m.  Compatible taxa are
    // m and its ancestors up to be not including a.

    Taxon[] constrainedPath(Registration reg,
                            Correspondence<Registration, Taxon> registrationToTaxa)
    throws ResolutionFailure
    {
        ResolutionFailure fail = null;
        Taxon[] result;
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
                    result = new Taxon[]{m, a};
                } else result = null;
            } else {
                Taxon a = m;
                while (!a.isRoot()) a = a.parent;
                a = a.parent;
                result = new Taxon[]{m, a}; // Kludge for root of tree
            }
        } else result = null;
        if (fail != null) throw fail;
        return result;
    }

    class ResolutionFailure extends Exception {
        Registration registration;
        ResolutionFailure(Registration reg) {
            registration = reg;
        }
    }

    // ------------------------------------------------------------
    // Resolution and assignment

    // Rsolution: choose one taxon for the given registration, based
    // on compatibility relation.

    public Taxon chooseTaxon(Registration reg,
                             Correspondence<Registration, Taxon> registrationToTaxa) {
        List<Taxon> seen = registrationToTaxa.get(reg);
        if (seen != null && seen.size() == 1) // ** sort by goodness of match ??
            return seen.get(0);
        else
            return null;
    }

    // Assignment: Choose one registration from among several that apply.

    public Registration chooseRegistration(Taxon node,
                                           Correspondence<Registration, Taxon> registrationToTaxa) {
        List<Registration> regs = registrationToTaxa.coget(node);
        if (regs == null) return null;
        Registration answer = null;

        // Check primary source id (e.g. ncbi:1234)
        for (Registration reg : regs) {
            List<Taxon> probe = registrationToTaxa.get(reg);
            if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                // keep going to see if there's more than one
                // in which case try using metadata to filter
                if (node.sourceIds != null && node.sourceIds.get(0).equals(reg.qid)) {
                    if (answer != null) {
                        System.out.format("ambiguous (1): %s %s %s qid=%s\n", node, reg, answer, reg.qid);
                        return null;      // FAIL ???
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
                if (node.sourceIds != null && node.sourceIds.contains(reg.qid)) {
                    if (answer != null) {
                        System.out.format("ambiguous (2): %s %s %s qid=%s\n", node, reg, answer, reg.qid);
                        return null;      // FAIL ???
                    } else
                        answer = reg;
                }
            }
        }
        if (answer != null) return answer;

        // Check OTT id
        for (Registration reg : regs) {
            List<Taxon> probe = registrationToTaxa.get(reg);
            if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                if (node.id != null && node.name != null && node.id.equals(reg.ottid)) {
                    if (answer != null) {
                        System.out.format("ambiguous: %s has same id %s as both %s and %s\n", node, node.id, reg, answer);
                        return null;      // FAIL ???
                    } else
                        answer = reg;
                }
            }
        }
        if (answer != null) return answer;

        // Check primary name
        for (Registration reg : regs) {
            List<Taxon> probe = registrationToTaxa.get(reg);
            if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                if (node.name != null && node.name.equals(reg.name)) {
                    if (answer != null) {
                        System.out.format("ambiguous (4): %s -> %s or %s\n", node, reg, answer);
                        return null;      // FAIL ???
                    } else
                        answer = reg;
                }
            }
        }
        if (answer != null) return answer;

        // Something to consider: synonyms - node.taxonomy.lookup(reg.name).contains(node)

        // Try anything
        for (Registration reg : regs) {
            List<Taxon> probe = registrationToTaxa.get(reg);
            if (probe != null && probe.size() == 1 && probe.get(0) == node) {
                // keep going to see if there's more than one
                if (answer != null) {
                    System.out.format("ambiguous (5): %s -> %s or %s\n", node, reg, answer);
                    return null;      // FAIL ???
                }
                answer = reg;
            }
        }
        return answer;
    }

    // ------------------------------------------------------------

    // Create registrations for any taxa that don't have them
    // uniquely.  Performs side effects on the taxon to registration
    // correspondence.
    // call this 'extend' ???

    void extend(Taxonomy tax,
                Correspondence<Registration, Taxon> registrationToTaxa) {
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

    void checkRegistrations(Taxonomy tax, Correspondence<Registration, Taxon> registrationToTaxa) {
        int losers = 0;
        Set<Registration> seen = new HashSet<Registration>();
        for (Taxon node : tax) {
            Registration reg = this.chooseRegistration(node, registrationToTaxa);
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
                                 Correspondence<Registration, Taxon> registrationToTaxa,
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
            Registration reg = registrationForTaxon(node);
            if (!node.isHidden()) ++reg.quality;
            // Tip.  Add to correspondence so that it can be used as a sample.
            registrationToTaxa.add(reg, node);
            Registration check = chooseRegistration(node, registrationToTaxa);
            if (check != reg)
                // this is not good.
                System.out.format("** registrationToTaxa.add(%s, %s) failed: %s\n", reg, node, check);
            else
                event("new registration created for tip", node, reg);
            return reg;
        } else if (probe != null) {
            event("found registration for internal node", node, probe);
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
            Registration reg = registrationForTaxon(node);
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

    Registration registrationForTaxon(Taxon node) {
        Registration reg = newRegistration();
        // Set metadata from node
        reg.name = node.name;
        if (node.sourceIds != null)
            reg.qid = node.sourceIds.get(0);
        if (node.name != null)
            reg.ottid = node.id;
        return reg;
    }

    // Second pass after registrations have been created with inclusions

    void addExclusions(Taxon node,
                       Map<Taxon, Registration> needExclusions,
                       Correspondence<Registration, Taxon> registrationToTaxa) {
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
            System.out.format("** registrationToTaxa.add(%s, %s) failed\n", reg, node);
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

    // Explains why reg is not assigned to node / reg does not resolve to node

    public String explain(Taxon node, Registration reg, Correspondence<Registration, Taxon> corr) {
        if (reg.samples != null)
            for (Registration sreg : reg.samples) {
                if (corr.get(sreg) == null)
                    return String.format("no taxon compatible with sample %s", sreg);
                Taxon sample = chooseTaxon(sreg, corr);
                if (sample == null)
                    return String.format("no taxon chosen for sample %s", sreg);
                if (!sample.descendsFrom(node))
                    return String.format("sample %s does not descend from %s", sample, node);
            }
        if (reg.exclusions != null)
            for (Registration xreg : reg.exclusions) {
                if (corr.get(xreg) == null)
                    return String.format("no taxon compatible with exclusion %s", xreg);
                Taxon exclusion = chooseTaxon(xreg, corr);
                if (exclusion == null)
                    return String.format("no taxon chosen for exclusion %s", xreg);
                if (exclusion.descendsFrom(node))
                    return String.format("exclusion %s descends from %s", exclusion, node);
            }

        // Should look at metadata...

        if (corr.get(reg) == null)
            return "no taxa for registration";

        if (!corr.get(reg).contains(node))
            return "taxon not compatible with registration";

        if (chooseRegistration(node, corr) == null)
            return "chooseRegistration failed, probably ambiguous";

        if (corr.coget(node) == null)
            return "no registrations for taxon";

        if (!corr.coget(node).contains(reg))
            return "taxon not compatible with registration"; // redundant

        if (chooseTaxon(reg, corr) == null)
            return "chooseTaxon failed";

        return "looks OK";
    }

    // Create Registration records on demand

    public Registration getRegistration(int n) {
        Registration probe = registrations.get(n);
        if (probe != null) return probe;
        Registration reg = new Registration(n);
        registrations.put(n, reg);
        if (n >= nextid)
            nextid = n + 1;
        return reg;
    }

    int nextid = 1;

    public Registration newRegistration() {
        return getRegistration(nextid++);
    }

    // ------------------------------------------------------------

    // Load registry from file

    public static Registry load(String filename) throws IOException {
        BufferedReader br = Taxonomy.fileReader(filename);
        String row;
        Registry registry = new Registry();
        while ((row = br.readLine()) != null) {
            Registration reg = Registration.parse(row, registry);
            registry.registrations.put(reg.id, reg);
        }
        System.out.format("%s registrations loaded\n", registry.registrations.size());
        br.close();
        for (Registration reg : registry.allRegistrations()) {
            if (reg.samples != null)
                Collections.sort(reg.samples, betterQuality);
            if (reg.exclusions != null)
                Collections.sort(reg.exclusions, betterQuality);
        }
        return registry;
    }

    // write registry

    public void dump(String filename) throws IOException {
        PrintStream out = Taxonomy.openw(filename);
        for (Registration reg : allRegistrations())
            reg.dump(out);
        out.close();
    }

    // entry from shell

	public static void main(String argv[]) throws Exception {
		if (argv.length > 0) {
			PythonInterpreter j = new PythonInterpreter();
            for (String source : argv)
                j.execfile(source);
        } else {
			System.out.format("Consider doing:\n" +
                              "from org.opentreeoflife.taxa import Taxonomy\n" +
                              "from org.opentreeoflife.registry import Registry\n");
			InteractiveConsole j = new JLineConsole();
			j.interact();
        }
    }

    // instrumentation

    Map<String, Integer> eventCounts = new HashMap<String, Integer>();

    void event(String tag, Object... args) {
        countEvent(tag, args);
        }

    int countEvent(String tag, Object[] args) {
		Integer probe = this.eventCounts.get(tag);
		int count;
		if (probe == null)
			count = 0;
		else
			count = probe;
        ++count;
		this.eventCounts.put(tag, count);
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

class Registration {

    int id;                     // Positive
    String name = null;
    QualifiedId qid = null;
    String ottid = null;
    List<Registration> samples = null;
    List<Registration> exclusions = null;
    int quality = 0;

    Registration(int id) {
        this.id = id;
    }

    void dump(PrintStream out) throws IOException {
        out.format("%s,%s,%s,%s",
                   this.id,
                   (this.name == null ? "" : this.name),
                   (this.qid == null ? "" : this.qid.toString()),
                   (this.ottid == null ? "" : this.ottid));
        if (samples != null)
            for (Registration reg : samples)
                out.format(",%s", reg.id);
        if (exclusions != null)
            for (Registration reg : exclusions)
                out.format(",%s", -reg.id);
        out.println();
    }

    static Registration parse(String row, Registry registry) {
        String[] cells = row.split(",");
        int id = Integer.parseInt(cells[0]);
        String name = cells[1];
        String qid = cells[2];
        // TBD: error if we've already seen a row for this id
        Registration entry = registry.getRegistration(id);
        if (name.length() > 0) entry.name = name;
        if (qid.length() > 0) entry.qid = new QualifiedId(qid);
        entry.ottid = cells[3];
        List<Registration> samples = new ArrayList<Registration>();
        List<Registration> exclusions = new ArrayList<Registration>();
        for (int i = 4; i < cells.length; ++i)
            if (cells[i].length() != 0) {
                int n = Integer.parseInt(cells[i]);
                if (n < 0)
                    exclusions.add(registry.getRegistration(-n));
                else
                    samples.add(registry.getRegistration(n));
            }
        entry.samples = samples;
        entry.exclusions = exclusions;
        return entry;
    }

    public String toString() {
        if (this.samples != null) {
            return String.format("(%s %s %s ...)", this.id, this.name, this.qid);
        } else
            return String.format("(%s %s %s)", this.id, this.name, this.qid);
    }

}
