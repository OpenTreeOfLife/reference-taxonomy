# How to update sources and issue a new release

## TL;DR

Following is the overall scheme for doing a taxonomy update.  This
example updates NCBI Taxonomy and the Open Tree curator tool taxon
additions, and gets any new directives added to
curation/adjustments.py and curation/amendments.py.

    # For builds POST-3.1 only!  See below for 3.1
    bin/configure ott3.8 ott3.7   #fill in the correct version numbers
    bin/unpack-archive ott-HEAD   #get previous OTT
    make refresh/idlist    #make 3.7 idlist from idlist-3.6 and ott3.7
    make refresh/ncbi
    make refresh/amendments
    make ott
    #**** Check for errors at this point, redo 'make ott' as needed ****
    make refresh/ott
    make store-all

## Special considerations for the OTT 3.1 build

Sometimes you want to use a saved version of a source instead of
either making a new one or using the one used in the previous OTT
build.  For OTT 3.1 there are a few of these: for genbank, irmng, and
worms.  Once these settings are used for the 3.1 build, they will be
retained for future builds.

A second 'wart' for building 3.1 is use of a newer OTT 2.10
all-identifiers list (idlist) rather than the OTT 2.10 identifier list
that was using in building OTT 3.0.

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
    #**** Do QC at this point, redo 'make ott' as needed ****
    make refresh/ott
    make store-all

## Prerequisites

I've been doing OTT builds on a 2014 MacBook with 16G of RAM, and on a
2010 server with 40G of RAM.  With less RAM than this I think it will
be painfully slow.  It already takes over 15 minutes even with plenty
of RAM.

Software requirements:
* GNU `make`.  I've been using 3.81 and 4.0 but I don't think any 
  particular version is required.
* `bash`.
* GNU `tar`.
* GNU `date`, available as `gdate`.
* `wget`.  I've been using GNU `wget` 1.18 and 1.16.
* python 2.7; I've been using 2.7.10 and 2.7.9.
* JVM/JRE/javac.  I've been using JRE build 1.8.0_05-b13 on Mac, 
  openjdk 1.8.0_111 on server.  Java 1.7 ought to work but I haven't 
  tried it.

Miscellaneous java libraries are retrieved from Maven Central during
the build process, so as long as you have an Internet connection, you
shouldn't have to worry about those.

## Smoke test

You can do

    make test

for a basic test.  This does not test everything needed for an OTT
build; e.g. it doesn't exercise the archiving scripts.  But it does
invoke many important parts of the infrastructure.

The outcome should be a taxonomy in the `t/tax/aster` directory.

The test taxonomy is rooted at Asterales.  It may be possible to use a
different taxon as root, e.g. Mammalia, but this hasn't been tested:

    export SELECTION=Mammalia
    export SELECTION_TAG=mml
    make test

(If you just want a subset of OTT, you can use the `Smasher` class at
the command line, or a simple python script, to select it from a
pre-built OTT.)


## Finding and fixing errors

Each invocation of `make refresh/ott` has the potential of detecting
errors, and of introducing mistakes that are not caught.

See the section [finding and fixing errors](errors.md) for more on finding
and fixing errors.

## Configuring the assembly process

Shell variables

* `FILES_URL_PREFIX`, defaults to `http://files.opentreeoflife.org` -
  this is the URL prefix used by `wget` for fetching tarballs of previous
  OTT and source versions.  If you have a local mirror (or subset) or the
  files site, you might be able to set this to a `file:` URL, but
  I suspect that wget doesn't understand `file:`.  I accessed by local
  files mirror using the local Apache 
  server set up with a symbolic link from the webroot to the mirror.
* `SSH_PATH_PREFIX`, defaults to
  `files.opentreeoflife.org:files.opentreeoflife.org` - this will be
  the destination used by `scp` for storing new tarballs.
  If the value of `SSH_PATH_PREFIX` does not include a colon `:`, the files
  will copied to a local directory using `cp -p`.

`make` variables in Makefile

* `NCBI_ORIGIN_URL` - where to get `taxdump.tar.gz` in order to refresh NCBI
  Taxonomy.
