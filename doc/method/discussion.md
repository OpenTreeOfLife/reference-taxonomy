
# Discussion

The primary actionable information in the source taxonomies consists of
name-strings, and therefore the core of our method is a set of heuristics that
can handle the common problems encountered when trying to merge hierarchies of
name-strings. These problems include expected taxonomic issues such as synonyms,
homonyms, and differences in placement and membership between sources. They also
include errors such as duplications, spelling mistakes,
and misplaced taxa. The problem cases add up to over 100,000 difficult alignments when the total number of source records measures over 6 million.
<!-- 
Estimating the number of problems (first group):
choice made + ambiguous + ambiguous + reject
(+ 139845 8540 205 16228) = 164818

Estimating number of errors (second group):
There are no rank inconsistencies any more.
Number of duplications is estimated by number of ambiguities,
which is 8540 + 205.
Spelling errors - no way to know.
Number of id collisions (qid-based vs. alignment-based) is 7700,
according to comment in assemble_ott.py.
Misplaced taxa ... hard to say.
-->

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
rudimentary classification information, and (minimally) ranks to guide assembly. We note the large role that our hand-curated "separation taxonomy" played in the alignment phase. This is a set of taxa that are consistent across the various sources, and allow us to make the (seemingly obvious) determination "these two taxa are in completely separate groups, so do not align them".

## Open Tree Taxonomy as a taxonomy

We have developed the Open Tree Taxonomy (OTT) for the very specific
purpose of aligning and synthesizing phylogenetic trees. We do not
intend it to be a reference for nomenclature, or to substitute for
expert-curated taxonomic databases. Several features of OTT make it
unsuitable for taxonomic and nomenclatural purposes. It contains
many names that are either not valid or not currently accepted. Some
of these come from DNA sequencing via NCBI Taxonomy, which is also not a
taxonomic reference, while others come directly from phylogenies
submitted by Open Tree curators via our taxonomy curation features. OTT
also contains more homonyms as compared to its sources. Most of these
are duplicates that are artifacts of the assembly heuristics. For our
purposes, these are not of great concern - when mapping OTUs in trees
to taxa in OTT, we generally restrict mapping to a specific taxonomic
context, and if there are multiple matches to OTT taxa with the same
name, a curator can clearly see this situation and choose the taxon
with the correct lineage.

## Community curation

We have also developed a system for curators to directly add new taxon records to the
taxonomy from published phylogenies, which often contain newly
described species that are not yet present in any source taxonomy.
These taxon records include provenance
information, including references explaining the taxon, and the identity of the curator. We
expose this provenance information through the web site and the taxonomy API.

We also provide a feedback mechanism on the synthetic supertree browser, and find that most of the comments left are about taxonomy. Expanding this feature to capture this feedback in a more structured, and therefore machine-readable, format would allow users to directly contribute taxonomic patches to the system.

## Comparison to other taxonomies

Given the very different goals of the Open Tree Taxonomy in comparison to most other taxonomy projects, it is difficult to compare OTT to other taxonomies in a meaningful way. The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomic database. The
GBIF method is yet unpublished. Once the GBIF method has been formally
described, it will be useful to compare the two approaches and identify
common and unique techniques for automated, scalable name-string matching.

## Potential improvements and future work

The development of the assembly asoftware has been driven by the needs
of the Open Tree project, not by any concerted effort to create a
widely applicable or theoretically principled tool.  Many improvements
are possible on both practical and theoretical grounds.  Following are
some of the directions for development that could have the highest
impact.

* It is very likely that alignment could be improved by making better
  use of species proximity implied by the shape of the classification,
  and decreasing its reliance on the names of
  internal nodes.  Better use of proximity might permit separation and
  identification of tips without use of a separation taxonomy,
  removing the need for the manual work of maintaining the separation
  taxonomy and the adjustment directives needed to align source taxonomies to it.

* Additional information that is available in some source taxon
  records could be put to good use in alignment, especially authority
  information.  Names could also be analyzed to detect partial
  matches, e.g. matching on species epithets even when the genus
  disagrees, and spelling and gender variant recognition.

* An assembly run can lead to a variety of error conditions and test
  failures.  Currently these are difficult to diagnose, mainly for
  lack of technology for displaying the particular pieces of the
  sources, workspace, and assembly history that
  are relevant to the error.  Once this information is surfaced it is
  usually not too difficult to work out a fix in the form of a patch
  or an improvement to the program logic.

* The community curation should be developed, as mentioned above.
  Its success would depend on allowing users to test proposed changes
  and diagnose and repair any problems with them.

* Curators frequently request new taxonomy sources.  The most frequently
  requested are improved fish, bird, and plant sources.  Again, the
  information is available, but not yet harvested.

  Some frequently requested sources may only be accessed under
  agreement with contractual terms (variously called "terms of use" or
  a "data use agreement").  One of these is the IUCN Red List [ref
  ...], an important source of up-to-date information on mammal
  species.  These sources are off limits to Open Tree due to the
  project's open data requirement.

* The presence of invalid and unaccepted names remains a significant
  problem.  The information needed to detect them is available,
  and could be harvested.

* Basic usability features for application to new projects would
  include proper packaging of the application, and support for Darwin
  Core [ref].

Future work on taxonomy aggregation should attempt a more rigorous and
pluralistic approach to classification [ref Nico Franz].  Alignment should detect and
record lumping and splitting events, and the classification conflicts
detected during merge should be exposed to users.  Exposing conflicts
is in the interest of scientific transparency.  Retaining alternative
groupings could be useful in phylogenetic analysis, as
a check on which of the sources agree or disagree with a given
analysis.  Lumping and splitting, when they can be detected, could be
recorded as taxon that has, as one of its children, a distinct taxon
with the same name-string.  Ideally better handling of "taxon
concepts" in aggregators would encourage sources to make links to
primary sources more readily available for a variety of
purposes.
