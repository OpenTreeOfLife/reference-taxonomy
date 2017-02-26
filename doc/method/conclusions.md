
# Conclusions

We have presented a method for merging multiple taxonomies into a single
synthetic taxonomy. The method is designed to produce a taxonomy optimized for
the Open Tree of Life phylogeny synthesis project. Most taxonomy projects are
databases of taxonomy information that are continuously updated by curators as
new information is published in the taxonomic literature. In contrast, the Open
Tree Taxonomy takes several of these curated taxonomies and assembles a synthetic
taxonomy de novo each time a new version of the taxonomy is needed.

We have also developed a system for curators to directly add new taxa to the
taxonomy from published phylogenies. These taxon additions include provenance
information, including evidence for the taxon and identity of the curator. We
expose this provenance information through the website and the taxonomy API.
Most of the OpenTree feedback has been about taxonomy, and expanding this
feature to other types of taxonomic information allows users to directly
contribute expertise and allows projects to easily share that information.

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

### Acknowledgments

We would like to thank Nico Franz and Mark Holder for their comments
on early versions of this manuscript; Markus Döring, Tony Rees, and
Paul Kirk for answering our many questions about source taxonomies;
Cody Hinchliff for writing an early version of the assembly code and
etablishing the general approach to taxonomy combination; and Yan Wong
and other users of the Open Tree system provided many helpful comments
on the taxonomy.

Financial support was provided by the National Science Foundation
under grant number NNNNN-NNNNN.
