# Abstract

Taxonomy and nomenclature data are critical for any project that synthesizes
biodiversity data, as most biodiveristy data sets use taxonomic names to
identify taxa. Open Tree of Life is one such project, synthesizing sets of
published phylogenetic trees into comprehensive supertrees. No single published
taxonomy met the taxonomic and nomenclatural needs of the project. We therefore
describe here a system for reproducibly combining several source taxonomies into
a synthetic taxonomy, and discuss the challenges of taxonomic and nomenclatural
synthesis for downstream biodiversity projects.

# Introduction

Biodiversity projects aggregate and reason over data sets involving
taxa.  The representation of information about taxa in data sources
employs taxon names or 'name-strings' [cite darwin core?].  To use a
data set it is necessary to be able to determine whether or not a
given occurrence of name-string in one data source is equivalent, for
the purposes of data integration, to any given name-string occurrence
in another.  Solving this equivalence problem requires both
identifying equivalent occurrences when the name-strings are different
(synonym detection) and distinguishing occurrences with the same
name-string (homonym detection).  Equivalence can then be used as a
basis of taxonomic understanding, or of the relationships between taxa.

## Taxonomy goals

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. Open Tree aims to
build a phylogeny of all life, using phylogenetic trees where available and filling
in gaps using taxonomic data. The system
requires an overall taxonomy of life meeting the following requirements:

 1. *OTU coverage*: Connecting the OTUs (operational taxonomic units)
    described by tip labels on phylogenetic
    trees to external taxonomic sources allows discovery of equivalences
    between OTUs in different phylogenetic trees, enabling
    tree comparison and
    supertree construction.
    In addition, it gives a way to integrate phylogenetic data with
    other kinds of biodiversity data.
 1. *Taxonomic coverage*: The taxonomy should cover as many as possible of
    the species
    that are described in the literature [more than 1.6 million, according to
    http://www.catalogueoflife.org/ retrieved 2016-11-01], so that we
    have the option to 'fill in the gaps' not covered by phylogenetic studies.  
 1. *Phylogenetically informed backbone*: A higher taxonomic backbone
    allows us to associate
    clades in the tree of life with higher taxa when possible, allows us to
    link otherwise unconnected phylogenies, and provides a
    substitute for missing phylogenetic information.
    Given Open Tree's emphasis on phylogeny, the backbone taxonomy should
    if possible include phylogenetically sound higher groups, even if
    they do not have designated Linnaean ranks, and exclude groups known
    to be unsound.
 1. *Ongoing update*: New taxa of importance to phylogenetic studies
    are constantly being added to the literature.
    The taxonomy needs to be updated with new information on an ongoing basis.
 1. *Open data*: The taxonomy must be available to anyone for unrestricted use
    without agreement to any terms of use.

No single available taxonomic source meets all five requirements.
The NCBI taxonomy has good coverage of OTUs, but its taxonomic coverage is
limited to taxa that have sequence data in GenBank. On the other hand,
traditional all-life taxonomies such as Catalogue of Life, IRMNG, and
GBIF meet the taxonomic coverage requirement, but miss OTUs from our
input trees matching taxa lacking a proper description and Linnaean
name, and their higher-level taxonomy is often not phylogenetically
informed.

We therefore developed a process for assembling a taxonomy from
multiple taxonomic sources.

Any taxonomy in practical use requires an economical method for
keeping it up to date, as new species are being described and named at
a steady clip, and higher classification improves as knowledge of
evolution increases.  The Open Tree taxonomy is updated from its
source taxonomies, which enjoy constant maintenance.  Rather than
create a conventional taxonomic database to which changes derived from
source taxonomies are applied, the taxonomy is updated by regenerating
it from updated versions of its inputs.

We note that the Open Tree Taxonomy is *not* 1) a reference for nomenclature
(we link to other sources for that); 2) a well-formed taxonomic hypothesis; or
3) a place to deposit curated taxonomic information.

## Taxonomy assembly

Following is a simplified description of the method. Several
generalities stated here are incorrect; the actual method used makes a
number of exceptions and elaborations.

We start with a sequence of source taxonomies S1, S2, ..., Sn, ordered
by priority: if S is judged more accurate or otherwise "better" than
S', then S occurs earlier in the sequence than S'.  Priority
judgements are made by curators on the project, and are sometimes made
in ignorance of the quality of the sources, since we do not have the
resources to evaluate source quality in detail.

Next we define an operator for combining taxonomies pairwise, written
schematically as U = S + S', and apply it from left to right:

> U1 = S1  
> U2 = U1 + S2  
> U3 = U2 + S3  
> ...

To form the combination S + S', first a mapping or _alignment_ from S'
to S is found, after which the result U is formed by _merging_ S' into
S, i.e. by starting with S and adding unaligned taxa from S' to it.

As a simple example, consider a genus a represented in both
taxonomies, but with different species in the two:

