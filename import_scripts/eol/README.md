
The EOL import basically works; it uses a concordance from EOL to map
worms/gbif/irmng record ids to EOL page ids, and then OTT maps the
record ids, with the net effect of assigning EOL page ids to OTT ids.

There are Makefile rules for preparing the EOL information that can
subsequently be used by an import module
[`util/load_eol/page_ids.py`](../../util/load_eol/page_ids.py).  This
operates in two different modes: either by reading OTT from a file and
writing the mapping to another file, or by using an OTT already in
memory and adding the mapping as new source ids `eol:1234` to the OTT
records, which can be written out later on.

Unfortunately, if used in the second mode (e.g. called from
`assemble_ott.py`), the process takes a godawful long time, about 1/2
hour, compared to about 7 minutes for the file-to-file version.  I
find this unacceptable, so I have not enable this feature in routine
OTT builds.

The slowdown is probably due to running out of memory, but I don't
understand why this operation should be so memory intensive.

We have a few options.  One is to rewrite the loading code in Java in
hopes that memory use will go down and speed will go up.  Another is
to enhance the 'offline' version so that it can write out an updated
taxonomy, and then add that as a separate phase in the OTT build
process.  In doing the latter, it would not be too hard to reimplement
taxonomies in pure Python rather than depending on Java, and this
would speed up the process significantly.
