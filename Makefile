# The tests work in JAR's setup...

# You'll need to put a copy of the previous (or baseline) version of OTT in tax/prev_ott/.
# This is a manual step.
# Get it from http://files.opentreeoflife.org/ott/

# Modify as appropriate to your own hardware
JAVAFLAGS=-Xmx14G

# Modify as appropriate
WHICH=2.10draft2
PREV_WHICH=2.9

#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

# Scripts and other inputs related to taxonomy

# The tax/ directory is full of taxonomies; mostly (entirely?) derived objects.
NCBI=tax/ncbi
GBIF=tax/gbif
SILVA=tax/silva
FUNG=tax/fung

# Preottol - for filling in the preottol id column
#  https://bitbucket.org/mtholder/ottol/src/dc0f89986c6c2a244b366312a76bae8c7be15742/preOTToL_20121112.txt?at=master
PREOTTOL=../../preottol

CP=-classpath ".:lib/*"
JAVA=JYTHONPATH=util java $(JAVAFLAGS) $(CP)
SMASH=org.opentreeoflife.smasher.Smasher
CLASS=org/opentreeoflife/smasher/Smasher.class
JAVASOURCES=$(shell find org/opentreeoflife -name "*.java")

all: ott

compile: $(CLASS)

$(CLASS): $(JAVASOURCES) \
	  lib/json-simple-1.1.1.jar lib/jython-standalone-2.7.0.jar \
	  lib/junit-4.12.jar
	javac -g $(CP) $(JAVASOURCES)

lib/jython-standalone-2.7.0.jar:
	wget -O "$@" --no-check-certificate \
	 "http://search.maven.org/remotecontent?filepath=org/python/jython-standalone/2.7.0/jython-standalone-2.7.0.jar"
	@ls -l $@

bin/jython:
	mkdir -p bin
	(echo "#!/bin/bash"; \
	 echo "export JYTHONPATH=.:$$PWD:$$PWD/util:$$PWD/lib/json-simple-1.1.1.jar"; \
	 echo exec java "$(JAVAFLAGS)" -jar $$PWD/lib/jython-standalone-2.7.0.jar '$$*') >$@
	chmod +x $@

# Daemon
bin/smasher:
	mkdir -p bin
	(echo "#!/bin/bash"; \
	 echo "cd $$PWD/service"; \
	 echo ./service '$$*') >$@
	chmod +x $@

# --------------------------------------------------------------------------

OTT_ARGS=$(SMASH) $(SILVA)/ tax/713/ tax/fung/ $(NCBI)/ $(GBIF)/ \
      --edits feed/ott/edits/ \
      --deforest \
      --ids tax/prev_ott/ \
      --out tax/ott/

ott: tax/ott/log.tsv
tax/ott/log.tsv: $(CLASS) make-ott.py assemble_ott.py taxonomies.py \
                    tax/silva/taxonomy.tsv \
		    tax/fung/taxonomy.tsv tax/713/taxonomy.tsv \
		    $(NCBI)/taxonomy.tsv $(GBIF)/taxonomy.tsv \
		    tax/irmng/taxonomy.tsv \
		    tax/worms/taxonomy.tsv \
		    feed/ott/edits/ott_edits.tsv \
		    tax/prev_ott/taxonomy.tsv \
		    feed/misc/chromista_spreadsheet.py
	@rm -f *py.class
	@mkdir -p tax/ott
	time bin/jython make-ott.py
	echo $(WHICH) >tax/ott/version.txt

# Index Fungorum

fung: tax/fung/taxonomy.tsv tax/fung/synonyms.tsv

# 12787947 Oct  6  2015 taxonomy.tsv
FUNG_URL=http://files.opentreeoflife.org/fung/fung-9/fung-9-ot.tgz

