Clade identifier registry 
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

### Resolution and assignment

The purpose of the registry is to assign ids to compatible nodes in
trees (including taxonomies) so that those nodes can be annotated,
used in URLs, and otherwise referenced.

Ideally, for a given tree, we would have a way to assign registrations
(or their ids) to compatible nodes in the tree, with no registration
assigned to two different nodes.  The assignment map would be used
when annotating a node, and a resolution map (in the opposite
direction) would resolve a registration id assigned to a node back to
the node again when an annotation using that id is encountered.  When
applying an annotation to a tree different from the one against which
it was created (such as a successor version of a taxonomy), there
might be a node in second tree compatible with the registration id, in
which case we can take both nodes (the one in the firstree, and the
one in the second) to stand for the same clade.

An injective mapping from nodes to compatible registrations is not
always possible.  For example, the registry may contain no
registrations compatible with a given node, or there may be ambiguity,
i.e. no unique compatible node for a registration.  Usually (always?
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

### Registration constraints and metadata

A registry contains information that permits determining the
compatibility of nodes with registrations.  There are two kinds of
compatibility information, topological constraints and metadata, and a
registration may have either kind by itself, or both kinds.
Topological constraints are used to resolve registrations to internal
nodes in trees, while metadata is mainly used to resolve to terminal
nodes.  In exceptional circumstances both kinds of information are
used in resolution, or metadata is used to resolve to an internal
node.

Topological constraints are specified by two sets of registrations
(registration ids), one of *samples* and one of *exclusions*.  A
sample constraint is met by any node from which the node corresponding
to the sample registration descends.  An exclusion constraint is met
by any node from which the node corresponding to the exclusion
registration does not descend.  If there is no unique sample or exclusion
node, then it is not determined whether the constraint is met.

Registration metadata consists of a set of properties that a node
*could* be observed to have.  Currently those properties are all
whether or not the node has some particular identifier or name, so
when you read "metadata" you can substitute "identifier" or "name",
but this might be expanded in the future.  We cannot insist that a
node have *all* of the properties listed in the registration, because
(1) different trees possess different subsets of the metadata, and (2)
some of the properties, such as the name, are unstable across taxonomy
releases.

Observe that the set of nodes satisfying a set of topological
constraints (assuming it contains at least one sample) will form a
path in the tree, starting with the nearest common ancestor of the
samples and going up until just below the first ancestor that contains
an exclusion node (or up until the root of the tree, if there are no
exclusion nodes).

### Resolution

The purpose of the registry is to manage the assignment (node to
registration) and resolution (registration to node) maps.  The
resolution map is defined as follows, and then the assignment map is
derived from it.  This design is a best effort heuristic for the
notion of nodes in different trees standing for the same clade.

Let T be a tree, let R be a given registration.  

Define m(R,T) = the node N in T that is the only one that is
metadata-compatible with R, or undefined if there is not exactly one
such node.

Define resolve(R,T) to be either a node in T, or undefined, as
follows:

* If R occurs as a constraint sample or exclusion in some other registration, 
  then resolve(R,T) = m(R,T).
* If R has no topological constraints, then
  resolve(R,T) = m(R,T).
* If R has topological constraints and the constraints are all satisfied by 
  exactly one node N, then resolve(R,T) = N.
* If R has topological constraints and the constraints are all
  satisfied by more than one node, and the node m(R,T) is among those
  nodes, then resolve(R,T) = m(R,T).
* If R has topological constraints and any constraint is not satisfied by 
  any node N, then resolve(R,T) = undefined.
* If the previous conditions don't apply, and any of the samples
  used in expressing a constraint does not resolve, then
  resolve(R,T) = m(R,T).

Resolution by metadata is based on registrations and nodes on both
values of attributes such as identifier in source taxonomy, OTT id, or
name, and on their presence or absence.  Attributes are checked in a
priority order.  If a node and a registration both have a particular
attribute, they are metadata-compatible if and only if that attribute
has the same value for both.  m(R,T) is the unique such node if there
is one.  Otherwise, the next attribute in order is considered.  If no
attribute has values for both the node and the registration, then no
nodes (or equivalently all nodes) are compatible with the
registration.

The procedure is heuristic, since sometimes attribute values can
'change' and we want to allow for that.

TBD: It would be a good idea for the implementation to give warnings when
there are near misses or contradictions among the attributes.

TBD: Because location within the tree (plant, animal, etc.) is such an
important means for resolving homonyms, it might be a good idea to add
some location information to the metadata at some point.

### Assigning registrations to nodes

In addition to resolving registrations to nodes, it is also necessary
to assign registrations to nodes.

If only one registration resolves to a node, then that registration is
assigned to the node.

However, more than one registration might resolve to a node, both
because multiple registrations in the tree may have metadata that's
compatible with that node, and because multiple registrations may have
topological constraints all uniquely satisfied by that node.

In this case there is no particular reason to assign one registration
or another to the node, so a choice can be made arbitrarily.  (In the
case of topological ambiguity, this would be a chance to decide
whether we have a preference for less inclusive node-based clades, or
more inclusive stem-based clades; for now we are neutral.)

### Creating new registrations

If a node is not assigned any registration, then it is possible to
create a new registration that resolves to that node and to no other
node in the tree.

If the node is terminal, then it is likely that there is associated
metadata that distinguishes that node from all other nodes in the
tree.  E.g. if the tree is provided in Newick form, then one would
hope that all the tip labels are distinct.  If the node metadata
reflects all information present in the Newick tip, then the node will
be distinguishable.

If the node is internal, then topological constraints are generated.
Descendants from each of at least two children are chosen as samples,
and descendants from at least one of the node's siblings (or possibly
'aunts and uncles' and so on) are chosen as exclusions.  The resulting
registration will, by construction, resolve to that node.  (The node's
metadata, if there is any, becomes the metadata in the registration.)

