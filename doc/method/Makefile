#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

REF_HOME=../..
JYTHON=$(REF_HOME)/bin/jython

all: draft.html

# use --html4tags flag?

draft.html: out/draft.html
	ln -sf $< $@

out/draft.html: out/draft.md lib/Markdown.pl
	@mkdir -p out
	bin/markdown $< >$@.new
	mv $@.new $@

MARKDOWNS=abstract.md introduction.md method-intro.md \
  out/sources_table.md method-sources.md method-details.md \
  out/results_tables.md \
  results.md discussion.md conclusions.md

out/draft.md: $(MARKDOWNS)
	@mkdir -p out
	cat $^ >$@

out/sources_table.md: sources_table.py
	@mkdir -p out
	python $< >$@.new
	mv $@.new $@

# Need to manually insert the output into results.py

out/taxonomy_metrics.md: taxonomy_metrics.py cache/taxonomy_metrics.json
	$(JYTHON) $< cache/taxonomy_metrics.json > $@.new
	mv $@.new $@

out/results_tables.md: $(REF_HOME)/util/reason_report.py \
		       cache/alignment_summary.json \
		       cache/merge_summary.json \
		       cache/taxonomy_metrics.json
	python $< cache/ cache/taxonomy_metrics.json > $@.new
	mv $@.new $@

out/ruggiero/taxonomy.tsv: ruggiero/ruggiero.csv
	python ruggiero/import_ruggiero.py $< out/ruggiero.new
	mv out/ruggiero.new out/ruggiero

out/outline.html: out/outline.md
	bin/markdown $< >$@.new
	mv $@.new $@

out/outline.md: outline.md
	@mkdir -p out
	echo 'Draft is [here](draft.html)' >$@
	cat $^ >>$@

open: out/draft.html out/outline.html
	open $<

clean:
	rm -rf out/*
	rm draft.html

lib/Markdown.pl:
	mkdir -p lib
	wget "http://daringfireball.net/projects/downloads/Markdown_1.0.1.zip"
	unzip Markdown_1.0.1.zip
	mv Markdown_1.0.1/Markdown.pl $@
	rm -r Markdown_1.0.1 Markdown_1.0.1.zip

# Use this to test that results.py works before siccing it on OTT
test-results: results.py
	$(JYTHON) $< $(REF_HOME)/t/tax/aster/



#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/frameworks-130b94ff796a9660d814b59665547ebaf99cc439323c908f41c6ff46e4255c8e.css" integrity="sha256-EwuU/3lqlmDYFLWWZVR+uvmcxDkyPJCPQcb/RuQlXI4=" media="all" rel="stylesheet" />' \
#	  >draft.html
#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/github-907704789dc9e0c1cd78c2f3adfc91e42ed23a0a97b2790c4171d9d8959f7cdc.css" integrity="sha256-kHcEeJ3J4MHNeMLzrfyR5C7SOgqXsnkMQXHZ2JWffNw=" media="all" rel="stylesheet" />' \
#	  >>draft.html


# This overwrites whatever is in cache.  Cache is checked in to the
# repository so that people can make the manuscript .HTML file without
# having a copy of the taxonomy on hand.  But it's necessary to do
# 'make refresh' after a taxonomy build.

refresh: taxonomy_metrics.py \
	     $(REF_HOME)/tax/ott/taxonomy.tsv \
	     out/ruggiero/taxonomy.tsv
	$(JYTHON) $< $(REF_HOME)/tax/ott/ $(REF_HOME)/tax/skel/ $@.new
	mv $@.new $@
	cp -p $(REF_HOME)/tax/ott/alignment_summary.json $@
	cp -p $(REF_HOME)/tax/ott/merge_summary.json $@

