# Changes to OTT (taxonomy and smasher) going from OTT 2.9 (13 October 2015) through 1 July 2016

* Changes to address open issues
* Changes to address errors and warnings during assembly
* Updating NCBI Taxonomy to newer version from NCBI
* NCBI processing changes
* IRMNG processing overhaul
* Change to alignment rules
* Other taxonomy changes
* Code changes to promote stability
* Changes to smasher that don't affect the taxonomy
    * SILVA processing overhaul
    * Changes to help out with taxonomy troubleshooting
    * Changes to code structure
    * Troubleshooting tools
    * Changes to data structures and the way the code is written
    * Maintenance, administration, documentation
* Side projects
    * Conflict service
    * Taxon counts
    * Misc
    * Additions

Maybe eliminate use of first person


## Changes to address open issues

The patches that I've made are all in the files taxonomies.py and
assemble-ott.py, the former when they are applied to a source taxonomy
(preferred) and the latter when they're applied to OTT, or when they
have to do with taxon alignment.

### Patches

Dealt with Yan Wong's comment on the google group (2015-10-10) about
Uncia by renaming Uncia uncia to Panthera uncia and putting Uncia in
Panthera.  This is an NCBI patch, so I put it in the NCBI-specific
part of taxonomies.py (could have gone in assemble-ott.py).
https://github.com/OpenTreeOfLife/feedback/issues/291

Dealt with Marc Jones's issue 2015-10-10 on the google group by
renaming Sphenodontia to Rhynchocephalia (taxonomies.py).
https://github.com/OpenTreeOfLife/feedback/issues/292

Fixed a typo in the Hibbett 2007 taxonomy Newick string from Romina
('Phyllum' instead of 'Phylum'), by directly editing the file
(tree.tre).  https://github.com/OpenTreeOfLife/feedback/issues/150

There was a problem with Choanomonada / Choanoflagellida, which Yan
had pointed out.  There were two of them in SILVA, one a descendant
genus of the other.  When the time came to match taxa from other
taxonomies to the two, the wrong choice got made.  The problem has
been fixed in NCBI, and propagated to SILVA by the NCBI-to-SILVA
mapping feature described below.  The offending taxon's new name is
'Choanoflagellate-like sp. ribosomal RNA small subunit (16S
rRNA-like)'.  I also renamed SILVA Choanomonada to the more traditional 
Choanoflagellida that all the other sources use, and deleted the 
bogus NCBI synonymy for Choanozoa.
https://github.com/OpenTreeOfLife/feedback/issues/45

Semionotiformes was erroneously marked extinct; it should be extant
now.  The fix was to merge the taxa Semionotiformes and
Lepisosteiformes which are gien as distinct in IRMNG (one extinct, the
other not) but unified in NCBI.  I'm not sure this is the best
solution but it's the best I could do without a deep understanding of
the taxonomy of those groups.
https://github.com/OpenTreeOfLife/feedback/issues/241

Added Hylobates alibarbis as a misspelling (synonym) for Hylobates alibarbis.
https://github.com/OpenTreeOfLife/feedback/issues/184

Removed Osteichthyes as a synonym for Actinopterygii (in WoRMS), and
created a synonym Osteichthyes for Euteleostomi (in NCBI).
https://github.com/OpenTreeOfLife/feedback/issues/194

Fixed genus Galeocerdo by a combination of an explicit patch (deleting
troublesome NCBI record 'Galeocerdo cf. cuvier GJPN-2012') and
general processing change (eliminating invalid taxa from IRMNG).
https://github.com/OpenTreeOfLife/feedback/issues/138

Dealt with GBIF duplicate for Réunion ibis by merging Raphus
solitarius into Threskiornis solitarius, and made it extinct.
https://github.com/OpenTreeOfLife/feedback/issues/186

Chelomophrynus and Shomronella (in Anura) are extinct.
https://github.com/OpenTreeOfLife/feedback/issues/282
https://github.com/OpenTreeOfLife/feedback/issues/283

Gillocystis is now (sadly) extinct.
https://github.com/OpenTreeOfLife/feedback/issues/86

Theretairus, Diphydontosaurus, and Leptosaurus (in Sphenodontia) are extinct.
https://github.com/OpenTreeOfLife/feedback/issues/165

Nesophontidae in Insectivora is extinct
https://github.com/OpenTreeOfLife/feedback/issues/159

Cryptobranchus matthewi in Amphibia is extinct
https://github.com/OpenTreeOfLife/feedback/issues/135

Hemitrypus in Amphibia is extinct
https://github.com/OpenTreeOfLife/feedback/issues/134

Cordicephalus in Amphibia is extinct
https://github.com/OpenTreeOfLife/feedback/issues/133

Put Cordicephalus in Pipoidea.
https://github.com/OpenTreeOfLife/feedback/issues/133

I fixed a problem with Notochelys (in Cheloniidae) extinctness
explicitly, but now it is fixed generically by the IRMNG processing change.
https://github.com/OpenTreeOfLife/feedback/issues/285

Fixed the taxa-with-multiple-NCBI issues, see SILVA to NCBI below
(this also required a temporary hack in alignment to erase them from
OTT 2.9 before assigning ids)
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/36

Repaired some of the flagging logic around incertae-sedis, not-otu,
etc..  This had to do with the reviewing the way some flags are
applied to NCBI and not to SILVA (e.g. 'uncultured' is considered a
taxon in SILVA but not in NCBI).  This was to address
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/175

Pseudostomum is now in Playhelminthes, as appropriate.  There is no
patch for this; it must have been fixed by one of the systematic
changes that I made.
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/198

Similarly, Nanoarchaeum is no longer incertae sedis, but I don't know
specifically how it got fixed.
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/199

