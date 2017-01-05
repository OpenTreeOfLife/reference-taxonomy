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

To choose a candidate, and thereby align a source node with a
workspace node, a set of heuristics is brought to bear.  They are
described in brief, followed by more detail.

 1. If node a in S' is an animal and node a in S is a plant, do not
    align the former to the latter.  This generalizes to other pairs
    of disjoint major taxa. [KC: is this where the separation taxa
    come into play? JR: yes, will fix later]

    (Example: the _Aporia_ cases above.)

 1. Prohibit alignment where one taxon is at the rank of genus or
    below (species, etc.) and the other is at the rank of family or above
    (order, etc.).

    (Example: IRMNG Pulicomorpha, a genus, matches NCBI 
    Pulicomorpha, a genus, not GBIF Pulicomorpha, a suborder.
    Both taxa are insects.)

 1. Prefer to align species or genus n' to n if they are in the same
    family.  Put a bit more carefully: prefer n if the name-string of
    the family node of n' is the same as the name-string of the
    family node of n.

    (Example: _Hyphodontia quercina_ irmng:11021089
    aligns with Hyphodontia quercina in Index Fungorum [if:298799],
    not Malacodon candidus [if:505193].  [Not a great example because
    a later heuristic would have gotten it.]  The synonymy is via GBIF.)
 1. Prefer to align n' to n if they overlap.
    Stated a bit more carefully: if n' has a descendant aligned to 
    a descendant of n.  

    (Example: need example. Scyphocoronis goes to Millotia instead of Scyphocoronis ?)

 1. Suppose the separation taxa include A and B, with B contained in A.
    If node n' is in B, and there are candidates in both A and B,
    prefer the one in B.  Similarly, if n' is in A, prefer the
    candidate in A.

    (Example: IRMNG Macbrideola indica goes to Macbrideola coprophila,
    not Utharomyces epallocaulus.  [get more info])

 1. All other things being equal, prefer a candidate that has the
    same (non-synonym) name.

    (Example: Zabelia tyaihyoni preferred to Zabelia mosanensis for
    GBIF Zabelia tyaihyoni.)

If there is a single candidate and it passes all heuristics, it is
aligned to that candidate.

### Alignment heuristics

#### Separate taxa if contained in disjoint separation taxa

If taxa A and B belong to taxa C and D (respectively), and C and D are
known to be disjoint, then it follows that A and B are distinct.  For
example, land plants and rhodophytes are disjoint, so if NCBI says its
_Pteridium_ is a land plant, and WoRMS says its _Pteridium_ is a
rhodophyte, then it follows that NCBI _Pteridium_ and WoRMS
_Pteridium_ are different taxa.

Separating plants from rhodophytes
resembles the use of nomenclatural codes to separate (hemi)homonyms,
but the codes are not fine grained enough to capture distinctions that
arise in practice.  For example, there are many [how many? dozens?
hundreds?] of fungus/plant homonyms, even though the two groups are
governed by the same nomenclatural code.

[NMF: Check [here](https://doi.org/10.3897/BDJ.4.e8080) for harder data (names
management sections and refs. therein).]

Some cases of apparent homonymy might be differences of scientific opinion
concerning whether or not a taxon possesses a diagnostic apomorphy, or
belongs phylogenetically in some designated clade (the MRCA of some
other taxa).  Different placement of a name in two source taxonomies
does not necessarily mean that the name denotes different taxa in the
two taxonomies.

The separation heuristic used here works as follows.  We establish a
set of separation taxa, containing about 25 higher taxa (Bacteria, Fungi,
Metazoa, etc.).  Before the main alignment process starts, every
source taxonomy is aligned - manually, if necessary - to the separation
taxonomy.  (Usually this initial mini-alignment is by simply by name,
although there are a few troublesome cases, such as Bacteria, where
higher taxon names are homonyms.)  If
taxa A and B with the same name N are in separation taxa C and D, and C and D
are disjoint in the separation taxonomy, then A and B are taken to be distinct.
The heuristic does not apply if C is an ancestor of D (or vice versa); see below.

