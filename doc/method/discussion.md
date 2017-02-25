
# Discussion

The primary actionable information in the source taxonomies consists of
name-strings, and therefore the core of our method is a set of heuristics that
can handle the common problems encountered when trying to merge hierarchies of
name-strings. These problems include expected taxonomic issues such as synonyms,
homonyms, and differences in placement and membership between sources. They also
include errors such as rank inconsistencies, duplications, spelling mistakes,
and misplaced taxa. Even though we find that the complicated cases are a small percentage of the total, these add up to tens of thousands of problematic alignments when the total number of nodes measures over 6 million.

Ultimately there is no fully automated and foolproof test to determine
whether two nodes can be aligned - whether node A and node B,
from different source taxonomies,
are about
the same taxon. The information to do this is in the
literature and in databases on the Internet, but often it is
(understandably) missing from the source taxonomies.

It is not feasible to investigate such problems individually, so the
taxonomy assembly methods identify and handle thousands of 'special
cases' in an automated way. We currently use only name-strings,
topology, and ranks to guide assembly. Using other
information contained in the source taxonomies, such as the structure
of species names, authority strings, and other nomenclatural information,
could be very helpful. We note the large role that our hand-curated "separation taxonomy" played in the alignment phase. This is a set of taxa that are consistent across the various sources, and allow us to make the (seemingly obvious) determination "these two taxa are in completely separate groups, so do not align them".

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

We also provide a feedback mechanism on the synthetic tree browser, and find that most of the comments left are about taxonomy. Expanding this feature to capture this feedback in a more structured, and therefore machine-readable, format would allow users to directly contribute taxonomic patches to the system.

## Comparison to other taxonomies

Given the very different goals of the Open Tree Taxonomy in comparison to most other taxonomy projects, it is difficult to compare OTT to other taxonomies in a meaningful way. The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomic database. The
GBIF method is yet unpublished. Once the GBIF method has been formally
described, it will be extremely useful to compare the two approaches and identify
common and unique heuristics to automated, scalable name-string matching.

## Potential improvements and future work

The development of the assembly asoftware has been driven by the needs
of the Open Tree project, not by any concerted effort to create a
widely applicable or theoretically principled tool.  Many improvements
are possible on both practical and theoretical grounds.  Following are
some of the directions for development that could have the highest
impact.

* It is very likely that the method could be improved by making better
  use of tree topology, and decreasing its reliance on the names of
  internal nodes.  Better use of topology might permit separation and
  identification of tips without use of a separation taxonomy,
  removing the need for the manual work of maintaining the separation
  taxonomy and aligning source taxonomies to it (when the alignment
  cannot be done automatically).

* Additional information that is available in some source taxon
  records could be put to good use in alignment, especially authority
  information.  Names could also be analyzed to detect partial
  matches, e.g. epithet-only matching (in the presence of other
  evidence such as family), spelling and gender variant recognition.

* An assembly run can lead to a variety of error conditions and test
  failures.  Currently these are difficult to diagnose, mainly for
  lack of technology for displaying the particular pieces of the
  source taxonomies, workspace, and alignment and merge history that
  are relevant to the error.  Once this information is surfaced it is
  usually not too difficult to work out a fix in the form of a patch
  or an improvement to the program.

* The presence of invalid and unaccepted names remains a significant
  problem.  The information necessary to detect them is out there in
  databases, and could be harvested.

* Curators frequently request new taxonomy sources.  The most frequently
  requested are improved fish, bird, and plant sources.  Again, the
  information is available, but not yet harvested.

* Basic usability features for application to new projects would
  include proper packaging of the application, and support for Darwin
  Core Archives.
