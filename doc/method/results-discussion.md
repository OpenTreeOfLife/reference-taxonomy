
# Results

## Characterizing the assembly product

 * Number of taxon records: 3577714
 * Number of synonym records: 1567363
 * Number of tips: 3323318
 * Number of binomials: 2115765
 * Number of taxa with rank 'species': 2913390
 * Number of polysemous name-strings: 13089

[TBD: filter out the was\_container nodes.  and maybe all the not\_otus.]

Annotations:

 * Number of taxa suppressed in supertree synthesis: 1248964, not suppressed 2328750
 * Incertae sedis (generically): ... of which unplaced due to merge: ...
 * Extinct: 199918
 * Suppressed at curator request: ...
 * Taxa below the level of species: ...
 * Higher taxa having no descendant species records: ...
 * Non-OTUs: [probably we should just remove these from the taxonomy!]

Polysemy analysis:

 * could we classify the polysemies?  by taxon rank, proximity, etc.  and compare to GBIF / NCBI
     * sibling, cousin, parent/child, within code, between codes
     * how many inherited from source taxonomy, as opposed to created?
     * could be created via skeleton separation
     * could be created via membership separation

## Manual vs. automatic synthesis operations

It's not just turn-and-go.

 * number of manual alignment operations required

## Characterizing the assembly process

Alignment

* Table with one row per source showing...
    * number of taxa
    * number of taxa contributed (incrementally) by that source
    * number of taxa in the source matched to the developing OTT

    source | binomial-taxa-from-that-source | binomial-taxa-exclusively-from-that-source
    if | 237482 | 18291
    worms | 258378 | 53233
    silva | 13953 | 44
    ncbi | 360455 | 88358
    gbif | 1629523 | 486411
    irmng | 1111550 | 313261
    addition | 15 | 15

 * Taxa coming from only a single source: 959613

 * Total number of source nodes: ...
 * No candidates: ...
 * >=1 candidate, no match / match:
 * >1 candidate, no match / unique match / ambiguity

Merge

 * Number of unmatched internal source nodes imported: ...
 * Number of unmatched internal source nodes merged (rather than inserted) due to non-coverage: ...
 * Number of unmatched internal source nodes dropped due to conflict between sources: 1167

  (example of a conflict: Zygomycota (if:90405) is not included because
  ... paraphyletic w.r.t. Hibbett 2007.  get proof?  not a great
  example, ncbi/gbif would be better.)