tax/fung/taxonomy.tsv: 
	@mkdir -p tmp
	wget --output-document=tmp/fung-ot.tgz $@ $(FUNG_URL)
	(cd tmp; tar xzf fung-ot.tgz)
	@mkdir -p `dirname $@`
	mv tmp/fung*/* `dirname $@`/
	@ls -l $@

tax/fung/about.json:
	@mkdir -p `dirname $@`
	cp -p feed/fung/about.json tax/fung/

# Create the aux (preottol) mapping in a separate step.
# How does it know where to write to?

tax/ott/aux.tsv: $(CLASS) tax/ott/log.tsv
	$(JAVA) $(SMASH) tax/ott/ --aux $(PREOTTOL)/preottol-20121112.processed

$(PREOTTOL)/preottol-20121112.processed: $(PREOTTOL)/preOTToL_20121112.txt
	python util/process-preottol.py $< $@

tax/prev_ott/taxonomy.tsv:
	@mkdir -p feed/prev_ott/in 
	wget --output-document=feed/prev_ott/in/ott$(PREV_WHICH).tgz \
	  http://files.opentreeoflife.org/ott/ott$(PREV_WHICH)/ott$(PREV_WHICH).tgz
	@ls -l feed/prev_ott/in/ott$(PREV_WHICH).tgz
	(cd feed/prev_ott/in/ && tar xvf ott$(PREV_WHICH).tgz)
	rm -rf tax/prev_ott
	@mkdir -p tax/prev_ott
	mv feed/prev_ott/in/ott*/* tax/prev_ott/
	if [ -e tax/prev_ott/taxonomy ]; then mv tax/prev_ott/taxonomy tax/prev_ott/taxonomy.tsv; fi
	if [ -e tax/prev_ott/synonyms ]; then mv tax/prev_ott/synonyms tax/prev_ott/synonyms.tsv; fi
	rm -rf feed/prev_ott/in

# Formerly, where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

# NCBI_URL="ftp://ftp.ncbi.nih.gov/pub/taxonomy/taxdump.tar.gz"
NCBI_URL="http://files.opentreeoflife.org/ncbi/ncbi-20151006/ncbi-20151006.tgz"

ncbi: $(NCBI)/taxonomy.tsv
$(NCBI)/taxonomy.tsv: feed/ncbi/in/nodes.dmp feed/ncbi/process_ncbi_taxonomy_taxdump.py 
	@mkdir -p $(NCBI).tmp
	@mkdir -p feed/ncbi/in
	python feed/ncbi/process_ncbi_taxonomy_taxdump.py F feed/ncbi/in \
            /dev/null $(NCBI).tmp $(NCBI_URL)
	rm -rf $(NCBI)
	mv -f $(NCBI).tmp $(NCBI)

feed/ncbi/in/nodes.dmp: feed/ncbi/in/taxdump.tar.gz
	@mkdir -p `dirname $@`
	tar -C feed/ncbi/in -xzvf feed/ncbi/in/taxdump.tar.gz
	touch $@

feed/ncbi/in/taxdump.tar.gz:
	@mkdir -p feed/ncbi/in
	wget --output-document=$@ $(NCBI_URL)
	@ls -l $@

# Formerly, where it says /dev/null, we had ../data/gbif/ignore.txt

gbif: $(GBIF)/taxonomy.tsv
$(GBIF)/taxonomy.tsv: feed/gbif/in/taxon.txt feed/gbif/process_gbif_taxonomy.py
	@mkdir -p $(GBIF).tmp
	python feed/gbif/process_gbif_taxonomy.py \
	       feed/gbif/in/taxon.txt \
	       /dev/null $(GBIF).tmp
	cp -p feed/gbif/about.json $(GBIF).tmp/
	rm -rf $(GBIF)
	mv -f $(GBIF).tmp $(GBIF)

# The '|| true' is because unzip erroneously returns status code 1
# when there are warnings.
feed/gbif/in/taxon.txt: feed/gbif/in/checklist1.zip
	(cd feed/gbif/in && (unzip checklist1.zip || true))
	touch feed/gbif/in/taxon.txt

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip
GBIF_URL=http://files.opentreeoflife.org/gbif/gbif-20130702/gbif-20130702.zip

feed/gbif/in/checklist1.zip:
	@mkdir -p feed/gbif/in
	wget --output-document=$@ "$(GBIF_URL)"
	@ls -l $@

irmng: tax/irmng/taxonomy.tsv

IRMNG_URL=http://files.opentreeoflife.org/irmng-ot/irmng-ot-20141020/irmng-ot-20141020.tgz

