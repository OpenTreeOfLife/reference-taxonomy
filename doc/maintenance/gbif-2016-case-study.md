
# Updating to the August 2016 version of the GBIF backbone

In updating OTT from the 2013 version to the August 2016 version of
GBIF, there were two kinds of problems: file format changes, and
taxonomic incompatibilities.

## File format change

There were three kinds of changes from 2013:

 * the information had moved to different columns
 * the new version lacked a `canonicalName` field, which OTT needs and which was present in the
   2013 version
 * the `nameAccordingTo` column from 2013 (a data set name) was replaced by `datasetID` (a UUID).

To adjust the column numbers without losing the ability to process the
2013 GBIF, I added a preprocessing step that selects relevant columns
from the taxon.txt file.  There are two different scripts to do this,
one for each format version.  I retained the ability to use the 2013
version just in case a reversion was needed, or if some kind of
comparison was desired.  The preprocessing script converts the
distribution to a common intermediate format, and then a GBIF to OTT
processing script turns that into an OTT-format source taxonomy.  The
script invocation is coordinated from the Makefile.

A better solution would be for OTT to understand Darwin Core Archives
(DwCA) ([issue 83](https://github.com/OpenTreeOfLife/reference-taxonomy/issues/83)).
This would make the GBIF import process immune to changes in GBIF taxon.txt column order.

To compute `canonicalName` the preprocessing script uses a set of
regular expressions.  This is not the best solution; it would be
better to use gnparse.  See [issue 318](https://github.com/OpenTreeOfLife/reference-taxonomy/issues/318).

Regarding `datasetID`, only a few particular values need to be
recognized.  I added them as alternatives to the OTT-format generator
so that either a data set name or a data set UUID can be put in that
column.

## Making pre-merge patches more robust

Upgrading GBIF caused a bunch of patches in adjustments.py to fail.
Building OTT takes 15 minutes, so to make patch testing quicker, I
used a two-line python script:

    import taxonomies
    taxonomies.load_gbif()

invoked using bin/jython.

A bunch of the pre-alignment patches failed because the names
had disappeared from GBIF or had become ambiguous.  E.g. instead of

    gbif.taxon('2439121').absorb('6075534')

this had to be

    myo2 = gbif.maybeTaxon('6075534')
    if myo2 != None:
        gbif.taxon('2439121').absorb(myo2)

because id from 2013 had disappeared in 6075534.  This ought to be
automatic in the patch language, so you don't have to think about node
existence and add ugly conditionals to the script, but it's not.

One error was that _Navicula_ showed up as ambiguous.  The two
_Navicula_s were siblings, clearly a new error in 2016 GBIF.  I fixed
this by pruning the "sibling homonym".  The prune directive I added
uses a taxon id, which is unambiguous, after which the preexisting
patch was able to refer to the remaining node using the name.  (Always
better to use names since the ids can be unstable.)

The following reference also became ambiguous:

    gbif.taxonThatContains('Aporia', 'Aporia agathon')

Apparently GBIF now knows of both a plant and an insect with this same
species name - in fact most of the genus is duplicated.  To fix this,
I had to find a species name that occurred only in the insect genus.
A few searches in the taxon.txt files led to _Aporia gigantea_.
Replacing _agathon_ with _gigantea_ made the error go away, but of course
it doesn't fix the underlying data problem, which remains in OTT.

The existing patch for _Bdellodes_ was actually incorrect.  It said:
put the _Bdellodes_ genus that contains _Bdellodes serpentinus_ in
mites (Bdellidae).  But I had made a mistake; _Bdellodes serpentinus_
is actually a flatworm (according to wikipedia).  I think 2013 GBIF
had only one _Bdellodes_ genus, perhaps combining the flatworms and
the mites, and I thought it was all mites, but now 2016 GBIF has fixed
the problem by splitting the two _Bdellodes_ genera.  (To complicate
things, though, the flatworm genus is listed as 'doubtful' and none of
the species have parent pointers in the GBIF dump.)

Solution: replace _serpentinus_ with _robusta_, after checking to make
sure that _robusta_ **is** a mite and only a mite.

## Post-merge patches

    ott.taxon('Thamnophilus bernardi').absorb(ott.taxon('Sakesphorus bernardi'))
    AttributeError: 'NoneType' object has no attribute 'absorb'

Rewrote this in a pretty awkward way.

Many other patches like this had to be made more robust by adding an
explicit test for None.  Again, the test ought to be automatic - not
something someone writing a patch should have to think about.

Instead of checking for null I could have removed the patches - but I
want to keep track of them since eventually there should be tests for
them all.

## Checking the deprecated file

Next I did a full OTT build and looked at the `deprecated.tsv` file.

There are 53 rows with both 'id-retired' and 'synthesis' them,
representing OTUs that would be lost from the synthetic tree.  (There
are 231 for all of phylesystem, and many many more in the whole tree.)
So the approach here is to pick a few at random to examine, to see if
there are easy fixes and/or some systematic problem (such as a
separation taxonomy alignment error).

Sample: _Hierococcyx bocki_.  'grep' tells us that this species is in
GBIF's taxon.txt file, so it must have been removed by the GBIF import
script.  Reason: the genus _Hierococcyx_ is marked 'doubtful' and the
script throws away anything that's doubtful, along with all of its
descendants.  (Solution: maybe have a way to grandfather doubtful
genera? I don't know... punt for now.)

Another sample: _Centropus burchellii_ - this one is different, it's
just plain missing from the new GBIF.  globalnames.org tells us it's a
bird, and claims it's in GBIF, which is not what we see in taxon.txt
(which is nothing) - GN must have an older version of GBIF.  The
species is current in the IOC World Bird List, which GBIF supposedly
incorporates, so possibly just version skew.

Another sample: _Isopora cylindrica_. - also missing.  2013 GBIF
says it came from the IUCN Red List.  IUCN Red List shows it as an
endangered coral (Cnidaria), first described 2008.  Maybe the red list
is no longer a GBIF source?  No... punt for now.

Another sample: _Psammocora nierstraszi_ and _Psammocora explanulata_
are deprecated, and they stand out because they occur in four
different taxonomies, in addition to being used in synthesis.  A
search in the taxonomy shows the name "_Psammocora explanulata_ van
der" - clearly a mistake in the way that authorities are stripped when
GBIF is processed.  The preprocessing script has a small list of
special case name prefixes like this (d' and von), to which I added
"van der".

Another sample: _Chlorestes notata_ - three subspecies are present in
taxon.txt as synonyms, but not _Chlorestes notata_ itself!  But there is
a species _Chlorestes notatus_.  Damn!  Globalnames has lots of entries
for notata, none for notatum.

In this case, and _Myiothlypis conspicillatus_ and many others, GBIF
seems to have fixed the gender ending (new: _Myiothlypis
conspicillata_) without leaving a synonym behind.  The change is fine,
but losing the OTT id means losing OTU curation effort.  (If GBIF had
kept their old id for the taxon, kept things would have been fine,
because we would have matched on GBID record id.  But it didn't.)

There are two ways one might address this: 1. go through the
id-retired list, adding a .synonym to the patch list in
assemble_ott.py for each one, or 2. add stemming logic to the
alignment module to handle this situation.

To guage how widespread the problem was, I added some logic to the
dumpLog method to check for changes from -a to -us and vice versa.
This turned up 28 cases.

It wouldn't be hard to put a kludge in getCandidate, in effect adding
an extra synonym automatically, but I'm reluctant to add this overhead
to the inner loop of alignment, which gets run over six million times.

Since there are only 28 (10 in synthesis), I made a list of synonym
directives and put it in the gbif section of adjustments.py, using
emacs keyboard macros.  A loop runs over the list to add the synonyms.

Other than the gender changes, all 53 problem cases could be patched
individually, but that's a lot of work and I was looking for
systematic patterns.  But other than the gender changes there were no
patterns, which I suppose is good.

The remaining cases could all be patched but this does not seem a good
use of time.


## Archive, and add metadata to captures.json

The new GBIF .zip file had to be copied to the gbif/ directory on
files.opentreeoflife.org, so that OTT can be rebuilt by others in the
future.  The GBIF date in the Makefile had to be updated (I think the
download location had to be changed too, not sure).

Also resources/captures.json had to be updated with metadata for the
new GBIF version.  This information is not currently used but it
should be.


## Future work

Rather than modify patches piecemeal to make them more robust, it
would be better to use a few abstractions for .absorb, .synonym, and
the rest of the patch directives that encapsulate the robustness logic
(tests for None).

