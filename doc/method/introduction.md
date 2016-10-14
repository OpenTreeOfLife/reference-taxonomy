# Abstract

TBD

# Introduction

Taxonomy and nomenclature data are critical for almost any
biodiversity project that aims to synthesize data based on taxonomic
names.

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. The system
requires an overall taxonomy of life meeting the following requirements:

 1. *OTU coverage*: connecting the OTUs of phylogenetic
    trees to taxonomic sources allows discovery of equivalences
    between OTUs in different phylogenetic trees, enabling 
    tree comparison and
    supertree construction.
    In addition, it gives a way to integrate phylogenetic data with 
    external data sources.
 1. *Taxonomic coverage*: the taxonomy should cover the [MMM] species
    [citation needed] that are described in the literature, so that we
    have the option to 'fill in the gaps' not filled by phylogenetic studies.  
    This is important when building an integrative 'tree of life'.
 1. *Modern backbone*: a higher taxonomic backbone allows identified clades
    to be associated with higher taxa when possible, and provides a
    substitute for phylogenetic information when it's unavailable.
    The backbone taxonomy should reflect a reasonable attempt at phylogenetic
    fidelity.

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

what this is *not*: 

* reference for nomenclature (we link to other sources for that)
* well-formed taxonomic hypothesis
* curated taxonomy


