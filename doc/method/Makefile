#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

all: draft.html

# use --html4tags flag?

draft.html: out/draft.html
	ln -sf $< $@

out/draft.html: out/draft.md lib/Markdown.pl
	@mkdir -p out
	bin/markdown $< >$@.new
	mv $@.new $@

MARKDOWNS=abstract.md introduction.md method-intro.md method-sources.md \
  out/sources_table.md method-details.md \
  results.md discussion.md conclusions.md

out/draft.md: $(MARKDOWNS)
	@mkdir -p out
	echo 'See also [proposed outline](outline.html)' >$@
	cat $^ >>$@

out/sources_table.md: sources_table.py
	@mkdir -p out
	python $< >$@.new
	mv $@.new $@

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

REF_HOME=../../../../repo/reference-taxonomy
JYTHON=$(REF_HOME)/bin/jython

results: results.py
	$(JYTHON) $< $(REF_HOME)/tax/ott/

# Use this to test that results.py works before siccing it on OTT
test-results: results.py
	$(JYTHON) $< $(REF_HOME)/t/tax/aster/



#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/frameworks-130b94ff796a9660d814b59665547ebaf99cc439323c908f41c6ff46e4255c8e.css" integrity="sha256-EwuU/3lqlmDYFLWWZVR+uvmcxDkyPJCPQcb/RuQlXI4=" media="all" rel="stylesheet" />' \
#	  >draft.html
#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/github-907704789dc9e0c1cd78c2f3adfc91e42ed23a0a97b2790c4171d9d8959f7cdc.css" integrity="sha256-kHcEeJ3J4MHNeMLzrfyR5C7SOgqXsnkMQXHZ2JWffNw=" media="all" rel="stylesheet" />' \
#	  >>draft.html