> [this would be a figure]   S = (c,d,e)a,  S' = (d,e,f)a

A _node_ is a representative of a taxon in a particular taxonomy.  S
and S' each have four nodes.  The obvious correspondence between S and
S' matches nodes by name-string, so that d, e, and a in S' correspond
to d, e, and a in S, respectively.

The combination U = S + S' is S with additional nodes added, all
derived from S'.  Given this correspondence, there is an obvious place
in U for the unmapped node f, namely as sibling to d and e and child
of a, so it is 'grafted' on to S:

> [figure] U = (c,d,e,f)a

One might call this merge heuristic 'my sibling's sibling is a
sibling' or 'transitivity of siblinghood'.

[Real example: a = Bufo, c,d,e = Bufo species in NCBI, d,e,f = Bufo
species in GBIF - and thousands of others.  Would that be better than
using letters?]

## Complications in alignment

*Synonyms:* Each taxonomy comes with a set of synonyms, or extra
name-strings that apply to nodes, in addition to the 'primary'
name-string (in most cases, what the source taxonomy takes to be the
accepted name-string).  It is important to make use of these in
alignments, since otherwise a single taxon will have two nodes (taxon
records) under different name-strings.

Example: GBIF _Nakazawaea pomicola_ is aligned with NCBI _Candida
pomiphila_ by way of a synonymy found in NCBI.

*Multiple candidates:* For a given node in S', there may be multiple
nodes in S with the same name-string, making determination of the
correct mapping unclear.

Example: There are two nodes named _Aporia lemoulti_ in the GBIF
backbone taxonomy; one is a plant and the other is an insect.  (One of
these two is an erroneous duplication, but the automated system has to
be able to cope with this situation because we don't have the
resources to correct all source taxonomy errors!)  It is necessary to
choose the right one for the IRMNG node with name _Aporia lemoulti_.
Consequences of incorrect placement might include placement of the
siblings of IRMNG _Aporia lemoulti_ in the wrong kingdom as well.

*Candidate is wrong:* For a given node n in S', it might be that the
node (or nodes) in S with n's name-string would be an incorrect match.

Example: the unique node with name-string _Buchnera_ in the
Lamiales taxonomy is a plant, while the unique node with name-string
_Buchnera_ in SILVA is a bacteria.  [a species example would be
better...]

Another example: _Fritillaria messanensis_ in WoRMS must not map to
_Fritillaria messanensis_ in NCBI Taxonomy because the taxon in WoRMS
is an animal (tunicate) while the taxon in NCBI is a flowering plant.
This is a case where there is a unique candidate that is the wrong one.

Similarly, _Aporia sordida_ is a plant in GBIF, an insect in IRMNG.

To combat these situations, a set of heuristics is brought to bear
during alignment.  These heuristics are detailed in the methods
section, but very briefly they are:

 1. If node a in S' is an animal and node a in S is a plant, do not
    align the former to the latter.  This generalizes to other pairs 
    of disjoint major taxa.  

    (Example: the _Aporia_ cases above.)

 1. Do not align a taxon with rank genus or below to a taxon with rank
    family or above, or vice versa.  

    (Example: IRMNG genus Proboscidea (in Mollusca) [1263875]
    does not align to NCBI order 9779 (in Mammalia).)

 1. Prefer to align species or genus n' to n if they are in the same
    family.  Put a bit more carefully prefer n if the name-string of
    the family node of n' is the same as the name-string of the 
    family node of n. 

    (Example: _Hyphodontia quercina_ irmng:11021089
    aligns with Hyphodontia quercina in Index Fungorum [if:298799],
    not Malacodon candidus [if:505193].  [Not a great example because
    a later heuristic would have gotten it.]  The synonymy is via GBIF.)
    (Example: IRMNG Pulicomorpha, a genus, matches NCBI 
    Pulicomorpha, a genus, not GBIF Pulicomorpha, a suborder.
    Both taxa are insects.)

 1. Prefer to align n' to n if they overlap in at least one taxon.
    A bit more carefully: if n' has a descendant aligned to 
    a descendant of n.  

    (Example: need example. Scyphocoronis goes to Millotia instead of Scyphocoronis ?)

 1. Suppose the tree of 'major taxa' has B contained in A.
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

The heuristics do not work in all cases.  Sometimes more than one
candidate passes all heuristics, in which case the node is left
unaligned.

## Complications in merge

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

*Failure to choose:* There are cases where the automated alignment
procedure can't figure out what the correct alignment target should
be, among multiple candidates.  If the node is a tip (that usually
means a species), then it is pretty safe to just discard the node,
i.e. exempt it from participation in the construction of U.  If the
node is internal, however, there may be many useful (unaligned) taxa
beneath it whose placement in U only comes via the placement of the
internal node in S'.  In this case (which is rare? see numbers) the
node is considered unaligned and may make turn a two-way ambiguity
into a three-way one.

Example:
