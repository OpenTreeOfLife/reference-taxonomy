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

ott: resource/ott/$(OTT)/log.tsv resource/ott/$(OTT)/version.txt resource/ott/$(OTT)/README.html
resource/ott/$(OTT)/log.tsv: bin/jython $(CLASS) \
            make-ott.py assemble_ott.py adjustments.py amendments.py \
	    curation/separation/taxonomy.tsv \
	    resource/silva/$(SILVA)/taxonomy.tsv \
	    resource/fung-ot/$(FUNG-OT)/taxonomy.tsv \
	    curation/lamiales/taxonomy.tsv \
	    curation/h2007/tree.tre \
	    resource/worms-ot/$(WORMS-OT)/taxonomy.tsv \
	    resource/ncbi/$(NCBI)/taxonomy.tsv \
	    resource/gbif/$(GBIF)/taxonomy.tsv \
	    resource/irmng/$(IRMNG)/taxonomy.tsv \
	    resource/amendments/$(AMENDMENTS)/amendments-1/next_ott_id.json \
	    curation/edits/ott_edits.tsv \
	    resource/ott/$(PREV-OTT)/taxonomy.tsv \
	    resource/idlist/$(IDLIST)/by_qid.csv \
	    ids_that_are_otus.tsv ids_in_synthesis.tsv \
	    inclusions.csv
	@date
	@rm -f *py.class util/*py.class
	@mkdir -p resource/ott/$(OTT)
	@echo Writing transcript to resource/ott/$(OTT)/transcript.out
	time bin/jython make-ott.py $(WHICH) 2>&1 | tee tmp/transcript.out.new
	mv tmp/transcript.out.new resource/ott/$(OTT)/transcript.out
	echo $(WHICH) >resource/ott/$(OTT)/version.txt

resource/ott/$(OTT)/version.txt:
	echo $(WHICH) >resource/ott/$(OTT)/version.txt

resource/ott/$(OTT)/README.html: resource/ott/$(OTT)/log.tsv util/make_readme.py
	python util/make_readme.py resource/ott/$(OTT)/ >$@

# ----- Taxonomy sources

# Recipe for adding a new taxonomy source x:
#
# 1. Define 'make x' rule, which creates resource/x/x-vvv, 
#    in 2 or 3 stages:
#    2a. 'make fetch-x' should fetch archive/x/x-vvv from
#        the files server (if not already present)
#    2b. resource/x/x-vvv is created from archive/x/x-vvv,
#    	 optionally by way of work/x/x-vvv
# 2. Define 'make refresh-x' rule, creating a new archive/x/x-vvv
#    2a. If the archive is anything other than a direct copy of a file 
#        from the web, also create work/x/x-vvv (a compilation, digest, 
#	 or subset of stuff found on the web)
# 3. Define 'make archive-x' to create archive/x/x-vvv from work/x/x-vvv
#    (opposite direction to 2b) and copy the archive file to the files
#    server
# 4. 'make store-x' is the inverse of 'make fetch-x'

# --- Source: SILVA
# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)

fetch-silva: archive/silva/$(SILVA)/.fetch

SILVA_URL=http://files.opentreeoflife.org/silva/$(SILVA)/$(SILVA).tgz

archive/silva/$(SILVA)/.fetch:
	@mkdir -p archive/silva/$(SILVA) tmp
	wget -q --output-document=tmp/silva.tmp $(SILVA_URL)
	mv -f tmp/silva.tmp archive/silva/$(SILVA)/$(SILVA).tgz
	ls -l archive/silva/$(SILVA)
	touch $@

work/silva/$(SILVA)/.source: archive/silva/$(SILVA)/$(SILVA).tgz
	tar -C work/silva -xzvf $<
	touch $@

# Get taxon names from Genbank accessions file.
# The accessions file has genbank id, ncbi id, strain, taxon name.
work/silva/$(SILVA)/cluster_names.tsv: work/silva/$(SILVA)/silva_no_sequences.txt \
				resource/ncbi/$(NCBI)/taxonomy.tsv \
				resource/genbank/$(GENBANK)/accessions.tsv
	python resource_scripts/silva/get_taxon_names.py \
	       resource/ncbi/$(NCBI)/taxonomy.tsv \
	       resource/genbank/$(GENBANK)/accessions.tsv \
	       $@.new
	mv $@.new $@

# Create the taxonomy import files from the no_sequences digest & accessions
resource/silva/$(SILVA)/taxonomy.tsv: import_scripts/silva/process_silva.py work/silva/$(SILVA)/silva_no_sequences.txt \
            work/silva/$(SILVA)/cluster_names.tsv 
	@mkdir -p resource/silva/$(SILVA)
	python import_scripts/silva/process_silva.py \
	       work/silva/$(SILVA)/silva_no_sequences.txt \
	       work/silva/$(SILVA)/cluster_names.tsv \
	       work/silva/$(SILVA)/origin_info.json \
	       resource/silva/$(SILVA)

import-silva: resource/silva/$(SILVA)/taxonomy.tsv

silva: resource/silva/$(SILVA)/taxonomy.tsv
	(cd tax; ln -sf ../`dirname $<` silva)

# Archive.

archive-silva:
	@mkdir -p archive/silva/$(SILVA)
	[ -d work/silva/$(SILVA) ] || \
	  (echo "Inputs to tar not available"; exit 1)
	tar -C work/silva -cvzf archive/silva/$(SILVA)/$(SILVA).tgz $(SILVA) \
	  $(EXCL)

store-silva: archive/silva/$(SILVA)/$(SILVA).tgz
	bin/publish-taxonomy archive silva $(SILVA) .tgz

# Refresh from web.

# To advance to a new SILVA release, delete raw/silva, then 'make refresh-silva'

# Make the new version the one to be used in assembly
refresh-silva: raw/silva/.new
	python util/update_config.py silva silva-`cat raw/silva/release` \
	  <config.json >config.mk

# Digestify fasta file and create archive

raw/silva/.new: raw/silva/download.tgz
	@mkdir -p work/silva/silva-`cat raw/silva/release`
	@mkdir -p archive/silva/silva-`cat raw/silva/release`
	gunzip -c raw/silva/download.tgz >raw/silva/silva.fasta
	grep ">.*;" raw/silva/silva.fasta > work/silva/silva-`cat raw/silva/release`/silva_no_sequences.txt
	python util/origin_info.py \
	  `cat raw/silva/date` \
	  `cat raw/silva/origin_url` \
	  >work/silva/silva-`cat raw/silva/release`/origin_info.json
	rm raw/silva/silva.fasta
	ls -l work/silva/silva-`cat raw/silva/release`
	tar -C work/silva -cvzf archive/silva/silva-`cat raw/silva/release`.tgz \
	  silva-`cat raw/silva/release` $(EXCL)
	touch $@

SILVA_ARCHIVE=https://www.arb-silva.de/no_cache/download/archive

# Get the fasta file (big; for release 128, it's 150M)

raw/silva/download.tgz: raw/silva/release
	$(SILVA_ARCHIVE)/release_`cat raw/silva/release`/Exports/SILVA_`cat raw/silva/release`_SSURef_Nr99_tax_silva.fasta.gz >raw/silva/origin_url
	 (echo \
	  wget -q --output-file=raw/silva/download.tgz.new \
	       `cat raw/silva/origin_url` && \
	  mv raw/silva/download.tgz.new raw/silva/download.tgz)

# Figure out which release we want, before downloading

raw/silva/release:
	@mkdir -p raw/silva
	wget -q -O raw/silva/release-index.html $(SILVA_ARCHIVE)/
	python import_scripts/silva/get_silva_release_info.py \
	  <raw/silva/release-index.html | \
	  (read r d; echo "$$r" >raw/silva/release; \
	  	     echo "$$d" >raw/silva/date)

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

fetch-genbank: archive/genbank/$(GENBANK)/.fetch

GENBANK_URL=http://files.opentreeoflife.org/genbank/$(GENBANK)/$(GENBANK).tgz

archive/genbank/$(GENBANK)/.fetch:
	@mkdir -p archive/genbank/$(GENBANK) tmp
	wget -q --output-document=tmp/genbank.tmp $(GENBANK_URL)
	mv -f tmp/genbank.tmp archive/genbank/$(GENBANK)/$(GENBANK).tgz
	ls -l archive/genbank/$(GENBANK)
	touch $@

resource/genbank/$(GENBANK)/accessions.tsv:
	tar -C resource/genbank -xzf archive/genbank/$(GENBANK)/$(GENBANK).tgz

genbank: resource/genbank/$(GENBANK)/accessions.tsv

archive-genbank:
	@mkdir -p archive/genbank/$(GENBANK)
	tar -C resource/genbank -cvzf archive/genbank/$(GENBANK)/$(GENBANK).tgz $(GENBANK) \
	  $(EXCL)

refresh-genbank:
	echo NYI

# This takes a long time - reads every flat file in genbank
# Also - be sure to update RANGES in accessionFromGenbank.py, so you
# don't miss any genbank records!  Manual process now, could be 
# automated.

new-genbank: import_scripts/genbank/accessionFromGenbank.py \
		 import_scripts/genbank/makeaccessionid2taxonid.py
	mkdir -p raw/genbank
	python import_scripts/genbank/accessionFromGenbank.py \
	       work/silva/$(SILVA)/silva_no_sequences.txt \
	       raw/genbank/Genbank.pickle
	@echo Making accessions.tsv
	python import_scripts/genbank/makeaccessionid2taxonid.py \
	       raw/genbank/Genbank.pickle \
	       resource/genbank/$(GENBANK)/accessions.tsv

store-genbank: archive/genbank/$(GENBANK)/$(GENBANK).tgz
	bin/publish-taxonomy archive genbank $(GENBANK) .tgz


# --- Source: Index Fungorum in Open Tree form

fetch-fung-ot: archive/fung-ot/$(FUNG-OT)/.fetch

# 12787947 Oct  6  2015 taxonomy.tsv
FUNG_URL=http://files.opentreeoflife.org/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz

archive/fung-ot/$(FUNG-OT)/.fetch:
	@mkdir -p archive/fung-ot/$(FUNG-OT) tmp
	wget -q --output-document=tmp/fung.tmp $(FUNG_URL)
	mv -f tmp/fung.tmp archive/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz
	ls -l archive/fung-ot/$(FUNG-OT)
	touch $@

archive/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz: archive/fung-ot/$(FUNG-OT)/.fetch

resource/fung-ot/$(FUNG-OT)/taxonomy.tsv: archive/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz
	@rm -rf tmp/fung-ot
	@mkdir -p resource/fung-ot/$(FUNG-OT) tmp/fung-ot
	(cd tmp/fung-ot; tar xzf -) <archive/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz
	@ls -l tmp/fung-ot
	mv tmp/fung-ot/*/* `dirname $@`/
	rm -rf tmp/fung-ot
	@ls -l `dirname $@`

