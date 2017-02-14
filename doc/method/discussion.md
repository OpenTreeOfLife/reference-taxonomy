
# Discussion

We have presented a method for merging multiple taxonomies into a single
synthetic taxonomy. The method is designed to produce a taxonomy optimized for
the Open Tree of Life phylogeny synthesis project. Most taxonomy projects are
databases of taxonomy information that are continuously updated by curators as
new information is published in the taxonomic literature. In contrast, the Open
Tree Taxonomy takes several of these curated taxonomies and assembles a synthetic
taxonomy de novo each time a new version of the taxonomy is needed.

The primary actionable information in the source taxonomies consists of
name-strings, and therefore the core of our method is a set of heuristics that
can handle the common problems encountered when trying to merge hierarchies of
name-strings. These problems include expected taxonomic issues such as synonyms,
homonyms, and differences in placement and membership between sources. They also
include errors such as rank inconsistencies, duplications, spelling mistakes,
and misplaced taxa. It is not feasible to curate such problem individually, so
the taxonomy synthesis methods identify and handle thousands of 'special cases'
in an automated way. We currently use only the name-strings (and ranks, in some
of the heuristics) to guide synthesis. Using other information contains in the
taxonomies (such as authority or nomenclatural information) could be possible in
the future.

We have also developed a system for curators to directly add new taxon records to the
taxonomy from published phylogenies. These taxon additions include provenance
information, including evidence for the taxon and identity of the curator. We
expose this provenance information through the website and the taxonomy API.
Most of the feedback on the synthetic tree of life has been about taxonomy, and expanding this
feature to other types of taxonomic information allows users to directly
contribute expertise and allows projects to easily share that information.

The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomy. The
GBIF method is yet unpublished. Once the GBIF method has been formally
described, it would be useful to compare the two approaches and identify both
common and unique heuristics.

Taxonomic information is certainly best curated at a scale smaller than "all
life" by experts in a particular group. Therefore, producing comprehensive
taxonomies should be a synthesis of curated taxonomies. We advocate for the type
of methods being used by OpenTree and by GBIF, where synthesis is done in a
repeatable fashion from sources, allowing changed information in sources to be
quickly included in the comprehensive taxonomy. Provenance information is
retained from sources and presented as part of the synthetic taxonomy. This type
of synthesis requires that source taxonomies be available online, either through
APIs or by bulk download, in a format that can be easily parsed, and ideally
without terms of use that prevent distribution and re-use of the resulting
synthetic taxonomies.    
