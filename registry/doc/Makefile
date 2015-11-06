
TAXONOMY_HOME=../..

svg/example.svg: drawtree.py
	mkdir -p svg
	java -cp $(TAXONOMY_HOME):$(TAXONOMY_HOME)/lib/* org.python.util.jython drawtree.py 

# Assume .ssh/config defines aliase 'files'
export: scenarios.html
	scp -pr scenarios.html svg/*.svg files:files.opentreeoflife.org/registry-svg/
