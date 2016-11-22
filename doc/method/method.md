[preceded by sources subsection]

## Taxonomy assembly overview

Terminology: 
  
  * union taxonomy = data structure for creation of the reference 
    taxonomy
  * source taxonomy = imported taxonomic source (NCBI taxonomy, etc.)
  * node = a taxon record, either from a source taxonomy or the union taxonomy;
    giving name-string, source information,
    parent node, and perhaps other information such as rank
  * polysemy = where a single name-string belongs to multiple nodes
    (within the same taxonomy); in 
    nomenclatural terms, either a homonym, hemihomonym, or mistaken 
    clerical duplication

The assembly process works, in outline, as follows:

 1. Start with an ordered list of imported source taxonomies S1, S2, ... (see above)
 1. Initialize the union taxonomy U to be empty
 1. For each source S:
     1. Load, normalize, and patch S
     1. Align S to U, i.e. match nodes of S to nodes of U, where possible
     1. Merge S into U
         1. Unaligned subtrees of S (subtrees of S that contain
            no matched nodes other than their root) are grafted onto U
         1. Where S provides a more resolved classification than U, 
            'insert' unmatched nodes of S into U
 1. Apply patches and perform ad hoc postprocessing steps
 1. Assign OTT identifiers to the nodes of U, by aligning (but not merging)
    the previous version of OTT to U

The hierarchical relationships are therefore determined by priority:
ancestor/descendant relationships in an earlier source S may be
refined by a later source S', but are never overridden (except by patches, q.v.).

For each new version of OTT, construction begins de novo, so that we
always get the latest version of the source taxonomies.  The only
state that persists from one version of OTT to the next is node
identifier assignments that are carried over from one version to the
next.

Details of each step follow.

## *NMF suggestion on how to explain all this*

1. Is it possible to assume an ideal case where merging two or more OTT input taxonomies requires no or only very minimal conflict/noise/ambiguity resolution? And the result is almost unambiguously correct? If so, perhaps you start your "assembly process" description by imaging/introducing such a case, and your core pipeline in relation to it. That is then out of the way - a scenario that OTT can handle well and easily.

   [JAR: Ideal case is something like NCBI (Bufo pageoti, Bufo japonicus)Bufo + GBIF (Buf japonicus, Bufo luchunnicus)Bufo -> OTT: (Bufo pageoti, Bufo japonicus, Bufo luchunnicus)Bufo  - could be expanded to two genera]

