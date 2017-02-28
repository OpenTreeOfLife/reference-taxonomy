# Introduction

Any large biodiversity data project requires one or more taxonomies
for discovery and data integration purposes, as in "find occurrence records
for primates" or "find the taxon record associated with this sequence".
GBIF [ref gbif], which focuses on occurrence records, and NCBI 
[ref 10.1093/nar/gkr1178], which focuses
on genetic sequence records, both have dedicated taxonomy efforts, while the Encyclopedia of
Life [ref EOL] is indexed by multiple taxonomies.  We present the design and
application of the Open Tree Taxonomy, which serves the Open Tree of
Life project, an aggregation of phylogenetic trees with tools for
operating on them.

In order to meet Open Tree's project requirements, the taxonomy is an automated
assembly of ten different source taxonomies. The
assembly process is repeatable so that we can easily incorporate updates to source taxonomies. Repeatability also allows us to easily test potential improvements to the assembly method. 

Information about taxa is typically
expressed in databases and files in terms of taxon names or
'name-strings'.  To combine taxonomies it is therefore
necessary to be able to determine name equivalence: whether or not an
occurrence of a name-string in one data source refers to the same
taxon as a given name-string occurrence in another.  Solving
this equivalence problem requires that we
distinguishing occurrences that only coincidentally have the same
name-string (homonym sense detection), and unify occurrences only when evidence justifies it. 
We have developed a set of heuristics
that scalably address this equivalence problem.

## The Open Tree of Life project

The Open Tree of Life project consists of a set of tools for

1. synthesizing phylogenetic supertrees from a corpus of
   phylogenetic tree inputs
   (input trees)
2. matching groupings in supertrees with higher taxa (such as Mammalia)
3. supplementing supertrees with taxa obtained only from
   taxonomy

The outcome is one or more summary trees combining phylogenetic and
taxonomic knowledge. Figure 1 illustrates an overview of the process of combining phylogeny and taxonomy, while the details are described in a separate publication [ref Redelings & Holder 2017].

<img src="../figures/fig1.jpeg" width="512" height="384"/>

Although Open Tree is primarily a phylogenetics effort, it requires a
reference taxonomy that can support each of these functions.

For supertree synthesis (1), 
we use the taxonomy for converting OTUs (operational taxonomic units, or
'tips') on input trees to a canonical form.  Supertree construction requires
that a input tree OTU be matched with an OTU from another input tree whenever
possible.  This is a nontrivial task because a taxon can have very different OTU
labels in different input trees due to synonymies, abbreviations, misspellings,
notational differences, and so on.  In addition, which taxon is named by a given
label can vary across trees (homonymy).  The approach we take is to map OTUs to
the reference taxonomy, so that OTUs in different input trees are compared by
comparing the taxa to which they map.

For higher taxon associations (2), we compare the groupings in the supertree to those in the
taxonomy.

For supplementation (3), only a relatively small number of described taxa are represented
in input trees (currently about 200,000 in the phylogenetic corpus out of two
million or more known taxa), so the taxonomy provides those that are not.
The large complement of taxonomy-only taxa can be 'grafted' onto a
supertree in phylogenetically plausible locations based on how they
relate taxonomically to taxa that are known from input trees.

## Reference taxonomy requirements

This overall program dictates what we should be looking for in a
reference taxonomy.  In addition to the technical requirements derived
from the above, we have two additional requirements coming from a
desire to situate Open Tree as ongoing infrastructure for the
evolutionary biology community, rather than as a one-off study.
Following are all five requirements:

 1. *OTU coverage:* The reference taxonomy should have a taxon 
    at the level of species or higher
    for
    every OTU that has the potential to occur in more than one study,
    over the intended scope of all cellular organisms.
 1. *Phylogenetically informed classification:* Higher taxa should be
    provided with as much resolution and phylogenetic fidelity as is
    reasonable.  Ranks and nomenclatural structure should not be
    required (since many well-established groups do not have proper
    Linnaean names or ranks) and groups at odds with phylogenetic
    understanding (such as Protozoa) should be avoided.
 1. *Taxonomic coverage:* The taxonomy should cover as many as possible of
    the species
    that are described in the literature, so that we
    can supplement generated supertrees as described in step 3 above.
 1. *Ongoing update:* New taxa of importance to phylogenetic studies
    are constantly being added to the literature.
    The taxonomy needs to be updated with new information on an ongoing basis.
 1. *Open data:* The taxonomy must be available to anyone for unrestricted use.
    Users should not have to ask permission to copy and use the taxonomy,
    nor should they be bound by terms of use that interfere with further reuse.

An additional goal is that the process should be reproducible and transparent.  Given
the source taxonomies, we should be able to regenerate the taxonomy, and 
taxon records should provide information about the taxonomic sources
from which it is derived.

No single available taxonomic source meets all requirements.  The
NCBI taxonomy [ref NCBI] has good coverage of OTUs, provides a rich source of
phyogenetically informed higher taxa, and is open, but its taxonomic
coverage is limited to taxa that have sequence data in GenBank (only about
360,000 NCBI species having standard binomial names at the time of this writing).  Traditional all-life
taxonomies such as Catalogue of Life [ref CoL], IRMNG [ref irmng], and GBIF [ref GBIF]  meet the
taxonomic coverage requirement, but miss many OTUs from our input
trees, and their higher-level taxonomies are often not as
phylogenetically informed or resolved as the NCBI taxonomy.  At the
very least, Open Tree needs to combine an NCBI-like sequence-aware
taxonomy with a traditional broad taxonomy that is also open.

These requirements cannot be met in an absolute sense; each is a 'best
effort' requirement subject to availability of project resources.

Note that the Open Tree Taxonomy is *not* supposed to be a
reference for nomenclature; it links to other sources for nomenclatural and other information.
Nor is it a place to deposit curated taxonomic information.
The taxonomy has not been vetted in detail, as doing so would be beyond
the capacity and focus of the Open Tree project.
It is known to contain many taxon duplications and technical artifacts.
Tolerating these shortcomings is a necessary tradeoff in
attempting to meet the above requirements.
