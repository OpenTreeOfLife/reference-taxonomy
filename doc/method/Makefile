#  $^ = all prerequisites
#  $< = first prerequisite
#  $@ = file name of target

all: draft.html

draft.md: introduction.md sources.md method.md results-discussion.md
	cat $^ >draft.md

draft.html: draft.md
	md draft.md >draft.html

open: draft.html
	open draft.html

clean:
	rm draft.*



#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/frameworks-130b94ff796a9660d814b59665547ebaf99cc439323c908f41c6ff46e4255c8e.css" integrity="sha256-EwuU/3lqlmDYFLWWZVR+uvmcxDkyPJCPQcb/RuQlXI4=" media="all" rel="stylesheet" />' \
#	  >draft.html
#	echo '<link crossorigin="anonymous" href="https://assets-cdn.github.com/assets/github-907704789dc9e0c1cd78c2f3adfc91e42ed23a0a97b2790c4171d9d8959f7cdc.css" integrity="sha256-kHcEeJ3J4MHNeMLzrfyR5C7SOgqXsnkMQXHZ2JWffNw=" media="all" rel="stylesheet" />' \
#	  >>draft.html
