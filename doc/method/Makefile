#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

REF_HOME=../..
JYTHON=$(REF_HOME)/bin/jython

all: out/draft.html supplementary/taxonomy_stats.csv

# use --html4tags flag?

out/draft.html: out/draft.md lib/Markdown.pl
	@mkdir -p out
	bin/markdown $< >$@.new
	mv $@.new $@

MARKDOWNS=abstract.md introduction.md method-intro.md \
  out/sources_table.md method-sources.md method-details.md \
  results.md discussion.md conclusions.md

out/draft.md: $(MARKDOWNS)
	@mkdir -p out
	cat $^ >$@

out/sources_table.md: sources_table.py
	@mkdir -p out
	python $< >$@.new
	mv $@.new $@

# Need to manually insert the output into results.py

out/outline.html: out/outline.md
	bin/markdown $< >$@.new
	mv $@.new $@

out/outline.md: outline.md
	@mkdir -p out
	echo 'Draft is [here](draft.html)' >$@
	cat $^ >>$@

supp: supplementary/taxonomy_stats.csv

supplementary/taxonomy_stats.csv: make_supplementary.py \
		       cache/alignment_summary.json \
		       cache/merge_summary.json \
		       cache/taxonomy_summary.json \
		       cache/contributions_summary.csv
	mkdir -p supplementary
	python $< cache supplementary

lib/Markdown.pl:
	mkdir -p lib
	wget "http://daringfireball.net/projects/downloads/Markdown_1.0.1.zip"
	unzip Markdown_1.0.1.zip
	mv Markdown_1.0.1/Markdown.pl $@
	rm -r Markdown_1.0.1 Markdown_1.0.1.zip


#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/frameworks-130b94ff796a9660d814b59665547ebaf99cc439323c908f41c6ff46e4255c8e.css" integrity="sha256-EwuU/3lqlmDYFLWWZVR+uvmcxDkyPJCPQcb/RuQlXI4=" media="all" rel="stylesheet" />' \
#	  >draft.html
#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/github-907704789dc9e0c1cd78c2f3adfc91e42ed23a0a97b2790c4171d9d8959f7cdc.css" integrity="sha256-kHcEeJ3J4MHNeMLzrfyR5C7SOgqXsnkMQXHZ2JWffNw=" media="all" rel="stylesheet" />' \
#	  >>draft.html


# This overwrites whatever is in cache.  Cache is checked in to the
# repository so that people can make the manuscript .HTML file without
# having a copy of the taxonomy on hand.  But it's necessary to do
# 'make refresh' after a taxonomy build.

refresh: taxonomy_metrics.py
	cp -p $(REF_HOME)/tax/ott/alignment_summary.json cache/
	cp -p $(REF_HOME)/tax/ott/merge_summary.json cache/
	$(JYTHON) $< $(REF_HOME)/tax/ott/ $(REF_HOME)/tax/separation/ \
	   taxonomy_summary.json.new \
	   contributions_summary.csv.new
	mv taxonomy_summary.json.new cache/taxonomy_summary.json
	mv contributions_summary.csv.new cache/contributions_summary.csv

# How well does the taxonomy cover...

coverage:
	$(REF_HOME)/bin/jython measure_coverage.py \
	   $(REF_HOME)/tax/ott/ $(REF_HOME)/ids_that_are_otus.tsv \
	   | tee out/coverage.txt

# -----------------------------------------------------------------------------
# Graveyard

# - no longer used. delete soon -
# there are pathnames in the code, should match prereq list
out/results_tables.md: make_table_markdown.py \
		       cache/alignment_summary.json \
		       cache/merge_summary.json \
		       cache/taxonomy_summary.json \
		       cache/contributions_summary.csv
	python $< cache > $@.new
	mv $@.new $@

out/ruggiero/taxonomy.tsv: ruggiero/ruggiero.csv
	python ruggiero/import_ruggiero.py $< out/ruggiero.new
	mv out/ruggiero.new out/ruggiero

out/taxonomy_summary.md: taxonomy_metrics.py cache/taxonomy_summary.json
	$(JYTHON) $< cache/taxonomy_summary.json > $@.new
	mv $@.new $@

# Use this to test that results.py works before siccing it on OTT
# NEEDS TO BE FIXED
test-results: make_table_markdown.py
	$(JYTHON) $< $(REF_HOME)/t/tax/aster/

# -----------------------------------------------------------------------------

open: out/draft.html out/outline.html
	open $<

clean:
	rm -rf out/*
	rm draft.html

