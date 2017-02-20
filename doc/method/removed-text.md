## *NMF suggestion on how to explain all this*

1. Is it possible to assume an ideal case where merging two or more OTT input taxonomies requires no or only very minimal conflict/noise/ambiguity resolution? And the result is almost unambiguously correct? If so, perhaps you start your "assembly process" description by imaging/introducing such a case, and your core pipeline in relation to it. That is then out of the way - a scenario that OTT can handle well and easily.

   [JAR: I think this is now handled by 'assembly overview' below.  Maybe the examples are too simple? Let me know.]

2. Complications, 1 - those complications that through various profound or pragmatic solution you can address to a fairly large degree of satisfaction. Outcome -- still rather sound OTT, but drawing now on a full scope of things you've added because you've had to given case 1. was not what the input looked like.

   [JAR: I think this is what the alignment and merge sections are about.]

3. Complications, 2 - issues that you either handle not to your own satisfaction, or simply cannot handle at all.

   [JAR: yes, but this belongs in the discussion section.]

I guess I am suggesting this because 1 & 2 give you an opportunity to shine first, and somewhat conclusively, for a significant subset of the input trees. At least for the purpose of mounting the narrative. Clearly any complete OTT assembly job will encounter everything. But you may not have to write such that you directly follow what I assume may be real -- every input taxonomy has instances 1, 2, 3 represented to varying degrees, or they arise as the OTT grows. Instead you could pretend that some input taxonomies are clean (1), individually and jointly. Or clean enough (2) - because of your work. And only 3 is the tough stuff - but tough for anybody, etc.

So, I wonder what would happen if you did this kind of thing. "For the sake of making this assembly process accessible to a wide readership, we first illustrate the entire pipeline when acting on two or more input taxonomies that are highly internally consistent, and also pose minimal conflict among them. Here the assembly works well from A to Z, as we show and exemplify.

"A second category are complications that occur frequently but for which we have developed adequate diagnosis and repair/resolution mechanisms. We show how we do this, and also show what else could be done for even better performance".

"A third category contains lingering challenges that point to future solution analysis/development needs. And we suggest ..."

(and of course you'd say that in reality, every input may be a mix of 1-3)


### Alignment heuristics

#### **Separation**: Separate taxa if contained in disjoint separation taxa

If taxa A and B belong to taxa C and D (respectively), and C and D are
known to be disjoint, then it follows that A and B are distinct.  For
example, land plants and rhodophytes are disjoint, so if NCBI says its

Separating plants from rhodophytes
resembles the use of nomenclatural codes to separate (hemi)homonyms,
but the codes are not fine grained enough to capture distinctions that
arise in practice.  For example, there are many [how many? dozens?
hundreds?] of fungus/plant homonyms, even though the two groups are
governed by the same nomenclatural code.

[NMF: Check [here](https://doi.org/10.3897/BDJ.4.e8080) for harder data (names
management sections and refs. therein).]

Some cases of apparent homonymy might be differences of scientific opinion
concerning whether or not a taxon possesses a diagnostic apomorphy, or
belongs phylogenetically in some designated clade (the MRCA of some
other taxa).  Different placement of a name in two source taxonomies
does not necessarily mean that the name denotes different taxa in the
two taxonomies.

The separation heuristic used here works as follows.  We establish a
set of separation taxa, containing about 25 higher taxa (Bacteria, Fungi,
Metazoa, etc.).  Before the main alignment process starts, every
source taxonomy is aligned - manually, if necessary - to the separation
taxonomy.  (Usually this initial mini-alignment is by simply by name,
although there are a few troublesome cases, such as Bacteria, where
higher taxon names are homonyms.)  If
taxa A and B with the same name N are in separation taxa C and D, and C and D
are disjoint in the separation taxonomy, then A and B are taken to be distinct.
The heuristic does not apply if C is an ancestor of D (or vice versa); see below.

[JAR in response to NMF: The separation taxonomy is not just the
top of the tree; it omits many intermediate layers (ranks) and only
includes big, well-known taxa. E.g. Opisthokonta is omitted from the separation taxonomy
because so
few sources have it, even though there are separation nodes both above
and below it. A separation taxon must be "found" in at least two source taxonomies
in order to be useful for the purpose of aligning and dividing up the namespace
and preventing animals from being plants (etc.).

JAR continuing in response to NMF:
The problem is not placing everything; that's not hard. The only purpose of
the separation taxon is to prevent incorrect
collapse of what ought to be homonyms. If you have record A with name Mus
bos, and record B with name Mus bos, then you generally want them to be
unified if they're both chordates, but if one is a chordate and the other
is a mollusc, you'd rather they not unify.

It's hard to find real examples of species-level homonyms where one
can be sure it's not an artifact or mistake, but one of them (from my
homonym list) is probably Porella capensis. OTT has about 180 homonyms at the
species level, many of which look like mistakes (since sometimes two
or three have the same genus name). Most are plant/animal or
plant/fungus or animal/fungus, but some cases, like Ctenella aurantia,
have distinct records within the same code (Cnidaria / Ctenophora in
this case). I can't analyze every one, so we need to err on the side
of creating redundant records, rather than unifying, which would cause
higher taxa from the lower priority taxonomy to be "broken".]

