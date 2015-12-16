compile:
	@(cd ..; make compile)

ott2.9: ott2.9/taxonomy.tsv
ott2.9/taxonomy.tsv:
	wget http://files.opentreeoflife.org/ott/ott2.9/ott2.9.tgz
	tar xzvf ott2.9.tgz
	mv ott ott2.9

draftversion4.tre:
	scp -p devapi:downloads/Life_v4.0_ott2.9draft12_synth_24Oct2015.tre.tgz draftversion4.tre.gz
	tar xzf draftversion4.tre.gz
	mv Life_v4.0_ott2.9draft12_synth_24Oct2015.tre draftversion4.tre

