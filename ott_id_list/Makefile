
JYTHON=../bin/jython

all: ott_id_list.csv by_qid.csv

ott_id_list: make_ott_id_list.py
	time $(JYTHON) make_ott_id_list.py ../../files.opentreeoflife.org/ott ott_id_list
	touch ott_id_list

test: make_ott_id_list.py
	$(JYTHON) make_ott_id_list.py test_otts test_ott_id_list

indexes: by_qid.csv

by_qid.csv: make_indexes.py ott_id_list
	python make_indexes.py ott_id_list

# sorts 160M in 3.2 seconds!!
ott_id_list.csv: ott_id_list
	time cat `ls ott_id_list/ids-*.csv` | sort -t , -k 1 -n -s > ott_id_list.csv

clean:
	rm -rf ott_id_list test_ott_id_list ott_id_list.csv by_*.csv
