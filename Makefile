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

EXCL=--exclude="*~" --exclude=".??*"

# ----- Version selection -----

include config.mk

refresh-config: config.mk

config.mk: config.json
	python util/update_config.py <config.json >config.mk

# ----- Taxonomy source locations -----

WORMS_URL=http://files.opentreeoflife.org/worms/$(WORMS)/$(WORMS)-ot.tgz

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip
GBIF_URL=http://files.opentreeoflife.org/gbif/gbif-20160729/gbif-20160729.zip

IRMNG_URL=http://files.opentreeoflife.org/irmng-ot/irmng-ot-20161108/irmng-ot-20161108.tgz

# This is used as a source of OTT id assignments.
PREV_OTT_URL=http://files.opentreeoflife.org/ott/ott$(PREV_WHICH)/ott$(PREV_WHICH).tgz

IDLIST_URL="http://files.opentreeoflife.org/idlist/idlist-20161118/by_qid.csv.gz"

# 9 Sep 2016
AMENDMENTS_REFSPEC=feed/amendments/refspec

# Where to put tarballs
#TARDIR=/raid/www/roots/opentree/ott
TARDIR?=tarballs

# -----

# Smasher related variables

CP=-classpath ".:lib/*"
JAVA=JYTHONPATH=util java $(JAVAFLAGS) $(CP)
SMASH=$(JAVA) org.opentreeoflife.smasher.Smasher
CLASS=org/opentreeoflife/smasher/Smasher.class
JAVASOURCES=$(shell find org/opentreeoflife -name "*.java")

# ----- Targets

# The open tree reference taxonomy

ott: tax/ott/log.tsv tax/ott/version.txt tax/ott/README.html
tax/ott/log.tsv: $(CLASS) make-ott.py assemble_ott.py adjustments.py amendments.py \
                    tax/silva/taxonomy.tsv \
		    import/fung/taxonomy.tsv tax/713/taxonomy.tsv \
		    tax/ncbi/taxonomy.tsv tax/gbif/taxonomy.tsv \
		    tax/irmng/taxonomy.tsv \
		    tax/worms/taxonomy.tsv \
		    feed/ott/edits/ott_edits.tsv \
		    tax/prev_ott/taxonomy.tsv \
		    feed/misc/chromista_spreadsheet.py \
		    ids_that_are_otus.tsv \
		    bin/jython \
		    inclusions.csv \
		    feed/amendments/amendments-1/next_ott_id.json \
		    tax/separation/taxonomy.tsv \
		    feed/ott_id_list/by_qid.csv
	@date
	@rm -f *py.class
	@mkdir -p tax/ott
	@echo Writing transcript to tax/ott/transcript.out
	time bin/jython make-ott.py $(WHICH) 2>&1 | tee tax/ott/transcript.out.new
	mv tax/ott/transcript.out.new tax/ott/transcript.out
	echo $(WHICH) >tax/ott/version.txt

tax/ott/version.txt:
	echo $(WHICH) >tax/ott/version.txt

tax/ott/README.html: tax/ott/log.tsv util/make_readme.py
	python util/make_readme.py tax/ott/ >$@

# ----- Taxonomy sources

# Recipe for adding a new taxonomy source x:
#
# 1. Define 'make refresh-x' rule, creating archive/x/x-vvv
#    1a. If the archive is anything other than a direct copy of a file 
#        from the web, also create source/x/x-vvv (a compilation, digest, 
#	 or subset of stuff found on the web)
# 2. Define 'make x' rule, which creates import/x/x-vvv
#    2a. 'make fetch-x' should get archive/x/x-vvv from the files server
#    2b. source/x/x-vvv can just be the extraction of archive/x/x-vvv
#    2c. import/x/x-vvv gets generated from source/x/x-vvv (or in 
#    	 cases where an OT-format taxonomy is archived,, directly from
# 	 archive/x/x-vvv)
# 3. Define 'make archive-x' to create archive/x/x-vvv from source/x/x-vvv
#    (opposite direction to 2b) and copy the archive file to the files
#    server

