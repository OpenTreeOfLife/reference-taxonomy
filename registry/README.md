This document describes work in progress, not production code.  It
assumes you know something about the Open Tree of Life project
(e.g. what a 'synthetic tree' is and what OTT and OTT id mean).

Node identifier registry 
--------------------

The 'registry' tool introduces a data structure called a registry.  A
registry is a table of entries called registrations.  Each
registration is intended to correspond to a clade.  Each registration
has a unique id, and if all goes well, such an id can be used to
identify a clade (the one corresponding to the registration).

Registration ids are designed for use in long-lived documents and data
sets, such as annotations, when there is a need for a stable reference
to representations of clades across versions of a taxonomic or
phylogenetic knowledge base.  Put another way, the purpose of the
registry is to enable transfer of annotations from one version of a
knowledge base to another without distortion of meaning.

We consider here only registrations for taxa or clades above the
species level, i.e. those that have children.  Ultimately we'll also
need some kind of registry for species, but for now OTT will serve
that purpose.

### Resolution and assignment

The purpose of the registry is to assign ids to compatible nodes in
trees (including taxonomies) so that those nodes can be annotated,
given URLs, and otherwise referenced.

Ideally, for a given tree, we would have a way to assign registrations
(or their ids) to compatible nodes in the tree, with no registration
assigned to two different nodes.  The assignment map would be used to
generate an id when annotating a node, and a resolution map (in the
opposite direction) would resolve a registration id assigned to a node
back to the node again when an annotation using that id is
encountered.  When applying an annotation to a tree different from the
one against which it was created (such as a successor version of a
taxonomy), there might be a unique node in second tree compatible with
the registration id, in which case we can take both nodes (the one in
the firstree, and the one in the second) to stand for the same clade.

