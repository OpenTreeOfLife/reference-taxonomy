# OTT maintainer's guide


## Addressing feedback issues

Ideally one starts by writing a test for each issue, both to see if it
has already been resolved and for regression testing.  Unfortunately
there's no pleasant way to write such tests.

There is no systematic approach to feedback issues.  The best way to
understand how to deal with them is to go through some worked
examples, which is what this document does.

All solutions currently involve edits either to `adjustments.py` or
`amendments.py`, both in the `curation` directory.  See [the patch
language documentation](patch.md) for information on the notations
used for writing patches.  It would be better if we had a directory
full of python files containing patches, to facilitate editing by
multiple authors.  TBD.

### [Issue 397](https://github.com/OpenTreeOfLife/feedback/issues/397) - Scallops in Cnidaria

The issue points to duplicate locations of the "same" taxon, 'Placopecten
magellanicus', one with ottid 449040 (in Bilatera) and one with ottid 6370755
(in Cnidaria). This taxa is a scallop, and should be in Bilatera, i.e. the
Cnidaria one is misplaced. Using `bin/investigate "Placopecten magellanicus"`
we see that the incorrect placement comes from gbif:
```
r/ott-NEW/source/taxonomy.tsv:6370755	|	6370754	|	Placopecten magellanicus	|	species	|	gbif:2285952	|	Placopecten magellanicus (species in phylum Cnidaria)	|		|
r/ott-NEW/source/taxonomy.tsv:449041	|	449040	|	Placopecten magellanicus	|	species	|	worms:156972,ncbi:6577,irmng:11384074,irmng:10529011	|	Placopecten magellanicus (species in Bilateria)	|	 
```
This appears to be [fixed in the current version of gbif](https://www.gbif.org/species/2285952), but we don't have that yet, and updating gbif is going to take some
time, so we will patch instead.

Compare the lineage of these two ott taxa (only showing output to the point
where they match). Using `bin/lineage 449041 r/ott-NEW/source`:

```
449041	449040	Placopecten magellanicus	species	worms:156972,ncbi:6577,irmng:11384074,irmng:10529011	Placopecten magellanicus (species in Bilateria)		
449040	5692602	Placopecten	genus	worms:156971,ncbi:6576,irmng:1019019	Placopecten (genus in Bilateria)		
5692602	975312	Palliolinae	subfamily	worms:393793			
975312	951120	Pectinidae	family	worms:213,ncbi:6566,irmng:115891			
951120	951119	Pectinoidea	superfamily	worms:151320,ncbi:106219		sibling_higher
951119	1025543	Pectinida	order	worms:387324,irmng:12740		sibling_higher
1025543	1025545	Pteriomorphia	subclass	worms:206,ncbi:6545			
1025545	802117	Bivalvia	class	worms:105,ncbi:6544,gbif:137,irmng:1146		sibling_higher
802117	155737	Mollusca	phylum	worms:51,ncbi:6447,gbif:52,irmng:175			
155737	189832	Lophotrochozoa	no rank	ncbi:1206795			
189832	117569	Protostomia	no rank	ncbi:33317			
117569	641038	Bilateria	no rank	ncbi:33213			
641038	691846	Eumetazoa	no rank	ncbi:6072			
```

and `bin/lineage 6370755 r/ott-NEW/source`:

```
6370755	6370754	Placopecten magellanicus	species	gbif:2285952	Placopecten magellanicus (species in phylum Cnidaria)		
6370754	442914	Placopecten	genus	gbif:2285951	Placopecten (genus in phylum Cnidaria)		
442914	712383	Merulinidae	family	worms:196102,ncbi:46736,ncbi:46733,gbif:3358,gbif:5181,gbif:8219,irmng:104400,irmng:100317,irmng:100502		sibling_higher
712383	1084485	Scleractinia	order	worms:1363,ncbi:6125,gbif:714,irmng:11376		sibling_higher
1084485	1084488	Hexacorallia	subclass	worms:1340,ncbi:6102			
1084488	641033	Anthozoa	class	worms:1292,ncbi:6101,gbif:206,irmng:1134			
641033	641038	Cnidaria	phylum	worms:1267,ncbi:6073,gbif:43,irmng:151			
641038	691846	Eumetazoa	no rank	ncbi:6072			
```

GBIF places the genus Placopecten in Merulinidae, which is definitely in
Cnidaria, so let's move that to Pectinidae (not to Palliolinae, which is a
barren subfamily that only exists in worms). 

### [Issue 345](https://github.com/OpenTreeOfLife/feedback/issues/345) - Conolophus extinct?

As discussed on github, the easy fix is to get rid of the mammal
Conolophus, keeping the other one.  For this approach, the following
should go in `adjust_irmng` or `align_irmng`:

    irmng.taxon('Conolophus', 'Mammalia').prune()

