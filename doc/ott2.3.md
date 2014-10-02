## Version 2.3 release notes

* Incorporates SILVA (minus plants, animals, fungi) and the taxonomy from 
  [study 713](http://reelab.net/phylografter/study/view/713)
* Incorporates patches from the [Interim taxonomy patch feature](Interim-taxonomy-file-format.md)
* More thorough synonym processing
* The deprecated.tsv file has a column providing a replacement ID for cases 
  where a newly known synonym caused an ID to be deprecated
* Updated to latest version of NCBI taxonomy (GBIF hasn't released a new version yet)

## Version 2.1 release notes

* Updated to latest version of NCBI taxonomy and GBIF nub
* Includes all taxa from NCBI, with "dubious" ones (unclassified, virus, etc.)
  marked with a "D" flag in the last column
* Various improvements to the "unique names"
* Synonyms file now contains "unique names" of the form "Xyz (synonym for Pqr)"
* Now includes synonyms from GBIF (about 800,000 of them)
* Synonyms file now has a column-name header row, like the taxonomy file
* Some IRMNG homonyms which were previously suppressed have been recovered (those taxa that have children)
* Eliminated the "kill lists" of certain NCBI and GBIF taxa. This might introduce some problems, unclear
* Fixed Rhodophyta duplications, Ciliophora mapping error, and various other minor problems
* Removed dubious IRMNG genera from top level of Metazoa and Plantae
* Editing system implemented, and used to add a list of about 70 fungal species

## Version 2.0 release notes

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
