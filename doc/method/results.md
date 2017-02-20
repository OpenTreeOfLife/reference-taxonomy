
## Homonym analysis

There are 8043 name-strings in the final version of the taxonomy for which there are
multiple nodes.  [JAR: update number when 3.0 is final]
By comparison, there are only 1440 in GBIF. Many of
the homonyms either are artifacts of the alignment method, or reflect
misclassifications or errors in the source taxonomies.

## Evaluating the taxonomy relative to requirements

The introduction sets out requirements for an Open Tree taxonomy.
How well are these requirements met?

### OTU coverage

We set out to cover the OTUs in the Open Tree corpus of phylogenetic trees. The
corpus contains published studies (each study with one or more phylogenetic
trees) that are manually uploaded and annotated by Open Tree curators. The use
interface contains tools that help curators map the OTUs in a study to taxa in
OTT. Of the 3242 studies in the Open Tree database, 2871 have at least 50% of
OTUs mapped to OTT.  (A lower overall mapping rate usually indicates incomplete
curation, not an inability to map to OTT.)  These 2871 studies contain 538728
OTUs, and curators have mapped 514346 to OTT taxa, or 95.5%.

To assess the reason for the remaining 4.5% of OTUs being unmapped, we
investigated a random sample of 10 OTUs.  In three cases, the label
was a genus name in OTT followed by "sp" (e.g. "_Euglena sp_"),
suggesting the curator's unwillingness to make use of an OTU not
classified to species.  In the remaining seven cases, the taxon was
already in OTT, and additional curator effort would have found it.
Two of these were misspellings in the phylogeny source; one was
present under a slightly different name-string (subspecies in OTT,
species in study, the study reflecting a very recent
reclassification); and in the remaining four cases, either the taxon
was added to OTT after the study was curated, or the curation task was
left incomplete.

[JAR: measure of how many mapped OTUs come from NCBI, i.e. how close NCBI 
gets us to the mapping requirement: `python doc/method/otus_in_ncbi.py` = 
'172440 out of 195675 OTUs mapped to OTT are in NCBI' (88%)]

### Taxonomic coverage

OTT has 2.3M binomials (presumptive valid species names), vs. 1.6M for
Catalogue of Life (CoL).  The number is larger in part because the
combination of the inputs has greater coverage than CoL, and in part
because OTT has many names that are either not valid or not currently
accepted.

Since the GBIF source we used includes the Catalogue of Life [ref],
OTT includes everything in CoL.

This level of coverage would seem to meet Open Tree's taxonomic
coverage requirement as well as any other available taxonomic source.

### Backbone quality

* We can check for resolution compared to other taxonomies, e.g. NCBI, GBIF,
  IRMNG.  Crude measure is ratio of
  nonterminal to terminal = average branching factor.  Might be good
  to control for tip set (use same set of species for every taxonomy)
  and/or only look at taxa above the species level.
* How phylogenetically accurate is it?  One test of this: How many
  nodes are found paraphyletic as a result of supertree synthesis?
  2614 contested taxa (out of ...?).  Ideally we would compare
  meaningfully to NCBI, GBIF, but this would require new syntheses...
  http://files.opentreeoflife.org/synthesis/opentree7.0/output/subproblems/index.html#contested

### Ongoing update

Building OTT version 3.0 from sources requires 15 minutes of real time. Our process currently runs on a machine with 16GB of memory; 8GB is not sufficient.

In the upgrade from 2.10 to 3.0, we added new versions of both NCBI
and GBIF. NCBI updates frequently, so changes tend to be manageable
and incorporating the new version was simple. In contrast, the
version from GBIF represented both a major change in their taxonomy
synthesis method. Many taxa disappeared, requiring changes to our ad
hoc patches during the normalization stage. In addition, the new
version of GBIF used a different taxonomy file format, which requires
extensive changes to our import code (most notably, handling taxon
name-strings that now included authority information).

We estimate the the update from OTT v2.10 to OTT v3.0 required approximately 3 days of development time. This was greater than previous updates due to the changes required to handle the major changes in GBIF content and format.  

### Open data

As the Open Tree project did not enter into any data use agreements
in order to obtain OTT's
taxonomic sources, it is not obliged to require any such agreement
from users of OTT.  (A data use agreement is sometimes called 'terms
of use'.  Legally, a DUA is a kind of contract.)
Therefore, users are not restricted in this way.
In addition, the taxonomy is not creative expression, so copyright
controls do not apply.  Therefore use of OTT is
unrestricted.

Certainly the taxonomy could be improved by incorporating DUA-encumbered
sources such the IUCN Red List, but doing so would conflict with the
project's open data requirement.
