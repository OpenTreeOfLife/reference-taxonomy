
## results - some notes -

Following is a breakdown of how each source taxonomy contributes to
the reference taxonomy.  [some document-generation automation needed here]

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

* source = name of source taxonomy
* total = total number of nodes in source
* copied = total number of nodes originating from this source (copied)
* aligned = number of source nodes aligned and copied
* absorbed = number of source nodes absorbed
* conflict = number of inconsistent source nodes

For possible discussion:

 * Number of taxa suppressed for supertree synthesis purposes: 696041

### Homonym analysis:

8043 of them [from above]. Compare 1440 in GBIF. Many of these are
artifacts of the alignment method, especially the rule that says
genera that do not share species are presumed disjoint.

 * Could we classify the homonyms?  by taxon rank, proximity, etc.  and compare to GBIF / NCBI
     * sibling, cousin, parent/child, within code, between codes
     * how many inherited from source taxonomy, as opposed to created?
     * could be created via separation taxonomy
     * could be created via membership separation

Following are the homonyms naming five or more nodes.

 * 5 Gordonia
 * 5 Haenkea
 * 5 Heringia
 * 5 Proboscidea
 * 5 Lobularia
 * 6 FamilyI
 * 7 Lampetia
 * 237 uncultured

'FamilyI' and 'uncultured' refer to phylogenetically supported groups
from SILVA for which the SILVA curators have not yet assigned more
descriptive names.


[why is 'not aligned' so much bigger than 'Number of taxon records'?]


   * KC: do certain heuristics work better / worse for different types of problems?
     [how would one assess this ?? what are examples of 'types of problems'?]


_absorbed into larger taxon_: [should be described in methods section]

_absorbed into larger taxon due to conflict_: [should be described in methods section]


[example of absorption: ...?]

[example of a conflict: Zygomycota (if:90405) is not included because
  ... paraphyletic w.r.t. Hibbett 2007.  get proof?  not a great
  example, ncbi/gbif would be better.]

[Interesting?:  57 taxa that were unplaced in a higher priority source
get placed by a lower priority source.]


## Evaluating the taxonomy relative to requirements

The introduction sets out requirements for an Open Tree taxonomy.
How well are these requirements met?

### OTU coverage

We set out to cover the OTUs in the Open Tree corpus of phylogenetic
trees.  To assess this, we looked at the 2871 curated studies having
at least 50% of OTUs mapped to OTT (excluding 371 from the total set).
A low mapping rate usually indicates incomplete curation, not an
inability to map to OTT.  Curators have mapped 514346 of 538728 OTUs
from these studies to OTT taxa, or 95.5%.

To assess the reason for the remaining 4.5% of OTUs being unmapped, we
investigated a random sample of 10 OTUs.  In three cases, the label
was a genus name in OTT followed by "sp" (e.g. "Euglena sp"),
suggesting the curator's unwillingness to make use of an OTU not
classified to species.  In the remaining seven cases, the taxon was
already in OTT, and additional curator effort would have found it.
Two of these were misspellings in the phylogeny source; one was
present under a slightly different name-string (subspecies in OTT,
species in study, the study reflecting a very recent
reclassification); and in the remaining four cases, either the taxon
was added to OTT after the study was curated, or the curation task was
left incomplete.

[do we need to explain what curation has to do with it?...]

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

NCBI update went smoothly - no intervention required.

GBIF update had some issues:

 * import code needed to be changed because columns in new GBIF backbone distribution are changed
 * lots of taxa are missing, requiring adjustments to patches, and a few new ones.

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
