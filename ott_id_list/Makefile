
JYTHON=../bin/jython

all: make_ott_id_list.py
	time $(JYTHON) make_ott_id_list.py ../../files.opentreeoflife.org/ott ott_id_list

test: make_ott_id_list.py
	$(JYTHON) make_ott_id_list.py test_otts test_ott_id_list

indexes: make_indexes.py
	python make_indexes.py ott_id_list

# sorts 160M in 3.2 seconds!!
sort:
	time cat `ls ott_id_list/ids-*.csv` | sort -t , -k 1 -n -s > ott_id_list.csv

clean:
	rm -rf ott_id_list test_ott_id_list
