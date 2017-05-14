# Observed values of the 'status' field:

# accepted
# interim unpublished    - treat like accepted
# temporary name    - trim [unassigned] when present?
# alternate representation   - what does this mean?
# unaccepted        - usually a synonym, but not always
# taxon inquirendum
# nomen nudum
# uncertain
# nomen dubium

import os, csv, argparse

parents = {}
taxa = {}
synonyms = {}
status_values = {}
has_children = {}

def process_worms(digest_path, out_path):
    names = sorted(os.listdir(digest_path))
    for name in names:
        if name.startswith('l'):
            process_links(os.path.join(digest_path, name))
    print '%s parent/child links' % len(parents)
    for name in names:
        if name.startswith('a'):
            process_records(os.path.join(digest_path, name))
    taxpath = os.path.join(out_path, 'taxonomy.tsv')
    print 'writing %s taxa to %s' % (len(taxa), taxpath)
    with open(taxpath + '.new', 'w') as taxfile:
        form = '%s\t%s\t%s\t%s\t%s\n'
        taxfile.write(form % ('uid', 'parent', 'name', 'rank', 'flags'))
        for taxon in sorted(taxa.values(), key=lambda taxon: int(taxon[0])):
            taxfile.write(form % taxon)
    os.rename(taxpath + '.new', taxpath)
    synpath = os.path.join(out_path, 'synonyms.tsv')
    print 'writing %s synonyms to %s' % (len(synonyms), synpath)
    with open(synpath + '.new', 'w') as synfile:
        form = '%s\t%s\t%s\n'
        synfile.write(form % ('uid', 'name', 'sid'))
        for synonym in sorted(synonyms.values(), key=lambda synonym: int(synonym[0])):
            synfile.write(form % synonym)
    os.rename(synpath + '.new', synpath)

def process_links(inpath):
    with open(inpath, 'r') as infile:
        reader = csv.reader(infile)
        reader.next()           # header row
        for row in reader:
            id = row[0]
            parent_id = row[1]
            if len(row) == 2 or row[2] == 'c':
                if id in parents:
                    print '## MULTIPLE PARENTS', row
                parents[id] = parent_id
                has_children[parent_id] = True

def process_records(inpath):
    with open(inpath, 'r') as infile:
        reader = csv.reader(infile)
        reader.next()           # header row
        for row in reader:
            # id,name,authority,rank,status,unacceptreason,valid,extinct
            # 0 ,1   ,2        ,3   ,4     ,5             ,6    ,7
            id = row[0]
            name = row[1]
            valid = row[6]
            if id == valid:
                parent = parents.get(id)
                if parent == None:
                    print 'missing parent', id
                    continue
                flags = []
                if row[7] != '':
                    flags.append('extinct')
                status = row[4]
                unassigned = '[unassigned] '
                if name.startswith(unassigned):
                    name = name[len(unassigned):]
                if status == 'accepted':
                    True
                elif status == 'interim unpublished':
                    True
                elif id in has_children:
                    True
                else:
                    flags.append('hidden')
                status_values[status] = True
                taxa[id] = (id, parent, name, row[3].lower(), ','.join(flags))
            elif valid != '':
                synonyms[id] = (valid, name, id)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('digest')
    parser.add_argument('out')
    args = parser.parse_args()
    process_worms(args.digest, args.out)

