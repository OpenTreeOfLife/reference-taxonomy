
import sys, os, json

tax = sys.argv[1]

with open(os.path.join(tax, 'about.json'), 'r') as infile:
    blob = json.load(infile)
    print '<p>Stub - to be filled out later</p>'
    print '<p>Version: ', blob["version"], '</p>'
