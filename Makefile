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

ott: tax/ott/log.tsv tax/ott/version.txt tax/ott/README.html
tax/ott/log.tsv: $(CLASS) make-ott.py assemble_ott.py adjustments.py amendments.py \
                    resource/silva/$(SILVA)/taxonomy.tsv \
		    resource/fung-ot/$(FUNG-OT)/taxonomy.tsv \
		    tax/713/taxonomy.tsv \
		    resource/worms-ot/$(WORMS-OT)/taxonomy.tsv \
		    resource/ncbi/$(NCBI)/taxonomy.tsv \
		    resource/gbif/$(GBIF)/taxonomy.tsv \
		    resource/irmng/$(IRMNG)/taxonomy.tsv \
		    feed/ott/edits/ott_edits.tsv \
		    resource/ott/$(PREV-OTT)/taxonomy.tsv \
		    ids_that_are_otus.tsv \
		    bin/jython \
		    inclusions.csv \
		    raw/amendments/amendments-1/next_ott_id.json \
		    tax/separation/taxonomy.tsv \
		    feed/ott_id_list/by_qid.csv
	@date
	@rm -f *py.class util/*py.class
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
#        from the web, also create work/x/x-vvv (a compilation, digest, 
#	 or subset of stuff found on the web)
# 2. Define 'make x' rule, which creates resource/x/x-vvv
#    2a. 'make retrieve-x' should get archive/x/x-vvv from the files server
#    2b. work/x/x-vvv can just be the extraction of archive/x/x-vvv
#    2c. resource/x/x-vvv gets generated from work/x/x-vvv (or in 
#    	 cases where an OT-format taxonomy is archived,, directly from
# 	 archive/x/x-vvv)
# 3. Define 'make archive-x' to create archive/x/x-vvv from work/x/x-vvv
#    (opposite direction to 2b) and copy the archive file to the files
#    server

# --- Source: SILVA
# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)

SILVA_URL=http://files.opentreeoflife.org/silva/$(SILVA)/$(SILVA).tgz

retrieve-silva: archive/silva/$(SILVA)/.retrieve

archive/silva/$(SILVA)/.retrieve:
	@mkdir -p archive/silva/$(SILVA)
	wget --output-document=archive/silva/retrieve.tmp $(SILVA_URL)
	mv archive/silva/retrieve.tmp archive/silva/$(SILVA)/$(SILVA).tgz
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
	bin/publish-taxonomy archive silva $(SILVA) .tgz

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
	@mkdir -p work/silva/silva-`cat raw/silva/release`
	@mkdir -p archive/silva/silva-`cat raw/silva/release`
	gunzip -c raw/silva/silva.gz >raw/silva/silva.fasta
	grep ">.*;" raw/silva/silva.fasta > work/silva/silva-`cat raw/silva/release`/silva_no_sequences.txt
	python util/origin_info.py \
	  `cat raw/silva/date` \
	  `cat raw/silva/origin_url` \
	  >work/silva/silva-`cat raw/silva/release`/origin_info.json
	rm raw/silva/silva.fasta
	ls -l work/silva/silva-`cat raw/silva/release`
	tar -C work/silva -cvzf archive/silva/silva-`cat raw/silva/release`.tgz \
	  silva-`cat raw/silva/release` $(EXCL)

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

GENBANK_URL=http://files.opentreeoflife.org/genbank/$(GENBANK)/$(GENBANK).tgz

archive/genbank/$(GENBANK)/$(GENBANK).tgz:
	@mkdir -p `dirname $@`
	wget --output-document=$@.new $(GENBANK_URL)
	mv $@.new $@

resource/genbank/$(GENBANK)/accessions.tsv:
	tar -C resource/genbank -xzf archive/genbank/$(GENBANK)/$(GENBANK).tgz

genbank: resource/genbank/$(GENBANK)/accessions.tsv

archive-genbank:
	@mkdir -p archive/genbank/$(GENBANK)
	tar -C resource/genbank -cvzf archive/genbank/$(GENBANK)/$(GENBANK).tgz $(GENBANK) \
	  $(EXCL)
	bin/publish-taxonomy archive genbank $(GENBANK) .tgz


# This takes a long time - reads every flat file in genbank
# Also - be sure to update RANGES in accessionFromGenbank.py, so you
# don't miss any genbank records!  Manual process now, could be 
# automated.

refresh-genbank: import_scripts/genbank/accessionFromGenbank.py \
		 import_scripts/genbank/makeaccessionid2taxonid.py
	mkdir -p raw/genbank
	python import_scripts/genbank/accessionFromGenbank.py \
	       work/silva/$(SILVA)/silva_no_sequences.txt \
	       raw/genbank/Genbank.pickle
	@echo Making accessions.tsv
	python import_scripts/genbank/makeaccessionid2taxonid.py \
	       raw/genbank/Genbank.pickle \
	       resource/genbank/$(GENBANK)/accessions.tsv

# --- Source: Index Fungorum in Open Tree form

retrieve-fung-ot: archive/fung-ot/$(FUNG-OT)/.retrieve

# 12787947 Oct  6  2015 taxonomy.tsv
FUNG_URL=http://files.opentreeoflife.org/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz

archive/fung-ot/$(FUNG-OT)/$(FUNG-OT).tgz:
	@mkdir -p `dirname $@`
	wget -q --output-document=$@.new $(FUNG_URL)
	mv -f $@.new $@
	ls -l archive/fung-ot/$(FUNG-OT)
	touch archive/fung-ot/$(FUNG-OT)/.retrieve

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

archive-fung-ot: archive/fung-ot/$(FUNG-OT)
	bin/publish-taxonomy archive fung-ot $(FUNG-OT) .tgz

# --- Source: WoRMS in Open Tree form

WORMS-OT_URL=http://files.opentreeoflife.org/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz

retrieve-worms-ot: archive/worms-ot/$(WORMS-OT)/.retrieve

archive/worms-ot/$(WORMS-OT)/.retrieve:
	@mkdir -p tax/worms-ot archive/worms-ot/$(WORMS-OT)
	wget --output-document=archive/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz $(WORMS-OT_URL)
	touch $@

worms-ot: archive/worms-ot/$(WORMS-OT)/$(WORMS-OT).tgz
	tar -C resource/worms-ot -xzf $<
	(cd tax; ln -sf ../resource/worms-ot/$(WORMS-OT) worms-ot)

archive-worms-ot: archive/worms-ot/$(WORMS-OT)
	bin/publish-taxonomy archive worms $(WORMS-OT) .tgz

refresh-worms-ot: 
	@echo NYI

# --- Source: WoRMS in native form (??)
# This is assembled by import_scripts/worms/worms.py which does a web crawl
# These rules haven't been tested!

refresh-worms: crawl-worms
	python util/update_config.py worms worms-`cat raw/worms/release` \
	  <config.json >config.mk

crawl-worms: import_scripts/worms/worms.py
	@mkdir -p raw/worms/new
	touch raw/worms/.today
	python import_scripts/worms/worms.py \
	       raw/worms/new/taxonomy.tsv raw/worms/new/synonyms.tsv raw/worms/new/worms.log
	python util/modification_date.py raw/worms/.today >raw/worms/release
	@rm -rf resource/worms/worms-`cat raw/worms/release`
	mv raw/worms/new resource/worms/worms-`cat raw/worms/release`

archive-worms: archive/worms/$(WORMS)
	bin/publish-taxonomy archive worms $(WORMS) .tgz

# --- Source: NCBI Taxonomy

# Formerly, where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

retrieve-ncbi: archive/ncbi/$(NCBI)/.retrieve

NCBI_URL="http://files.opentreeoflife.org/ncbi/$(NCBI)/$(NCBI).tgz"

archive/ncbi/$(NCBI)/.retrieve:
	@mkdir -p archive/ncbi/$(NCBI)
	wget --output-document=archive/ncbi/$(NCBI)/retrieve.tmp $(NCBI_URL)
	mv archive/ncbi/$(NCBI)/retrieve.tmp archive/ncbi/$(NCBI)/$(NCBI).tgz
	ls -l archive/ncbi/$(NCBI)

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

# Archive

archive-ncbi:
	bin/publish-taxonomy archive ncbi $(NCBI) .tgz

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

# --- Source: GBIF

gbif: resource/gbif/$(GBIF)/taxonomy.tsv

retrieve-gbif: archive/gbif/$(GBIF)/$(GBIF).zip

GBIF_URL=http://files.opentreeoflife.org/gbif/$(GBIF)/$(GBIF).zip

# The '|| true' is because unzip erroneously returns status code 1
# when there are warnings.
archive/gbif/$(GBIF)/.retrieve:
	@mkdir -p archive/gbif/$(GBIF)
	wget --output-document=archive/gbif/$(GBIF)/retrieve.tmp $(GBIF_URL)
	mv archive/gbif/$(GBIF)/retrieve.tmp archive/gbif/$(GBIF)/$(GBIF).zip
	ls -l archive/gbif/$(GBIF)

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

# Archive to files server

archive-gbif: archive/gbif/$(GBIF)/$(GBIF).zip
	bin/publish-taxonomy archive gbif $(GBIF) .zip


# Get a new GBIF from the web and store to archive/gbif/$(GBIF)/$(GBIF).zip

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip

GBIF_ORIGIN_URL=http://rs.gbif.org/datasets/backbone/backbone-current.zip

refresh-gbif:
	@mkdir -p raw/gbif/zip
	wget --output-document=raw/gbif/gbif.zip "$(GBIF_ORIGIN_URL)"
	(cd raw/gbif/zip && (unzip raw/gbif/gbif.zip  || true))
	python util/modification_date.py raw/gbif/zip/taxon.txt >raw/gbif/release
	[ -d archive/gbif/gbif-`cat raw/gbif/release` ] && (echo "Already got it!"; exit 1)
	echo New GBIF version is `cat raw/gbif/release`
	@mkdir -p work/gbif/gbif-`cat raw/gbif/release`
	mv raw/gbig/zip work/gbif/gbif-`cat raw/gbif/release`/
	@mkdir -p archive/gbif/gbif-`cat raw/gbif/release`
	mv -f raw/gbif/gbif.zip archive/gbif/gbif-`cat raw/gbif/release`/gbif-`cat raw/gbif/release`.zip

# --- Source: IRMNG

retrieve-irmng: archive/irmng/$(IRMNG)/$(IRMNG).zip

IRMNG_URL=http://files.opentreeoflife.org/irmng/$(IRMNG)/$(IRMNG).zip

archive/irmng/$(IRMNG)/$(IRMNG).zip:
	@mkdir -p `dirname $@`
	wget --output-document=$@ $(IRMNG_URL)

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

archive-irmng:
	bin/publish-taxonomy archive irmng $(IRMNG) .zip

# Build IRMNG from Tony's .csv files

IRMNG_ORIGIN_URL=http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip

refresh-irmng: 
	@mkdir -p raw/irmng/zip
	wget --output-document=raw/irmng/irmng.zip "$(IRMNG_ORIGIN_URL)"
	(cd raw/irmng/zip && (unzip raw/irmng/irmng.zip || true))
	python util/modification_date.py raw/irmng/zip/IRMNG_DWC.csv >raw/irmng/release
	[ -d archive/irmng/irmng-`cat raw/irmng/release` ] && (echo "Already got it!"; exit 1)
	echo New IRMNG version is `cat raw/irmng/release`
	@mkdir -p work/irmng/irmng-`cat raw/irmng/release`
	mv raw/gzip/zip work/irmng/irmng-`cat raw/irmng/release`/
	@mkdir -p archive/irmng/irmng-`cat raw/irmng/release`
	mv raw/gzip/irmng.zip archive/irmng/irmng-`cat raw/irmng/release`/irmng-`cat raw/irmng/release`.zip

# --- Source: Open Tree curated amendments

retrieve-amendments:
	echo "NYI"

# 9 Sep 2016
AMENDMENTS_REFSPEC=raw/amendments/refspec

fetch_amendments: raw/amendments/amendments-1/next_ott_id.json

raw/amendments/amendments-1/next_ott_id.json: raw/amendments/amendments-1 $(AMENDMENTS_REFSPEC)
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1; git checkout -q `cat ../../../$(AMENDMENTS_REFSPEC)`)

refresh-amendments: raw/amendments/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1; git log -n 1) | head -1 | sed -e 's/commit //' >$(AMENDMENTS_REFSPEC).new
	mv $(AMENDMENTS_REFSPEC).new $(AMENDMENTS_REFSPEC)
	(cd raw/amendments/amendments-1; git checkout -q `cat ../../../$(AMENDMENTS_REFSPEC)`)

raw/amendments/amendments-1:
	@mkdir -p raw/amendments
	(cd raw/amendments; git clone https://github.com/OpenTreeOfLife/amendments-1.git)

# --- Source: Previous version of OTT, for id assignments

# This is used as a source of OTT id assignments.
PREV_OTT_URL=http://files.opentreeoflife.org/ott/$(PREV-OTT)/$(PREV-OTT).tgz

resource/ott/$(PREV-OTT)/taxonomy.tsv:
	@mkdir -p tmp 
	wget --output-document=tmp/prev_ott.tgz $(PREV_OTT_URL)
	@ls -l tmp/prev_ott.tgz
	(cd tmp/ && tar xvf prev_ott.tgz)
	rm -rf resource/ott/$(PREV-OTT)
	@mkdir -p resource/ott/$(PREV-OTT)
	mv tmp/ott*/* resource/ott/$(PREV-OTT)/
	if [ -e resource/ott/$(PREV-OTT)/taxonomy ]; then mv resource/ott/$(PREV-OTT)/taxonomy resource/ott/$(PREV-OTT)/taxonomy.tsv; fi
	if [ -e resource/ott/$(PREV-OTT)/synonyms ]; then mv resource/ott/$(PREV-OTT)/synonyms resource/ott/$(PREV-OTT)/synonyms.tsv; fi
	rm -rf tmp

