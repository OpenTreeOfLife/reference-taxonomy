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

The purpose of the registry is to assign ids to nodes in trees
(including taxonomies) so that those nodes can be annotated, used in
URLs, and otherwise referenced.  ("Compatible" is defined below.)

Ideally, for a given tree, we would have an injective mapping from
nodes to their assigned registrations.  Such a mapping could be used
to select a registration id when annotating a node, and (in the
inverse direction) to resolve a registration id to the node again when
an annotation using that id is encountered.  When consulting a
different tree (such as a successor version of a taxonomy), the
registration id might resolve to a node in that tree that is
equivalent in some sense to the node in the original tree.

An injective mapping from nodes to registrations is not always
possible.  For example, the registry may contain no registrations
compatible with a given node, or there may be ambiguity, i.e. no
unique "best" registration for a node or unique "best" node for a
registration.  Usually (always? I don't know) it is possible, however,
to obtain an injective mapping by extending the registry with new
registrations for nodes that do not map or map ambiguously.

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

A registration contains information that permits determining the
compatibility of nodes with that registration.  There are two kinds of
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
registration descends.  If there is no unique sample or exclusion
node, then it is not known whether the constraint is met.

Registration metadata consists of a set of properties that a node
*could* be observed to have; currently those properties are all
whether or not the node has some particular identifier or name, but
this might be expanded in the future.  We cannot insist that a node
have *all* of the properties listed in the registration, because (1)
different trees possess different subsets of the metadata, and (2)
some of the properties, such as the name, are unstable.

Observe that the nodes satisfying a set of topological constraints
(assuming it contains at least one sample) will form a path in the
tree, starting with the nearest common ancestor of the samples and
going up until just below the first ancestor that contains an
exclusion node (or the root of the tree, if there are no exclusion
nodes).

### Compatibility

A registration R and a node N may be compatible, or not, where
compatibility is defined as follows:

If R has topological constraints and they are all satisfied by N, then
R and N are compatible.  (If R also has metadata, that metadata is not
taken into consideration.)

If R has topological constraints and any is not satisfied by N, then R
and N are not compatible.

If R has a topological constraint that has an unresolved sample or
exclusion registration, then it is not clear what to do.  TBD.

If R has no topological constraints, then it must have metadata.  N
and R are then compatible if they have at least one metadata property
(e.g. name or identifier) in common.

### Resolving registrations to nodes

Resolution maps a registration to a set of compatible nodes.
Ordinarily resolution will map to a single node.

For the resolution map we choose the nodes among the compatible nodes
that are the "best metadata match" to the registration.  There may be
no compatible nodes, or there may be two or more compatible nodes that
are equally "good" (typically this means two nodes with the same
name and no other way to distinguish them).

We have to define "best metadata match".  "Best metadata match" is a
heuristic that is almost always just simple name comparison, but is
elaborated because there are multiple kinds of names (identifiers) and
they unfortunately collide and/or change.  "Best metadata match" might
be evaluated as follows:

Let N1, N2 be nodes compatible with R.  Then N2 is a better metadata
match to R than N1 if

* N2 and R have the same source reference (e.g. NCBI taxon id), while
  N1 has a different source reference
* The source references are unavailable or uninformative (don't 
  determine whether N2 is a better match than N1), but N2 and R
  have the same OTT id and N1 has a different one
* Source references and OTT ids are uninformative, but N2 and R
  have the same name and N1 has a different name

The implementation may be more elaborate and clever than this (or
less, which is how it stands right now).  It might be a good idea for
the implementation to give warnings when there are near misses or
contradictions among the properties.

Since location within the tree (plant, animal, etc.) is such an
important means for resolving homonyms, it might be a good idea to add
some location information to the metadata at some point.

### Assigning registrations to nodes

In addition to resolving registrations to nodes, it is also necessary
to assign registrations to nodes.  In good circumstances resolution
(registration to node) and assignment (node to registration) are
inverse operations.

It may be that multiple registrations resolve to the same node,
e.g. if topological constraints apply to multiple nodes or if two
nodes are "best metadata matches" to a registration.  It is then
desirable to pick just one registration as the "best" one to assign to
that node.

If one of the registrations resolves only to that node, and not to any
others, then that registration should be assigned to the node.  But
there may be no such registration, in which case assignment is
ambiguous (i.e. the node is assigned a set of registrations).  This
ambiguity can be corrected by adding a new registration (below).

### Creating new registrations

If a node is assigned no "best" registration, either because there is no
registration that resolves to it or because there's no single best
registration resolving to it, then one can create a new registration
that resolves to that node and to no other node in the tree.

If the node is terminal, then it is likely that there is associated
metadata that uniquely picks out the node.  E.g. if the tree is
provided in Newick form, then all the tip labels should be distinct.
In this case the new registration has metadata based on the tip label
that applies better to that taxon than to any other.  (At least one
hopes.)

If the node is internal, then descendants from each of at least two
children are chosen as samples, and descendants from at least one of
the node's siblings are chosen as exclusions.  The resulting
registration will be, by construction, unique to that node.  (The
node's metadata, if there is any, becomes the metadata in the
registration.)

When choosing the samples and exclusions, the child nodes are sorted
according to quality heuristics that try to pick out nodes that are
more stable.  E.g. nodes likely to be suppressed in downstream
processing (extinct, unclassified, etc.) are given lower priority than
visible ones, and nodes associated with a greater number of taxonomic
sources are given priority over those with a lower number.

### Monotypic taxa

If node A has B as a child and no other nodes, then A and B cannot be
distinguished by topological constraints as formulated above.  They
must therefore be distinguished, if at all, using metadata.  This is
one of the rare cases where the metadata for an internal node matters.

### Topological changes to the tree

Suppose that a node A has children in a tree, but the node A in a new
version of the tree (intended to be "the same" as the previous node A)
has no children.  There will be a registration for A that has
topological constraints, but this registration will be useless in the
new version - the registrations given in the constraints will not
resolve (assuming the children have been deleted rather than moved).
It is highly desirable that the registration, which contains the
appropriate metadata, resolve to the new node A nonetheless.  Work in
progress.

Suppose that a node A has no children in a tree, but node A has
children in a new version of the tree.  We will have a registration
for A containing no topological constraints, and the registration will
resolve fine to A in the original version.  It should also resolve to
the child-possessing A node in the new version.  I think this works
but it should be tested.

### Polytomy refinement

Suppose a tree has (using Newick notation) the arrangement (A,B,C)E,
i.e. it has a node E with direct children A, B, C.  The tree is then
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
But the problem cannot be eliminated completely, because new nodes
similar to C can be introduced in new versions of the tree, creating
the same D/E ambiguity that C creates above.


### The code

The registry tool is 
[a Java program](../org/opentreeoflife/registry/Registry.java)
in the org.opentreeoflife.registry
package; the source code is in this repository.  Its main operations
are resolving (Registry.resolve method) and registering
(Registry.register method) the nodes in a taxonomy against a registry.
It references taxonomy support classes defined in the
org.opentreeoflife.taxa package.  It can be scripted using jython.  To
get an interactive jython interpreter, do:

    cd reference-taxonomy
    make compile
    export CLASSPATH="$PWD:$PWD/lib/*"
    java org.python.util.jython

(probably would be better to create a shell executable and control visibility of classpath)

At this point one would generally want to do one or both of the following

    from org.opentreeoflife.taxa import Taxonomy
    from org.opentreeoflife.registry import Registry

To run a jython script from a file:

    java org.python.util.jython filename.py

There's a test script in registry/registry_test.py, but it requires
some setup.  See the comments in the file.  Work in progress.

    cd registry
    java org.python.util.jython registry_test.py

or

    java org.python.util.jython registry_test.py taxonomy1/ taxonomy2/

(Instructions needed on how to do taxonomy and synthetic tree
selections e.g. plants, fungi, chordates.  There are some hints in the .py
file.)

See the (sparse) code comments for some information on how the
resolution and registration operations work.

Currently topological constraints consist of at most two samples and
at most one exclusion.  The number of samples can be increased easily;
increasing the number of exclusions will require additional coding.
