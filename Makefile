# The tests work in JAR's setup...

# You'll need to put a copy of the previous (or baseline) version of OTT in tax/prev_ott/.
# This is a manual step.
# Get it from http://files.opentreeoflife.org/ott/

#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

# Modify as appropriate to your own hardware - I set it one or two Gbyte
# below physical memory size
JAVAFLAGS=-Xmx14G

# Modify as appropriate
WHICH=3.0draft3a
PREV_WHICH=2.10

# ----- Taxonomy source locations -----

# 12787947 Oct  6  2015 taxonomy.tsv
FUNG_URL=http://files.opentreeoflife.org/fung/fung-9/fung-9-ot.tgz

WORMS_URL=http://files.opentreeoflife.org/worms/worms-1/worms-1-ot.tgz

NCBI_URL="http://files.opentreeoflife.org/ncbi/ncbi-20161109/ncbi-20161109.tgz"

IDLIST_URL="http://files.opentreeoflife.org/ott_id_list/idlist-20161118/by_qid.csv.gz"

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip
GBIF_URL=http://files.opentreeoflife.org/gbif/gbif-20160729/gbif-20160729.zip

IRMNG_URL=http://files.opentreeoflife.org/irmng-ot/irmng-ot-20161108/irmng-ot-20161108.tgz

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)
# see also http://www.arb-silva.de/no_cache/download/archive/release_115/ ?

SILVA_EXPORTS=ftp://ftp.arb-silva.de/release_115/Exports
SILVA_URL=$(SILVA_EXPORTS)/SSURef_NR99_115_tax_silva.fasta.tgz

# This is used as a source of OTT id assignments.
PREV_OTT_URL=http://files.opentreeoflife.org/ott/ott$(PREV_WHICH)/ott$(PREV_WHICH).tgz

# 9 Sep 2016
AMENDMENTS_REFSPEC=feed/amendments/refspec

# -----

# Where to put tarballs
#TARDIR=/raid/www/roots/opentree/ott
TARDIR?=tarballs

# Scripts and other inputs related to taxonomy

# The tax/ directory is full of taxonomies; mostly (entirely?) derived objects.
FUNG=tax/fung

CP=-classpath ".:lib/*"
JAVA=JYTHONPATH=util java $(JAVAFLAGS) $(CP)
SMASH=org.opentreeoflife.smasher.Smasher
CLASS=org/opentreeoflife/smasher/Smasher.class
JAVASOURCES=$(shell find org/opentreeoflife -name "*.java")

# ----- Targets

all: compile

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

# The open tree taxonomy

ott: tax/ott/log.tsv tax/ott/version.txt tax/ott/README.html
tax/ott/log.tsv: $(CLASS) make-ott.py assemble_ott.py adjustments.py amendments.py \
                    tax/silva/taxonomy.tsv \
		    tax/fung/taxonomy.tsv tax/713/taxonomy.tsv \
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
		    tax/skel/taxonomy.tsv \
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

# ----- Taxonomy inputs

feed/ott_id_list/by_qid.csv:
	@mkdir -p feed/ott_id_list

	@mkdir -p tmp
	@mkdir -p `dirname $@`
	wget --output-document=tmp/by_qid.csv.gz $(IDLIST_URL)
	(cd tmp; gunzip by_qid.csv.gz)
	mv tmp/by_qid.csv `dirname $@`/
	@ls -l $@

# Input: Index Fungorum

fung: tax/fung/taxonomy.tsv tax/fung/synonyms.tsv

tax/fung/taxonomy.tsv: 
	@mkdir -p tmp
	@mkdir -p `dirname $@`
	wget --output-document=tmp/fung-ot.tgz $(FUNG_URL)
	(cd tmp; tar xzf fung-ot.tgz)
	mv tmp/fung*/* `dirname $@`/
	@ls -l $@

tax/fung/about.json:
	@mkdir -p `dirname $@`
	cp -p feed/fung/about.json tax/fung/

# Input: NCBI Taxonomy
# Formerly, where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

ncbi: tax/ncbi/taxonomy.tsv
tax/ncbi/taxonomy.tsv: feed/ncbi/in/nodes.dmp feed/ncbi/process_ncbi_taxonomy_taxdump.py 
	@mkdir -p tax/ncbi.tmp
	@mkdir -p feed/ncbi/in
	python feed/ncbi/process_ncbi_taxonomy_taxdump.py F feed/ncbi/in \
            /dev/null tax/ncbi.tmp $(NCBI_URL)
	rm -rf tax/ncbi
	mv -f tax/ncbi.tmp tax/ncbi

feed/ncbi/in/nodes.dmp: feed/ncbi/in/taxdump.tar.gz
	@mkdir -p `dirname $@`
	tar -C feed/ncbi/in -xzvf feed/ncbi/in/taxdump.tar.gz
	touch $@

feed/ncbi/in/taxdump.tar.gz:
	@mkdir -p feed/ncbi/in
	wget --output-document=$@.new $(NCBI_URL)
	mv $@.new $@
	@ls -l $@

NCBI_ORIGIN_URL=ftp://ftp.ncbi.nlm.nih.gov/pub/taxonomy/taxdump.tar.gz
NCBI_TAXDUMP=feed/ncbi/in/taxdump.tar.gz

refresh-ncbi:
	@mkdir -p feed/ncbi/in
	wget --output-document=$(NCBI_TAXDUMP).new $(NCBI_ORIGIN_URL)
	mv $(NCBI_TAXDUMP).new $(NCBI_TAXDUMP)
	@ls -l $(NCBI_TAXDUMP)

# Don't forget to scp -p feed/ncbi/in/taxdump.tar.gz to
#  files:files.opentreeoflife.org/ncbi/ncbi-YYYYMMDD/ncbi-YYYYMMDD.tgz

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
	wget --output-document=tmp/worms-1-ot.tgz $(WORMS_URL)
	(cd tmp; tar xzf worms-1-ot.tgz)
	rm -f tax/worms/*
	mv tmp/worms-1-ot*/* tax/worms/

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
	bin/publish-taxonomy irmng-ot


