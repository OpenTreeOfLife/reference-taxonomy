
refresh-synth: labelled_supertree.tre
	ln -sf labelled_supertree.tre synth.tre

labelled_supertree.tre:
	curl http://files.opentreeoflife.org/synthesis/current/output/labelled_supertree/labelled_supertree.tre >labelled_supertree.tre.new
	mv labelled_supertree.tre.new labelled_supertree.tre

asterales.tre:
	../bin/jython ../util/select_from_taxonomy.py labelled_supertree.tre ott1042120 asterales.new.tre
	mv asterales.new.tre asterales.tre

clean:
	rm labelled_supertree.tre
