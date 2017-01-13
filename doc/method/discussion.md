
# Discussion

We have presented a method for merging multiple taxonomies into a single
synthetic taxonomy. The individual items in a taxonomy are name strings, and
therefore our method is a set of heuristics that can handle the common
problems encountered when comparing hierarchies of name-strings - synonyms,
homonyms, differences in placement. The method is designed to produce a
taxonomy optimized for the Open Tree of Life phylogeny synthesis project.
Most taxonomy projects are databases of taxonomy information that are
continuously updated by curators as new information is published in the
taxonomic literature. In contrast, the Open Tree Taxonomy takes several of these
curated taxonomies and produces a synthetic taxonomy de novo each time you run
the algorithm.

The Open Tree Taxonomy is most similar to the GBIF taxonomy, in the sense that
both are a synthesis of existing taxonomies rather than a curated taxonomy. The
GBIF method is yet unpublished, save for a few blog posts. Once the GBIF method
is available, it would be interesting to compare the two approaches.

In addition to taxonomy synthesis, the methods described here could also be used
as as a sanity check when curating and publishing taxonomies. Could detect
logical erros, duplications, etc.  

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
* OTU curation challenges?  curation feature?

* importance of provenance for debugging (e.g. recent rosids example).  [implicit criticism of other taxonomies?]
* do something messy and fix it.
* GBIF duplications and misplacements
* our Species Fungorum blunder (not knowing about it & using it)
* (almost) rank-free approach

* richly resolved classification in NCBI (e.g. has unranked taxa like eudicots.  can quantify??)
* rank analysis? ... ; number of rank inversions, sampling of reasons for them (hard to figure out)
* paraphyletic taxa in conventional taxonomies

* barrier woes: Annelida/Myzostomida, Fungi/Microsporidia
* SILVA 'sample contamination'
* taxon identity, e.g. Blattodea, Archaea - currently we just use the
  'name' even if the name goes through multiple 'taxon concepts'.
  If this were more principled, it's not clear how we would
  communicate 'taxon concept' to curators or whether curators would be
  able to make any sensible use of the information.

* time required to build: 11 minutes 42 second real time (for OTT 2.11)

* talk about inclusions.csv ?


[text excised from methods section]

Ultimately there is no fully automated and foolproof test to determine
whether two nodes can be aligned - whether node A and node B are about
the same taxon. The information to do this is out there in the
literature and in databases on the Internet, but often it is
(understandably) missing from the source taxonomies.

The heuristics do not work in all cases.  Sometimes more than one
candidate passes all heuristics, in which case the node is left
unaligned.

### Collisions

[move to discussion?]
There are often false polysemies within a source taxonomy - that is, a
name belongs to more than one node in the source taxonomy, when on
inspection it is clear that this is a mistake in the source taxonomy, and there
is really only one taxon in question.  Example: _Aricidea rubra_
occurs twice in WoRMS, but the two nodes are very close in the
taxonomy and one of them appears to be a duplication.

If the union taxonomy has an appropriate node with that name, then
multiple source taxonomy nodes can match it, the collision will be
allowed, and the polysemy will go away.  However, if the union
taxonomy has no such node, both source nodes will end up being copied
into the union.  This is an error in the method which needs to be
fixed.

### Rationale for various parts of the method

(Explaining the perverted lineage rule)

  There are several factors that
make it tricky to turn this truism into an actionable rule.

* At the time that alignment is being done, we do not know the
  alignments of the ancestors, so we cannot compare ancestors very well.
  We use ancestor name as a proxy for ancestor identity.
* Sometimes having ancestors of the same name is not informative, as
  with species that are true polysemies, which have ancestors (genera)
  that are also true polysemies.  Ancestors whose names are string
  prefixes of the given taxon's name are skipped over.
* It is not enough that *some* ancestor (or ancestor name) is shared,
  since every pair of taxa share some ancestor (name).  We need to
  restrict the assessment to near ancestors.

[move to discusion]
Broadening the search beyond the 'quasiparent' of both nodes is
necessary because different taxonomies have different resolution: in
one a family might be divided into subfamilies, where in the other it
is not.  But by ensuring that one of the two nodes being compared is a
quasiparent, we avoid vacuous positives due to both being descendants
of 'life'.


[move to discusion] This heuristic [overlapping membership] has both
false negatives (taxa that should be combined but aren't) and false
positives (cases where merging taxa does not lead to the best
results).


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
somewhere (intro?)]

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

[Mention CoL]

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
* It would be good to find an alternative to the barrier taxonomy.  One thing
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
