# OTT id list

This script creates a master list of all OTT ids, past and present.

`make` runs the script, assuming that directory
`../../files.opentreeoflife.org/ott` contains a set of directories,
one per OTT version; each directory in turn contains a taxonomy
directory containing a taxonomy.tsv file, i.e.

    ../../files.opentreeoflife.org/ott/ott1.0/ott1.0/taxonomy.tsv
    ../../files.opentreeoflife.org/ott/ott2.0/ott2.0/taxonomy.tsv

etc.  except that the '.tsv' is optional.

The output is a lexicographically ordered set of files in the ott_id_list subdirectory.
Each file is a CSV with one row per id.
The name of the file indicates which OTT version it's associated with, if any.
The columns are:

1. the OTT id
2. the source id
3. the unique id of the particular source taxonomy version
4. empty, or 'was x' where x is a new source id, or 'dup y' if this OTT id is a duplicate

The output set will contain more than one row for a given OTT id if a
later taxonomy assigns a new source identifier with the OTT id.  The
lines are marked with "was xxx" in column 4.

It takes about 20 minutes for the script to run (on a 2014 MacBook Pro
16G).  Most of the time is taken in reading the taxonomy files.

