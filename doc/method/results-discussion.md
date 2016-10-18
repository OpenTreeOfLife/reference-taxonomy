
# Results

First, some overall measurements

[NMF comment:
'Any sort of metric about the OTT (2.10) are likely helpful, because
they'd illustrate the magnitude of the achievements, and magnitude of
challenges.]

* number tips, synonyms, homonyms (about 13,000) [TBD: Get metrics for OTT 2.10]
    * could classify the homonyms?  by rank, proximity, etc.  and compare to GBIF / NCBI
    * homonyms vs hemihomonyms?
* number of inter-source conflicts (1167)
* effectiveness of various alignment and separation heuristics
   * do certain heuristics work better / worse for different types of problems?
   * since the process runs through a set of heuristics until it finds a
   solution or gives up, can we say anything about the number of heuristics
   required across source / union node combinations (or the number of unresolved
     ambiguities)? i.e. 70% of combinations solved with 1st heuristic, 20% with
     second heuristic, etc?
* number of manual operations required (i.e. modifications to the generic
  assembly process)

(example of a conflict: Zygomycota (if:90405) is not included
because ... paraphyletic w.r.t. Hibbett 2007.  get proof?)

Next: The introduction sets out three requirements for the taxonomy.
How well are these requirements met?

## OTU coverage

* compare OTT's coverage of phylesystem with coverage by NCBI, GBIF
  (i.e. how well does OTT do what it's supposed to do, compared to
  ready-made taxonomies?  OTT gets X%, NCBI only gets Y%?)
* number of OTUs that are mapped, that come from NCBI - I previously
  measured this as about 97% of OTUs in phylesystem
* what about unmapped OTUs?  how many are binomials?
  (need to distinguish unmapped because someone tried and failed, from
  unmapped because nobody bothered.  check to see how many other OTUs
  in the study are mapped (measure of attempt), and/or just look up
  binomials in OTT and look for failures)

After extracting name-strings from study tip labels using a regexp, it
appears that 195934 out of 203559 names are mapped (of those studies
that are at least 50% mapped).  That's 96.3% of name-strings.  (The
regexp is pretty restrictive, so does not include subspecies or
strains.  Includes some false hits like 'Foo sp.'  Might be interesting
to count OTUs instead of name-strings.)

## Taxonomic coverage

(Table or plot showing how many taxa come from each source, & how many exclusively:)

    taxon,source,binomial-taxa-from-that-source,binomial-taxa-exclusively-from-that-source

    life total: 3453838 binomials: 2093125 single source: 959613
    life,irmng,1111550,313261
    life,worms,258378,53233
    life,silva,13953,44
    life,gbif,1629523,486411
    life,ncbi,360455,88358
    life,if,237482,18291
    life,addition,15,15

    Fungi total: 394138 binomials: 251156 single source: 35427
    Fungi,irmng,31637,192
    Fungi,worms,1347,42
    Fungi,gbif,246147,14514
    Fungi,ncbi,34134,3435
    Fungi,if,232257,17244

    Malacostraca total: 59095 binomials: 44235 single source: 5509
    Malacostraca,irmng,26343,1057
    Malacostraca,worms,33069,1375
    Malacostraca,gbif,42054,2789
    Malacostraca,ncbi,6514,288

[Another possible measurement is binomials (taxa, etc) coming
primarily from a source (as opposed to merely aligned, as in column 3
above)]

[Consider evaluating against HHDB (hemihomonyms) - for coverage and/or
accuracy (ideally we would have all senses of each HHDB polysemy)]

## Backbone quality

* number of internal nodes ??  compared to ... ?  ratio of
  nonterminal to terminal (branching factor) ... ?  average branching factor controlled
  for tip set ... ?
* how phylogenetic is it?  how many nodes are found paraphyletic by the corpus?
  2614 contested taxa (out of ...?).  ideally we would compare to NCBI, GBIF
  http://files.opentreeoflife.org/synthesis/opentree7.0/output/subproblems/index.html#contested
* something like the following: of all the internal nodes that coincide
  with GBIF nodes, how many are found paraphyletic by the corpus, as a
  fraction of all GBIF internal nodes?... how to come up with any kind
  of null hypothesis?
* rank analysis?; number of rank inversions, sampling of reasons for them (hard to figure out)

Comparing the OTT backbone with Ruggiero et al. taxonomy of all life to order:
(counts are for taxa *above* order)

* conflicts_with: 83  (R. taxa that are paraphyletic per OTT)
* resolves: 119  (R. taxa that provide resolution to OTT)
* partial_path_of: 270 (cases where several R. taxa match one OTT taxon)
* supported_by: 285 (exact matches)
* mapped tips: 1357 (R. orders that were found in OTT)
* unmapped tips: 141 (R. orders that were not found in OTT)
* other: 21 (higher R. taxa none of whose orders is in OTT)

(this could be a pie chart or bar chart?)


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
* paraphyletic taxa in conventional taxonomies

* skeleton woes: Annelida/Myzostomida, Fungi/Microsporidia
* SILVA 'sample contamination'
* taxon identity, e.g. Blattodea, Archaea - currently we just use the
  'name' even if the name goes through multiple 'taxon concepts'.
  If this were more principled, it's not clear how we would
  communicate 'taxon concept' to curators or whether curators would be
  able to make any sensible use of the information.


## File formats

[NMF asks about choice of common format [for converted source
taxonomies.

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
