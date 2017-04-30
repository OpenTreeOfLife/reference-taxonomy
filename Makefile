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

ott: resource/$(OTT)/.made

resource/$(OTT)/.made: resource/$(OTT)/log.tsv \
		       resource/$(OTT)/version.txt \
		       resource/$(OTT)/README.html
	touch $@

resource/$(OTT)/log.tsv: bin/jython $(CLASS) \
            make-ott.py assemble_ott.py adjustments.py amendments.py \
	    curation/separation/taxonomy.tsv \
	    resource/$(SILVA)/.made \
	    resource/$(FUNG)/.made \
	    curation/lamiales/taxonomy.tsv \
	    curation/h2007/tree.tre \
	    resource/$(WORMS)/.made \
	    resource/$(NCBI)/.made \
	    resource/$(GBIF)/.made \
	    resource/$(IRMNG)/.made \
	    resource/$(AMENDMENTS)/.made \
	    curation/edits/ott_edits.tsv \
	    resource/$(PREV-OTT)/.made \
	    resource/$(IDLIST)/by_qid.csv \
	    ids_that_are_otus.tsv ids_in_synthesis.tsv \
	    inclusions.csv
	@date
	@rm -f *py.class util/*py.class curation/*py.class
	@mkdir -p resource/$(OTT)/debug
	@echo Writing transcript to resource/$(OTT)/debug/transcript.out
	time bin/jython make-ott.py $(WHICH) config.json resource/$(OTT)/ \
	  2>&1 | tee tmp/transcript.out
	mv tmp/transcript.out resource/$(OTT)/debug/transcript.out

resource/$(OTT)/version.txt:
	echo $(WHICH) >resource/$(OTT)/version.txt

resource/$(OTT)/README.html: resource/$(OTT)/log.tsv util/make_readme.py
	python util/make_readme.py resource/$(OTT)/ >$@

# ----- Taxonomy sources

# Recipe for adding a new taxonomy source x:
#
# 1. Define a rule for resource/x-vvv/.made to create the resource files
#    (e.g. taxonomy) from the files in source/x-vvv (or direct from archive/x-vvv).
# 2. Define a rule for new/x, creating a new source/x-vvv from stuff on the web.

# Pattern rules!  All targets phony.

fetch/%:
	bin/fetch-archive archive/`basename $@`

# store does a pack, if necessary

store/%:
	bin/store-archive archive/`basename $@`

# Unpack an archive (also fetch it if necessary)
# Unpack does a fetch, if necessary

unpack/%:
	bin/unpack-archive archive/`basename $@` source/`basename $@`

pack/%: source/%/.made
	bin/pack-archive source/% archive/%

source/%/.made:
	d=`dirname $@`; bin/unpack-archive archive/`basename $$d` $$d

# Imported taxonomy stored on archive server

refresh/%: new/%
	python util/update_config.py `basename $<` `basename $<`-`cat raw/%/release` \
	  <config.json >config.mk
	rm -rf raw/`basename $<`


# --- Source: SILVA
# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)

# Create the taxonomy import files from the no_sequences digest & accessions
resource/$(SILVA)/.made: import_scripts/silva/process_silva.py \
			 source/$(SILVA)/.made \
			 work/$(SILVA)/cluster_names.tsv
	@mkdir -p resource/$(SILVA)
	python import_scripts/silva/process_silva.py \
	       source/$(SILVA)/silva_no_sequences.fasta \
	       work/$(SILVA)/cluster_names.tsv \
	       source/$(SILVA)/origin_info.json \
	       resource/$(SILVA)
	touch $@

# Get taxon names from Genbank accessions file.
# The accessions file has genbank id, ncbi id, strain, taxon name.
# -- it seems the accessions file now has taxon names, so we
# probably don't need to take NCBI taxonomy as an input.
work/$(SILVA)/cluster_names.tsv: resource/$(NCBI)/.made \
				 resource/$(GENBANK)/accessions.tsv
	@mkdir -p `dirname $@`
	python import_scripts/silva/get_taxon_names.py \
	       resource/$(NCBI)/taxonomy.tsv \
	       resource/$(GENBANK)/accessions.tsv \
	       $@.new
	mv -f $@.new $@

# Refresh from web.
# To advance to a new SILVA release, delete raw/silva, then 'make refresh/silva'

# Digestify fasta file and create sources for archive

new/silva: raw/silva/download.tgz
	@mkdir -p work/silva-`cat raw/silva/release`
	@mkdir -p archive/silva-`cat raw/silva/release`
	gunzip -c raw/silva/download.tgz >raw/silva/silva.fasta
	grep ">.*;" raw/silva/silva.fasta > source/silva-`cat raw/silva/release`/silva_no_sequences.fasta
	python util/origin_info.py \
	  `cat raw/silva/date` \
	  `cat raw/silva/origin_url` \
	  >source/silva-`cat raw/silva/release`/origin_info.json
	rm raw/silva/silva.fasta
	touch source/silva-`cat raw/silva/release`/.made
	ls -l source/silva-`cat raw/silva/release`

# Get the fasta file (big; for release 128, it's 150M)

raw/silva/download.tgz: raw/silva/release
	echo $(SILVA_ARCHIVE)/release_`cat raw/silva/release`/Exports/SILVA_`cat raw/silva/release`_SSURef_Nr99_tax_silva.fasta.gz >raw/silva/origin_url
	(echo \
	 wget -q --output-file=raw/silva/download.tgz.new \
	      `cat raw/silva/origin_url` && \
	 mv raw/silva/download.tgz.new raw/silva/download.tgz)

# Figure out which release we want, before downloading, by scanning the index.html file

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

resource/$(GENBANK)/.made: source/$(GENBANK)/.made
	(cd resource; ln -sf ../source/$(GENBANK) $(GENBANK))

# This takes a long time - reads every flat file in genbank
# Also - be sure to update RANGES in accessionFromGenbank.py, so you
# don't miss any genbank records!  Manual process now, could be 
# automated.

new/genbank: import_scripts/genbank/accessionFromGenbank.py \
	     import_scripts/genbank/makeaccessionid2taxonid.py \
	     source/$(SILVA)/silva_no_sequences.fasta
	mkdir -p work/genbank-NEW source/genbank-NEW
	@echo "*** Reading all of Genbank - this can take a while!"
	python import_scripts/genbank/accessionFromGenbank.py \
	       source/$(SILVA)/silva_no_sequences.fasta \
	       work/genbank-NEW/Genbank.pickle
	python util/modification_date.py work/genbank-NEW/Genbank.pickle \
	   >work/worms-NEW/release
	@echo Making accessions.tsv
	@mkdir -p source/genbank-`cat work/worms-NEW/release`
	python import_scripts/genbank/makeaccessionid2taxonid.py \
	       work/genbank-NEW/Genbank.pickle \
	       source/genbank-`cat work/worms-NEW/release`/accessions.tsv
	touch source/genbank-`cat work/worms-NEW/release`/.made

# --- Source: Index Fungorum in Open Tree form

unpack/$(FUNG):
	bin/unpack-archive archive/$(FUNG) resource/$(FUNG)

pack/$(FUNG): resource/$(FUNG)/.made
	bin/pack-archive resource/$(FUNG) archive/$(FUNG)

# new/fung: ...

# How fung-9 was created:
# import_scripts/fung/patch_together.py 
#   which reads fung-4, fung-2, fung-1
#
# The earlier versions were created using various versions of the
# process_fungorum.py script, operating on various files from Paul
# Kirk as unput.

# --- Source: WoRMS in Open Tree form

unpack/$(WORMS):
	bin/unpack-archive archive/$(WORMS) resource/$(WORMS)

pack/$(WORMS): resource/$(WORMS)/.made
	bin/pack-archive resource/$(WORMS) archive/$(WORMS)

# WoRMS is imported by import_scripts/worms/worms.py which does a web crawl
# This rule hasn't been tested!

new/worms: import_scripts/worms/worms.py
	echo "*** Warning! This can take several days to run. ***"
	@mkdir -p work/worms-NEW/r
	python import_scripts/worms/worms.py \
	       work/worms-NEW/r/taxonomy.tsv work/worms-NEW/r/synonyms.tsv work/worms-NEW/r/worms.log
	touch work/worms-NEW/.today
	python util/modification_date.py work/worms-NEW/.today >work/worms-NEW/release
	mv work/worms-NEW/r resource/worms-`cat work/worms-NEW/release`
	mv work/worms-NEW work/worms-`cat work/worms-NEW/release`

# --- Source: NCBI Taxonomy

# Formerly (version 1.0), where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

resource/$(NCBI)/.made: source/$(NCBI)/.made import_scripts/ncbi/process_ncbi_taxonomy.py
	@rm -rf resource/$(NCBI).new
	@mkdir -p resource/$(NCBI).new
	python import_scripts/ncbi/process_ncbi_taxonomy.py F source/$(NCBI) \
            /dev/null resource/$(NCBI).new $(NCBI_ORIGIN_URL)
	rm -rf `dirname $@`
	mv -f resource/$(NCBI).new `dirname $@`
	touch $@

# Refresh from web.

# We look at division.dmp just to get the date of the release.
# Could have been any of the 9 files, but that one is small.

new/ncbi:
	@mkdir -p archive/ncbi-NEW/archive.tgz raw/ncbi
	wget -q --output-document=archive/ncbi-NEW/archive.tgz $(NCBI_ORIGIN_URL)
	touch archive/ncbi-NEW/.made
	@ls -l archive/ncbi-NEW
	tar -C raw/ncbi -xzf archive/ncbi-NEW division.dmp
	python util/modification_date.py raw/ncbi/division.dmp >raw/ncbi/release
	echo New NCBI version is `cat raw/ncbi/release`
	rm -rf archive/ncbi-`cat raw/ncbi/release`
	mv -f archive/ncbi-NEW archive/ncbi-`cat raw/ncbi/release`

# --- Source: GBIF

# Formerly, where it says /dev/null, we had ../data/gbif/ignore.txt

resource/$(GBIF)/.made: work/$(GBIF)/projection.tsv \
			import_scripts/gbif/process_gbif_taxonomy.py
	@mkdir -p `dirname $@`
	@mkdir -p resource/$(GBIF).new
	python import_scripts/gbif/process_gbif_taxonomy.py \
	       work/$(GBIF)/projection.tsv \
	       resource/$(GBIF).new
	touch resource/$(GBIF).new/.made
	rm -rf resource/$(GBIF)
	mv resource/$(GBIF).new resource/$(GBIF)

work/$(GBIF)/projection.tsv: source/$(GBIF)/.made \
			     import_scripts/gbif/project_2016.py
	@mkdir -p `dirname $@`
	python import_scripts/gbif/project_2016.py source/$(GBIF)/taxon.txt $@.new
	mv $@.new $@

# Get a new GBIF from the web and store to archive/$(GBIF)/archive.zip

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip

# should be very similar to IRMNG

new/gbif:
	@mkdir -p archive/gbif-NEW source/gbif-NEW
	wget -q --output-document=archive/gbif-NEW/archive.zip "$(GBIF_ORIGIN_URL)"
	touch archive/gbif-NEW/.made
	(cd source/gbif-NEW && (unzip archive/gbif-NEW/archive.zip  || true))
	python util/modification_date.py source/gbif-NEW/taxon.txt >raw/gbif/release
	touch source/gbif-NEW/.made
	echo New GBIF version is `cat raw/gbif/release`
	mv source/gbif-NEW source/gbif-`cat raw/gbif/release`
	mv archive/gbif-NEW archive/gbif-`cat raw/gbif/release`

# --- Source: IRMNG

resource/$(IRMNG)/.made: source/$(IRMNG)/.made \
			 import_scripts/irmng/process_irmng.py
	@mkdir -p `dirname $@`.new
	python import_scripts/irmng/process_irmng.py \
	   source/$(IRMNG)/IRMNG_DWC.csv \
	   source/$(IRMNG)/IRMNG_DWC_SP_PROFILE.csv \
	   resource/$(IRMNG).new/taxonomy.tsv \
	   resource/$(IRMNG).new/synonyms.tsv
	touch resource/$(IRMNG).new/.made
	rm -rf resource/$(IRMNG)
	mv resource/$(IRMNG).new resource/$(IRMNG)

# Build IRMNG from Tony's .csv files

# should be same as for GBIF

new/irmng:
	@mkdir -p archive/irmng-NEW source/irmng-NEW raw/irmng
	wget -q --output-document=archive/irmng-NEW/archive.zip "$(IRMNG_ORIGIN_URL)"
	touch archive/irmng-NEW/.made
	(cd source/irmng-NEW && (unzip archive/irmng-NEW/archive.zip || true))
	touch source/irmng-NEW/.made
	python util/modification_date.py source/irmng-NEW/IRMNG_DWC.csv >raw/irmng/release
	echo New IRMNG version is `cat raw/irmng/release`
	[ -d archive/irmng-`cat raw/irmng/release` ] && (echo "Already got it!"; exit 1)
	mv source/irmng-NEW source/irmng-`cat raw/irmng/release`
	mv archive/irmng-NEW archive/irmng-`cat raw/irmng/release`

# --- Source: Open Tree curated amendments

# Dummy
unpack/$(AMENDMENTS):
	@true

store/$(AMENDMENTS):
	@true

resource/$(AMENDMENTS)/.made: raw/amendments/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1 && git checkout -q $(AMENDMENTS_REFSPEC))
	@mkdir -p resource/$(AMENDMENTS).new/amendments-1
	cp -pr raw/amendments/amendments-1/amendments \
	       resource/$(AMENDMENTS).new/amendments-1/
	echo $(AMENDMENTS_REFSPEC) > resource/$(AMENDMENTS).new/refspec
	touch resource/$(AMENDMENTS).new/.made
	rm -rf resource/$(AMENDMENTS)
	mv resource/$(AMENDMENTS).new resource/$(AMENDMENTS)

# fetch from github

raw/amendments/amendments-1:
	@mkdir -p raw/amendments
	(cd raw/amendments; git clone $(AMENDMENTS_ORIGIN_URL))

new/amendments: raw/amendments/amendments-1
	(cd raw/amendments/amendments-1 && git checkout master && git pull)
	(cd raw/amendments/amendments-1; git log -n 1) | head -1 | sed -e 's/commit //' >raw/amendments/refspec.new
	mv raw/amendments/refspec.new raw/amendments/refspec
	(cd raw/amendments/amendments-1; git checkout -q `cat ../../../raw/amendments/refspec`)
	head -c 7 raw/amendments/refspec > raw/amendments/version
	mkdir -p resource/amendments-`cat raw/amendments/version`/amendments-1
	cp -pr raw/amendments/amendments-1/amendments resource/amendments-`cat raw/amendments/version`/amendments-1/
	cp -p raw/amendments/refspec resource/amendments-`cat raw/amendments/version`/
	@echo "TBD: STORE NEW REFSPEC AND VERSION IN config.json"

# --- Source: Previous version of OTT, for id assignments

unpack/$(PREV-OTT):
	bin/unpack-archive archive/$(PREV-OTT) resource/$(PREV-OTT)

# Dummy, no action required
pack/$(PREV-OTT): archive/$(PREV-OTT)/archive.tgz

# --- Source: OTT id list compiled from all previous OTT versions

unpack/$(IDLIST):
	bin/unpack-archive archive/$(IDLIST) resource/$(IDLIST)

pack/$(IDLIST): resource/$(IDLIST)/.made
	bin/pack-archive resource/$(IDLIST) archive/$(IDLIST)

# When we build 3.1, IDLIST is idlist-3.0, which has ids through OTT 3.0.
# So, to make the id list for 3.1, we first make OTT (or get) 3.1, then
# combine the 3.0 id list with new registrations from 3.1.

NEW-IDLIST=idlist-$(WHICH)

new/idlist: resource/$(IDLIST)/.made
	@rm -rf resource/$(NEW-IDLIST)
	@mkdir -p resource/$(NEW-IDLIST)
	cp -pr resource/$(IDLIST)/regs resource/$(NEW-IDLIST)/
	python import_scripts/idlist/extend_idlist.py \
	       resource/$(IDLIST)/regs \
	       resource/$(PREV-OTT) \
	       $(PREV-OTT) \
	       resources/captures.json \
	       resource/$(NEW-IDLIST)/regs/$(PREV-OTT).csv
	touch resource/$(NEW-IDLIST)/.made

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

tarball: resource/$(OTT)/.made
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/$(OTT).tgz.tmp -C tax ott $(EXCL) && \
	 mv $(TARDIR)/$(OTT).tgz.tmp $(TARDIR)/$(OTT).tgz )
	@echo "Don't forget to bump the version number"

# Then, something like
# scp -p -i ~/.ssh/opentree/opentree.pem tarballs/ott2.9draft3.tgz \
#   opentree@ot10.opentreeoflife.org:files.opentreeoflife.org/ott/ott2.9/

# Not currently used since smasher already suppresses non-OTU deprecations
resource/$(OTT)/otu_deprecated.tsv: ids_that_are_otus.tsv resource/$(OTT)/deprecated.tsv
	$(SMASH) --join ids_that_are_otus.tsv resource/$(OTT)/deprecated.tsv >$@.new
	mv $@.new $@
	wc $@

# This file is big
work/$(OTT)/differences.tsv: resource/$(PREV-OTT)/.made resource/$(OTT)/.made
	$(SMASH) --diff resource/$(PREV-OTT)/ resource/$(OTT)/ $@.new
	mv $@.new $@
	wc $@

# OTUs only
resource/$(OTT)/otu_differences.tsv: work/$(OTT)/differences.tsv
	$(SMASH) --join ids_that_are_otus.tsv work/$(OTT)/differences.tsv >$@.new
	mv $@.new $@
	wc $@

resource/$(OTT)/otu_hidden.tsv: resource/$(OTT)/hidden.tsv
	$(SMASH) --join ids_that_are_otus.tsv resource/$(OTT)/hidden.tsv >$@.new
	mv $@.new $@
	wc $@

# The works
works: ott resource/$(OTT)/otu_differences.tsv resource/$(OTT)/forwards.tsv
	touch resource/$(OTT)/.made

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
	bin/jython util/check_inclusions.py inclusions.csv resource/$(OTT)/

# -----------------------------------------------------------------------------
# Asterales test system ('make test')

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: resource/$(PREV-OTT)/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) resource/$(PREV-OTT)/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on resource/$(NCBI)/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) resource/$(NCBI)/ --select2 $(TAXON) --out t/tax/ncbi_aster/

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
	rm -rf resource/$(OTT)
	rm -rf *.tmp new_taxa
	rm -rf resource/$(NCBI) resource/$(GBIF)
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf archive work resource tmp raw
