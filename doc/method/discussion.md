
# Discussion

The primary actionable information in the source taxonomies consists of
name-strings, and therefore the core of our method is a set of heuristics that
can handle the common problems encountered when trying to merge hierarchies of
name-strings. These problems include expected taxonomic issues such as synonyms,
homonyms, and differences in placement and membership between sources. They also
include errors such as rank inconsistencies, duplications, spelling mistakes,
and misplaced taxa.

Ultimately there is no fully automated and foolproof test to determine
whether two nodes can be aligned - whether node A and node B are about
the same taxon. The information to do this is in the
literature and in databases on the Internet, but often it is
(understandably) missing from the source taxonomies.

It is not feasible to curate such problem individually, so the taxonomy
synthesis methods identify and handle thousands of 'special cases' in an
automated way. We currently use only the name-strings (and ranks, in some of the
heuristics) to guide synthesis. Using other information contained in the source
taxonomies (such as authority or nomenclatural information) could be possible in
the future.

* [JAR: something about how messy the inputs are]

## Open Tree Taxonomy as a taxonomy
We have developed the Open Tree Taxonomy (OTT) for the very specific purpose of aligning and synthesizing phylogenetic trees. We do not intend it to be a reference for nomenclature, or to fill the role of expert-curated taxonomic databases. Several features of OTT make it not suitable for taxonomic and nomenclatural purposes. It contains many names that are either not valid or not currently accepted. Some of these come from DNA sequencing via NCBI, which is also not a taxonomic reference, while others come directly from phylogenies submitted to OpenTree curators via our taxonomy curation features. OTT also contains more homonyms as compared to its sources. Many of these duplicated names are artifacts of the synthesis heuristics. For our purposes, these are not of great concern - when mapping OTUs in trees to taxa in OTT, we generally restrict mapping to a specific taxonomic context, and if there are multiple matches to OTT taxa with the same name, a curator can clearly see this situation and choose the taxa with the correct lineage. [JAR: anything else here?]

## Allowing community curation
We have also developed a system for curators to directly add new taxon records to the
taxonomy from published phylogenies. These taxon additions include provenance
information, including evidence for the taxon and identity of the curator. We
expose this provenance information through the website and the taxonomy API.
Most of the feedback on the synthetic tree of life has been about taxonomy, and expanding this
feature to other types of taxonomic information allows users to directly
contribute expertise and allows projects to easily share that information.

## Extinct/extant annotations

One important taxon annotation is 'extinct'.  The OTT backbone
is essentially the NCBI Taxonomy, which records very few extinct taxa, so when such taxa are found in other taxonomies, there are often no higher
taxa to put them into [JAR: figure out a better way to explain this]. This had a very negative impact on the subsequent phylogeny synthesis, with most extinct taxa badly placed in the tree - for example, there were many extinct genera and families that appeared as direct children of Mammalia.  
We therefore removing from synthesis those taxa in the
taxonomy that are annotated as extinct, leading to a cleaner synthesis.

Incorporating extinct taxa into OTT would be better accomplished by adding a source that explicitly focuses on extinct taxa. 

## Comparison to other taxonomies
Given the very different goals of Open Tree Taxonomy in comparison to most other taxonomy project, it is difficult to compare OTT to other taxonomies in a meaningful way. The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomy. The
GBIF method is yet unpublished. Once the GBIF method has been formally
described, it will be extremely useful to compare the two approaches and identify
common and unique heuristics to automated, scalable name-string matching.

## Potential improvements / future work
