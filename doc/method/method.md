
# Taxonomy assembly

The assembly process works, in outline, as follows:

 1. Start with an ordered list of source taxonomies S1, S2, ...
 1. Initialize the 'union' taxonomy U to be empty
 1. For each source S:
     1. Load, normalize, and patch S
     1. Align S to U, i.e. match the nodes of S to nodes of U, where possible
     1. Merge S into U
         1. Unaligned subtrees of S (subtrees of S that contain
            no matched nodes other than their root) are grafted onto U
         1. Where S provides a more resolved classification than U, 
            'insert' unmatched nodes of S into U
 1. Apply patches and perform ad hoc postprocessing steps
 1. Assign OTT identifiers to the nodes of U, by aligning the previous 
    version of OTT to U

The hierarchical relationships are therefore determined by priority:
ancestor/descendant relationships in an earlier source S may be
refined by a later source S', but are never overridden.

For each new version of OTT, construction begins de novo, so that we
always get the latest version of the source taxonomies.  The only
state that persists from one version of OTT to the next is OTT
identifier assignment.

Details of each step follow.

## Source taxonomy import and normalization

Each source taxonomy has its own import procedure, usually a file
transfer followed by application of a format conversion script.
E.g. the GBIF taxonomy is downloaded from the GBIF web site, and then
converted to the Open Tree 'interim taxonomy format' by a python
script.  Given the ITF files, the taxonomy can be 'loaded' by the
assembly procedure.

In two cases an imported source taxonomy is split into parts before
processing continues, so that the parts can be positioned in different
places in the align/merge order: Index Fungorum is split into high
priority Fungi and lower priority everything-except-Fungi, and WoRMS
is split into Malacostraca and everything-except-Malacostraca.

After each source taxonomy is loaded, the following two normalizations
are performed:

 1. Child taxa of "containers" in the source taxonomy are made to be
    children of the parent container's parent.  "Containers" are
    grouping nodes in the tree that don't represent taxa, for example
    "incertae sedis" or "environmental samples" nodes.  The fact that
    the child had been in a container is recorded as a flag on the
    child node.
 1. Monotypic homonym removal - when taxon with name N has as its
    only child another taxon with name N, the parent is removed.
    This is done to avoid an ambiguity when later on a node with name
    N needs to be matched.

## Source patching

Most source taxonomies have ad hoc patches that are applied at this
point.  Capitalizations andmisspellings can be fixed at this point,
and synonyms added (e.g. 'Stramenopiles' for 'Heterokonta'), to
improve matches between taxonomies.  Other patches such as extinct or
extant annotations or topology changes can be applied at this point.
Generally it's best to handle taxonomic problems by correcting source
taxonomies at this point, but if it is difficult or inconvenient to
localize the problem to one or more sources, repair can be left until
the final patch phase after all source taxonomies have been loaded and
merged.

Editing the source taxonomy presents a provenance tracking problem
that has yet to be addressed.

## Align source to union

It is important that source taxa be matched with union taxa when
and only when this is appropriate.  A mistaken identity between a
source taxon and a union taxon can be disastrous, leading not just to
an incorrect tree but to downstream curation errors in OTU matching
(e.g. putting a snail in flatworms).  A mistaken non-identity
(separation) can also be a problem, since taxon duplication leads to
incomplete annotation (information about one copy not propagating to
the other) and to loss of unification opportunities in phylogeny
synthesis.

The process of matching source nodes with union nodes, or equivalently
determining the identity of the corresponding taxa, is called
"alignment" in the following.

Ultimately there is no automatable test to determine whether alignment
has been done correctly.  The process is necessarily heuristic.
Difficult cases must be investigated manually and either repaired
manually (patched) or repaired by improvements to the heuristics.

Alignment proceeds one name at a time.  First, a list of candidate
union matches is made for the source nodes having that name.  Then, a
set of heuristics is applied to find a unique best union node match,
if any, for each source node having the given name.

[Going one name at a time is not really correct since it does not
handle synonyms very well.  There is a kludge for dealing with
synonyms, but it is not satisfactory.  An experimental version of
the code deals with one source node at a time, with the potential for
a different candidate set for each node having a given name, based on
its synonyms.]

Heuristics are of two kinds:

 1. Separation heuristics identify union nodes that mustn't be match
    targets for the source node.
 2. Preference heuristics attempt to separate the set of candidate
    targets into two groups, those that are more appropriate vs. less
    appropriate as match targets for the given source node.

A heuristic is essentially a two-place predicate, applied to a source
node and a union node, that either succeeds (the two could match
according to this heuristic) or fails (they do not match according to
this heuristic).  The method for applying the heuristics is as
follows:

 1. Start with a source node and a set C of union nodes (candidates).
 2. For each heuristic H:
      1. Let C' = those members of C for which H succeeds.
      2. If C' is singleton, we are done; the source node matches the member of C'.
      3. If H is a separation heuristic, replace C with C'.  If C is now empty, we're done.
      4. If H is a preference heuristic, and C' is nonempty, replace C
      with C'.  (If C' is empty or C' = C, it gave us no information,
      and we ignore it.)
 3. If C is empty, no union node matches the source node.  (A polysemy may be created in the merge phase.)
 4. If C is singleton, its member is taken to be the correct match.
 5. Otherwise, the source node is ambiguous.

