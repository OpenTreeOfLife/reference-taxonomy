[preceded by sources subsection]


### Import and normalization

Each source taxonomy has its own import procedure, usually a file
download from the provider's web site followed by application of a
script that converts the source to a common format for import.  Given
the converted source files, the taxonomy can be read by the assembly
procedure.

After each source taxonomy is loaded, the following normalizations
are performed:

 1. Child taxa of "containers" in the source taxonomy are made to be
    children of the container's parent.  "Containers" are
    groupings in the tree that don't represent taxa, for example nodes
    named "incertae sedis" or "environmental samples".  The members of
    a container aren't more closely related to one another than they
    are to the container's siblings; the container is only present as
    a way to say something about the members.  The fact that a node
    had originally been in a container is recorded as a flag on the
    child node.
 1. Monotypic homonym removal - when a node with name N has as its
    only child another node with the same name N, the parent is removed.
    This is done to avoid ambiguities when aligning taxonomies.
    [get examples by rerunning]
 1. Diacritics removal - accents and umlauts are removed in order to improve 
    name matching, as well as to follow the nomenclatural codes, which prohibit them.
    The original name-string is kept, as a synonym.

[KC: need to say something about whether these cases get touched again
during the process, i.e. do these nodes ever get added back, or are
they permanently removed?  JAR: something like "the normalized
versions of the taxonomies then become the input to subsequent
processing phases"?]



## Aligning nodes across taxonomies

It is important that source taxonomy nodes be matched with workspace nodes when and
only when this is appropriate.  A mistaken identity between a source
node and a workspace node can be disastrous, leading not just to an
incorrect tree but to downstream curation errors in OTU matching
(e.g. putting a snail in flatworms).  A mistaken non-identity
(separation) can also be a problem, since taxon duplication
(i.e. multiple nodes for the same taxon) leads to incomplete
annotation (information about one copy not propagating to the other)
and to loss of unification opportunities in phylogeny synthesis.

### Ad hoc alignment adjustments

Automated alignment is preceded by ad hoc 'adjustments' that address
known issues that are beyond the capabilities of the automated process
to fix.  Although each individual adjustment is ad hoc, i.e. not the
result of automation, the adjustments are recorded in a file that can
be run as a script.  Following are some examples of adjustments.

1. capitalization and spelling repairs (e.g. change 'sordariomyceta' to 'Sordariomyceta')
1. addition of synonyms to facilitate later matching (e.g. 'Florideophyceae' as synonym for 'Florideophycidae')
1. name changes (e.g. 'Choanomonada' to 'Choanoflagellida')
1. deletions (e.g. removing synonym 'Eucarya' for 'Eukaryota' to avoid
   confusing eukaryotes with genus Eucarya in Magnoliopsida; or removing
   unaccepted genus _Tipuloidea_ in Hemiptera to avoid confusion with
   the superfamily in Diptera)
1. merges to repair redundancies in the source (e.g. Pinidae, Coniferophyta, Coniferopsida)
1. rename taxa to avoid confusing homonyms (e.g. there are two Cyanobacterias in SILVA, one
   a parent of the other; the parent is renamed to its NCBI name 'Cyanobacteria/Melainabacteria group')
1. alignments when names differs (Diatomea is Bacillariophyta)
1. alignments to override automated alignment rules (Eccrinales not in Fungi,
   Myzostomatida not in Annelida)

In the process of assembling the reference taxonomy, 284 ad hoc
adjustments are made to the source taxonomies before they are
aligned to the workspace. [check]

### Candidate identification

Given a source node, the alignment procedure begins by finding the
nodes in the workspace that it could _possibly_ align with.
These workspace nodes are called _candidates._ The candidates are simply
the nodes that have a name-string (either primary or synonym) that
matches any name-string (primary or synonym) of the source node.

Example: GBIF _Nakazawaea pomicola_ has NCBI _Candida pomiphila_ as
a candidate by way of a record found in NCBI that says that
_Nakazawaea pomicola_ is a synonym of _Candida pomiphila_.

It follows that if the workspace has multiple nodes with the same
name-string (homonyms), all of these nodes will become candidates for
every source node that also has that name-string.

### Candidate selection

The purpose of the alignment phase is to choose a single correct
candidate for each source node, or to reject all candidates if none is
correct.

Example: There are two nodes named _Aporia lemoulti_ in the GBIF
backbone taxonomy; one is a plant and the other is an insect.  (One of
these two is an erroneous duplication, but the automated system has to
be able to cope with this situation because we don't have the
resources to correct all source taxonomy errors!)  It is necessary to
choose the right candidate for the IRMNG node with name _Aporia
lemoulti_.  Consequences of incorrect placement might include putting
siblings of IRMNG _Aporia lemoulti_ in the wrong kingdom as well.

Example: _Fritillaria messanensis_ in WoRMS must not map to
_Fritillaria messanensis_ in NCBI Taxonomy because the taxon in WoRMS
is an animal (tunicate) while the taxon in NCBI is a flowering plant.
This is a case where there is a unique candidate, but it is wrong.

Similarly, _Aporia sordida_ is a plant in GBIF, but an insect in IRMNG.

To choose a candidate, and thereby align a source node n' with a
workspace node n, a set of heuristics is brought to bear.  The
heuristics are as follows:

 1. **Separation**: If n and n' are contained in "obviously different" major groups
    such as animals and plants, do not align n' to n.  Two major
    groups (or "separation taxa") are "obviously different" if they
    are disjoint as determined by the separation taxonomy and the alignments 
    of the source taxonomy and workspace to it.

    (Examples: (1) the _Aporia_ cases above; (2) 
    NCBI says n = _Pteridium_ is a land plant, WoRMS says n' = _Pteridium_ is a
    rhodophyte, and the separation taxonomy says land plants and rhodophytes 
    are disjoint, so n and n' are different taxa; (3) [some example where the heuristic 
    is used for disambiguation instead of homonym creation].  
    [Also look for good species-level examples as genera are so fraught anyhow.])

 1. **Disparate ranks**: Prohibit alignment where n and n' have "obviously 
    incompatible" (disparate) ranks.
    A rank is "obviously incompatible" with another if one is genus or
    a rank inferior to genus (species, etc.) and the other is family or
    a rank superior to family (order, etc.).

    (Examples: (1) IRMNG _Pulicomorpha_, a genus, matches NCBI 
    _Pulicomorpha_, a genus, not GBIF Pulicomorpha, a suborder.
    Note that both candidates are insects. (2) For genus _Ascophora_ in 
    GBIF (which is in
    Platyhelminthes), candidate _Ascophora_ from WoRMS, a genus, is
    preferred to candidate Ascophora from NCBI, an infraorder.)

 1. **Lineage**: Prefer to align species or genus n' to n if they have 
    common near lineage.
    For example, prefer n to other candidates if the name-string of
    the family-rank ancestor node of n' is the same as the name-string of the
    family-rank ancestor node of n.

    (Example: _Hyphodontia quercina_ irmng:11021089
    aligns with Hyphodontia quercina in Index Fungorum [if:298799],
    not Malacodon candidus [if:505193].  [Not a great example because
    a later heuristic would have gotten it.]  The synonymy is via GBIF.)

    The details are complicated because (a) all pairs of nodes share
    at least _some_ of their lineage, and (b) the genus names do not
    provide any information when comparing species nodes with the same
    name-string.  The exact rule used is the following:

    Define the 'quasiparent name' of n, q(n), to be the
    name-string of the nearest ancestor of n whose name-string is not
    a prefix of n's name-string.  (For example, the quasiparent of a species would typically be
    a family.)
    If q(n) is the name-string of
    an ancestor of n', or vice versa, then n' is a preferred match to candidate n
    (i.e. candidates with this property are preferred to those that don't).

 1. **Overlap**: Prefer to align n' to n if they are higher level groupings that overlap.
    Stated a bit more carefully: if n' has a descendant aligned to 
    a descendant of n.  

    (Example: need example. Scyphocoronis goes to Millotia instead of Scyphocoronis ?)

 1. **Proximity**: Suppose the separation taxonomy includes A and B, with B contained in A.
    If node n' is in B, then prefer candidates that are in B to those that are in A but not in B.

    (Example: IRMNG _Macbrideola indica_ goes to _Macbrideola coprophila_,
    not _Utharomyces epallocaulus_.  [get more info])

 1. **Same name-string**: Prefer candidates whose primary name-string
    is the same as the primary name-string of n'.

    (Example: candidate _Zabelia tyaihyoni_ preferred to candidate _Zabelia mosanensis_ for
    n' = GBIF _Zabelia tyaihyoni_.)

If there is a single candidate that is not rejected by any heuristic,
it is aligned to that candidate.

### Method for applying Heuristics

The automated alignment process proceeds one source node at a time.
First, a list of candidate matches, based on names and synonyms,
is prepared for the source node.  Then, a set of heuristics is applied
to find a unique best candidate, if any, for that source node.

A heuristic is a rule that, when presented with a source node and a
candidate (workspace node), answers 'yes', 'no', or 'no information'.  'Yes' means
that according to the rule the two nodes refer to the same taxon, 'no'
means they refer to different taxa, and 'no information' means that
this rule provides no information as to whether the nodes refer to the
same taxon.

The answers are assigned numeric scores of 1 for yes, 0 for no
information, and -1 for no.  Roughly speaking, a candidate that a
heuristic gives a no is eliminated; one that is unique in getting a
yes is selected, and if there are no yeses or no unique yes, more
heuristics are consulted.

More specifically, the method for applying the heuristics is as
follows:

 1. Start with a source node N and its set C of workspace node candidates C1 ... Cn (see above).
 2. For each heuristic H:
      1. For each candidate Ci currently in C, use H to obtain the score H(N, Ci)
      1. Let Z = the highest score from among the scores H(N, Ci)
      1. If Z < 0, we are done
      1. Let C' = those members of C that have score Z
      1. If Z > 0 and C' contains only one candidate, we are done (match is that candidate)
      1. Replace C with C' and proceed to the next heuristic
 4. If C is singleton, its member is taken to be the correct match.
 5. Otherwise, the source node is ambiguous.

[NMF: Again, depending on target audience, a brief worked example may
help communicate what's being achieved here.  JAR: easy to find
examples - the log.tsv file is full of them, e.g. Conocybe siliginea.  But hard to find
illuminating examples since in most cases only one aspect of the method
is being used, and the answer almost always looks obvious to the human eye.  I will try though.]

If the process ends with multiple candidates, an unresolvable
ambiguity is declared.  If the ambiguous source node has no children,
it is dropped - which is OK because it probably corresponds to one of
the existing candidates and therefore would make no new contribution
to the workspace.  If the ambiguous source node has children, it
is treated as a potentially new node, possibly turning an N-way
homonym into an N+1-way homonym, which is almost certainly wrong.
Usually, subsequent analysis determines that the grouping is inconsistent
with the workspace and it is dropped.  If it is not dropped, then
this is a rare and troublesome situation that requires manual
intervention.

The heuristics are applied in the order in which they are listed
below.  The outcome is sensitive to the ordering.  The ordering is
forced to some extent by internal logic [discuss after the reader
knows what the heuristics are].

### Failure to choose

There are cases where the automated alignment procedure can't figure
out what the correct alignment target should be, among multiple
candidates.  If the node is a tip (that usually means a species), then
it is safe to just discard the node, i.e. exempt it from participation
in the construction of the workspace U; it cannot provide much new
information in any case.  If the node is internal, however, there may
be many useful (unaligned) taxa beneath it whose placement in U only
comes via the placement of the internal node in S'.  In this case
(which is rare? see numbers) the node is considered unaligned and may
make turn a two-way ambiguity into a three-way one.

[Example: ...]


## Merging unaligned source nodes into the workspace

### Complications in merge

WORK IN PROGRESS, FOLD INTO FOLLOWING SECTION

The combined taxonomy U is constructed by adding copies of nodes from
S' one at a time to a copy of S.  Nodes of S' therefore map to U in
either of two ways: by mapping to a copy of an S-node (via the S'-S
alignment), or by mapping to a copy of an S'-node (when there is no
S'-S alignment for the S'-node).

The following schematic examples illustrate four cases for the
treatment of internal S'-nodes:

1. ((a,b)x,(c,d)y)z + (a,b,c,d)z = ((a,b)x,(c,d)y)z

   Not a problem.  S' simply has less resolution than S.

1. (a,b,c,d)z + ((a,b)x,(c,d)y)z = ((a,b)x,(c,d)y)z is fine - S' refines S

   Not a problem.  Supposing x and y are unaligned, then x and y from
   S' refine the classification of z.

   Example: superfamily Chitonoidea, which is in WoRMS but not in NCBI
   Taxonomy, refines NCBI Taxonomy. Its parent is suborder Chitonina,
   which is in NCBI (which is higher priority than WoRMS for this part
   of the tree), and its children are six families that are all in
   NCBI.

1. ((a,b)x,(c,d)y)z + (a,b,c,d,e)z = ((a,b)x,(c,d)y,?e)z

   In this situation, we don't where to put the unaligned taxon e from
   S': in x, in y, or in z (sibling to x and y).  The solution used
   here is to add e to z and mark it as 'incertae sedis', which means
   that e is not a proper child of z: e is somewhere in z but not
   necessarily disjoint from z's other children.

1. (a,b,c,d,e)z + ((a,b)x,(c,d)y)z = (a,b,c,d,e)z

   We don't want to lose the fact from the higher priority taxonomy S
   that e is a proper child of z (i.e. not incertae sedis), so we
   discard the taxa x and y from S', ignoring what would otherwise
   have been a refinement.

   [not such a great technical term: 'absorption' - but code currently
   says 'merged' and that would be too confusing]

1. ((a,b)x,(c,d)y)z + ((a,c,e)xx,(b,d)yy)z = ((a,b)x,(c,d)y,?e)

   If the S' topology is inconsistent with the S topology,
   we throw away the internal nodes from S'.  Any leftover taxa
   are made incertae sedis in the common ancestor of their
   siblings, which in this case is z.

   [example of inconsistency: gbif:7919320 = Helotium, contains
   Helotium lonicerae and Helotium infarciens, but IF knows Helotium infarciens as a synonym for
   Hymenoscyphus infarciens, which isn't in OTT Helotium]

   [another random example: GBIF (S') Paludomidae has children
   Tiphobia and Bridouxia, but the two children have different parents
   in S]


*Absorption:* Hmm...

Example: (a few thousand of the last case)


### different, older text

Following alignment, taxa from the source taxonomy are merged into the
workspace.  This is performed via bottom-up traversal of the
source.  A parameter 'sink' is passed down to recursive calls, and is
simply the nearest (most tipward) alignment target seen so far on the descent.
It is called 'sink' because it's the node to which orphan nodes will
be attached when a source taxon is dropped due to inconsistency with
the workspace.

The following cases arise during the merge process:

 * Source taxonomy tip:
     * If the source node is matched to a workspace node,
       there is nothing to do - the taxon is already present.
     * If it's ambiguous - could equally match
       more than one workspace node, even after all alignment heuristics come
       to bear - then there is also nothing to do; it is effectively
       blocked and ignored.
     * If unmatched and not ambiguous, then create a corresponding tip node in the workspace, to be
       attached higher up in the recursion.  The source tip is then matched to the new
       tip.
 * Source taxonomy internal node: the source node's children have already been
   processed (recursively), and are all matched to targets in the
   workspace (or, occasionally, blocked, see above).  The targets are either
   'old' (they were already in the workspace before the merge started) or
   'new' (copied from source to workspace during this round of the merge
   process).  One of the
   following rules then applies (assuming in each case that none of
   the previous rules apply):

     * Matched internal node: Attach any new targets to the internal
       node's alignment target.

     * Graft: all targets are new.  Create a new workspace node, and attach
       the new targets to it.

     * Inconsistent: if the old target nodes do not all have the same parent,
       attach the new target nodes to the sink, and flag them as 'unplaced'.

     * Refinement: if every child of the sink (in the workspace)
       is the match of some source node, we say the source
       node 'refines' the developing taxonomy in the workspace.
       Create a new workspace node to
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

## Finishing the assembly

After all source taxonomies are aligned and merged, general ad hoc
patches are applied to the workspace, in a manner similar to that
employed with the source taxonomies.  Patches are represented in a
variety of formats representing historical accidents of curation.  Rather
than convert all patches to
some form already known to the system, we kept it in the original form,
which facilitates further editing.

* give the number of patches [at least 123 - not clear how to count], give breakdown by type?
or is that a result?

There is a step to mark as extinct those taxa whose only source
taxonomy is GBIF and that come to GBIF via PaleoDB [reference].  This
is a heuristic, as PaleoDB can (rarely) contain extant taxa, but the
alternative is failing to recognize a much larger number of taxa as
extinct.

The final step is to assign unique, stable identifiers to nodes.  As
before, some identifiers are assigned on an ad hoc basis.  Then,
automated identifier assignment is done by aligning the previous
version of OTT to the new taxonomy.  Additional candidates are
found by comparing node identifiers used in source taxonomies to
source taxonomy node identifiers stored in the previous OTT version.
After transferring identifiers of aligned nodes, any remaining workspace
nodes are given newly 'minted' identifiers.

The previous OTT version is not merged into the new version; the
alignment is only for the purpose of assigning identifiers.
Therefore, if every taxon record giving rise to a particular workspace
node is deleted from the sources, the workspace node is automatically
omitted from OTT.
