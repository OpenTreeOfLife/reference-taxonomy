# Abstract

Taxonomy and nomenclature data are critical for any project that synthesizes
biodiversity data, as most data is associated with taxonomic names as
identifiers. The Open Tree of Life project aims to synthesize published
phylogenetic trees into a comprehensive tree of life. No single published
taxonomy met the needs of the project. We describe here a system for
reproducibly combining several input taxonomies into a synthetic taxonomy, and discuss the challenges of
taxonomic and nomenclatural synthesis for downstream biodiversity projects.

# Introduction

Taxonomy and nomenclature data are critical for almost any
biodiversity project that aims to synthesize data based on taxonomic
names. Such projects want to know whether given strings are equivalent
to known taxonomic names (taxonomic name reconciliation), how to
distinguish between multiple matches (homonyn detection),
and whether other strings apply to the same taxon (synonym detection).
In addition, knowledge of the hierarchy of names into taxonomies is crucial
for organizing biodiversity data for downstream use.

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. OpenTree aims to
build a phylogeny of all life, using phylogenies where available and filling
in gaps using taxonomic data. The system
requires an overall taxonomy of life meeting the following requirements:

 1. *OTU coverage*: connecting the OTUs (operational taxonomic units)
    described by tip labels on phylogenetic
    trees to external taxonomic sources allows discovery of equivalences
    between OTUs in different phylogenetic trees, enabling
    tree comparison and
    supertree construction.
    In addition, it gives a way to integrate phylogenetic data with
    external data sources.
 1. *Taxonomic coverage*: the taxonomy should cover as many as possible of
    the [MMM] species
    [citation needed] that are described in the literature, so that we
    have the option to 'fill in the gaps' not covered by phylogenetic studies.  
 1. *Modern backbone*: a higher taxonomic backbone allows us to associate
    clades in the tree of life with higher taxa when possible, allows us to
    link unconnected phylogenies, and provides a
    substitute for missing phylogenetic information.
    Given the aims of OpenTree, the backbone taxonomy should reflect a
    reasonable attempt at phylogenetic fidelity.

No single available taxonomic source meets all three requirements.
NCBI taxonomy has good coverage of OTUs, but its taxonomic coverage is
limited to taxa that have sequence data in GenBank. On the other hand,
traditional all-life taxonomies such as Catalog of Life, GBIF, and
IRMNG meet the taxonomic coverage requirement, but miss OTUs from our
input trees that do not match any described species, and their
higher-level taxonomy is often out of date.

We therefore constructed a taxonomy from multiple taxonomic sources
using a tool created for the purpose.  The taxonomy is called the Open
Tree Taxonomy (OTT) and the software tool that creates it is called
'smasher'.

Having decided that we needed a dedicated Open Tree taxonomy, it
became necessary to establish an economical method for keeping it up
to date.  Rather than create a conventional taxonomy database
requiring constant maintenance as its sources are updated, the
taxonomy is updated by regenerating it from updated versions of its
inputs.  This way we can rely on the maintenance and development
performed at the source taxonomies, rather than taking on those tasks
ourselves.

We note that the Open Tree Taxonomy is *not* 1) a reference for nomenclature
(we link to other sources for that); 2) a well-formed taxonomic hypothesis; or
3) a curated taxonomy.
