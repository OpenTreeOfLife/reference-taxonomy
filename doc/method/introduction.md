# Abstract

Taxonomy and nomenclature data are critical for any project that
synthesizes biodiversity data, as most biodiveristy data sets use
taxonomic names to identify taxa. The Open Tree of Life project
synthesizes sets of published phylogenetic trees into comprehensive
supertrees or "draft trees of life". Although it would have been
preferable to build directly off of another project's taxonomic work,
no single published taxonomy met the needs of the project. We describe
here a system for reproducibly combining several source taxonomies
into a synthetic taxonomy, and discuss the challenges of taxonomic and
nomenclatural synthesis for downstream biodiversity projects.

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
name-string (homonym detection).  Equivelance can then be used as a
basis of taxonomic understanding, or the relationships between taxa.

[KC's prior version] Taxonomy and nomenclature data are critical for almost any
biodiversity project that aims to synthesize data based on taxonomic
names. Such projects want to know whether given strings are equivalent
to known taxonomic names (taxonomic name reconciliation), how to
distinguish between multiple matches (homonym detection),
and whether other strings apply to the same taxon (synonym detection).
In addition, knowledge of the taxonomic hierarchy of names is crucial
for organizing biodiversity data for downstream use.

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. Open Tree aims to
build a phylogeny of all life, using phylogenies where available and filling
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

No single available taxonomic source meets all three requirements.
NCBI taxonomy has good coverage of OTUs, but its taxonomic coverage is
limited to taxa that have sequence data in GenBank. On the other hand,
traditional all-life taxonomies such as Catalog of Life, GBIF, and
IRMNG meet the taxonomic coverage requirement, but miss OTUs from our
input trees matching taxa lacking a proper description and Linnaean
name, and their higher-level taxonomy is often not phylogenetically
informed.

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
