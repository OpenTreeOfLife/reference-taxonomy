# OTT maintainer's guide


## Addressing feedback issues

Ideally one starts by writing a test for each issue, both to see if it
has already been resolved and for regression testing.  Unfortunately
there's no pleasant way to write such tests.

There is no systematic approach to feedback issues.  But here are
some worked examples.

All solutions involve edits either to `adjustments.py` or
`amendments.py`.  Descriptions of the appropriate methods are in the
[scripting feature documentation](https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/doc/scripting.md).


### [Issue 341](https://github.com/OpenTreeOfLife/feedback/issues/341) - Campanulales = Asterales?

Is there an error or not?  I followed the supplied OTT link to Asterales,
and from there to the second IRMNG record, for Campanulales.  Indeed, equating Campanulales
with Asterales sounds fishy.  But this is what NCBI does, so it stands.
The synonymy is pro parte, I believe, so perhaps it shouldn't stand.
See new [issue 316](https://github.com/OpenTreeOfLife/reference-taxonomy/issues/316) on pro parte synonyms.

### [Issue 340](https://github.com/OpenTreeOfLife/feedback/issues/340) Myomorpha incorrectly synonymised with Sciurognathi

Mark has already analyzed this/  Turns out to be logically the same as the
previous issue (341).

### [Issue 336](https://github.com/OpenTreeOfLife/feedback/issues/336) - Miliaria calandra is synonym for Emberiza calandra

Running the following commands at the shell

    bin/investigate "Miliaria calandra"
    bin/investigate "Emberiza calandra"

shows that NCBI has the two names as separate species; so that is the
source of the error.

There are many possible fixes.  The easiest - requiring the least amount of
thinking - is to do an `absorb` in `patch_ott` (amendments.py):

    ott.taxon('Emberiza calandra').absorb(ott.taxon('Miliaria calandra'))

(To find out which of the two is 'best' I checked worldbirdnames.org and wikipedia.)

But personally I would prefer to put the directive in `patch_ncbi` in
adjustments.py, to prevent the creation of incorrect structure in OTT
in the first place.  This requires some knowledge of the merge order,
so is less robust to future taxonomy changes than the `amendments.py`
method.

    ncbi.taxon('Emberiza calandra').absorb(ncbi.taxon('Miliaria calandra'))

There will be a genus `'Miliaria'` left over; it's not too harmful to
leave it in, but to be tidy one would want to get rid of it:

    ott.taxon('Emberiza', 'Aves').absorb(ott.taxon('Miliaria', 'Aves'))

I'm specifying an ancestor for the genera because genus names are so often ambiguous.

TBD: .absorb is too vague for this purpose.  We really need a way to
say that X and Y are synonyms of a particular type
(objective/contypic vs. subjective/allotypic, and coextensive vs. pro parte).

### [Issue 332] - Misspelling of _Strigops habroptilus_

First try to understand what's going on.

    grep "Strigops habroptilus" tax/ott/taxonomy.tsv
    809432	|	512918	|	Strigops habroptilus	|	species	|	ncbi:57251,irmng:11435975

    grep "Strigops habroptila" tax/ott/taxonomy.tsv
    5857013	|	512918	|	Strigops habroptila	|	species	|	gbif:2479236

It's not obvious which is right, without a knowledge of Latin.
Wikipedia has -us; IUCN has -a (mentioned in the Wikipedia article).
IUCN asserts "Strigops habroptilus Gray, 1845 [orth. error in BirdLife International (2004)]".
GBIF imports IUCN, and if I remember correctly GBIF also has
significant smarts for correcting the gender ending of epithets.  
My bet is on -ila because IUCN explicitly says that -ilus is an error.

Easier fix: in amendments.py, merge the two:

    ott.taxon('Strigops habroptila').absorb(ott.taxon('Strigops habroptilus'))

Better fix: fix the name in `align_ncbi` in adjustments.py:

    ncbi.taxon('Strigops habroptilus').rename('Strigops habroptila')

The latter leaves a synonymy behind, which is enough to cause the
IRMNG node to align to it.

### [Issue 327] 

First look at taxon 152136 _(Cyanea)_ in the browser to see sources
for both nodes.  _citrea_ is from GBIF via ITIS; _citrae_ is from
WoRMS (and subsequently CoL, GBIF, and IRMNG - GBIF has both
spellings).  But it's not at all clear which is right.

_Cyanea citrea_ Kishinouye, 1910 - ITIS (NOAA)  
_Cyanea citrae_ (Kishinouye, 1910) - WoRMS (URMO)

_Cyanea citrae_ seems to be the misspelling, based on Google Scholar
searches of the two spellings.
The best fix would be in `align_worms` since WoRMS is merged before
GBIF and IRMNG:

    worms.taxon('Cyanea citrae').rename('Cyanea citrea')

This creates a synonym record, allowing both spellings to align to
this record down the line.

It's not clear whether _rosea_ and _rosella_ are different.  _rosella_
smells like a synonym, because of its meagre provenance, but is it?
First we check to see if the authority of the two is the same; that
would suggest a misspelling.  No, clearly different authorities, per
GBIF.  _rosella_ has no occurrence records and comes to GBIF only from
IRMNG (according to its GBIF page).  IRMNG gets it from CAAB.  CAAB is
Codes for Australian Aquatic Biota.  CAAB takes us to Gowlett-Holmes,
K.L., 2008. A field guide to the marine invertebrates of South
Australia. notomares, Hobart, TAS. 333pp.  At about this point my
energy runs out and I say let's just assume it's a separate
species.

_C. lamarcki_ vs. _C. lamarckii_ - one _i_ - 132 scholar results; two _i_'s 344
results.  WoRMS also has two _i_'s.  Rather than spend a lot of time
trying to track down the original description, I would go with the
majority.  It can always be fixed later.  A possible fix would be to
add the synonym to `align_worms`.

    worms.taxon('Cyanea lamarckii').synonym('Cyanea lamarcki', 'misspelling')

Another option would be to use `establish` at the beginning of
assembly; that would be robust to changes in merge order.  But there
is no precedent for this so far.

_C. nozaki_ vs. _C. nozakii_ - _nozaki_ 17 hits, _nozakii_ 520 hits.

    worms.taxon('Cyanea nozakii').synonym('Cyanea nozaki', 'misspelling')

_postelsi_ vs. _posteli_ - 2 hits vs. 0 hits, three sources vs. one
source.  I'd say _postelsi_.

    worms.taxon('Cyanea postelsi').synonym('Cyanea posteli', 'misspelling')

Now it would be nice if there were one thing you could write that
fixed these problems, without you having to know about sources and
merge order and all that.  There isn't (other than the `.absorb`
solution, which I don't like because it messes up the sources list,
and is fragile to changes in the source taxonomies).

