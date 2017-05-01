# The tests work in JAR's setup...

#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

# Modify as appropriate to your own hardware - I set it one or two Gbyte
# below physical memory size
JAVAFLAGS=-Xmx14G

# Modify as appropriate
WHICH=3.1
PREV_WHICH=3.0

all: compile

EXCL=--exclude="*~" --exclude=".??*" --exclude="#*" --exclude=debug

# External URLs.  These may change from time to time

SILVA_ARCHIVE=https://www.arb-silva.de/no_cache/download/archive
NCBI_ORIGIN_URL=ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz
GBIF_ORIGIN_URL=http://rs.gbif.org/datasets/backbone/backbone-current.zip
IRMNG_ORIGIN_URL=http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip
AMENDMENTS_ORIGIN_URL=https://github.com/OpenTreeOfLife/amendments-1.git

# ----- Version selection -----

include config.mk

refresh-config: config.mk

config.mk: config.json util/update_config.py
	python util/update_config.py <config.json >config.mk

# ----- Taxonomy source locations -----

# Where to put tarballs
#TARDIR=/raid/www/roots/opentree/ott
TARDIR?=tarballs

# -----

# Smasher related configuration

CP=-classpath ".:lib/*"
JAVA=JYTHONPATH=util java $(JAVAFLAGS) $(CP)
SMASH=$(JAVA) org.opentreeoflife.smasher.Smasher
CLASS=org/opentreeoflife/smasher/Smasher.class
JAVASOURCES=$(shell find org/opentreeoflife -name "*.java")

# ----- Targets

# The open tree reference taxonomy

ott: r/$(OTT)/resource/.made

r/$(OTT)/resource/.made: r/$(OTT)/resource/log.tsv \
		       r/$(OTT)/resource/version.txt \
		       r/$(OTT)/resource/README.html
	touch $@

