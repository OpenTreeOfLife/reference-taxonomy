
# Open Tree Reference Taxonomy

## Introduction

Taxonomy and nomenclature data are critical for almost any biodiversity project that aims to synthesize data based on taxonomic names.

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. The process also
requires an overall taxonomy of life for (at least) two purposes:

 1. Aligning tips between phylogenetic inputs: the taxonomy provides
    a source of synonyms, enabling equations between tips with
    different labels; and a source of homonyms, ensuring that tips
    with the same name but different meanings are not mistakenly equated.
 1. Available phylogenetic trees cover only a small portion of known
    species.  The taxonomy can help fill in the gaps when a
    comprehensive tree is sought.

We discovered early on that no single available taxonomic source
would satisfy the project's taxonomic needs.  NCBI taxonomy has good coverage of
taxa occurring in phylogenetic studies, and is curated for phylogenetic
fidelity, but the coverage is limited to the approximately 400,000 taxa that
have sequence data in GenBank. Conventional taxonomies like GBIF have better
overall coverage, but miss many tips from our input trees, and are not
phylogenetically sound. Some groups have adequate coverage only in their own
specialist taxonomies (e.g. Index Fungorum for fungi, WoRMS for decapods).

We therefore constructed a taxonomy from multiple taxonomic sources for use
within Open Tree of Life - or rather we created a system for constructing such
combined taxonomies.  The taxonomy is called the Open Tree Taxonomy (OTT) and
the software that creates it is called 'smasher'. The method allows for
reproducible synthesis of input taxonomies; for easy update when input
taxonomies change; and for user-contributed patches to fix specific issues or
add missing information.

### Related projects

* NCBI taxonomy: The NCBI taxonomy exists to support annotation and 
  search of sequence data. NCBI has one or
  more taxonomy curators on staff.  Curators add taxa that are needed to cover sequence
  deposits, and also update classifications as the need arises.  The NCBI
  taxonomy adds as many levels to the tree as necessary in order to reflect current
  phylogenetic knowledge, meaning sibling taxa often have different
  ranks and many taxa (e.g. 'eudicots') are lacking rank altogether.
  There is no requirement that nomenclature follow code.
  (No citation available?)
* GBIF backbone taxonomy: This taxonomy's purpose is to 
  facilitate annotation and search of occurrence data hosted on GBIF's web site.
  GBIF has collected a set of 'checklists' [150
  last time I looked] that are combined algorithmically, so among 
  such projects, it is the most similar to Open Tree.
  The GBIF backbone adheres strictly to the seven-rank system.
* IRMNG:  IRMNG seems to
  mainly be the work of one person (Tony Rees) who imports information
  from outside sources both in detail and in bulk.  
  IRMNG adheres strictly to the seven-rank system, and retains taxa
  such as Protista that are known to be non-monophyletic.
  (No citation available?)
* Catalog of Life: (research) - focus on nomenclature - hierarchically
  distributed responsibility.  Not open; licensing business model
  supports a full-time administrator. CoL is an input to IRMNG and
  GBIF.
* WoRMS: (need to research)
* uBio: (research)
* EOL: not a taxonomy project, imports taxonomies from other sources without integration.

There are also numerous projects covering particular groups: the Plant List, etc.

## Taxonomy construction

The assembly process works, in outline, as follows:

 1. We start with an ordered list of source taxonomies S1, S2, ...
 1. The 'union' taxonomy U is initialized to be empty
 1. For each source S:
     1. The nodes of S are aligned with, or separated from, nodes of U
        where possible (in a manner explained below)
     1. Unaligned subtrees of S - i.e. subtrees of S whose tips are tips
        of S, and that contain no aligned nodes other than the root - are grafted onto U
     1. Where S provides a more resolved classification than U, unaligned
        internal nodes of S are 'inserted' into U
 1. Ad hoc postprocessing steps
 1. Identifiers are assigned to the nodes of U

The hierarchical relationships are therefore determined by priority:
ancestor/descendant relationships in an earlier source S may be
subdivided by a later source S', but are never overridden.

For each new version of OTT, construction begins de novo, so that we
always get the freshest taxonomic information from the source
taxonomies, which are revised constantly.

Details of each step follow.

### Source taxonomy preparation

Each source taxonomy has its own ingest procedure, usually a file
transfer followed by application of a format conversion script.
E.g. the GBIF taxonomy is downloaded from the GBIF web site, and then
converted to the Open Tree 'interim taxonomy format' by a python
script.

Following ingest the following two normalizations are performed:

 1. "Containers" - nodes in the tree that don't represent taxa - are
    removed and replaced by flags (node annotations indicating edge
    type); the most prominent being the pseudo-taxon "Incertae sedis"
 1. Monotypic homonym removal - when taxon with name N has as its
    only child another taxon with name N, remove one of the two

Before alignment and merge, each source taxonomy gets patched
individually.  It's always best to treat a problem as early as
possible, so that its ill effects don't interfere with alignment of
other taxonomies and with proper synthesis.  This is separate from the
general patch phase that takes place at the end of taxonomy
construction (below). A frequent kind of patch is to add a synonym or
change a name so that a source taxon aligns with a taxon from an
earlier or later source.  Patches that bring a source taxonomy into
agreement with the skeleton also happen here.

### Separation