Crenarchaeota pruned from SILVA.  (my notes only say "What's going on
with Crenarchaeota?" from June 2014.)
https://github.com/OpenTreeOfLife/feedback/issues/189

Opisthokonta has no rank
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/195

Cestracion is redundant
https://github.com/OpenTreeOfLife/feedback/issues/127

Attempt at straightening out the Equisetopsida situation (NCBI error)
https://github.com/OpenTreeOfLife/feedback/issues/281

Attempt to deal with Coniferales / Pinidae situation (NCBI error)
https://github.com/OpenTreeOfLife/feedback/issues/278

Removed bogus Acomys cahirinus / Acomys airensis synonymy
https://github.com/OpenTreeOfLife/feedback/issues/248

Added Selachimorpha as synonym for sharks
https://github.com/OpenTreeOfLife/feedback/issues/152

Deal with spelling problem in Aotus
https://github.com/OpenTreeOfLife/feedback/issues/142

Name of Lepilemur tymerlachsonorum
https://github.com/OpenTreeOfLife/feedback/issues/144

Gryphodobatis is extinct
https://github.com/OpenTreeOfLife/feedback/issues/123

Plectophanes altus = Plectophanes alta
https://github.com/OpenTreeOfLife/feedback/issues/167

### Extinctness from PaleoDB

(Not sure, this may have been done for OTT 2.9.  Need to find issues.)
The list of ids of GBIF records that GBIF says originate from PaleoDB
is written to a file as a side effect of GBIF processing.  This list
is then used in an assembly step to annotate taxa as being extinct.
This hack addresses numerous issues regarding taxa that are not
flagged extinct but should be.

I changed this PaleoDB rule to operate only on taxa whose first import
was from GBIF. I don't remember the reasoning here.  Maybe we will now
have false positive extant taxa (from other taxonomies) that were true
negatives before this change.  Maybe I need to review this.  In any
case https://github.com/OpenTreeOfLife/feedback/issues/43
(Lepisosteiformes, Amiiformes) seems to be better now, although this
should be checked.
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/176

For one of the feedback issues (which?) that complained about some
taxon being extant, I tracked the problem to inappropriate use of a
new utility that declares a taxon extant, removing the exinct flag
from all ancestors.  I think I've fixed this (commit 033cf36).

### Bad homonyms via SILVA

Regarding an annoying set of inappropriate taxon names in SILVA e.g. a bacterium called
Caenorhabditis elegans
(https://github.com/OpenTreeOfLife/reference-taxonomy/issues/201, https://github.com/OpenTreeOfLife/reference-taxonomy/issues/167, https://github.com/OpenTreeOfLife/reference-taxonomy/issues/100), I
wrote a script to compare SILVA's placement of certain taxa with
NCBI's.  The script shows the names of ancestors of the two taxa that
have the same parent (i.e. they are children of the mrca of the two
taxa).  Reading the output makes obvious which names are almost
certainly wrong; they're the ones that have Bacteria and Eukaryota as
the divergence pair (or either of those and Archaea).  I went through
this list and added the offenders to a kill list (silva_bad_names in
taxonomies.py).  There are 34 of these.  I hope this fix gets rid of
most of the problems.  I know of one remaining, which is Solanum
lycopersicum (tomato), which I'll need to work on later (technical
debt).  That one is annoying because there are 23 different bacterial
clusters that are assigned to that NCBI plant taxon.  I may just have
to add all of them to this kill list.


## Changes to address errors and warnings during assembly

Getting rid of assembly time errors was a request from Ben Redelings
on an Open Tree hangout recently (June 29 or 22).  To see what these
errors were, look at the 2.9 assembly transcript (https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/log/2.9draft12.log).

Errors are indicated in the transcript with two asterisks.  I demoted
certain kinds of error to warnings, and this of course helped a lot.

Perhaps in the future I should create issues for things like this; but
generally I notice something and fix it right away, and/or nobody
else would care, so it hardly seems worthwhile.


### Fungal taxonomy

Many of the orders from the Hibbett fungal orders taxonomy were empty,
because the taxonomy Romina gave me went only to order without saying
what belonged in each order, and many orders were new.  There was code
(in assemble_ott.py) to detect these empty orders and flag them.  I've been
gradually taking care of them, but it requires doing web searches and
finding the applicable taxonomic revision to find out which families
go in many of these new orders.  I think I've taken care of the last
of them.

'Stereopsidaceae' (from Romina's fungal taxonomy information) showed
up as missing in 2.9 assembly, so I researched it and found the fungal
families that belong in it.

Similarly, Loxosporales was flagged as empty, so I did some research
and added Loxospora to it.  A similar case was Hyaloraphidiaceae.

** Barren taxon from h2007 Hyaloraphidiales [fungus]
** Barren taxon from h2007 Stereopsidales
** Barren taxon from h2007 Sarrameanales

### Rank alignment

One persistent set of errors (or warnings) during assembly has
been four instances of rank inversion, i.e. where a family was a child of a
genus (or anything similar like subclass child of family and so on).

    ** Ranks out of order: (gbif:4865225+7 Chonetoidea) genus has child (gbif:3252414+30 Rugosochonetidae) family
    ** Ranks out of order: (gbif:2050173+1 Alydus calcaratus) species has child (gbif:3265607+5 Camptopus) genus
    ** Ranks out of order: (gbif:1010609+8 Limulus) genus has child (gbif:4649996+1 Paleolimulidae) family
    ** Ranks out of order: (gbif:4587706+4 Nuculoidea) genus has child (gbif:4587862+2 Nucularcidae) family

I made a change speculatively thinking it might help, but it didn't,
but in doing so I added diagnostics that found some minor problems,
which I fixed.  The change is a new taxon alignment criterion based on
ranks, as follows: a taxon at rank genus or below (species etc) will
not be matched with a taxon at rank family or above.  The criterion
did not make a difference in the above cases.  However, it created 2
or 3 necessary homonyms (previously false merges), which is good;
detected about 6 records from GBIF that needed to be deleted; and
detected a mistaken rank for an NCBI taxon (Vezdaeaceae is a family,
not a genus).

To help diagnose rank inversions, a warning is now generated whenever
the assertion of a parent/child relationship creates an inversion.

### Miscellaneous

(more detail?) I researched Acremonium and a few other names (and
their ids), because of inclusion test failures (id mismatches between
the test and the taxonomy).  I fixed some or all of these by explicit
alignment directives.  From the 2.9 assembly log:

    ** Acremonium is 5262599, not 5342243
    ** Crepidula is 24075, not 5394882
    ** Epiphloea=5342482 not under Lichinales
    ** Dischloridium is 946379, not 895423
    ** Leucocryptos=111139 not under Katablepharidophyta
    ** Gloeosporium is 176780, not 75019

I removed some rows from the Katz lab Chromista/Protista spreadsheet
that were leading to errors, and made a spelling correction
(Discorbidea -> Discorboidea) so that other rows could be preserved.

Removed three patches (Drepano-Hypnum and two similar to that) that
are now moot due to recent changes to GBIF processing; they no
longer occur in the processed version.
Relates to https://github.com/OpenTreeOfLife/reference-taxonomy/issues/1

Made a Pelecypoda patch more robust against disappearance of that
name.  I discovered this problem the hard way as an Python exception
during assembly.  I'm not sure why the problem eventually went away;
it's either gone from NCBI, or gone as a result of the source taxonomy
processing changes.

I detected (I don't remember how, must have been by looking at
deprecated.tsv or log.tsv or the assembly transcript) that Glaucophyta
and Glaucophyceae were being treated as separate taxa, when they are
actually the same (or one of them is monotypic).  So I added a patch
to merge them at the end of assembly.  (see also Rhodophyta change,
below.)

Pantoea ananatis in OTT 2.8 was renamed to Pantoea ananatis LMG 20103
in OTT 2.9, leading to an id-retired entry in deprecated.tsv.  I fixed
this as a one-off in 2.9, but the fix is now covered by the new NCBI
to SILVA mapping feature (see below).  But the old patch then
generated a warning, so I had to add tests to make sure the patch is
only applied when the old name exists.

I had a hard time dealing with a bogus 'Calothrix' homonym.  It looked
like a mistake to me so I renamed one of them "Calothrix (homonym)",
but probably more research is needed to figure out what this clade
(from SILVA) should be called.

To fix the error message "** No unique taxon found with name
Bostrychia in context Rhodophyceae" (from a patch) I added a synonymy
Rhodophyceae = Rhodophyta.  Later I also added this to the synonyms
list for the skeleton taxonomy, so this patch may be moot now.



## Updating NCBI Taxonomy to newer version from NCBI

I upgraded to the latest version of NCBI taxonomy using 'make
refresh-ncbi'.

In going over the taxonomy build with Karen, we saw in deprecated.tsv
that many ids were 'retired' i.e. no new taxon matching an old one.
On checking one or two in NCBI (looking up their NCBI id) the reason
turns out to be that NCBI has changed the taxon name without leaving a
synonym behind.  It would be possible to address these changes one at
a time by adding to the special assignments list (in assemble_ott.py),
but this would be time consuming and error prone.  To address the
problem automatically, there is now a last-ditch matching loop that
takes place right after automated taxon id assignment (i.e. id
carryover from the previous taxonomy version - 2.9 in this case - to
new draft).  It matches taxa based on their source ids e.g. ncbi:1234.
It seems to be pretty successful; most of the id-retired rows in
deprecated.tsv have gone away.

Repaired a few failed inclusion tests by setting taxon ids explicitly.
I don't know how the ids came to be changed, but it must have been due
to recent changes such as those to NCBI content or IRMNG processing
rules.

Removed Progenitohyus from the test file inclusions.csv, since it was
showing up as unknown.  This could have been a casualty either of the
NCBI update or of the IRMNG processing update.  (It was already hidden
as of 2.9.)  (inclusions.tsv in the reference-taxonomy repo is updated
from germinator/taxa/inclusions.tsv.  When we release 2.10 we can copy
it back to the germinator repo.)

Looks like I did something about the children of Bilateria but I don't
remember why - I think prior patches for one of Brian Drew's old
issues were failing because NCBI updated its taxonomy so that the
patches were no longer needed?.  (assemble_ott.py)

I don't know if the following relate to the NCBI update, or the IRMNG revision:
* 24c2f7e deal with numerous troublesome taxa, e.g. Protaspis
* I had to clean up some confusion over Marssonia vs. Marssonina; I
  forget how this came to my attention.  Marssonia = Gloeosporium.

Had to make the Chaetognatha patch more robust; it was fixed in OTT a long time ago, but I think has now been fixed in NCBI Taxonomy.
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/88

### NCBI-to-SILVA mapping

New logic for aligning NCBI to SILVA.  On re-running assembly after
refreshing NCBI Taxonomy, I noticed that many ids were 'retired',
which generally means the name was in the previous version, but
disappeared in the new version.  (Example: 
On investigating I found that some of
these were taxa from SILVA that no longer mapped to NCBI.  The problem
was that taxa are matched by name, not by a SILVA or NCBI identifier.
This works as long as a taxon has the same name in both.  SILVA
derives its names from NCBI, but if there is 'version skew' (our
version of NCBI Taxonomy is different from the one used for the SILVA
build) then smasher won't be able to see that they're the same.

To fix this, there is a special step in assembly to map NCBI to SILVA
using identifiers.  The mapping is accomplished in two steps.  A
script (which we already had) looks up all genbank ids occurring in
the SILVA taxonomy (and optionally elsewhere) in genbank, and gets
NCBI taxon ids for them.  A second script inverts this mapping and
discards ambiguous NCBI ids.  The NCBI/SILVA table is loaded when the
SILVA taxonomy is built and used to map between the two.  There are 85
cases where the mapping differs form what is inferred from other
information.

This features also repairs erroneous mismatches in SILVA that I had
addressed piecemeal previously, e.g. 'Arthrobacter Sp. PF2M5' (SILVA)
failing to match 'Arthrobacter sp. PF2M5' (NCBI).  After the general
fix, these patches were failing so that had to be modified to be more
defensive.

Other examples, initially fixed as one-offs and now done
automatically, are 'Vibrio sp. Ex25' to 'Vibrio antiquarius' and
'Phaeobacter inhibens DSM 17395' to 'Phaeobacter gallaeciensis DSM
17395 = CIP 105210'.

This change is related to Yan's (and others') complaint about multiple
taxa having the same NCBI id.  (need issue #)  That is fixed now.  (In detail:
formerly, if a source taxon itself had sources, those would become
additional sources of the OTT taxon.  NCBI taxa were listed as taxa of
SILVA taxa that mapped to different NCBI taxa, which created the
confusion.  Source-sources are no longer copied to OTT.)

## NCBI processing changes

In addition to updating NCBI taxonomy content, I changed the
processing logic for importing NCBI taxonomy.  I made a list of the
synonym types in NCBI and went over them to decide which types we
should keep and which not.  E.g. "in-part" is not a good type; it says
the synonym is a paraphyletic taxon *larger* than the taxon (Monera is
an 'in-part' synonym of Bacteria).  I think there's an open issue
about common names.  (tbd: diff old & new process_ncbi scripts and see
if this is the only change)


## IRMNG processing overhaul

(there is an issue for this, need to find.  the change fixes feedback 74, Lymnea)

The IRMNG import has been a bit of a mess; e.g. it has (or had)
a lot of duplications, and many of its taxa were implicated in homonym
confusiong.  And we've been getting complaints about 'junk' names
being included in the taxonomy (need to find issues).  So I decided to
take a look.  It turns out there's lots of useful information in the
IRMNG dump, in particular the TAXONOMICSTATUS and NOMENCLATURALSTATUS
fields.  I studied those and made some revisions, rewriting parts of
the IRMNG processing script (it's not that big).  E.g. some names are
marked 'nomen nudum' and we don't really need those.  This change
removes about 350,000 taxa, and makes all the IRMNG problems I know
about go away.  A few of these were used as OTUs in phylesystem, and
those have been grandfathered.

One problem that this fixes is duplicated taxa from IRMNG.  In OTT
2.9, there are about 100 taxon records with the name 'Hippopsis
lemniscata'.  Now there is only one.  In a number of similar cases we
end up with either one taxon or none at all.  By eliminating
duplicates, we help ensure that the extinct/extant flag is set
correctly, because presemuably the 'accepted' record is better curated
than all of the 'nomen nudum' and other illegitimate records.  100-way
duplicates generate warnings during assembly, which is how I knew that
this problem got fixed.

Several patches had to be modified to prevent errors in running the
patches, e.g. being more robust to the disappearance of genus
Tipuloidea in Hemiptera (a dubious homonym of craneflies), Neopithecus
disappearing from primates, an Ophiurina homonym, Pelecypoda in
Bivalvia, a genus Diptera somewhere, a bad Steinia homonym,
Ctenophora in Chelicerata.

More: Epiphloea in Halymeniaceae bad homonym, Epiphloea in Lichinales
gone away.

As part of this change I removed the IRMNG pseudo-taxon 'Unaccepted' -
I figured nothing good could come out of such a grouping.

See https://github.com/OpenTreeOfLife/feedback/issues/187 for an issue
that was fixed by this change.

See e.g. https://github.com/OpenTreeOfLife/feedback/issues/288 (which
isn't fixed yet because I don't transfer this information over to
GBIF!)


## Change to alignment rules

This is somewhat involved... unfortunately it is not related to an
issue, but is just something I've been concerned about for a long
period of time and had wanted to fix for a long time.

I had noticed that there were many names that showed up with two nodes
in the taxonomy, when really there was only one taxon at issue.  This
happens when the taxon appears in very different places in two source
taxonomies.  For example, if a taxon is Eukaryota incertae sedis on
source A, but in a particular place in Metazoa in source B, then we
end up with two taxon nodes in the final output.  This situation is a
"false negative" because the system says no, they don't match, when in
fact they should.

The reason for this has to do with the way that true homonyms are
normally separated, e.g. a plant and animal with the same name.  To do
this there is a small set (about 20) of 'barrier nodes,' and these are
located each source taxonomy and in the union taxonomy
(OTT-in-progress) prior to all other alignments.  If taxon A is under
barrier node B in the union, and taxon A' with the same name is not
under B in the source, then A and A' are considered distinct taxa and
are not matched.  (Similarly vice versa.)  This arrangement will
result in the erroneous behavior described above.

This affects not just incertae sedis taxa, but also situations where
the classifications are very different, as e.g. NCBI and GBIF, or
where taxa are just in the wrong place in the tree.

To fix this, the separation rule (in B vs. not in B) is relaxed a
bit.  The barrier nodes are arranged hierarchically into a 'skeleton
taxonomy', and the separation rule is modified so that A and A' are
considered distinct only if their nearest enclosing barrier nodes B
and B' are disjoint in the skeleton taxonomy, i.e. neither is an
ancestor of the other.

While this change turns many false negatives into true positives, it
also turns a few true negatives into false positives.  There was a
handful of these, which I had to fix manually.  They were detected
using inclusion tests that I had set up a while back, i.e. I knew
these were challenging homonyms before.

The most difficult false positives to deal with were for the name
'Ctenophora'.  There are three taxa named Ctenophora (formerly four,
before I changed the IRMNG processing script for other reasons): comb
jellies, a genus of diatoms, and a genus of craneflies.  Diatoms have
nearest barrier node Eukaryota; comb jellies, Metazoa, which is in
Eukaryota; and craneflies, Diptera, which is in Metazoa.  To keep
these homonymous taxa separate requires override mappings of these
taxa in their source taxonomies - but the taxa to which they must map
don't exist yet at the point in processing.

(I feel like pointing out that changes to the alignment logic to make
it more heavily reliant on taxon membership and less heavily reliant
on names probably would have solved this problem without manual
intervention.)

I think Trichosporon was similar; I had straightened this out for 2.9
but it broke again after this change.

The solution I came up with, after trying various other approaches,
was to 'manually' create these nodes in the union taxonomy at the
outset, before loading any of the source taxonomies, so that they
would be available when needed.  The taxa are created using a new
'establish' utility in assemble.ott.py.  Then after each source
taxonomy is loaded but before automatic mapping to the union, all of
its Ctenophora taxa are connected explicity to the correct union taxon.

Other taxa affected (former homonyms that got merged) are Protaspis in
Cercozoa, Coscinodiscus in Stramenopiles, Rhizaria in Brachiopoda,
Campanella in SAR, Neoptera in Pterygota, Hessea in Fungi.  Many of
these were flagged by the inclusion tests.  I removed the taxa among
these that did not disappear with the IRMNG upgrade.  It might be
better to (re-)create these homonyms, but for the most part these are
obscure or dubious taxa and I couldn't justify spending much time on
them.

Some others: Euxinia (flatworm + amphipod), Campanella (alveolata +
fungus), Diphylleia ('apusozoan' + eudicot).  I have recently learned
of the Hemihomonym Database and it would be interesting to compare the
set of hemihomonyms in OTT with those in the database.

'Heterolobosea' is a case of false homonyms that are now united.

These homonym-patch-related 2.9 warnings got fixed somehow (details TBD)

    ** No unique taxon found with name Epiphloea in context Halymeniaceae
    ** No unique taxon found with name Epiphloea in context Lichinales
    ** No unique taxon found with name Rhynchonelloidea in context Rhynchonellidae


## Other changes to the taxonomy

This section gives minor changes that are not responses to issues;
things I just noticed and fixed.

Asterinales and Asteriniales are synonymized (don't remember how detected).
This is a fungal order in the Hibbett 2007 taxonomy.

Smasher can now merge genera as well as species.  There are several
genera in our harvest of WoRMS that are duplicated, resulting in false
homonyms; the species in these genera are also duplicated.  To
compensate for this there is now logic to detect this situation and
merge the genera.

I scanned the list of all taxa with rank 'subgenus' and fixed a few
names, e.g. 'Acetobacter subgen. Acetobacter' => 'Acetobacter subgenus
Acetobacter', 'Festuca subg. Vulpia' => 'Vulpia', 'Ophion
(Platophion)' => 'Platophion'.  There is more to be done, e.g. fixing
some subgenus names of the form 'Plasmodium (X)'.

Added a cleanup pass applied for source taxonomies to set the rank as
appropriate if the name contains "subsp." or "var".

Merged Florideophycidae with Florideophyceae (in Rhodophyceae').
Found while debugging the homony Epiphloea.

Made Heterokonta (from WoRMS) be a synonym for Stramenopiles.  Noticed at some
point as an assembly warning "No Stramenopiles".

Pruned Viruses from WoRMS and IRMNG.  I'd actually like to get rid of them
altogether.

Some interaction between Blattoptera (from IRMNG) and Blattodea was
making Blattodea (cockroaches) extinct, but I don't remember how I
discovered this - perhaps I did a synthetic tree search for
cockroaches.  Blattoptera is extinct only, so I just pruned it.

I undid some OTT 2.9 logic for removing extinct flags from NCBI taxa,
overriding settings from IRMNG - basically, if a taxon is in NCBI, it
is assumed to be extant, under the assumption that IRMNG is enough
improved that the extinct flags from IRMNG are no longer needed.  I
think.  Hmm, I need to review this, maybe we still need it.

Hmm, what are these id assignments about?

    ('Cordana', 'Ascomycota', '946160'),
    ('Pseudofusarium', 'Ascomycota', '655794'),
    ('Marssonina', 'Dermateaceae', '372158'), # ncbi:324777
    ('Escherichia coli', 'Enterobacteriaceae', '474506'), # ncbi:562

Trichosporon / Hypochrea is a quagmire; I changed the way it is fixed
and hope it's better now.

Species fungorum puts WoRMS Trichosporon diddensiae in Candida.  At
some point in untangling Trichosporon I found that it was in WoRMS (a
fungus in WoRMS??), and found this error.

NCBI provides a file of NCBI taxon identifier merges, and this is now
used during alignment.  This new feature has the effect of 'resuing'
about 100 OTT ids occurring in phylesystem that otherwise would have
been deprecated due to a failure to recognize that NCBI had folded a
taxon into one that already had an OTT id.

Improved treatment of some SILVA parent/child homonyms:
* Changed name "Cyanobacteria sup." to "Cyanobacteria/Melainabacteria group"
* Changed name "Actinobacteria sup." to "Actinobacteraeota"
* Changed name "Acidobacteria sup." to "Acidobacteriia"

Restored the Index Fungorum genus "Acantharia" from IF because it
turns out to be needed (occurs in other taxonomies)

## Changes to smasher that don't affect the taxonomy

These changes help preserve our investment in curation effort (OTU
mapping) by improving the way alignment is done (across successive OTT
releases).  Taxa in a new release get their ids through alignment with
the old release, so any alignment failure means an id unnecessarily
'splits' and opportunities to match OTUs between studies are lost.

### Record-keeping for taxon merges

The forwards.tsv feature is relatively new.  This is a two-column file
in the distribution that lists ids for all taxa that have been merged
into other taxa, with the id of the target taxon.  This file is
important because OTT ids are stored in studies, and if a merged id
just goes away, that's bad, especially if there's a replacement
available.  Given forwards.tsv, a client can map old ids to new ids,
and keep those OTUs.

The way the forwards file is made went through a couple of stages of
development, and now seems to work relatively well, with full automation.

NCBI has the identical feature, and their merges are now imported when
the NCBI taxonomy is loaded.  This should help reduce OTT identifier
loss when we revise the taxonomy.

The forwards are carried over from previous versions of OTT, so we
don't lose information.

### Keeping ids

Sometimes an identifier change is reported while running the inclusion
tests (the tests contain ids).  That is, a taxon is preserved from the
previous OTT version, so the test passes, but it is given a new
identifier, which doesn't match the identifier given in the test.  I
don't know why this happens.  When this happens I try to figure out
what the "right" identifier is.  If there is a homonym split then this
may not be clear, but usually I can look at a few prior OTT versions
to see what id has been given to the taxon in the past.  But usually
it is OK to force the identifier to be its old value.  There is a list
of cases like this in assemble_ott.py.  By adding an entry to that
list, the assembly warning goes away and the id is preserved (which
helps preserve our curation investment).

There are several places where one refers to a taxon using a pair of
names, the name of the taxon and the name of either an ancestor or
descendant.  Something (like something in assembly output) must have
led me to want to fix a limitation of this, that it was not aware of
synonyms when looking for the ancestor or descendant.  So this is
fixed now.

The Taxon rename method now checks for a taxon of the same name among
the taxon's siblings, and generates a warning if one's already there.



## Changes that don't have a direct effect on taxonomy

Looks like I fixed a bug in one of the mrca methods; don't remember
why.


### SILVA processing overhaul

I don't think this affected the taxonomy, or if it did, it did so
in very minor ways.

 * The overhaul relates to Laura Katz's old request to expose microbial
   biodiversity (opentree#27), which I think about frequently.
   In the end this overhaul had very little effect, but the SILVA 
   processing code is
   cleaner now, and more flexible in case we want to start
   adding some or all of the clusters to the taxonomy.  The basic
   idea is that there is a choice as to whether to include all
   SILVA cluster, none, or some, and similarly whether to include
   all NCBI taxa that include clusters, or just some.
 * As a consequence of revising the SILVA script, the former
   approach of filtering out multicellular organisms early in SILVA
   processing led to some problem that I can't remember, so I
   changed the script so that all of SILVA is included in the
   taxonomy that ends up in tax/silva/.  They are then pruned out
   in taxonomies.py.  This approach gives us more flexibility,
   e.g. we might try to make use of those other SILVA taxa somehow.
 * Revised some of the SILVA patches to be more robust in case taxa
   disappear.  (There is an annoyance in the "version 2" patch
   system where you say taxon('x').rename('y') and that gets an
   exception if there is no taxon 'x'.  This is one of the things I
   want to fix in the next version of the patch system - the patch
   will fail gracefully and you'll just get a warning.)



### Changes to help out with taxonomy troubleshooting

Ah.  An important change introduced to support the ability to
determine the source of a synonym [issue #174; from Mark].  I had been
wanting to make this change anyhow, for other reasons.  The change is
to introduce new classes Node and Synonym, and make Taxon a sibling of
Synonym.  Every Synonym points to one Taxon; the children of a Taxon
remain Taxons.  There is a new field in a Taxon which is a list of its
Synonyms.  The taxonomy's name index now maps strings to sets of
Nodes, not sets of Taxons.
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/185

Part of this change is to be able to read source and type information
from a file, store them in a Synonym, and write them out to a file.

Also addressed in the process of doing this change:
types of synonym.  https://github.com/OpenTreeOfLife/reference-taxonomy/issues/113


### Changes to code structure

Many code changes for OTT 2.10 are directed at making the code more
readable and more maintainable.

As a cleanup requested during a group hangout ("please don't make
conflict analysis be a part of smasher") I split up the classes and
methods so that the utilities not related to taxonomy synthesis are in
their own package (org.opentreeoflife.taxa) while everything having to
do with synthesis is in the org.opentreeoflife.smasher package.
Conflict analysis uses functionality from the taxa package but not
smasher.

New classes created by extracting existing code from other classes:

 * The 'merge' phase of assembly is now its own class
   (MergeMachine).  Along with this change I reworked alignments, trying to put
   everything that has to do with alignment (e.g. the 'skeleton') in
   Alignment.java.

 * An "EventLogger" class for managing
   events that occur during synthesis.  Splitting this out makes the
   Taxonomy and UnionTaxonomy classes smaller.

 * I moved everything having to do with the "interim taxonomy file
   format" to its own class.  This change does not affect behavior, but
   makes the Taxonomy class less cluttered.

 * Similarly, anything to do with processing the old TSV-formatted patch
   files is now in its own class.

 * I moved some SKOS-related methods written by Dan Leehr to its own
   file, to get it out of the way.  It's not used.

Moved comments describing flags (from the design meeting with Cody) to
Flag.java, to make them easier to find...

### Troubleshooting tools

If I'm having trouble understanding the fate of a name (disappearance,
inappropriate identity or nonidentity) I add it to the
'names_of_interest' list (in assemble-ott.py), then re-run assembly.
If a name is in the list then a trace of the alignment process for
that name will be recorded in log.tsv.  (We can't just log all
alignment history since then the file becomes enormous and difficult
to search and read.)  I added a bunch of names to the list,
e.g. Epiphloea (a homonym that was sometimes inappropriately merged)
and Spinus barbatus (which was having its id retired, according to
deprecated.tsv, because "multiple-use" whose meaning I can't remember
off the top of my head - that's in dumpLog in UnionTaxonomy.java).

Whenever I'm trying to debug a name I take notes and put them in the
file doc/name-research.com.  This time around I was having a lot of
trouble with Ctenophora, a 4-way homonym (or probably always had had
trouble; just wanted to straighten in out now).  Another hard case is
Uronema, where not only is the genus polyemous but four of its
species are too.  Others: Calothrix, Trichoderma.

Added a hack to provide a bit of debugging information for
'incompatible-use' annotations, which are usually difficult to fathom.

Added extra diagnostic checks to the 'take' patch operation: (1) if
the child is being moved out to one of its ancestors (above parent),
issue a warning, since this is unusual and could easily be wrong.  (2)
if the child is already a child, warn and do nothing.  I probably did
this after some patch operation went wrong that took a long time to
debug.

There's some funny code in there for debugging something about the
hidden flag for Ephedra gerardiana, which I may be able to get rid of
now since it's never invoked.

I updated the taxonomies used for 'make test' (ncbi_aster and
gbif_aster, which are in the github repo).  There are 'make' rules for
these but they are not normally triggered because the targets exist
and there are no dependencies.  This is a completely manual step, and
not very important since it doesn't matter much which taxonomies are
used for this test.

With all this basic work on data structure I sometimes found that
basic invariants of the Taxonomy structure were violated (e.g. every
taxon with an id is indexed).  To help debug violations I wrote a
utility (Taxonomy.check) to detect problems.

I had been using a utility called 'notSame' to force polysemies to get
separate taxa.  But with the change to alignment rules (above), this
broke, so I had to go through and change them all to 'same' of some
other taxon; and sometimes the other taxon had to be created so it
would be there to 'distract' the alignment code.  Usually homonym
separation is automatic, but a recent change removing certain kinds of
separations (because of pairs that were *not* homonyms and had been
treated as such), while turning many false negatives into true
positives, also turned many true negatives into false positives.

I use inclusions.tsv to test to see if homonyms are being separated
properly; this is very useful.  The taxonomy now has its own copy of
inclusions.tsv so that I can add tests that won't pass in the released
taxonomy version.

I improved the check_inclusions script so that it reports better
diagnostic information on failure.

(many of the names in the names_of_interest list probably ought to
have inclusion tests too.)

There is a Makefile target for running the inclusion tests, but they
are also run automatically at the end of every assembly.  I put some
effort into improving the output of the script to help diagnose
problems when they occur (usually related to homonyms).

### Changes to data structures and the way the code is written

Internally changed the 'infraspecific' flag to 'specific'; this is to
simplify the logic and make it easier to think about.  Externally it's
still rendered as 'infraspecific'.  This is probably one of those
cases where I tried to convince myself that my own code was correct,
and failed, and rewrote the code to make it easier to understand.

Created a new field 'idspace' of Taxonomy objects.  This replaces (in
most circumstances) the 'tag' field which provides a unique id for
taxonomies for debugging purposes.  An idspace (e.g. "worms") can be
shared among several taxonomies.  I must have done this when there
were two "worms" taxonomies and I wanted to be able to tell them apart
in debugging output.  When you create a Taxonomy, it is now obligatory
to provide an idspace.

Added a 'descendants' iterator for visiting all descendants of a node.
I forget why I wanted this, but it is nice to have and it was just an
adaptation of existing code that iterated over nodes of a taxonomy.

I changed the type of the .children list for a Taxon node from List to
Collection.  Although the children list is actually a List, this helps
reduce spurious dependencies.  I don't remember why I did this; I must
have been considering a change, e.g. to a Set or custom collection
class.

As a cleanup I changed the representation of ranks in Taxon objects.
There were a couple of reasons for this that I can't remember, but in
general it is nice to be able to compare ranks using == and to access
both their names and their numeric "level" quickly.  (The level is a
smaller positive integer for ranks closer to the root, and larger for
ranks further from the root.)  So now there is a Rank class.

Another cleanup had to do with minimizing (with an eye to removing)
use of the 'prunedp' flag.  When a node is deleted, it has to be
removed from various lists and indexes.  Before the Synonym structure
was added, there were situations where it wasn't possible to find all
pointers to the node, so instead it was marked deleted (prunedp) and
there were checks for this flag in many places so the node could be
ignored.  Things are better now and the flag is going away.  This has
no effect on the result of assembly.

Renamed the file containing the list from ids-that-are-otus.tsv to
ids_that_are_otus.tsv, to match .py file.  (I prefer to use underscores
instead of hyphens in python file names, but sometimes forget.)

If a taxon has no children then the .children field is null.  This is
sort of unfortunate, because it requires an extra condition for each
iteration over children, and there are dozens of those.  It would be
better if it were an empty list.  I am making this change
incrementally, so the code is logically consistent but stylistically
inconsistent right now.

* store a Rank object in taxon instead of rank name
* add a 'synonyms' field to Taxon for list of synonyms
* data abstraction around the idIndex

The most delicate and difficult part of smasher is the Matrix code in
AlignmentByName.  I don't really want to break the taxonomy, which
might happen if I make major changes to this class, but I would like
to start making small changes that might make it clearer and perhaps
better.  I've made a few changes just to get started on this.

Removed the 'samples' rank as it is no longer used.  I used this word
before I understood that the tips of the SILVA tree represent clusters
with reference sequences, not just sequences.

Made use of System.out vs. System.err more consistent (the former for
notes and warnings, the latter for errors requiring attention), and
made use of message prefixes more consistent (`|` for notes, `*` for
warnings, `**` for errors).

Alignments are now between arbitrary taxonomies; removed the
requirement that one of them be a union taxonomy.  Eliminated the
`.mapped` field in favor of a hash table in the Alignment object.


### Archiving and reproducibility

I collected and organized all versions of all source taxonomies used
to assemble past OTT versions starting with OTT 1.0.  I have put them
all on files.opentreeoflife.org (in /ncbi, /irmng, etc.).  In theory
we can now rebuild any past version from sources.  In practice, even
when I built OTT 2.9, there were small differences in output (just a
couple of taxa), and I don't understand that.

The metadata for all of those source taxonomy versions is checked into
the resources/ directory.

The latest source taxonomy snapshots on files.opentreeoflife.org are
now known to the Makefile and used in assembly; 'make' in a fresh
clone now retrieves them for use as inputs.  There are a few scripts
to refresh sources from the web, and publish newly captured versions
to files.opentreeoflife.org, which should be done whenever a there's a
new OTT release.

Moved all archive URLs for the source taxonomies at the top of the Makefile.



### Maintenance, administration, documentation

I upgraded jython from 2.5.3 to 2.7.0 because I wanted to use some
newer features, such as "import *".  This required changes to the
Makefile.

I created a shell script 'bin/jython' that invokes Java with both
jython and smasher active.  It is invoked the same way as the 'python'
shell command.  The script is made by a Makefile rule from a template.

I removed jscheme, which we weren't using.  (Maintainer deceased,
sadly.)

Smasher can compute a list of OTT ids that are OTU mapping targets in
phylesystem, which it uses for various purposes.  Because it takes a while to
generate this, it is checked into the repository and only updated on
request.  It doesn't matter a whole lot if it's accurate.  I did an
update around 20 June.

Others...

 * Created a Makefile target 'refresh-ncbi' for refreshing the local
   copy of NCBI Taxonomy from the NCBI ftp site.

 * Created a Makefile rule for reprocessing IRMNG from the csv files that
   I got from Tony Rees.

 * Added a script for 'publishing' locally retrieved tarballs
   (e.g. OTT, IRMNG) to files.opentreeoflife.org (see above).  This is
   to be used when we release an OTT version, to snapshot the source
   taxonomy versions that were used in assembly.

 * Put the Makefile rules in a more logical order.

 * New Makefile target 'tags' to support emacs tags-search and related
   commands.

 * There is now a flag that controls whether to add taxa that are
   IRMNG-only to the taxonomy; this allows the possibility of an
   IRMNG-free build.  I tested this and made it work.

 * I moved the NCBI to SILVA mapping table to tax/silva/ so that
   all outputs are found in the same placed.  (It was in
   feed/silva/out.)  I still need to change the OTT script so
   that it looks for the file in the new location.

 * I improved the contributors list a bit, and added Peter Midford.

 * Removed the long-obsolete t/tax/prev_nem files


## Side projects

### Taxon counts

Count of binomial names in the taxonomies - something David Hibbett
requested.  The script I wrote for him is in
util/fungal-order-counts.py. It creates a table showing number of
binomials per taxon for every fungal order.  This does not affect OTT.

### Conflict service

I won't dwell on the conflict analysis service, which is new and
doesn't affect assembly, but a few enhancements to the rest of the
system to support it might be interesting:

For the conflict service, I wrote a Nexson loader.  It's a bit awkward,
e.g. it might benefit from having some wrapper classes, but
currently just passed around JSON blobs directly.

The Nexson parser has to pay attention to the forwarding pointers from
forwards.tsv ...

To better support reading and writing Newick, in addition to names
being optional, ids are also optional.  This presents a problem when
writing a taxonomy to flat files in the usual smasher 4-column format;
this depends on there being identifiers.  To address this I have logic
to add identifiers to a taxonomy that doesn't have them, and remove
them from one that does.  The convention is that if it is an integer
starting with '-' then it is a temporary id created for output
purposes and removed on input.

Small changes were needed in the Newick parser to accommodate
supertree Newick files generated by the propinquity pipeline.

There's something about fixing unicode characters in Newick
strings... I forget

### Misc

I've added the option for a taxonomy node to have no name.  This
requires a lot of annoying node.name == null checks, but it is
required for conflict analysis (where nexson source trees are
represented internally as Taxonomy objects).

Added finer control of the name_id feature in Newick string
generation (controlled by a flag).

Created a simple python script util/select_from_taxonomy.py that just
invokes Java to extract a subtree.  (need to make sure it still works)

Yan Wong provided code to harvest wikidata ids from wikidata.  I put
this in the repository but have not done anything with it yet.
https://github.com/OpenTreeOfLife/reference-taxonomy/issues/161

Tweaked worms.py (the script that fetches WoRMS from the WoRMS web
site using its API) to filter out invalid taxon copies.  This logic is
not used yet, since I haven't rerun the WoRMS web crawl.

### Additions

I added a class (Addition) to take care of the new taxon addition
feature.  It does two things: (1) it reads the addition files from the
repository and adds them to the draft, (2) it composes a request to
the addition service for new taxa required by the draft under
construction.

While debugging a new draft, identifier allocation is done not by the
web service, but by code that manipulates a directory locally
(util/process_addition_request.py).  On the last run (or two) before
publication (either draft publication or release), the assembly should
be switched over to the web service to make the identifiers permanent.

The additions feature will fix a problem that's been nagging me for
years - that the identifiers change with each draft, because the
baseline is always the previous OTT version, not the previous draft.
That needn't be the case now - you can look up a name in draft7, and
get the same OTT id when you look it up in draft8.  I don't have
automation for this yet.

Made the additions processor smarter about sibling homonyms; sibling
homonyms are an error, and now the error is detected and reported.