[number of taxa unplaced in one source that get placed by a later
source????  that's too detailed I think.]

## Evaluation of alignment heuristics

* effectiveness of the various alignment heuristics
   * do certain heuristics work better / worse for different types of problems?
     [how would one assess this ?? what are examples of 'types of problems'?]
   * since the process runs through a set of heuristics until it finds a
     solution or gives up, can we say anything about the number of heuristics
     required across source / union node combinations (or the number of unresolved
     ambiguities)? i.e. 70% of combinations solved with 1st heuristic, 20% with
     second heuristic, etc?

Number of times each heuristic was able to reduce the number of
candidates:

[following is some sample data to look at, for the NCBI merge.  we can
add up all these numbers for all 8 merges, and format it nicely.]

 * same-division: 971
 * ranks: 2
 * same-ancestor: 762
 * overlaps: 159
 * same-division-weak: 61
 * same-primary-name: 2235

If the alignment loop exhausts all heuristics without getting any
'yes' or 'no' from any of them, we have either alignment by process of
elimination, if a single candidate remains; or an ambiguity, if there
are several.  There were 116269 alignments by elimination, and 8088
amiguities.  Of the ambiguities, 7749 were of tips, 339 of internal
nodes.  Ambiguous tips are simply ignored, while the ambiguous
internal nodes lead to multiple nodes with the same name-string [but this
ought to be explained in the methods section].

## Evaluating the taxonomy relative to goals

The introduction sets out requirements for an Open Tree taxonomy.
How well are these requirements met?

### OTU coverage

We set out to get coverage of the Open Tree corpus of phylogenetic
trees.  Curators have mapped 514346 of 538728 OTUs from 2871 curated
studies to OTT taxa, or 95.5%.  (371 studies, those having less than
50% OTUs mapped, were excluded, as such a low mapping rate usually
indicates incomplete curation.)

To assess the reason the remaining 4.5% being unmapped, we
investigated a small random sample of 10 OTUs.  In three cases, the
label was a genus name followed by "sp" (e.g. "Euglena sp"),
suggesting an unwillingness to make any use of an OTU not classified
to species.  In the remaining seven cases, the taxon was already in
OTT, and additional curator effort would find it.  (Two of these were
misspellings in the phylogeny source; one was present under a
different name-string (subspecies in OTT, valid species in study); and
in the remaining four cases, the taxon may have been added to OTT
after the study was curated, or the curation task was left
incomplete.)

[need to explain what curation has to do with it?... appears to be
wandering off topic]

* compare OTT's coverage of phylesystem with coverage by NCBI, GBIF
  (i.e. how well does OTT do what it's supposed to do, compared to
  ready-made taxonomies?  OTT gets 95% of OTUs, NCBI only gets ??92%??)
* number of OTUs that are mapped, that come from NCBI - I previously
  measured this as about 97% of OTUs in phylesystem
* what about unmapped OTUs?  how many are binomials?
  (need to distinguish unmapped because someone tried and failed, from
  unmapped because nobody bothered.  check to see how many other OTUs
  in the study are mapped (measure of attempt), and/or just look up
  binomials in OTT and look for failures)

[can we find *any* OTUs that do not have a taxon in OTT?
rather difficult.]

### Taxonomic coverage

OTT has 2.1M, vs. 1.6M for Catalogue of Life (CoL).  The number is
larger because OTT has many names that are either not valid or not
current.

Since GBIF includes the 2011 edition of CoL [2011], OTT's coverage
includes everything in that edition of CoL.

This level of coverage meets Open Tree's taxonomic coverage goal.

[Consider evaluating against HHDB (hemihomonyms) - for coverage and/or
accuracy (ideally we would have all senses of each HHDB polysemy)]

### Backbone quality

[Not clear what to measure here.]

* what comparison to make - NCBI, GBIF, IRMNG ... ?  ratio of
  nonterminal to terminal (branching factor) ... ?  average branching factor controlled
  for tip set ... ?
* how phylogenetic is it?  how many nodes are found paraphyletic as a result of supertree synthesis?
  2614 contested taxa (out of ...?).  ideally we would compare meaningfully to NCBI, GBIF.
  http://files.opentreeoflife.org/synthesis/opentree7.0/output/subproblems/index.html#contested

Comparing the OTT backbone with Ruggiero et al. taxonomy of all life to order:
(counts are for taxa *above* order.)  (Maybe a 3-way comparison, OTT / Ruggiero / synthesis?)

 * conflicts\_with: 83  (R. taxa that are paraphyletic per OTT)
 * resolves: 119  (R. taxa that provide resolution to OTT)
 * partial\_path\_of: 270 (cases where several R. taxa match one OTT taxon)
 * supported\_by: 285 (exact matches)
 * mapped tips: 1357 (R. orders that were found in OTT)
 * unmapped tips: 141 (R. orders that were not found in OTT)
 * other: 21 (higher R. taxa none of whose orders is in OTT)

(this could be a pie chart, maybe?)

### Ongoing update

[TBD: do an NCBI update and see what happens - how much manual
intervention is required.]


### Open data

As the Open Tree project did not have to enter into data use
agreements in order to obtain its taxonomic sources, it is not obliged
to require any such agreement from users of the taxonomy.  Therefore
users are not restricted by contract (terms of use).  In addition, the
taxonomy is not creative expression, and therefore copyright
limitations do not apply either.

Certainly the taxonomy could be improved by incorporating closed
sources such as CoL and the IUCN Red List, but doing so would conflict
with the open data goal.


# Discussion

* general remarks on the method
    * artifacts, e.g. (a,b,c,d,e)f + ((a,b)g,(c,e)h)f, you can't win
      e.g. g = Homininae
    * could do resolutions instead of merges ?

* figure out some way to quantify how dirty & messy it was?
    * list of messinesses - could get this by scanning the code
      (char codings, diacritics, 'sibling homonyms', 'aunt/niece
      homonyms' (from WoRMS), monotypics, cousin homonyms, lots of
      corner cases, etc etc etc)
* challenges in construction
  (dirty inputs, homonyms, a gazillion special cases...)
* "junk" taxa (actually what does that mean?) or bad placement
* limitations of method
* OTU curation challenges?  additions feature?

* importance of provenance for debugging (e.g. recent rosids example).  [implicit criticism of other taxonomies?]
* do something messy and fix it.
* GBIF duplications and misplacements
* our Species Fungorum blunder (not knowing about it & using it)
* (almost) rank-free approach

* richly resolved classification in NCBI (e.g. has unranked taxa like eudicots.  can quantify??)
* rank analysis? ... ; number of rank inversions, sampling of reasons for them (hard to figure out)
* paraphyletic taxa in conventional taxonomies

* skeleton woes: Annelida/Myzostomida, Fungi/Microsporidia
* SILVA 'sample contamination'
* taxon identity, e.g. Blattodea, Archaea - currently we just use the
  'name' even if the name goes through multiple 'taxon concepts'.
  If this were more principled, it's not clear how we would
  communicate 'taxon concept' to curators or whether curators would be
  able to make any sensible use of the information.

* time required to build: 11 minutes 42 second real time (for OTT 2.11)

* talk about inclusions.csv ?

## File formats

[NMF asks about choice of common format for converted source
taxonomies.]

JAR answer: every source taxonomy we used is provided in a different
format, so there is no shared code on the import side.  (could review
them all...)  The Open Tree "common format" is an ad hoc format
specific to Open Tree: a taxonomy is represented as a small set of TSV
tables, trivially parsed using tools built into modern programming
languages.  Smasher also reads and writes Newick format.

With some effort we might be able to use Darwin Core Archive (DwCA)
[reference], JSON-LD [reference], or CSVW [reference] internally
and/or for publication, but would this be appropriate, and would it be
worth the effort?  In any data integration task, parsing the data is
usually the least difficult part; most of the effort is in data
cleaning and alignment.  Therefore conformance to standards has not
been a priority for the project.]

