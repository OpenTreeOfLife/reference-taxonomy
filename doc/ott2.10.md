# Open Tree of Life reference taxonomy version 2.10

Version 2.10 draft 11 was generated on 10 September 2016.

## Download

[Download](http://files.opentreeoflife.org/ott/ott2.10/ott2.10.tgz)

## Major changes since OTT 2.9

* Updated to NCBI Taxonomy downloaded June 29, 2016.
* The basic taxon matching method has been rewritten.  Among other improvements it
  now considers all synonym/synonym matches, and matches based on ids.
* Names in IRMNG that are marked invalid (nomen nudum, etc.) do not become OTT taxa
  (unless grandfathered because used in an OTU match).  There are 365811 of these
  although the corresponding OTT taxa are not removed if they also come from another source.
* IRMNG-only taxa are annotated 'hidden' to reduce number of problematic species.
  There are 351271 of these.
* Build transcript is now in transcript.out.
* The download file synonyms.tsv contains information about where synonyms come from.
* Changes to improve the NCBI/SILVA mapping
* Improvements to preprocessing of NCBI Taxonomy, GBIF, and IRMNG
* All inputs are now archived, and most nondeterminism weeded out, to enhance reproducibility
* See below for taxonomy corrections

## Statistics

* OTT identifiers ('taxa'): 3453839 (74510 fewer, due to IRMNG trim)
* Visible: [TBD]
* Synonyms: 1001466 [contains some duplicates, get better number and show delta]
* Deprecated/hidden ids occurring in phylesystem: 531
* Deprecated/hidden ids occurring in studies in synthesis: 184
* Source taxa dissolved due to conflict (conflicts.tsv): 1167

## Contents of download

All files use UTF-8 character encoding.  For documentation about file formats, see [the documentation in the reference taxonomy
wiki](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format),
on github.

**taxonomy.tsv**: The file that contains the taxonomy.

**synonyms.tsv**: The list of synonyms.

**forwards.tsv**: Forwarding pointers - a list of OTT ids that are
  retired and should be replaced by new ones (usually due to
  'lumping')

**conflicts.tsv**: Report on taxa from input taxonomies that are
  hidden because they are paraphyletic with respect to a higher
  taxon from a higher priority input taxonomy.  Number in first column is depth in taxonomic tree of
  nearest common ancestor of its children.

**deprecated.tsv**: List all taxon ids occurring in phylesystem
  studies that have been deprecated since previous version.  This
  includes ids that no longer identify any taxon, those that have been
  'lumped' with other ids, and those for taxa that are suppressed in
  synthesis but weren't suppressed in the previous version.

**version.tsv**: The version of OTT.

**transcript.out**: Console debugging output generated during the taxonomy build process.

**log.tsv**: Debugging information related to homonym resolution.

**weaklog.csv**: internal debugging tool

## Build script

The reference taxonomy is an algorithmic combination of several
source taxonomies.  For code,
see <a href="https://github.com/OpenTreeOfLife/reference-taxonomy">the
source code repository</a>.
Version 2.10 draft 11 was generated using 
<a href="https://github.com/OpenTreeOfLife/reference-taxonomy/commit/eca7bdefdc8ad10fb39b9ff8c98db1cc186d7e94">commit eca7bde</a>.</p>

## Sources

Any errors in OTT
should be assumed to have been introduced by the Open Tree of Life 
project until confirmed as originating in the source taxonomy.

Download locations are for the particular versions used to construct
OTT 2.10.  For new work, current versions of these sources should be
retrieved.

1.  Curated additions from the Open Tree amendments-1 repository, commit [4b3ba1a](https://github.com/OpenTreeOfLife/amendments-1/commit/4b3ba1afdf1fd650b41d520486cdf1bbfba7f36c).  These taxa are added during OTU mapping using the curator application.

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
    Download location (converted to OTT format): [http://files.opentreeoflife.org/fung/fung-9/fung-9-ot.tgz](http://files.opentreeoflife.org/fung/fung-9/fung-9-ot.tgz).

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
    Download location: [http://files.opentreeoflife.org/worms/worms-1/worms-1-ot.tgz](http://files.opentreeoflife.org/worms/worms-1/worms-1-ot.tgz)

1.  NCBI Taxonomy, from the 
    [US National Center on Biotechnology Information](http://www.ncbi.nlm.nih.gov/).
    Web site: [http://www.ncbi.nlm.nih.gov/Taxonomy/](http://www.ncbi.nlm.nih.gov/Taxonomy/).
    <br />
    For OTT 2.10 we used a version downloaded from NCBI on 29 June 2016.
    Download location: [http://files.opentreeoflife.org/ncbi/ncbi-20151006/ncbi-20151006.tgz](http://files.opentreeoflife.org/ncbi/ncbi-20151006/ncbi-20151006.tgz).
    <br />
    Current version download location:
    [ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz](ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz)

1.  GBIF Backbone Taxonomy, from the 
    [Global Biodiversity Information facility](http://www.gbif.org/).
    <br />
    We used a version dated 2013-07-02.
    Download location: [http://purl.org/opentree/gbif-backbone-2013-07-02.zip](http://purl.org/opentree/gbif-backbone-2013-07-02.zip).
    <br />
    Current version download location: 
    [http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c](http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c).

1.  [Interim Register of Marine and Nonmarine Genera (IRMNG)](http://www.obis.org.au/irmng/), from CSIRO.
    <br />
    We used a version dated 2014-01-31.  Download location:
    [http://purl.org/opentree/ott/ott2.8/inputs/IRMNG\_DWC-2014-01-30.zip](http://purl.org/opentree/ott/ott2.8/inputs/IRMNG_DWC-2014-01-30.zip).
 
1.  Taxon identifiers are carried over from [OTT 2.9](http://files.opentreeoflife.org/ott/ott2.9/) when possible
 
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

## Taxonomy corrections (incomplete list)

* https://github.com/OpenTreeOfLife/feedback/issues/43 
* https://github.com/OpenTreeOfLife/feedback/issues/45 Choanoflagellida
* https://github.com/OpenTreeOfLife/feedback/issues/86 Gillocystis
* https://github.com/OpenTreeOfLife/feedback/issues/123 Gryphodobatis
* https://github.com/OpenTreeOfLife/feedback/issues/127 Cestracion
* https://github.com/OpenTreeOfLife/feedback/issues/133 Cordicephalus
* https://github.com/OpenTreeOfLife/feedback/issues/134 Hemitrypus
* https://github.com/OpenTreeOfLife/feedback/issues/135 Cryptobranchus matthewi
* https://github.com/OpenTreeOfLife/feedback/issues/136 Pristophorus lanceolatus
* https://github.com/OpenTreeOfLife/feedback/issues/137 Galeolerdo
* https://github.com/OpenTreeOfLife/feedback/issues/138 Galeocerdo
* https://github.com/OpenTreeOfLife/feedback/issues/142 Aotus
* https://github.com/OpenTreeOfLife/feedback/issues/145 ?
* https://github.com/OpenTreeOfLife/feedback/issues/144 Lepilemur tymerlachsonorum
* https://github.com/OpenTreeOfLife/feedback/issues/150 'Phyllum'
* https://github.com/OpenTreeOfLife/feedback/issues/152 Selachimorpha
* https://github.com/OpenTreeOfLife/feedback/issues/159 Nesophontidae
* https://github.com/OpenTreeOfLife/feedback/issues/160
* https://github.com/OpenTreeOfLife/feedback/issues/165 Theretairus, Diphydontosaurus, Leptosaurus
* https://github.com/OpenTreeOfLife/feedback/issues/167 Plectophanes altus
* https://github.com/OpenTreeOfLife/feedback/issues/184 Hylobates alibarbis
* https://github.com/OpenTreeOfLife/feedback/issues/186 Threskiornis solitarius
* https://github.com/OpenTreeOfLife/feedback/issues/187 Raphus ineptus
* https://github.com/OpenTreeOfLife/feedback/issues/189 Crenarchaeota
* https://github.com/OpenTreeOfLife/feedback/issues/194 Osteichthyes
* https://github.com/OpenTreeOfLife/feedback/issues/219
* https://github.com/OpenTreeOfLife/feedback/issues/220 Phaeosphaeria sp. S93-48
* https://github.com/OpenTreeOfLife/feedback/issues/241 Semionotiformes
* https://github.com/OpenTreeOfLife/feedback/issues/248 Acomys cahirinus
* https://github.com/OpenTreeOfLife/feedback/issues/249 Heteropholis genneus
* https://github.com/OpenTreeOfLife/feedback/issues/258 Hippopotamus mad[a]gascariensis
* https://github.com/OpenTreeOfLife/feedback/issues/259 Turbellaria
* https://github.com/OpenTreeOfLife/feedback/issues/278 Coniferales
* https://github.com/OpenTreeOfLife/feedback/issues/281 Equisetopsida
* https://github.com/OpenTreeOfLife/feedback/issues/282 Chelomophrynus
* https://github.com/OpenTreeOfLife/feedback/issues/283 Shomronella
* https://github.com/OpenTreeOfLife/feedback/issues/285 Notochelys
* https://github.com/OpenTreeOfLife/feedback/issues/289 Puccinia triticina
* https://github.com/OpenTreeOfLife/feedback/issues/291 Panthera uncia
* https://github.com/OpenTreeOfLife/feedback/issues/292 Sphenodontia
* https://github.com/OpenTreeOfLife/feedback/issues/294 Heterodon platirhinos
* https://github.com/OpenTreeOfLife/feedback/issues/307 Archaeplastida
* https://github.com/OpenTreeOfLife/feedback/issues/308 Solenodon
* https://github.com/OpenTreeOfLife/feedback/issues/309 Pseudomonas

* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/36 NCBI->OTT many-to-one
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/100 Solanum lycopersicum
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/167 Lentisphaerae bacterium 8KG-4
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/176 PaleoDB
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/175 annotations
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/195 Opisthokonta rank
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/198 Pseudostomum
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/199 Nanoarchaeum
* https://github.com/OpenTreeOfLife/reference-taxonomy/issues/201 Caenorhabditis elegans

## Previous versions

See <a href="http://files.opentreeoflife.org/ott/">http://files.opentreeoflife.org/ott/</a>

## Errata

* Two records have irregular sources lists; the peculiar entries should be removed.  The records are Eukaryota (304358) and SAR (5246039).
  This is corrected in draft 12.
* There are a few redundant synonym records (45727 to be exact).  Programs reading the synonyms.tsv file should ignore rows with a given id and name, other than the first such.
  This is corrected in draft 12.
* The Makefile specifies the 20151006 version of NCBI taxonomy, but
  actually the 20160629 version was used to build OTT 2.10.
