Clade identifier registry 
--------------------

The 'registry' tool introduces a data structure called a registry.  A
registry is a table of entries called registrations.  Each
registration is intended to correspond to a clade.  Each registration
has a unique id, and if all goes well, such an id can be used to
identify a clade (the one corresponding to the registration).

Registration ids are designed for use in long-lived documents and data
sets, such as annotations, when there is a need for a stable reference
to a clade.

### Resolution

A given tree (such as a taxonomy or synthetic tree) can be resolved
against a registry.  Resolution results in a many to many
correspondence between registrations in the registry and nodes in the
tree.  "Many to many" means that multiple registrations may map to the
same node (meaning that the registrations cannot be distinguished in
the given tree), and that multiple nodes may map to the same
registration (the registration may be ambiguous - it applies equally
well to multiple nodes).

### Registration

A second operation is to add sufficient new registrations to a
registry in ensure that resolving the tree in the extended registry
will lead to all nodes in the tree resolving uniquely.

### Constraints and metadata

A registration contains information that permits determining the
compatibility of nodes with that registration.  There are two kinds of
compatibility information, topological constraints and metadata, and a
registration may have either kind by itself, or both kinds.

Topological constraints are specified by two sets of registrations
(registration ids), one of *samples* and one of *exclusions*.  A
sample constraint is met by any node from which the node mapped to the
sample registration descends.  An exclusion constraint is met by any
node from which the node mapped to the exclusion registration
descends.

Registration metadata consists of a set of properties (name, source
reference, OTT id) that a node *could* be observed to have.  We cannot
insist that a node have *all* of the properties listed in the
registration, because (1) different trees possess different subsets of
the metadata, and (2) some of the properties, such as the name, are
unstable.

### Resolution

For any registration R we would like to find the 'best' node in the
tree to which R's identifier could map.

Let T = those nodes satisfying R's resolved topological constraints
and M = those nodes sharing any metadata property with R.

(A constraint is *resolved* if its sample or exclusion resolves
uniquely to a node.  Think about this.  We may want to require that
all constraints resolve, or that at least one exclusion and two
samples resolve.)

There are three cases:

* R has both kinds of constraint, and T and M intersect.  In this case R resolves to
  the nodes in the intersection or T and M that have the best metadata
  matches (see below) with R.
* R has topological constraints but
  T and M do not intersect.
  In this case R resolves to all nodes in T.  Usually this will be one node, but
  it could be several, or none.
* R has metadata constraints only.  In this case R resolves to the best metadata
  matches with R, if there are any.  Usually there will be only one
  best metadata match, but inadequate or ambiguous metadata could lead
  to there being more than one.  There could be none.

We have to define "best metadata match".  "Best metadata match" is a
heuristic and might be evaluated as follows:  Let N1, N2 be nodes.
Then N2 is a better metadata match to R than N1 if

* N2 and R have the same source reference, while N1 has
  a different source reference
* The source references are unavailable or uninformative, but N2 and R
  have the same OTT id and N1 has a different one
* Source references and OTT ids are uninformative, but N2 and R
  have the name and N1 has a different name

The implementation may be more elaborate and clever than this (or
less, which is how it stands right now).  It might be a good idea for
the implementation to give warnings when there are near misses or
contradictions among the properties.

Since location within the tree (plant, animal, etc.) is such an
important means for resolving homonyms, it might be a good idea to add
this to the metadata at some point, but the implementation might be
quite tricky.

### Mapping nodes to registrations

It may be that multiple registrations resolve to the same node,
e.g. if topological constraints apply to multiple nodes (such as when
a polytomy is resolved) or if node metadata is ambiguous.  It is then
desirable to pick just one registration as the "best" one for that
node.

If a registration resolves only to that node, and not to any others,
then that registration is a pretty good choice for the "best" one for
that node.  I haven't yet thought of any other rules for making this
choice.

### Creating new registrations

If a node has no "best" registration, either because it has no
registration that resolves to it or because there's no single best
registration resolving to it, then one can create a new registration
that resolves to that node and to no other node in the tree.

If the node is terminal, then it is likely that there is associated
metadata that uniquely picks out the node.  E.g. if the tree is
provided in Newick form, then all the tip labels should be distinct.
In this case the new registration has metadata based on the tip label
that applies better to that taxon than to any other.

If the node is internal, then descendants from each of at least two
children are chosen as samples, and descendants from at least one of
the node's siblings can be chosen as exclusions.  The node's metadata,
if any, can also be retained in the registration.

When choosing the samples and exclusions the nodes are sorted
according to quality heuristics that try to pick out nodes that are
more stable.  E.g. hidden nodes (extinct, incertae sedis, etc.) are
given lower priority than visible ones, and nodes with a greater
number of taxonomic sources are given priority over those with a lower
number.

### The code

The registry tool is a Java program in the org.opentreeoflife.registry
package.  Its main operations are read and write a registry and
resolve and register a taxonomy.  It references taxonomy support
classes defined in the org.opentreeoflife.taxa package.  It can be
scripted using jython.  To get an interactive jython interpreter, do:

    cd reference-taxonomy
    make compile
    export CLASSPATH="$PWD:$PWD/lib/*"
    java org.python.util.jython

At this point one would generally want to do one or both of the following

    from org.opentreeoflife.taxa import Taxonomy
    from org.opentreeoflife.registry import Registry

(probably would be better to create a shell executable and control visibility of classpath)

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
