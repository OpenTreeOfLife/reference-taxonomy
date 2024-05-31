## OTT version 2.0 release notes

## Download

[Download](https://files.opentreeoflife.org/ott/ott2.0/ott2.0.tgz) (gzipped tar file, 62 Mbyte) 

## Release notes

OTT 2.0 employs a version of the GBIF taxonomy that was published in July 2012.
The GBIF taxonomy was retrieved via [this page](http://ecat-dev.gbif.org/about/nub/).

This version is intended to supersede what I'll call "version 1.0" (the OTToL we've been using through March 2013, see [here](https://github.com/OpenTreeOfLife/taxomachine/wiki/Loading-the-OTToL-working-taxonomy)).

New features / changes since version 1.0:

* Use of synonyms from NCBI in resolving taxa from GBIF
* A more careful merging method, some errors fixed
* Update to more recent version of NCBI (previous was from approximately March 1)
* Inclusion of "unclassified" taxa from NCBI
* About 600,000 additional taxa from GBIF
* Ensures that a name occurring under disjoint nomenclatural authorities is considered homonymous
* The build process is repeatable with identifier semantics preserved across updates to the sources
* A full mapping from "preottol" is provided

OTT 2.0 is provided as a suite of files gathered into a compressed 
tarball; see <http://files.opentreeoflife.org/ott/>