## Extinct/extant annotations

[KC: 'I wonder if there should be a separate section somewhere about extinct taxa.']

The most important taxon annotation is 'extinct'.  In early versions
of the supertree there was no awareness of the distinction between
extinct and extact taxa.  The result was that most extinct taxa were
badly placed in the tree - for example, many extinct genera and
families showed up as direct children of Mammalia.  The OTT backbone
is essentially the NCBI Taxonomy, which records very few extinct taxa,
so when such taxa are found in other taxonomies, there are no higher
taxa to put them into (??? figure out a better way to explain this).
Removing from synthesis those (usually badly placed) taxa in the
taxonomy that are annotated extinct leads to a cleaner synthesis.

example?: Bovidae, where the placement of extinct genera (from GBIF)
into subfamilies (from NCBI) is unknown.  Fungi? but that had other
problems.  Examples in primates?  umm, may need to measure
distribution of extinct taxa.

## Patches

Coming up with a patch notation that is general enough to cover most
common cases but simple enough for non-programmers to understand has
been a challenge.

[NMF: Transparency as theme - not just taxa, but process - highlight
somewhere]

[NMF: And it's good to point out how many parts that used to be
"manual" are now automated, compared to other synthesis projects
(GBIF..).  JAR: it's hard to tell but GBIF looks to be just as
automated as OTT.]

[NMF: I am also guessing that you can say something somewhere
about the efficiency/complexity of the process, because I am assuming
that it is more efficient (given the amount of operations undertaken)
than any "competitor".']

[Figure out where the following fits, if anywhere]
By scripting edits to source taxonomies, as opposed to just editing
either the sources or the final taxonomy directly, we accomplish two
things: First, the script can be applied to a later version of the
source, which means it is relatively easy to update OTT to newer
versions of source taxonomies as they come out.  Second, we can
preserve the provenance of the changes in the script.  (For now this
is done as comments in the file containing the script, but there is no
reason not to make provenance information machine readable.)
Provenance includes some combination of curator name, github issue
number, publication, and descriptive information gleaned from
investigation of the problem.

At present this provenance information [for source taxonomy patches]
is unfortunately not linked from the final taxonomy file, but it could
be, and ideally it would be.  [NMF: Fair to say it's one of the/your
longer term design maxims? If so, bring that up at some point.]

## Comparison with other projects

NCBI, IRMNG, IPNI, etc. are databases.  They probably have scripts for
updating an imported source but I don't understand how an update fails
to overwrite manual record changes that have been made since the last
import?

[GBIF is the biggy, but difficult because (1) it's not documented very
well - just the two blog posts (2) to the extent it's documented, it's
the new version that is, not the one we used in assembly.  Cynical
view: engineers are not scientists - they want to make things that
work, not understand them or teach them.  Look at Markus's blog posts
I guess and make best effort.]

* maybe discuss global names architecture, bionames, bioguid ... GNA
  doesn't deal with synonyms, for example; does bionames?  what about
  misspellings?


## Potential improvements / future work

* It would be good to find an alternative to the skeleton.  One thing
  to try is continuity: we know that taxa cannot be mapped only on
  name, but it is possible that pairs of 'nearby' taxa *can* be mapped
  by name: if A and B are close (in the taxonomic tree), and A maps by
  name to A' and B to B', and A' and B' are close, then A and B map to
  A' and B'.
* Would be good to try membership based alignment of internal nodes.
* Should deal with the large number of higher-taxon ambiguities due to
  disjointness - probably most could be merged
* More work on *removing* names
* Rank inversions are probably errors and should be fixed.
* Anchoring OTT ids to source taxonomy records (particular version).
* microbes (SILVA tips) - internal disagreement in the project, but
  there are ways we could move forward