tax/irmng/taxonomy.tsv:
	@mkdir -p tax/irmng tmp
	wget --output-document=tmp/irmng-ot.tgz $(IRMNG_URL)
	(cd tmp; tar xzf irmng-ot.tgz)
	rm -f tax/irmng/*
	mv tmp/irmng-ot*/* tax/irmng/

# Build IRMNG from Tony's .csv files - these unfortunately are not public

refresh-irmng: feed/irmng/process_irmng.py feed/irmng/in/IRMNG_DWC.csv 
	@mkdir -p `dirname $@`
	python feed/irmng/process_irmng.py \
	   feed/irmng/in/IRMNG_DWC.csv \
	   feed/irmng/in/IRMNG_DWC_SP_PROFILE.csv \
	   tax/irmng/taxonomy.tsv \
	   tax/irmng/synonyms.tsv

feed/irmng/in/IRMNG_DWC.csv: feed/irmng/in/IRMNG_DWC.zip
	(cd feed/irmng/in && \
	 unzip IRMNG_DWC.zip && \
	 mv IRMNG_DWC_2???????.csv IRMNG_DWC.csv && \
	 mv IRMNG_DWC_SP_PROFILE_2???????.csv IRMNG_DWC_SP_PROFILE.csv)

feed/irmng/in/IRMNG_DWC.zip:
	@mkdir -p `dirname $@`
	wget --output-document=$@.tmp "http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip"
	mv $@.tmp $@

WORMS_URL=http://files.opentreeoflife.org/worms/worms-1/worms-1-ot.tgz

tax/worms/taxonomy.tsv:
	@mkdir -p tax/worms tmp
	wget --output-document=tmp/worms-1-ot.tgz $(WORMS_URL)
	(cd tmp; tar xzf worms-1-ot.tgz)
	rm -f tax/worms/*
	mv tmp/worms-1-ot*/* tax/worms/

# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# tax_ranks file moved to ftp://ftp.arb-silva.de/release_115/Exports/tax_ranks_ssu_115.csv ?
# issue #62 - verify  (is it a tsv file or csv file?)
# see also http://www.arb-silva.de/no_cache/download/archive/release_115/ ?

SILVA_EXPORTS=ftp://ftp.arb-silva.de/release_115/Exports
SILVA_URL=$(SILVA_EXPORTS)/SSURef_NR99_115_tax_silva.fasta.tgz
SILVA_RANKS_URL=$(SILVA_EXPORTS)/tax_ranks_ssu_115.csv

silva: $(SILVA)/taxonomy.tsv
$(SILVA)/taxonomy.tsv: feed/silva/process_silva.py feed/silva/in/silva.fasta feed/silva/in/accessionid_to_taxonid.tsv 
	@mkdir -p feed/silva/out
	python feed/silva/process_silva.py feed/silva/in feed/silva/out "$(SILVA_URL)"
	@mkdir -p $(SILVA)
	cp -p feed/silva/out/taxonomy.tsv $(SILVA)/
	cp -p feed/silva/out/synonyms.tsv $(SILVA)/
	cp -p feed/silva/out/about.json $(SILVA)/

feed/silva/in/accessionid_to_taxonid.tsv: feed/silva/accessionid_to_taxonid.tsv
	@mkdir -p `dirname $@`
	(cd `dirname $@` && ln -sf ../accessionid_to_taxonid.tsv ./)

feed/silva/in/silva.fasta:
	@mkdir -p `dirname $@`
	wget --output-document=feed/silva/in/tax_ranks.txt $(SILVA_RANKS_URL)
	@ls -l feed/silva/in/tax_ranks.txt
	wget --output-document=feed/silva/in/silva.fasta.tgz "$(SILVA_URL)"
	@ls -l feed/silva/in/silva.fasta.tgz
	(cd feed/silva/in && tar xzvf silva.fasta.tgz && mv *silva.fasta silva.fasta)

#TARDIR=/raid/www/roots/opentree/ott
TARDIR?=tarballs

tarball: tax/ott/log.tsv
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/ott$(WHICH).tgz.tmp -C tax ott \
	   --exclude differences.tsv && \
	 mv $(TARDIR)/ott$(WHICH).tgz.tmp $(TARDIR)/ott$(WHICH).tgz )
	@echo "Don't forget to bump the version number"