#### Disparate ranks implies different taxa

We assume that a taxon with rank above the level of genus (family,
class, etc.) cannot be the same as a taxon with rank genus or below
(tribe, species, etc.).  A candidate that matches the source node in
this regard will be preferred to one that doesn't.  (That is, in
regard to whether it is genus or below; not in regard to its
particular rank.)

For example, for


#### Prefer taxa with shared lineage

A taxon that's in the "same place" in the taxonomy as a source is
preferred to one that's not; for example we would rather match a
weasel to a weasel than to a fish.

#### Separate taxa that have incompatible membership

[This section needs to be rewritten!  This heuristic now makes use of
aligned tips, rather than names.  And I believe it's a preference, not
a separation.]

For each source or workspace node A, we define its 'membership proxy' to
be the set of aligned nodes under it that do not have any aligned node
as a descendant (that is, as aligned nodes, they are tip-like).

If A and B both have nonempty membership proxies, and the proxies are
disjoint (supposing one considers aligned nodes to be unified), then
we consider A and B to be incompatible, and prevent a match between
them.

#### Prefer same separation taxon

There are many cases (about 4,000? will need to instrument and re-run
to count) where A's nearest enclosing separation taxon (say, C) is properly contained in B's
(say, D) or vice versa.  A and B are therefore not
separated by the separation heuristic.  It is not clear what
to do in these cases.  In many situations the taxon in question is
unplaced in the source (e.g. is in Eukaryota but not in Metazoa) and
ought to be matched with a placed taxon in the workspace (in both
Eukaryota and Metazoa).  In OTT 2.9, [??  figure out what happens -
not sure], but the number of affected names is quite high, so many
false homonyms are created.  Example: the separation taxonomy does not
separate _Brightonia_ the mollusc (from IRMNG) from _Brightonia_ the
echinoderm (from higher priority WoRMS), because echinoderms is not a
separation taxon, so [whatever happens].  [example no good in OTT
2.11 - get another.]  [need example going the other way.]

#### Prefer matches not involving synonyms

B is preferred to other candidates if its primary name is the same as
A's.

[NMF: What you do here is follow a general pattern of introducing a
particular workflow step, then say what may go wrong, then try to
discuss that. And this repeats for each paragraph / workflow step. I
will suggest a different arrangement.. [SEE BELOW]]




### different, older merge text

Following alignment, taxa from the source taxonomy are merged into the
workspace.  This is performed via bottom-up traversal of the
source.  A parameter 'sink' is passed down to recursive calls, and is
simply the nearest (most tipward) alignment target seen so far on the descent.
It is called 'sink' because it's the node to which orphan nodes will
be attached when a source taxon is dropped due to inconsistency with
the workspace.

The following cases arise during the merge process:

 * Source taxonomy tip:
     * If the source node is matched to a workspace node,
       there is nothing to do - the taxon is already present.
     * If it's ambiguous - could equally match
       more than one workspace node, even after all alignment heuristics come
       to bear - then there is also nothing to do; it is effectively
       blocked and ignored.
     * If unmatched and not ambiguous, then create a corresponding tip node in the workspace, to be
       attached higher up in the recursion.  The source tip is then matched to the new
       tip.
 * Source taxonomy internal node: the source node's children have already been
   processed (recursively), and are all matched to targets in the
   workspace (or, occasionally, blocked, see above).  The targets are either
   'old' (they were already in the workspace before the merge started) or
   'new' (copied from source to workspace during this round of the merge
   process).  One of the
   following rules then applies (assuming in each case that none of
   the previous rules apply):

     * Matched internal node: Attach any new targets to the internal
       node's alignment target.

     * Graft: all targets are new.  Create a new workspace node, and attach
       the new targets to it.

     * Inconsistent: if the old target nodes do not all have the same parent,
       attach the new target nodes to the sink, and flag them as 'unplaced'.

     * Refinement: if every child of the sink (in the workspace)
       is the match of some source node, we say the source
       node 'refines' the developing taxonomy in the workspace.
       Create a new workspace node to
       match the source node, and attach both old and new targets to it.

     * Merge: some child of the sink is _not_ the match of any source
       node.  Attach the new targets to the common parent of the old
       targets, discarding the source internal node.

