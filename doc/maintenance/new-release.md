
# Configuration

Shell variables

* `SSH_PATH_PREFIX`, defaults to
  `files.opentreeoflife.org:files.opentreeoflife.org` - this will be
  the destination used by 'scp' for storing new source and OTT tarballs.
* `FILES_URL_PREFIX`, defaults to `http://files.opentreeoflife.org` -
  this is the URL prefix used by `wget` for fetching tarballs

`make` variables in Makefile

* `NCBI_ORIGIN_URL` - where to get `taxdump.tar.gz` whenever NCBI
  Taxonomy is refreshed
* Several other similar variables.  These may need to be updated as
  the source web sites can be fickle.


# How to update sources and issue a new release


## Getting started

The Makefile needs to know where to get archived versions of sources
(those that are not to be updated for whatever reason).  The following
command tells it:

    ./configure ott3.0
       or make configure/ott3.0

    (reads ott3.0/properties.json, writes config1.mk)


## Updating sources

    make refresh/amendments    - you'll want to do this.

    make refresh/ncbi

    make refresh/gbif and so on.


## Issuing a new release

    make refresh/ott

    (writes config2.mk)

    make store-all

## Older instructions

* [reference-taxonomy repository README](../README.md)
* [Deploying a new taxonomy version](https://github.com/OpenTreeOfLife/germinator/wiki/Deploying-a-new-taxonomy-version)
