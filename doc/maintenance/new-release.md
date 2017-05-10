

# How to update sources and issue a new release


## Getting started

    ./configure ott3.0
       or make configure/ott3.0

    (writes config1.mk)


## Updating sources

    make refresh/amendments    - you'll want to do this.

    make refresh/ncbi

    make refresh/gbif and so on.


## Issuing a new release

    make refresh/ott

    (writes config2.mk)

    make store-all