### [Issue 318] _Syntexix_ misspelling for _Syntexis_

_Syntexis_ has two senses.  Using the taxonomy browser we see that the
one in Anaxyelidae (in Hymenoptera) (893510) comes from NCBI; the
other is a synonym for Mollisia in Fungi.  _Syntexix_ comes from GBIF and IRMNG
(it's probably in GBIF by way of IRMNG) and is in Anaxyelidae (in
Hymenoptera).  So the first sense of _Syntexis_ is pretty clearly the
same as the single sense of _Syntexix_.

This is a bit trickier because we need to merge both the genus and its
single species.  But again I would recommend just changing the
alignments in `align_gbif`.  This time the genus ancestor is essential
in order to avoid the Fungi ambiguity.

    a.same(gbif.taxon('Syntexix', 'Hymenoptera'), 
           ott.taxon('Syntexis', 'Hymenoptera'))
    a.same(gbif.taxon('Syntexix libocedrii', 'Hymenoptera'),
           ott.taxon('Syntexis libocedrii', 'Hymenoptera'))

[317] All the Nautilidae genera ending in -ceras are fossils 

The original problem is fixed, but there remains a superfluity of
species in _Nautilus_.  I sent email to Tony Rees to have him fix the
genus in IRMNG, and GBIF picks up the genus from IRMNG, so given
enough time, waiting for the IRMNG fix to propagate is the correct fix
for the problem.  However that could take a couple of years.

The easiest short-term fix is just to clean out the bad ones, or set
them hidden.  Wikipedia lists four valid extant species, so let's keep
those and remove the others.

    valid = ['belauensis', 'macromphalus', 'pompilius', 'stenomphalus']

    for child in ott.taxon('Nautilus', 'Cephalopoda').getChildren():
        (species, epithet) = child.name.split(' ', 1)
        if not epithet in valid:
            child.hide()        


## Updating sources

What can go wrong?

- changes to source format (e.g. GBIF 2013 had a canonicalName column,
  but GBIF 2016 didn't)
- a source can change in some incompatible, perhaps leading to
  division problems or bad duplications or merges
- separation problems leading to duplication

See [Notes from the 2016 GBIF update](https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/doc/gbif-update-1.md)
for a case analysis.


## Testing

### Building OTT is a test

Look for '\*\*' lines in the transcript.  Compare the current
transcript to the one for the previous OTT build to see what has
changed.  A '\*\*' line might be in the previous build, in which case
it probably can be ignored.

Check the `deprecated.tsv` file.  If it has a very large number of
id-retired lines, or if they follow a pattern, something might be
wrong (such as a division alignment).

### Inclusion tests

A set of taxon inclusion tests runs every time the taxonomy is built;
it can also be run directly from the shell, as
`util/check_inclusions.py`.  (Optional arguments: list of tests,
default `inclusions.csv`, and taxonomy, default `tax/ott/`.)

The inclusions tests in the reference-taxonomy repository are more
current than the ones in the germinal repository.  The list should be
copied from the one repo to the other from time to time, or if these
tests are not used in the synthetic tree build, the germinator version
should probably just be deleted.

An attempt has been made to provide useful information when a test
fails.  A taxon can disappear, or its OTT id can change, or the
relationship may fail to hold, perhaps due to a merge or a split.

    
## Troubleshooting

Some tools to use

The `bin/investigate` shell command shows all occurrences of a given
name in OTT, the previous version of OTT, and all source taxonomies.

The `bin/lineage` shell command, given an id and a taxonomy, lists the
lineage of the given taxon.

`grep` is always handy.  You can put tabs and `^` in search strings.

    grep "Peripatus dominicae" tax/ott/taxonomy.tsv 

`log.txt` shows alignment, merge, and other events connected with
certain names.  To force logging for a name, if it's not already being
logged, add it to the `names_of_interest` list in assemble_ott.py.

    grep -A 2 Campanella tax/ott/choices.tsv 

`choices.txt` shows alignment choices that were made, whenever there
were two or more options.

Taxomachine API and taxonomy browser - you can make a taxomachine db
and put it on devapi or a local taxomachine instance and access it
through the API, through a local taxonomy browser page (it's a
one-page static webapp so easy to set up), or through the taxonomy
browser on devtree.

Print statements in the python and Java code are helpful.
Particularly useful: The `.show()` method on a `Taxon` (node) object
displays the taxon's lineage, children, and flags.
