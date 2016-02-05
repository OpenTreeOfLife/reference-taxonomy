This repository contains the following:

* taxa, Java classes for taxa and taxonomies
* smasher, a Java program for combining taxonomies
* jython and shell scripts for creating OTT, the Open Tree reference taxonomy

For Open Tree of Life documentation see [the germinator repository's wiki](https://github.com/OpenTreeOfLife/germinator/wiki).

## How to use 'smasher'

See file doc/scripting.md for documentation on using the scripting features of smasher for building, subsetting, querying and other operations on taxonomies. 

Taxonomies are represented as directories, see 
[here](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format).

To test to see whether you can run Smasher, do 'make aster'.

If you're puzzled by some decision the algorithm has made, it might be
helpful to look at log.tsv and conflicts.tsv.

## How to create a new version of OTT

See files in the doc/ directory for release notes for the taxonomy itself.

- You may have to put certain source taxonomies such as Index Fungorum
  in place.  Some of these are 'personal communication' so contact JAR
  to get ahold of them (until better arrangements are made).

- Put previous version of OTT in tax/prev_ott/ .  The taxonomy file name
  should be taxonomy.tsv, similarly synonyms.tsv and so on.  (Around version 2.2
  the file names changed from no extension to a .tsv extension.)
  The purpose of prev_ott is to get continuity of identifier assignments.

- Edit definition of WHICH in Makefile to be new version number, e.g.
  WHICH=2.7.draft13

- To refresh NCBI, rm -rf tax/ncbi.  Similarly GBIF and Silva.
  (Note that GBIF is being updated very infrequently,
  so refreshing it is sort of a waste of time.  Silva is updated 
  every few months, I think.)

- 'make'

- Result will be in tax/ott/

- Simple quality control check: do 'make short-list.tsv' - this will show you 
  taxa have three properties: (1) are used in study OTUs, (2) are deprecated 
  in this version of OTT 2.3, (3) have no replacement taxon id.