r/$(OTT)/resource/log.tsv: bin/jython $(CLASS) \
            make-ott.py assemble_ott.py adjustments.py amendments.py \
	    curation/separation/taxonomy.tsv \
	    r/$(SILVA)/resource/.made \
	    r/$(FUNG)/resource/.made \
	    curation/lamiales/taxonomy.tsv \
	    curation/h2007/tree.tre \
	    r/$(WORMS)/resource/.made \
	    r/$(NCBI)/resource/.made \
	    r/$(GBIF)/resource/.made \
	    r/$(IRMNG)/resource/.made \
	    r/$(AMENDMENTS)/resource/.made \
	    curation/edits/ott_edits.tsv \
	    r/$(PREV-OTT)/resource/.made \
	    r/$(IDLIST)/resource/.made \
	    ids_that_are_otus.tsv ids_in_synthesis.tsv \
	    inclusions.csv
	@date
	@rm -f *py.class util/*py.class curation/*py.class
	@mkdir -p r/$(OTT)/resource/debug
	@echo Writing transcript to r/$(OTT)/resource/debug/transcript.out
	time bin/jython make-ott.py $(WHICH) config.json r/$(OTT)/resource/ \
	  2>&1 | tee tmp/transcript.out
	mv tmp/transcript.out r/$(OTT)/resource/debug/transcript.out

r/$(OTT)/resource/version.txt:
	echo $(WHICH) >r/$(OTT)/resource/version.txt

r/$(OTT)/resource/README.html: r/$(OTT)/resource/log.tsv util/make_readme.py
	python util/make_readme.py r/$(OTT)/resource/ >$@

# ----- Taxonomy sources

# Recipe for adding a new taxonomy source x:
#
# 1. Define a rule for r/x-vvv/resource/.made to create the resource files
#    (e.g. taxonomy) from the files in source/x-vvv (or direct from archive/x-vvv).
# 2. Define a rule for new/x, creating a new source/x-vvv from stuff on the web.

# Pattern rules!  All targets phony.

fetch/%:
	bin/fetch-archive `basename $@`

# store does a pack, if necessary

store/%:
	bin/store-archive `basename $@`

# Unpack an archive (also fetch it if necessary)
# Unpack does a fetch, if necessary

unpack/%:
	bin/unpack-archive `basename $@`

pack/%:
	bin/pack-archive `basename $@`

# Imported taxonomy stored on archive server

refresh/%: new/%
	d=`basename $@`; python util/update_config.py $$d `cat r/$$d/source/name` \
	  <config.json >config.mk
	rm -rf raw/`basename $<`

# "It is possible that more than one pattern rule will meet these
# criteria. In that case, make will choose the rule with the
# shortest stem (that is, the pattern that matches most
# specifically). If more than one pattern rule has the shortest
# stem, make will choose the first one found in the makefile."

r/%-NEW/source/.made: new/%

r/%/source/.made:
	d=`dirname $@; e=`dirname $$d`; bin/unpack-archive `basename $$e`


# --- Source: SILVA
# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)

# Create the taxonomy import files from the no_sequences digest & accessions
r/$(SILVA)/resource/.made: import_scripts/silva/process_silva.py \
			 r/$(SILVA)/source/.made \
			 r/$(SILVA)/work/cluster_names.tsv
	@mkdir -p r/$(SILVA)/resource
	python import_scripts/silva/process_silva.py \
	       r/$(SILVA)/source/silva_no_sequences.fasta \
	       r/$(SILVA)/work/cluster_names.tsv \
	       r/$(SILVA)/source/origin_info.json \
	       r/$(SILVA)/resource
	touch $@

# Get taxon names from Genbank accessions file.
# The accessions file has genbank id, ncbi id, strain, taxon name.
# -- it seems the accessions file now has taxon names, so we
# probably don't need to take NCBI taxonomy as an input.
r/$(SILVA)/work/cluster_names.tsv: r/$(NCBI)/resource/.made \
				 r/$(GENBANK)/resource/accessions.tsv
	@mkdir -p `dirname $@`
	python import_scripts/silva/get_taxon_names.py \
	       r/$(NCBI)/resource/taxonomy.tsv \
	       r/$(GENBANK)/resource/accessions.tsv \
	       $@.new
	mv -f $@.new $@

# Refresh from web.
# To advance to a new SILVA release, delete raw/silva, then 'make refresh/silva'

# Digestify fasta file and create sources for archive

new/silva: r/silva-NEW/work/download.gz r/silva-NEW/work/release
	gunzip -c r/silva-NEW/work/download.gz | \
	  grep ">.*;" > r/silva-NEW/source/silva_no_sequences.fasta
	python util/origin_info.py \
	  `cat r/silva-NEW/work/date` \
	  `cat r/silva-NEW/work/origin_url` \
	  >r/silva-NEW/source/origin_info.json
	echo silva-`cat r/silva-NEW/work/release` > r/silva-NEW/source/name
	touch r/silva-NEW/source/.made
	ls -l r/silva-NEW/source

# Get the fasta file (big; for release 128, it's 150M)

r/silva-NEW/work/download.tgz: r/silva-NEW/work/release
	echo $(SILVA_ARCHIVE)/release_`cat r/silva-NEW/work/release`/Exports/SILVA_`cat r/silva-NEW/work/release`_SSURef_Nr99_tax_silva.fasta.gz >r/silva-NEW/work/origin_url
	(echo \
	 wget -q --output-file=r/silva-NEW/work/download.tgz.new \
	      `cat r/silva-NEW/work/origin_url` && \
	 mv r/silva-NEW/work/download.tgz.new r/silva-NEW/work/download.tgz)

# Figure out which release we want, before downloading, by scanning the index.html file

r/silva-NEW/work/release:
	@mkdir -p r/silva-NEW/work
	wget -q -O r/silva-NEW/work/release-index.html $(SILVA_ARCHIVE)/
	python import_scripts/silva/get_silva_release_info.py \
	  < r/silva-NEW/work/release-index.html | \
	  (read r d; echo "$$r" >r/silva-NEW/work/release; \
	  	     echo "$$d" >r/silva-NEW/work/date)

# --- Source: Digest of genbank sequence records

# -rw-r--r--+ 1 jar  staff  17773988 Dec 16 19:38 feed/silva/work/accessions.tsv
# -rw-r--r--+ 1 jar  staff   6728426 Dec  2 22:42 feed/silva/accessionid_to_taxonid.tsv
# -rw-r--r--+ 1 jar  staff      6573 Jun 28  2016 feed/genbank/accessionFromGenbank.py
# -rw-r--r--+ 1 jar  staff      1098 Oct  7  2015 feed/genbank/makeaccessionid2taxonid.py
#
# accessionFromGenbank.py reads genbank and writes Genbank.pickle
# makeaccessionid2taxonid.py reads Genbank.pickle writes accessionid_to_taxonid.tsv
#
# feed/silva/accessionid_to_taxonid.tsv:
#   As of 2017-04-25, this file was in the repository.  6.7M
#   It's an input to the SILVA build.
#   It really ought to be versioned and archived.
#   Originally made: 30 Nov 2013
#
# accessions.tsv not in repo, is more recent, includes taxon name and a column for strain.
# Created before 6 July 2016, don't know when.  But none of the rows have strain info.

r/$(GENBANK)/resource/.made: r/$(GENBANK)/source/.made
	(cd r/$(GENBANK); ln -sf source resource)

# This takes a long time - reads every flat file in genbank
# Also - be sure to update RANGES in accessionFromGenbank.py, so you
# don't miss any genbank records!  Manual process now, could be 
# automated.

r/genbank-NEW/work/Genbank.pickle: import_scripts/genbank/accessionFromGenbank.py \
	     r/$(SILVA)/source/silva_no_sequences.fasta
	mkdir -p r/genbank-NEW/work
	@echo "*** Reading all of Genbank - this can take a while!"
	python import_scripts/genbank/accessionFromGenbank.py \
	       r/$(SILVA)/source/silva_no_sequences.fasta \
	       r/genbank-NEW/work/Genbank.pickle

new/genbank: r/genbank-NEW/work/Genbank.pickle \
	     import_scripts/genbank/makeaccessionid2taxonid.py
	@echo Making accessions.tsv
	@mkdir -p r/genbank-NEW/source
	python import_scripts/genbank/makeaccessionid2taxonid.py \
	       r/genbank-NEW/work/Genbank.pickle \
	       r/genbank-NEW/source/accessions.tsv
	touch r/genbank-NEW/source/.made

newversion/genbank: r/genbank-NEW/work/Genbank.pickle
	python util/modification_date.py r/genbank-NEW/work/Genbank.pickle \
	   >r/worms-NEW/work/release

# --- Source: Index Fungorum in Open Tree form

new/fung: r/fung-1/resource/.made r/fung-3/resource/.made r/fung-4/resource/.made
	python import_scripts/fung/cobble_fung.py
	rm -rf r/fung-NEW/source
	mv hackedfung r/fung-NEW/source
	cp -p r/fung-4/resource/synonyms.tsv resource/fung-NEW
	touch r/fung-NEW/resource/.made
	echo TBD: Bump the version number and use it

# How fung-9 was created:
# import_scripts/fung/cobble_fung.py 
#   which reads fung-4, fung-2, fung-1
#
# The earlier versions were created using various versions of the
# feed/if.py (process_fungorum.py) script, operating on various files
# from Paul Kirk as unput.

# --- Source: WoRMS in Open Tree form

# WoRMS is imported by import_scripts/worms/worms.py which does a web crawl
# This rule hasn't been tested!

new/worms: import_scripts/worms/worms.py
	echo "*** Warning! This can take several days to run. ***"
	@mkdir -p r/worms-NEW/source
	python import_scripts/worms/worms.py \
	       r/worms-NEW/source/taxonomy.tsv \
	       r/worms-NEW/source/synonyms.tsv \
	       r/worms-NEW/source/worms.log
	touch r/worms-NEW/work/.today
	python util/modification_date.py r/worms-NEW/work/.today >r/worms-NEW/work/release
	echo worms-`cat r/worms-NEW/work/release` >r/worms-NEW/source/name

# --- Source: NCBI Taxonomy

# Formerly (version 1.0), where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

r/$(NCBI)/resource/.made: r/$(NCBI)/source/.made import_scripts/ncbi/process_ncbi_taxonomy.py
	@rm -rf r/$(NCBI)/resource.new
	@mkdir -p r/$(NCBI)/resource.new
	python import_scripts/ncbi/process_ncbi_taxonomy.py F r/$(NCBI)/source \
            /dev/null r/$(NCBI)/resource.new $(NCBI_ORIGIN_URL)
	rm -rf `dirname $@`
	mv -f r/$(NCBI)/resource.new `dirname $@`
	touch $@

# Refresh from web.

# We look at division.dmp just to get the date of the release.
# Could have been any of the 9 files, but that one is small.

new/ncbi: r/ncbi-NEW/source/.made r/ncbi-NEW/properties.json

r/ncbi-NEW/source/.made: r/ncbi-NEW/archive/.made
	@mkdir -p r/ncbi-NEW/source
	tar -C r/ncbi-NEW/source -xzf r/ncbi-NEW/archive/archive.tgz
	touch r/ncbi-NEW/source/.made

r/ncbi-NEW/properties.json: r/ncbi-NEW/source/.made r/ncbi-NEW/archive/.made
	echo "{}" >r/ncbi-NEW/properties.json
	bin/put ncbi-NEW series ncbi
	bin/put ncbi-NEW label \
	   `python util/modification_date.py r/ncbi-NEW/source/names.dmp`
	bin/put ncbi-NEW name ncbi-`bin/get ncbi-NEW label`
	bin/put ncbi-NEW archive_file `bin/get ncbi-NEW name`.tgz
	bin/put ncbi-NEW bytes `wc -c r/ncbi-NEW/archive/archive.tgz | (read c d && echo $$c)`
	bin/put ncbi-NEW legal pd
	bin/put ncbi-NEW ott_idspace ncbi
	echo New NCBI version is `bin/get ncbi-NEW name`

# bin/get ncbi-NEW label
# bin/put ncbi-NEW label `python util/...`

# archive_file, date, description, bytes

r/ncbi-NEW/archive/.made:
	@mkdir -p r/ncbi-NEW/archive
	wget -q --output-document=r/ncbi-NEW/archive/archive.tgz $(NCBI_ORIGIN_URL)
	touch r/ncbi-NEW/archive/.made
	@ls -l r/ncbi-NEW/archive

blah/ncbi:
	rm -rf r/`cat r/ncbi-NEW/source/name`/archive
	mv -f r/ncbi-NEW r/ncbi-`cat r/ncbi-NEW/source/release`/archive

# --- Source: GBIF

# Formerly, where it says /dev/null, we had ../data/gbif/ignore.txt

r/$(GBIF)/resource/.made: r/$(GBIF)/work/projection.tsv \
			import_scripts/gbif/process_gbif_taxonomy.py
	@mkdir -p `dirname $@`
	@mkdir -p r/$(GBIF)/resource.new
	python import_scripts/gbif/process_gbif_taxonomy.py \
	       r/$(GBIF)/work/projection.tsv \
	       r/$(GBIF)/resource.new
	touch r/$(GBIF)/resource.new/.made
	rm -rf r/$(GBIF)/resource
	mv r/$(GBIF)/resource.new r/$(GBIF)/resource

r/$(GBIF)/work/projection.tsv: r/$(GBIF)/source/.made \
			     import_scripts/gbif/project_2016.py
	@mkdir -p `dirname $@`
	python import_scripts/gbif/project_2016.py r/$(GBIF)/source/taxon.txt $@.new
	mv $@.new $@

# Get a new GBIF from the web and store to r/$(GBIF)/archive/archive.zip

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip

# should be very similar to IRMNG

new/gbif:
	@mkdir -p r/gbif-NEW/archive r/gbif-NEW/source
	wget -q --output-document=r/gbif-NEW/archive/archive.zip "$(GBIF_ORIGIN_URL)"
	touch archive/gbif-NEW/.made
	(cd r/gbif-NEW/source && (unzip r/gbif-NEW/archive/archive.zip  || true))
	echo gbif-`python util/modification_date.py source/gbif-NEW/taxon.txt` \
	   > r/gbif-NEW/source/name
	touch r/gbif-NEW/source/.made
	echo New GBIF is `cat r/gbif-NEW/source/name`

blah/gbif:
	[ -d r/`cat r/gbif-NEW/source/name` ] && (echo "collision" && exit 1)
	mv r/gbif-NEW r/`cat r/gbif-NEW/source/name`

# --- Source: IRMNG

r/$(IRMNG)/resource/.made: r/$(IRMNG)/source/.made \
			 import_scripts/irmng/process_irmng.py
	@mkdir -p `dirname $@`.new
	python import_scripts/irmng/process_irmng.py \
	   r/$(IRMNG)/source/IRMNG_DWC.csv \
	   r/$(IRMNG)/source/IRMNG_DWC_SP_PROFILE.csv \
	   r/$(IRMNG)/resource.new/taxonomy.tsv \
	   r/$(IRMNG)/resource.new/synonyms.tsv
	touch r/$(IRMNG)/resource.new/.made
	rm -rf r/$(IRMNG)/resource
	mv r/$(IRMNG)/resource.new r/$(IRMNG)/resource

# Build IRMNG from Tony's .csv files

# should be same as for GBIF

new/irmng:
	@mkdir -p r/irmng-NEW/archive r/irmng-NEW/source raw/irmng
	wget -q --output-document=r/irmng-NEW/archive/archive.zip "$(IRMNG_ORIGIN_URL)"
	touch r/irmng-NEW/archive/.made
	(cd r/irmng-NEW/source && (unzip r/irmng-NEW/archive/archive.zip || true))
	echo irmng-`python util/modification_date.py r/irmng-NEW/source/IRMNG_DWC.csv` \
	   >r/irmng-NEW/source/name
	touch r/irmng-NEW/source/.made
	echo New IRMNG is `cat r/irmng-NEW/source/name`
	[ -d archive/`cat r/irmng-NEW/source/name` ] && (echo "Already got it!"; exit 1)

blah/irmng:
	mv source/irmng-NEW source/`cat r/irmng-NEW/source/name`
	mv archive/irmng-NEW archive/`cat r/irmng-NEW/source/name`

# --- Source: Open Tree curated amendments

# Dummy
unpack/$(AMENDMENTS):
	@true

store/$(AMENDMENTS):
	@true

r/$(AMENDMENTS)/resource/.made: raw/amendments/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1 && git checkout -q $(AMENDMENTS_REFSPEC))
	@mkdir -p r/$(AMENDMENTS)/resource.new/amendments-1
	cp -pr raw/amendments/amendments-1/amendments \
	       r/$(AMENDMENTS)/resource.new/amendments-1/
	echo $(AMENDMENTS_REFSPEC) > r/$(AMENDMENTS)/resource.new/refspec
	touch r/$(AMENDMENTS)/resource.new/.made
	rm -rf r/$(AMENDMENTS)/resource
	mv r/$(AMENDMENTS)/resource.new r/$(AMENDMENTS)/resource

# fetch from github

raw/amendments/amendments-1:
	@mkdir -p raw/amendments
	(cd raw/amendments; git clone $(AMENDMENTS_ORIGIN_URL))

new/amendments: raw/amendments/amendments-1
	@mkdir -p r/amendments-NEW/source/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1; git log -n 1) | \
	  head -1 | sed -e 's/commit //' >raw/amendments/refspec.new
	mv raw/amendments/refspec.new r/amendments-NEW/source/refspec
	cp -pr raw/amendments/amendments-1/amendments r/amendments-NEW/source/amendments-1/
	echo amendments-`head -c 7 raw/amendments/refspec` >r/amendments-NEW/source/name
	touch r/amendments-NEW/source/.made

blah/amendments:
	mkdir -p r/`cat r/amendments-NEW/source/name`/source/resource/amendments-1
	@echo "TBD: STORE NEW REFSPEC AND VERSION IN config.json"

# --- Source: Previous version of OTT, for id assignments

r/$(PREV-OTT)/resource/.made: r/$(PREV-OTT)/source/.made
	(cd r/$(PREV-OTT); ln -s source resource)

# Dummy, no action required
pack/$(PREV-OTT): r/$(PREV-OTT)/archive/archive.tgz

# --- Source: OTT id list compiled from all previous OTT versions

r/$(IDLIST)/resource/.made: r/$(IDLIST)/source/.made
	(cd r/$(IDLIST); ln -sf source resource)

# When we build 3.1, IDLIST is idlist-3.0, which has ids through OTT 3.0.
# So, to make the id list for 3.1, we first make OTT (or get) 3.1, then
# combine the 3.0 id list with new registrations from 3.1.

NEW-IDLIST=idlist-$(WHICH)

new/idlist: r/$(IDLIST)/source/.made
	@rm -rf r/$(NEW-IDLIST)/source
	@mkdir -p r/$(NEW-IDLIST)/source
	cp -pr r/$(IDLIST)/source/regs r/$(NEW-IDLIST)/source/
	python import_scripts/idlist/extend_idlist.py \
	       r/$(IDLIST)/source/regs \
	       r/$(PREV-OTT)/resource \
	       $(PREV-OTT) \
	       resources/captures.json \
	       r/$(NEW-IDLIST)/source/regs/$(PREV-OTT).csv
	touch r/$(NEW-IDLIST)/source/.made

# ----- TBD: wikidata.py (from which we get EOL ids)

# Get wikidata dump from https://dumps.wikimedia.org/wikidatawiki/entities/wikidata-DDDDDDDD-all.json.gz
# e.g. DDDDDDDD = 20170424
# The .bz2 would be better (only 66% the size).

new/wikidata: import_scripts/wikidata/get_wikidata.py

# Make a digest that we can archive. 
#   source qid, wikidata id, EOL id

# To make resource, look up OTT source ids ... or maybe just use the
# digest directly

# ----- Phylesystem OTU list

# Used only for reporting!  We want to avoid having OTT depend on
# phylesystem.

# This rule typically won't run, since the target is checked in.
# To refresh, remove the target, then make it
ids_that_are_otus.tsv:
	time python util/ids_that_are_otus.py $@.new
	mv $@.new $@
	wc $@

# Synthetic tree OTT id list (this rule assumes you're part of the
# Open Tree project and can use scp; could be modified to use
# curl. this doesn't matter much since the list is checked into the
# repo.)
# To refresh, remove the target, then make it
ids_in_synthesis.tsv: bin/jython
	rm -rf synth-nexson-tmp
	mkdir -p synth-nexson-tmp
	scp -p files:"files.opentreeoflife.org/synthesis/current/output/phylo_snapshot/*@*.json" synth-nexson-tmp/
	time bin/jython util/ids_in_synthesis.py --dir synth-nexson-tmp --outfile $@.new
	mv $@.new $@

# ----- Products

# For publishing OTT drafts or releases.
# File names beginning with # are emacs lock links.
 
# Maybe not needed now that we have pack/ and store/ targets?

tarball: r/$(OTT)/resource/.made
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/$(OTT).tgz.tmp -C tax ott $(EXCL) && \
	 mv $(TARDIR)/$(OTT).tgz.tmp $(TARDIR)/$(OTT).tgz )
	@echo "Don't forget to bump the version number"

# Then, something like
# scp -p -i ~/.ssh/opentree/opentree.pem tarballs/ott2.9draft3.tgz \
#   opentree@ot10.opentreeoflife.org:files.opentreeoflife.org/ott/ott2.9/

# Not currently used since smasher already suppresses non-OTU deprecations
r/$(OTT)/resource/otu_deprecated.tsv: ids_that_are_otus.tsv r/$(OTT)/resource/deprecated.tsv
	$(SMASH) --join ids_that_are_otus.tsv r/$(OTT)/resource/deprecated.tsv >$@.new
	mv $@.new $@
	wc $@

# This file is big
r/$(OTT)/work/differences.tsv: r/$(PREV-OTT)/resource/.made r/$(OTT)/resource/.made
	$(SMASH) --diff r/$(PREV-OTT)/resource/ r/$(OTT)/resource/ $@.new
	mv $@.new $@
	wc $@

# OTUs only
r/$(OTT)/resource/debug/otu_differences.tsv: r/$(OTT)/work/differences.tsv
	$(SMASH) --join ids_that_are_otus.tsv r/$(OTT)/work/differences.tsv >$@.new
	mv $@.new $@
	wc $@

r/$(OTT)/resource/debug/otu_hidden.tsv: r/$(OTT)/resource/hidden.tsv
	$(SMASH) --join ids_that_are_otus.tsv r/$(OTT)/resource/hidden.tsv >$@.new
	mv $@.new $@
	wc $@

# The works
works: ott r/$(OTT)/resource/debug/otu_differences.tsv r/$(OTT)/resource/forwards.tsv
	touch r/$(OTT)/resource/.made

tags: $(JAVASOURCES)
	etags *.py util/*.py $(JAVASOURCES)

# ----- Libraries

lib/jython-standalone-2.7.0.jar:
	wget -q -O "$@" --no-check-certificate \
	 "http://search.maven.org/remotecontent?filepath=org/python/jython-standalone/2.7.0/jython-standalone-2.7.0.jar"
	@ls -l $@

lib/json-simple-1.1.1.jar:
	wget -q --output-document=$@ --no-check-certificate \
	  "http://search.maven.org/remotecontent?filepath=com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"
	@ls -l $@

lib/junit-4.12.jar:
	wget -q --output-document=$@ --no-check-certificate \
	  "http://search.maven.org/remotecontent?filepath=junit/junit/4.12/junit-4.12.jar"
	@ls -l $@

# -----Taxon inclusion tests

inclusions.csv:
	wget -q --output-document=$@ --no-check-certificate \
	  "https://raw.githubusercontent.com/OpenTreeOfLife/germinator/master/taxa/inclusions.csv"

# ----- Testing

# Trivial, not very useful any more
test-smasher: compile
	$(JAVA) org.opentreeoflife.smasher.Test

# internal tests
test2: $(CLASS)
	$(SMASH) --test

check:
	bash run-tests.sh

inclusion-tests: inclusions.csv bin/jython
	bin/jython util/check_inclusions.py inclusions.csv r/$(OTT)/resource/

# -----------------------------------------------------------------------------
# Asterales test system ('make test')

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: r/$(PREV-OTT)/resource/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) r/$(PREV-OTT)/resource/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on r/$(NCBI)/resource/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) r/$(NCBI)/resource/ --select2 $(TAXON) --out t/tax/ncbi_aster/

# dependency on GBIF taxonomy.tsv - correct but expensive
t/tax/gbif_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) r/$(GBIF)/resource/ --select2 $(TAXON) --out t/tax/gbif_aster/

# Previously:
#t/tax/aster/taxonomy.tsv: $(CLASS) \
#                          t/tax/ncbi_aster/taxonomy.tsv \
#                          t/tax/gbif_aster/taxonomy.tsv \
#                          t/tax/prev_aster/taxonomy.tsv \
#                          t/edits/edits.tsv
#        @mkdir -p `dirname $@`
#        $(SMASH) t/tax/ncbi_aster/ t/tax/gbif_aster/ \
#             --edits t/edits/ \
#             --ids t/tax/prev_aster/ \
#             --out t/tax/aster/

# New:
t/tax/aster/taxonomy.tsv: compile t/aster.py \
                          t/tax/ncbi_aster/taxonomy.tsv \
                          t/tax/gbif_aster/taxonomy.tsv \
                          t/tax/prev_aster/taxonomy.tsv \
                          t/edits/edits.tsv \
			  bin/jython
	@mkdir -p `dirname $@`
	bin/jython t/aster.py

t/tax/aster/README.html: t/tax/aster/about.json util/make_readme.py
	python util/make_readme.py t/tax/aster/ >$@

test: aster
aster: t/tax/aster/taxonomy.tsv t/tax/aster/README.html

aster-tarball: t/tax/aster/taxonomy.tsv
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/aster.tgz.tmp -C t/tax aster && \
	 mv $(TARDIR)/aster.tgz.tmp $(TARDIR)/aster.tgz )

# ----- Smasher

# Shorthand target
compile: $(CLASS)

# Compile the Java classes
$(CLASS): $(JAVASOURCES) \
	  lib/jython-standalone-2.7.0.jar \
	  lib/json-simple-1.1.1.jar \
	  lib/junit-4.12.jar
	javac -g $(CP) $(JAVASOURCES)

# Script to start up jython (with OTT classes preloaded)
bin/jython:
	mkdir -p bin
	(echo "#!/bin/bash"; \
	 echo "export JYTHONPATH=.:$$PWD:$$PWD/util:$$PWD/lib/json-simple-1.1.1.jar"; \
	 echo exec java "$(JAVAFLAGS)" -jar $$PWD/lib/jython-standalone-2.7.0.jar '$$*') >$@
	chmod +x $@

# Script to start up the background daemon
bin/smasher:
	mkdir -p bin
	(echo "#!/bin/bash"; \
	 echo "cd $$PWD/service"; \
	 echo ./service '$$*') >$@
	chmod +x $@

# ----- Clean

# The 'clean' target deletes everything except files fetched from the Internet.
# To really delete everything, use the 'distclean' target.

clean:
	rm -f `find . -name "*.class"`
	rm -rf bin/jython
	rm -rf r/$(OTT)
	rm -rf *.tmp new_taxa suffix
	rm -rf r/*/resource
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf r tmp raw
