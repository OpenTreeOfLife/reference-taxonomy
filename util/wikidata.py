#!/usr/bin/env python3
# -*- coding: utf-8 -*-

'''
Contributed by: Yan Wong

Call as:

    OTT_2_Wikidata.py taxonomy.tsv wikidata-20151005-all.json.gz > map_file.txt

A taxonomy.tsv file can be obtained from http://files.opentreeoflife.org/ott/
Wikidata JSON dumps are available from https://dumps.wikimedia.org/wikidatawiki/entities/

If 3rd/4th etc arguments are given, they specify sitelinks to output, for example,
to output the title of the equivalent page on en.wikipedia, you can do

    WikiDataMap.py taxonomy.tsv wikidata-20151005-all.json.gz enwiki > map_file.txt

Note that this is usually not necessary. Unless you want to produce analysis using sitelinks for all the 
taxa on wikidata, it is better to use the various wikidata APIs to obtain titles/site links as required,
e.g., from wikidata ID = Q36611

https://www.wikidata.org/wiki/Special:EntityData/Q36611.json
 or 
https://www.wikidata.org/w/api.php?action=wbgetentities&sitefilter=enwiki&ids=Q36611&props=sitelinks&format=json

Using the API to obtain URLs avoids the problem of links becoming stale. Outputting titles is only useful if you
want to do further processing of the entire set of links (e.g. to look up page visits for every taxon)

This script parses the OTT taxonomy.tsv file (>=2.9, which includes WoRMS data and reduces duplication of ncbi ids 
(see https://github.com/OpenTreeOfLife/reference-taxonomy/issues/167)). It looks for the source IDs for each taxon, 
which it stores in a set of arrays, as in

NCBI[NCBI_ID]=OTTID

It then streams through the wikidata dump file, looking initially for items ("type":"item") that contain the string
Q16521, since all taxon items have property P31 ("instance of") set to Q16521 ("taxon").

Wikidata dump has one line per object or property, in the following format e.g. for Gorilla (Q36611)
(here simplified from the output obtained via `gzcat wikidata-20151005-all.json.gz | grep -A 200 '"id": "Q36611"'`)

{"type":"item","id":"Q36611","labels":{"eu":{"language":"eu","value":"Gorila"},"pl":{"language":"pl","value":"goryl"},"en":{"language":"en","value":"Gorilla"}...},"claims":{"P31":[{
"mainsnak": {"datatype": "wikibase-item","datavalue":{"type": "wikibase-entityid","value":{"entity-type":"item","numeric-id":16521}},"property":"P31","snaktype":"value"},"rank":"normal","type":"statement"}],"P685":[{"mainsnak": {"datatype": "string","datavalue":{"type":"string","value":"9592"},"property": "P685","snaktype":"value"},"rank": "normal"}],...},"sitelinks":{"arwiki":{"badges":[],"site":"arwiki","title":"\u063a\u0648\u0631\u064a\u0644\u0627"},"enwiki": {"badges":[],"site":"enwiki","title": "Gorilla"},...},...}

For any matching line, it parses the JSON, checks that indeed claim P31[0]['mainsnak']['datavalue']['value']['numeric-id'] == 16521 
then looks up identifiers for the set of databases listed in WD_to_OTT (currently ncbi, gbif, worms, & if), and matches
them against the IDs obtained from the taxonomy.tsv file.

The output is tab-delimited set of lines with OTT_id wikidataID EOLid_if_exists
Many lines in this file may be repeated (potentially one for each source for each taxon). Duplicate lines can be removed with
cat map_file.txt | sort -g | uniq

'''
import json
import sys
import csv
import re
import resource
import fileinput

def warn(*objs):
    print(*objs, file=sys.stderr)

def memory_usage_resource():
    import resource
    rusage_denom = 1024.
    if sys.platform == 'darwin':
        # ... it seems that in OSX the output is different units ...
        rusage_denom = rusage_denom * rusage_denom
    mem = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / rusage_denom
    return mem

