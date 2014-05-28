This page describes the format used to represent the taxonomies that are the inputs and outputs of the Open Tree of Life taxonomy build system.

The format derives from NCBI and is intentionally rudimentary because our needs are minimal. A better format to use in the long run might be [Darwin Core Archive](https://code.google.com/p/gbif-ecat/wiki/DwCArchive), which is what is used by GBIF, EOL, and the Global Names Architecture (GNA).

***

Each source taxonomy (NCBI, GBIF, Index Fungorum, ...) has its own script that converts its
native format into this format.

A taxonomy consists of a directory of files with fixed names.  Example: `mycobank/taxonomy.tsv`, `mycobank/synonyms.tsv`, `mycobank/about.md`.

## Character encoding

All files  use the UTF-8 character encoding.  Native taxonomy files often use some other encoding, so conversion might be necessary.  Some aggregated taxonomies on the web have gotten this wrong and are a mess of mixed encodings and spurious re-encodings.

## Taxonomy

### File `taxonomy.tsv`

Four required columns, each column followed by tab - vertical bar - tab (even for the last column, which is unlike NCBI).  The taxonomy build tool 'smasher' doesn't require the vertical bars; they are optional although they should be either all present or all absent.  But some other consumers of these files may still require the vertical bars.

A header row of column names is recommended, but not required (for `Smasher`). If provided, it looks like:

    uid	|	parent_uid	|	name	|	rank	|	

All following rows are one row per taxon

**Columns:**

1. _identifier_ - an identifier for the taxon, unique within this file.  Should be native accession number whenever possible.  Usually this is an integer, but it need not be.
2. _parent taxon identifier_ or the empty string if there is no parent (i.e., it's a root).
3. _name_ - arbitrary text for the taxon name; not necessarily unique within the file.
4. _rank_, e.g. species, family, class.  Should be all lower case.  If no rank is assigned, or the rank is unknown, put "no rank".

Example (from NCBI):

        5157	|	1028423	|	Ceratocystis	|	genus	|	
        5156	|	91171	|	Gondwanamyces proteae	|	species	|	

**Optional additional columns:**

* _sourceinfo_: a comma-separated list of specifiers, each one either a URL or a CURIE.  If a URL, it should be either a DOI in the form of a URL, or a link to some other source such as a database.  URLs begin 'http://' or 'https://' and DOI URLs begin 'http://dx.doi.org/10.'.  A CURIE is an abbreviated URI using a prefix drawn from a known set, e.g. ncbi:1234 is taxon 1234 in the NCBI taxonomy.  Other prefixes include gbif:, if: (Index Fungorum), mb: (Mycobank). New prefixes can be added but this is a manual process, please request explicitly.
* _uniqueName_: a human-readable string that is unique to this taxon, typically the taxon name if it is unique, or taxon name followed by "([rank] in [ancestor])" where rank is the taxon's rank and ancestor is an ancestor that is unique to this taxon (among the taxa that have the same name).
* _flags_: a comma-separated list of flags or markers.  Usually these are generated by taxonomy synthesis and are used to decide whether a taxon is 'hidden' or not.  For example, if there's an 'extinct' flag then it may be desirable to suppress the taxon in an application.  See [here](https://github.com/OpenTreeOfLife/taxomachine/blob/master/src/main/java/org/opentree/taxonomy/OTTFlag.java).

### Synonyms

Usually there are synonyms.  These go into a second file, `synonyms.tsv`.  This file must have a header row

        uid	|	name	|	type	|	rank	|	

The header is necessary because it designates the order of the columns, which can sometimes change. These are the four columns:

* _uid_ - the id for the taxon (from the taxonomy file) that this synonym resolves to
* _name_ - the synonymic taxon name
* _type_ - typically will be 'synonym' but could be any of the NCBI synonym types (authority, common name, etc.)
* _rank_ - currently ignored for taxonomy synthesis.

Example from NCBI:

        89373	|	Flexibacteraceae	|	synonym	|	|	

### Metadata

Overall metadata for the taxonomy is placed in a separate file.  The metadata format is currently under development. `Smasher` generates this in JSON format as `about.json`, but this file is currently not used programmatically, and is in the process of being overhauled. When generating a taxonomy according to this format in external tools, for now it is best to simply write a markdown or plain text file called `about.md` (in the same directory as `taxonomy.tsv` and `synonyms.tsv`).

The metadata provided in the file should include the source of the taxonomy (article or database) as a URL and any other descriptive information that's available.  The purpose of the metadata is not just explanatory but also to explain how to check the correctness of the taxonomy against its source and make corrections and other improvements should the source be updated. When using information from changing sources (databases) the date or dates of retrieval should be recorded.

***

_This page was originally part of the [open tree wiki](https://github.com/OpenTreeOfLife/opentree/wiki/Interim-taxonomy-file-format), and was transferred, since then maintained here on 2014-02-06._