* Several other similar variables for other sources.  These may need
  to be updated, as the source web sites (e.g. gbif.org) can be
  fickle.

## Getting started

The Makefile needs to know which version to build, and where to get
archived versions of any sources that are not being refreshed.  The
following command tells it the version numbers of the previous and new
versions of OTT:

    bin/configure ott3.1 ott3.0

This has the effect of reading the file `ott3.0/properties.json`,
obtaining it from the files server if it's not already present
locally, then writing `config.mk`, which is included by the main
`Makefile`.  `config.mk` encodes the knowledge of which versions of
the sources were used in building the old OTT (`ott3.0` in this case).
In addition `bin/configure` sets up a few files, directories, and
links in preparation for the upcoming build.


## Updating sources

Each source has a 'refresh' target that updates the source from the
source's origin on the web.  Each of these refresh rules is a little
different from the others, and you need to think before deciding to do
each one.

1. `make refresh/idlist` - When building version 3.1 of OTT (say), a
   version 3.0 identifier list is needed (that is, a list of all OTT
   identifiers through OTT 3.0).  This list is built by extending the
   OTT 2.10 identifier list with the new identifiers that were added
   in OTT 3.0.  So this command has to be part of every release
   sequence.

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

1. `make refresh/fung` - this should not be used.  Properly done, it
   would depend on new information from Paul Kirk (or some other
   authorized person associated with Index Fungorum), which we don't
   have yet, and each dump will need its own script.

1. `make refresh/gbif` - The GBIF backbone taxonomy dump changed
   format between 2013 and 2016.  This required a new preprocessing
   script to select the particular columns that are used, and to split
   off authority information.  It is quite possible that a new script
   will be needed in order to import a 2017 or later GBIF backbone.  A
   DwCA importer would certainly help here by making the import robust
   to addition or rearrangement of columns in the GBIF flat file.  But
   I don't think it will always be sufficient - e.g. it would not have
   helped with the switch from canonicalName to scientificName between
   2013 and 2016.

1. `make refresh/irmng` - This is of no use, without further work,
   since it will just get the 2014-01-31 version again from the web,
   which we already have.  The time to look at this again is when VLIZ
   decides to issue an IRMNG dump with post-2014 content.  (I believe
   they are providing partial dumps now, but I don't recommend trying
   to work with partial dumps.)  Again, a DwCA importer ought to help.

For more information on how the update process works see
[here](build-system.md).

## Using previously imported source versions

The `use-version` command lets you select an archived version other
than the one from which the previous OTT version was built.  This
differs from the `refresh/` targets in that the latter will actually
go out and get new content from the web, while `use-version` only
selects content to be loaded from the Open Tree 'files server'.

    bin/use-version genbank-20161216

## Creating a draft

    make ott

The draft taxonomy is placed in `r/ott-NEW/source/`.


## TBD: Putting a draft up on devapi

There needs to be a command for creating a draft tarball, without also
putting the tarball on the files server as a full point release.
Coming soon.


## Issuing a new release

After debugging, make the release candidate with

    make refresh/ott

To do the real release, i.e. to publish to the files server, we'll
need an OTT tarball, and tarballs for all of the sources that went
into building OTT.  The following command creates all of the tarballs
and stores them on the files server:

    make store-all

Find the tarballs as
`http://files.openetreeoflife.org/ott/ott3.1/ott3.1.tgz`,
`http://files.openetreeoflife.org/ncbi/ncbi-20170523/ncbi-20170523.tgz`,
and so on.


## And then:

1. Create release notes for the new release
2. Deploy to taxomachine, oti, otindex - some old instructions 
   [here](https://github.com/OpenTreeOfLife/germinator/wiki/Deploying-a-new-taxonomy-version)
   should be useful
3. Do a new synthesis - it would be confusing to users were a new
   taxonomy deployed at the same time that an old synthetic tree was 
   active.


## Note re identifiers

Smasher allocates identifiers for new non-addition OTT records above
7000000.  OTT identifiers for curator webapp additions are allocated
in the range (approximately) 6000000 to 7000000.  There is no check
for collisions, so in the unlikely event there are more than a million
additions, some kind of intervention will be needed.