import-fung-ot: resource/fung-ot/$(FUNG-OT)/taxonomy.tsv

fung-ot: resource/fung-ot/$(FUNG-OT)/taxonomy.tsv
	(cd tax; ln -sf ../`dirname $<` fung-ot)

archive-fung-ot:
	echo "TBD"

store-fung-ot: archive/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz
	bin/publish-taxonomy archive fung-ot $(FUNG-OT) .tgz

# --- Source: WoRMS in Open Tree form

WORMS-OT_URL=http://files.opentreeoflife.org/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz

fetch-worms-ot: archive/worms-ot/$(WORMS-OT)/.fetch

archive/worms-ot/$(WORMS-OT)/.fetch:
	@mkdir -p archive/worms-ot/$(WORMS-OT) tmp
	wget -q --output-document=tmp/worms.tmp $(WORMS_URL)
	mv -f tmp/worms.tmp archive/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz
	ls -l archive/worms-ot/$(WORMS-OT)
	touch $@

worms-ot: archive/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz
	tar -C resource/worms-ot -xzf $<
	(cd tax; ln -sf ../resource/worms-ot/$(WORMS-OT) worms-ot)

archive-worms-ot:
	echo NYI

refresh-worms-ot: archive/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz
	@echo NYI

store-worms-ot:
	echo "TBD"

