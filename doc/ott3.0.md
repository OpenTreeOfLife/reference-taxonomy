* Removed Viruses
* Small improvements to treatment of containers (X incertae sedis, samples)

Note that SILVA precedes Hibbett in the load order


# Open Tree of Life reference taxonomy version 3.0

Version 3.0 draft 6 was generated on 26 February 2017.

## Download

[Download](https://files.opentreeoflife.org/ott/ott3.0/ott3.0.tgz)

## Major changes since OTT 2.10

* Updated to NCBI Taxonomy dated 9 November 2016.
* Updated to GBIF backbone dated 29 July 2016.  Unfortunately, as a
  consequence, about 200 species, mostly birds, have been renamed with
  no known link to the previous name.  (Example: Collocalia ocista,
  formerly known as Aerodramus ocistus) This means occurrences of
  these species in previously curated studies will not be recognized
  in synthesis runs.  It is hoped that this is a temporary problem.
* Cnidaria now gets its taxonomy from WoRMS instead of NCBI.
* Removed Viruses
* A number of taxa whose OTT id unnecessarily changed in the past have
  had their OTT ids restored, with the newer id as an id alias.
  This change should not have any ill effects for clients that are
  aware of id aliases (forwards.tsv).

## Statistics

* OTT identifiers ('taxa'): 3594550
* Visible: [TBD]
* Synonyms: 1842403 (big increase)
* Deprecated/hidden ids occurring in phylesystem: total 3686,
  but only 586 if lumped ids are excluded
* Deprecated/hidden ids occurring in studies in synthesis: total 1097,
  but only 118 if lumped ids are excluded
* Source taxa dissolved due to conflict (conflicts.tsv): 1171

## Contents of download

All files use UTF-8 character encoding.  For documentation about file formats, see [the documentation in the reference taxonomy
wiki](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format),
on github.

**taxonomy.tsv**: The file that contains the taxonomy.

**synonyms.tsv**: The list of synonyms.

**forwards.tsv**: Aliases ('forwarding pointers') - a list of OTT ids that are
  retired and should be replaced by new ones (usually due to
  'lumping')

**conflicts.tsv**: Report on taxa from input taxonomies that are
  hidden because they are paraphyletic with respect to a higher
  taxon from a higher priority input taxonomy.
  Since this file is mainly for debugging purposes I change
  it from time to time without notice.  The format used in 3.0
  is very different from that used in 2.10.

**deprecated.tsv**: List of all taxon ids occurring in phylesystem
  studies that have been deprecated or suppressed since previous version.  This
  includes ids that no longer identify any taxon, those that have been
  'lumped' with other ids, and those for taxa that are suppressed in
  synthesis but weren't suppressed in the previous version.

**version.tsv**: The version of OTT.

**transcript.out**: Console debugging output generated during the taxonomy build process.

**log.tsv**: Debugging information related to homonym resolution.

**otu_differences.tsv**: list of differences with OTT 2.10, restricted
  to ids used in phylesystem

There are some new .json files in the dump containing metrics, created for
the purpose of the taxonomy method writeup.  Not documented as of
this writing.

## Build script

The reference taxonomy is an algorithmic combination of several
source taxonomies.  For code,
see <a href="https://github.com/OpenTreeOfLife/reference-taxonomy">the
source code repository</a>.
Version 3.0 draft 6 was generated using 
<a href="https://github.com/OpenTreeOfLife/reference-taxonomy/commit/fc5cb5c2749e9e986749281a3284d2c1df42b58d">commit fc5cb5c</a>.</p>

## Sources

Any errors in OTT
should be assumed to have been introduced by the Open Tree of Life 
project until confirmed as originating in the source taxonomy.

Download locations are for the particular versions used to construct
OTT 3.0.  For new work, current versions of these sources should be
retrieved.

1.  Curated additions from the Open Tree amendments-1 repository, commit [bcafdea](https://github.com/OpenTreeOfLife/amendments-1/commit/bcafdea7f2e9231274cae2df2beed7d732ca46c5).  These taxa are added during OTU mapping using the curator application.

1.  Taxonomy from: 
    DS Hibbett, M Binder, JF Bischoff, M Blackwell, et al. 
    A higher-level phylogenetic classification of the <i>Fungi</i>.
    [Mycological Research</i> <b>111</b>(5):509-547, 2007](http://dx.doi.org/10.1016/j.mycres.2007.03.004).
    Newick string with revisions
    archived at [http://figshare.com/articles/Fungal\_Classification\_2015/1465038](http://figshare.com/articles/Fungal_Classification_2015/1465038).
    <br />
    Download location: [https://github.com/OpenTreeOfLife/reference-taxonomy/tree/ott2.10draft11/feed/h2007](https://github.com/OpenTreeOfLife/reference-taxonomy/tree/ott2.10draft11/feed/h2007)

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
    Download location (converted to OTT format): [https://files.opentreeoflife.org/fung/fung-9/fung-9-ot.tgz](https://files.opentreeoflife.org/fung/fung-9/fung-9-ot.tgz).

1.  Taxonomy from:
    Sch&auml;ferhoff, B., Fleischmann, A., Fischer, E., Albach, D. C., Borsch,
    T., Heubl, G., and M&uuml;ller, K. F. (2010). Towards resolving Lamiales
    relationships: insights from rapidly evolving chloroplast
    sequences. 
    [<i>BMC evolutionary biology</i> 10(1), 352.](http://dx.doi.org/10.1186/1471-2148-10-352).
    Manually transcribed from the paper and converted to OTT format.
    <br />
    Download location: [http://purl.org/opentree/ott/ott2.8/inputs/lamiales-20140118.tsv](http://purl.org/opentree/ott/ott2.8/inputs/lamiales-20140118.tsv)

1.  [World Register of Marine Species (WoRMS)](http://www.marinespecies.org/aphia.php) - harvested from web site using web API over several days ending around 1 October 2015.
    Download location: [https://files.opentreeoflife.org/worms/worms-1/worms-1-ot.tgz](https://files.opentreeoflife.org/worms/worms-1/worms-1-ot.tgz)

1.  NCBI Taxonomy, from the 
    [US National Center on Biotechnology Information](http://www.ncbi.nlm.nih.gov/).
    Web site: [http://www.ncbi.nlm.nih.gov/Taxonomy/](http://www.ncbi.nlm.nih.gov/Taxonomy/).
    <br />
    We used a version dated 9 November 2016.
    Archived location: [https://files.opentreeoflife.org/ncbi/ncbi-20151006/ncbi-20151006.tgz](https://files.opentreeoflife.org/ncbi/ncbi-20161109/ncbi-20161109.tgz).
    <br />
    Current version download location:
    [ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz](ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz)

1.  GBIF Backbone Taxonomy, from the 
    [Global Biodiversity Information facility](http://www.gbif.org/).
    <br />
    We used a version dated 2016-07-29.
    Download location: [http://files.opentreeoflife.org/gbif/gbif-20160729/gbif-201609729.zip](http://files.opentreeoflife.org/gbif/gbif-20160729/gbif-201609729.zip).
    <br />
    Current version download location (unverified):
    [http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c](http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c).

1.  [Interim Register of Marine and Nonmarine Genera (IRMNG)](http://irmng.org/).
    <br />
    We used a version dated 2014-01-31.  Download location:
    [http://purl.org/opentree/ott/ott2.8/inputs/IRMNG\_DWC-2014-01-30.zip](http://purl.org/opentree/ott/ott2.8/inputs/IRMNG_DWC-2014-01-30.zip).
 
1.  Taxon identifiers are carried over from [OTT 2.10](http://files.opentreeoflife.org/ott/ott2.10/) when possible
 
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
content and given a priority lower than NCBI and GBIF.

The non-Malacostraca, non-Ctenophora content of WoRMS is separated from the
Malacostraca and Ctenophora content and given a priority lower than NCBI but higher
than GBIF.

## Previous versions

See <a href="http://files.opentreeoflife.org/ott/">http://files.opentreeoflife.org/ott/</a>