If we wanted to keep both taxa, we could use `establish`, with the
following in `align_irmng`:

    establish('Conolophus', ott, 'genus', ancestor='Mammalia', source='irmng:1415243')

We don't need to `establish` the iguana because it's already present in NCBI and GBIF.

That ought to be enough in itself, but I haven't tried it yet.  To be
sure, one would align the IRMNG genera to OTT:

    a.same(irmng.taxon('Conolophus', 'Iguania'), ott.taxon('Conolophus', 'Iguania'))
    a.same(irmng.taxon('Conolophus', 'Mammalia'), ott.taxon('Conolophus', 'Mammlia'))


### [Issue 341](https://github.com/OpenTreeOfLife/feedback/issues/341) - Campanulales = Asterales?

Is there an error or not?  I followed the supplied OTT link to Asterales,
and from there to the second IRMNG record, for Campanulales.  Indeed, equating Campanulales
with Asterales sounds fishy.  But this is what NCBI does, so it stands.
The synonymy is pro parte, I believe, so perhaps we should consider these to be distinct
taxon records.  I think I would leave this one alone for now.

See new [issue 316](https://github.com/OpenTreeOfLife/reference-taxonomy/issues/316) on pro parte synonyms.

### [Issue 340](https://github.com/OpenTreeOfLife/feedback/issues/340) Myomorpha incorrectly synonymised with Sciurognathi

Mark has already analyzed this.  Turns out to be logically the same as the
previous issue (341).

### [Issue 336](https://github.com/OpenTreeOfLife/feedback/issues/336) - Miliaria calandra is synonym for Emberiza calandra

Running the following commands at the shell

    bin/investigate "Miliaria calandra"
    bin/investigate "Emberiza calandra"

shows that NCBI has the two names as separate species; so that is the
source of the error.

To find out which of the two names is currently accepted, I checked
worldbirdnames.org and wikipedia.

The easiest fix - requiring the least amount of thinking - is in
amendments.py:

    proclaim(ott, synonym_of(taxon('Miliaria calandra'), taxon('Emberiza calandra'),
                             'objective synonym', otc(60)))

or equivalently (using the v2 patch facility)

    ott.taxon('Emberiza calandra').absorb(ott.taxon('Miliaria calandra'))

Be sure to change `60` to some unused `otc` number.

A more nuanced method is to put the directive in `patch_ncbi` in
`adjustments.py`, to prevent the creation of incorrect structure in OTT
in the first place.  This requires some knowledge of the merge order,
so is less robust to future taxonomy changes than the `amendments.py`
method.

    proclaim(ncbi, synonym_of(taxon('Miliaria calandra'), taxon('Emberiza calandra'),
                              'objective synonym', otc(60)))

There will be an empty genus `'Miliaria'` left over.  It's not too
harmful to leave it in, since it will be flagged `barrent` and
suppressed from synthesis, but to be tidy one would want to get rid of
it:

    proclaim(ott, synonym_of(taxon('Miliaria', 'Aves'), taxon('Emberiza', 'Aves'),
                             'proparte synonym', otc(61)))

I'm specifying an ancestor for the genera because genus names are so
often ambiguous, and it doesn't hurt.

### [Issue 332](https://github.com/OpenTreeOfLife/feedback/issues/332) - Misspelling of _Strigops habroptilus_

First try to understand what's going on.

    grep "Strigops habroptilus" r/ott-NEW/source/taxonomy.tsv
    809432	|	512918	|	Strigops habroptilus	|	species	|	ncbi:57251,irmng:11435975

    grep "Strigops habroptila" r/ott-NEW/source/taxonomy.tsv
    5857013	|	512918	|	Strigops habroptila	|	species	|	gbif:2479236

It's not obvious which is right, without a knowledge of Latin.
Wikipedia has _-ilus_; IUCN has _-ila_ (mentioned in the Wikipedia article).
IUCN asserts "Strigops habroptilus Gray, 1845 [orth. error in BirdLife International (2004)]".
GBIF imports IUCN, and if I remember correctly GBIF also has
significant smarts for correcting the gender ending of epithets.  
My bet is on -ila because IUCN explicitly says that -ilus is an error.

Easier fix: in `amendments.py`, merge the two:

    proclaim(ott, synonym_of(taxon('Strigops habroptilus'), taxon('Strigops habroptila'),
                             'gender variant', otc(62)))

Alternate fix: fix the name in `align_ncbi` in adjustments.py:

    proclaim(ncbi, synonym_of(taxon('Strigops habroptilus'), taxon('Strigops habroptila'),
                              'gender variant', otc(62)))

The latter leaves a synonymy behind, which is enough to cause the
misspelled IRMNG node to align to it.

### [Issue 327](https://github.com/OpenTreeOfLife/feedback/issues/327) - nozaki vs nozakii, lamarckii vs lamarcki

First look at taxon 152136 _(Cyanea)_ in the taxonomy browser to see sources
for both nodes.  _citrea_ is from GBIF via ITIS; _citrae_ is from
WoRMS (and subsequently CoL, GBIF, and IRMNG - GBIF has both
spellings).  But it's not at all clear which is right.

_Cyanea citrea_ Kishinouye, 1910 - ITIS (NOAA)  
_Cyanea citrae_ (Kishinouye, 1910) - WoRMS (URMO)

_Cyanea citrae_ seems to be the misspelling, based on primary literature
(found by Google Scholar searches of the two spellings).
The best fix would be in `align_worms` since WoRMS is merged before
GBIF and IRMNG:

    proclaim(worms, synonym_of(taxon('Cyanea citrea'), taxon('Cyanea citrae'),
                               'misspelling', otc(63)))

This creates a synonym record, allowing both spellings to align to
this record down the line.

It's not clear whether _rosea_ and _rosella_ are different.  _rosella_
smells like a synonym, because of its meagre provenance, but is it?
First we check to see if the authority of the two is the same; that
would suggest a misspelling.  No, clearly different authorities, per
GBIF (GBIF and IRMNG are both good sources of authority information).
_rosella_ has no occurrence records and comes to GBIF only from IRMNG
(according to its GBIF page).  IRMNG gets it from CAAB.  CAAB is Codes
for Australian Aquatic Biota.  CAAB takes us to Gowlett-Holmes, K.L.,
2008. A field guide to the marine invertebrates of South
Australia. notomares, Hobart, TAS. 333pp.  At about this point my
energy runs out and I say let's just assume it's a separate species.

_C. lamarcki_ vs. _C. lamarckii_ - one _i_ - 132 scholar results; two _i_'s 344
results.  WoRMS also has two _i_'s.  Rather than spend a lot of time
trying to track down the original description, I would go with the
majority.  It can always be fixed later.  A possible fix would be to
add the synonym to `align_worms`.

    proclaim(worms, synonym_of(taxon('Cyanea lamarcki'), taxon('Cyanea lamarckii'),
                               'misspelling', otc(64)))

This could also be done in `adjustments.py` with `ott` as the taxonomy.

Another option would be to use `establish` at the beginning of
assembly to force the correct spelling at the outset, but I consider
`establish` to be a sledgehammer to be used only when necessary.


_C. nozaki_ vs. _C. nozakii_ - _nozaki_ 17 hits, _nozakii_ 520 hits.

    proclaim(worms, synonym_of(taxon('Cyanea nozaki'), taxon('Cyanea nozakii'),
                               'misspelling', otc(65)))

### [Issue 318](https://github.com/OpenTreeOfLife/feedback/issues/318) _Syntexix_ misspelling for _Syntexis_

_Syntexis_ has two senses.  Using the taxonomy browser we see that the
one in Anaxyelidae (in Hymenoptera) (893510) comes from NCBI.  The
other is a synonym for Mollisia in Fungi.  _Syntexix_ comes from GBIF and IRMNG
(it's probably in GBIF by way of IRMNG) and is in Anaxyelidae (in
Hymenoptera).  So the first sense of _Syntexis_ is pretty clearly the
same as the single sense of _Syntexix_.

This is a bit trickier because we need to merge both the genus and its
single species.  I would recommend modifying the GBIF-to-OTT alignment
in `align_gbif`, but one could also modify OTT in `adjustments.py`.
This time, specifying an ancestor of the genus is essential
in order to avoid the Fungi ambiguity.

    a.same(gbif.taxon('Syntexix', 'Hymenoptera'),
           ott.taxon('Syntexis', 'Hymenoptera'))
    a.same(gbif.taxon('Syntexix libocedrii', 'Hymenoptera'),
           ott.taxon('Syntexis libocedrii', 'Hymenoptera'))

### [Issue 317](https://github.com/OpenTreeOfLife/feedback/issues/317) All the Nautilidae genera ending in _-ceras_ are extinct

The original problem is fixed in the OTT 3.1 draft, but there remains a superfluity of
species in _Nautilus_.  I sent email to Tony Rees to have him fix the
genus in IRMNG, and GBIF picks up the genus from IRMNG, so given
enough time, waiting for the IRMNG fix to propagate is the correct fix
for the problem.  However that could take years, and if new versions
of IRMNG are not made available without a TOU, it could be forever.

The easiest short-term fix is just to clean out the bad ones, or set
them hidden.  Wikipedia lists four valid extant species, so let's keep
those and remove the others.

    valid = ['belauensis', 'macromphalus', 'pompilius', 'stenomphalus']

    for child in ott.taxon('Nautilus', 'Cephalopoda').getChildren():
        (species, epithet) = child.name.split(' ', 1)
        if not epithet in valid:
            child.hide()        
