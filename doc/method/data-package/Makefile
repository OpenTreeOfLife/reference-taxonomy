# Creates a bunch of .tgz files, to be uploaded to Dryad one at a time.
# Be sure to upload README as well.

# This Makefile needs to be run in a directory that has just successfully built OTT 3.0 draft 3.

all: $F/ott3.0.tgz $F/separation.tgz $F/silva.tgz $F/worms.tgz		\
 $F/fung.tgz $F/ncbi.tgz $F/gbif.tgz $F/irmng.tgz $F/ $F/		\
 $F/accessions.tgz $F/amendments.tgz $F/ott2.10.tgz $F/by_qid.tgz	\
 $F/amendments.tgz $F/README

REF=../../..
F=files

$F/README: README
	cp -p $< $@

$F/ott3.0.tgz: $(REF)/tarballs/ott3.0draft3.tgz
	ln -sf $< $@

$F/alt-ott3.0.tgz:
	wget http://files.opentreeoflife.org/ott/ott3.0/ott3.0draft3.tgz

$F/separation.tgz: $(REF)/tax/separation/taxonomy.tsv
	tar czf $@ -C $(REF) tax/separation

$F/silva.tgz: $(REF)/tax/silva/taxonomy.tsv
	tar czf $@ -C $(REF) tax/silva

$F/worms.tgz: $(REF)/tax/worms/taxonomy.tsv
	tar czf $@ -C $(REF) tax/worms

$F/fung.tgz: $(REF)/tax/fung/taxonomy.tsv
	tar czf $@ -C $(REF) tax/fung

$F/ncbi.tgz: $(REF)/tax/ncbi/taxonomy.tsv
	tar czf $@ -C $(REF) tax/ncbi

$F/gbif.tgz: $(REF)/tax/gbif/taxonomy.tsv
	tar czf $@ -C $(REF) tax/gbif

$F/irmng.tgz: $(REF)/tax/irmng/taxonomy.tsv
	tar czf $@ -C $(REF) tax/irmng

# genbank accessions
$F/accessions.tgz: $(REF)/feed/silva/work/accessions.tsv
	tar czf $@  -C $(REF) feed/silva/work/accessions.tsv

# amendments
$F/amendments.tgz: $(REF)/feed/amendments/amendments-1
	tar czf $@ -C $(REF) --exclude "\\.git" --exclude LICENSE feed/amendments

# /amendments-1 
# --exclude "\\.git" --exclude LICENSE 

$F/ott2.10.tgz: $(REF)/tax/prev_/taxonomy.tsv
	tar czf $@ -C $(REF) tax/prev_ott

$F/by_qid.tgz: $(REF)/ott_id_list/by_qid.csv
	tar czf $@ -C $(REF) ott_id_list/by_qid.csv

clean: rm *.tgz
