
# Discussion

The primary actionable information in the source taxonomies consists of
name-strings, and therefore the core of our method is a set of heuristics that
can handle the common problems encountered when trying to merge hierarchies of
name-strings. These problems include expected taxonomic issues such as synonyms,
homonyms, and differences in placement and membership between sources. They also
include errors such as rank inconsistencies, duplications, spelling mistakes,
and misplaced taxa.

Ultimately there is no fully automated and foolproof test to determine
whether two nodes can be aligned - whether node A and node B,
from different source taxonomies,
are about
the same taxon. The information to do this is in the
literature and in databases on the Internet, but often it is
(understandably) missing from the source taxonomies.

It is not feasible to curate such problem individually, so the
taxonomy assembly methods identify and handle thousands of 'special
cases' in an automated way. We currently use only name-strings,
topology, and ranks to guide assembly. Using other
information contained in the source taxonomies, such as the structure
of species names, authority strings, and other nomenclatural information,
could be very helpful.

* [JAR: something about how messy the inputs are]

## Open Tree Taxonomy as a taxonomy

We have developed the Open Tree Taxonomy (OTT) for the very specific
purpose of aligning and synthesizing phylogenetic trees. We do not
intend it to be a reference for nomenclature, or to substitute for
expert-curated taxonomic databases. Several features of OTT make it
unsuitable for taxonomic and nomenclatural purposes. It contains
many names that are either not valid or not currently accepted. Some
of these come from DNA sequencing via NCBI Taxonomy, which is also not a
taxonomic reference, while others come directly from phylogenies
submitted to Open Tree curators via our taxonomy curation features. OTT
also contains more homonyms as compared to its sources. Many of these
duplicated names are artifacts of the assembly heuristics. For our
purposes, these are not of great concern - when mapping OTUs in trees
to taxa in OTT, we generally restrict mapping to a specific taxonomic
context, and if there are multiple matches to OTT taxa with the same
name, a curator can clearly see this situation and choose the taxon
with the correct lineage.

## Allowing community curation

We have also developed a system for curators to directly add new taxon records to the
taxonomy from published phylogenies, which often contain newly
described species that are not yet present in any source taxonomy.
These taxon additions include provenance
information, including evidence for the taxon and the identity of the curator. We
expose this provenance information through the web site and the taxonomy API.
Most of the feedback on the synthetic tree of life has been about taxonomy, and expanding this
feature to other types of taxonomic information allows users to directly
contribute expertise and allows projects to easily share that information.
[KC: not sure what you mean by "expanding this feature" - what
feature? how?]

## Comparison to other taxonomies

Given the very different goals of Open Tree Taxonomy in comparison to most other taxonomy project, it is difficult to compare OTT to other taxonomies in a meaningful way. The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomy. The
GBIF method is yet unpublished. Once the GBIF method has been formally
described, it will be extremely useful to compare the two approaches and identify
common and unique heuristics to automated, scalable name-string matching.

## Potential improvements / future work
