# Creates a bunch of .tgz files, to be uploaded to Dryad one at a time.
# Be sure to upload README as well.

# This Makefile needs to be run in a directory that has just successfully built OTT 3.0 draft 3.

all: ott3.0.tgz separation.tgz silva.tgz worms.tgz fung.tgz ncbi.tgz gbif.tgz irmng.tgz \
  accessions.tgz amendments.tgz ott2.10.tgz by_qid.tgz amendments.tgz README

REF=../../..

ott3.0.tgz: $(REF)/tarballs/ott3.0draft3.tgz
	ln -sf $< $@

alt-ott3.0.tgz:
	wget http://files.opentreeoflife.org/ott/ott3.0/ott3.0draft3.tgz

separation.tgz: $(REF)/tax/separation/taxonomy.tsv
	tar czf $@ -C $(REF) tax/separation

silva.tgz: $(REF)/tax/silva/taxonomy.tsv
	tar czf $@ -C $(REF) tax/silva

worms.tgz: $(REF)/tax/worms/taxonomy.tsv
	tar czf $@ -C $(REF) tax/worms

fung.tgz: $(REF)/tax/fung/taxonomy.tsv
	tar czf $@ -C $(REF) tax/fung

ncbi.tgz: $(REF)/tax/ncbi/taxonomy.tsv
	tar czf $@ -C $(REF) tax/ncbi

gbif.tgz: $(REF)/tax/gbif/taxonomy.tsv
	tar czf $@ -C $(REF) tax/gbif

irmng.tgz: $(REF)/tax/irmng/taxonomy.tsv
	tar czf $@ -C $(REF) tax/irmng

# genbank accessions
accessions.tgz: $(REF)/feed/silva/work/accessions.tsv
	tar czf $@  -C $(REF) feed/silva/work/accessions.tsv

# amendments
amendments.tgz: $(REF)/feed/amendments/amendments-1
	tar czf $@ -C $(REF) --exclude "\\.git" --exclude LICENSE feed/amendments

# /amendments-1 
# --exclude "\\.git" --exclude LICENSE 

ott2.10.tgz: $(REF)/tax/prev_/taxonomy.tsv
	tar czf $@ -C $(REF) tax/prev_ott

by_qid.tgz: $(REF)/ott_id_list/by_qid.csv
	tar czf $@ -C $(REF) ott_id_list/by_qid.csv

clean: rm *.tgz