If the process ends with multiple candidates, an unresolvable
ambiguity is declared.  If the ambiguous source node has no children,
it is dropped - which is OK because it probably corresponds to one of
the existing candidates and therefore would make no new contribution
to the union taxonomy.  If the ambiguous source node has children, it
is treated as a potentially new node, possibly turning an N-way
polysemy into an N+1-way polysemy, which is almost certainly wrong.
Usually, subsequent analysis determines that the taxon is inconsistent
with the union taxonomy and it is dropped.  If it is not dropped, then
this is a rare and troublesome situation that requires manual
intervention.

### Candidate identification

The analysis loop begins by identifying candidate nodes.

Potentially, any source node might match any union node, because we do
not have complete information about synonymies, and we have no
information that can be used to definitively rule out any particular
match.  Of course considering all options is not practical, so we
limit the search to union nodes that have a name (primary or synonym)
in common with the source node.

In some cases it would be possible to identify further candidates by
looking at identifiers; for example, it often happens that when NCBI
is revised, a taxon keeps its NCBI identifier, but changes its name.
This is not currently handled.

### Separation heuristic: Skeleton taxonomy

The heuristics are described in the order in which they are applied.

If taxa A and B belong to taxa C and D (respectively), and C and D are
known to be disjoint, then A and B can be considered distinct.  For
example, land plants and rhodophytes are disjoint, so if NCBI says its
_Pteridium_ is a land plant, and WoRMS says its _Pteridium_ is a
rhodophyte, then either there's been a gross misclassification in one
of the sources, or NCBI _Pteridium_ and WoRMS _Pteridium_ are
different taxa.  Since misclassifications at the level of rhodophytes
vs. plants are much rarer than name-strings that are polysemous across
major groups, it's better to assume we have a polysemy.

Drawing a 'polysemy barrier' between plants and rhodophytes
resembles the use of nomenclatural codes to separate polysemies,
but the codes are not fine grained enough to capture distinctions that
actually arise.  For example, there are many [how many? dozens?
hundreds?] of fungus/plant polysemies, even though the two groups are
covered by the same nomenclatural code.

Some cases like this one are actual differences of opinion concerning
classification, and different placement of a name between two source
taxonomies does not mean that we are talking about different taxa.

The separation heuristic used here works as follows.  We establish a
"skeleton" taxonomy, containing about 25 higher taxa (Bacteria,
Metazoa, etc.).  Every source taxonomy is aligned - manually, if
necessary - to the skeleton taxonomy.  (Usually this initial
mini-alignment is by simply by name, although there are a few
troublesome cases, such as Bacteria, where higher taxon names are
polysemies.)  For any node/taxon A, the smallest skeleton taxon
containing A is called its _division_.  If taxa A and B with the same
name N have divisions C and D, and C and D are disjoint in the
skeleton, then A and B are taken to be distinct.

There are many cases (about 4,000) where A's division (say, C) is
properly contained in B's nearest division (say, D) or vice versa.  A
and B are therefore not separated.  It is not clear what to do in
these cases.  In many situations the taxon in question is unplaced in
the source (e.g. in Eukaryota but not in Metazoa) and ought to be
matched with a placed taxon in the union (in both Eukaryota and
Metazoa).  In OTT 2.9, [?? figure out what happens - not sure], but
the number of affected names is quite high, so many false polysemies
are created.  Example: the skeleton taxonomy does not separate
_Brightonia_ the mollusc (from IRMNG) from _Brightonia_ the echinoderm
(from higher priority WoRMS), because there is no division for
echinoderms, so [whatever happens].  [need example going the other
way.]

### Preference heuristic: Lineage

Taxa with a common lineage are preferred to those that don't.  The
rule here is: If A's 'quasiparent name' is the name of an ancestor of
B, or vice versa, then B is a preferred match for A.  The 'quasiparent
name' of A (or B) is the name of the nearest ancestor of A (or B)
whose name is not a prefix of A's name.  E.g. if A is a species then
the genus name is skipped (because it is uninformative) and A's
quasiparent name is that of the containing family (or subfamily, etc.)
name.

### Separation heuristic: Incompatible membership

For each source or union node A, we define its 'membership proxy' as
follows.  Let S be the set of names that are (a) present in both
taxonomies and (b) unambiguous in each taxonomy.  Then the membership
proxy of A is the set of names of tips under A that are also in S.

If A and B both have nonempty membership proxies, and the proxies are
disjoint, then we consider A and B to be incompatible, and prevent a
match between them.