An injective mapping from nodes to compatible registrations is not
always possible.  For example, the registry may contain no
registrations compatible with a given node, or there may be ambiguity,
i.e. multiple nodes compatible with a registration.  Usually (always?
I don't know) it is possible, however, to obtain an injective mapping
by extending the registry with new registrations for nodes that do not
map or map ambiguously.  Ordinarily, when assigning registrations to
nodes, the registry will have been extended in this way.

### Operations on registries

The registry implementation supports two operations:

* resolution/assignment: given a tree and a registry, obtain a correspondence
  between nodes and compatible registrations that is as close as
  possible to being an injective mapping
* extension: given a tree and a registry, extend the registry so that such such a
  correspondence is an injective mapping.  An extension of a registry
  has all of its registrations, unchanged, plus additional ones as 
  needed.

### Membership constraints

A registry contains information that permits determining the
compatibility of nodes with registrations.  
Compatibility information consists of membership constraints.

Membership constraints are specified by two sets of registrations
(registration ids), one of *inclusions* and one of *exclusions*.
(Collectively inclusions and exclusions are called *samples*.)  An
inclusion constraint is met by any node from which the node
corresponding to the inclusion registration descends.  An exclusion
constraint is met by any node from which the node corresponding to the
exclusion registration does not descend.  If a sample does
not resolve to a node, then it is not determined whether the
constraint is met.

A pair of such constraint sets is called a *split*.

Observe that the set of nodes satisfying a set of membership
constraints (assuming it contains at least one inclusion) will form a
path in the tree, starting with the nearest common ancestor of the
inclusions and going up until just below the first ancestor that contains
an exclusion node (or up to the root of the tree, if there are no
exclusions).

### Resolution

The purpose of the registry is to manage the assignment (node to
registration) and resolution (registration to node) maps.

For a given tree T and a registration R, define compat(R,T) = the set
of all nodes in T that are compatible with R.

For a deterministic resolution map, define resolve(R,T) to be the
single member of compat(R,T), if compat(R,T) has cardinality 1, or
undefined otherwise.

### Assigning registrations to nodes

In addition to resolving registrations to nodes, it is also necessary
to assign registrations to nodes that don't have registrations that
uniquely resolve to them.

If only one registration resolves to a node, then that registration is
assigned to the node.

However, more than one registration might resolve to a node, because
multiple registrations may have membership constraints all uniquely
satisfied by that node.

In this case there may be no particular reason to assign one
registration or another to the node, and that's OK.  A choice can be
made arbitrarily.  (Currently the prototype picks the most recently
created registration.)

### Creating new registrations

If a node is not assigned any registration, because no registration
resolves uniquely to that node, then it is (or should be) always
possible to create a new registration that resolves to that node.

Membership constraints are generated that select that node and no
others.  Descendants from each of at least two children are chosen as
inclusions, and one or more samples not descending from the node are
chosen as exclusions.  The resulting registration will, by
construction, resolve to that node.  

When choosing the inclusions and exclusions, nodes are preferred that
are more stable, i.e. more likely to occur in other trees (including
both future and past 'versions' of the given tree).  Assessing future
stability employs a set of heuristics.  For example, nodes more likely
to be suppressed in downstream Open Tree processing (extinct,
unclassified, etc.) are given lower priority, nodes from higher
priority taxonomic sources preferred (e.g. NCBI is higher priority
than GBIF), and nodes associated with a greater number of taxonomic
sources are preferred to those with a lower number.

### Versioning (not yet implemented)

Sometimes a registration will resolve in tree A but not uniquely in tree B, but
there is a registration to a node in tree B that provides a good
alternative.  It is useful to record the connection between the two
registrations, with the new registration being a 'new version' of the
previous one.

Identification of this correspondence cannot be done using only
information in the registry; it is contingent on additional
information maintained by the procedure that creates the new tree.

### Monotypic taxa

If node A has B as a child and no other nodes, then A and B cannot be
distinguished by membership constraints as formulated above.

In this case we employ a kludge.  We posit a 'phantom' second child of
a monotypic node representing a hypothetical taxon that is a member of
the monotypic taxon but not of the child taxon.  The parent's
registration has inclusion of the phantom as a constraint, and the
child's registration has exclusion of the phantom as a constraint.

### Implementation note

The size of the membership constraint set is an implementation
parameter.  One may vary it to vary the tradeoff between
inconsistency, ambiguity, and uniqueness.

### Test framework

The system can be stress tested in many ways; so far I have one
generic test (in registry_test.py) that can be applied to a pair of
trees, and "old" tree A and a "new" tree B.  It performs the following
sequence of operations:

1. Start with an empty registry
2. Extend it by making new registrations for nodes in tree A
3. Resolve registrations against tree B
4. Extend registry by making new registrations for nodes in tree B
   lacking assigned registrations

We can then analyze the outcome as follows.  Look at every node N1 in
tree A that has a counterpart N2 in tree B (as determined by
OTT id).  N1 was assigned registration R1 after step 2, and N2 was
assigned registration R2 after step 4.  If R1 = R2, the registry has
successfully rediscovered that the nodes correspond.  If R1 != R2, but
R1 resolves to N2, we probably have a case where nodes were merged.
Otherwise, the registry has decided that the two nodes, which were
determined to be "the same" by smasher, are so different that they
need distinct ids, i.e. annotations applied to N1 may not be
applicable to N2.

Such a change might occur if the membership constraints for R1 cannot
be satisfied in the new tree, or if a taxon in the old tree is simply
not present ("deleted") from the new tree.

The consequence of a change in assignment (i.e. registry id) is that
occurrences of the old ids in annotations will not resolve in the
newer tree.  The assignment is therefore "lost" if one is consulting
the newer tree to resolve identifiers.  Information about the
identifier is retained in the registry, however, and that may enable
some kind of recovery.

To simplify matters, we ignore 'hidden' nodes (incertae sedis etc.)
in the analysis.

## Results

### One taxonomy compare to the next

One can apply the simple test framework to successive versions of a
taxonomy, such as the Open Tree reference taxonomy (OTT).  
I applied it to the plants branch of OTT 2.8 and the plants branch of
OTT 2.9.  This test is the 'test28_29' target in the Makefile.

In OTT 2.8, the species *Orontium cochinchinense* is chosen as one of
the inclusions for the registration for genus *Orontium*.  In OTT 2.9,
the species has been synonymized with *Acorus calamus*, which
according to OTT 2.9 is not in what it calls the genus *Orontium*.
The registry system therefore thinks the two nodes stand for "different"
clades, and gives them distinct registrations and ids.  As it happens,
the old registration does not resolve in OTT 2.9, i.e. OTT 2.9 has no
taxon equivalent to what OTT 2.8 called *Orontium*.

*Ammochloinae* is similar: The registration changes because inclusion
*Cutandia memphitica* is no longer in the taxon (i.e. is not in the
OTT 2.9 *Ammochloinae* taxon).  However this time the old
registration does resolve; it resolves to the OTT 2.9 taxon
*Parapholiinae*.

There are 351 cases like this one in plants.  In 43 of them, the 2.8
registration resolves in OTT 2.9, to a taxon with a different OTT id.
To put this in perspective, almost all of these cases are at the genus
level, and there are 46510 genera in OTT 2.9.

Every instance of OTT id / registration id inconsistency in this
experiment is of this form.

### Taxonomy compared to synthetic tree

*This section needs to be rewritten in light of new tests experiments.py*

One can also apply the simple test framework to a taxonomy and a
synthetic tree that is based on that taxonomy.  When we apply it to the plants
branch of OTT 2.9 and the plants branch of the draft 4 synthetic tree,
10 non-merge node pairs have "changed" registrations.  In every case
this is because registrations mentioned in membership constraints do
not resolve because the synthetic tree lacks a sample node that's present in
the taxonomy.

The fact that membership constraints are always satisfied is a result
of the way the synthesis procedure assigns OTT ids to internal nodes:
it ensures that a tip descends from a node in the synthetic tree if
and only if the taxon corresponding to the tip descends, in the
taxonomy, from the taxon corresponding to taxon with the assigned id.

Nodes missing from the synthetic tree are almost all 'hidden' nodes,
because the synthesis procedure erases them before processing begins.

Another measurement of interest is that 9,025 plant registrations from
OTT 2.9, e.g. *Halimeda*, become ambiguous (i.e. compatible with
more than one node) when applied to the draft 4 synthetic tree, compared with 10,624 that resolve uniquely using constraints. This means
that the samples chosen based on taxonomy were not 'tight' enough to
hone in on the correct node in the synthetic tree. Either an
exclusion that belongs to a sibling in the taxonomy does not belong to
any sibling in the synthetic tree, or the samples, which come from
different children in the taxonomy, all come from the same child in
the synthetic tree. 

(It should be possible to reduce this number reduced by increasing the
number of samples per taxon.  TBD.)

*Work in progress here. I don't understand these numbers.* 11,699 new,
more specific registrations (8,689 named, 3,010 unnamed or
synthesis-only) are created to separate path ambiguities.  The node
that synthesis has identified as having the same membership as the
taxon should be recorded as the replacement for the ambiguous one (not
yet implemented).

Interesting: 55 registrations resolve to nodes in the synthetic tree
that do not correspond to taxonomy (in the sense of compatible
membership).  These are situations where the membership of the taxon
is not compatible with the membership (descent) of any synthetic tree
node, yet the registry includes a set of membership constraints
satisfied by both a taxon and a synthetic tree node.

This test is the 'test29_4' target in the Makefile.  It currently
accesses draft 4 from a protected location; this should be fixed.

### Other tests

I haven't run tests involving three or more trees, other parts of the
tree, older taxonomies, etc.



## Discussion

### Topological changes to the tree

Suppose that a node A has children in a tree, but the node A in a new
version of the tree (intended to be "the same" as the previous node A)
has no children.  There will be a registration for A that has
membership constraints, but this registration will be useless in the
new version - the registrations given in the constraints will not
resolve (assuming the children have been deleted rather than moved).
It is highly desirable that the registration resolve to the new node A
nonetheless.

Suppose that a node A has no children in a tree, but node A has
children in a new version of the tree.  We will have a registration
for A containing no membership constraints, and the registration will
resolve to A in the original version.  It also resolves to the
child-possessing A node in the new version, assuming all of
the new A's resolved membership constraints are satisfied.

### Polytomy refinement

Suppose a tree contains the arrangement (using Newick notation) (A,B,C)E,
i.e. it has a node E with direct children A, B, C.  The tree is later
'revised' to have ((A,B)D,C)E.  What registrations are assigned to
nodes D and E in the new version?  This depends on which samples were
chosen when E was originally registered.

Suppose E is originally assigned registration R.  If at least one
inclusion descends from A or B and one descends from C, then the new E
node will have only be compatible with the registration for the old E
node, so will be assigned that registration.

On the other hand, if all the inclusions for R descend from A and B, then
both D and E in the new tree will satisfy R's membership constraints.
R will be unrecoverably ambiguous and both D
and E will get new registrations.  The new registration for D will
have inclusions descended from A and B and an exclusion descended from C,
while the new registration for E will have one or more inclusion
descended from D, one or more descended from C, and some exclusion
outside E.

This problem of 'losing' R - its identifier becoming obsolete or
ambiguous as a result of polytomy refinement - can be made less
frequent by increasing the number of samples.  In the
limit this would mean the inclusions list would include all descendant
tips of E at the time that R was created and added to the registry,
and the exclusions list would include all other nodes in the taxonomy.
But no matter how long the inclusions and exclusions lists, the problem
cannot be eliminated completely, because new nodes similar to C can be
introduced in new versions of the tree, creating the same D/E
ambiguity that C creates above.

The new registration may or may not resolve in the old tree, depending
on whether its constraints are resolvable and satisfiable, and it may
or may not resolve to the same node that the previous registration
did.  If the old registration's samples are all included among the new
registration's samples, then at least the new registration cannot
resolve to a different node; at worst it will be inconsistent and fail
to resolve.

As an alternative to sample retention, one can imagine a system that
attempts to ensure that new registrations resolve correctly in old
trees, but it would require keeping all old tree on hand, and this
would be expensive.  (is that right?)


## The code

The registry tool is 
[a Java program](../org/opentreeoflife/registry/Registry.java)
in the org.opentreeoflife.registry
package; the source code is in this repository.  Its main operations
are resolving registrations to nodes in a taxonomy (Registry.resolve method)
and extending a registry with new registrations as needed so that
all nodes in a taxonomy have assigned registrations
(Registry.extend method).  Typically the two are called in sequence for each taxonomy.

To compile:

    cd reference-taxonomy/registry
    make compile

A scripted test is available via 'make test' in the registry directory
- but beware, this does some big file transfers, and can take a while
(15 minutes? haven't measured it).

A variety of other tests, e.g. comparing different versions of OTT,
are listed as other targets in the Makefile.


### Interactive use

The registry references taxonomy support classes defined in the
org.opentreeoflife.taxa package.  It can be scripted using jython.  To
get an interactive jython interpreter, assuming you're in the registry
directory, do:

    export CLASSPATH="../$PWD:../$PWD/lib/*"
    java org.python.util.jython

(probably would be better to create a shell executable and control visibility of classpath)

### Scripted use

In jython one would generally want to start a script with one or both
of the following

    from org.opentreeoflife.taxa import Taxonomy
    from org.opentreeoflife.registry import Registry

To run a jython script from a file:

    java org.python.util.jython filename.py

(Instructions needed on how to do taxonomy and synthetic tree
selections e.g. plants, fungi, chordates.  There are some hints in the .py
file.)

See the (sparse) code comments for some information on how the
resolution and registration operations work.

Extracting subtrees of a synthetic tree: First get the compressed
Newick file from files.opentreeoflife.org, and uncompress putting the
result in e.g. 'draftversion3.tre' or 'draftversion4.tre'.  There are
rules in the Makefile for doing this.  Then

    java -Xmx14G org.python.util.jython
    from org.opentreeoflife.taxa import Taxonomy
    synth3 = Taxonomy.getNewick('draftversion3.tre', 'synth')
    synth3.select('Chloroplastida').dump('plants-synth3/')

Or see rules in the Makefile that accomplish the same thing.

Extracting subtrees of OTT:  First get the compressed tarball from
files.opentreeoflife.org, then unpack to directory ott2.8/ (or ott2.9,
etc.  Then

    java -Xmx14G org.python.util.jython
    from org.opentreeoflife.taxa import Taxonomy
    ott28 = Taxonomy.getTaxonomy('ott2.8/', 'ott')
    ott28.select('Chloroplastida').dump('plants-ott28/')

## Ideas for things to do

Sample choice has a big impact on identifier stability.
Here are some thoughts aimed at increasing stability:

* Filter out 'hidden' nodes from sample sets when the taxon being
  registered is itself not hidden.  (This is to help make sure that
  registrations can be shared between taxonomy and synthetic tree.)
* In selecting samples, prefer registrations that are already in use
  as samples in other registrations.

Changes in nonterminal / terminal status

Sometimes a node's children will all be deleted, and it will go from
being nonterminal to terminal.  This needs to be handled somehow.

And vice versa, children can be added to a formerly nonterminal node,
as when a species are added to genus with no species.



Scholarship

* Compare to phylocode
* Acks
