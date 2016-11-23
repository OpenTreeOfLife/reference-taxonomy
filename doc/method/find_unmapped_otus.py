
# For results section: analyze OTU coverage
# How many OTUs are mapped?
#   Of the ones that aren't - take a sample - why did curator fail to find a mapping?

# Input (command line argument): phylesystem-1 location
# Output (standard output): unmapped OTUs

# python find_unmapped_otus.py ~/a/ot/repo/phylesystem-1 >unmapped.csv
# gshuf -n 10 unmapped.csv


import sys, os, json, csv, re, codecs

# name_re = re.compile('([A-Z][a-z]+ [a-z]+)|([A-Z][a-z]+)')
# m = name_re.search(label)

study_count = 0
reject_count = 0
unmapped = []

total_otu_count = 0
total_mapped_count = 0

def process_all(path):
    process(path)
    print >>sys.stderr, 'studies:', study_count, 'rejected:', reject_count
    print >>sys.stderr, 'OTUs:', total_otu_count, 'mapped:', total_mapped_count

    # Unmapped OTUs, from which to sample
    writer = csv.writer(sys.stdout)
    for (study_id, label) in unmapped:
        writer.writerow([study_id, label.encode('utf8')])

def process(path):
    if os.path.isdir(path):
        for name in os.listdir(path):
            process(os.path.join(path, name))
    elif path.endswith('.json'):
        process_study(path)

def process_study(path):
    global study_count, reject_count, total_otu_count, total_mapped_count
    nexson = load_study(path)
    if nexson == None:
        # e.g. next_study_id.json
        print >>sys.stderr, ("ill-formed: %s" % path)
        return

    study_id = nexson["^ot:studyId"]

    label_to_ottid = get_label_to_ottid(nexson)
    mapped_count = count_mapped(label_to_ottid)
    count = len(label_to_ottid)

    if count == 0 or mapped_count == 0:
        reject_count += 1
        return

    if count < 3:
        print >>sys.stderr, ("rejected %s because only %s OTU(s)" %
                             (study_id, count))
        reject_count += 1
        return

    if mapped_count * 2 < count:
        print >>sys.stderr, ("rejected %s because only %s OTUs mapped out of %s" %
                             (study_id, mapped_count, count))
        reject_count += 1
        return

    study_count += 1
    total_otu_count += count
    total_mapped_count += mapped_count

    for label in label_to_ottid:
        if label_to_ottid[label] == None:
            unmapped.append((study_id, label))

def load_study(path):
    with open(path, 'r') as infile:
        blob = json.load(infile)
        if not "nexml" in blob: return
        return blob["nexml"]

def get_label_to_ottid(nexson):
    label_to_ottid = {}
    for block in nexson["otusById"].itervalues():
        for otu in block["otuById"].itervalues():
            label = otu["^ot:originalLabel"]
            if "^ot:ottId" in otu:
                id = otu.get("^ot:ottId", None)
                label_to_ottid[label] = id
            else:
                label_to_ottid[label] = None
    return label_to_ottid

def count_mapped(label_to_ottid):
    count = 0
    for label in label_to_ottid:
        if label_to_ottid[label] != None:
            count += 1
    return count

process_all(sys.argv[1])
