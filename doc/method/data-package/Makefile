# Creates a bunch of .tgz files, to be uploaded to Dryad one at a time.
# Be sure to upload README as well.

# This Makefile needs to be run in a directory that has successfully
# built OTT 3.0, so that all the processed source taxonomies are available.

F=files
REF=../../..
DRAFT=

all: $F/README $F/ott3.0.tgz $F/separation.tgz \
  $F/accessions.tgz $F/amendments.tgz $F/ott2.10.tgz $F/by_qid.tgz 

# Decided not to include these
# $F/silva.tgz $F/worms.tgz $F/fung.tgz $F/ncbi.tgz $F/gbif.tgz $F/irmng.tgz

$F/README: README
	mkdir -p $F
	cp -p $< $@

$F/ott3.0.tgz: cache/ott3.0.tgz readme-ott-3.0.txt
	rm -rf ott tax/ott3.0
	mkdir -p tax $F
	tar xzf cache/ott3.0.tgz \
	    ott/taxonomy.tsv ott/synonyms.tsv ott/forwards.tsv ott/version.txt
	mv ott tax/ott3.0
	cp -p readme-ott-3.0.txt tax/ott3.0/README
	tar czf $@ tax/ott3.0

# For assigning identifiers only

$F/ott2.10.tgz: cache/ott2.10.tgz readme-ott-2.10.txt
	rm -rf ott tax/ott2.10
	mkdir -p tax $F
	tar xzf cache/ott2.10.tgz \
	    ott/taxonomy.tsv ott/synonyms.tsv ott/forwards.tsv ott/version.txt
	mv ott tax/ott2.10
	cp -p readme-ott-3.0.txt tax/ott2.10/README
	tar czf $@ tax/ott2.10

cache/ott3.0.tgz:
	mkdir -p cache
	wget -O $@.new http://files.opentreeoflife.org/ott/ott3.0/ott3.0.tgz
	mv $@.new $@

cache/ott2.10.tgz:
	mkdir -p cache
	wget -O $@.new http://files.opentreeoflife.org/ott/ott2.10/ott2.10.tgz
	mv $@.new $@

$F/by_qid.tgz: cache/by_qid.csv.gz
	(cd cache; gunzip by_qid.csv.gz)
	mkdir -p ott_id_list
	mv cache/by_qid.csv ott_id_list/
	tar czf $@ ott_id_list

cache/by_qid.csv.gz:
	mkdir -p cache
	wget -O $@.new http://files.opentreeoflife.org/idlist/idlist-20161118/by_qid.csv.gz
	mv $@.new $@

# Copy the separation taxonomy from the git repo.
$F/separation.tgz: $(REF)/tax/separation/taxonomy.tsv
	tar czf $@ -C $(REF) --exclude "*~" tax/separation

# Genbank accessions
$F/accessions.tgz: $(REF)/feed/silva/work/accessions.tsv
	tar czf $@  -C $(REF) feed/silva/work/accessions.tsv

# Curator additions
$F/amendments.tgz: $(REF)/feed/amendments/amendments-1
	tar czf $@ -C $(REF) --exclude "\\.git" --exclude LICENSE feed/amendments

clean:
	rm -rf files tax

# Don't put these in the data package.

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

