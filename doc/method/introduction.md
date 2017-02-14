# Introduction

Any large biodiversity data project requires one or more taxonomies
for discovery and data integration purposes, as in "find occurrence records
for primates" or "find the IRMNG taxon record for this sequence".  For
example, the GBIF occurrence record database and the NCBI Genbank
sequence database both have dedicated taxonomy efforts, while Encyclopedia of
Life is indexed by multiple taxonomies.  We present the design and
application of the Open Tree Taxonomy, which serves the Open Tree of
Life project, an aggregation of phylogenetic trees with tools for
operating on them.

In order to meet Open Tree's project requirements, the taxonomy is an automated
synthesis of ten different source taxonomies with different strengths. The
synthesis process is repeatable so that updates to source taxonomies can be
incorporated easily.

Information about taxa is typically
expressed in databases and files in terms of taxon names or
'name-strings'.  To combine data sets it is
necessary to be able to determine name equivalence: whether or not an
occurrence of a name-string in one data source refers to the same
taxon as a given name-string occurrence in another.  Solving
this equivalence problem requires detecting equivalence when the
name-strings are different (synonym detection), as well as
distinguishing occurrences that only coincidentally have the same
name-string (homonym detection). We have developed a set of heuristics
that scalably address this equivalence problem.

## The Open Tree of Life project

The Open Tree of Life project consists of a set of tools for

1. synthesizing phylogenetic supertrees from a corpus of
   phylogenetic tree inputs
   (source trees)
2. matching groupings in supertrees with higher taxa (such as Mammalia)
3. supplementing supertrees with taxa obtained only from
   taxonomy

The outcome is one or more summary trees combining phylogenetic and
taxonomic knowledge.

Although it is primarily a phylogenetics effort, Open Tree requires a
reference taxonomy for each of these functions.

In 1, we use the taxonomy for converting OTUs (operational taxonomic units, or
'tips') on source trees to a canonical form.  Supertree construction requires
that a source tree OTU be matched with an OTU from another tree whenever
possible.  This is a nontrivial task because a taxon can have very different OTU
labels in different source trees due to synonymies, abbreviations, misspellings,
notational differences, and so on.  In addition, which taxon is named by a given
label can vary across trees (homonymy).  The approach we take is to map OTUs to
the reference taxonomy, so that OTUs in different source trees are compared by
comparing the taxa to which they map.

In 2, we compare the groupings in the supertree to those in the
taxonomy.

In 3, only a relatively small number of described taxa are represented
in source trees (currently about 200,000 in the corpus out of two
million or more known taxa), so the taxonomy covers those that are not.
The large complement of taxonomy-only taxa can be 'grafted' onto a
supertree in phylogenetically plausible locations based on how they
relate taxonomically to taxa that are known from source trees.

## Reference taxonomy requirements

This overall program dictates what we should be looking for in a
reference taxonomy.  In addition to the technical requirements derived
from the above, we have two additional requirements coming from a
desire to situate Open Tree as ongoing infrastructure for the
evolutionary biology community, rather than as a one-off study.
Following are all five requirements:

 1. *OTU coverage:* The reference taxonomy should have a taxon for
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
    that are described in the literature [more than 1.6 million, according to
    http://www.catalogueoflife.org/ retrieved 2016-11-01], so that we
    can supplement generated supertrees as described in step 3 above.
 1. *Ongoing update:* New taxa of importance to phylogenetic studies
    are constantly being added to the literature.
    The taxonomy needs to be updated with new information on an ongoing basis.
 1. *Open data:* The taxonomy must be available to anyone for unrestricted use.
    Users should not have to ask permission to copy and use the taxonomy,
    nor should they be bound by terms of use that interfere with further reuse.

An additional goal is that the process is reproducible and transparent - given
the inputs, we can both re-generate the taxonomy and see sufficient detail in
the output to understand the source of the information in the taxonomy.

No single available taxonomic source meets all requirements.  The
NCBI taxonomy has good coverage of OTUs, provides a rich source of
phyogenetically informed higher taxa, and is open, but its taxonomic
coverage is limited to taxa that have sequence data in GenBank (about
360,000 species having standard binomial names at the time of this writing).  Traditional all-life
taxonomies such as Catalogue of Life, IRMNG, and GBIF meet the
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
The taxonomy has not been vetted in detail, as that would be beyond
the capacity and focus of the Open Tree project.
It is known to contain many taxon duplications and technical artifacts.
Tolerating these shortcomings is a necessary tradeoff in
attempting to meet the above requirements.