# Then, something like
# scp -p -i ~/.ssh/opentree/opentree.pem tarballs/ott2.9draft3.tgz \
#   opentree@ot10.opentreeoflife.org:files.opentreeoflife.org/ott/ott2.9/

# This predates use of git on norbert...
#norbert:
#	rsync -vaxH --exclude=$(WORK) --exclude="*~" --exclude=backup \
#           ./ norbert.csail.mit.edu:/raid/jar/NESCent/opentree/smasher

# ERROR: certificate common name `google.com' doesn't match requested host name `code.google.com'.

lib/json-simple-1.1.1.jar:
	wget --output-document=$@ --no-check-certificate \
	  "https://json-simple.googlecode.com/files/json-simple-1.1.1.jar"
	@ls -l $@

lib/junit-4.12.jar:
	wget --output-document=$@ --no-check-certificate \
	  "http://search.maven.org/remotecontent?filepath=junit/junit/4.12/junit-4.12.jar"
	@ls -l $@

test-smasher: compile
	$(JAVA) org.opentreeoflife.smasher.Test

z: feed/misc/chromista_spreadsheet.py
feed/misc/chromista_spreadsheet.py: feed/misc/chromista-spreadsheet.csv feed/misc/process_chromista_spreadsheet.py
	python feed/misc/process_chromista_spreadsheet.py \
           feed/misc/chromista-spreadsheet.csv >feed/misc/chromista_spreadsheet.py

# internal tests
test2: $(CLASS)
	$(JAVA) $(SMASH) --test


old-ids-that-are-otus.tsv:
	time wget -O ids_report.csv "http://reelab.net/phylografter/ottol/ottol_names_report.csv/" 
	tr "," "	" <ids_report.csv >$@
	rm ids_report.csv

# This typically won't run since the target is checked in
ids-that-are-otus.tsv:
	time python util/ids-that-are-otus.py $@.new
	mv $@.new $@
	wc $@

tax/ott/otu_deprecated.tsv: ids-that-are-otus.tsv tax/ott/deprecated.tsv
	$(JAVA) $(SMASH) --join ids-that-are-otus.tsv tax/ott/deprecated.tsv >$@.new
	mv $@.new $@
	wc $@

tax/ott/differences.tsv: tax/prev_ott/taxonomy.tsv tax/ott/taxonomy.tsv
	$(JAVA) $(SMASH) --diff tax/prev_ott/ tax/ott/ $@.new
	mv $@.new $@
	wc $@

tax/ott/otu_differences.tsv: tax/ott/differences.tsv
	$(JAVA) $(SMASH) --join ids-that-are-otus.tsv tax/ott/differences.tsv >$@.new
	mv $@.new $@
	wc $@

tax/ott/otu_hidden.tsv: tax/ott/hidden.tsv
	$(JAVA) $(SMASH) --join ids-that-are-otus.tsv tax/ott/hidden.tsv >$@.new
	mv $@.new $@
	wc $@

tax/ott/forwards.tsv: tax/ott/new-forwards.tsv legacy-forwards.tsv util/get_forwards.py
	cat legacy-forwards.tsv tax/ott/new-forwards.tsv | python util/get_forwards.py $@.new
	mv $@.new $@

# The works
works: ott tax/ott/otu_differences.tsv tax/ott/forwards.tsv

clean:
	rm -rf feed/*/in
	rm -rf tax/fung tax/ncbi tax/prev_nem tax/silva
	rm -f $(CLASS)
#	rm -f feed/ncbi/in/taxdump.tar.gz

# -----------------------------------------------------------------------------
# Model village: Asterales

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

test: aster
aster: t/tax/aster/taxonomy.tsv

aster-tarball: t/tax/aster/taxonomy.tsv
	(mkdir -p $(TARDIR) && \
	 tar czvf $(TARDIR)/aster.tgz.tmp -C t/tax aster && \
	 mv $(TARDIR)/aster.tgz.tmp $(TARDIR)/aster.tgz )

check:
	bash run-tests.sh

inclusion-tests: ../germinator/taxa/inclusions.csv tax/ott/log.tsv
	bin/jython util/check_inclusions.py ../germinator/taxa/inclusions.csv tax/ott/
