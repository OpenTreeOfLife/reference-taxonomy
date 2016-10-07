# Abstract

# Introduction

Taxonomy and nomenclature data are critical for almost any biodiversity project that aims to synthesize data based on taxonomic names.

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. The process
requires an overall taxonomy of life for (at least) three purposes:

 1. Assigning taxa to OTUs: connecting the OTUs of phylogenetic
    trees to taxonomic sources allows discovery of equivalences
    between OTUs in different phylogenetic trees, enabling supertree construction.
    In addition, it gives a way to integrate phylogenetic data with 
    external data sources.
 1. Associating identified clades with higher taxa, when possible, to ease
    navigation and orientation within supertrees.
 1. Filling in the gaps when a comprehensive tree is sought, using
    taxonomic structure as a rough substitute for phylogenetic 
    information.

No single available taxonomic source satisfies the needs of the
project.  NCBI taxonomy has good coverage of taxa occurring in
phylogenetic studies, but its coverage is limited to taxa that have
sequence data in GenBank. On the other hand, conventional taxonomies
such as Catalog of Life, GBIF, and IRMNG have better overall species
coverage, but miss many OTUs from our input trees.

We therefore constructed a taxonomy from multiple taxonomic sources
for use within Open Tree of Life - or rather we created a system for
constructing such combined taxonomies.  The taxonomy is called the
Open Tree Taxonomy (OTT) and the software that creates it is called
'smasher'. The method allows for reproducible taxonomy synthesis from
source taxonomies; for easy update when input taxonomies change; and
for user-contributed patches to fix specific issues or add missing
information.
