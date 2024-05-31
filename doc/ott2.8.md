# Open Tree of Life reference taxonomy version 2.8

Version 2.8 (also known as version 2.8 draft 5) was generated on 11 June 2014.

## Download

[Download](https://purl.org/opentree/ott/ott2.8/ott2.8.tgz) (gzipped tar file, 119 Mbyte) 

## Contents
All files are encoded UTF-8.  For detailed documentation about file formats, see [documentation in the reference taxonomy
wiki](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format),
on github.

**conflicts.tsv**: Report on taxa that are hidden because they are paraphyletic with respect to a higher priority taxon.  Number at beginning is height in taxonomic tree of nearest common ancestor with priority taxon that 'steals' one or more children.

**deprecated.tsv**: List all of taxa that have been deprecated since previous version. 

**differences.tsv:** Summary of differences between ott2.8 and ott2.7.

**hidden.tsv**: Report on 'hidden' taxa (incertae sedis and other suppressed taxa). Columns are OTT id, name, source taxonomy and id, containing major group, and flags (reasons for hiding).

**log.tsv**: Debugging information related to homonym resolution.

**taxonomy.tsv**: The file that contains the taxonomy. Columns are separated by vertical bars ('|'). Columns are:

  * uid - an identifier for the taxon, unique within this file. Should be native accession number whenever possible. Usually this is an integer, but it need not be. 
  * parent_uid - the identifier of this taxon's parent, or the empty string if there is no parent (i.e., it's a root).
  * name - arbitrary text for the taxon name; not necessarily unique within the file.
  * rank, e.g. species, family, class. If no rank is assigned, or the rank is unknown, contains "no rank".
  * sourceinfo - the source taxonomies providing the taxon.
  * uniqname - human friendly name providing homonym disambiguation.
  * flags - notable properties of this taxon, if any.

**synonyms.tsv**: The list of synonyms. Columns are separated by vertical bars ('|'). Columns are:

* uid - the id for the taxon (from taxonomy.tsv) that this synonym resolves to
* name - the synonymic taxon name
* type - typically will be 'synonym' but could be any of the NCBI synonym types (authority, common name, etc.)
* uniqname - name with name of target taxon

**version.tsv**: The version of OTT.

## Build script

The reference taxonomy is an algorithmic combination of several
source taxonomies.  For code,
see <a href="https://github.com/OpenTreeOfLife/reference-taxonomy">the
source code repository</a>.
Version 2.8 was generated using 
[commit f2ee381ef3c2d1806cd3d4b311124fb590aa2a42](https://github.com/OpenTreeOfLife/reference-taxonomy/commit/f2ee381ef3c2d1806cd3d4b311124fb590aa2a42).

Where taxonomies conflict regarding taxon relationships, they are
resolved in favor of the higher priority taxonomy.  The priority
ordering is as given below.

## Sources

Any errors in OTT
should be assumed to have been introduced by the Open Tree of Life 
project until confirmed as originating in the source taxonomy.

Download locations are for the particular versions used to construct
OTT 2.8.  For new work, current versions of these sources should be
retrieved.

1.  Taxonomy from: 
    DS Hibbett, M Binder, JF Bischoff, M Blackwell, et al. 
    A higher-level phylogenetic classification of the <i>Fungi</i>.
    [Mycological Research</i> <b>111</b>(5):509-547, 2007](http://dx.doi.org/10.1016/j.mycres.2007.03.004).
    Newick string with revisions
    archived at [http://dx.doi.org/10.6084/m9.figshare.915439](http://dx.doi.org/10.6084/m9.figshare.915439).
    <br />
    Download location: [http://purl.org/opentree/ott/ott2.8/inputs/hibbett-20140313.tre](http://purl.org/opentree/ott/ott2.8/inputs/hibbett-20140313.tre)

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
    Kirk, April 2014 (personal communication).
    Web site: [http://www.indexfungorum.org/](http://www.indexfungorum.org/).
    <br />
    Download location (converted to OTT format): [https://purl.org/opentree/ott/ott2.8/inputs/if-20140514.tgz](https://purl.org/opentree/ott/ott2.8/inputs/if-20140514.tgz).

1.  Taxonomy from:
    Sch&auml;ferhoff, B., Fleischmann, A., Fischer, E., Albach, D. C., Borsch,
    T., Heubl, G., and M&uuml;ller, K. F. (2010). Towards resolving Lamiales
    relationships: insights from rapidly evolving chloroplast
    sequences. 
    [<i>BMC evolutionary biology</i> 10(1), 352.](http://dx.doi.org/10.1186/1471-2148-10-352).
    Manually transcribed from the paper and converted to OTT format.
    <br />
    Download location: [http://purl.org/opentree/ott/ott2.8/inputs/lamiales-20140118.tsv](http://purl.oeg/opentree/ott/ott2.8/inputs/lamiales-20140118.tsv)

1.  NCBI Taxonomy, from the 
    [US National Center on Biotechnology Information](http://www.ncbi.nlm.nih.gov/).
    Web site: [http://www.ncbi.nlm.nih.gov/Taxonomy/](http://www.ncbi.nlm.nih.gov/Taxonomy/).
    Current version download location:
    [ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz](ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz)
    <br />
    For OTT 2.8 we used a version dated circa 11 June 2014.
    Download location: [https://purl.org/opentree/ott/ott2.8/inputs/taxdump-20140611.tgz](https://purl.org/opentree/ott/ott2.8/inputs/taxdump-20140611.tgz).
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
 
It has been requested that we relay the following statement:

> REUSE OF IRMNG CONTENT:
> IRMNG (the Interim Register of Marine and Nonmarine Genera) is assembled, with permission, from a range of third party data sources, certain of which permit data reuse only under specific conditions. In particular, for data originating from the Catalogue of Life (CoL), please refer to the relevant terms and conditions for reuse of CoL data as available at [http://www.catalogueoflife.org/content/terms-use](http://www.catalogueoflife.org/content/terms-use), and for data originating from the World Register of Marine Species (WoRMS) refer the paragraph "Terms of Use and Citation" at [http://www.marinespecies.org/about.php](http://www.marinespecies.org/about.php). The compilers of IRMNG accept no liability for any reuse of IRMNG content by downstream users which may be construed by the original data providers to violate their publicly available conditions of use.

The Open Tree Taxonomy does not reproduce its sources in their
entirety or in their original form of expression, but only uses
limited information expressed in them. See "[Scientific names of
organisms: attribution, rights, and licensing](http://dx.doi.org/10.1186/1756-0500-7-79)" ([http://dx.doi.org/10.1186/1756-0500-7-79](http://dx.doi.org/10.1186/1756-0500-7-79))
regarding use of taxonomic information and attribution.