[JAR in response to NMF: The separation taxonomy is not just the
top of the tree; it omits many intermediate layers (ranks) and only
includes big, well-known taxa. E.g. Opisthokonta is omitted from the separation taxonomy 
because so
few sources have it, even though there are separation nodes both above
and below it. A separation taxon must be "found" in at least two source taxonomies
in order to be useful for the purpose of aligning and dividing up the namespace
and preventing animals from being plants (etc.).

JAR continuing in response to NMF:
The problem is not placing everything; that's not hard. The only purpose of
the separation taxon is to prevent incorrect
collapse of what ought to be homonyms. If you have record A with name Mus
bos, and record B with name Mus bos, then you generally want them to be
unified if they're both chordates, but if one is a chordate and the other
is a mollusc, you'd rather they not unify.

It's hard to find real examples of species-level homonyms where one
can be sure it's not an artifact or mistake, but one of them (from my
homonym list) is probably Porella capensis. OTT has about 180 homonyms at the
species level, many of which look like mistakes (since sometimes two
or three have the same genus name). Most are plant/animal or
plant/fungus or animal/fungus, but some cases, like Ctenella aurantia,
have distinct records within the same code (Cnidaria / Ctenophora in
this case). I can't analyze every one, so we need to err on the side
of creating redundant records, rather than unifying, which would cause
higher taxa from the lower priority taxonomy to be "broken".]

#### Disparate ranks implies different taxa

We assume that a taxon with rank above the level of genus (family,
class, etc.) cannot be the same as a taxon with rank genus or below
(tribe, species, etc.).  A candidate that matches the source node in
this regard will be preferred to one that doesn't.  (That is, in
regard to whether it is genus or below; not in regard to its
particular rank.)

For example, for genus Ascophora in GBIF (which is in
Platyhelminthes), candidate Ascophora from WoRMS, a genus, is
preferred to candidate Ascophora from NCBI, an infraorder.


#### Prefer taxa with shared lineage

A taxon that's in the "same place" in the taxonomy as a source is
preferred to one that's not; for example we would rather match a
weasel to a weasel than to a fish.

The rule used is this one:

Let the 'quasiparent name' of A (or B) be the name of the nearest
ancestor Q of A (or B) such that (1) Q's name occurs in both source
and target, and (2) Q's name is not a prefix of A's name.  If A's
'quasiparent name' is the name of an ancestor of B, or vice versa,
then B is a preferred match for A.  For example, the quasiparent of a
species would typically be a family.

#### Separate taxa that have incompatible membership

[This section needs to be rewritten!  This heuristic now makes use of
aligned tips, rather than names.  And I believe it's a preference, not
a separation.]

For each source or workspace node A, we define its 'membership proxy' to
be the set of aligned nodes under it that do not have any aligned node
as a descendant (that is, as aligned nodes, they are tip-like).

If A and B both have nonempty membership proxies, and the proxies are
disjoint (supposing one considers aligned nodes to be unified), then
we consider A and B to be incompatible, and prevent a match between
them.

#### Prefer same separation taxon

There are many cases (about 4,000? will need to instrument and re-run
to count) where A's nearest enclosing separation taxon (say, C) is properly contained in B's
(say, D) or vice versa.  A and B are therefore not
separated by the separation heuristic.  It is not clear what
to do in these cases.  In many situations the taxon in question is
unplaced in the source (e.g. is in Eukaryota but not in Metazoa) and
ought to be matched with a placed taxon in the workspace (in both
Eukaryota and Metazoa).  In OTT 2.9, [??  figure out what happens -
not sure], but the number of affected names is quite high, so many
false homonyms are created.  Example: the separation taxonomy does not
separate _Brightonia_ the mollusc (from IRMNG) from _Brightonia_ the
echinoderm (from higher priority WoRMS), because echinoderms is not a
separation taxon, so [whatever happens].  [example no good in OTT
2.11 - get another.]  [need example going the other way.]

#### Prefer matches not involving synonyms

B is preferred to other candidates if its primary name is the same as
A's.

[NMF: What you do here is follow a general pattern of introducing a
particular workflow step, then say what may go wrong, then try to
discuss that. And this repeats for each paragraph / workflow step. I
will suggest a different arrangement.. [SEE BELOW]]


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
