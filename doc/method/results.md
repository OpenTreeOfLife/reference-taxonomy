
# Results

We have described the taxonomic and nomenclatural needs of the Open Tree of
Life project, and a process for building a taxonomy that meets these needs.
Now, we present the performance of various stages of the taxonomy build process,
and assess how well the resulting taxonomy meets the needs of the Open Tree.  

[performance?]

## Alignment

As OTT is assembled, the alignment procedure examines every source
node, either choosing an alignment target for it in the workspace, or
leaving it unaligned.  The following is a breakdown, by source node,
of how it reached its decisions one way or the other, pooled for all
sources.

[combine this table with description of each row]

An explanation of each category follows the table.

         49  curated alignment
        105  align to barrier taxonomy

Choice between multiple candidates determined using heuristics:

      21584  disjoint divisions
        172  disparate ranks
      25800  by lineage
       7653  overlapping membership
        219  same division
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

_align to barrier taxonomy:_ Alignments to the barrier taxonomy (for
'division' calculations) are performed before the main alignment loop
begins.

_disjoint divisions_, ..., _by name_:
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

[KC not keen on saying 'unaligned' in one place and 'not aligned' in
another, but 'the taxon was unaligned' doesn't sound right since
'unalign' is not a verb, while 'copy a not-aligned node' doesn't work
because 'not-aligned' is an awkward adjective.]

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


## Characterizing the overall assembly product

[begin automatically generated]

General metrics on OTT:

 * Number of taxon records: 3550554
 * Number of synonym records: 2050661
 * Number of internal nodes: 276148
 * Number of tips: 3274406
 * Number of records with rank 'species': 3118191
 * Number of taxa with binomial name-strings: 2337337
 * Number of homonym name-strings: 8043  
      * of which any of the taxa is a species: 2648
      * of which any of the taxa is a genus:   5298

Annotations:

 * Number of taxa marked incertae sedis or equivalent: 318972  
     of which leftover children of inconsistent source taxa: 17669
 * Number of extinct taxa: 254929
 * Number of infraspecific taxa (below the rank of species): 70873
 * Number of species-less higher taxa (rank above species but containing no species): 66488

Assembly:

Contributions from various sources  [older numbers - need to update]

       Source     Total   Contrib   Aligned    Merged  Conflict
        silva     74412     74407         5         -         -
        h2007       227       226         1         -         -
           if    284878    281709      3103        42        24
        worms    327635    268851     57163       992       629
     study713       119       118         1         -         -
         ncbi   1320692   1198200    119486      1996      1010
         gbif   2452674   1637225    812960      1821       668
        irmng   1563961     89789   1470267      3078       827
      curated        29        29         0         -         -
        total   6024627   3550554   2462986      7929      3158


Topology:

 * Maximum depth of any node in the tree: 38
 * Branching factor: average 13.02 children per internal node

## Homonym analysis:

7360 of them [on a recent run]. That's too many.

 * Could we classify the homonyms?  by taxon rank, proximity, etc.  and compare to GBIF / NCBI
     * sibling, cousin, parent/child, within code, between codes
     * how many inherited from source taxonomy, as opposed to created?
     * could be created via barrier taxonomy separation
     * could be created via membership separation


## Evaluating the product

[I think this section does not make a contribution and should be flushed]

The outcome of the assembly method is a new version of OTT.  There are
various ways to evaluate a taxonomy.

1. The ultimate determinant of correctness is scientific and
bibliographic: are these all the taxa, are they given the right names,
and do they stand in the correct relationship to one another?  - Nobody
has the answers to all of these questions; without original taxonomic
research, the best we can do is compare to the best available
understanding, as reflected in the current scientific literature.
Doing this would be a mammoth undertaking, but (as with many options
below) one could consider samples, especially samples at particularly
suspect points.

1. We can check to see whether the tests run.  Each test has been
vetted by a curator.  However, there is a relatively small number of
tests (107).

1. We can try to figure out whether the new version is 'better' than
the new one.  What would be the measure of that?  Not size, since a
better quality taxonomy might have fewer 'junk' taxon records.

1. We can check for internal logical consistency.  But it is easy for
a taxonomy to be both consistent and wrong, or inconsistent and right.
(really?  example: rank inversion?  need to look at these?  any other
such checks?)

1. We can check whether the sources are reflected properly - but this
is not reliable, since in many cases OTT will use better information
from another source.

1. We can check that all information in OTT is justified by at least
one source, and that every piece of information in a source is
reflected in OTT.  (but this is true by construction?)

1. We can try to mathematically prove the correctness assembly program itself.

[and...]


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