The actual logic is more complicated than this due to the need to
properly handle unplaced (incertae sedis) taxa.  Generally speaking,
unplaced taxa are ignored in the calculations of inconsistency and
refinement.  The source taxonomy might provide a better position for
an unplaced taxon (more resolved, or resolved), or not.  Unplaced taxa
should not inhibit the application of any rule, but they shouldn't get
lost during merge, either.


There is a step to mark as extinct those taxa whose only source
taxonomy is GBIF and that come to GBIF via PaleoDB [reference].  This
is a heuristic, as PaleoDB can (rarely) contain extant taxa, but the
alternative is failing to recognize a much larger number of taxa as
extinct.


## foo




The choice and ranking of input taxonomies are driven by the three
requirements listed in the introduction: 1) OTU coverage (mapping OTUs
from phylogenies to taxa in the taxonomy); 2) a phylogenetically-informed
backbone hierarchy; and 3) taxonomic coverage
of the tree of life beyond the OTUs present in the input trees.

As an open science project, Open Tree of Life only uses information
sources that are not subject to terms of use (data use agreement).
This policy decision imposes an additional requirement on the
taxonomy, ruling out some sources that would otherwise be a good fit
for the project.


Details of sources: [in what order? the rank order is in the table,
use rank order or something different?  historical? by number of
taxa?]

**NCBI taxonomy**  
The first requirement of the reference taxonomy is to align OTUs across
phylogenetic studies.  This need is largely met by using the NCBI
taxonomy, since (1) most modern phylogenetic studies are molecular,
(2) publishers require molecular sequences used in studies to be
deposited in GenBank, and (3) every GenBank deposit is annotated with
a taxon in the NCBI taxonomy.  NCBI taxonomy also tends to be more
phylogenetically informed than other taxonomies (see Results, below),
which makes it a good backbone classification for Open Tree's purposes. The NCBI
taxonomy therefore forms the nucleus of OTT.


**GBIF backbone taxonomy**  
The GBIF backbone taxonomy provides good taxonomic coverage - about 2.5
million species records.  The GBIF backbone is an automated
synthesis drawing from 54 sources (including Catalog of Life, IRMNG,
and Index Fungorum).  Like the NCBI taxonomy, it has ongoing institutional
support for maintenance and growth, and provides access without
agreement to terms of use, making it a good choice for Open Tree.

SILVA
The classifications of prokaryotes and unicellular eukaryotes in NCBI and GBIF are
not consistent with current phylogenetic hypotheses. We therefore imported the
SILVA taxonomy, which is a curated classification based on phylogenetic analysis.


**Sch&auml;ferhoff 2010 (Lamiales)**  
Open Tree curators provided a taxonomy of the order Lamiales based
on a recent publication.


**Index Fungorum (Fungi)**  
We incorporated Index Fungorum in order to improve the coverage and
classification of Fungi. We obtained database dumps of Index Fungorum
around [date] (noting that the version of IF in in GBIF was [XXX]).


**Hibbett 2007 (Fungi)**  
The OpenTree Fungi curators provided an order-level
higher taxonomy of Fungi.  We gave this
taxonomy higher priority than other sources of fungal data. This taxonomy
is based on [ref Hibbett] but adds revisions from the literature.


**WoRMS (Decapoda)**  
We incorporated WoRMS in order to improve classification and coverage of
decapods.  We obtained the WoRMS taxonomy
via its Web API around [date].

[organizational note: basic descriptive information on sources is not
for the results section, but an assessment of the inputs to the
method, thus belongs under 'materials'.  some extended discussion
might go in the discussion section.]

[I'm trying to decide what to do about this section.  As a matter of
transparency, it is necessary to say certain things about how some of
the sources were processed.  But not every source has something
interesting to say about it, and extended discussion does not belong
in a methods section.]

