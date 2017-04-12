# Open Tree of Life reference taxonomy version 2.9

Version 2.9 draft 12 was generated on 12 October 2015.  Draft 12 is the final draft in the 2.9 series of drafts.

## Download

[Download](http://files.opentreeoflife.org/ott/ott2.9/ott2.9.tgz) (gzipped tar file, 91 Mbyte) 

## Contents
All files are encoded UTF-8.  For documentation about file formats, see [the documentation in the reference taxonomy
wiki](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format),
on github.

**taxonomy.tsv**: The file that contains the taxonomy.

**synonyms.tsv**: The list of synonyms.

**conflicts.tsv**: Report on taxa from input taxonomies that are
  hidden because they are paraphyletic with respect to a higher
  taxon from a higher priority input taxonomy.  Number in first column is depth in taxonomic tree of
  nearest common ancestor of its children.

**deprecated.tsv**: List all taxon ids occurring in phylesystem studies that have been deprecated since previous version. 

**log.tsv**: Debugging information related to homonym resolution.

**version.tsv**: The version of OTT.

**forwards.tsv**: Forwarding pointers - a list of OTT ids that are
  retired and should be replaced by new ones (usually due to
  'lumping')

**weaklog.csv**: internal debugging tool

## Build script

The reference taxonomy is an algorithmic combination of several
source taxonomies.  For code,
see <a href="https://github.com/OpenTreeOfLife/reference-taxonomy">the
source code repository</a>.
Version 2.9 draft 12 was generated using 
<a href="https://github.com/OpenTreeOfLife/reference-taxonomy/commit/a58f25af5a35c988684979c87f156a5e9364c54d">commit a58f25a</a>.</p>

## Sources

Any errors in OTT
should be assumed to have been introduced by the Open Tree of Life 
project until confirmed as originating in the source taxonomy.

Download locations are for the particular versions used to construct
OTT 2.9.  For new work, current versions of these sources should be
retrieved.

1.  Taxonomy from: 
    DS Hibbett, M Binder, JF Bischoff, M Blackwell, et al. 
    A higher-level phylogenetic classification of the <i>Fungi</i>.
    [Mycological Research</i> <b>111</b>(5):509-547, 2007](http://dx.doi.org/10.1016/j.mycres.2007.03.004).
    Newick string with revisions
    archived at [http://figshare.com/articles/Fungal\_Classification\_2015/1465038](http://figshare.com/articles/Fungal_Classification_2015/1465038).
    <br />
    Download location: [http://purl.org/opentree/ott/??TBD??](http://purl.org/opentree/ott/??TBD??)

1.  Taxonomy from: SILVA 16S ribosomal RNA database, version 115.
    See: Quast C, Pruesse E, Yilmaz P, Gerken J, Schweer T, Yarza P, Peplies J,
    Gl&ouml;ckner FO (2013) The SILVA ribosomal RNA gene database project:
    improved data processing and web-based tools. 
    [Nucleic Acids Research</i> 41 (D1): D590-D596](http://dx.doi.org/10.1093/nar/gks1219).
    Web site: [http://www.arb-silva.de/](http://www.arb-silva.de/).
    <br />
    Download location: [ftp://ftp.arb-silva.de/release\_115/Exports/tax\_ranks\_ssu\_115.csv](ftp://ftp.arb-silva.de/release_115/Exports/tax_ranks_ssu_115.csv).

1.  Index Fungorum.
    Download location: derived from database query result files provided by Paul
    Kirk, 7 April 2014 (personal communication).
    Web site: [http://www.indexfungorum.org/](http://www.indexfungorum.org/).
    <br />
    Download location (converted to OTT format): [http://purl.org/opentree/ott/??TBD??](http://purl.org/opentree/ott/??TBD??).

1.  Taxonomy from:
    Sch&auml;ferhoff, B., Fleischmann, A., Fischer, E., Albach, D. C., Borsch,
    T., Heubl, G., and M&uuml;ller, K. F. (2010). Towards resolving Lamiales
    relationships: insights from rapidly evolving chloroplast
    sequences. 
    [<i>BMC evolutionary biology</i> 10(1), 352.](http://dx.doi.org/10.1186/1471-2148-10-352).
    Manually transcribed from the paper and converted to OTT format.
    <br />
    Download location: [http://purl.org/opentree/ott/ott2.8/inputs/lamiales-20140118.tsv](http://purl.oeg/opentree/ott/ott2.8/inputs/lamiales-20140118.tsv)

1.  World Register of Marine Species (WoRMS) - harvested from web site using web API over several days ending around 1 October 2015.
    [http://www.marinespecies.org/aphia.php](http://www.marinespecies.org/aphia.php)

1.  NCBI Taxonomy, from the 
    [US National Center on Biotechnology Information](http://www.ncbi.nlm.nih.gov/).
    Web site: [http://www.ncbi.nlm.nih.gov/Taxonomy/](http://www.ncbi.nlm.nih.gov/Taxonomy/).
    Current version download location:
    [ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz](ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz)
    <br />
    For OTT 2.9 we used a version downloaded from NCBI on 6 October 2015.
    Download location: [http://purl.org/opentree/ott/??TBD??](http://purl.org/opentree/ott/??TBD??).
  </li>

1.  GBIF Backbone Taxonomy, from the 
    [Global Biodiversity Information facility](http://www.gbif.org/).
    Current version download location: 
    [http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c](http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c).
    <br />
    We used a version dated 2013-07-02.
    Download location: [http://purl.org/opentree/gbif-backbone-2013-07-02.zip](http://purl.org/opentree/gbif-backbone-2013-07-02.zip).

1.  [Interim Register of Marine and Nonmarine Genera (IRMNG)](http://www.obis.org.au/irmng/), from CSIRO.
    Current version download location:
    [http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip](http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip).
    <br />
    We used a version dated 2014-01-31.  Download location:
    [http://purl.org/opentree/ott/ott2.8/inputs/IRMNG\_DWC-2014-01-30.zip](http://purl.org/opentree/ott/ott2.8/inputs/IRMNG_DWC-2014-01-30.zip).
 
1.  Taxon identifiers are carried over from [OTT 2.8](http://purl.org/opentree/ott/ott2.8/) when possible
 
It has been requested that we relay the following statement:

> REUSE OF IRMNG CONTENT:
> IRMNG (the Interim Register of Marine and Nonmarine Genera) is assembled, with permission, from a range of third party data sources, certain of which permit data reuse only under specific conditions. In particular, for data originating from the Catalogue of Life (CoL), please refer to the relevant terms and conditions for reuse of CoL data as available at [http://www.catalogueoflife.org/content/terms-use](http://www.catalogueoflife.org/content/terms-use), and for data originating from the World Register of Marine Species (WoRMS) refer the paragraph "Terms of Use and Citation" at [http://www.marinespecies.org/about.php](http://www.marinespecies.org/about.php). The compilers of IRMNG accept no liability for any reuse of IRMNG content by downstream users which may be construed by the original data providers to violate their publicly available conditions of use.

The Open Tree Taxonomy does not reproduce its sources in their
entirety or in their original form of expression, but only uses
limited information expressed in them. See "[Scientific names of
organisms: attribution, rights, and licensing](http://dx.doi.org/10.1186/1756-0500-7-79)" ([http://dx.doi.org/10.1186/1756-0500-7-79](http://dx.doi.org/10.1186/1756-0500-7-79))
regarding use of taxonomic information and attribution.

## Priority

Where taxonomies conflict regarding taxon relationships, they are
resolved in favor of the higher priority taxonomy.  The priority
ordering is as given above, with the following exceptions:

The non-Fungi content of Index Fungorum is separated from the Fungi
content and given a priority lower than NCBI but higher than GBIF.

The non-Malacostraca content of WoRMS is separated from the
Malacostraca content and given a priority lower than NCBI but higher
than GBIF.

## Release notes

Changes since OTT 2.8 (a.k.a 2.8draft5) which was built on 11 June 2014:

Statistics:

* Identifiers: 3528349
* Visible: 2628944
* Synonyms: 867366
* In deprecated file (used in phylesystem): 2451
* In deprecated file (used in synthesis): 368
* Source taxa dissolved due to conflict (conflicts.tsv): 1054

New [flags](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Taxon-flags):

* unplaced - similar to incertae\_sedis (this means a child of an
  inconsistent taxon, where t is inconsistent if it occurs in a
  lower-priority taxonomy but is inconsistent with the higher-priority
  taxonomies.  'tattered' is now deprecated)
* unplaced\_inherited - descends from a placed taxon.
* inconsistent (formerly 'tattered') - taxon in lower priority 
  taxonomy that is inconsistent (see above).
* merged - this taxon was consistent with another and got folded 
  into it.  Taxon is hidden, children aren't.  Taxon may be
  revived if it's learned later that the it is actually different.
* was\_container - treat same as incertae\_sedis, merged, and
  inconsistent - the 'taxon' was formerly a 'bucket' but is now empty and is
  preserved as a placeholder.
* extinct - replaces extinct_direct.
* major\_rank\_conflict - replaces major\_rank\_conflict\_direct.
* incertae\_sedis - (former) child of an incertae sedis container.
* sibling\_lower is deprecated, that information is not recorded (but you can
  always tell, just by looking at ranks of the siblings).  sibling\_higher
  is retained.
* Deprecated: tattered, tattered_inherited

Specific content changes (inputs):

* Added WoRMS
* Updated Hibbett 2007 from [http://figshare.com/articles/Fungal\_Classification_2015/1465038](http://figshare.com/articles/Fungal_Classification_2015/1465038)
* Minor IF update (to 7 April 2014 and modified processing software)
* Minor GBIF update (same origin content, modified processing, much faster)
* NCBI update (6 October 2015)
* Fixes for many bugs reported in feedback and reference-taxonomy repos (see milestones)

Generic content changes (processing):

* 'Lumping' is now allowed more promiscuously than before.  E.g. if NCBI
  has names A and B with B a synonym of A, and GBIF has A and B as separate 
  taxa, then GBIF's A and B will both map to NCBI's A.
* New file forwards.tsv gives replacement ids for some ids that no
  longer exist in the taxonomy.  E.g. if A and B were separate in an earlier
  version of OTT, and 'lumped' in this version, then there will be
  a row in forwards.tsv mapping B's old id to A's id.
* The "unique names" column shows the highest distinguishing taxon, e.g. "Morganella
  (genus in kingdom Fungi)" instead of the lowest "Morganella (genus in family
  Agaricaceae)"
* Somewhat more informative deprecated.tsv
* Deprecated.tsv file is now restricted to taxa mentioned in phylesystem,
  and includes not only deprecated ids but also newly hidden ids (those
  that were not hidden in 2.8, but are hidden now)
* As a heuristic, taxa that come *only* from PaleoDB are marked extinct
* 'skeleton' feature replaces 'pinning' for homonym separation (see
  tax/skel/ for list of barrier nodes)

## Previous versions

See <a href="http://files.opentreeoflife.org/ott/">http://files.opentreeoflife.org/ott/</a>
