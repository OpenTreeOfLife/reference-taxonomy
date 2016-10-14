
import sys, os, json, csv, re, codecs

name_re = re.compile('([A-Z][a-z]+ [a-z]+)|([A-Z][a-z]+)')

all_names = {}
id_names = {}
study_count = 0

unmapped_studies = []

def process(path):
    writer = csv.writer(sys.stdout)
    if os.path.isdir(path):
        for name in os.listdir(path):
            process(os.path.join(path, name))
    else:
        process_study(path, writer)

# TBD: count how many labels are mapped, not just names

def process_study(path, writer):
    global study_count
    otus = []
    with open(path, 'r') as infile:
        blob = json.load(infile)
        if not "nexml" in blob: return
        nexson = blob["nexml"]
        study = nexson["^ot:studyId"]
        have_id = 0
        for block in nexson["otusById"].itervalues():
            for otu in block["otuById"].itervalues():
                if "^ot:ottId" in otu:
                    have_id += 1
                otus.append(otu)

        if have_id * 4 > len(otus):
            print >>sys.stderr, study, have_id, len(otus)
            for otu in otus:
                id = otu.get("^ot:ottId", 'NO ID')
                label = otu["^ot:originalLabel"]
                m = name_re.search(label)
                if m == None:
                    n = 'NO NAME'
                else:
                    n = m.group()
                writer.writerow([n.encode('utf8'), label.encode('utf8'), id, study])
                all_names[n] = True
                if "^ot:ottId" in otu:
                    id_names[n] = True
        else:
            unmapped_studies.append(study)

process(sys.argv[1])

print >>sys.stderr, 'studies:', study_count, 'poorly-mapped:', len(unmapped_studies)

print >>sys.stderr, 'mapped names:', len(id_names), 'name:', len(all_names)