The incompatibility calculation relies an a bit of implementation
cleverness for speed, so that it is not necessary to create data
structures for every proxy.  Most of the time the calculation is a
simple range check.

This heuristic has both false negatives (taxa that should be combined
but aren't) and false positives (cases where merging taxa does not
lead to the best results).

### Other heuristics

1. Overlapping membership:
   We prefer a candidate node if its membership proxy (see above) overlaps
   with the source node's.
1. Same source id: candidate B is preferred if A's primary source id
   (e.g. NCBI:1234) is the same as B's.  This heuristic applies mainly
   during the final id assignment phase, since before this point no
   identifiers are shared between the source and union taxonomies.
1. Any source id: candidate B is preferred if any of A's source ids matches any of
   B's.
1. 'Weak division' separation:
   B and A are considered distinct at this point if they are in different
   divisions.  E.g. [looking in log files for examples. need better
   instrumentation.]  For example,
   _Brightonia_ in division Mollusca is distinguished from 
   _Brightonia_ in division Metazoa (which includes Mollusca), which turns out to be correct because
   the second _Brightonia_ is an echinoderm, not a mollusc.
1. Rank:
   B is preferred if A and B both have designated ranks, and the ranks are the same.
1. Synonym avoidance:
   B is preferred if its primary name is the same as A's.


### Collisions

There are often false polysemies within a source taxonomy - that is, a
name belongs to more than one node in the source taxonomy, when on
inspection it is clear that this is a mistake in the source taxonomy, and there
is really only one taxon in question.  Example: _Aricidea rubra_
occurs twice in WoRMS, but the two nodes are very close in the
taxonomy and one of them appears to be a duplication.

If the union taxonomy has an appropriate node with that name, then
multiple source taxonomy nodes can match it, the collision will be
allowed, and the polysemy will go away.  However, if the union
taxonomy has no such node, both source nodes will end up being copied
into the union.  This is an error in the method which needs to be
fixed.

[The above is a bit of a lie.  In fact some amount of collision
prevention logic remains in the code (class `Matrix`), so we do not
always remove false polysemies in a source, even when there is a union
node to map them to.  I don't really understand how the code works or
whether it's doing the right thing; need to review.]


## Merge source into union

Following alignment, taxa from the source taxonomy are merged into the
union taxonomy.  This is performed via bottom-up traversal of the
source.  A parameter 'sink' is passed down to recursive calls, and is
simply the nearest (most tipward) alignment target seen so far on the descent.
It is called 'sink' because it's the node to which orphan nodes will
be attached when a source taxon is dropped due to inconsistency with
the union.

The following cases arise during the merge process:

 * Source taxonomy tip: if the source node is matched to a union node,
   there is nothing to do.  If it's unmatched and not blocked for some
   reason (e.g. ambiguity), then create a corresponding tip node in
   the union, to be attached later on.  The source tip is then matched
   to the new union tip.

 * Source taxonomy internal node: the source node's children have already been
   processed (recursively), and are all matched to targets in the
   union (or, rarely, blocked, see above).  The targets are either
   'old' (they were already in the union before the merge started) or
   'new' (created in the union during the merge process).  One of the
   following rules then applies (assuming in each case that none of
   the previous rules apply):

     * Matched internal node: Attach any new targets to the internal
       node's alignment target.

     * Graft: all targets are new.  Create a new union node, and attach
       the new targets to it.

     * Inconsistent: if the old target nodes do not all have the same parent,
       attach the new target nodes to the sink, and flag them as 'unplaced'.

     * Refinement: if every child of the sink (in the union taxonomy)
       is the match of some source node, we say the source
       node 'refines' the union taxonomy.  Create a new union node to
       match the source node, and attach both old and new targets to it.

     * Merge: some child of the sink is _not_ the match of any source
       node.  Attach the new targets to the common parent of the old
       targets, discarding the source internal node.

The actual logic is more complicated than this due to the need to
properly handle unplaced (incertae sedis) taxa.  Generally speaking,
unplaced taxa are ignored in the calculations of inconsistency and
refinement.  The source taxonomy might provide a better position for
an unplaced taxon (more resolved, or resolved), or not.  Unplaced taxa
should not inhibit the application of any rule, but they shouldn't get
lost during merge, either.

## Postprocessing

After all source taxonomies are aligned and merged, general ad hoc
patches are applied to the union taxonomy.  Some patches are
represented in the 'version 1' (TSV-based) form, and others in the
'version 2' (python-based) form.  A further set of patches for
microbial Eukaryotes comes from a spreadsheet prepared by the Katz
lab.

There is a special step to locate taxa that come only from PaleoDB
and mark them extinct.

## Id assignment

The final step is to assign OTT ids to taxa.  This is done by aligning
the previous version of OTT to the new union taxonomy.  After
transferring ids of aligned taxa, any remaining union taxa are given
newly 'minted' identifiers.
