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
typically employs taxon names or 'name-strings' [cite darwin core?].  To use a
data set it is necessary to be able to determine whether or not a
given occurrence of a name-string in one data source is equivalent, for
the purposes of data integration, to any given name-string occurrence
in another.  Solving this equivalence problem requires both
identifying equivalent occurrences when the name-strings are different
(synonym detection) and distinguishing occurrences that only coincidentally have the same
name-string (homonym detection).  Equivalence can then be used as a
basis of taxonomic understanding, of the relationships between taxa, 
and of data integration across data sets.

## Open Tree of Life

The Open Tree of Life project consists of a set of tools for

1. synthesizing phylogenetic supertrees from phylogenetic tree inputs
   (source trees)
2. matching groupings in supertrees with higher taxa (such as Mammalia)
   to assist understanding the relationship between phylogeny and taxonomy
3. supplementing the supertrees with taxa obtained only from 
   taxonomy

The outcome is one or more summary trees combining phylogenetic and
taxonomic knowledge.

Although it is primarily a phylogenetics effort, Open Tree requires a
reference taxonomy at each of these three stages.

In step 1, the taxonomy is used for converting OTUs (operational
taxonomic units, or 'tips') to a canonical form.  Supertree
construction requires that a source tree OTU be matched with an OTU
from another tree whenever possible.  This is a nontrivial task.  The
approach taken is to map OTUs to the reference taxonomy, so that OTUs
are compared by comparing the taxa to which they map.

In step 2, the groupings in the supertree are compared to those in the
taxonomy.

In step 3, the taxonomy provides supplemental taxa beyond the ones
that are in the source trees, using the outcome of step 2 to determine
where they are to be 'grafted'.

## Reference taxonomy requirements

This overall program dictates what we should be looking for in a
reference taxonomy.  In addition to the technical requirements derived
from the above, we have two additional requirements coming from a
desire to situate Open Tree as ongoing infrastructure for the
community, rather than a one-off study.  Following are all five
requirements:

 1. *OTU coverage*: The reference taxonomy should have a taxon for
    every OTU that has the potential to occur in more than one study.
 1. *Phylogenetically informed classification*: Higher taxa should be
    provided with as much resolution and phylogenetic fidelity as is
    reasonable.  Ranks and nomenclatural structure should not be 
    required (since many well-established groups do not have proper 
    Linnaean names or ranks) and groups at odds with phylogenetic 
    understanding (such as Protozoa) should be avoided.
 1. *Taxonomic coverage*: The taxonomy should cover as many as possible of
    the species
    that are described in the literature [more than 1.6 million, according to
    http://www.catalogueoflife.org/ retrieved 2016-11-01], so that we
    can supplement generated supertrees as described in step 3 above.
 1. *Ongoing update*: New taxa of importance to phylogenetic studies
    are constantly being added to the literature.
    The taxonomy needs to be updated with new information on an ongoing basis.
 1. *Open data*: The taxonomy must be available to anyone for unrestricted use.
    Users should not have to ask permission to copy and use the taxonomy, 
    or observe terms of use.

No single available taxonomic source meets all five requirements.  The
NCBI taxonomy has good coverage of OTUs, but its taxonomic coverage is
limited to taxa that have sequence data in GenBank. On the other hand,
traditional all-life taxonomies such as Catalogue of Life, IRMNG, and
GBIF meet the taxonomic coverage requirement, but miss OTUs from our
input trees matching taxa lacking a proper description and Linnaean
name, and their higher-level taxonomy is often not phylogenetically
informed.  At the very least, Open Tree needs to combine an NCBI-like
sequence-aware taxonomy with a traditional broad taxonomy.

These requirements cannot be met in an absolute sense; each is a 'best
effort' requirement subject to availability of project resources.

We note that the Open Tree Taxonomy is *not* supposed to be 1) a
reference for nomenclature (we can link to other sources for that); 2)
a well-formed taxonomic hypothesis; or 3) a place to deposit curated
taxonomic information.
