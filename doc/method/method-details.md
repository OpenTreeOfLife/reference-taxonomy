
### Import and normalization

Each source taxonomy has its own import procedure, usually a file
download from the provider's web site followed by application of a
script that converts the source to a common format for import.  Given
the converted source files, the taxonomy can be read by the OTT assembly
procedure.

After each source taxonomy is loaded, the following normalizations
are performed:

 1. Diacritics removal - accents, umlauts, and other diacritic
    marks are removed in order to improve
    name matching, as well as to follow the nomenclatural codes, which prohibit them.
    The original name-string is kept as a synonym.
 1. Child taxa of "containers" in the source taxonomy are made to be
    children of the container's parent.  "Containers" are
    groupings in the source that don't represent taxa, for example nodes
    named "incertae sedis" or "environmental samples".  The members of
    a container aren't more closely related to one another than they
    are to the container's siblings; the container is only present as
    a way to say something about the members.  The fact that a node
    had originally been in a container is recorded as a flag on the
    child node.
 1. When a subgenus X has the same name-string as its containing genus,
    its name-string is changed to "X subgenus X".  This follows a convention
    used by NCBI Taxonomy and helps distinguish the two taxa later in
    assembly.

The normalized versions of the taxonomies then become the input to subsequent
processing phases.



## Aligning nodes across taxonomies

This section and the next give details of the taxonomy combination
method introduced above.

OTT is assembled in a temporary work area or _workspace_ by
alternately aligning a source to the workspace and merging that source
into the workspace.
It is important that source taxonomy nodes be matched with workspace nodes when and
only when this is appropriate.  A mistaken identity between a source
node and a workspace node can be disastrous, leading not just to an
incorrect classification but to downstream curation errors in OTU matching
(e.g. putting a snail in flatworms).  A mistaken non-identity
(separation) can also be a problem, since taxon duplication
(i.e. multiple nodes for the same taxon) leads to 
loss of unification opportunities in phylogeny synthesis.

As described above, source taxonomies are processed (aligned and
merged) in priority order.  For each source taxonomy, _ad hoc_
adjustments are applied before automatic alignments.  For automatic
alignment, alignments closest to the tips of the source taxonomy are
found in a first pass, and all others in a second pass.  The two-pass
structure permits first-pass alignments to be used during the second
pass (see Overlap, below).

### _Ad hoc_ alignment adjustments

A set of _ad hoc_ 'adjustments' address
known issues that are beyond the capabilities of the automated process
to address. These often reflect either errors or missing information in source taxonomies, discovered through the failure of automated alignment, and confirmed manually via the literature. Although each individual adjustment is _ad hoc,_ i.e. not the
result of automation, the adjustments are recorded in a file that can
be run as a script.  Following are some examples of adjustments.

1. capitalization and spelling repairs (e.g. change 'sordariomyceta' to 'Sordariomyceta')
1. addition of synonyms to facilitate later matching (e.g. 'Florideophyceae' as synonym for 'Florideophycidae')
1. name changes (e.g. 'Choanomonada' to 'Choanoflagellida')
1. deletions (e.g. removing synonym 'Eucarya' for 'Eukaryota' to avoid
   confusing eukaryotes with genus _Eucarya_ in Magnoliopsida; or removing
   unaccepted genus _Tipuloidea_ in Hemiptera to avoid confusion with
   the superfamily in Diptera)
1. merges to repair redundancies in the source (e.g. Pinidae, Coniferophyta, Coniferopsida)
1. rename taxa to avoid confusing homonyms (e.g. there are two Cyanobacterias in SILVA, one
   a parent of the other; the parent is renamed to its NCBI name 'Cyanobacteria/Melainabacteria group')
1. alignments when names differs (Diatomea is Bacillariophyta)
1. alignments to override automated alignment rules (Eccrinales not in Fungi,
   Myzostomatida not in Annelida)

In the process of assembling the reference taxonomy, about 300 _ad hoc_
adjustments are made to the source taxonomies before they are
aligned to the workspace.
<!--
[JAR: check numbers when 3.0 is final: `python util/count_patches.py adjustments.py` ~= 289]
-->

### Candidate identification

Given a source node, the alignment procedure begins by finding the
nodes in the workspace that it could _possibly_ align with.
These workspace nodes are called _candidates._ The candidates are simply
the nodes that have a name-string (either primary or synonym) that
matches any name-string (primary or synonym) of the source node.

Example: GBIF _Nakazawaea pomicola_ has NCBI _Candida pomiphila_ as
a candidate by way of an NCBI record that lists
_Nakazawaea pomicola_ as a synonym of _Candida pomiphila_.

It follows that if the workspace has multiple nodes with the same
name-string (homonyms), all of these nodes will become candidates for
every source node that also has that name-string.

