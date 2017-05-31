# The tests work in JAR's setup...

#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target
#  $(*F) = The directory part and the file-within-directory part of
#    the stem (stem = % in pattern rules)

# Modify as appropriate to your own hardware - I set it one or two Gbyte
# below physical memory size
JAVAFLAGS=-Xmx14G

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

fetch: $(FETCHES)

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

# for debugging
ott: r/ott-NEW/source/debug/transcript.out

refresh/ott: r/ott-NEW/source/.made
	bin/christen ott-NEW

# The works
# Reinstate later: r/ott-NEW/source/debug/otu_differences.tsv
r/ott-NEW/source/.made: r/ott-NEW/source/debug/transcript.out \
		       	     r/ott-NEW/source/version.txt \
		       	     r/ott-NEW/source/README.html
	touch $@

r/ott-NEW/source/debug/transcript.out: bin/jython $(CLASS) \
            make-ott.py assemble_ott.py \
	    curation/adjustments.py \
	    curation/amendments.py \
	    util/proposition.py \
	    curation/separation/taxonomy.tsv \
	    $(RESOURCES) \
	    r/idlist-HEAD/source/.made \
	    r/ott-HEAD/resource/.made \
	    curation/lamiales/taxonomy.tsv \
	    curation/h2007/tree.tre \
	    curation/edits/ott_edits.tsv \
	    ids_that_are_otus.tsv ids_in_synthesis.tsv \
	    inclusions.csv \
	    r/ott-NEW/source
	@date
	@rm -f *py.class util/*py.class curation/*py.class
	@mkdir -p r/ott-NEW/source
	@echo SET THE VERSION.
	bin/put ott-NEW draft $$((1 + `bin/get ott-NEW draft 0`))
	@echo Writing transcript to r/ott-NEW/source/debug/transcript.out
	rm -f /tmp/make-ott-completed
	(time bin/jython make-ott.py ott-NEW 2>&1 \
	  && touch /tmp/make-ott-completed) \
	  | tee r/ott-NEW/source/transcript.out.new
	[ -e /tmp/make-ott-completed ]
	bin/put ott-NEW "generated_on" `date +"%Y%m%d"`
	bin/put ott-NEW "date" `date +"%Y%m%d"`
	(cd r/ott-NEW/source && \
	 [ -e taxonomy.tsv ] && \
	 mkdir -p debug && \
	 mv *.* debug/ && \
	 mv debug/taxonomy.tsv debug/synonyms.tsv debug/forwards.tsv ./ && \
	 mv debug/transcript.out.new debug/transcript.out)

r/ott-NEW/source/version.txt: r/ott-NEW/source
	echo `bin/get ott-NEW version`draft`bin/get ott-NEW draft` \
	    >r/ott-NEW/source/version.txt

r/ott-NEW/source/README.html: r/ott-NEW/source/debug/transcript.out util/make_readme.py
	python util/make_readme.py r/ott-NEW/source/ >$@

r/ott-NEW/source: r/ott-HEAD/resource/.made
	bin/new-version ott .tgz cc0
	bin/put ott-NEW minor $$((1 + `bin/get ott-HEAD minor`))
	bin/put ott-NEW draft 0
	bin/put ott-NEW ott_idspace ott
	mkdir $@

r/ott-HEAD/resource/.made: r/ott-HEAD/source/.made
	(cd r/ott-HEAD && rm -f resource && ln -s source resource)


# ----- Taxonomy sources

# N.b. unpack-archive will fetch an archive if there isn't one already
# there

# Pattern rules!

# From the gnu make manual, re pattern rules:
# "all of its prerequisites (after pattern substitution) must name
# files that exist or can be made"
# For our purposes this means most pattern rules should have zero
# prerequisistes.

# My experience is that this is not true.  The pattern rule won't
# apply even if the prerequisiste *can* be made.

# "It is possible that more than one pattern rule will meet these
# criteria. In that case, make will choose the rule with the
# shortest stem (that is, the pattern that matches most
# specifically). If more than one pattern rule has the shortest
# stem, make will choose the first one found in the makefile."

fetch-all: $(UNPACKS)

# unpack does a fetch, if necessary

unpack/%:
	bin/unpack-archive $(*F)

# (why doesn't this just use bin/set-head?)

unpack-to-head/%: unpack/%
	@h=r/`bin/get $(*F) series`-HEAD && \
	  if [ -L $$h ]; then \
	    true; else  \
	    echo $$h := $(*F); ln -s $(*F) $$h; \
	  fi

# store does a pack, if necessary

store/%:
	if [ -e r/$(*F)/source/.made ]; then bin/store-archive $(*F); fi

store-all: $(STORES)

# Not quite right... need to handle draft releases
ott-release: ott store-all

# --- Source: SILVA
# Significant tabs !!!

# Silva 115: 206M uncompresses to 817M
# issue #62 - verify  (is it a tsv file or csv file?)

# Create the taxonomy import files from the no_sequences digest & accessions
r/silva-HEAD/resource/.made: import_scripts/silva/process_silva.py \
			 r/silva-HEAD/source/.made \
			 r/silva-HEAD/work/cluster_names.tsv
	@mkdir -p r/silva-HEAD/resource
	python import_scripts/silva/process_silva.py \
	       r/silva-HEAD/source/silva_no_sequences.fasta \
	       r/silva-HEAD/work/cluster_names.tsv \
	       r/silva-HEAD/source/origin_info.json \
	       r/silva-HEAD/resource
	touch $@

# Get taxon names from Genbank accessions file.
# The accessions file has genbank id, ncbi id, strain, taxon name.
# -- it seems the accessions file now has taxon names, so we
# probably don't need to take NCBI taxonomy as an input.
r/silva-HEAD/work/cluster_names.tsv: r/ncbi-HEAD/resource/.made \
				     r/genbank-HEAD/resource/.made
	@mkdir -p `dirname $@`
	python import_scripts/silva/get_taxon_names.py \
	       r/ncbi-HEAD/resource/taxonomy.tsv \
	       r/genbank-HEAD/resource/accessions.tsv \
	       $@.new
	mv -f $@.new $@

# Refresh from web.


# Digestify fasta file and create sources for archive

refresh/silva: r/silva-NEW/source/.made
	bin/christen silva-NEW

r/silva-NEW/source/.made: r/silva-NEW/source/silva_no_sequences.fasta
	python util/origin_info.py \
	  `cat r/silva-NEW/work/date` \
	  `cat r/silva-NEW/work/origin_url` \
	  >r/silva-NEW/source/origin_info.json
	ls -l r/silva-NEW/source
	touch $@

r/silva-NEW/source/silva_no_sequences.fasta: r/silva-NEW/work/download.gz
	gunzip -c r/silva-NEW/work/download.gz | \
	  grep ">.*;" > r/silva-NEW/source/silva_no_sequences.fasta

# Get the fasta file (big; for release 128, it's 150M)

r/silva-NEW/work/download.tgz: r/silva-NEW
	@mkdir -p r/silva-NEW/work
	r=`bin/get silva-NEW version`; \
	bin/put silva-NEW origin_url \
	  	"$(SILVA_ARCHIVE)/release_$$r/Exports/SILVA_$$r_SSURef_Nr99_tax_silva.fasta.gz"
	wget -q --output-file=r/silva-NEW/work/download.tgz.new \
	     `bin/get silva-NEW origin_url` && \
	mv -f r/silva-NEW/work/download.tgz.new r/silva-NEW/work/download.tgz

# Figure out which release we want, before downloading, by scanning the index.html file

r/silva-NEW:
	bin/new-version silva .tgz public
	bin/put silva-NEW ott_idspace "silva"
	wget -q -O - $(SILVA_ARCHIVE)/ | \
	  python import_scripts/silva/get_silva_release_info.py | \
	  (read r d; bin/put silva-NEW version $$r; \
		     bin/put silva-NEW date $$d)
	touch $@

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

# This takes a long time - reads every flat file in genbank
# Also - be sure to update RANGES in accessionFromGenbank.py, so you
# don't miss any genbank records!  Manual process now, could be 
# automated.

r/genbank-HEAD/resource/.made: r/genbank-HEAD/source/.made
	(cd r/genbank-HEAD && rm -f resource && ln -s source resource)

r/genbank-NEW/source/.made: r/genbank-NEW/work/Genbank.pickle \
	     		      import_scripts/genbank/makeaccessionid2taxonid.py
	@echo Making accessions.tsv
	@mkdir -p r/genbank-NEW/source
	python import_scripts/genbank/makeaccessionid2taxonid.py \
	       r/genbank-NEW/work/Genbank.pickle \
	       r/genbank-NEW/source/accessions.tsv
	touch $@

# Alert: sometimes we might want silva-NEW instead of silva-HEAD

r/genbank-NEW/work/Genbank.pickle: import_scripts/genbank/accessionFromGenbank.py \
	     r/silva-HEAD/source/silva_no_sequences.fasta \
	     r/genbank-NEW
	mkdir -p r/genbank-NEW/work
	@echo "*** Reading all of Genbank - this can take a while!"
	python import_scripts/genbank/accessionFromGenbank.py \
	       r/silva-HEAD/source/silva_no_sequences.fasta \
	       r/genbank-NEW/work/Genbank.pickle
	d=`python util/modification_date.py r/genbank-NEW/work/Genbank.pickle`; \
          bin/put genbank-NEW date $$d; \
          bin/put genbank-NEW version $$d

r/genbank-NEW:
	bin/new-version genbank .tgz pd

# --- Source: Index Fungorum in Open Tree form

refresh/fung: r/fung-NEW/source/.made
	bin/christen fung-NEW

r/fung-HEAD/resource/.made: r/fung-HEAD/source/.made
	(cd r/fung-HEAD; rm resource; ln -sf source resource)

r/fung-NEW/source/.made: r/fung-1/resource/.made r/fung-3/resource/.made r/fung-4/resource/.made
	@mkdir r/fung-NEW/source
	python import_scripts/fung/cobble_fung.py
	rm -rf r/fung-NEW/source
	mv hackedfung r/fung-NEW/source
	cp -p r/fung-4/resource/synonyms.tsv resource/fung-NEW
	touch $@

r/fung-NEW:
	bin/new-version fung .tgz public
	bin/put ott-NEW ott_idspace if
	(cd r/fung-NEW; rm resource; ln -sf source resource)

# How fung-9 was created:
# import_scripts/fung/cobble_fung.py 
#   which reads fung-4, fung-2, fung-1
#
# The earlier versions were created using various versions of the
# feed/if.py (process_fungorum.py) script, operating on various files
# from Paul Kirk as unput.

# --- Source: WoRMS in Open Tree form

# Make resource (i.e. taxonomy) from source (digest of WoRMS API traversal)

r/worms-HEAD/resource/.made: import_scripts/worms/process_worms.py r/worms-HEAD/source/.made
	python import_scripts/worms/process_worms.py r/worms-HEAD/source/digest r/worms-HEAD/resource
	touch $@

refresh/worms: r/worms-NEW/source/.made
	bin/christen worms-NEW

r/worms-NEW/source/.made: import_scripts/worms/fetch_worms.py r/worms-NEW
	mkdir -p r/worms-NEW/work r/worms-NEW/source/digest
	time python import_scripts/worms/fetch_worms.py --queue r/worms-NEW/work/q.q \
	       --out r/worms-NEW/source/digest --chunks 5000 --chunksize 500
	touch $@

r/worms-NEW:
	bin/new-version worms .tgz public
	bin/put ott-NEW ott_idspace worms
	(cd r/worms-NEW; rm -f resource; ln -sf source resource)

# --- Source: NCBI Taxonomy

# Build resource from source.
# Formerly (version 1.0), where we now have /dev/null, we had
# ../data/ncbi/ncbi.taxonomy.homonym.ids.MANUAL_KEEP

r/ncbi-HEAD/resource/.made: r/ncbi-HEAD/source/.made import_scripts/ncbi/process_ncbi_taxonomy.py
	@rm -rf r/ncbi-HEAD/resource.new
	@mkdir -p r/ncbi-HEAD/resource.new
	python import_scripts/ncbi/process_ncbi_taxonomy.py F r/ncbi-HEAD/source \
            /dev/null r/ncbi-HEAD/resource.new $(NCBI_ORIGIN_URL)
	rm -rf `dirname $@`
	mv -f r/ncbi-HEAD/resource.new `dirname $@`
	touch $@

# Refresh source from web.  Source depends on archive.
# Override default pattern rule (archive instead of source).

refresh/ncbi: r/ncbi-NEW/source/.made
	bin/christen ncbi-NEW

r/ncbi-NEW/source/.made: r/ncbi-NEW/archive/.made
	bin/unpack-archive ncbi-NEW
	d=`python util/modification_date.py r/ncbi-NEW/source/names.dmp`; \
          bin/put ncbi-NEW date $$d; \
          bin/put ncbi-NEW version $$d

r/ncbi-NEW/archive/.made: r/ncbi-NEW
	@mkdir -p r/ncbi-NEW/archive
	bin/put ncbi-NEW origin_url $(NCBI_ORIGIN_URL)
	wget -q --output-document=r/ncbi-NEW/archive/archive.tgz $(NCBI_ORIGIN_URL)
	bin/put ncbi-NEW bytes `wc -c r/ncbi-NEW/archive/archive.tgz | (read c d && echo $$c)`
	touch $@

r/ncbi-NEW:
	bin/new-version ncbi .tgz pd
	bin/put ncbi-NEW ott_idspace ncbi

# --- Source: GBIF

# Formerly, where it says /dev/null, we had ../data/gbif/ignore.txt

r/gbif-HEAD/resource/.made: r/gbif-HEAD/work/projection.tsv \
			import_scripts/gbif/process_gbif_taxonomy.py
	@mkdir -p `dirname $@`
	@mkdir -p r/gbif-HEAD/resource.new
	python import_scripts/gbif/process_gbif_taxonomy.py \
	       r/gbif-HEAD/work/projection.tsv \
	       r/gbif-HEAD/resource.new
	rm -rf r/gbif-HEAD/resource
	mv r/gbif-HEAD/resource.new r/gbif-HEAD/resource
	touch $@

r/gbif-HEAD/work/projection.tsv: r/gbif-HEAD/source/.made \
			     import_scripts/gbif/project_2016.py
	@mkdir -p `dirname $@`
	python import_scripts/gbif/project_2016.py r/gbif-HEAD/source/taxon.txt $@.new
	mv $@.new $@

# Get a new GBIF from the web and store to r/gbif-HEAD/archive/archive.zip

# Was http://ecat-dev.gbif.org/repository/export/checklist1.zip
# Could be http://rs.gbif.org/datasets/backbone/backbone.zip
# 2016-05-17 purl.org is broken, cannot update this link
# GBIF_URL=http://purl.org/opentree/gbif-backbone-2013-07-02.zip

# should be very similar to IRMNG

refresh/gbif: r/gbif-NEW/source/.made
	bin/christen gbif-NEW

r/gbif-NEW/source/.made: r/gbif-NEW/archive/.made
	bin/unpack-archive gbif-NEW
	d=`python util/modification_date.py r/gbif-NEW/source/taxon.txt`; \
          bin/put gbif-NEW date $$d; \
          bin/put gbif-NEW version $$d

r/gbif-NEW/archive/.made: r/gbif-NEW
	@mkdir -p r/gbif-NEW/archive
	bin/put gbif-NEW origin_url $(GBIF_ORIGIN_URL)
	wget -q --output-document=r/gbif-NEW/archive/archive.zip "$(GBIF_ORIGIN_URL)"
	bin/put gbif-NEW bytes `wc -c r/gbif-NEW/archive/archive.zip | \
			         (read c d && echo $$c)`
	touch $@

r/gbif-NEW:
	bin/new-version gbif .zip public
	bin/put gbif-NEW ott_idspace gbif

# --- Source: IRMNG

r/irmng-HEAD/resource/.made: r/irmng-HEAD/source/.made \
			 import_scripts/irmng/process_irmng.py
	@mkdir -p `dirname $@`.new
	python import_scripts/irmng/process_irmng.py \
	   r/irmng-HEAD/source/IRMNG_DWC_20*.csv \
	   r/irmng-HEAD/source/IRMNG_DWC_SP_PROFILE_20*.csv \
	   r/irmng-HEAD/resource.new/taxonomy.tsv \
	   r/irmng-HEAD/resource.new/synonyms.tsv
	rm -rf r/irmng-HEAD/resource
	mv r/irmng-HEAD/resource.new r/irmng-HEAD/resource
	touch $@

# Build IRMNG from Tony's .csv files
#  should be mostly the same as for GBIF
# Refresh makes archive instead of source

refresh/irmng: r/irmng-NEW/source/.made
	bin/christen irmng-NEW

r/irmng-NEW/source/.made: r/irmng-NEW/archive/.made
	bin/unpack-archive irmng-NEW
	d=`python util/modification_date.py r/irmng-NEW/source/IRMNG_DWC.csv`; \
          bin/put gbif-NEW date $$d; \
          bin/put gbif-NEW version $$d
	touch $@

r/irmng-NEW/archive/.made: r/irmng-NEW
	@mkdir -p r/irmng-NEW/archive
	bin/put irmng-NEW origin_url $(IRMNG_ORIGIN_URL)
	wget -q --output-document=r/irmng-NEW/archive/archive.zip "$(IRMNG_ORIGIN_URL)"
	bin/put irmng-NEW bytes `wc -c r/gbif-NEW/archive/archive.zip | (read c d && echo $$c)`
	touch $@

r/irmng-NEW:
	bin/new-version irmng .zip public
	bin/put ott-NEW ott_idspace irmng

# --- Source: Open Tree curated amendments

# Note, no dependence on /source/.
# A dependence on /source/ would attempt archive retrieval, which would fail.

r/amendments-HEAD/resource/.made: r/amendments-HEAD/.issue
	mkdir -p `dirname $@`
	n=`bin/get amendments-PREVIOUS name` && bin/put amendments-HEAD version $${n:11}
	(cd tmp/amendments/amendments-1 && \
	 git fetch && \
	 git checkout `bin/get amendments-HEAD version`)
	cp -pr tmp/amendments/amendments-1/amendments \
	       r/amendments-HEAD/resource/amendments-1/
	touch $@

# Recursive make is not generally recommended, but don't see what else
# to do in this case.  We don't want HEAD to depend (in the Makefile
# sense) on PREVIOUS, since otherwise a demand for HEAD would force
# a retrieval of PREVIOUS, which is not always what we want.

r/amendments-HEAD/.issue:
	$(MAKE) r/amendments-PREVIOUS/.is-previous
	bin/set-head amendments amendments-PREVIOUS easy
	touch $@

# New version: fetch from github

refresh/amendments: r/amendments-NEW/source/.made
	bin/christen amendments-NEW

r/amendments-NEW/source/.made: tmp/amendments/amendments-1 r/amendments-NEW
	@mkdir -p r/amendments-NEW/source/amendments-1
	(cd tmp/amendments/amendments-1 && \
	 git checkout master && \
	 git pull)
	bin/put amendments-NEW version `(cd tmp/amendments/amendments-1; git log -n 1) | \
	  head -1 | sed -e 's/commit //'`
	cp -pr tmp/amendments/amendments-1/amendments \
	       r/amendments-NEW/source/amendments-1/
	touch $@

# Local clone
tmp/amendments/amendments-1:
	@mkdir -p tmp/amendments
	(cd tmp/amendments; git clone $(AMENDMENTS_ORIGIN_URL))

r/amendments-NEW:
	bin/new-version amendments .tgz cc0

# --- Source: OTT id list compiled from all previous OTT versions

r/idlist-HEAD/resource/.made: r/idlist-HEAD/source/.made
	(cd r/idlist-HEAD && rm -f resource && ln -s source resource)


# When we build OTT 3.1, we need idlist-3.0, but we have
# idlist-PREVIOUS which is idlist-2.10, which has ids through OTT 2.9.

# So, to make the id list needed to build OTT 3.1, we extend
# idlist-2.10 with new identifiers added in OTT 3.0 (ott-HEAD), obtaining
# idlist-3.0, which is then used as input in build OTT 3.1.
# Whew!

refresh/idlist: r/idlist-NEW/source/.made
	bin/christen idlist-NEW

r/idlist-NEW/source/.made: r/idlist-PREVIOUS/source/.made \
			   r/ott-HEAD/source/.made \
			   r/idlist-NEW
	@rm -rf r/idlist-NEW/source
	@mkdir -p r/idlist-NEW/source
	cp -pr r/idlist-PREVIOUS/source/regs r/idlist-NEW/source/
	bin/put idlist-NEW version `bin/get ott-HEAD version`
	python import_scripts/idlist/extend_idlist.py \
	       r/idlist-PREVIOUS/source \
	       r/ott-HEAD/source \
	       `bin/get ott-HEAD name` \
	       r/ott-HEAD/properties.json \
	       r/idlist-NEW/source
	touch $@

r/idlist-NEW:
	bin/new-version idlist .tgz cc0

# ----- TBD: wikidata.py (from which we get EOL ids)

# Get wikidata dump from https://dumps.wikimedia.org/wikidatawiki/entities/wikidata-DDDDDDDD-all.json.gz
# e.g. DDDDDDDD = 20170424
# The .bz2 would be better (only 66% the size).

r/wikidata-NEW/source/.made: import_scripts/wikidata/get_wikidata.py
	@echo wikidata/EOL NYI

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

SSH_PATH_PREFIX?=files.opentreeoflife.org:files.opentreeoflife.org

# Synthetic tree OTT id list (this rule assumes you're part of the
# Open Tree project and can use scp; could be modified to use
# curl. this doesn't matter much since the list is checked into the
# repo.)
# To refresh, remove the target, then make it
ids_in_synthesis.tsv:
	rm -rf synth-nexson-tmp
	mkdir -p synth-nexson-tmp
	scp -p "$(SSH_PATH_PREFIX)/synthesis/current/output/phylo_snapshot/*@*.json" synth-nexson-tmp/
	time bin/jython util/ids_in_synthesis.py --dir synth-nexson-tmp --outfile $@.new
	mv $@.new $@

# ----- Products

# For publishing OTT drafts or releases.
# File names beginning with # are emacs lock links.
 
# Maybe this rule isn't needed now that we have pack/ and store/ targets?

tarball: r/ott-HEAD/archive/.made

# Then, something like
# scp -p -i ~/.ssh/opentree/opentree.pem tarballs/ott2.9draft3.tgz \
#   opentree@ot10.opentreeoflife.org:files.opentreeoflife.org/ott/ott2.9/

# This file is big
r/ott-NEW/work/differences.tsv: r/ott-HEAD/resource/.made r/ott-NEW/source/debug/transcript.out
	$(SMASH) --diff r/ott-HEAD/resource/ r/ott-NEW/source/ $@.new
	mv $@.new $@
	wc $@

# OTUs only
r/ott-NEW/source/debug/otu_differences.tsv: r/ott-NEW/work/differences.tsv
	$(SMASH) --join ids_that_are_otus.tsv r/ott-NEW/work/differences.tsv >$@.new
	mv $@.new $@
	wc $@

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

# These are run by the OTT build script so this is usually redundant
inclusion-tests: inclusions.csv bin/jython
	bin/jython util/check_inclusions.py inclusions.csv r/ott-NEW/source/

# -----------------------------------------------------------------------------
# Asterales test system ('make test')

TAXON=Asterales

# t/tax/prev/taxonomy.tsv: r/ott-HEAD/resource/taxonomy.tsv   - correct expensive
t/tax/prev_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) r/ott-HEAD/resource/ --select2 $(TAXON) --out t/tax/prev_aster/

# dependency on r/ncbi-HEAD/resource/taxonomy.tsv - correct expensive
t/tax/ncbi_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) r/ncbi-HEAD/resource/ --select2 $(TAXON) --out t/tax/ncbi_aster/

# dependency on GBIF taxonomy.tsv - correct but expensive
t/tax/gbif_aster/taxonomy.tsv: 
	@mkdir -p `dirname $@`
	$(SMASH) r/gbif-HEAD/resource/ --select2 $(TAXON) --out t/tax/gbif_aster/

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
	rm -rf r/*/resource r/*/source r/*/work
	rm -rf config.mk
	rm -rf r/*-NEW
	rm r/*-HEAD r/*-PREVIOUS
	rm -rf tmp *.tmp properties
	rm -rf t/amendments t/tax/aster

distclean: clean
	rm -f lib/*
	rm -rf r tmp raw