# Input: SILVA
# Significant tabs !!!

feed/silva/out/taxonomy.tsv: feed/silva/process_silva.py feed/silva/work/silva_no_sequences.fasta feed/silva/work/accessions.tsv 
	@mkdir -p feed/silva/out
	python feed/silva/process_silva.py \
	       feed/silva/work/silva_no_sequences.fasta \
	       feed/silva/work/accessions.tsv \
	       feed/silva/out "$(SILVA_URL)"

silva: tax/silva/taxonomy.tsv

tax/silva/taxonomy.tsv: feed/silva/out/taxonomy.tsv
	@mkdir -p tax/silva
	cp -p feed/silva/out/* tax/silva/

feed/silva/in/silva.fasta:
	@mkdir -p `dirname $@`
	wget --output-document=$@.tgz.new "$(SILVA_URL)"
	mv $@.tgz.new $@.tgz
	@ls -l $@.tgz
	(cd feed/silva/in && tar xzvf silva.fasta.tgz && mv *silva.fasta silva.fasta)

# To make loading the information faster, we remove all the sequence data
feed/silva/work/silva_no_sequences.fasta: feed/silva/in/silva.fasta
	@mkdir -p feed/silva/work
	grep ">.*;" $< >$@.new
	mv $@.new $@

# This file has genbank id, ncbi id, strain, taxon name
feed/silva/work/accessions.tsv: feed/silva/work/silva_no_sequences.fasta \
				tax/ncbi/taxonomy.tsv \
				feed/silva/accessionid_to_taxonid.tsv
	python feed/silva/get_taxon_names.py \
	       tax/ncbi/taxonomy.tsv \
	       feed/silva/accessionid_to_taxonid.tsv \
	       $@.new
	mv $@.new $@

# No longer used

SILVA_RANKS_URL=$(SILVA_EXPORTS)/tax_ranks_ssu_115.csv
feed/silva/in/tax_ranks.txt:
	@mkdir -p `dirname $@`
	wget --output-document=$@.new $(SILVA_RANKS_URL)
	mv $@.new $@
	@ls -l $@

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
ids_in_synthesis.tsv:
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
	$(JAVA) $(SMASH) tax/ott/ --aux $(PREOTTOL)/preottol-20121112.processed

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
	$(JAVA) $(SMASH) --join ids_that_are_otus.tsv tax/ott/deprecated.tsv >$@.new
	mv $@.new $@
	wc $@

# This file is big
tax/ott/differences.tsv: tax/prev_ott/taxonomy.tsv tax/ott/taxonomy.tsv
	$(JAVA) $(SMASH) --diff tax/prev_ott/ tax/ott/ $@.new
	mv $@.new $@
	wc $@

# OTUs only
tax/ott/otu_differences.tsv: tax/ott/differences.tsv
	$(JAVA) $(SMASH) --join ids_that_are_otus.tsv tax/ott/differences.tsv >$@.new
	mv $@.new $@
	wc $@

tax/ott/otu_hidden.tsv: tax/ott/hidden.tsv
	$(JAVA) $(SMASH) --join ids_that_are_otus.tsv tax/ott/hidden.tsv >$@.new
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
	$(JAVA) $(SMASH) --test

check:
	bash run-tests.sh

inclusion-tests: inclusions.csv 
	bin/jython util/check_inclusions.py inclusions.csv tax/ott/

# -----------------------------------------------------------------------------
# Asterales test system ('make test')

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: tax/prev_ott/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(JAVA) $(SMASH) tax/prev_ott/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on tax/ncbi/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(JAVA) $(SMASH) tax/ncbi/ --select2 $(TAXON) --out t/tax/ncbi_aster/

# dependency on tax/gbif/taxonomy.tsv - correct but expensive
t/tax/gbif_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(JAVA) $(SMASH) tax/gbif/ --select2 $(TAXON) --out t/tax/gbif_aster/

# Previously:
#t/tax/aster/taxonomy.tsv: $(CLASS) \
#                          t/tax/ncbi_aster/taxonomy.tsv \
#                          t/tax/gbif_aster/taxonomy.tsv \
#                          t/tax/prev_aster/taxonomy.tsv \
#                          t/edits/edits.tsv
#        @mkdir -p `dirname $@`
#        $(JAVA) $(SMASH) t/tax/ncbi_aster/ t/tax/gbif_aster/ \
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

# ----- Clean

# The 'clean' target deletes everything except files fetched from the Internet.
# To really delete everything, use the 'distclean' target.

clean:
	rm -f `find . -name "*.class"`
	rm -rf bin/jython
	rm -rf tax/ott
	rm -rf feed/*/out feed/*/work
	rm -rf *.tmp new_taxa
	rm -rf tax/ncbi tax/gbif tax/silva
	rm -f feed/misc/chromista_spreadsheet.py
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf feed/amendments/amendments-1
	rm -rf feed/*/in
	rm -rf tax/fung tax/irmng* tax/worms 
	rm -rf tax/prev_ott
