<# Finding and fixing errors

See also:

 * [GBIF 2016 import case study](gbif-2016-case-study.md)
 * [Addressing feedback issues](curation.md)

## What can go wrong

Many kinds of things can go wrong.

1. A source taxonomy can contain an error (taxon in the wrong place)
1. There can be a spurious alignment, or incorrect equation of two taxa
1. An alignment can be missed, leading to a duplication
1. A merge can fail, leading to a conflict and an absent higher taxon
1. Assembly logic can be wrong, usually leading to a large number of
   similar errors
1. A patch can fail as a result of a repair to the source taxonomy.

Errors detected during OTT assembly are written to
`r/ott-NEW/source/debug/transcript.out` on lines beginning `**`.  (But
some of those lines might be innocent, so attempting to get rid of all
such messages may be futile, or worse.)

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

## Procedure

After each assembly run (`make ott`), the transcript
(`debug/transcript.out`) should be checked for errors.  Errors are
indicated with two asterisks at the beginning of the line (`**`).

I often check the inclusions tests first.  These show up at the very
end.

When building `ott3.1`, the following showed up:

    ** There is no taxon named Enidae (id 591146) in Gastropoda
       There is a Enidae (division Mollusca) not in Gastropoda; its id happens to be 591146
       Id 591146 belongs to (Enidae 591146=ncbi:145762?)

First we need to know where Enidae belongs.  Wikipedia is very clear
that it should be in Gastropoda.

To understand what went wrong, we need to know where Enidae came from,
and where it ended up in the taxonomy, if not Gastropoda.  There are
utilities for this.  First, where it comes from:

    bin/investigate Enidae
    r/ncbi-HEAD/resource/taxonomy.tsv:145762	|	145342	|	Enidae	|	family
    r/gbif-HEAD/resource/taxonomy.tsv:2297508	|	1456	|	Enidae	|	family
    r/irmng-HEAD/resource/taxonomy.tsv:114831	|	10595	|	Enidae	|	family
    r/ott-PREVIOUS/resource/taxonomy.tsv:591146	|	639691	|	Enidae	|	family	|	ncbi:145762,gbif:2297508,irmng:114831

This is apparently a well-known family, not something obscure.  To
determine where it is in the draft OTT, we can use `bin/lineage`:

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
suppress these from what users see of OTT, yet preserve them for
future reference.  This may not be the best strategy - maybe they
should be omitted entirely.  That would be a simple change to the
WoRMS conversion script, and it ought to solve this problem.  However,
let's look deeper.

You might think we could simply 'unhide' Eupulmonata, but that might
give a screwy classification.  If you look at the WoRMS record for
Eupulmonata you find that they think it is paraphyletic, and their
classification of Pulmonata does not include it; Stylommatophora is a
direct child of Pulmonata.  That is why Eupulmonata is terminal and
not accepted.  The correct approach, to bring OTT in line with WoRMS
and WoRMS's priority status in molluscs, is to find all the NCBI
children of Eupulmonata and place them in the correct WoRMS order
inside Pulmonata.

It turns out there is only one child (other than a bunch of incertae
sedis that we don't care much about), Stylommatophora.  Why did NCBI
Stylommatophora not align with WoRMS Stylommatophora? - that would
have fixed this problem.  The reason is that Stylommatophora is not in our WoRMS digest
at all - the whole Stylommatophora subtree is missing, as if it
weren't a child of Pulmonata at all.

It's present in our older WoRMS dump, but not in the latest one (May
2017).  This is because of a bug in the new WoRMS refresh script.
That bug can be fix, but in the meantime it is instructive to see if
there is another way to fix this.  The most direct approach is a patch
(in `amendments.py`) that makes NCBI Stylommatophora a child of WoRMS
Pulmonata:

    \# 2017-05-29 Work around bug in WoRMS API
    proclaim(ott, has_parent(taxon('Stylommatophora', 'Gastropoda'),
                             taxon('Pulmonata', 'Gastropoda'),
                             otc(NNN)))

where NNN is some integer that doesn't occur in any other `otc(...)`
expression.  `otc(...)` is currently use only in `adjustments.py` and
`amendments.py`.  (The idea is that eventually the otc numbers will
end up in sources lists in OTT, making it possible to drill down to
the text of the patch and any nearby comments.  This is not yet
implemented.)

See [the section on patch writing](patch.md) for information on 
`proclaim`, `has_parent`, and so on.


## Updating sources

What can go wrong?

- changes to source format (e.g. GBIF 2013 had a canonicalName column (without authority),
  but GBIF 2016 has scientificName (with authority))
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
default `inclusions.csv`, and taxonomy, default `r/ott-NEW/source/`.)

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
troublesome cases.

* [Research on particular names](../names-research.txt)

## Older documentation (user documentation for smasher):

* [Scripting feature](https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/doc/scripting.md)
