# How to update sources and issue a new release

## TL;DR

Following is the general scheme for doing an update after version 3.1,
updating NCBI Taxonomy, taxon additions, and any new directives added
to curation/adjustments.py and curation/amendments.py.

    # For builds POST-3.1 only!  See below for 3.1
    bin/configure ott3.8 ott3.7   #fill in the correct version numbers
    bin/unpack-archive ott-HEAD   #get previous OTT
    make refresh/idlist    #make 3.0 idlist from idlist-2.10 and ott3.0
    make refresh/ncbi
    make refresh/amendments
    make ott
    #**** Do QC at this point, redo 'make refresh/ott' as needed ****
    make refresh/ott
    make store-all

## Special considerations for the OTT 3.1 build

Sometimes you want to use a saved version of a source instead of
either making a new one or using the one used in the previous OTT
build.  For OTT 3.1 there are a few of these: for genbank, irmng, and
worms.

A second 'wart' for building 3.1 is use of a newer OTT 2.10 identifier
list rather than the OTT 2.10 identifier list that was using in
building OTT 3.0.

The 3.1 build should therefore look like the following:

    bin/configure ott3.1 ott3.0
    bin/unpack-archive ott-HEAD   #get previous OTT
    bin/set-previous idlist idlist-2.10
    make refresh/idlist    #make 3.0 idlist from idlist-2.10 and ott3.0
    bin/use-version genbank-20161216
    bin/use-version irmng-20140131
    bin/use-version worms-20170513
    make refresh/ncbi
    make refresh/amendments
    make ott
    #**** Do QC at this point, redo 'make refresh/ott' as needed ****
    make refresh/ott
    make store-all

## Finding and fixing errors

Each invocation of `make refresh/ott` has the potential of detecting
errors, and of introducing mistakes that are not caught.

Detected errors are written to `r/ott-NEW/source/debug/transcript.out` on lines
beginning `**`.  (But some of those lines might be innocent, so
attempting to get rid of all such messages may be futile or worse.)

Undetected errors are sometimes not seen until a person looks at the
taxonomy, and often result from bugs in the assembly code.  The best
way to find these is to deploy the taxonomy draft on devapi and use
the taxonomy browser to inspect it, and/or to make a synthetic tree

Errors of both kinds are usually repaired by changes to
`curation/adjustments.py` or `curation/amentments.py`.  More strategic
changes are made in `assemble-ott.py` or in the smasher code itself.

Ideally, over time, errors of the second kind will be converted to
errors of the first time, often by the addition of rows to
`inclusions.tsv`.

See the section [finding and fixing errors](qc.md) for more on finding
and fixing errors.

## Configuring the assembly process

Shell variables

* `FILES_URL_PREFIX`, defaults to `http://files.opentreeoflife.org` -
  this is the URL prefix used by `wget` for fetching tarballs
* `SSH_PATH_PREFIX`, defaults to
  `files.opentreeoflife.org:files.opentreeoflife.org` - this will be
  the destination used by 'scp' for storing new source and OTT tarballs.

`make` variables in Makefile

* `NCBI_ORIGIN_URL` - where to get `taxdump.tar.gz` when NCBI
  Taxonomy is refreshed.
* Several other similar variables for other sources.  These may need
  to be updated, as the source web sites (e.g. gbif.org) can be
  fickle.

The Makefile needs to know which version to build, and where to get
archived versions of any sources that are not being refreshed.  The
following command tells it the version numbers of the previous and new
versions of OTT:

    bin/configure ott3.1 ott3.0

This reads `ott3.0/properties.json`, first copying it from the files
server if it's not already present locally, and writes `config.mk`,
which is included by the main Makefile.  `config.mk` encodes the
knowledge of which versions of the sources were used in building the
old OTT (`ott3.0` in this case).


## Updating sources

Each source has an optional 'refresh' target that updates the source
from the source's origin on the web.  Each of these is a little
different from the others.

1. `make refresh/amendments` - you'll want to do this for every
   release.  This obtains the latest versions of the taxon additions
   from the `amendments` repo on github.

1. `make refresh/ncbi` - so far this has worked pretty smoothly, so it
   should be done on every release.

1. `make refresh/worms` - this is a new script to access WoRMS via
   their API.  When I ran it it took about 15 hours.  I don't
   recommend running it casually or frequently, since even though the
   script throttles its requests, the load on the WoRMS API server may
   be noticeable.

1. `make refresh/fung` - this should not be used.  It depends on new
   information from Paul Kirk (or some other authorized person), which
   we don't have yet, and
   each dump will need its own script.

1. `make refresh/gbif` - The GBIF backbone taxonomy dump changed
   format between 2013 and 2016.  This required a new preprocessing
   script to select the particular columns that are used, and to split
   off authority information.  It is quite possible that a new script
   will be needed in order to import a 2017 or later GBIF backbone.  A
   DwCA importer would certainly help here, but I don't think it will
   always be sufficient.

1. `make refresh/irmng` - This is of no use, without further work,
   since it will get get the 2014-01-31 version again from the web,
   resulting in no new content.  The time to look at this is when VLIZ
   decides to issue an IRMNG dump with post-2014 content.  (I believe
   they are providing partial dumps now, but I don't recommend trying
   to work with partial dumps.)

1. `make refresh/idlist` - When building version 3.1 of OTT (say), a
   version 3.0 identifier list is needed (that is, a list of all OTT
   identifiers through OTT 3.0).  This is built by extending the OTT
   2.10 identifier list with the new identifiers that were added in
   OTT 3.0.  So this command has to be part of every release sequence.

For more information on how the update process works see
[here](build-system.md).

## Using previously imported source versions

The `use-version` command lets you select an archived version other
than the one from which the previous OTT version was built.  This
differs from the `refresh/` targets in that the latter will actually
go out and get new content from the web, while `use-version` only
selects content to be loaded from the Open Tree 'files server'.

    bin/use-version genbank-20161216

## TBD: Putting a draft up on devapi

There needs to be a command for creating a tarball, without also
putting the tarball on the files server as a full point release.
Coming soon.


## Issuing a new release

After debugging, make the release candidate with

    make refresh/ott

To do the real release, i.e. to publish to the files server, we'll
need an OTT tarball, and tarballs for all of the sources that went
into building OTT.  The following creates all of the tarballs and
stores them on the files server:

    make store-all

Find them as `http://files.openetreeoflife.org/ott/ott3.1/ott3.1.tgz`,
`http://files.openetreeoflife.org/ncbi/ncbi-20170523/ncbi-20170523.tgz`,
and so on.


## Older instructions

* [reference-taxonomy repository README](../README.md)
* [Deploying a new taxonomy version](https://github.com/OpenTreeOfLife/germinator/wiki/Deploying-a-new-taxonomy-version)