# Input: SILVA
# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)

SILVA_URL=http://files.opentreeoflife.org/fung/$(SILVA)/$(SILVA).tgz

archive/silva/$(SILVA)/$(SILVA).tgz:
	@mkdir -p `dirname $@`
	wget --output-document=$@.new $(SILVA_URL)
	mv $@.new $@

fetch-silva: archive/silva/$(SILVA)/$(SILVA).tgz
	ls -l archive/silva/$(SILVA)

source/silva/$(SILVA)/.source: archive/silva/$(SILVA)/$(SILVA).tgz
	tar -C source/silva -xzvf $<
	touch $@

# Get taxon names from Genbank accessions file.
# The accessions file has genbank id, ncbi id, strain, taxon name.
# *** maybe put accessionid_to_taxonid.tsv into the digest? ***
source/silva/$(SILVA)/cluster_names.tsv: source/silva/$(SILVA)/silva_no_sequences.txt \
				import/ncbi/$(NCBI)/taxonomy.tsv \
				feed/silva/accessionid_to_taxonid.tsv
	python import_scripts/silva/get_taxon_names.py \
	       import/ncbi/$(NCBI)/taxonomy.tsv \
	       feed/silva/accessionid_to_taxonid.tsv \
	       $@.new
	mv $@.new $@

# Create the taxonomy import files from the no_sequences digest & accessions
import/silva/$(SILVA)/taxonomy.tsv: import_scripts/silva/process_silva.py source/silva/$(SILVA)/silva_no_sequences.txt \
            source/silva/$(SILVA)/cluster_names.tsv 
	@mkdir -p import/silva/$(SILVA)
	python import_scripts/silva/process_silva.py \
	       source/silva/$(SILVA)/silva_no_sequences.txt \
	       source/silva/$(SILVA)/cluster_names.tsv \
	       source/silva/$(SILVA)/origin_info.json \
	       import/silva/$(SILVA)

import-silva: import/silva/$(SILVA)/taxonomy.tsv

silva: import/silva/$(SILVA)/taxonomy.tsv
	(cd tax; ln -sf ../`dirname $<` silva)

# Archive.

archive-silva:
	@mkdir -p archive/silva/$(SILVA)
	[ -d source/silva/$(SILVA) ] || \
	  (echo "Inputs to tar not available"; exit 1)
	tar -C source/silva -cvzf archive/silva/$(SILVA)/$(SILVA).tgz $(SILVA) \
	  $(EXCL)
	bin/publish-taxonomy silva $(SILVA) .tgz

# Refresh from web.

# Make the new version the one to be used in assembly
refresh-silva: get-silva-release
	python util/update_config.py silva silva-`cat raw/silva/release` \
	  <config.json >config.mk

SILVA_ARCHIVE=https://www.arb-silva.de/no_cache/download/archive

# To advance to a new SILVA release, delete this file, then 'make refresh-silva'
raw/silva/release:
	@mkdir -p raw/silva
	wget -q -O raw/silva/release-index.html $(SILVA_ARCHIVE)/
	python import_scripts/silva/get_silva_release_info.py \
	  <raw/silva/release-index.html | \
	  (read r d; echo "$$r" >$@; echo "$$d" >raw/silva/date)

# Get the fasta file (big; for release 128, it's 150M)
raw/silva/silva.gz: raw/silva/release
	mkdir -p raw/silva 
	echo $(SILVA_ARCHIVE)/release_`cat raw/silva/release`/Exports/SILVA_`cat raw/silva/release`_SSURef_Nr99_tax_silva.fasta.gz >raw/silva/origin_url
	wget -O $@ `cat raw/silva/origin_url`
	touch $@

