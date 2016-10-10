
# Results

The introduction sets out three requirements for the taxonomy.  How
well are these requirements met?


General results on the Open Tree taxonomy. Not a review on the taxonomic quality of any particular clade.

* number tips, synonyms, homonyms [TBD: Get metrics for OTT 2.10]
* number of inter-source conflicts (about 1000)

## OTU coverage

* compare OTT's coverage of phylesystem with coverage by NCBI, GBIF
  (i.e. how well does OTT do what it's supposed to do, compared to
  ready-made taxonomies?)
* number of OTUs that are mapped, that come from NCBI - I previously
  measured this as about 97% of OTUs in phylesystem
* what about unmapped OTUs?  how many are binomials?
  (need to distinguish unmapped because someone tried and failed, from
  unmapped because nobody bothered.  check to see how many other OTUs
  in the study are mapped (measure of attempt), and/or just look up
  binomials in OTT and look for failures)

## Backbone quality

* number of internal nodes ??  compared to ... ?
* how phylogenetic is it?  how many nodes are found paraphyletic by the corpus?
* something like the following: of all the internal nodes that coincide
  with GBIF nodes, how many are found paraphyletic by the corpus, as a
  fraction of all GBIF internal nodes?... how to come up with any kind
  of null hypothesis?
* rank analysis?; number of rank inversions


## Taxonomic coverage

(Table or plot showing how many taxa come from each source, & how many exclusively:)

    taxon,source,binomials-from-that-source,binomials-exclusively-from-that-source

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


# Discussion

* challenges in construction
  (dirty inputs, homonyms, a gazillion special cases...)
* "junk" taxa or bad placement
* limitations of method
* what this is *not*: reference for nomenclature (nomenclature is outsourced); well-formed taxonomic hypothesis
* OTU curation challenges?  additions feature?

* importance of provenance.  [implicit criticism of other taxonomies?]
* do something messy and fix it.
* the SILVA tips problem?
* GBIF duplications and misplacements
* our Species Fungorum blunder
* rank-free approach
* compare discuss global names architecture, bionames, bioguid

* rich resolution in NCBI
* paraphyletic taxa in conventional taxonomies

* skeleton woes: Annelida/Myzostomida, Fungi/Microsporidia

* artifacts, e.g. (a,b,c,d,e)f + ((a,b)g,(c,e)h)f

[NMF asks about choice of common format [for converted source
taxonomies].  Every source taxonomy we used is provided in a different
format, so there is no shared code.  And the "common format" is an ad
hoc format specific to Open Tree.  With some effort we might be able
to use Darwin Core Archive (DwCA) [reference], JSON-LD [reference], or
CSVW [reference] internally and/or for publication, but would this be
appropriate, and would it be worth the effort?  The format we use is
very simple - just a small number of tables, trivially parsed using
tools built into modern programming languages.  In any data
integration task, parsing the data is usually the least difficult
part; most of the effort is in data cleaning and alignment.  Therefore
conformance to standards has been put off to the future.  - maybe this
belongs in the discussion section.]


The most important annotation is 'extinct'.  We initially included
extinct taxa in the synthetic phylogenetic tree, and the result was
that most extinct taxa were badly placed in the tree - for example,
many extinct genera showed up as direct children of Mammalia.  While
the NCBI Taxonomy usually gives a modern classification for the
species it covers, it makes no attempt to fit in extinct taxa, so when
such taxa are found in other taxonomies, there is no good place to put
them in the Open Tree taxonomy.  Removing from synthesis those
(usually badly placed) taxa in the taxonomy that are annotated extinct
leads to a cleaner synthesis.

[KC: 'I wonder if there should be a separate section somewhere about extinct taxa.']

Coming up with a patch notation that is general enough to cover most
common cases but simple enough for non-programmers to understand has
been a challenge.

NMF: Transparency as theme - highlight somewhere

NMF comment:
'Any sort of metric about the OTT (2.10) are likely helpful, because
they'd illustrate the magnitude of the achievements, and magnitude of
challenges. And it's good to point out how many parts that used to be
"manual" are now automated, compared to other synthesis projects
(GBIF..). I am also guessing that you can say something somewhere
about the efficiency/complexity of the process, because I am assuming
that it is more efficient (given the amount of operations undertaken)
than any "competitor".'

[I think the following para is garbage now]
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

## Comparison with GBIF

[Difficult because (1) it's not documented very well (2) to the extent
it's documented, it's the new version that is, not the one we used in
assembly.  Engineers are not scientists.  Look at Markus's blog posts
I guess and make best effort.]

## Potential improvements / future work

Would be good to try membership based alignment of internal nodes.

Rank inversions are probably errors and should be fixed.

Anchoring OTT ids to source taxonomy records (particular version).
