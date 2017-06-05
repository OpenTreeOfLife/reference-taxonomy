# Finding and fixing errors

See also:

 * [Addressing feedback issues](feedback.md)
 * [GBIF 2016 import case study](gbif-2016-case-study.md)

## What can go wrong

Many kinds of things can go wrong when making a new OTT version.

1. A source taxonomy can contain an error (taxon in the wrong place, duplicate, etc)
1. There can be a spurious alignment, i.e. incorrect equation of what ought to be two taxa
1. An alignment can be missed, leading to a duplication
1. A merge can fail, leading to a conflict and an absent higher taxon
1. A patch can fail as a result of a repair to the source taxonomy.
1. Assembly logic can be wrong, usually leading to a large number of
   similar errors
1. Incompatible changes to source format (e.g. GBIF 2013 had a canonicalName column (without authority),
   but GBIF 2016 has scientificName (with authority))
1. A source can change in some incompatible way, perhaps leading to
   separation problems or bad duplications or merges
1. Separation problems leading to duplication
1. A source can contain a rank previously unknown to OTT

etc.

See [Notes from the 2016 GBIF update](https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/doc/gbif-update-1.md)
for a case study.



Errors detected during OTT assembly are written to
`r/ott-NEW/source/debug/transcript.out` on lines beginning `**`.
(Some of those lines might be innocent, so attempting to get rid of
all such messages may not be unnecessary.)

In particular, a set of taxon inclusion tests (`inclusions.tsv`) is
run at the end of each assembly, and may detect problems.

Errors are sometimes not seen until a person looks at the taxonomy.
While some errors are imported from source taxonomies, some result
from mistakes in the assembly logic.  The best way to find either kind
is to deploy the taxonomy draft on devapi and use the taxonomy browser
to inspect it, and/or to make a synthetic tree from it.

Errors are typically repaired by changes to the files
`curation/adjustments.py` or `curation/amentments.py`.  More strategic
changes are made in `assemble-ott.py` or in the smasher code itself.

If it is clear which source taxonomies are involved, e.g. for an
alignment error, repairs should be made in `adjustments.py`.  This is
preferable especially for higher taxa.  But usually it is easier to
make repairs in `amendments.py`, because that does not require that
one determine the reason for the failure or which particular sources
are involved.

Ideally, over time, undeteced errors will be converted to detectable
errors by the addition of rows to `inclusions.tsv`.

## Inclusion test failures

After each assembly run (`make ott`), the transcript
(`debug/transcript.out`) should be checked for errors.  Errors are
indicated with two asterisks at the beginning of the line (`**`).

The following illustrates (among other things) the debugging utilities
`investigate` and `lineage`.

I often check the inclusions tests first.  These show up at the very
end of `transcript.out`.

### Land snails

When building `ott3.1`, the following showed up:

    ** There is no taxon named Enidae (id 591146) in Gastropoda
       There is a Enidae (division Mollusca) not in Gastropoda; its id happens to be 591146
       Id 591146 belongs to (Enidae 591146=ncbi:145762?)

This comes from the following test in `includes.csv`:

    Enidae,Gastropoda,591146,"snail, not foram"

First we need to know where Enidae belongs.  Wikipedia is very clear
that it should be in Gastropoda.

To understand what went wrong, we need to know which source(s)
contributed Enidae, and where it ended up in the taxonomy, if not
Gastropoda.  There are utilities for this.  First, where it comes
from:

    bin/investigate Enidae
    r/ncbi-HEAD/resource/taxonomy.tsv:145762	|	145342	|	Enidae	|	family
    r/gbif-HEAD/resource/taxonomy.tsv:2297508	|	1456	|	Enidae	|	family
    r/irmng-HEAD/resource/taxonomy.tsv:114831	|	10595	|	Enidae	|	family
    r/ott-PREVIOUS/resource/taxonomy.tsv:591146	|	639691	|	Enidae	|	family	|	ncbi:145762,gbif:2297508,irmng:114831

