
# Results

[KC: results section should start with comment about difficulty in assessing correctness.
Mention spot checking.]

[begin automatically generated]

General metrics:
 * Number of taxon records: 3300547
 * Number of synonym records: 1543159
 * Number of internal nodes: 249706
 * Number of tips: 3050841
 * Number of records with rank 'species': 2870706
 * Number of taxa with binomial name-strings: 2112660
 * Number of polysemous name-strings: 9295  
     of which species 2531, genera 6622

Annotations:
 * Number of taxa marked incertae sedis or equivalent: 322155  
     of which leftover children of inconsistent source taxa: 22957
 * Number of extinct taxa: 167501
 * Number of infraspecific taxa (below the rank of species): 71185
 * Number of species-less higher taxa (rank above species but containing no species): 1
 * Number of taxa suppressed for supertree synthesis purposes: 861323

Assembly:
 * Contributions from various sources
       Source   Contrib   Aligned    Merged  Conflict
        silva     74428         5         -         -
        h2007       226         1         -         -
           if    281748      3014        38        16
        worms    269574     55860       912       472
     study713       118         1         -         -
         ncbi   1164001    118893      1721       728
         gbif   1111758    747237      1126       408
        irmng    398675   1162528       703       161
    additions        17         0         -         -
         misc         2         0         -         -
        total   3300547   2087539      4500      1785

Topology:
 * Maximum depth of any node in the tree: 38
 * Branching factor: average 13.38 children per internal node

Comparison with Ruggiero
 * Number of taxa in Ruggiero: 2276
 * Ruggiero orders aligned by name to OTT: 1355
 * Disposition of Ruggiero taxa above rank of order:
     * Taxon contains at least one order aligned by name to OTT: 757
     * Fully consistent alignment between Ruggiero and OTT: 278
     * Taxon resolves an OTT polytomy: 123
     * Taxon supports more than one OTT taxon: 276
     * Taxon conflicts with one or more OTT taxa: 80
     * Taxon containing no aligned order: 20

[end automatically generated]

Polysemy analysis:

 * Could we classify the polysemies?  by taxon rank, proximity, etc.  and compare to GBIF / NCBI
     * sibling, cousin, parent/child, within code, between codes
     * how many inherited from source taxonomy, as opposed to created?
     * could be created via skeleton separation
     * could be created via membership separation

## Characterizing the assembly process

Alignment

* Table with one row per source summarizing the fates of its records.
    * number of taxa (records)
    * number of taxa in the source matched to union (already there)
    * number of taxa contributed (incrementally) by that source (new)
    * number of higher taxa lost due to merges and inconsistensies

[The following is not that table, but similar]

    source   | binomial-taxa-from-that-source
    if       |  237482
    worms    |  258378
    silva    |   13953
    ncbi     |  360455
    gbif     | 1629523
    irmng    | 1111550
    addition |      15

Breakdown of source nodes by fate

 * Total number of source nodes: ...
 * 'Manually' aligned: ...
 * No candidates: ... about 959613 ...
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

## Manual vs. automatic synthesis operations

It's not just turn-and-go.

 * number of manual alignment operations required

recent example of bad alignment: Morganella fungus -> bacteria

## Evaluation of alignment heuristics

* effectiveness of the various alignment heuristics
   * KC: do certain heuristics work better / worse for different types of problems?
     [how would one assess this ?? what are examples of 'types of problems'?]
   * KC: since the process runs through a set of heuristics until it finds a
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

If the alignment loop exhausts all heuristics without getting a
'yes' or 'no' from any of them, we have either alignment by process of
elimination, if a single candidate remains; or an ambiguity, if there
are several.  There were 116269 alignments by elimination, and 8088
amiguities.  Of the ambiguities, 7749 were of tips, 339 of internal
nodes.  Ambiguous tips are simply ignored [I think the method section
says this], while the ambiguous internal nodes may lead to multiple nodes
with the same name-string [but this ought to be explained in the
methods section].  (Some of these nodes may be eliminated during merge.)

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
rather difficult.  this is what the additions feature was for.]

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

Every source taxonomy we imported is provided in a different format,
so there is no shared import code on the import side.  The Open Tree
taxonomy exchange format is a simple, ad hoc format specific to Open
Tree: a taxonomy is represented as a three TSV tables, for taxa,
synonyms, and identifier aliases (merges).
This form is trivially parsed using tools built into modern
programming languages.

GBIF and IRMNG are provided using quite different subsets of Darwin
Core Archive (DwCA) format.  [reference GBIF] With some effort we might be
able to import general Darwin Core Archive (DwCA), JSON-LD
[reference W3C], or CSVW [reference W3C] internally and/or for publication.
If we were going to import a large number of sources in one of these
formats, establishing such facilities would be a good investment.

Smasher reads Newick format, which is used for one of the sources
(Hibbett 2007).

In any data integration task, parsing the data is
usually the least difficult part; most of the effort is in data
cleaning and alignment.  Therefore conformance to standards has not
been a priority for the project.

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

* Fishbase, world bird names, plant list
* It would be good to find an alternative to the skeleton.  One thing
  to try is continuity: we know that taxa cannot be matched only on
  name, but it is possible that pairs of 'nearby' taxa *can* be matched
  by name: if A and B are close in the source, and A maps by
  name to A' and B to B', and A' and B' are close in the target, 
  then it is very likely that A and B map to A' and B', respectively.
  [Probably a subject for another paper.]
* Would be good to try membership based alignment of internal nodes.
  Not clear what to do when membership based and name based
  (heuristic) alignments conflict.
* Should deal with the large number of higher-taxon ambiguities due to
  equivocal disjointness - probably most could be merged
* More work on *removing* names - e.g. can use IRMNG annotations 
  to remove names from GBIF
* Rank inversions are probably errors and should be fixed somehow.
* Anchoring OTT ids to source taxonomy records (particular version).
  (this is sort of done.  have list but not implemented in smasher.)
* microbes (SILVA tips) - add all clusters for assembly, then remove 
  or hide clusters we don't want at the very end
