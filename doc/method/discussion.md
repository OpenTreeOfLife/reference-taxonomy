
# Discussion

We have presented a method for merging multiple taxonomies into a single
synthetic taxonomy. The method is designed to produce a taxonomy optimized for
the Open Tree of Life phylogeny synthesis project. Most taxonomy projects are
databases of taxonomy information that are continuously updated by curators as
new information is published in the taxonomic literature. In contrast, the Open
Tree Taxonomy takes several of these curated taxonomies and produces a synthetic
taxonomy de novo each time you run the algorithm.

The individual items in a taxonomy are name strings, and therefore the core of
our method is a set of heuristics that can handle the common problems
encountered when trying to merge hierarchies of name-strings. These problems
include expected taxonomic issues such as synonyms, homonyms, and differences in
placement and membership between sources. They also include errors such as rank
inconsistencies, duplications, spelling mistakes, and mis-placed taxa. In
developing the methods for synthesis, we needed to identify and handle thousands
of 'special cases' in an automated way.

We have also developed a system for curators to directly add new taxa to the
taxonomy from published phylogenies. These taxon additions include provenance
information, including evidence for the taxon and identity of the curator. They
are made publicly available through an API, and therefore could be incorporated
by other taxonomies. Most of the OpenTree feedback has been about taxonomy, and
expanding this feature to other types of taxonomic information allows users to
directly contribute expertise and allows projects to easily share that
information.

The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomy. The
GBIF method is yet unpublished, save for a two blog posts. Once the GBIF method
is available, it would be useful to compare the two approaches and identify both
common and unique heuristics.

In addition to taxonomy synthesis, the methods described here could also be used
as as a sanity check when curating and publishing taxonomies to detect
logical errors, duplications, and other issues.  

Taxonomic information is certainly best curated at a scale smaller than "all
life" by experts in a particular group. Therefore, producing comprehensive
taxonomies should be a synthesis of curated taxonomies. We advocate for the type
of methods being used by OpenTree and by GBIF, where synthesis is done in a
repeatable fashion from sources, allowing changed information in sources to be
quickly included in the comprehensive taxonomy. Provenance information is
retained from sources and presented as part of the synthetic taxonomy. This type
of synthesis requires that source taxonomies be available online, either through
APIs or by bulk download in a format that can be easily parsed, and without
terms of use that prevent such synthesis.    
