# Abstract

# Introduction

Taxonomy and nomenclature data are critical for almost any biodiversity project that aims to synthesize data based on taxonomic names.

Open Tree of Life is a system for synthesis of phylogenetic supertrees from
inputs drawn from a corpus of published phylogenetic trees. The process
requires an overall taxonomy of life for (at least) two purposes:

 1. Aligning tips between phylogenetic inputs: the taxonomy provides
    a source of synonyms, enabling equations between tips with
    different labels; and taxonomic context of homonyms, ensuring that tips
    with the same name but different meanings are not mistakenly equated.
 1. Available phylogenetic trees cover only a small portion of known
    species.  The taxonomy can help fill in the gaps when a
    comprehensive tree is sought.

No single available taxonomic source satisfies the needs of the
project.  On the one hand, the NCBI taxonomy has good coverage of taxa
occurring in phylogenetic studies, and it is organized along
phylogenetic lines, but its coverage is limited to taxa that have
sequence data in GenBank. On the other hand, conventional taxonomies
like GBIF and IRMNG have better overall species coverage, but miss
many tips from our input trees, and are not as phylogenetically sound
as NCBI.

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


# Method
How we build the taxonomy. [See [`method.md`](./method.md) in this directory.]

# Results
General results on the Open Tree taxonomy. Not a review on the taxonomic quality of any particular clade.

* number tips, synonyms, homonyms
* number patches
* number of inter-source conflicts? (about 1000)

* compare OTT's coverage of phylesystem with coverage by NCBI, GBIF
  (i.e. how well does OTT do what it's supposed to do, compared to
  ready-made taxonomies?)

# Discussion

* challenges in construction
* limitations of method
* OTU curation challenges?  additions feature?

