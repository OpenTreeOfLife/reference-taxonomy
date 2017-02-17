## Summary of Open Tree Taxonomy

The methods and results presented here are for version 3.0 of the Open Tree Taxonomy (which follows five previous releases using the automated assembly method). The taxonomy contains 3,592,725 total taxa, X tips and Y internal nodes. There are 2,031,519 synonym records and 8043 homonyms (name-strings for which there are multiple nodes).

## Contributions of source taxonomies to the assembly

Following is a breakdown of how each source taxonomy contributes to
the reference taxonomy.  [should be a proper table]

        source     total    copied   aligned  absorbed  conflict
    separation        26        26         0         -         -
         silva     74401     74396         5         -         -
         h2007       227       226         1         -         -
            if    284878    281709      3103        42        24
         worms    327633    268847     57163       992       631
      study713       119       118         1         -         -
          ncbi   1320679   1198186    119485      1997      1011
          gbif   2452614   1636666    813455      1822       671
         irmng   1563804     89840   1470048      3083       833
       curated        29        29         0         -         -
         total   6024410   3550043   2463261      7936      3170

Key:

* source = name of source taxonomy
* total = total number of nodes in source
* copied = total number of nodes originating from this source (copied)
* aligned = number of source nodes aligned and copied
* absorbed = number of source nodes absorbed
* conflict = number of inconsistent source nodes


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

* compare OTT's coverage of phylesystem with coverage by NCBI, GBIF
  (i.e. how well does OTT do what it's supposed to do, compared to
  ready-made taxonomies?  OTT gets 95% of OTUs, NCBI only gets ??92%??
  (besides just being interesting, this will tell us whether we could
   have gotten away with just NCBI, or if GBIF and the rest were really
   needed.)
  (how about GNI?? trying to think of an independent name source
  to compare to, as a control?)
* number of OTUs that are mapped, that come from NCBI - I previously
  measured this as about 97% of OTUs in phylesystem (actually 97%
  of taxon names, not OTUs)
* what about unmapped OTUs?  of those, how many are binomials (and
  presumably mappable)?

[can we find *any* OTUs that do not have a taxon in OTT?
rather difficult.  this is what the curation feature was for.]

### Taxonomic coverage

OTT has 2.3M binomials (presumptive valid species names), vs. 1.6M for
Catalogue of Life (CoL).  The number is larger in part because the
combination of the inputs has greater coverage than CoL, and in part
because OTT has many names that are either not valid or not currently
accepted.

Since the GBIF source we used includes the 2011 edition of CoL [2011],
OTT includes everything in that edition of CoL.  [did it get updated
for 2016 GBIF?]

This level of coverage would seem to meet Open Tree's taxonomic
coverage requirement as well as any other available taxonomic source.

[As another coverage check, and test of alignment, consider evaluating
against HHDB (hemihomonym database) - ideally we would have all senses of
each HHDB hemihomonym, in the right places]

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

Building OTT version 2.11 from sources requires 11 minutes 42 second of real time [KC: and memory?].

In the upgrade from 2.10 to 2.11, we added new versions of both NCBI and GBIF. NCBI updates frequently, so changes tend to be minimal and incorporating the new version was trivial. In contrast, the version from GBIF represented a major change in their taxonomy synthesis method. The file format changed, requiring changes in our import code. In addition, many taxa disappeared, requiring changes to our ad hoc patches during the normalization stage.  

* [KC: estimate on number of curation hours required for 2.10 -> 2.11 update?]

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
