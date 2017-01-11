
# Results

The assembly method described above yields the reference taxonomy that
is used by the Open Tree of Life project.  The taxonomy itself, the
details of how the assembly method unrolls to generate the taxonomy,
and the degree to which the taxonomy meets the goals set out for it
are all of interest.  We will address each of these three aspects of
the method in turn.


## Characterizing the overall assembly product

[begin automatically generated]

[Excluding 46104 non-taxa from analysis]

Following are some general metrics on the reference taxonomy.  [should be a table]

 * Number of taxon records:                   3550043
 * Number of synonym records:                 2027143
 * Number of internal nodes:                   276141
 * Number of tips:                            3273902
 * Number of records with rank 'species':     3117677
 * Number of nodes with binomial name-strings: 2336603
 * Number of polysemous name-strings:          8040
      * of which any of the nodes is a species: 2646
      * of which any of the nodes is a genus:   5297
      * of which neither of the above:           97
 * Maximum depth of any node in the tree: 38
 * Branching factor: average 13.02 children per internal node

The number of taxa with binomial name-strings (i.e. Genus epithet) is 
given as a proxy for the number of described species in the taxonomy.
Many records with rank 'species' have nonstandard or temporary names.  Most 
of these are from NCBI and represent either undescribed species, or
genetic samples that have not been identified to species.

[Description / motivations of flags should go to the methods section!]

Some taxa are marked with special annotations, or 'flags'.  The important flags are:

 * Flagged _incertae sedis_ or equivalent: 319083  
     number of these that are leftover children of inconsistent source nodes: 17776
 * Flagged extinct: 254921
 * Flagged infraspecific (below the rank of species): 70873
 * Flagged species-less (rank is above species, but contains no species): 66490

Following is a breakdown of how each source taxonomy contributes to the reference taxonomy.

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

Appendix: Some extreme polysemies.

 * 5 Gordonia
 * 5 Haenkea
 * 5 Heringia
 * 5 Proboscidea
 * 5 Lobularia
 * 6 FamilyI
 * 7 Lampetia
 * 237 uncultured

<snip>

## Comparison with Ruggiero et al. 2015 (goes to characterizing backbone):

 * Number of taxa in Ruggiero: 2276  of which orders/tips: 1498
 * Ruggiero orders aligned by name to OTT: 1379
 * Disposition of Ruggiero taxa above rank of order:
     * Taxon contains at least one order aligned by name to OTT: 759
     * Full topological consistency between Ruggiero and OTT: 281
     * Taxon resolves an OTT polytomy: 127
     * Taxon supports more than one OTT taxon: 275
     * Taxon conflicts with one or more OTT taxa: 76
     * Taxon containing no aligned order: 18

[end automatically generated]

## Homonym analysis:

8043 of them [from above]. That's too many.

 * Could we classify the homonyms?  by taxon rank, proximity, etc.  and compare to GBIF / NCBI
     * sibling, cousin, parent/child, within code, between codes
     * how many inherited from source taxonomy, as opposed to created?
     * could be created via separation taxonomy
     * could be created via membership separation



## Alignment

As OTT is assembled, the alignment procedure examines every source
node, either choosing an alignment target for it in the workspace, or
leaving it unaligned.  The following is a breakdown on the use frequency of the various alignment heuristics, pooled across all source
taxonomies.  [JR: actually the heuristics only come into play when there is a choice to be made, so that is only part of what is reported, just six rows out of 15 in the table.
In other situations something else happens.  Maybe two different tables?]

[combine this table with description of each row]

An explanation of each category follows the table.

         49  curated alignment
        105  align to separation taxonomy

Choice between multiple candidates determined using heuristics:

      21584  in disjoint separation taxa
        172  disparate ranks
      25800  by lineage
       7653  overlapping membership
        219  in same separation taxon
      83012  by name

Only one candidate:

    2325870  confirmed
       1767  by elimination

Not aligned:

       8592  ambiguous tip
        452  ambiguous internal
      10678  rejected
        921  disjoint membership
    3757548  not aligned

    6244422  total source taxon records

An explanation of each category follows the table.

_curated alignment:_ Some alignments are hand-crafted, usually to
repair mistakes made by automatic alignment.  

_align to separation taxonomy:_ Alignments to the separation taxonomy (for
separation calculations) are performed before the main alignment loop
begins.

_disjoint separation taxa_, ..., _by name_:
Automated source record alignments, broken down according to which
heuristic (see methods) was responsible for narrowing the candidate
set down to a single workspace node.

_confirmed_: There was only a single candidate, and it was confirmed
by a 'yes' answer from one of the heuristics (usually same name).

_by elimination_: Only a single candidate, but not confirmed by any
heuristic (match involved a synonym).

_ambiguous_: The heuristics were unable to choose from among multiple
candidates; no alignment is recorded for the source node.

_ambiguous tip_: Ambiguous, and the source node is a tip.

_ambiguous internal_: Ambiguous, and the source node is an internal
node (has children).

_rejected_: [to be done]

_disjoint membership_: [to be done]

_not aligned_: The source node was not aligned to any workspace node.
There were no candidates at all for this source taxon.

[why is 'not aligned' so much bigger than 'Number of taxon records' below?]

   * KC: do certain heuristics work better / worse for different types of problems?
     [how would one assess this ?? what are examples of 'types of problems'?]


## Merge

The merge phase examines every source node, copying unaligned source
nodes into the workspace when possible.  The following table
categorizes the fate of each source node during the merge phase.

    2162104  aligned tip
     304121  aligned internal node
    3482704  new tip
       6178  new tip (homonym)
     267746  new internal node, part of graft
       1909  refinement
       7938  merged into larger taxon
       3158  merged into larger taxon due to conflict
    6235858  total

[why is the merge total different from the alignment total?]

_aligned tip_: There is already a workspace node for the given source
node, so the source node is not copied.  The only action is to record
an additional source for the workspace node, and to copy any extinct flag.

_aligned internal node_: Similarly.

_new tip_: There were no candidates for aligning the source node, so a
new node (a tip) is added to the workspace.

_new tip (homonym)_: Same as _new tip_ but in copying the node, a
homonym is created.

_new internal node_: No descendant of the source node is aligned, so
this node is simply copied, finishing up a copy of its subtree.

_refinement_: The source node refines a classification already present
in the workspace.

_merged into larger taxon_: [should be described in methods section]

_merged into larger taxon due to conflict_: [should be described in methods section]



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

OTT has 2.1M binomials (presumptive valid names), vs. 1.6M for
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

#### Comparison with Ruggiero et al. 2015

(goes to characterizing backbone)

 * Number of taxa in Ruggiero: 2276  of which orders/tips: 1498
 * Ruggiero orders aligned by name to OTT: 1378
 * Disposition of Ruggiero taxa above rank of order:
     * Taxon contains at least one order aligned by name to OTT: 759
     * Full topological consistency between Ruggiero and OTT: 281
     * Taxon resolves an OTT polytomy: 127
     * Taxon supports more than one OTT taxon: 276
     * Taxon conflicts with one or more OTT taxa: 75
     * Taxon containing no aligned order: 18

(Interesting but not clear what lesson to draw from this - are the numbers good or bad?
Maybe do a 3-way
comparison, OTT / Ruggiero / synthesis?
With a bit of work, could get similar numbers for R. vs. synth and OTT
vs. synth.
The numbers might turn out pretty well.
OTOH using the synthetic tree as ground truth seems a bit risky? ]

### Ongoing update

NCBI update went smoothly - no intervention required.

GBIF update had some issues:

 * import code needed to be changed because columns in new GBIF backbone distrubtion are changed
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
