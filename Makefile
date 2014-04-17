# The tests work in JAR's setup...

# You'll need to put a copy of the previous (or baseline) version of OTT in tax/prev_ott/.
# This is a manual step.
# Get it from http://files.opentreeoflife.org/ott/
# and if there's a file "taxonomy" change that to "taxonomy.tsv".

WHICH=2.7draft1
PREV_WHICH=2.6

#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

# Scripts and other inputs related to taxonomy

# The tax/ directory is full of taxonomies; mostly (entirely?) derived objects.
NCBI=tax/ncbi
GBIF=tax/gbif
SILVA=tax/silva
IF=tax/if

# Root of local copy of taxomachine git repo, for nematode examples
# (TBD: make local copies so that setup is simpler)
TAXOMACHINE_EXAMPLE=../../taxomachine/example

# Preottol - for filling in the preottol id column
#  https://bitbucket.org/mtholder/ottol/src/dc0f89986c6c2a244b366312a76bae8c7be15742/preOTToL_20121112.txt?at=master
PREOTTOL=../../preottol

CP=-classpath ".:lib/*"
JAVA=java $(CP)
BIG_JAVA=$(JAVA) -Xmx12G
SMASH=org.opentreeoflife.smasher.Smasher
CLASS=org/opentreeoflife/smasher/Smasher.class

all: ott

compile: $(CLASS)