wikilinks = sys.argv[3:]
output_header = ["OTTid","wikidataid","EOLid"] + [x+'_title' for x in wikilinks]
WD_to_OTT = {'P685':'ncbi','P846':'gbif','P850':'worms','P1391':'if'} #maps the name in taxonomy.tsv to the property ID in wikidata (e.g. 'ncbi' in OTT, P685 in wikidata. Note that wikidata does not have 'silva' and 'irmng' types)
EOLid_prop = 'P830'
#this dictionary contains, e.g. 'ncbi':{NCBI_ID1:OTT_ID1,NCBI_ID2:OTT_ID2,...}
OTTids = {WD_to_OTT[key]:{} for key in WD_to_OTT}

#hack for NCBI_via_silva (see https://groups.google.com/d/msg/opentreeoflife/L2x3Ond16c4/CVp6msiiCgAJ)
silva_regexp = re.compile(r'ncbi:(\d+),silva:([^,$]+)')
silva_sub = r'ncbi_silva:\1'  #can chop off the silva ID since it is not used in wikidata
OTTids['ncbi_silva'] = {}

try:
    OTT = open(sys.argv[1])
    WDF = fileinput.input(sys.argv[2],openhook=fileinput.hook_compressed)
except IndexError:
    sys.exit('Provide the name of an OTT taxonomy.tsv file as the first argument and the name of a wikidata dump file (could be gzipped) as the second argument')
except IOError as e:
    sys.exit("I/O error({0}): {1}".format(e.errno, e.strerror))



ottreader = csv.reader(OTT, delimiter='\t')
header = next(ottreader)
h = {v:k for k,v in enumerate(header)}
for fields in ottreader:
    if (ottreader.line_num % 100000 == 0):
        warn("{} OTT taxa read: mem usage {} Mb".format(ottreader.line_num, memory_usage_resource()))
    OTTid = int(fields[h['uid']])
    sources = fields[h['sourceinfo']]
    sources = silva_regexp.sub(silva_sub,sources)
    for source in sources.split(','):
        name, val = source.split(':',1)
        if name in OTTids:
            if val in OTTids[name]:
                warn("Looking at taxon {} ({}) and found {}: but ignoring because '{}' already has {} defined as OTT number(s) {}".format(OTTid,fields[h['name']],source,name,val,OTTids[name][val]))
            else:
                OTTids[name][val]= OTTid
OTT.close()
warn("Done")

def check_presence(json):
    try:
        EoLprop = item['claims'][EOLid_prop][0]['mainsnak']['datavalue']['value']
    except LookupError:
        EoLprop = ''
    titles = [json['sitelinks'][sitelink]['title'] if json.get('sitelinks') and json['sitelinks'].get(sitelink) else '' for sitelink in wikilinks]
        
    for wd_src, ott_src in WD_to_OTT.items():
        try:
            for idprop in json['claims'][wd_src]:
                id = idprop['mainsnak']['datavalue']['value']
                try:
                    print("\t".join([str(OTTids[ott_src][id]),json['id'],EoLprop] + titles))
                except LookupError:
                    if (ott_src=='ncbi'):
                        try:
                            print("\t".join([str(OTTids['ncbi_silva'][id]),json['id'],EoLprop] + titles))
                        except LookupError:
                            pass;
        except LookupError:
            pass

print("\t".join(output_header))
for line in WDF:
    #this file is in byte form, so must match byte strings
    if line.startswith(b'{"type":"item'):
        if b'"numeric-id":16521}' in line:
            #this could be an item with "P31":[{"mainsnak":{"snaktype":"value","property":"P31","datavalue":{"value":{"entity-type":"item","numeric-id":16521},
            item = json.loads(line.decode('UTF-8').rstrip().rstrip(","))
            try:
                for c in item['claims']['P31']:
                    if c['mainsnak']['datavalue']['value']['numeric-id'] == 16521:
                        check_presence(item)
            except LookupError:
                try:
                    name = "'" + item['labels']['en']['value'] + "'"
                except LookupError:
                    name = "no english name"
                warn("There might be a problem with wikidata item {} ({}), might be a taxon but cannot get taxon data from it".format(item['id'],name));
                