[Originally I had one paragraph per source, in the order in which they
occur in the table.  Then, I changed this to logical order, roughly
that in which the sources were added to the OTT mix over the source of
the project.  Now I'm thinking it should just be a set of notes.]


*Known synonyms:* Each taxonomy comes with a set of synonyms, or extra
name-strings that apply to nodes, in addition to the 'primary'
name-string (in most cases, what the source taxonomy takes to be the
accepted name-string).  It is important to make use of these in
alignments, since otherwise a single taxon will have two nodes (taxon
records) under different name-strings.


For example, if source node A has synonym name-string C, and workspace
node B also has synonym name-string C, then B is a candidate for being
an alignment target for A.


**WORK IN PROGRESS - new text followed by old text**

Following are a few of the things that can go wrong during alignment.

*Multiple candidates:* For a given node in S', there may be multiple
nodes in S with the same name-string, making determination of the
correct mapping unclear.

*Candidate is wrong:* For a given node n in S', it might be that the
node (or nodes) in S with n's name-string would be an incorrect match.

Example: the unique node with name-string _Buchnera_ in the
Lamiales taxonomy is a plant, while the unique node with name-string
_Buchnera_ in SILVA is a bacteria.  [a species example would be
better...]

    (Example: _Diplura_, spider genus in GBIF, does not match
    candidate Diplura, hexapod order from NCBI.)


## Evaluating the product

[I think this section does not make a contribution and should be flushed]
[KC: agreed]

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


# From discussion

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

* separation woes: Annelida/Myzostomida, Fungi/Microsporidia
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
There are often false homonyms within a source taxonomy - that is, a
name belongs to more than one node in the source taxonomy, when on
manual inspection it is clear that this is a mistake in the source taxonomy, and there
is really only one taxon in question.  Example: _Aricidea rubra_
occurs twice in WoRMS, but the two nodes are very close in the
taxonomy and one of them appears to be a duplication.

If the workspace has an appropriate node with that name, then
multiple source taxonomy nodes can match it, the collision will be
allowed, and the homonym will go away.  However, if the workspace
has no such node, both source nodes will end up being copied
into the workspace.  This is an error in the method which needs to be
fixed.

### Rationale for various parts of the method

(Explaining the perverted lineage rule)

  There are several factors that
make it tricky to turn this truism into an actionable rule.

* At the time that alignment is being done, we do not know the
  alignments of the ancestors, so we cannot compare ancestors very well.
  We use ancestor name as a proxy for ancestor identity.
* Sometimes having ancestors of the same name is not informative, as
  with species that are true homonyms, which have ancestors (genera)
  that are also true homonyms.  Ancestors whose names are string
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

[from method section intro]
The conventional approach to meeting the requirements stated above
would have been to create a database, copy the first taxonomy into it,
then somehow merge the second taxonomy into that, repeating for
further sources if necessary.  However, it is not clear how to meet
the the ongoing update requirement under this approach.  As the source
taxonomies change, we would like for the combined taxonomy to contain
only information derived from the latest versions of the sources, no
residual information from previous versions.  Many changes to the
sources are corrections, and we do not want to hang on to or even be
influenced by information known to be incorrect.  Properly updating a
database that is populated with old information is something we don't
know how to do.

  There are additional benefits [to an automated tool] as well, such as the ability to
add additional sources relatively easily, and to use the tool for
other purposes.

[Priority judgments] are sometimes made
in ignorance of the quality of the sources, since we do not have the
resources to evaluate the quality of every source in detail.

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

The GBIF backbone is the closest and best point of comparison.  But
comparing to GBIF is difficult because it's not documented very well -
just two blog posts and the source code.  Infrastructure work such as
the GBIF backbone is generally viewed as a means to some other end and
not a research output.  This is understandable given the GBIF's
mission and the pressures it faces.

* maybe discuss global names architecture, bionames, bioguid ... GNA
  doesn't deal with synonyms (yet), for example; does bionames?  what about
  misspellings?


## Potential improvements / future work

* Fishbase, world bird names, plant list
* It would be good to find an alternative to the separation taxonomy.  One thing
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


---- FROM RESULTS 2017-01-24 ----

For possible discussion:

 * Number of taxa suppressed for supertree synthesis purposes: 696041

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



2017-02-18

Removing this case because I cannot find an example of it!

1. ((a,b)x,(c,d)y)z + ((a,c,e)u,(b,d)v)z = ((a,b)x,(c,d)y,?e)

   If sibling nodes in S' have different parents in S, we throw away their parent node.
   In the example, a and c, which are siblings under u, have different parents x and y
   in S, and similarly for b and d.
   Any unaligned children (e in this case)
   are flagged _incertae sedis_ in the attachment point, which in this case is z.