### Candidate selection

The purpose of the alignment phase is to choose a single correct
candidate for each source node, or to reject all candidates if none is
correct.  For over 97% of source nodes, there are no candidates or only one
candidate, and selection is fairly simple, but the remaining nodes
require special treatment.

Example: There are two nodes named _Aporia lemoulti_ in the GBIF
backbone taxonomy; one is a plant and the other is an insect.  One of
these two is an erroneous duplication, but the automated system has to
be able to cope with this situation because we don't have the
resources to correct all source taxonomy errors.  When IRMNG is
aligned, it is necessary to
choose the right candidate for the node with name _Aporia
lemoulti_.  Consequences of incorrect placement might include putting
siblings of IRMNG _Aporia lemoulti_ in the wrong kingdom as well.

Example: _Fritillaria messanensis_ in WoRMS must not map to
_Fritillaria messanensis_ in NCBI Taxonomy because the taxon in WoRMS
is an animal (tunicate) while the taxon in NCBI is a flowering plant.
This is a case where there is a unique candidate, but it is wrong.

Similarly, _Aporia sordida_ is a plant in GBIF, but an insect in IRMNG.

### Alignment heuristics

Once we have a list of candidates, we apply a set of heuristics in an attempt
to find a single candidate, and thereby align a source node n'  with a workspace
node n.  The heuristics are as follows, presented in the order that we apply
them in the alignment process:

 1. **Separation**: If n and n' are contained in "obviously different" major groups
    such as animals and plants, do not align n' to n.  Two major
    groups (or "separation taxa") are "obviously different" if they
    are disjoint as determined by the separation taxonomy.

    _Examples:_ (1) the _Aporia_ cases above; (2)
    NCBI says n = _Pteridium_ is a land plant, WoRMS says n' = _Pteridium_ is a
    rhodophyte, and the separation taxonomy says land plants and rhodophytes
    are disjoint, so n and n' are different taxa.

 1. **Disparate ranks**: Prohibit alignment where n and n' have "obviously
    incompatible" (disparate) ranks.
    A rank is "obviously incompatible" with another if one is genus or
    a rank inferior to genus (species, etc.) and the other is family or
    a rank superior to family (order, etc.).

    _Examples:_ (1) IRMNG _Pulicomorpha_, a genus, matches NCBI
    _Pulicomorpha_, a genus, not GBIF Pulicomorpha, a suborder.
    Note that both candidates are insects. (2) For genus _Ascophora_ in
    GBIF (which is in
    Platyhelminthes), candidate _Ascophora_ from WoRMS, a genus, is
    preferred to candidate Ascophora from NCBI, an infraorder.

 1. **Lineage**: Prefer to align species or genus n' to n if they have
    common lineage.
    For example, if n' is a species, prefer candidates n where the name-string of
    the family-rank ancestor node of n' is the same as the name-string of the
    family-rank ancestor node of n.

    _Example:_ Source node _Plasmodiophora diplantherae_ from Index
    Fungorum, in Protozoa, has one workspace candidate derived from
    NCBI and another from WoRMS.  Because the source node and the NCBI
    candidate both claim to be in a taxon with name 'Phytomyxea', while the
    WoRMS candidate has no near lineage in common, the NCBI
    candidate is chosen.

    The details are complicated because (a) every pair of nodes have
    at least _some_ of their lineage in common, and (b) genus names do not
    provide any information when comparing species nodes with the same
    name-string, so for species we can't just look at the parent taxon.  The exact
    rule used is the following:
    Define the 'quasiparent name' of n, q(n), to be the
    name-string of the nearest ancestor of n whose name-string is not
    a prefix of n's name-string.  (For example, the quasiparent of a species would typically be
    a family.)
    If q(n) is the name-string of
    an ancestor of n', or q(n') is the name-string of an ancestor of n,
    then prefer n to candidates that lack these properties.

 1. **Overlap**: Prefer to align n' to n if they are higher level groupings that overlap.
    Stated a bit more carefully: Prefern' if n' has a descendant aligned to
    a descendant of n.  

    _Example:_ Source node _Peranema_ from GBIF has two candidates from NCBI.
    One candidate shares descendant _Peranema cryptocercum_ with the source taxon,
    while the other shares no descendants with the source taxon.
    The source is therefore aligned to the one with the shared descendant.

 1. **Proximity**:
    Require a candidate n to have the property that
    the smallest separation taxon containing the source node n'
    is also the smallest separation taxon containing n.

    _Example:_ for source node Heterocheilidae in IRMNG (a nematode family) whose smallest
    separation ancestor is Metazoa, choose
    the workspace (NCBI) candidate with smallest separation ancestor
    Metazoa (also a nematode family), and not the one with smallest separation
    ancestor Diptera (a fly family).

 1. **Same name-string**: Prefer candidates whose primary name-string
    is the same as the primary name-string of n'.

    _Example:_ For source node n' = GBIF _Zabelia tyaihyoni_,
    candidate _Zabelia tyaihyoni_ from NCBI is preferred to candidate
    _Zabelia mosanensis_, also from NCBI.  NCBI _Z. mosanensis_ is a
    candidate for n' because GBIF declares that _Z. mosanensis_ is a synonym
    for GBIF _Z. tyaihyoni_.

