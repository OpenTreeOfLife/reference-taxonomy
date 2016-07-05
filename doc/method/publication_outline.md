# Abstract

# Introduction

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

No single available taxonomic source satisfies these taxonomic needs.  
NCBI taxonomy has good coverage of
taxa occurring in phylogenetic studies, and is curated for phylogenetic
fidelity, but the coverage is limited to taxa that
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

# Method
How we build the taxonomy. See `method.md` in this same directory.

# Results
General results on the Open Tree taxonomy. Not a review on the taxonomic quality of any particular clade.

* number tips, synonyms, homonyms
* number patches
* conflict

# Discussion

* challenges in construction
* limitations of method