# --- Source: OTT id list compiled from all previous OTT versions

retrieve-idlist: archive/idlist/$(IDLIST)/$(IDLIST).tgz

IDLIST_URL="http://files.opentreeoflife.org/idlist/$(IDLIST)/$(IDLIST).tgz"

archive/idlist/$(IDLIST)/$(IDLIST).tgz:
	@mkdir -p `dirname $@`
	wget --output-document=$@ $(IDLIST_URL)

resource/idlist/$(IDLIST)/by_qid.csv: archive/idlist/$(IDLIST)/$(IDLIST).tgz
	@mkdir -p tmp/idlist resource/idlist/$(IDLIST)
	tar -C tmp/idlist -xzf archive/idlist/$(IDLIST)/$(IDLIST).tgz
	mv tmp/idlist/*/* resource/idlist/$(IDLIST)/

idlist: resource/idlist/$(IDLIST)/by_qid.csv

# When we build 3.1, the IDLIST version is for ids through OTT 3.0.
# So, to make the id list for 3.1, we first make 3.1, then
# combine the 3.0 id list with new registrations from 3.1.

refresh-idlist:
	rm -rf resource/idlist/idlist-$(WHICH)
	cp -pr resource/idlist/idlist-$(PREV_WHICH) resource/idlist/idlist-$(WHICH)
	... extend it by adding new taxonomy ...
	python import_scripts/idlist/extend_idlist.py \
	       resource/idlist/idlist-$(PREV_WHICH)/registrations \
	       resource/ott/ott$(WHICH) \
	       resources/captures.json \
	       resource/idlist/idlist-$(WHICH)/registrations/ott$(WHICH).csv
	mv ott_id_list/by_qid.csv work/idlist/
	python util/archivetool.py refresh idlist source stage archive by_qid.csv | bash
	... ??? dir name should include version number ...
	... tar czvf stage/idlist/by_qid.tgz -C stage idlist-xxx/by_qid.csv

archive-idlist: resource/idlist/$(IDLIST)/by_qid.csv    # or stage/ ???
	bin/publish-taxonomy archive idlist $(IDLIST) .tgz

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
	 tar czvf $(TARDIR)/$(OTT).tgz.tmp -C tax ott \
	   --exclude differences.tsv --exclude "#*" && \
	 mv $(TARDIR)/$(OTT).tgz.tmp $(TARDIR)/$(OTT).tgz )
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
tax/ott/differences.tsv: resource/ott/$(PREV_OTT)/taxonomy.tsv tax/ott/taxonomy.tsv
	$(SMASH) --diff resource/ott/$(PREV_OTT)/ tax/ott/ $@.new
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
	rm -rf tax/ott
	rm -rf feed/*/out feed/*/work
	rm -rf *.tmp new_taxa
	rm -rf resource/ncbi/$(NCBI) tax/gbif tax/silva
	rm -f feed/misc/chromista_spreadsheet.py
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf raw/amendments/amendments-1
	rm -rf feed/*/in
	rm -rf tax/fung tax/irmng* tax/worms-ot 
	rm -rf resource/ott/$(PREV_OTT)