# Get latest SILVA from web; extract fasta file; make digest
get-silva-release: raw/silva/silva.gz
	@mkdir -p source/silva/silva-`cat raw/silva/release`
	@mkdir -p archive/silva/silva-`cat raw/silva/release`
	gunzip -c raw/silva/silva.gz >raw/silva/silva.fasta
	grep ">.*;" raw/silva/silva.fasta > source/silva/silva-`cat raw/silva/release`/silva_no_sequences.txt
	python util/origin_info.py \
	  `cat raw/silva/date` \
	  `cat raw/silva/origin_url` \
	  >source/silva/silva-`cat raw/silva/release`/origin_info.json
	rm raw/silva/silva.fasta
	ls -l source/silva/silva-`cat raw/silva/release`
	tar -C source/silva -cvzf archive/silva/silva-`cat raw/silva/release`.tgz \
	  silva-`cat raw/silva/release` $(EXCL)

# Input: Genbank sequence records

# One file, feed/silva/accessionid_to_taxonid.tsv
# As of 2017-04-25, this file was in the repository.  6.7M
# It really ought to be versioned and archived.

# -rw-r--r--+ 1 jar  staff  17773988 Dec 16 19:38 feed/silva/work/accessions.tsv
# -rw-r--r--+ 1 jar  staff   6728426 Dec  2 22:42 feed/silva/accessionid_to_taxonid.tsv
# -rw-r--r--+ 1 jar  staff      6573 Jun 28  2016 feed/genbank/accessionFromGenbank.py
# -rw-r--r--+ 1 jar  staff      1098 Oct  7  2015 feed/genbank/makeaccessionid2taxonid.py
#
# accessionFromGenbank.py reads genbank and writes Genbank.pickle
# makeaccessionid2taxonid.py reads Genbank.pickle writes accessionid_to_taxonid.tsv


archive/genbank/$(GENBANK)/$(GENBANK).tgz:
	echo foo


# Input: Index Fungorum

# 12787947 Oct  6  2015 taxonomy.tsv
FUNG_URL=http://files.opentreeoflife.org/fung/$(FUNG)/$(FUNG)-ot.tgz

archive/fung/$(FUNG)/$(FUNG)-ot.tgz:
	@mkdir -p `dirname $@`
	wget --output-document=$@.new $(FUNG_URL)
	mv $@.new $@

fetch-fung: archive/fung/$(FUNG)/$(FUNG)-ot.tgz
	ls -l archive/fung/$(FUNG)

