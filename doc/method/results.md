
# Results

We have described the taxonomic and nomenclatural needs of the Open Tree of
Life project, and a process for building a taxonomy that meets these needs.
Now, we present the performance of various stages of the taxonomy build process,
and how the resulting taxonomy better meets the needs of the Open Tree.  

## Alignment

The alignment procedure examines every source node.  The following is
a breakdown of the disposition of source nodes, across all sources, as
OTT is assembled.  Explanations of the categories follow.

```
     49  curated alignment
    105  align to skeleton taxonomy

  21584  disjoint divisions
    172  disparate ranks
  25800  by lineage
   7653  overlapping membership
    219  same division
  83012  by name

2325870  confirmed
   1767  by elimination
   8592  ambiguous tip
    452  ambiguous internal
  10678  disjoint membership
    921  disjoint membership
3757548  not aligned

6244422  total source taxon records
```


_curated alignment:_ Some alignments are hand-crafted, usually to
repair mistakes made by automatic alignment.  

_align to skeleton taxonomy:_ Alignments to the skeleton (for
'division' calculations) are performed before the main alignment loop
begins.

_disjoint divisions_, ..., _by name_:
Automated source record alignments, broken down according to which
heuristic (see methods) was responsible for narrowing the candidate
set down to a single union record.

_confirmed_: There was only a single candidate, and it was confirmed
by a 'yes' answer from one of the heuristics (usually same name).

_by elimination_: Only a single candidate, but not confirmed by any
heuristic (match involved a synonym).

_ambiguous_: The heuristics were unable to choose from among multiple
candidates; no alignment is recorded for the source node.

_ambiguous tip_: Ambiguous, and the source node is a tip.

_ambiguous internal_: Ambiguous, and the source node is an internal
node (has children).

_not aligned_: There were union taxonomy no candidates at all for this
source taxon.  The source taxon name is new at this point in assembly.


   * KC: do certain heuristics work better / worse for different types of problems?
     [how would one assess this ?? what are examples of 'types of problems'?]


## Merge

The merge phase examines every source node, copying unaligned source
nodes into the union taxonomy when possible.  The following table
categorizes the fate of each source node during the merge phase.

```
2162104  aligned tip
 304121  aligned internal node
3482704  new tip
   6178  new tip (polysemous)
 267746  new internal node, part of graft
   1909  refinement
   7938  merged into larger taxon
   3158  merged into larger taxon due to conflict
6235858  total
```

[why is the merge total different from the alignment total?]

_aligned tip_: There is already a union node for the given source
node, so the source node is not copied.  The only action is to record
an additional source for the union node, and to copy any extinct flag.

_aligned internal node_: Similarly.

_new tip_: There were no candidates for aligning the source node, so a
new node (a tip) is added to the union taxonomy.

_new tip (polysemous)_: Same as _new tip_ but in copying the node, a
polysemy is created.

_new internal node_: No descendant of the source node is aligned, so
this node is simply copied, finishing up a copy of its subtree.

_refinement_: The source node refines a classification already present
in the union.

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
 * Number of polysemous name-strings: 8043  
     of which species 2648, genera 5298

Annotations:
 * Number of taxa marked incertae sedis or equivalent: 318972  
     of which leftover children of inconsistent source taxa: 17669
 * Number of extinct taxa: 254929
 * Number of infraspecific taxa (below the rank of species): 70873
 * Number of species-less higher taxa (rank above species but containing no species): 66488

Assembly:
 * Contributions from various sources  [older numbers - need to update]
```
       Source   Contrib   Aligned    Merged  Conflict
        silva     74428         5         -         -
        h2007       226         1         -         -
           if    281748      3014        38        16
        worms    269574     55860       912       472
     study713       118         1         -         -
         ncbi   1164001    118893      1721       728
         gbif   1111758    747237      1126       408
        irmng    398675   1162528       703       161
     curation        17         0         -         -
         misc         2         0         -         -
        total   3300547   2087539      4500      1785
```

Topology:
 * Maximum depth of any node in the tree: 38
 * Branching factor: average 13.38 children per internal node

## Comparison with Ruggiero 2015

 * Number of taxa in Ruggiero: 2276
 * Ruggiero orders aligned by name to OTT: 1355
 * Disposition of Ruggiero taxa above rank of order:
     * Taxon contains at least one order aligned by name to OTT: 757
     * Fully consistent alignment between Ruggiero and OTT: 278
     * Taxon resolves an OTT polytomy: 123
     * Taxon supports more than one OTT taxon: 276
     * Taxon conflicts with one or more OTT taxa: 80
     * Taxon containing no aligned order: 20

## Polysemy analysis:

 * Could we classify the polysemies?  by taxon rank, proximity, etc.  and compare to GBIF / NCBI
     * sibling, cousin, parent/child, within code, between codes
     * how many inherited from source taxonomy, as opposed to created?
     * could be created via skeleton separation
     * could be created via membership separation


## Evaluating the product

The outcome of the assembly method is a new version of OTT.  There are
various ways to evaluate a taxonomy.

1. The ultimate determinant of correctness is scientific and
bibliographic: are these all the taxa, are they given the right names,
and do the stand in the correct relationship to one another?  - Nobody
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

1. We can try to verify the assembly program itself.

[and...]


## Evaluating the taxonomy relative to goals

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
OTT's coverage includes everything in that edition of CoL.

This level of coverage would seem to meet Open Tree's taxonomic
coverage goal. [hmm, hard to make a definite statement since the goal
is 'best effort' rather than quantitative]

[As another coverage check, and test of alignment, consider evaluating
against HHDB (hemihomonyms) - ideally we would have all senses of
each HHDB polysemy, in the right places]

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

Comparing the OTT backbone with Ruggiero et al. taxonomy of all life
to order: (counts are for taxa *above* order.)  (Maybe a 3-way
comparison, OTT / Ruggiero / synthesis?  We can ask which has fewer
inconsistencies, Ruggiero or OTT.

Following is the breakdown of Ruggiero taxa in comparison to OTT:

 * mapped tips: 1357 (R. orders that were found in OTT)
 * unmapped tips: 141 (R. orders that were not found in OTT)

 * supported\_by: 285 (exact matches)
 * resolves: 119  (R. taxa that provide resolution to OTT)
 * partial\_path\_of: 270 (cases where one R. taxa matches multiple OTT taxa i.e. OTT is more highly resolved)
 * conflicts\_with: 83  (R. taxa that are paraphyletic per OTT)
 * other: 21 (higher R. taxa none of whose orders is in OTT)

With a bit of work, could get similar numbers for R. vs. synth and OTT
vs. synth.

[Using the synthetic tree as ground truth seems a bit risky? But the
numbers could turn out pretty well.]

### Ongoing update

[TBD: do an NCBI update and see what happens - how much manual
intervention is required.  - The fact that we haven't done any other
updates would threaten any claim that this goal has been met, but
maybe talking about NCBI is enough.]


### Open data

As the Open Tree project did not have to enter into data use
agreements in order to obtain OTT's taxonomic sources, it is not
obliged to require any such agreement from users of OTT.  Therefore,
users are not restricted by any contract (terms of use).  In addition,
the taxonomy is not creative expression, so copyright limitations do
not apply either.  Therefore use of OTT is unrestricted.

Certainly the taxonomy could be improved by incorporating closed
sources such the IUCN Red List, but doing so would conflict with the
project's goal of providing open data.