If taxa A and B belong to taxa C and D (respectively), and C and D are
disjoint, then A and B are disjoint.  For example, land plants and
rhodophytes are disjoint, so if NCBI says _Pteridium_ is a land plant,
and WoRMS says _Pteridium_ is a rhodophyte, then either there's been a
gross misclassification, or NCBI _Pteridium_ and WoRMS _Pteridium_ are
different taxa.  Since misclassifications at the level of rhodophytes
vs. plants are much rarer than homonyms, it's better to assume the
latter.

Drawing a 'homonym barrier' between plants and rhodophytes
resembles the use of nomenclatural codes to separate homonyms,
but the codes are not fine grained enough to capture distinctions that
actually arise.  For example, there are many [how many? dozens?
hundreds?] of fungus/plant homonyms, even though the two groups are
covered by the same nomenclatural code.

Of course some cases like this are actual differences of opinion
concerning classification, and different placement of a name between
two source taxonomiges does not mean that we are talking about
different taxa.

The particular solution adopted is as follows.  We establish a
"skeleton" taxonomy, containing about 25 higher taxa (Bacteria,
Metazoa, etc.) by fiat.  Every source taxonomy is aligned - manually,
if necessary - to the skeleton taxonomy.  (Usually this initial
mini-alignment is by simply by name, although there are a few
troublesome cases, such as Bacteria, where higher taxon names are
homonyms.)  If taxa A and B with the same name N belong to C and D in
the skeleton taxonomy, and C and D are disjoint in the skeleton, then
A and B are taken to be disjoint as well, i.e. homonyms.

There are many cases (about 4,000) where A's nearest enclosing
skeleton taxon C is contained in B's nearest skeleton taxon D or vice
versa.  It is not clear what to do in these cases.  In OTT 2.9, A and
B are treated as weak homonyms, and A is suppressed due to the
uncertainty, but the number is so high that a better bet might be to
assume they're all true homonyms.  Example: the skeleton taxonomy does
not separate _Brightonia_ the mollusc (from IRMNG) from _Brightonia_
the echinoderm (from higher priority WoRMS), so the mollusc is
suppressed.

### Alignment

Alignment proceeds by iterating over the taxa in the source taxonomy,
and finding a unique best match in the union taxonomy, if there is
one.  If there are multiple plausible candidates in the union of equal
quality, an unresolvable ambiguity is declared and the source taxon is
dropped - which is OK because it probably corresponds to one of the
candidates.

If every taxon had a unique name, alignment would simply be name
matching.  Synonyms and homonyms make it hard.  For example, there are
three distinct genera named _Pteridium_.  NCBI has one of them, WoRMS
has a different one, GBIF and IRMNG have both, and Index Fungorum
contributes a third.  As construction proceeds, it is important that
the WoRMS taxon not be confused with the NCBI taxon, but equated with
the appropriate GBIF/IRMNG taxon.

At the same time, there are often false homonyms within a source
taxonomy - that is, the same name appears in more than one place in
the taxonomy, although on inspection it is clear that there is only
one taxon in question.  (Example: Aricidea rubra)

When there is more than one plausible union candidate, a set of
heuristic criteria is applied to choose among them.  For example, if
the parent of A in S is an ancestor of B in U (or more precisely: if
the parent of A has the same name as an ancestor of B), or vice versa,
then A will align to B, assuming there is no B' for which the same
property holds.

Alignment to a taxon with the same primary name is always attempted
before alignment via a synonym is tried.

### Merge

Following alignment, taxa from the source taxonomy are merged into the
union taxonomy.  This is performed via bottom-up traversal of
the source.  A parameter 'sink' is passed down to recursive
calls, and is simply the most rootward alignment target seen so far on
the descent.  The following cases arise during processing:

 * Tip: if aligned, there is nothing to do; if unaligned and not blocked for
   some reason (e.g. fatal ambiguity), create a corresponding tip in
   the union.

 * Aligned internal node: the children have already been processed,
   and correspond to targets in the union.  The targets are either
   'old' (they were already in the union before the merge started) or 'new'
   (created in the union during the merge process).  Attach any new
   child targets to the parent node's alignment target.

 * Graft: all targets are new.  Create a new union node, and attach
   the new targets to it.

 * Inconsistent: if the child target nodes do not all have the same parent,
   attach the new target nodes to the sink, and annotate them as 'unplaced'.

 * Refinement: the source taxonomy refines the union taxonomy at a
   given node if every child of the sink (which is in the union) is
   the alignment target of some source node.  Create a new union node
   corresponding to the source node, and attach both old and new
   targets (of children of the source node) to it.

 * Merge: similar to refinement, but some child of the sink is _not_
   an alignment target.  Attach new child targets to the common parent
   of the old child targets.

The actual logic is more complicated than this due to the need to
properly handle unplaced (incertae sedis) taxa.  The source taxonomy
might provide a better position for an unplaced taxon (more resolved,
or resolved), or not.  Unplaced taxa should not influence the treatment
of placed taxa, but they shouldn't get lost either.

### Postprocessing

After all source taxonomies are aligned and merged, general patches
are applied.  Some patches are represented in the 'version 1'
(TSV-based) form, and others in the 'version 2' (python-based) form.
A further set of patches for microbial Eukaryotes comes from a
spreadsheet prepared by the Katz lab.

There is a special step to locate taxa that come only from PaleoDB
and mark them extinct.

### Id assignment

The final step is to assign OTT ids to taxa.  This is done by aligning
the previous version of OTT to the new union taxonomy.  After
transferring ids of aligned taxa, any remaining union taxa are given
newly 'minted' identifiers.

## Results

(Get metrics for OTT 2.10)


## Discussion

Challenges in construction: dirty inputs, homonyms, a gazillion special cases...

Limitations of method: ...