import/fung/$(FUNG)/taxonomy.tsv: archive/fung/$(FUNG)/$(FUNG)-ot.tgz
	@rm -rf tmp/fung
	@mkdir -p import/fung/$(FUNG) tmp/fung
	(cd tmp/fung; tar xzf -) < $<
	@ls -l tmp/fung
	mv tmp/fung/*/* `dirname $@`/
	rm -rf tmp/fung
	@ls -l `dirname $@`

import-fung: import/fung/$(FUNG)/taxonomy.tsv

fung: import/fung/$(FUNG)/taxonomy.tsv
	(cd tax; ln -sf ../`dirname $<` fung)

# Refresh fung... hmm... incomplete

source/fung/$(FUNG)/about.json:
	@mkdir -p `dirname $@`
	cp -p feed/fung/about.json tax/fung/

# Input: NCBI Taxonomy

# Formerly, where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

NCBI_URL="http://files.opentreeoflife.org/ncbi/$(NCBI)/$(NCBI).tgz"

archive/ncbi/$(NCBI)/$(NCBI).tgz:
	@mkdir -p `dirname $@`
	wget --output-document=$@.new $(NCBI_URL)
	mv $@.new $@

fetch-ncbi: archive/ncbi/$(NCBI)/$(NCBI).tgz
	ls -l archive/ncbi/$(NCBI)

source/ncbi/$(NCBI)/.source: archive/ncbi/$(NCBI)/$(NCBI).tgz
	@mkdir -p `dirname $@`
	tar -C `dirname $@` -xzvf $<
	touch $@

import/ncbi/$(NCBI)/.import: source/ncbi/$(NCBI)/.source import_scripts/ncbi/process_ncbi_taxonomy.py
	@rm -rf tmp/ncbi
	@mkdir -p tmp/ncbi
	python import_scripts/ncbi/process_ncbi_taxonomy.py F source/ncbi/$(NCBI) \
            /dev/null tmp/ncbi $(NCBI_URL)
	rm -rf `dirname $@`
	mv -f tmp/ncbi `dirname $@`
	touch $@

import-ncbi: import/ncbi/$(NCBI)/.import

ncbi: import/ncbi/$(NCBI)/taxonomy.tsv
	(cd tax; ln -sf ../`dirname $<` ncbi)

# Archive

archive-ncbi:
	bin/publish-taxonomy ncbi $(NCBI) .tgz

# Refresh from web.

NCBI_ORIGIN_URL=ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz
NCBI_TAXDUMP=raw/ncbi/taxdump.tar.gz

refresh-ncbi: get-ncbi-release
	python util/update_config.py ncbi ncbi-`cat raw/ncbi/release` \
	  <config.json >config.mk

# We look at division.dmp just to get the date of the release.
# Could have been any of the 9 files, but that one is small.

get-ncbi-release:
	@mkdir -p raw/ncbi
	wget --output-document=$(NCBI_TAXDUMP).new $(NCBI_ORIGIN_URL)
	mv -f $(NCBI_TAXDUMP).new $(NCBI_TAXDUMP)
	@ls -l $(NCBI_TAXDUMP)
	tar -C raw/ncbi -xvzf $(NCBI_TAXDUMP) division.dmp
	@ls -l raw/ncbi
	python util/modification_date.py raw/ncbi/division.dmp >raw/ncbi/release
	echo New NCBI version is `cat raw/ncbi/release`
	rm raw/ncbi/division.dmp
	mkdir -p archive/ncbi/ncbi-`cat raw/ncbi/release`
	mv -f $(NCBI_TAXDUMP) archive/ncbi/ncbi-`cat raw/ncbi/release`/ncbi-`cat raw/ncbi/release`.tgz

# Formerly, where it says /dev/null, we had ../data/gbif/ignore.txt

gbif: tax/gbif/taxonomy.tsv

feed/gbif/work/projection_2016.tsv: feed/gbif/in/taxon.txt feed/gbif/project_2016.py
	@mkdir -p feed/gbif/work
	python feed/gbif/project_2016.py feed/gbif/in/taxon.txt $@.new
	mv $@.new $@

feed/gbif/work/projection_2013.tsv: feed/gbif/in/2013/taxon.txt feed/gbif/project_2013.py
	@mkdir -p feed/gbif/work
	python feed/gbif/project_2013.py feed/gbif/in/2013/taxon.txt $@.new
	mv $@.new $@

GBIF_VERSION=2016

tax/gbif/taxonomy.tsv: feed/gbif/work/projection_$(GBIF_VERSION).tsv feed/gbif/process_gbif_taxonomy.py
	@mkdir -p tax/gbif.tmp
	python feed/gbif/process_gbif_taxonomy.py \
	       feed/gbif/work/projection_$(GBIF_VERSION).tsv \
	       tax/gbif.tmp
	cp -p feed/gbif/about.json tax/gbif.tmp/
	rm -rf tax/gbif
	mv -f tax/gbif.tmp tax/gbif

# The '|| true' is because unzip erroneously returns status code 1
# when there are warnings.
feed/gbif/in/taxon.txt: feed/gbif/in/gbif-backbone.zip
	(cd feed/gbif/in && (unzip gbif-backbone.zip || true))
	touch feed/gbif/in/taxon.txt

feed/gbif/in/gbif-backbone.zip:
	@mkdir -p feed/gbif/in
	wget --output-document=$@.new "$(GBIF_URL)"
	mv $@.new $@
	@ls -l $@

GBIF_SOURCE_URL=http://rs.gbif.org/datasets/backbone/backbone-current.zip

refresh-gbif:
	@mkdir -p feed/gbif/in
	wget --output-document=gbif.new "$(GBIF_SOURCE_URL)"
	rm -f feed/gbif/in/gbif-backbone.zip
	mv gbif.new feed/gbif/in/gbif-backbone.zip

# TBD: a publication rule for new GBIF versions.
# publish-gbif
#	bin/publish-taxonomy gbif


# Input: WoRMS
# This is assembled by feed/worms/process_worms.py which does a web crawl

tax/worms/taxonomy.tsv:
	@mkdir -p tax/worms tmp
	wget --output-document=tmp/$(WORMS)-ot.tgz $(WORMS_URL)
	(cd tmp; tar xzf $(WORMS)-ot.tgz)
	rm -f tax/worms/*
	mv tmp/$(WORMS)-ot*/* tax/worms/