# --- Source: WoRMS in native form (?? not in production ??)

# WoRMS is imported by import_scripts/worms/worms.py which does a web crawl
# These rules haven't been tested!

refresh-worms: raw/worms/release
	python util/update_config.py worms worms-`cat raw/worms/release` \
	  <config.json >config.mk

new-worms: import_scripts/worms/worms.py
	@mkdir -p raw/worms/new
	touch raw/worms/.today
	python import_scripts/worms/worms.py \
	       raw/worms/new/taxonomy.tsv raw/worms/new/synonyms.tsv raw/worms/new/worms.log
	python util/modification_date.py raw/worms/.today >raw/worms/release
	@rm -rf resource/worms/worms-`cat raw/worms/release`
	mv raw/worms/new resource/worms/worms-`cat raw/worms/release`

archive-worms:
	echo "NYI"

store-worms: archive/worms/$(WORMS)/$(WORMS).tgz
	bin/publish-taxonomy archive worms $(WORMS) .tgz

# --- Source: NCBI Taxonomy

# Formerly, where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

fetch-ncbi: archive/ncbi/$(NCBI)/.fetch

NCBI_URL="http://files.opentreeoflife.org/ncbi/$(NCBI)/$(NCBI).tgz"

archive/ncbi/$(NCBI)/.fetch:
	@mkdir -p archive/ncbi/$(NCBI) tmp
	wget -q --output-document=tmp/ncbi.tmp $(NCBI_URL)
	mv tmp/fetch.tmp archive/ncbi/$(NCBI)/$(NCBI).tgz
	ls -l archive/ncbi/$(NCBI)
	touch $@