When choosing the samples and exclusions, nodes are preferred that are
more stable, i.e. more likely to occur in other trees (future
versions).  Nodes more likely to be suppressed in downstream Open Tree
processing (extinct, unclassified, etc.) are given lower priority, and
nodes associated with a greater number of taxonomic sources are
preferred to those with a lower number.

### Monotypic taxa

If node A has B as a child and no other nodes, then A and B cannot be
distinguished by topological constraints as formulated above.  They
must therefore be distinguished, if at all, using metadata.  This is
one of the rare cases where the metadata for an internal node matters.

A limitation of this system is that every node must have either
metadata or more than one child.

### Topological changes to the tree

Suppose that a node A has children in a tree, but the node A in a new
version of the tree (intended to be "the same" as the previous node A)
has no children.  There will be a registration for A that has
topological constraints, but this registration will be useless in the
new version - the registrations given in the constraints will not
resolve (assuming the children have been deleted rather than moved).
It is highly desirable that the registration, which contains the
appropriate metadata, resolve to the new node A nonetheless.  This
contingency is supported by the resolution rules given above.

Suppose that a node A has no children in a tree, but node A has
children in a new version of the tree.  We will have a registration
for A containing no topological constraints, and the registration will
resolve to A in the original version.  It also resolve to the
child-possessing A node in the new version, assuming all of
the new A's resolved topological constraints are satisfied.

### Polytomy refinement

Suppose a tree contains the arrangement (using Newick notation) (A,B,C)E,
i.e. it has a node E with direct children A, B, C.  The tree is later
'revised' to have ((A,B)D,C)E.  What registrations are assigned to
nodes D and E in the new version?  This depends on which samples were
chosen when E was originally registered, and on what metadata is
available.

Suppose E is originally assigned registration R.  If at least one
sample descends from A or B and one descends from C, then the new E
node will have only be compatible with the registration for the old E
node, so will be assigned that registration.

On the other hand, if all the samples for R descend from A and B, then
both D and E in the new tree will satisfy R's topological constrains.
If R and the new E have metadata, and the new E is a better metadata
match to R than D, then E will be assigned R and D will get a new
registration (one with a descendant of C as an exclusion).  But if
metadata is not helpful, R will be unrecoverably ambiguous and both D
and E will get new registrations.  The new registration for D will
have samples descended from A and B and an exclusion descended from C,
while the new registration for E will have one or more sample
descended from D, one or more descended from C, and some exclusion
outside E.

This problem of 'losing' R - its identifier becoming obsolete or
ambiguous as a result of polytomy refinement - can be made less
frequent by increasing the number of samples and exclusions.  In the
limit this would mean the samples list would include all descendant
tips of E at the time that R was created and added to the registry.
But no matter how long the samples and exclusions lists, the problem
cannot be eliminated completely, because new nodes similar to C can be
introduced in new versions of the tree, creating the same D/E
ambiguity that C creates above.

### Implementation note

Currently topological constraints consist of at most two samples and
at most one exclusion.  The number of samples can be increased easily;
increasing the number of exclusions will require additional coding.


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

## Outcomes of tests

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

Such a change might occur if the topological constraints for R1 cannot
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

### Comparing taxonomy with synthetic tree

We can apply this test to a taxonomy and a synthetic tree produced
based on it.  When we apply it to the plants branch of OTT 2.9 and the
plants branch of the draft 4 synthetic tree, 10 non-merge node pairs
have "changed" registrations.  In every case this is because
registrations mentioned in topological constraints do not resolve
because the synthetic tree lacks a node that's present in the
taxonomy.

The fact that topological constraints are always satisfied is a result
of the way the synthesis procedure assigns OTT ids to internal nodes:
it ensures that a tip descends from a node in the synthetic tree if
and only if the taxon corresponding to the tip descends, in the
taxonomy, from the taxon corresponding to taxon with the assigned id.

Nodes missing from the synthetic tree are almost all 'hidden' nodes,
which means that we have 

This test is the 'test29_4' target in the Makefile.

### Comparing taxonomy versions

We can also apply the test to successive versions of OTT.  When we
apply it to the plants branch of OTT 2.8 and the plants branch of OTT
2.9, 859 non-merge node pairs have distinct registrations.  In
every case this is because the topological constraints from the old
tree are not satisfied in the new tree.

This test is the 'test28_29' target in the Makefile.

### Other tests

I haven't run tests involving three or more trees.