$(CLASS): org/opentreeoflife/smasher/Smasher.java \
	  org/opentreeoflife/smasher/Taxonomy.java \
	  org/opentreeoflife/smasher/Taxon.java \
	  lib/jscheme.jar lib/json-simple-1.1.1.jar lib/jython-standalone-2.5.3.jar
	javac -g $(CP) org/opentreeoflife/smasher/*.java

lib/jython-standalone-2.5.3.jar:
	wget -O "$@" "http://search.maven.org/remotecontent?filepath=org/python/jython-standalone/2.5.3/jython-standalone-2.5.3.jar"
	@ls -l $@

# --------------------------------------------------------------------------

# Add tax/if/ when it starts to work

OTT_ARGS=$(SMASH) $(SILVA)/ tax/713/ tax/if/ $(NCBI)/ $(GBIF)/ \
      --edits feed/ott/edits/ \
      --deforest \
      --ids tax/prev_ott/ \
      --out tax/ott/

ott: tax/ott/log.tsv
tax/ott/log.tsv: $(CLASS) make-ott.py $(SILVA)/taxonomy.tsv \
		    tax/if/taxonomy.tsv tax/713/taxonomy.tsv \
		    $(NCBI)/taxonomy.tsv $(GBIF)/taxonomy.tsv \
		    tax/irmng/taxonomy.tsv \
		    feed/ott/edits/ott_edits.tsv \
		    tax/prev_ott/taxonomy.tsv \
		    feed/misc/chromista_spreadsheet.py
	@mkdir -p tax/ott
	$(BIG_JAVA) $(SMASH) --jython make-ott.py
	echo $(WHICH) >tax/ott/version.txt

tax/if/taxonomy.tsv: tax/if/synonyms.tsv tax/if/about.json
	@mkdir -p `dirname $@`
	wget --output-document=$@ http://files.opentreeoflife.org/ott/IF/taxonomy.tsv
	@ls -l $@

tax/if/synonyms.tsv:
	@mkdir -p `dirname $@`
	wget --output-document=$@ http://files.opentreeoflife.org/ott/IF/synonyms.tsv
	@ls -l $@

tax/if/about.json:
	@mkdir -p `dirname $@`
	cp -p feed/if/about.json tax/if/

# Create the aux (preottol) mapping in a separate step.
# How does it know where to write to?

tax/ott/aux.tsv: $(CLASS) tax/ott/log.tsv
	$(BIG_JAVA) $(SMASH) tax/ott/ --aux $(PREOTTOL)/preottol-20121112.processed

$(PREOTTOL)/preottol-20121112.processed: $(PREOTTOL)/preOTToL_20121112.txt
	python process-preottol.py $< $@

tax/prev_ott/taxonomy.tsv:
	@mkdir -p feed/prev_ott/in 
	wget --output-document=feed/prev_ott/in/ott$(PREV_WHICH).tgz \
	  http://files.opentreeoflife.org/ott/ott$(PREV_WHICH).tgz
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

NCBI_URL="ftp://ftp.ncbi.nih.gov/pub/taxonomy/taxdump.tar.gz"

ncbi: $(NCBI)/taxonomy.tsv
$(NCBI)/taxonomy.tsv: feed/ncbi/in/nodes.dmp feed/ncbi/process_ncbi_taxonomy_taxdump.py 
	@mkdir -p $(NCBI).tmp
	python feed/ncbi/process_ncbi_taxonomy_taxdump.py F feed/ncbi/in \
            /dev/null $(NCBI).tmp $(NCBI_URL)
	rm -rf $(NCBI)
	mv -f $(NCBI).tmp $(NCBI)

feed/ncbi/in/nodes.dmp: feed/ncbi/taxdump.tar.gz
	@mkdir -p `dirname $@`
	tar -C feed/ncbi/in -xzvf feed/ncbi/taxdump.tar.gz
	touch $@

feed/ncbi/taxdump.tar.gz:
	@mkdir -p feed/ncbi
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

feed/gbif/in/checklist1.zip:
	@mkdir -p feed/gbif/in
	wget --output-document=$@ \
             http://ecat-dev.gbif.org/repository/export/checklist1.zip
	@ls -l $@

irmng: tax/irmng/taxonomy.tsv

tax/irmng/taxonomy.tsv: feed/irmng/process_irmng.py \
          feed/irmng/in/IRMNG_DWC_20140131.csv feed/irmng/process_irmng.py
	@mkdir -p `dirname $@`
	python feed/irmng/process_irmng.py \
	   feed/irmng/in/IRMNG_DWC_20140113.csv \
	   feed/irmng/in/IRMNG_DWC_SP_PROFILE_20140113.csv \
	   > tax/irmng/taxonomy.tsv

feed/irmng/in/IRMNG_DWC_20140113.csv: feed/irmng/in/IRMNG_DWC.zip
	(cd feed/irmng/in; unzip IRMNG_DWC.zip)

feed/irmng/in/IRMNG_DWC.zip:
	@mkdir -p `dirname $@`
	wget --output-document=$@.tmp "http://www.cmar.csiro.au/datacentre/downloads/IRMNG_DWC.zip"
	mv $@.tmp $@

# Significant tabs !!!

SILVA_URL=https://www.arb-silva.de/fileadmin/silva_databases/release_115/Exports/SSURef_NR99_115_tax_silva.fasta.tgz

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

# Silva 115: 206M uncompresses to 817M

feed/silva/in/silva.fasta:
	@mkdir -p `dirname $@`
	wget --output-document=feed/silva/in/tax_ranks.txt \
	  https://www.arb-silva.de/fileadmin/silva_databases/release_115/Exports/tax_ranks_ssu_115.txt
	@ls -l feed/silva/in/tax_ranks.txt
	wget --output-document=feed/silva/in/silva.fasta.tgz "$(SILVA_URL)"
	@ls -l feed/silva/in/silva.fasta.tgz
	(cd feed/silva/in && tar xzvf silva.fasta.tgz && mv *silva.fasta silva.fasta)

TARDIR=/raid/www/roots/opentree/ott

tarball: tax/ott/log.tsv
	(cd tax && \
	 tar czvf $(TARDIR)/ott$(WHICH).tgz.tmp ott && \
	 mv $(TARDIR)/ott$(WHICH).tgz.tmp $(TARDIR)/ott$(WHICH).tgz )
	@echo "Don't forget to bump the version number"

# This predates use of git on norbert...
#norbert:
#	rsync -vaxH --exclude=$(WORK) --exclude="*~" --exclude=backup \
#           ./ norbert.csail.mit.edu:/raid/jar/NESCent/opentree/smasher

# ERROR: certificate common name `google.com' doesn't match requested host name `code.google.com'.

lib/json-simple-1.1.1.jar:
	wget --output-document=$@ --no-check-certificate \
	  "https://json-simple.googlecode.com/files/json-simple-1.1.1.jar"
	@ls -l $@

# internal tests
test2: $(CLASS)
	$(JAVA) $(SMASH) --test


ids_report.tsv:
	time wget -O ids_report.csv "http://reelab.net/phylografter/ottol/ottol_names_report.csv/" 
	tr "," "	" <ids_report.csv >$@

short-list.tsv: ids_report.tsv tax/ott/deprecated.tsv
	grep "\\*" tax/ott/deprecated.tsv | grep -v "excluded" >dep-tmp.tsv
	$(JAVA) $(SMASH) --join ids_report.tsv dep-tmp.tsv >$@
	wc $@

clean:
	rm -rf feed/*/in
	rm -rf tax/if tax/ncbi tax/prev_nem tax/silva
	rm -f $(CLASS)
	rm -f feed/ncbi/taxdump.tar.gz