From the number of sources, this is apparently a well-known family,
not something obscure.  To determine where it is in the draft OTT, we
can use `bin/lineage`:

    bin/lineage 591146 r/ott-NEW/source
    591146	639691	Enidae	family	ncbi:145762,gbif:2297508,irmng:114831		merged,hidden_inherited,barren	
    639691	329706	Enoidea	superfamily	ncbi:145342		hidden_inherited
    329706	844843	Sigmurethra	no rank	ncbi:216366		hidden_inherited
    844843	379916	Stylommatophora	infraorder	ncbi:6527,gbif:1456,irmng:10595		hidden_inherited
    379916	2914936	Eupulmonata	order	worms:412657,ncbi:120490		hidden
    2914936	178260	Pulmonata	infraclass	worms:103	Pulmonata (infraclass worms:103)
    178260	409995	Heterobranchia	subclass	worms:14712	Heterobranchia (subclass worms:14712)
    409995	802117	Gastropoda	class	worms:101,ncbi:6448,gbif:225,irmng:1298		sibling_higher
    802117	155737	Mollusca	phylum	worms:51,ncbi:6447,gbif:52,irmng:175
    155737	189832	Lophotrochozoa	no rank	ncbi:1206795
    189832	117569	Protostomia	no rank	ncbi:33317
    117569	641038	Bilateria	no rank	ncbi:33213
    641038	691846	Eumetazoa	no rank	ncbi:6072
    691846	5246131	Metazoa	kingdom	silva:D14357/#4,ncbi:33208,worms:2,gbif:1,irmng:11
    5246131	332573	Holozoa	no rank	silva:D14357/#3
    332573	304358	Opisthokonta	no rank	silva:D11377/#2,ncbi:33154,irmng:183
    304358	93302	Eukaryota	domain	silva:D11377/#1,ncbi:2759
    93302	805080	cellular organisms	no rank	ncbi:131567

So it actually _is_ in Gastropoda, but was veiled from the test by its
ancestor Eupulmonata being hidden.

Eupulmonata is made hidden by some logic in the WoRMS conversion
script that applies to names that are not 'accepted'.  The idea is to
suppress 'bad taxa' from what users see of OTT, yet preserve them for
future reference.  This may not be the best strategy - maybe the
unaccepted names should be omitted entirely.  That would be a simple
change to the WoRMS conversion script, and it would solve this
problem.  However, the idea seems a sound one, so let's look deeper.

You might think we could simply 'unhide' Eupulmonata, but that might
give a screwy classification.  If you look at the WoRMS record for
Eupulmonata you find that the WoRMS curators think it is paraphyletic,
and their classification of Pulmonata does not include it;
Stylommatophora is a direct child of Pulmonata.  That is why
Eupulmonata is terminal and not accepted.  The correct approach, to
bring OTT in line with WoRMS and WoRMS's priority status in molluscs,
is to find all the NCBI children of Eupulmonata and place them in the
correct WoRMS order inside Pulmonata.

It turns out there is only one child of Eupulmonata (other than a
bunch of incertae sedis that we don't care much about),
Stylommatophora.  Why did NCBI Stylommatophora not align with WoRMS
Stylommatophora? - that would have fixed this problem.  The reason is
that Stylommatophora is in WoRMS, but not in our WoRMS digest - the
whole Stylommatophora subtree is missing, as if it weren't a child of
Pulmonata at all.

It's present in our older WoRMS dump, but not in the latest one (May
2017).  This is because of a bug in the new WoRMS refresh script
(forgot to set `?marine_only=false`).  That bug can be fixed, but in the
meantime it is instructive to see if there is another way to fix this.
The most direct approach is a patch (in `amendments.py`) that makes
NCBI Stylommatophora a child of WoRMS Pulmonata:

    \# 2017-05-29 Work around bug in WoRMS API
    proclaim(ott, has_parent(taxon('Stylommatophora', 'Gastropoda'),
                             taxon('Pulmonata', 'Gastropoda'),
                             otc(NNN)))

