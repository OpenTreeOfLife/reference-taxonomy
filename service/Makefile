

labelled_supertree.tre:
	curl http://files.opentreeoflife.org/synthesis/opentree7.0/opentree7.0_tree/labelled_supertree/labelled_supertree.tre >labelled_supertree.tre.new
	mv labelled_supertree.tre.new labelled_supertree.tre

asterales.tre:
	../bin/jython ../util/select_from_taxonomy.py labelled_supertree.tre ott1042120 asterales.new.tre
	mv asterales.new.tre asterales.tre
