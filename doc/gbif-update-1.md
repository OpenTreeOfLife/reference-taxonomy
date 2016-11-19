
# Updating to the August 2016 version of the GBIF backbone

## File format change

For update to the August 2016 GBIF, the input format had changed a bit
from 2013.  Most of the information had simple moved to different
columns, but now the taxon's 'canonicalName' (without authority) has
to be extracted from the 'scientificName' field.

Also nameAccordingTo (a data set name) is replaced by datasetID (a UUID).

To adjust the column numbers without losing the ability to process the
2013 GBIF, I added a preprocessing step that selects relevant columns
the taxon.txt file.  There are two different scripts to do this, one
for each format version.  The scripts are invoked from the Makefile.
Then the common GBIF processing script turns the selected data into an
OTT-format source taxonomy.

## Making pre-merge patches more robust

To re-run the GBIF pre-alignment patches (in taxonomies.py) repeatedly
without having to do a full OTT build, I used a tiny python script:

    import taxonomies
    taxonomies.load_gbif()

invoked with bin/jython {script.py}.

Next, a bunch of the pre-alignment patches failed because the names
had disappeared or had become ambiguous.  E.g. instead of

    gbif.taxon('2439121').absorb('6075534')

this had to be

    myo2 = gbif.maybeTaxon('6075534')
    if myo2 != None:
        gbif.taxon('2439121').absorb(myo2)

because id from 2013 had disappeared in 6075534.

In one case, I had to prune a sibling homonym in order to be able to
make a subsequent reference unambiguous (Navicula).  (2016 GBIF
introduces this second sense of Navicula, which is ranked 'species'
and is clearly a mistake.)  The new prune directive uses a taxon id,
which the following patch was able to use the name (always better to
use names since the ids can be pretty unstable).

The following reference became ambiguous:

    gbif.taxonThatContains('Aporia', 'Aporia agathon')

Apparently GBIF now knows of both a plant and an insect with this same
species name - in fact most of the genus is duplicated.  To fix this,
I had to find a species name that occurred only in the insect genus.
A few searches in the taxon.txt files led to Aporia gigantea.
Replacing agathon with gigantea made the error go away, but of course
it doesn't fix the underlying data problem.

The patch for Bdellodes was actually incorrect.  It said: put the
Bdellodes genus that contains Bdellodes serpentinus in mites
(Bdellidae).  But I had made a mistake; Bdellodes serpentinus is
actually a flatworm (according to wikipedia).  I think 2013 GBIF had
only one Bdellodes genus, perhaps combining the flatworms and the
mites, and I thought it was all mites, but now 2016 GBIF has two
Bdellodes genera, i.e. the bug in GBIF got fixed (so to speak).  (To
complicate things, though, the flatworm genus is 'doubtful' and none
of the species have parent pointers.)

Solution: replace 'serpentinus' with 'robusta', which *is* a mite.

## Post-merge patches

    ott.taxon('Thamnophilus bernardi').absorb(ott.taxon('Sakesphorus bernardi'))
AttributeError: 'NoneType' object has no attribute 'absorb'

Rewrote this in a pretty awkward way.

Many others had to be 'robustified' (test for null).

## Check deprecated file

Look for rows with 'id-retired' and 'synthesis' - there are 53 of
them, which is a lot of synthetic tree tips to lose.  (There are 231
for all of phylesystem, and many many more in the whole tree.)  So the
approach here is to pick a few at random to examine.

Example: Hierococcyx bocki.  'grep' tells us that this species is in
GBIF's taxon.txt file, so it must have been removed by the GBIF import
script.  Reason: the genus Hierococcyx is marked 'doubtful' and the
script throws away anything that's doubtful, along with all of its
descendants.  (Solution: maybe have a way to grandfather doubtful
genera? I don't know...)

Check another: Centropus burchellii - this one is different, it's just
plain missing from the new GBIF.  globalnames.org tells us it's a
bird, and claims it's in GBIF, which is not what we see in taxon.txt
(which is nothing).  The species is current in the IOC World Bird List
- yet another reason to get that merged.

Sample another one: Isopora cylindrica. - also missing.  2013 GBIF
says it came from the IUCN Red List.  IUCN Red List shows it as an
endangered coral (Cnidaria), first described 2008.  Maybe the red list
is no longer a GBIF source?

Chlorestes notata - three subspecies are present in taxon.txt as
synonyms, but not Chlorestes notata itself!  But there is a species
Chlorestes notatus.  Damn!  Globalnames has lots of entries for
notata, none for notatum.  This could be patched, as could all 53
problem cases, but here I'm looking for general patterns, and not
finding any.

In the case of Myiothlypis conspicillatus and many others, GBIF seems
to have fixed the gender (new: Myiothlypis conspicillata) ending
without leaving a synonym behind.  The change is fine, but losing the
OTT id means losing curation effort.  (If GBIF had kept their old id
for the taxon, things would have been fine, because we would have
matched on GBID record id.)

There are two ways one might address this: 1. go through the list
manually, adding a .synonym to the patch list in assemble_ott.py for
each one, or 2. add stemming logic to the alignment module to handle
this situation.

To guage how widespread the problem is, I added some logic to dumpLog
to check for changes from -a to -us and vice versa.  Tested with 'make
test' (after forcing a refresh of gbif_aster and ncbi_aster by
deleting old versions).  That turned up 6 apparent gender changes,
which is a lot given how small Asterales is.  So this looks
real... just to check, I'll run the whole OTT to see.  OK, 28 cases.

It wouldn't be hard to put a kludge in getCandidate, in effect adding
an extra synonym automatically, but I'm reluctant to add this overhead
to the inner loop of alignment, which gets run over two million times.

Since there are only 28 (10 in synthesis), I will make a list of
synonym directives and put it in the gbif section of taxonomies.py
(pre-alignment adjustments).  Using emacs keyboard macros.

In any case that's only about 20% of the problem cases.

Psammocora nierstraszi and Psammocora explanulata are deprecated, and
they stand out because they occur in four different taxonomies, in
addition to being used in synthesis.  A search in the taxonomy shows
the name "Psammocora explanulata van der" - clearly a mistake in the
way that authorities are stripped when GBIF is processed.  There is a
small list of special case name prefixes like this (d' and von), to
which I added "van der".


## Archive, and add metadata to captures.json

TBD


## Future work

Rather than modify patches piecemeal to make them more robust, it
would be better to use a few abstractions for .absorb, .synonym, and
the rest of the patch directives that encapsulate the robustness logic
(tests for null).  Later...