See [the section on patch writing](patch.md) for information on
`proclaim`, `has_parent`, `taxon`, and `otc`.

### Horsetails

Failed inclusion test:

    ** A taxon cannot be its own parent: (Equisetidae ncbi:1521258+1) (Equisetidae ncbi:1521258+1)

The offending directive is

    ncbi.taxon('Equisetopsida', 'Moniliformopses').take(ncbi.taxon('Equisetidae', 'Moniliformopses'))

This patch, which did not previously result in an error, adjusted for
some nonsense previously in NCBI that has since been fixed.  So I
think we can just delete this.  I've added some notes to
doc/name-research.txt.

### Latzelia

An inclusion test can fail because of a newly introduced ambiguity.

    ** More than one taxon named Latzelia is in Myriapoda
       [(Glomeridella 146027=ncbi:1173033+8), (Haplogona 3516350=ncbi:1569519+6)]

This resulted from a WoRMS refresh that included nonmarine taxa as
well as marine ones.  The test (in inclusions.tsv) is:

    Latzelia,Myriapoda,,"not to be confused with fungus (Epiphloea)"

This one is odd in that there is no OTT id in the test.  Doing
`bin/investigate Latzelia` gives a twisted story, where the name is a
synonym for two different diplopod genera.  That suggests it's not
really a synonym for these genera but rather a paraphyletic group.  In
any case, rather than analyze this in detail, maybe it will work to
just pick one of them, say the one in Glomerida, and update the test.
That would be 146027 (Glomeridella).

    Latzelia,Glomerida,146027,"not to be confused with fungus (Epiphloea)"


## New rank

While assembling OTT 3.1:

    ** Unrecognized rank: cohort 33341

Smasher has a fixed set of ranks (Rank.java), so any new rank needs to
be added as a source code edit.  In this case, first we figure out
where 'cohort' goes relative to the ranks smasher knows about.  The
easiest way to do this is by consulting
[wikipedia](https://en.wikipedia.org/wiki/Taxonomic_rank#Cohort_.28biology.29).
Alternatively, or in addition, we can consult NCBI, where we see that
33341 Polyneoptera contains Dermaptera (rank order) and is contained
in Neoptera (rank infraclass).  In either case, cohort ends up in
between the xxxclass ranks and the xxxorder ranks.


## Patch failures

### Name change leading to broken version 2 patch

    ** No taxon found in worms with this name or id: Biota

The culprit is this:

    a.same(worms.taxon('Biota'), ott.taxon('life'))

The reason for the error is that the new version of the WoRMS import
scripts assigned the name 'life' to the root of the tree, where
formerly it has 'Biota'.  Since 'Biota' no longer exists, we get an
error for `worms.taxon('Biota')`.

I can think of several approaches.

1. Ignore the error.  This would work, but having a `**` message in
the assembly is distracting and its benign-ness would have to be rechecked
on each assembly.
1. Delete or comment out the patch.
1. Protect the patch by a test for the presence of the taxon:

    if worms.maybeTaxon('Biota') != None:
        a.same(worms.taxon('Biota'), ott.taxon('life'))

I've chosen the last approach since it seems most robust.  (e.g. in
the future someone might "fix" the WoRMS import by changing life back
to Biota.)

### Spelling of Cyrto-hypnum lepidoziaceum

    ** gbif-11 seemed to accept synonym_of(taxon('Cyrto-Hypnum lepidoziaceum'), taxon('Cyrto-hypnum lepidoziaceum'), spelling variant, otc:26), but we couldn't confirm

This comes from a `synonym_of` patch (version 3).  Neither name occurs
in GBIF, so the patch cannot be effected, but that should be OK.  The
message indicates an error in the logic (`proclaim` and `.check` in
sequence ought to return the same value) but I can't find it.  However, the following

    bin/investigate "Cyrto-Hypnum lepidoziaceum"

