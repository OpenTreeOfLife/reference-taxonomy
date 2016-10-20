
JYTHON=../bin/jython

all: make_ott_id_list.py
	time $(JYTHON) make_ott_id_list.py ../../files.opentreeoflife.org/ott ott_id_list

test: make_ott_id_list.py
	$(JYTHON) make_ott_id_list.py test_otts test_ott_id_list

clean:
	rm -rf ott_id_list test_ott_id_list