# Input: IRMNG

irmng: tax/irmng/taxonomy.tsv

tax/irmng/taxonomy.tsv:
	@mkdir -p tmp/irmng-ot
	wget --output-document=tmp/irmng-ot.tgz $(IRMNG_URL)
	(cd tmp/irmng-ot; tar xzf ../irmng-ot.tgz)
	(rm -rf tax/irmng && \
	 mv -f tmp/irmng-ot/* tax/irmng && \
	 rm -rf tmp/irmng-ot)

# Build IRMNG from Tony's .csv files - these files unfortunately are
# not public

refresh-irmng: feed/irmng/process_irmng.py feed/irmng/in/IRMNG_DWC.csv 
	@mkdir -p feed/irmng/out
	python feed/irmng/process_irmng.py \
	   feed/irmng/in/IRMNG_DWC.csv \
	   feed/irmng/in/IRMNG_DWC_SP_PROFILE.csv \
	   feed/irmng/out/taxonomy.tsv \
	   feed/irmng/out/synonyms.tsv
	rm -rf tax/irmng
	mv feed/irmng/out tax/irmng

feed/irmng/in/IRMNG_DWC.csv: feed/irmng/in/IRMNG_DWC.zip
	(cd feed/irmng/in && \
	 unzip IRMNG_DWC.zip && \
	 mv IRMNG_DWC_2???????.csv IRMNG_DWC.csv && \
	 mv IRMNG_DWC_SP_PROFILE_2???????.csv IRMNG_DWC_SP_PROFILE.csv)

feed/irmng/in/IRMNG_DWC.zip:
	@mkdir -p `dirname $@`
	wget --output-document=$@.new "http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip"
	mv $@.new $@

irmng-tarball:
	(mkdir -p $(TARDIR) && \
	 d=`date "+%Y%m%d"` && \
	 echo Today is $$d && \
	 cp -prf tax/irmng tax/irmng-ot-$$d && \
	 tar czvf $(TARDIR)/irmng-ot-$$d.tgz.tmp -C tax irmng-ot-$$d && \
	 mv $(TARDIR)/irmng-ot-$$d.tgz.tmp $(TARDIR)/irmng-ot-$$d.tgz )

publish-irmng:
	bin/publish-taxonomy irmng-ot irmng-ot-zzz .tgz


# ----- Katz lab Protista/Chromista parent assignments

z: feed/misc/chromista_spreadsheet.py
feed/misc/chromista_spreadsheet.py: feed/misc/chromista-spreadsheet.csv feed/misc/process_chromista_spreadsheet.py
	python feed/misc/process_chromista_spreadsheet.py \
           feed/misc/chromista-spreadsheet.csv >feed/misc/chromista_spreadsheet.py

# ----- Amendments

fetch_amendments: feed/amendments/amendments-1/next_ott_id.json

feed/amendments/amendments-1/next_ott_id.json: feed/amendments/amendments-1 $(AMENDMENTS_REFSPEC)
	(cd feed/amendments/amendments-1 && git checkout master && git pull)
	(cd feed/amendments/amendments-1; git checkout -q `cat ../../../$(AMENDMENTS_REFSPEC)`)

refresh-amendments: feed/amendments/amendments-1
	(cd feed/amendments/amendments-1 && git checkout master && git pull)
	(cd feed/amendments/amendments-1; git log -n 1) | head -1 | sed -e 's/commit //' >$(AMENDMENTS_REFSPEC).new
	mv $(AMENDMENTS_REFSPEC).new $(AMENDMENTS_REFSPEC)
	(cd feed/amendments/amendments-1; git checkout -q `cat ../../../$(AMENDMENTS_REFSPEC)`)

feed/amendments/amendments-1:
	@mkdir -p feed/amendments
	(cd feed/amendments; git clone https://github.com/OpenTreeOfLife/amendments-1.git)

# ----- Previous version of OTT, for id assignments

tax/prev_ott/taxonomy.tsv:
	@mkdir -p tmp 
	wget --output-document=tmp/prev_ott.tgz $(PREV_OTT_URL)
	@ls -l tmp/prev_ott.tgz
	(cd tmp/ && tar xvf prev_ott.tgz)
	rm -rf tax/prev_ott
	@mkdir -p tax/prev_ott
	mv tmp/ott*/* tax/prev_ott/
	if [ -e tax/prev_ott/taxonomy ]; then mv tax/prev_ott/taxonomy tax/prev_ott/taxonomy.tsv; fi
	if [ -e tax/prev_ott/synonyms ]; then mv tax/prev_ott/synonyms tax/prev_ott/synonyms.tsv; fi
	rm -rf tmp

# Source: OTT id list compiled from all previous OTT versions

import/idlist/by_qid.csv: source/idlist/by_qid.csv
	... link or copy ??? ...
	ln -sf $< $@

source/idlist/by_qid.csv: stage/idlist/by_qid.tgz stage/idlist/meta.json
	(cd stage/idlist; gunzip by_qid.tgz)
	mv -f stage/idlist/*/* $@
	cp -p stage/idlist/meta.json source/idlist/meta.json

stage/idlist/by_qid.tgz: stage/idlist/about.json
	... which version? ...
	... version is in stage/idlist/meta.json ...
	python util/archivetool.py get idlist stage/idlist/about.json archive/idlist 
	... writes archive/, links from stage/ ...

refresh-idlist:
	(cd ott_id_list; make)
	mv ott_id_list/by_qid.csv source/idlist/
	... create source/idlist/meta.json ...
	python util/archivetool.py refresh idlist source stage archive by_qid.csv | bash
	... ??? dir name should include version number ...
	... tar czvf stage/idlist/by_qid.tgz -C stage idlist-xxx/by_qid.csv

archive-idlist: source/idlist/about.json    # or stage/ ???
	...

# ,,,,,,,,,,,

feed/ott_id_list/by_qid.csv:
	@mkdir -p feed/ott_id_list

	@mkdir -p tmp
	@mkdir -p `dirname $@`
	wget --output-document=tmp/by_qid.csv.gz $(IDLIST_URL)
	(cd tmp; gunzip by_qid.csv.gz)
	mv tmp/by_qid.csv `dirname $@`/
	@ls -l $@

# ----- Phylesystem OTU list

# This rule typically won't run, since the target is checked in
ids_that_are_otus.tsv:
	time python util/ids_that_are_otus.py $@.new
	mv $@.new $@
	wc $@

# Synthetic tree OTT id list (this rule assumes you're part of the
# Open Tree project and can use scp; could be modified to use
# curl. this doesn't matter much since the list is checked into the
# repo.)
ids_in_synthesis.tsv: bin/jython
	rm -rf synth-nexson-tmp
	mkdir -p synth-nexson-tmp
	scp -p files:"files.opentreeoflife.org/synthesis/current/output/phylo_snapshot/*@*.json" synth-nexson-tmp/
	time bin/jython util/ids_in_synthesis.py --dir synth-nexson-tmp --outfile $@.new
	mv $@.new $@


# ----- Preottol - for filling in the preottol id column
# No longer used
# PreOTToL is here if you're interested:
#  https://bitbucket.org/mtholder/ottol/src/dc0f89986c6c2a244b366312a76bae8c7be15742/preOTToL_20121112.txt?at=master
PREOTTOL=../../preottol

# Create the aux (preottol) mapping in a separate step.
# How does it know where to write to?

tax/ott/aux.tsv: $(CLASS) tax/ott/log.tsv
	$(SMASH) tax/ott/ --aux $(PREOTTOL)/preottol-20121112.processed

$(PREOTTOL)/preottol-20121112.processed: $(PREOTTOL)/preOTToL_20121112.txt
	python util/process-preottol.py $< $@

# ----- Products

# For publishing OTT drafts or releases.
# File names beginning with # are emacs lock links.
 
tarball: tax/ott/log.tsv tax/ott/version.txt
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/ott$(WHICH).tgz.tmp -C tax ott \
	   --exclude differences.tsv --exclude "#*" && \
	 mv $(TARDIR)/ott$(WHICH).tgz.tmp $(TARDIR)/ott$(WHICH).tgz )
	@echo "Don't forget to bump the version number"

# Then, something like
# scp -p -i ~/.ssh/opentree/opentree.pem tarballs/ott2.9draft3.tgz \
#   opentree@ot10.opentreeoflife.org:files.opentreeoflife.org/ott/ott2.9/

# Not currently used since smasher already suppresses non-OTU deprecations
tax/ott/otu_deprecated.tsv: ids_that_are_otus.tsv tax/ott/deprecated.tsv
	$(SMASH) --join ids_that_are_otus.tsv tax/ott/deprecated.tsv >$@.new
	mv $@.new $@
	wc $@

# This file is big
tax/ott/differences.tsv: tax/prev_ott/taxonomy.tsv tax/ott/taxonomy.tsv
	$(SMASH) --diff tax/prev_ott/ tax/ott/ $@.new
	mv $@.new $@
	wc $@

# OTUs only
tax/ott/otu_differences.tsv: tax/ott/differences.tsv
	$(SMASH) --join ids_that_are_otus.tsv tax/ott/differences.tsv >$@.new
	mv $@.new $@
	wc $@

tax/ott/otu_hidden.tsv: tax/ott/hidden.tsv
	$(SMASH) --join ids_that_are_otus.tsv tax/ott/hidden.tsv >$@.new
	mv $@.new $@
	wc $@

# The works
works: ott tax/ott/otu_differences.tsv tax/ott/forwards.tsv

tags: $(JAVASOURCES)
	etags *.py util/*.py $(JAVASOURCES)

# ----- Libraries

lib/jython-standalone-2.7.0.jar:
	wget -O "$@" --no-check-certificate \
	 "http://search.maven.org/remotecontent?filepath=org/python/jython-standalone/2.7.0/jython-standalone-2.7.0.jar"
	@ls -l $@

lib/json-simple-1.1.1.jar:
	wget --output-document=$@ --no-check-certificate \
	  "http://search.maven.org/remotecontent?filepath=com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"
	@ls -l $@

lib/junit-4.12.jar:
	wget --output-document=$@ --no-check-certificate \
	  "http://search.maven.org/remotecontent?filepath=junit/junit/4.12/junit-4.12.jar"
	@ls -l $@

# -----Taxon inclusion tests

# OK to override this locally, e.g. with
# ln -sf ../germinator/taxa/inclusions.tsv inclusions.tsv,
# so you can edit the file in the other repo.

inclusions.csv:
	wget --output-document=$@ --no-check-certificate \
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
	bin/jython util/check_inclusions.py inclusions.csv tax/ott/

# -----------------------------------------------------------------------------
# Asterales test system ('make test')

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: tax/prev_ott/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) tax/prev_ott/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on import/ncbi/$(NCBI)/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) import/ncbi/$(NCBI)/ --select2 $(TAXON) --out t/tax/ncbi_aster/

# dependency on tax/gbif/taxonomy.tsv - correct but expensive
t/tax/gbif_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) tax/gbif/ --select2 $(TAXON) --out t/tax/gbif_aster/

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
	rm -rf tax/ott
	rm -rf feed/*/out feed/*/work
	rm -rf *.tmp new_taxa
	rm -rf import/ncbi/$(NCBI) tax/gbif tax/silva
	rm -f feed/misc/chromista_spreadsheet.py
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf feed/amendments/amendments-1
	rm -rf feed/*/in
	rm -rf tax/fung tax/irmng* tax/worms 
	rm -rf tax/prev_ott