work/ncbi/$(NCBI)/.source: archive/ncbi/$(NCBI)/$(NCBI).tgz
	@mkdir -p `dirname $@`
	tar -C `dirname $@` -xzvf $<
	touch $@

resource/ncbi/$(NCBI)/.import: work/ncbi/$(NCBI)/.source import_scripts/ncbi/process_ncbi_taxonomy.py
	@rm -rf tmp/ncbi
	@mkdir -p tmp/ncbi
	python import_scripts/ncbi/process_ncbi_taxonomy.py F work/ncbi/$(NCBI) \
            /dev/null tmp/ncbi $(NCBI_URL)
	rm -rf `dirname $@`
	mv -f tmp/ncbi `dirname $@`
	touch $@

import-ncbi: resource/ncbi/$(NCBI)/.import

ncbi: resource/ncbi/$(NCBI)/taxonomy.tsv
	(cd tax; ln -sf ../`dirname $<` ncbi)

# Refresh from web.

NCBI_ORIGIN_URL=ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz
NCBI_TAXDUMP=raw/ncbi/taxdump.tar.gz

refresh-ncbi: raw/ncbi/release
	python util/update_config.py ncbi ncbi-`cat raw/ncbi/release` \
	  <config.json >config.mk

# We look at division.dmp just to get the date of the release.
# Could have been any of the 9 files, but that one is small.

raw/ncbi/release:
	@mkdir -p raw/ncbi/tarball raw
	wget -q --output-document=raw/ncbi/ncbi.new $(NCBI_ORIGIN_URL)
	mv -f raw/ncbi/ncbi.new raw/ncbi/ncbi
	@ls -l raw/ncbi/ncbi
	tar -C raw/ncbi/tarball -xvzf raw/ncbi/ncbi division.dmp
	@ls -l raw/ncbi/tarball
	python util/modification_date.py raw/ncbi/tarball/division.dmp >raw/ncbi/release
	echo New NCBI version is `cat raw/ncbi/release`
	rm raw/ncbi/tarball/division.dmp
	mkdir -p archive/ncbi/ncbi-`cat raw/ncbi/release`
	mv -f raw/ncbi/ncbi \
	  archive/ncbi/ncbi-`cat raw/ncbi/release`/ncbi-`cat raw/ncbi/release`.tgz

archive-ncbi:
	echo NYI

store-ncbi: archive/ncbi/$(NCBI)/$(NCBI).tgz
	bin/publish-taxonomy archive ncbi $(NCBI) .tgz


# --- Source: GBIF

gbif: resource/gbif/$(GBIF)/taxonomy.tsv

fetch-gbif: archive/gbif/$(GBIF)/.fetch

GBIF_URL=http://files.opentreeoflife.org/gbif/$(GBIF)/$(GBIF).zip

# The '|| true' is because unzip erroneously returns status code 1
# when there are warnings.
archive/gbif/$(GBIF)/.fetch:
	@mkdir -p archive/gbif/$(GBIF)
	wget -q --output-document=archive/gbif/$(GBIF)/fetch.tmp $(GBIF_URL)
	mv archive/gbif/$(GBIF)/fetch.tmp archive/gbif/$(GBIF)/$(GBIF).zip
	ls -l archive/gbif/$(GBIF)
	touch $@

