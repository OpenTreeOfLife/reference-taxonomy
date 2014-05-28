This is the Open Tree Taxonomy (OTT) version 2.6, created on 2014-04-11.

The taxonomy was generated using the 'smasher' utility, commit
f624223f31, which resides on github, here:
https://github.com/OpenTreeOfLife/reference-taxonomy/commit/f624223f31767fa1787f3ba2ddad5daa56fd939b

File in this package
====================

ott/taxonomy.tsv
ott/synonyms.tsv

  The format of these files is described in doc/Interim-taxonomy-file-format.md
    https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/doc/Interim-taxonomy-file-format.md

ott/hidden.tsv

  Report on 'hidden' taxa (incertae sedis and other suppressed taxa).
  Columns are OTT id, name, source taxonomy and id, containing major
  group, and flags (reasons for hiding).

ott/used-but-hidden.tsv

  Subset of hidden.tsv for taxa that are referenced from source trees.
  Columns are as for hidden.tsv, with the addition of a column for
  Phylografter study number.

ott/conflicts.tsv

  Report on taxa that are hidden because they are paraphyletic with
  respect to a higher priority taxon.  Number at beginning is height
  in taxonomic tree of nearest common ancestor with priority taxon
  that 'steals' one or more children.

ott/deprecated.tsv

  List all of taxa that have been deprecated since version 2.5.

ott/log.tsv

  Additional debugging information related to deprecated taxa.

Inputs required to create this version
======================================

The following information is current as of 2014-05-28.

SILVA
  Retrieved from
    https://www.arb-silva.de/fileadmin/silva_databases/release_115/Exports/SSURef_NR99_115_tax_silva.fasta.tgz
  Last-modified date: 2013-09-07
  See: Quast C, Pruesse E, Yilmaz P, Gerken J, Schweer T, Yarza P,
    Peplies J, Glockner FO (2013) The SILVA ribosomal RNA gene
    database project: improved data processing and web-based tools.
    Nucleic Acids Research 41 (D1): D590-D596.
    http://dx.doi.org/10.1093/nar/gks1219
  Web site: https://www.arb-silva.de/

Taxonomy from Hibbett et al 2007, with updates through 2014
  Retrieved from
    http://dx.doi.org/10.6084/m9.figshare.915439
  Last-modified date: 2014-03-10
  There is a copy in the git repository.
  See: A higher-level phylogenetic classification of the Fungi.
    DS Hibbett, M Binder, JF Bischoff, M Blackwell, et al.
    Mycological Research 111(5):509-547, 2007.
    http://dx.doi.org/10.1016/j.mycres.2007.03.004

Index Fungorum
  We received database table dumps from Paul Kirk in email in
  November-December 2013, and converted them to the interim taxonomy
  format using ad hoc scripts.  The converted form is here:
    http://purl.org/opentree/ott2.6-inputs/tax/if/taxonomy.tsv
    http://purl.org/opentree/ott2.6-inputs/tax/if/synonyms.tsv
  Web site: http://www.indexfungorum.org/

Lamiales taxonomy from Schaferhof et al 2010
  File prepared from figure by Open Tree of Life staff.
  There is a copy in the git repository.
  See:
    Schaferhoff, B., Fleischmann, A., Fischer, E., Albach, D. C.,
    Borsch, T., Heubl, G., and Muller, K. F. (2010). Towards resolving
    Lamiales relationships: insights from rapidly evolving chloroplast
    sequences. BMC evolutionary biology 10(1), 352.
    http://dx.doi.org/10.1186/1471-2148-10-352

NCBI Taxonomy
  Retrieved from ftp://ftp.ncbi.nih.gov/pub/taxonomy/taxdump.tar.gz
  Last-modified date: 2014-01-06
  As far as we can tell, NCBI does not archive past versions of its
  taxonomy.  We have captured the applicable version here:
    http://purl.org/opentree/ott2.6-inputs/feed/ncbi/taxdump.tar.gz
  Web site: https://www.ncbi.nlm.nih.gov/taxonomy

GBIF backbone taxonomy
  Retrieved from http://ecat-dev.gbif.org/repository/export/checklist1.zip
  Last-modified date: 2013-07-02
  GBIF intends to reorganize their data archives and this file may
  move.  We will make a best effort to maintain the following PURL:
    http://purl.org/opentree/gbif-backbone-2013-07-02.zip
  In case these links do not work, search gbif.org data sets and use the
  following file information for confirmation:
    Size: 323093992 bytes
    sha1sum: b7c7c19f1835af3f424ce4f2c086c692c1818b90
    Format: Zip file containing a Darwin Core Archive
    Contains file taxon.txt which has 4416348 lines
  Web site: http://www.gbif.org/dataset/d7dddbf4-2cf0-4f39-9b2a-bb099caae36c

IRMNG (Interim Register of Marine and Nonmarine Genera)
  Retrieved from http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip
  Last-modified date: 2014-01-12
    http://purl.org/opentree/ott2.6-inputs/feed/irmng/in/IRMNG_DWC.zip
  Web site: http://www.obis.org.au/irmng/

OTT version 2.5
  The previous version of OTT is used only for the purpose of ensuring
  identifier choice consistency from one version of OTT to the next.
    http://files.opentreeoflife.org/ott/ott2.5.tgz