2. Complications, 1 - those complications that through various profound or pragmatic solution you can address to a fairly large degree of satisfaction. Outcome -- still rather sound OTT, but drawing now on a full scope of things you've added because you've had to given case 1. was not what the input looked like.

    [JAR: lumping is easy; splitting is anguishing, when source 2 has species that source 1 doesn't.]

3. Complications, 2 - issues that you either handle not to your own satisfaction, or simply cannot handle at all. 

I guess I am suggesting this because 1 & 2 give you an opportunity to shine first, and somewhat conclusively, for a significant subset of the input trees. At least for the purpose of mounting the narrative. Clearly any complete OTT assembly job will encounter everything. But you may not have to write such that you directly follow what I assume may be real -- every input taxonomy has instances 1, 2, 3 represented to varying degrees, or they arise as the OTT grows. Instead you could pretend that some input taxonomies are clean (1), individually and jointly. Or clean enough (2) - because of your work. And only 3 is the tough stuff - but tough for anybody, etc.

So, I wonder what would happen if you did this kind of thing. "For the sake of making this assembly process accessible to a wide readership, we first illustrate the entire pipeline when acting on two or more input taxonomies that are highly internally consistent, and also pose minimal conflict among them. Here the assembly works well from A to Z, as we show and exemplify. 

"A second category are complications that occur frequently but for which we have developed adequate diagnosis and repair/resolution mechanisms. We show how we do this, and also show what else could be done for even better performance".

"A third category contains lingering challenges that point to future solution analysis/development needs. And we suggest ..."

(and of course you'd say that in reality, every input may be a mix of 1-3)

JAR: This sounds plausible to me.  Making a user-friendly exposition
will require many figures containing lots of little trees, but so it
goes.  I'm not sure that 2 and 3 can be separated; there are not very
many cases (graft, refinement, inconsistent, merge) and they are not
very complicated.


## Source taxonomy import and normalization

Each source taxonomy has its own import procedure, usually a file
download from the provider's web site followed by application of a
script that converts the source to a exchange format for import.  Given
the converted source files, the taxonomy can be read by the assembly
procedure.

After each source taxonomy is loaded, the following two normalizations
are performed:

 1. Child taxa of "containers" in the source taxonomy are made to be
    children of the container's parent.  "Containers" are
    groupings in the tree that don't represent taxa, for example
    "incertae sedis" or "environmental samples" nodes.  The members of
    a container aren't more closely related to one another than they
    are to the container's siblings; the container is only present as
    a way to say something about the members.  The fact that a node
    had originally been in a container is recorded as a flag on the
    child node.
 1. Monotypic homonym removal - when node with name N has as its
    only child another node with the same name N, the parent is removed.
    This is done to avoid an ambiguity when later on a node with name
    N needs to be matched.  [get examples by rerunning]

## Alignment of source taxonomy to union taxonomy

It is important that source taxa be matched with union taxa when and
only when this is appropriate.  A mistaken identity between a source
taxon and a union taxon can be disastrous, leading not just to an
incorrect tree but to downstream curation errors in OTU matching
(e.g. putting a snail in flatworms).  A mistaken non-identity
(separation) can also be a problem, since taxon duplication
(i.e. multiple nodes for the same taxon) leads to incomplete
annotation (information about one copy not propagating to the other)
and to loss of unification opportunities in phylogeny synthesis.

The process of matching source nodes with union nodes, or equivalently
determining the identity of the corresponding taxa, is called
"alignment" in the following.

Ultimately there is no automatable test to determine whether alignment
has been done correctly.  There is no oracle for deciding whether node
A and node B are about the same taxon; and available information
(names, relationships) from the source taxonomies is often mistaken,
making positives look like negatives and vice versa. The process is
necessarily heuristic.  Difficult cases must be investigated manually
and either repaired manually (patched) or repaired by improvements to
the heuristics.

Alignment consists of scripted ad hoc patches followed by an automatic
alignment procedure.  Scripted patches allow source nodes to be
correctly matched to union nodes in cases where this cannot be
automatic, or prevent incorrect matches.  Scripted alignment patches
include capitalization and spelling repairs, addition of synonyms
(e.g. 'Heterokonta' as synonym for 'Stramenopiles'), name changes
(e.g. 'Choanomonada' to 'Choanoflagellida'), deletions (e.g. removing
synonym 'Eucarya' for 'Eukaryota' to avoid confusing eukaryotes with
genus Eucarya in Magnoliopsida).

There are NNN ad hoc patches to prepare for alignment.

The automated alignment process proceeds one source node at a time.
First, a list of candidate union matches, based on names and synonyms,
is prepared for the source node.  Then, a set of heuristics is applied
to find a unique best union node match, if any, for that source node.

A heuristic is a rule that, when presented with a source node and a
union node, answers 'yes', 'no', or 'no information'.  'Yes' means
that according to the rule the two nodes refer to the same taxon, 'no'
means they refer to different taxa, and 'no information' means that
this rule provides no information as to whether the nodes refer to the
same taxon.

The answers are assigned numeric scores of -1 for no, 0 for no
information, and 1 for yes.

The method for applying the heuristics is as follows:

 1. Start with a source node N and a set C of union node candidates C1 ... Cn.
 2. For each heuristic H:
      1. For each candidate Ci, obtain the score H(N, Ci)
      1. Let Z = the highest score from among the scores H(N, Ci)
      1. If Z = -1, we are done
      1. Let C' = those members of C that have score Z
      1. If Z = 1 and C' contains only one candidate, we are done (match is that candidate)
      1. Replace C with C' and proceed to the next heuristic
 4. If C is singleton, its member is taken to be the correct match.
 5. Otherwise, the source node is ambiguous.

[NMF: Again, depending on target audience, a brief worked example may
help communicate what's being achieved here.  JAR: easy to find
examples - the log.tsv file is full of them, e.g. Conocybe siliginea.  But hard to find
illumnating examples since in most cases only one aspect of the method
is being used, and the answer almost always looks obvious to the human eye.  I will try though.]

If the process ends with multiple candidates, an unresolvable
ambiguity is declared.  If the ambiguous source node has no children,
it is dropped - which is OK because it probably corresponds to one of
the existing candidates and therefore would make no new contribution
to the union taxonomy.  If the ambiguous source node has children, it
is treated as a potentially new node, possibly turning an N-way
polysemy into an N+1-way polysemy, which is almost certainly wrong.
Usually, subsequent analysis determines that the grouping is inconsistent
with the union taxonomy and it is dropped.  If it is not dropped, then
this is a rare and troublesome situation that requires manual
intervention.

The heuristics are applied in the order in which they are listed
below.  The outcome is sensitive to the ordering.  The ordering is
forced to some extent by internal logic [discuss after the reader
knows what the heuristics are].

### Candidate identification

Given a source node, the analysis loop begins by identifying candidate
nodes in the union taxonomy.

Potentially, any source node might match any union node, because we do
not have complete information about synonymies, and we have no
information that can be used to definitively rule out any particular
match.  Of course considering all options is not practical, so we
limit the search to union nodes that have a name-string (either
primary or synonym) that matches any name-string of the source node.

### Separate taxa if in disjoint 'divisions'

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

[NMF: Check [here](https://doi.org/10.3897/BDJ.4.e8080) for harder data (names
management sections and refs. therein).]

Some cases of apparent polysemy might be differences of scientific opinion
concerning whether or not a taxon possesses a diagnostic apomorphy, or
belongs phylogenetically in some designated clade (the MRCA of some
other taxa).  Different placement of a name in two source taxonomies
does not necessarily mean that the name denotes different taxa in the
two taxonomies.

The separation heuristic used here works as follows.  We establish a
"skeleton" taxonomy, containing about 25 higher taxa (Bacteria, Fungi,
Metazoa, etc.).  Before the main alignment process starts, every
source taxonomy is aligned - manually, if necessary - to the skeleton
taxonomy.  (Usually this initial mini-alignment is by simply by name,
although there are a few troublesome cases, such as Bacteria, where
higher taxon names are polysemies.)  For any node/taxon A, the
smallest skeleton taxon containing A is called its _division_.  If
taxa A and B with the same name N have divisions C and D, and C and D
are disjoint in the skeleton, then A and B are taken to be distinct.
The heuristic does not apply if C is an ancestor of D (or vice versa); see below.

[JAR in response to NMF: The skeleton is not just the
top of the tree; it omits many intermediate layers (ranks) and only
includes big, well-known taxa. E.g. Opisthokonta is omitted because so
few sources have it, even though there are skeleton nodes both above
and below it. The skeleton is "found" in as many source taxonomies as
possible, for the purpose of aligning and dividing up the namespace
and preventing animals from being plants.

JAR continuing in response to NMF:
The problem is not placing everything; that's not hard. The only purpose of
the skeleton (whose taxa are called 'divisions') is to prevent incorrect
collapse of what ought to be polysemies. If you have record A with name Mus
bos, and record B with name Mus bos, then you generally want them to be
unified if they're both chordates, but if one is a chordate and the other
is a mollusc, you'd rather they not unify.

It's hard to find real examples of species-level polysemies where one
can be sure it's not an artifact or mistake, but one of them (from my
polysemy list) is probably Porella capensis. I have about 180 at the
species level, many of which look like mistakes (since sometimes two
or three have the same genus name). Most are plant/animal or
plant/fungus or animal/fungus, but some cases, like Ctenella aurantia,
have distinct records within the same code (Cnidaria / Ctenophora in
this case). I can't analyze every one, so we need to err on the side
of creating redundant records, rather than unifying, which would cause
higher taxa from the lower priority taxonomy to be "broken".]

### Separate taxa with disparate ranks

We assume that a taxon with rank above the level of genus (family,
class, etc.) cannot be the same as a taxon with rank genus or below
(tribe, species, etc.).

[e.g. Ascophora is a genus in Platyhelminthes, and an infraorder in
Bryozoa]

### Prefer taxa with shared lineage

A taxon that's in the "same place" in the taxonomy as a source is
preferred to one that's not; for example we would rather match a
weasel to a weasel than to a fish.  There are several factors that
make it tricky to turn this truism into an actionable rule.

* At the time that alignment is being done, we do not know the
  alignments of the ancestors, so we cannot compare ancestors very well.
  We use ancestor name as a proxy for ancestor identity.
* Sometimes having ancestors of the same name is not informative, as
  with species that are true polysemies, which have ancestors (genera)
  that are also true polysemies.  Ancestors whose names are string
  prefixes of the given taxon's name are skipped over.
* It is not enough that *some* ancestor (or ancestor name) is shared,
  since every pair of taxa share some ancestor (name).  We need to
  restrict the assessment to near ancestors.

The rule used is this one: 

Let the 'quasiparent name' of A (or B) be the name of the nearest
ancestor Q of A (or B) such that (1) Q's name occurs in both source
and target, and (2) Q's name is not a prefix of A's name.  If A's
'quasiparent name' is the name of an ancestor of B, or vice versa,
then B is a preferred match for A.  For example, the quasiparent of a
species would typically be a family.

[move to discusion] 
Broadening the search beyond the 'quasiparent' of both nodes is
necessary because different taxonomies have different resolution: in
one a family might be divided into subfamilies, where in the other it
is not.  But by ensuring that one of the two nodes being compared is a
quasiparent, we avoid vacuous positives due to both being descendants
of 'life'.

### Separate taxa that have incompatible membership

[This section needs to be rewritten!  This heuristic now makes use of
aligned tips, rather than names.]

For each source or union node A, we define its 'membership proxy' as
follows.  Let S be the set of names that are (a) present in both
taxonomies and (b) unambiguous in each taxonomy.  Then the membership
proxy of A is the set of names of tips under A that are also in S.

If A and B both have nonempty membership proxies, and the proxies are
disjoint, then we consider A and B to be incompatible, and prevent a
match between them.

[move to discusion] This heuristic has both false negatives (taxa that should be combined
but aren't) and false positives (cases where merging taxa does not
lead to the best results).

### Prefer same division

There are many cases (about 4,000? will need to instrument and re-run
to count) where A's division (say, C) is properly contained in B's
nearest division (say, D) or vice versa.  A and B are therefore not
separated by the division separation heuristic.  It is not clear what
to do in these cases.  In many situations the taxon in question is
unplaced in the source (e.g. is in Eukaryota but not in Metazoa) and
ought to be matched with a placed taxon in the union (in both
Eukaryota and Metazoa).  In OTT 2.9, [??  figure out what happens -
not sure], but the number of affected names is quite high, so many
false polysemies are created.  Example: the skeleton taxonomy does not
separate _Brightonia_ the mollusc (from IRMNG) from _Brightonia_ the
echinoderm (from higher priority WoRMS), because there is no division
for echinoderms, so [whatever happens].  [example no good in OTT
2.11.]  [need example going the other way.]

### Prefer matches not involving synonyms

B is preferred if its primary name is the same as A's.


### Collisions

[move to discussion?]
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

[NMF: What you do here is follow a general pattern of introducing a
particular workflow step, then say what may go wrong, then try to
discuss that. And this repeats for each paragraph / workflow step. I
will suggest a different arrangement.. [SEE BELOW]]


## Merging source taxonomy into union taxonomy

Following alignment, taxa from the source taxonomy are merged into the
union taxonomy.  This is performed via bottom-up traversal of the
source.  A parameter 'sink' is passed down to recursive calls, and is
simply the nearest (most tipward) alignment target seen so far on the descent.
It is called 'sink' because it's the node to which orphan nodes will
be attached when a source taxon is dropped due to inconsistency with
the union.

The following cases arise during the merge process:

 * Source taxonomy tip:
     * If the source node is matched to a union node,
       there is nothing to do - the taxon is already present.
     * If it's ambiguous - could equally match
       more than one union node, even after all alignment heuristics come
       to bear - then there is also nothing to do; it is effectively
       blocked and ignored.
     * If unmatched and not ambiguous, then create a corresponding tip node in the union, to be
       attached higher up in the recursion.  The source tip is then matched to the new union
       tip.
 * Source taxonomy internal node: the source node's children have already been
   processed (recursively), and are all matched to targets in the
   union (or, rarely, blocked, see above).  The targets are either
   'old' (they were already in the union before the merge started) or
   'new' (copied from source to union during this round of the merge
   process).  One of the
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
patches are applied to the union taxonomy, in a manner similar to that
employed with the source taxonomies.  Patches are represented in a
variety of formats representing different theories of what patches
should look like as developed through the course of the project.  For
example, a set of patches for microbial Eukaryotes was prepared by the
Katz lab as a table of their own design.  Rather than convert it to
some form already known to the system, we kept it in the original form
to facilitate further editing.

* give the number of patches [at least 151 - not clear how to count], give breakdown by type

There is a step to mark as extinct those taxa whose only source
taxonomy is GBIF and that come to GBIF via PaleoDB [reference].  This
is a heuristic, as PaleoDB can (rarely) contain extant taxa, but the
alternative is failing to recognize a much larger number of taxa as
extinct.

The final step is to assign OTT ids to taxa.  As before, some
identifiers are assigned on an ad hoc basis.  Then, automated
identifier assignment is done by aligning the previous version of OTT
to the new union taxonomy.  Additional candidates are found by comparing
identifiers of source taxonomy nodes to source taxon identifiers
stored in the previous OTT version.  After transferring identifiers of
aligned taxa, any remaining union taxa are given newly 'minted'
identifiers.

The previous OTT version is not merged into the new version; the
alignment is only for the purpose of assigning identifiers.
Therefore, if a taxon record is deleted from every source taxonomy
that contributes it, it is automatically deleted from OTT.
