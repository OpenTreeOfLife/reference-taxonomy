#!/bin/bash

# Try out the IRMNG-to-smasher conversion using a small subset of
# IRMNG (for more rapid debugging)

set -e

N=500000
echo "Using $N records"

head -$N feed/irmng/in/IRMNG_DWC.csv >/tmp/irmng-sample.csv
head -$N feed/irmng/in/IRMNG_DWC_SP_PROFILE.csv  >/tmp/irmng-sample_sp_profile.csv

dir=irmng-sample.tax

mkdir -p $dir

python feed/irmng/process_irmng.py /tmp/irmng-sample.csv /tmp/irmng-sample_sp_profile.csv $dir/taxonomy.tsv $dir/synonyms.tsv
echo "Wrote files to $dir/"
