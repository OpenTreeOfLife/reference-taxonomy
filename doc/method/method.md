
# Taxonomy assembly

The assembly process works, in outline, as follows:

 1. Start with an ordered list of source taxonomies S1, S2, ...
 1. Initialize the 'union' taxonomy U to be empty
 1. For each source S:
     1. The nodes of S are aligned with nodes of U
        where possible (in a manner explained below)
     1. Unaligned subtrees of S - i.e. subtrees of S that contain
        no aligned nodes other than their root - are grafted onto U
     1. Where S provides a more resolved classification than U, unaligned
        internal nodes of S are 'inserted' into U
 1. Perform ad hoc postprocessing steps
 1. Assign OTT identifiers to the nodes of U

The hierarchical relationships are therefore determined by priority:
ancestor/descendant relationships in an earlier source S may be
subdivided by a later source S', but are never overridden.

For each new version of OTT, construction begins de novo, so that we
always get the latest version of the source
taxonomies.

Details of each step follow.

## Source taxonomy preparation

Each source taxonomy has its own import procedure, usually a file
transfer followed by application of a format conversion script.
E.g. the GBIF taxonomy is downloaded from the GBIF web site, and then
converted to the Open Tree 'interim taxonomy format' by a python
script.  Given the ITF files, the taxonomy can be 'loaded' by the
assembly procedure.

After each source taxonomy is loaded, the following two normalizations
are performed:

 1. Child taxa of "containers" in the source taxonomy are made to be
    children of the parent container's parent.
    "Containers" are grouping nodes in the tree that don't represent 
    taxa, for example "incertae sedis" nodes.  The fact that the child
    had been in a container is recorded as a flag on the child node.
 1. Monotypic homonym removal - when taxon with name N has as its
    only child another taxon with name N, the parent is removed.
    This is done to avoid an ambiguity when later on a node with name
    N needs to be aligned.

## Patching

After loading and before alignment and merge, each source taxonomy
gets patched individually.  It's always best to treat a problem as
early as possible, so that its ill effects don't interfere with
alignment of other taxonomies and with proper synthesis.  This is
separate from the general patch phase that takes place at the end of
taxonomy construction (below). A frequent kind of patch is to add a
synonym or change a name so that a source taxon aligns with an
existing union taxon.  Patches that bring a source taxonomy into
agreement with the skeleton (see below) also happen here.

## Alignment

It is very important that source taxa be matched with union taxa, when
and only when this is appropriate.  A mistaken identity between a
source taxon and a union taxon can be disastrous, leading to
downstream curation errors (e.g. putting a snail in flatworms).  A
mistaken non-identity (separation) also be a problem, since taxon
duplication leads to incomplete annotation (information about one copy
not propagating to the other) and to loss of unification opportunities
in phylogeny synthesis

The process of matching source taxa with union taxa is called
"alignment" in following.

Ultimately there is no test to determine whether alignment has been
done correctly.  The process is necessarily heuristic.  Difficult
cases must be investigated manually and either repaired manually
(patched) or repaired by an improvement to the heuristic set.

Alignment proceeds one source node at a time, as follows:

 1. A list of candidate matches is made.
 2. Matches that should not be made are removed from the list, using a
    set of heuristics (separation).
 3. Among those that remain, the best candidate is chosen, using a
    set of heuristics (preference).

If there is no single best candidate, an unresolvable ambiguity is
declared and the source taxon is dropped - which is OK because it
probably corresponds to one of the candidates and therefore would make
no contribution to the union taxonomy.


### Candidate identification

Potentially, any source node might match any union node, because we do
not have complete information about synonymies, and we have no
information that can be used to definitively rule out any particular
match.  Of course considering all options is not practical, so we
assume that we need *some* reason to think nodes might be matched
before evaluating them.  The criterion used is that they must have a
name in common.  The common case is having the same primary
name-string, but other paths between the two are possible via
synonyms.

### Separation heuristic: Skeleton taxonomy

If taxa A and B belong to taxa C and D (respectively), and C and D are
disjoint, then A and B are disjoint.  For example, land plants and
rhodophytes are disjoint, so if NCBI says its _Pteridium_ is a land
plant, and WoRMS says its _Pteridium_ is a rhodophyte, then either
there's been a gross misclassification in one of the sources, or NCBI
_Pteridium_ and WoRMS _Pteridium_ are different taxa.  Since
misclassifications at the level of rhodophytes vs. plants are much
rarer than name-strings that are polysemous across major groups, it's
better to assume we have a polysemy.

Drawing a 'polysemy barrier' between plants and rhodophytes
resembles the use of nomenclatural codes to separate polysemies,
but the codes are not fine grained enough to capture distinctions that
actually arise.  For example, there are many [how many? dozens?
hundreds?] of fungus/plant polysemies, even though the two groups are
covered by the same nomenclatural code.

Of course some cases like this are actual differences of opinion
concerning classification, and different placement of a name between
two source taxonomies does not mean that we are talking about
different taxa.

