This is the repository for the Open Tree of Life reference taxonomy
(OTT), which is only one part of the Open Tree of Life project.

For general Open Tree of Life project documentation see 
[the germinator repository's wiki](https://github.com/OpenTreeOfLife/germinator/wiki).

This repository contains the following:

1. `Smasher`, a taxonomy manipulation tool
    * Java package `taxa`, general classes for taxa and taxonomies
    * Java package `smasher`, for combining taxonomies
    * Java package `conflict`, for conflict analysis
    * a variety of python utilities
1. Scripts for creating the Open Tree reference taxonomy (OTT)
    * python and shell scripts for creating OTT
    * taxonomic source information, such as the separation taxonomy, Hibbett 2007, and Schaferhoff 2012
    * scripts for making the master OTT id list

## How to use 'smasher'

See file [doc/scripting.md](doc/scripting.md)
for documentation on using the scripting features of smasher for
building, subsetting, querying, and other operations on taxonomies.

Taxonomies are represented as directories, see 
[here](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format).

To test to see whether you can run Smasher successfully, do 'make test'.

If you're puzzled by some decision the algorithm has made, it can be
helpful to look at output files log.tsv, choices.tsv, and conflicts.tsv.

## How to create a new version of OTT

See files in the doc/ directory for release notes for the taxonomy itself.

- Source taxonomies are downloaded from files.opentreeoflife.org by rules in the Makefile

- Edit the definition of WHICH in Makefile to be a new version number, e.g.
  WHICH=4.0.draft13

- 'make ott'

- Result will be written to `tax/ott/`


## Updating source taxonomies

- To refresh NCBI, `make refresh-ncbi`.  Similarly `refresh-gbif`.

- Dealing with WoRMS, Index Fungorum, and IRMNG is more involved.