### Control flow for applying heuristics

Each heuristic, when presented with a source node and a
candidate (workspace node), answers 'yes', 'no', or 'no information'.  'Yes' means
that according to the rule, the two nodes refer to the same taxon, 'no'
means they refer to different taxa, and 'no information' means that
this rule provides no information as to whether the nodes refer to the
same taxon.

The answers are assigned numeric scores of 1 for yes, 0 for no
information, and -1 for no.  A candidate that a
heuristic gives a no is eliminated, one that is unique in getting a
yes is selected, and if there are no yeses or no unique yes, more
heuristics are consulted.

The heuristics are applied in the order in which they are listed
above.  The outcome is sensitive to the ordering.  The ordering is
forced to some extent by internal logic, but overall the ordering
was determined by trial and error.

If there is a single candidate that is not rejected by any heuristic,
it is aligned to that candidate.

More specifically, the method for applying the heuristics is as
follows:

 1. Start with a source node N and its set C of workspace node candidates C1 ... Cn.
 2. For each heuristic H as listed above:
      1. For each candidate Ci currently in C, use H to obtain the score H(N, Ci)
      1. Let Z = the highest score from among the scores H(N, Ci)
      1. If Z < 0, we are done - no candidate is suitable
      1. Let C' = those members of C that have score Z
      1. If Z > 0 and C' contains only one candidate, we are done (match is that candidate)
      1. Otherwise, replace C with C' and proceed to the next heuristic
 4. If C is singleton after all heuristics are exhausted, its
    member is taken to be the correct match.
 5. Otherwise, the source node does not match unambiguously, and alignment fails.

### Failure to choose

If the alignment process ends with multiple candidates, there is an
unresolvable ambiguity.  If the ambiguous source node has no children,
it is dropped, which is OK because it probably corresponds to one of
the existing candidates and therefore would make no new contribution.
If the ambiguous source node has children, it is
treated as unaligned and therefore new, possibly turning an N-way
homonym into an N+1-way homonym.  This could easily be wrong because
it is so unlikely that the source node really represents a distinct taxon.
Usually, the subsequent merge phase determines that the grouping is
not needed because it inconsistent or can be 'absorbed', and it is
dropped.  If it is not dropped, then there is a troublesome situation
that calls for manual review.

As an example of an unaligned tip, consider GBIF _Katoella pulchra_.
The candidates are NCBI
_Davallodes pulchra_ and _Davallodes yunnanensis_.  (There is no
_Katoella pulchra_ in the workspace at the time of alignment.
The two candidates come from synonymies with _Katoella pulchra_
declared by GBIF.)
Neither candidate is preferable to the other, so
_Katoella pulchra_ is left unaligned and
is omitted from the assembly.


## Merging unaligned source nodes into the workspace

After the alignment phase, we are left with the set of source nodes that
could not be aligned to the workspace. The next step is to determine
if and how these (potentially new) nodes can be merged into the workspace.

The combined taxonomy (U, above) is constructed by adding copies of unaligned
nodes from the
source taxonomy S' one at a time to the workspace, which initially
contains a copy of S.  Nodes of S' therefore correspond to workspace nodes in
either of two ways: by mapping to a copy of an S-node (via the S'-S
alignment), or by mapping to a copy of an S'-node (when there is no
S'-S alignment for the S'-node).

As described above, each copied S'-node is part of either a graft or
an insertion.  A graft or insertion rooted at r' is attached to the workspace as
a child of the
nearest common ancestor node of r''s siblings' images.  A graft is
flagged _incertae
sedis_ if that NCA is a node other than the parent of the sibling
images.  By construction, insertions, never have this property, so an insertion is never
flagged _incertae sedis_.

The following schematic examples illustrate each of the cases that come
up while merging taxonomies. Taxonomy fragments are written in Newick
notation [ref ?].
Figure 3 illustrates each of these six cases.

1. ((a,b)x,(c,d)y)z + ((c,d)y,(e,f)w)z = ((a,b)x,(c,d)y,(e,f)w)z

   This is a simple graft.  The taxon w does not occur in the workspace,
   so it and its children are copied.  The workspace copy of w is
   attached as a sibling of its siblings' images: its sibling is y in S',
   which is aligned to y in the workspace, so the copy becomes a child
   of y's parent, or z.

1. ((a,b)x,(c,d)y)z + (a,b,c,d)z = ((a,b)x,(c,d)y)z

   No nodes are copied from S' to the workspace because
   every node in S' is aligned to some node in S - there are no nodes
   that _could_ be copied.

1. (a,b,c,d)z + ((a,b)x,(c,d)y)z = ((a,b)x,(c,d)y)z

   Supposing x and y are unaligned, then x and y from
   S' insert into the classification of z.  The workspace gets copies of these
   two S'-nodes.

   Example: superfamily Chitonoidea, which is in WoRMS (S') but not in
   NCBI Taxonomy (S), inserts into NCBI Taxonomy. Its parent is suborder
   Chitonina, which is in NCBI (i.e. aligned to the workspace), and
   its children are six families that are all in NCBI (aligned).

1. ((a,b)x,(c,d)y)z + (a,b,c,d,e)z = ((a,b)x,(c,d)y,?e)z

   In this situation, we don't know where to put the unaligned taxon e from
   S': in x, in y, or in z (sibling to x and y).  The solution used
   here is to add e to z and mark it as _incertae sedis_ (indicated above
   by the question mark).

   For example, family Melyridae from GBIF has five genera, of which two
   (_Trichoceble_, _Danacaea_) are not found in the workspace,
   and the other three do not all have the same parent after
   alignment -
   they are in three different subfamilies.  _Trichoceble_ and _Danacaea_
   are made to be _incertae sedis_ children of Melyridae, because
   there is no telling which NCBI subfamily they are supposed to go in.

1. (a,b,c,d,e)z + ((a,b)x,(c,d)y)z = (a,b,c,d,e)z

   We don't want to lose the fact from the higher priority taxonomy S
   that e is a proper child of z (i.e. not _incertae sedis_), so we
   discard nodes x and y, ignoring what would otherwise
   have been an insertion.

   So that we have a term for this situation, say that x is _absorbed_ into z.

1. ((a,b)x,(c,d)y)z + ((a,c)p,(b,d,e)q)z = ((a,b)x,(c,d)y,?e)z

   If the source has a hierarchy that is incompatible with the one in
   the workspace, the conflicting source nodes are ignored, and any
   unaligned nodes (e) become _incertae sedis_ nodes under an ancestor
   containing the incompatible node's children.

   For example, when WoRMS is merged, the workspace has, from NCBI,

   ((Archaeognatha)Monocondylia,(Pterygota,Zygentoma)Dicondylia)Insecta

   and the classification given by WoRMS is

   ((Archaeognatha,Thysanura=Zygentoma)Apteryogota,Pterygota)Insecta

   That is, NCBI groups Thysanura (Zygentoma) with
   Pterygota, while WoRMS groups it with Archaeognatha.  The WoRMS
   hierarchy is ignored in favor of the higher priority NCBI
   hierarchy.  If Insecta in WoRMS had had an unaligned third child, it would
   have ended up _incertae sedis_ in Insecta.

   The test for compatibility is very simple: a source node is
   incompatible with the workspace if the nodes that its aligned
   children align with do not all have the same parent.

<img src="../figures/fig3a.jpeg" width="512" height="384"/>
<img src="../figures/fig3b.jpeg" width="512" height="384"/>


## Final patches

After all source taxonomies are aligned and merged, we apply general
_ad hoc_ additions and patches to the workspace, in a manner similar to
that employed with the source taxonomies.  Patches are represented in
three formats.  An early patch system used hand-written tabular files,
additions via the user interface use a machine-processed JSON format,
and most other patches are written as simple Python statements.  There
are 106 additions in JSON form, 97 additions and patches in tabular
form, and approximately 121 in Python form.

<!--
[JAR: get numbers from 3.0 when final;
`grep "^[a-z]" ../../feed/ott/edits/*.tsv | wc` = 97
`cat amendments/*.json | grep original_label | wc` = 106
`python util/count_patches.py amendments.py` = 121 ]
-->

## Assigning identifiers

The final step is to assign unique, stable identifiers to nodes so that external links to OTT nodes will continue to function correctly after the previous OTT version is replaced by the new one.

Identifier assignment is done by aligning the previous version of OTT
to the new version.  As with the other alignments, there are scripted
_ad hoc_ adjustments to correct for some errors that would otherwise
be made by automated assignment.  For this alignment, the set of
heuristics is extended by adding rules that prefer candidates that have
the same source taxonomy node id as the previous version node being
aligned.  After transferring identifiers of aligned nodes, any
remaining workspace nodes are given newly 'minted' identifiers.

The alignment is computed only for the purpose of assigning identifiers;
the previous OTT version is not merged into the workspace.   An identifier can only persist
from one OTT version to the next if it continues to occur in some source
taxonomy.