The particular solution adopted is as follows.  We establish a
"skeleton" taxonomy, containing about 25 higher taxa (Bacteria,
Metazoa, etc.) by fiat.  Every source taxonomy is aligned - manually,
if necessary - to the skeleton taxonomy.  (Usually this initial
mini-alignment is by simply by name, although there are a few
troublesome cases, such as Bacteria, where higher taxon names are
polyesmies.)  If taxa A and B with the same name N belong to C and D in
the skeleton taxonomy, and C and D are disjoint in the skeleton, then
A and B are taken to be disjoint as well, i.e. polyesmies.

There are many cases (about 4,000) where A's nearest enclosing
skeleton taxon C is contained in B's nearest skeleton taxon D or vice
versa.  It is not clear what to do in these cases.  In OTT 2.9, A and
B are treated as weak polysemies, and A is suppressed due to the
uncertainty, but the number is so high that a better bet might be to
assume they're all true polysemies.  Example: the skeleton taxonomy does
not separate _Brightonia_ the mollusc (from IRMNG) from _Brightonia_
the echinoderm (from higher priority WoRMS), so the mollusc is
suppressed.

### Separation heuristic: incompatible membership

(Hmm... this will take a long time to explain... we take a proxy for
taxon membership and check for disjointness of source taxon with union
taxon; nodes with disjoint taxa are kept separate.  The proxy is based
on sets of names.  If S is the set of names that are (a) unambiguous
in both taxonomies and (b) present in both taxonomies, we look at the
subsets of S for nodes under the source node and the union node.  If
the subsets are nonempty and disjoint, we take the nodes to be
separate, even if they have the same name.)

### Preference heuristics

A sequence of preference heuristics is applied in order in order to
eliminate candidates so that (it is hoped) only one remains at the
end.

A preference heuristic is essentially a two-place predicate, applied
to a source node and a union node, that either succeeds (the two seem
to match according to this heuristic) or fails (they do not match
according to this heuristic, but might match according to another).  The
method for applying the heuristics is as follows:

 1. Start with a source node and a set C of union nodes (candidates)
 2. For each preference heuristic P:
      1. Let C' = those members of C for which P succeeds
      2. If C' is singleton, we are done; the source node matches the member of C'
      3. If C' is nonempty, replace C with C'; if C' is empty leave C alone
 3. If we have run out of heuristics P, the source node is ambiguous 
    and is not used in taxonomy synthesis.

[OK, this isn't really accurate.  In fact the separation and
preference heuristics are interleaved.  I don't like it that way and
I don't think interleaving confers any advantage.]


### Collisions

There are often false polysemies within a source taxonomy - that is, a
name appears on more than one node in the source taxonomy, when on
inspection it is clear that this is a mistake in the source, and there
is only one taxon in question.  [Example: Aricidea rubra - explain.]

If the union taxonomy has an appropriate node with that name, then
multiple source taxonomy nodes can match it.  However, if the union
taxonomy has no such node, both source nodes will end up being copied
into the union.  This is an error in the method which needs to be
fixed.

[The above is a bit of a lie.  In fact some amount of collision
avoidance logic remains in the code (class `Matrix`).  I don't really understand how it
works any more.]


## Merge

Following alignment, taxa from the source taxonomy are merged into the
union taxonomy.  This is performed via bottom-up traversal of the
source.  A parameter 'sink' is passed down to recursive calls, and is
simply the most rootward alignment target seen so far on the descent.
It is called 'sink' because it's the node to which orphan nodes will
be attached when a source taxon is dropped due to inconsistency with
the union.

The following cases arise during the merge process:

 * Source taxonomy tip: if it's matched to a union node, there is
   nothing to do.  If unmatched and not blocked for some reason
   (e.g. ambiguity), create a corresponding tip node in the union, to
   be attached later on.  The source tip is then matched to the new
   union tip.

 * Source taxonomy internal node: the children have already been
   processed (recursively), and are all matched to targets in the
   union (or, rarely, blocked).  The targets are either 'old' (they
   were already in the union before the merge started) or 'new'
   (created in the union during the merge process).  One of the
   following cases then holds:

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
properly handle unplaced (incertae sedis) taxa.  Generally speaking
unplaced taxa are ignored in the calculations of inconsistency and
refinement.  The source taxonomy might provide a better position for
an unplaced taxon (more resolved, or resolved), or not.  Unplaced taxa
should not influence the treatment of placed taxa, but they shouldn't
get lost either.

## Postprocessing

After all source taxonomies are aligned and merged, general patches
are applied to the union taxonomy.  Some patches are represented in
the 'version 1' (TSV-based) form, and others in the 'version 2'
(python-based) form.  A further set of patches for microbial
Eukaryotes comes from a spreadsheet prepared by the Katz lab.

There is a special step to locate taxa that come only from PaleoDB
and mark them extinct.

## Id assignment

The final step is to assign OTT ids to taxa.  This is done by aligning
the previous version of OTT to the new union taxonomy.  After
transferring ids of aligned taxa, any remaining union taxa are given
newly 'minted' identifiers.