indicates that the name isn't in GBIF at all, only in IRMNG.  To make
these _Cyrto-hypnum_ patches a bit more robust, I decided to move them
to `amendments.py` and perform them on `ott` instead of `gbif`.  This
fixes the latent problem that the spelling correction was being
applied to GBIF but not to IRMNG.

### No such taxon: Myrmecia

    ** No such taxon: Myrmecia in Microthamniales (gbif)

This comes from 

    a.same(gbif.taxon('Myrmecia', 'Microthamniales'),
           ott.taxon('Myrmecia', 'Microthamniales'))

in adjustments.py.  In `r/gbif-HEAD/resource/taxonomy.tsv'` we see

    1317709	|	4342	|	Myrmecia	|	genus	|	
    2638661	|	6784730	|	Myrmecia	|	genus	|	

    1360	|	332	|	Microthamniales	|	order	|	

so it's strange that the patch doesn't work.  Using the OTT taxonomy
browser, we find that _Myrmecia_ (genus in order Microthamniales) is
OTT 739611 with no GBIF link, but it has a species _Myrmecia
astigmatica_ that connects GBIF and NCBI.  Visiting the GBIF page, we
find it's in family Trebouxiaceae, order Trebouxiales, class
Trebouxiophyceae, phylum Chlorophyta.  So we want to replace
Microthamniales with one of these ancestors, but which one is best?
NCBI and IRMNG both have Trebouxiophyceae, so I would say that should
be pretty stable.

Ergo:

    a.same(gbif.taxon('Myrmecia', 'Trebouxiophyceae'),
           ott.taxon('Myrmecia', 'Trebouxiophyceae'))

But this doesn't work.  It seems NCBI has two _Myrmecias_ in
Trebouxiophyceae.  So disambiguating by membership is probably better
than by ancestry.  It turns out the "right" one in OTT (via NCBI), the
one with almost all of the species, is the one that aligns with GBIF
in 3.0.  We can pick a descendant pretty much arbitrarily, but
_Myrmecia irregularis_ looks good since it's in NCBI, GBIF, and IRMNG.  So:

    a.same(gbif.taxonThatContains('Myrmecia', 'Myrmecia irregularis'),
           ott.taxonThatContains('Myrmecia', 'Myrmecia irregularis'))




### No expansum containing Penicillium expansum

    ** No such taxon: Penicillium containing Penicillium expansum (gbif)

    a.same(gbif.taxonThatContains('Penicillium', 'Penicillium expansum'),
           ott.taxonThatContains('Penicillium', 'Penicillium expansum'))

The intent here seems to be to address some ambiguity over the genus
name _Penicillium_.

Go to _Penicillium expansum_ in OTT 3.0 browser, find sources
if:159382 (ncbi:27334, irmng:11314226) - so it's not in GBIF at all.
This error must have been present in the 3.0 assembly as well.
Ideally what we want is some species belongs to the appropriate NCBI,
GBIF, and IRMNG genera.  The genus has 3000 children, and most seem to
be NCBI only, so using the OTT browser to find such a species isn't
going to work.  But a specially crafted grep does the trick:

    grep "Penicillium .*species.*ncbi.*gbif.*irmng" r/ott-HEAD/source/taxonomy.tsv

The results are _Penicillium inflatum, Penicillium diversum,_ and
_Penicillium dupontii_, all with parent _Penicillium_.  So we ought to
be able to pick any of the three.  But a new assembly with _inflatum_
yields

    ** No such taxon: Penicillium containing Penicillium inflatum (gbif)

Unfortunately, none of these species is in genus _Penicillium_ in
GBIF.  _Penicillium_ has only one species in GBIF, _Penicillium
salamii_.  That's also in NCBI, so let's use it.

    a.same(gbif.taxonThatContains('Penicillium', 'Penicillium salamii'),
           ott.taxonThatContains('Penicillium', 'Penicillium salamii'))

### Pulmonata ambiguous

    ** Ambiguous taxon name: Pulmonata (ott)
    **   Pulmonata (order worms:382238) = (Pulmonata =worms:382238+4) in Mollusca
    **   Pulmonata (infraclass worms:103) = (Pulmonata =worms:103+5) in Mollusca

