# How to update sources and issue a new release

## TL;DR

    bin/configure ott3.8 ott3.7  # or ott3.1 ott3.0, or whatever
    # **** see below when building ott3.1 ***
    bin/unpack-archive ott-HEAD
    make refresh/idlist
    make refresh/ncbi         # optional
    make refresh/amendments
    make refresh/ott
    # do QC here
    make refresh/idlist    (make 3.1 idlist from idlist-3.0 and ott3.0)
    make store-all

Except - for building OTT 3.1 only, do the following after the
bin/configure step:

    bin/set-previous idlist idlist-2.10
    bin/use-version genbank-20161216
    bin/use-version irmng-20140131
    bin/use-version worms-20170513


## Configuration

Shell variables

* `FILES_URL_PREFIX`, defaults to `http://files.opentreeoflife.org` -
  this is the URL prefix used by `wget` for fetching tarballs
* `SSH_PATH_PREFIX`, defaults to
  `files.opentreeoflife.org:files.opentreeoflife.org` - this will be
  the destination used by 'scp' for storing new source and OTT tarballs.

`make` variables in Makefile

* `NCBI_ORIGIN_URL` - where to get `taxdump.tar.gz` whenever NCBI
  Taxonomy is refreshed.
* Several other similar variables.  These may need to be updated, as
  the source web sites (e.g. gbif.org) can be fickle.


# How to update sources and issue a new release


## Getting started

The Makefile needs to know which version to build, and where to get
archived versions of any sources that are not being refreshed.  The
following command tells it:

    bin/configure ott3.1 ott3.0

This reads ott3.0/properties.json, first copying it from the files
server if it's not already present locally, and writes config.mk,
which is included by the main Makefile.


## Updating sources

Each source has a 'refresh' target that updates the source from the
source's origin on the web.

    make refresh/amendments    - you'll want to do this for every release.

    make refresh/ncbi    - every release.

    make refresh/gbif and so on.


## Issuing a new release

    make refresh/ott

    (writes config2.mk)

    make store-all


## Older instructions

* [reference-taxonomy repository README](../README.md)
* [Deploying a new taxonomy version](https://github.com/OpenTreeOfLife/germinator/wiki/Deploying-a-new-taxonomy-version)