z: feed/misc/chromista_spreadsheet.py
feed/misc/chromista_spreadsheet.py: feed/misc/chromista-spreadsheet.csv feed/misc/process_chromista_spreadsheet.py
	python feed/misc/process_chromista_spreadsheet.py \
           feed/misc/chromista-spreadsheet.csv >feed/misc/chromista_spreadsheet.py

# -----------------------------------------------------------------------------
# Test: nematodes

# Nematode test
TEST_ARGS=$(SMASH) tax/nem_ncbi/ tax/nem_gbif/ \
      --edits feed/nem/edits/ \
      --ids tax/prev_nem/ \
      --out tax/nem/

nem: tax/nem/log.tsv
tax/nem/log.tsv: $(CLASS) tax/prev_nem/taxonomy.tsv tax/nem_ncbi/taxonomy.tsv tax/nem_gbif/taxonomy.tsv
	@mkdir -p tax/nem/
	$(JAVA) $(TEST_ARGS)

# Inputs to the nem test

tax/nem_ncbi/taxonomy.tsv:
	@mkdir -p `dirname $@`
	cp -p $(TAXOMACHINE_EXAMPLE)/nematoda.ncbi $@

tax/nem_gbif/taxonomy.tsv:
	@mkdir -p `dirname $@`
	cp -p $(TAXOMACHINE_EXAMPLE)/nematoda.gbif $@

# little test of --select feature
tax/nem_dory/taxonomy.tsv: tax/nem/log.tsv $(CLASS)
	@mkdir -p `dirname $@`
	$(JAVA) $(SMASH) --start tax/nem/ --select Dorylaimina tax/nem_dory/

# -----------------------------------------------------------------------------
# Model village: Asterales

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: tax/prev_ott/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(BIG_JAVA) $(SMASH) tax/prev_ott/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on tax/ncbi/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(BIG_JAVA) $(SMASH) tax/ncbi/ --select2 $(TAXON) --out t/tax/ncbi_aster/

# dependency on tax/gbif/taxonomy.tsv - correct but expensive
t/tax/gbif_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(BIG_JAVA) $(SMASH) tax/gbif/ --select2 $(TAXON) --out t/tax/gbif_aster/

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
                          t/edits/edits.tsv
	@mkdir -p `dirname $@`
	$(JAVA) $(SMASH) --jython t/aster.py

test: aster
aster: t/tax/aster/taxonomy.tsv

aster-tarball: t/tax/aster/taxonomy.tsv
	(cd t/tax && \
	 tar -czvf $(TARDIR)/aster.tgz.tmp aster && \
	 mv $(TARDIR)/aster.tgz.tmp $(TARDIR)/aster.tgz )
