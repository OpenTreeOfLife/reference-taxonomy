
"""
curl -X POST http://api.opentreeoflife.org/v2/studies/find_studies \
  -H "content-type:application/json" -d '{}'

http://api.opentreeoflife.org/v1/study/pg_10/otus

real	24m30.823s
user	0m13.776s
sys 	0m2.502s

"""

import requests, json, sys

tab = {}

def doit():

    outname = sys.argv[1]
    outfile = open(outname, 'w')

    r = requests.post("http://api.opentreeoflife.org/v2/studies/find_studies",
                      data=json.dumps({}),
                      headers={'Content-type': 'application/json'})

    r.raise_for_status()

    #   "matched_studies" : [ {
    #     "ot:studyId" : "pg_1439"
    #    }, ... ]

    result = r.json()

    i = 0

    for clod in result[u'matched_studies']:
        i = i + 1
        #if i > 8: continue
        studyid = clod[u'ot:studyId']
        print studyid

        s = requests.get('http://api.opentreeoflife.org/phylesystem/v1/study/%s/otus'%(studyid))

        s.raise_for_status()

        # {"otus137":
        #  {"otuById":
        #   {"otu186984": {"^ot:treebaseOTUId": "Tl406927", "^ot:ottId": 500022, 
        #     "^ot:originalLabel": "Pimelodendron griffithianum", 
        #     "^ot:ottTaxonName": "Pimelodendron griffithianum"},

        sdict = s.json()

        for value in sdict.itervalues():
            for clump in value[u'otuById'].itervalues():
                if u'^ot:ottId' in clump:
                    ottid = clump[u'^ot:ottId']
                    if not ottid in tab:
                        tab[ottid] = [studyid]
                    elif not studyid in tab[ottid]:
                        tab[ottid].append(studyid)

    for ottid in sorted(tab.keys()):
        studyids = tab[ottid]
        outfile.write('%s\t'%(ottid))
        firstp = True
        for studyid in studyids:
            if not firstp:
                outfile.write(',')
            outfile.write(studyid)
            firstp = False
        outfile.write('\n')

doit()
