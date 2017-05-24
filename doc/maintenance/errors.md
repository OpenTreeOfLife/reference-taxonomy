# Finding and fixing errors

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
1. A patch can fail as a result of a repair in a source taxonomy.

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
determine where it is, we can use `bin/lineage`:

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
future reference.  This may not be the best strategy - maybe the
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

Turns out there is only one child (other than a bunch of incertae
sedis that we don't care much about), Stylommatophora.  Why did NCBI
Stylommatophora not align with WoRMS Stylommatophora? - that would
have fixed this problem.  Stylommatophora is not in our WoRMS digest
at all - the whole Stylommatophora subtree is missing, as if it
weren't a child of Pulmonata at all.

It's present in our older WoRMS dump, but not in the new one.  This is
troubling... it means either the WoRMS API doesn't work, or there is a
bug in the harvesting or import code.

To test the WoRMS API, we need to write a little python script, since
the API uses SOAP and SOAP requests are difficult to cobble together.

    from SOAPpy import WSDL
    DEFAULT_PROXY = 'http://www.marinespecies.org/aphia.php?p=soap&wsdl=1'
    WORMSPROXY = WSDL.Proxy(DEFAULT_PROXY)
    taxon_id = 103  # Pulmonata
    children = WORMSPROXY.getAphiaChildrenByID(taxon_id)
    for child in children:
        print child.AphiaID, child.scientificname, child.status

The output is:

    382238 [unassigned] Pulmonata temporary name
    1770 Archaeopulmonata alternate representation
    197 Basommatophora unaccepted
    412657 Eupulmonata alternate representation
    446 Gymnomorpha unaccepted
    382243 Hygrophila accepted
    1772 Systellommatophora accepted

and Stylommatophora is not in the list, even though it is on the
Pulmonata page at the WoRMS site.

Turns out there is REST API.  Try that:

    curl http://www.marinespecies.org/rest/AphiaChildrenByAphiaID/103

Same result.

So the WoRMS API is the culprit, not our harvest or import code.  I
have sent email to WoRMS and we'll see what they say.  In the meantime
- we still need a fix.  The most direct approach is a patch
(`amendments.py`) that makes NCBI Stylommatophora a child of WoRMS
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

### Update

Heard back from WoRMS.  The reason land snails are missing is that
they are not marine, and by default the API only tells you about
marine organisms.  I need to re-run with `?marine_only=false`.

In the meantime, the above patch should help.