work/gbif/$(GBIF)/zip/.source: archive/gbif/$(GBIF)/$(GBIF).zip
	mkdir -p `dirname $@`
	(cd work/gbif/$(GBIF)/zip && (unzip $< || true))
	touch work/gbif/$(GBIF)/zip/.source

work/gbif/$(GBIF)/projection_2016.tsv: work/gbif/$(GBIF)/zip/.source import_scripts/gbif/project_2016.py
	@mkdir -p `dirname $@`
	python import_scripts/gbif/project_2016.py work/gbif/$(GBIF)/zip/.source $@.new
	mv $@.new $@

# Formerly, where it says /dev/null, we had ../data/gbif/ignore.txt

resource/gbif/$(GBIF)/taxonomy.tsv: work/gbif/$(GBIF)/projection_2016.tsv \
				  import_scripts/gbif/process_gbif_taxonomy.py
	@mkdir -p `dirname $@`
	@mkdir -p work/gbif/$(GBIF)/new
	python import_scripts/gbif/process_gbif_taxonomy.py \
	       work/gbif/$(GBIF)/projection_2016.tsv \
	       work/gbif/$(GBIF)/new
	rm -rf resource/gbif/$(GBIF)
	mv work/gbif/$(GBIF)/new resource/gbif/$(GBIF)

archive-gbif:
	@echo NYI

# Archive to files server

store-gbif: archive/gbif/$(GBIF)/$(GBIF).zip
	bin/publish-taxonomy archive gbif $(GBIF) .zip


# Get a new GBIF from the web and store to archive/gbif/$(GBIF)/$(GBIF).zip

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip

GBIF_ORIGIN_URL=http://rs.gbif.org/datasets/backbone/backbone-current.zip

refresh-gbif:
	@mkdir -p raw/gbif/zip
	wget -q --output-document=raw/gbif/gbif.zip "$(GBIF_ORIGIN_URL)"
	(cd raw/gbif/zip && (unzip raw/gbif/gbif.zip  || true))
	python util/modification_date.py raw/gbif/zip/taxon.txt >raw/gbif/release
	[ -d archive/gbif/gbif-`cat raw/gbif/release` ] && (echo "Already got it!"; exit 1)
	echo New GBIF version is `cat raw/gbif/release`
	@mkdir -p work/gbif/gbif-`cat raw/gbif/release`
	mv raw/gbig/zip work/gbif/gbif-`cat raw/gbif/release`/
	@mkdir -p archive/gbif/gbif-`cat raw/gbif/release`
	mv -f raw/gbif/gbif.zip archive/gbif/gbif-`cat raw/gbif/release`/gbif-`cat raw/gbif/release`.zip

# --- Source: IRMNG

fetch-irmng: archive/irmng/$(IRMNG)/.fetch

IRMNG_URL=http://files.opentreeoflife.org/irmng/$(IRMNG)/$(IRMNG).zip

archive/irmng/$(IRMNG)/.fetch:
	@mkdir -p archive/irmng/$(IRMNG)
	wget -q --output-document=archive/irmng/$(IRMNG)/$(IRMNG).zip $(IRMNG_URL)
	touch $@

work/irmng/$(IRMNG)/zip/IRMNG_DWC.csv:
	@mkdir -p `dirname $@`
	(cd work/irmng/$(IRMNG)/zip && (unzip archive/irmng/$(IRMNG)/$(IRMNG).zip || true))
	touch work/irmng/$(IRMNG)/.source

resource/irmng/$(IRMNG)/taxonomy.tsv: import_scripts/irmng/process_irmng.py \
				    work/irmng/$(IRMNG)/zip/IRMNG_DWC.csv 
	@mkdir -p `dirname $@`
	python import_scripts/irmng/process_irmng.py \
	   work/irmng/$(IRMNG)/zip/IRMNG_DWC.csv \
	   work/irmng/$(IRMNG)/zip/IRMNG_DWC_SP_PROFILE.csv \
	   resource/irmng/$(IRMNG)/taxonomy.tsv \
	   resource/irmng/$(IRMNG)/synonyms.tsv

irmng: tax/irmng/taxonomy.tsv

# Build IRMNG from Tony's .csv files

refresh-irmng: 

IRMNG_ORIGIN_URL=http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip

new-irmng: 
	@mkdir -p raw/irmng/zip
	wget -q --output-document=raw/irmng/irmng.zip "$(IRMNG_ORIGIN_URL)"
	(cd raw/irmng/zip && (unzip raw/irmng/irmng.zip || true))
	python util/modification_date.py raw/irmng/zip/IRMNG_DWC.csv >raw/irmng/release
	[ -d archive/irmng/irmng-`cat raw/irmng/release` ] && (echo "Already got it!"; exit 1)
	echo New IRMNG version is `cat raw/irmng/release`
	@mkdir -p work/irmng/irmng-`cat raw/irmng/release`
	mv raw/gzip/zip work/irmng/irmng-`cat raw/irmng/release`/
	@mkdir -p archive/irmng/irmng-`cat raw/irmng/release`
	mv raw/gzip/irmng.zip archive/irmng/irmng-`cat raw/irmng/release`/irmng-`cat raw/irmng/release`.zip

archive-irmng:
	@echo No way to make a new archive file.

store-irmng: archive/irmng/$(IRMNG)/$(IRMNG).zip
	bin/publish-taxonomy archive irmng $(IRMNG) .zip

# --- Source: Open Tree curated amendments

fetch-amendments: resource/amendments/$(AMENDMENTS)/.fetch

resource/amendments/$(AMENDMENTS)/.fetch: raw/amendments/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1 && git checkout -q $(AMENDMENTS_REFSPEC))
	@mkdir -p resource/amendments/$(AMENDMENTS)/amendments-1
	cp -pr raw/amendments/amendments-1/amendments \
	       resource/amendments/$(AMENDMENTS)/amendments-1/
	echo $(AMENDMENTS_REFSPEC) > resource/amendments/$(AMENDMENTS)/refspec
	touch $@

refresh-amendments: raw/amendments/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1; git log -n 1) | head -1 | sed -e 's/commit //' >raw/amendments/refspec.new
	mv raw/amendments/refspec.new raw/amendments/refspec
	(cd raw/amendments/amendments-1; git checkout -q `cat ../../../raw/amendments/refspec`)
	head -c 7 raw/amendments/refspec > raw/amendments/version
	mkdir -p resource/amendments/amendments-`cat raw/amendments/version`/amendments-1
	cp -pr raw/amendments/amendments-1/amendments resource/amendments/amendments-`cat raw/amendments/version`/amendments-1/
	cp -p raw/amendments/refspec resource/amendments/amendments-`cat raw/amendments/version`/
	echo "TBD: STORE NEW REFSPEC AND VERSION IN config.json"

# fetch

raw/amendments/amendments-1:
	@mkdir -p raw/amendments
	(cd raw/amendments; git clone https://github.com/OpenTreeOfLife/amendments-1.git)

# --- Source: Previous version of OTT, for id assignments

# This is used as a source of OTT id assignments.
PREV_OTT_URL=http://files.opentreeoflife.org/ott/$(PREV-OTT)/$(PREV-OTT).tgz

resource/ott/$(PREV-OTT)/taxonomy.tsv:
	@mkdir -p tmp 
	wget -q --output-document=tmp/prev_ott.tgz $(PREV_OTT_URL)
	@ls -l tmp/prev_ott.tgz
	(cd tmp/ && tar xvf prev_ott.tgz)
	rm -rf resource/ott/$(PREV-OTT)
	@mkdir -p resource/ott/$(PREV-OTT)
	mv tmp/ott*/* resource/ott/$(PREV-OTT)/
	if [ -e resource/ott/$(PREV-OTT)/taxonomy ]; then mv resource/ott/$(PREV-OTT)/taxonomy resource/ott/$(PREV-OTT)/taxonomy.tsv; fi
	if [ -e resource/ott/$(PREV-OTT)/synonyms ]; then mv resource/ott/$(PREV-OTT)/synonyms resource/ott/$(PREV-OTT)/synonyms.tsv; fi
	rm -rf tmp

# --- Source: OTT id list compiled from all previous OTT versions

fetch-idlist: archive/idlist/$(IDLIST)/.fetch

IDLIST_URL="http://files.opentreeoflife.org/idlist/$(IDLIST)/$(IDLIST).tgz"

archive/idlist/$(IDLIST)/.fetch:
	@mkdir -p archive/idlist/$(IDLIST)
	wget -q --output-document=archive/idlist/$(IDLIST).tgz $(IDLIST_URL)
	touch $@

resource/idlist/$(IDLIST)/by_qid.csv: archive/idlist/$(IDLIST)/$(IDLIST).tgz
	@mkdir -p tmp/idlist resource/idlist/$(IDLIST)
	tar -C tmp/idlist -xzf archive/idlist/$(IDLIST)/$(IDLIST).tgz
	mv tmp/idlist/*/* resource/idlist/$(IDLIST)/

idlist: resource/idlist/$(IDLIST)/by_qid.csv

# When we build 3.1, the IDLIST version is for ids through OTT 3.0.
# So, to make the id list for 3.1, we first make 3.1, then
# combine the 3.0 id list with new registrations from 3.1.

refresh-idlist:
	python util/archivetool.py refresh idlist source resource archive by_qid.csv | bash

new-idlist: resource/idlist/$(PREV-IDLIST)/by_qid.csv
	@rm -rf resource/idlist/$(IDLIST)
	@mkdir -p resource/idlist/$(IDLIST)
	cp -pr resource/idlist/$(PREV-IDLIST)/regs resource/idlist/$(IDLIST)/
	python import_scripts/idlist/extend_idlist.py \
	       resource/idlist/$(IDLIST)/regs \
	       resource/ott/$(PREV-OTT) \
	       $(PREV-OTT) \
	       resources/captures.json \
	       resource/idlist/$(IDLIST)/regs/$(PREV-OTT).csv

archive-idlist: resource/idlist/$(IDLIST)/by_qid.csv
	tar -C resource/idlist -czf archive/idlist/$(IDLIST).tgz $(IDLIST)

store-idlist: archive/idlist/$(IDLIST)/$(IDLIST).tgz
	bin/publish-taxonomy archive idlist $(IDLIST) .tgz

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
 
tarball: resource/ott/$(OTT)/log.tsv resource/ott/$(OTT)/version.txt
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/$(OTT).tgz.tmp -C tax ott \
	   --exclude differences.tsv --exclude "#*" && \
	 mv $(TARDIR)/$(OTT).tgz.tmp $(TARDIR)/$(OTT).tgz )
	@echo "Don't forget to bump the version number"

