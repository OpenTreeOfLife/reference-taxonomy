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

    // for jython
    public static Registry newRegistry() { return new Registry(); }

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

    public Correspondence<Registration, Taxon> resolve(Taxonomy taxonomy) {
        Map<QualifiedId, Taxon> qidIndex = makeQidIndex(taxonomy);
        Correspondence<Registration, Taxon> byMetadata = mapTaxaByMetadata(taxonomy, qidIndex);
        Correspondence<Registration, Taxon> result = new Correspondence<Registration, Taxon>();
        // 1. Look up sample/exclusion nodes using metadata (see chooseTaxon)
        // 2. Look up internal nodes by topology (using metadata to disambiguate)
        // 3. Look up remaining tips by metadata
        // Steps 1 and 2 are done together by the following loop;
        // samples/exclusions are resolved as they are encountered:
        int i = 0;
        for (Registration reg : this.allRegistrations()) {
            // findByTopology has the side effect of entering samples in result
            List<Taxon> taxa = this.findByTopology(reg, byMetadata, result);
            if (taxa != null) {
                result.put(reg, taxa);
                ++i;
            }
        }
        // Step 3:
        int j = 0;
        for (Registration reg : this.allRegistrations()) {
            if (result.get(reg) == null && reg.samples == null && reg.exclusions == null) {
                List<Taxon> taxa = byMetadata.get(reg);
                if (taxa != null) {
                    result.put(reg, taxa);
                    ++j;
                }
            }
        }
        System.out.format("of %s registrations, mapped %s to internal nodes, %s to samples, %s to non-samples\n",
                          registrations.size(), i, result.size() - i - j, j);
        System.out.format("mapped to %s taxa\n", result.cosize());
        return result;
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

    // Find compatible taxa, using metadata, for one registation.
    // (Not sure how to divide labor between this method and chooseRegistration.)

    List<Taxon> taxaByMetadata(Registration reg, Taxonomy taxonomy, Map<QualifiedId, Taxon> qidIndex) {
        List<Taxon> result = new ArrayList<Taxon>(1);
        if (reg.qid != null) {
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

    List<Taxon> findByTopology(Registration reg, Correspondence<Registration, Taxon> byMetadata, Correspondence<Registration, Taxon> registrationToTaxa) {
        Taxon[] path = constrainedPath(reg, byMetadata, registrationToTaxa);
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
        } else return null;
    }

    // Find compatible taxa, using topological constraints, for one registration.
    // Return value is {m, a} where a is an ancestor of m.  Compatible taxa are
    // m and its ancestors up to be not including a.

    Taxon[] constrainedPath(Registration reg,
                            Correspondence<Registration, Taxon> byMetadata,
                            Correspondence<Registration, Taxon> registrationToTaxa) {
        if (reg.samples == null || reg.exclusions == null)
            return null;

        Taxon m = null;

        for (Registration s : reg.samples) {
            Taxon snode = chooseTaxon(s, byMetadata, registrationToTaxa);
            if (snode != null) {
                if (m == null)
                    m = snode;
                else
                    m = m.mrca(snode);
            } else
                return null;    // Couldn't find sample in taxonomy
        }
        if (m == null || m.noMrca()) return null;

        Taxon a = null;

        if (m != null && reg.exclusions != null && reg.exclusions.size() > 0) {
            for (Registration s : reg.exclusions) {
                Taxon snode = chooseTaxon(s, byMetadata, registrationToTaxa);
                if (snode != null) {
                    Taxon b = m.mrca(snode);
                    if (a == null)
                        a = b;
                    else if (b.descendsFrom(a))
                        a = b;
                } else
                    return null; // couldn't find exclusion in taxonomy
            }
        }
        if (a == null || a.noMrca()) return null;

        if (a == m) return null; // Registration is paraphyletic in this taxonomy

        return new Taxon[]{m, a};
    }

    // ------------------------------------------------------------

    // Create registrations for any taxa that don't have them
    // uniquely.  Performs side effects on the taxon to registration
    // correspondence.
    // call this 'extend' ???

    void register(Taxonomy tax,
                  Correspondence<Registration, Taxon> registrationToTaxa) {
        Map<Taxon, Registration> needExclusions = new HashMap<Taxon, Registration>();
        int before = registrationToTaxa.size();
        // 1. Register all unregistered tips
        // 2. Register all unregistered internal nodes
        for (Taxon root : tax.roots())
            registerSubtree(root, registrationToTaxa, needExclusions);
        System.out.format("%s internal, %s tips\n", needExclusions.size(), registrationToTaxa.size() - before);
        for (Taxon node : needExclusions.keySet())
            addExclusions(node, needExclusions, registrationToTaxa);
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
            if (probe != null)
                return probe;
            Registration reg = registrationForTaxon(node);
            // Tip.  Add to correspondence so that it can be used as a sample.
            registrationToTaxa.add(reg, node);
            Registration check = chooseRegistration(node, registrationToTaxa);
            if (check != reg)
                // this is not good.
                System.out.format("** registrationToTaxa.add(%s, %s) failed\n", reg, node);
            return reg;
        } else if (probe != null) {
            Collections.sort(node.children, betterAsType);
            for (Taxon child : node.children)
                registerSubtree(child, registrationToTaxa, needExclusions);
            return probe;
        } else {
            // Persistent side effect to list of children in that node!
            // (the order will be assumed when finding exclusions, as well)
            Collections.sort(node.children, betterAsType);
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
            reg.samples = samples;
            // To do on second pass: exclusions
            needExclusions.put(node, reg);
            return reg;
        }
    }

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
        registrationToTaxa.add(reg, node);
        Registration check = chooseRegistration(node, registrationToTaxa);
        if (check == null)
            // this is not good.
            System.out.format("** registrationToTaxa.add(%s, %s) failed\n", reg, node);
    }

    // Returns negative if a would make a better 'type' than b.
    // Prefer larger taxa to smaller ones.
    // Prefer less homonymic names.
    // Prefer larger number of source taxonomies.
    // Try to ignore anything below species rank.

    public Comparator<Taxon> betterAsType = new Comparator<Taxon>() {
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

    // Choose one registration from among several that apply.

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
                        System.out.format("ambiguous (4): %s %s %s\n", node, reg, answer);
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
                    System.out.format("ambiguous (5): %s %s %s\n", node, reg, answer);
                    return null;      // FAIL ???
                }
                answer = reg;
            }
        }
        return answer;
    }

    // public interface
    public Taxon chooseTaxon(Registration reg,
                             Correspondence<Registration, Taxon> registrationToTaxa) {
        return chooseTaxon(reg, null, registrationToTaxa);
    }

    public Taxon chooseTaxon(Registration reg,
                             Correspondence<Registration, Taxon> byMetadata,
                             Correspondence<Registration, Taxon> registrationToTaxa) {
        List<Taxon> seen = registrationToTaxa.get(reg);
        if (seen != null && seen.size() == 1) // grumble
            return seen.get(0);
        else if (byMetadata != null) {
            List<Taxon> taxa = byMetadata.get(reg);
            if (taxa == null) {
                // System.out.format("no such taxon: %s\n", reg);
                return null;
            } else if (taxa.size() == 1) {
                registrationToTaxa.add(reg, taxa.get(0));
                return taxa.get(0);
            } else {
                System.out.format("taxon-ambiguous registration: %s %s\n", reg, taxa);
                return null;
            }
        } else return null;
    }

    public String explain(Taxon node, Registration reg, Correspondence<Registration, Taxon> corr) {
        if (reg.samples != null)
            for (Registration sreg : reg.samples) {
                Taxon sample = chooseTaxon(sreg, corr);
                if (sample == null)
                    return String.format("no sample taxon for sample %s", sreg);
                if (!sample.descendsFrom(node))
                    return String.format("sample %s does not descend", sample);
            }
        if (reg.exclusions != null)
            for (Registration xreg : reg.exclusions) {
                Taxon exclusion = chooseTaxon(xreg, corr);
                if (exclusion == null)
                    return String.format("no exclusion taxon for sample %s", xreg);
                if (exclusion.descendsFrom(node))
                    return String.format("exclusion %s descends", exclusion);
            }

        // Should look at metadata...

        if (corr.coget(node) == null)
            return "no registrations for taxon";

        if (corr.get(reg) == null)
            return "no taxa for registration";

        if (chooseRegistration(node, corr) == null)
            return "chooseRegistration failed, probably ambiguous";

        return null;
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

        // index by name?
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

}

class Registration {

    int id;                     // Positive
    String name = null;
    QualifiedId qid = null;
    String ottid = null;
    List<Registration> samples = null;
    List<Registration> exclusions = null;

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
        // TBD: error if there's already a registration with that id
        Registration entry = new Registration(id);
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