Looking at the WoRMS page for Pulmonata we see

>Order [unassigned] Pulmonata (temporary name)

making me wonder if I'm interpreting '[unassigned]' correctly - the
script currently trims off the '[unassigned]'.  I think I'll disable
this feature.  There are 35 of these (`grep "unassigned"
r/worms-20170513/source/digest/*.csv | wc`).  Now I guess what it
means is "members have not yet been assigned to a parent taxon".

Removing the logic in `process_worms.py` to trim "[unassigned]" off
beginning of names.

The meaning now sounds similar to _incertae sedis_, so adding
'unassigned' to incertae_sedisRegex in Taxonomy.java as another way to
express _incertae sedis_.

### Bad interaction between id= and synonym_of

    ** gbif-11 seemed to accept synonym_of(taxon('None', id=8101279), taxon('Rotalia', 'Foraminifera'), synonym, otc:50), but we couldn't confirm

Using ids in patches is generally a bad idea.  In this case, there is
a test following the patch to make sure that the patch got made, and
it's failing because the taxon can't be found under the name used to
find it before the patch.

This is definitely a tangle.  The two synonym patches are trying to
get rid of redundant Rotalites records, converging on a single right
one.  There is a list of these Rotalites records in the comments, but
they're obsolete.

As I remember, this patch is important, because without it, these
records act as magnets for alignments of other sources later on.

I checked id 8101279 (Rotalites Lamarck, 1801) on the GBIF web site,
and it seems has been removed, but it is still in the dump we're
using, so we need to deal with it somehow.

GBIF currently has a foram 8829867 with no authority.  Hmm.  See
name-research.txt for an inventory.

This may be a case where the v3 patch language is not precise enough
to do the trick.  That's not inherent in v3.  My repair here is to
revert to the v2 language and do the repair at a low level.

    rotalia = gbif.taxon('Rotalia', 'Foraminifera')
    foram = gbif.taxon('Foraminifera')
    for lites in gbif.lookup('Rotalites'):
        if lites.name == 'Rotalites' and lites.descendsFrom(foram):
            rotalia.absorb(lites, 'proparte synonym', otc(49))
    # Blow away distracting synonym
    foram.notCalled('Rotalites')

Ugly, but I don't see how else to do it given that there are multiple
GBIF records that are difficult to distinguish, and with volatile
record ids and placement.

### Problems with edits/ v1 patches

Skipping for now.

### Quiscalus

    ott.taxon('Icteridae').take(ott.taxon('Quiscalus', 'Fringillidae'))
    ** No such taxon: Quiscalus in Fringillidae (ott)

The change has been effected in some source.  We can either delete the
patch or make it more robust.  This might be a good place to use
`taxonThatContains` instead of `taxon`.

    ott.taxon('Icteridae').take(ott.taxonThatContains('Quiscalus', 'Quiscalus mexicanus'))

### Chlamydotomus

What a headache.  There are extensive comments in adjustments.py.

    ** No such taxon: Chlamydotomus containing Chlamydotomus beigelii (ott)

I think I would just disable the patch.  Too fussy.


### Oxymonadida gone

What happened to Oxymonadida ?

    https://groups.google.com/d/msg/opentreeoflife/a69fdC-N6pY/y9QLqdqACawJ

    ** No such taxon: Oxymonadida in Eukaryota (ott)

    tax = ott.taxonThatContains('Excavata', 'Euglena')
    if tax != None:
        tax.take(ott.taxon('Oxymonadida','Eukaryota'))

In the OTT 3.0 browser, it shows up as a barren order, deep in Excavata (parent Trimastix).
So something is already wrong.

Oxymonadidae, a child of Oxymonadida according to Wikipedia, is a
sister in OTT 3.0.  It looks as if Wikipia Oxymonadidae is pretty much
the same as Trimastix (which everyone has).