# Then, something like
# scp -p -i ~/.ssh/opentree/opentree.pem tarballs/ott2.9draft3.tgz \
#   opentree@ot10.opentreeoflife.org:files.opentreeoflife.org/ott/ott2.9/

# Not currently used since smasher already suppresses non-OTU deprecations
resource/ott/$(OTT)/otu_deprecated.tsv: ids_that_are_otus.tsv resource/ott/$(OTT)/deprecated.tsv
	$(SMASH) --join ids_that_are_otus.tsv resource/ott/$(OTT)/deprecated.tsv >$@.new
	mv $@.new $@
	wc $@

# This file is big
resource/ott/$(OTT)/differences.tsv: resource/ott/$(PREV_OTT)/taxonomy.tsv resource/ott/$(OTT)/taxonomy.tsv
	$(SMASH) --diff resource/ott/$(PREV_OTT)/ resource/ott/$(OTT)/ $@.new
	mv $@.new $@
	wc $@

# OTUs only
resource/ott/$(OTT)/otu_differences.tsv: resource/ott/$(OTT)/differences.tsv
	$(SMASH) --join ids_that_are_otus.tsv resource/ott/$(OTT)/differences.tsv >$@.new
	mv $@.new $@
	wc $@

resource/ott/$(OTT)/otu_hidden.tsv: resource/ott/$(OTT)/hidden.tsv
	$(SMASH) --join ids_that_are_otus.tsv resource/ott/$(OTT)/hidden.tsv >$@.new
	mv $@.new $@
	wc $@

# The works
works: ott resource/ott/$(OTT)/otu_differences.tsv resource/ott/$(OTT)/forwards.tsv

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
	bin/jython util/check_inclusions.py inclusions.csv resource/ott/$(OTT)/

# -----------------------------------------------------------------------------
# Asterales test system ('make test')

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: resource/ott/$(PREV_OTT)/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) resource/ott/$(PREV_OTT)/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on resource/ncbi/$(NCBI)/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) resource/ncbi/$(NCBI)/ --select2 $(TAXON) --out t/tax/ncbi_aster/

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
	rm -rf resource/ott/$(OTT)
	rm -rf *.tmp new_taxa
	rm -rf resource/ncbi/$(NCBI)
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf archive work resource
	rm -rf tax/fung tax/irmng* tax/worms-ot 
	rm -rf resource/ott/$(PREV_OTT)
