
# Adding a new taxonomic source to OTT

The best thing to do is to find the most similar source already in
OTT, and adapt the logic used to import it.

Probably best to use this guide in conjunction with the [build system guide](build-system.md).

Every import needs these pieces:

### Choose a short name

To be used in file and directory names, and as a prefix in source ids
in the OTT 'sourceIds' field.

For example, `ioc`.


### Fetch source from web and create digest

A script in `import_scripts/ioc`, say `import_scripts/ioc/fetch.py`
(or could be a shell script), that reads the taxonomy from the web and
creates a 'digest' file or files.  The digest can exclude information
that is clearly not needed for OTT, but should otherwise be as
unprocessed as possible.  The reason for this is that if the
processing logic is done as a second step, errors in processing can be
tracked down and fixes tested without having to go back out to the web
on each debugging cycle.

In some cases (e.g. NCBI) the digest is the same as what is downloaded
from the web, in which case no script is needed.


### Refresh rule in Makefile

The `refresh/ioc` rule in the Makefile should invoke
`import_scripts/ioc/fetch.py` and store the digest in the
`ioc-NEW/source/` directory.


### Digest-to-taxonomy rule in Makefile

A second script, say `import_scripts/ioc/process.py`, converts the
digest into a form that's readable by OTT, typically the so-called
['interim format'](interim-format.md).

The digest is found in `ioc-HEAD/source/`, and the processed for is put in 
`ioc-HEAD/resource/`.

The vertical bars are optional.


### Tie it into the assembly

Assess priority relative to other sources.

Follow the pattern of the other sources in `assembly_ott.py` in order
for the source to be aligned and merged along with the others.


### Get it into the release

Do `make refresh/ioc` when building the release.

Troubleshoot any errors.

Add it to the release notes.