Since Laura and Dail don't give references, and I don't want to do a
lot of research, and I'm guessing that SILVA and NCBI are in good
shape now (the email preceds our adoption of SILVA), I think it's best
to just delete (or comment out) this patch.

### No taxon found in ott with this name or id: Araripia

    ara = ott.taxon('Araripia')
    ** No taxon found in ott with this name or id: Araripia

This is just a dumb mistake.  That should be maybeTaxon.

    ara = ott.maybeTaxon('Araripia')
    if ara != None: ara.extinct()

### Couldn't confirm

    ** union seemed to accept synonym_of(taxon('Cyrto-Hypnum lepidoziaceum'), taxon('Cyrto-hypnum lepidoziaceum'), spelling variant, otc:26), but we couldn't confirm
    ** union seemed to accept synonym_of(taxon('Cyrto-Hypnum brachythecium'), taxon('Cyrto-hypnum brachythecium'), spelling variant, otc:35), but we couldn't confirm

Working on this, haven't figured it out yet.

### Name is x, but expected y

    ** ncbi:730 name is [Haemophilus] ducreyi, but expected Haemophilus ducreyi
    ** ncbi:738 name is [Haemophilus] parasuis, but expected Haemophilus parasuis
    ** ncbi:40520 name is Blautia obeum, but expected [Ruminococcus] obeum

This is due to a bit of extra checking in some ad hoc id assignment
logic.  These warnings are all benign.  The fix is to change these from
double-`*` messages to single-`*` messages.

### No taxon found in ott with this name or id

    ** No taxon found in ott with this name or id: Cyrto-Hypnum lepidoziaceum
    ** No taxon found in ott with this name or id: Cyrto-hypnum lepidoziaceum
    ** union seemed to enact synonym_of(taxon('Cyrto-Hypnum lepidoziaceum'), taxon('Cyrto-hypnum lepidoziaceum'), spelling variant, otc:26), but we couldn't confirm (None)
    ** No taxon found in ott with this name or id: Cyrto-Hypnum brachythecium
    ** No taxon found in ott with this name or id: Cyrto-hypnum brachythecium
    ** union seemed to enact synonym_of(taxon('Cyrto-Hypnum brachythecium'), taxon('Cyrto-hypnum brachythecium'), spelling variant, otc:35), but we couldn't confirm (None)

These names are gone from the sources and the taxonomy, so they don't
matter.  To make the errors go away, delete the patches (or comment
them out - in case the problem returns later on).

## Testing

### Building OTT is a test

Look for '\*\*' lines in `transcript.out`.  Compare the current
transcript to the one for the previous OTT build to see whether an
error is old or new.  A '\*\*' line might be in the previous build, in
which case it probably can be ignored.

Check the `deprecated.tsv` file.  If it has a very large number of
`id-retired` lines (over 20??), or if the contents follow some
pattern, something might be wrong (such as a division alignment).

### Inclusion tests

A set of taxon inclusion tests runs every time the taxonomy is built.
It can also be run directly from the shell, as
`bin/jython util/check_inclusions.py`.  (Optional arguments: list of tests,
default `inclusions.csv`, and taxonomy, default `r/ott-NEW/source/`.)

The inclusions tests in the reference-taxonomy repository are more
current than the ones in the germinator repository.  The list should be
copied from the one repo to the other from time to time.

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
Tabs are helpful in picking out a genus record (as opposed to all
species in the genus).  `^` is useful for lookup by id.  E.g.

    grep "Peripatus dominicae" r/ott-NEW/source/taxonomy.tsv 

`log.txt` shows alignment, merge, and other events connected with
certain names.  To force logging for a name, if it's not already being
logged, add it to the `names_of_interest` list in assemble_ott.py.

    grep -A 2 Campanella r/ott-NEW/source/choices.tsv 

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

## Particular alignment analyses

When alignment is overeager, or when it fails to happen at all, it is
necessary to first determine what the correct state of affairs is,
based on external information.  Only then can a repair be instituted.

Here are notes that I've taken in tracking down particularly
troublesome cases: [Research on particular names](../names-research.txt)
