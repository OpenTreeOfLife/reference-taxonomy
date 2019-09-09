# Observed values of the 'status' field:

# accepted
# interim unpublished    - treat like accepted
# temporary name    - trim [unassigned] when present?
# alternate representation   - what does this mean?
# unaccepted        - usually a synonym, but not always
# nomen nudum
# uncertain
# nomen dubium
# taxon inquirendum    - do not use

import os, csv, argparse, re

parents = {}
taxa = {}
synonyms = {}
has_children = {}
taxa_by_name = {}    # name -> records

def process_worms(digest_path, out_path):
    names = sorted(os.listdir(digest_path))
    for name in names:
        if name.startswith('l'):
            process_links(os.path.join(digest_path, name))
    print '%s parent/child links' % len(parents)
    for name in names:
        if name.startswith('a'):
            process_records(os.path.join(digest_path, name))
    if not os.path.isdir(out_path):
        os.makedirs(out_path)
    taxpath = os.path.join(out_path, 'taxonomy.tsv')
    print 'writing %s taxa to %s' % (len(taxa), taxpath)
    with open(taxpath + '.new', 'w') as taxfile:
        form = '%s\t%s\t%s\t%s\t%s\n'
        taxfile.write(form % ('uid', 'parent_uid', 'name', 'rank', 'flags'))
        for taxon in sorted(taxa.values(), key=lambda taxon: int(taxon[0])):
            if not suppress(taxon):
                taxfile.write(form % taxon)
    os.rename(taxpath + '.new', taxpath)
    synpath = os.path.join(out_path, 'synonyms.tsv')
    print 'writing %s synonyms to %s' % (len(synonyms), synpath)
    with open(synpath + '.new', 'w') as synfile:
        form = '%s\t%s\t%s\t%s\n'
        synfile.write(form % ('uid', 'name', 'type', 'sid'))
        for synonym in sorted(synonyms.values(), key=lambda synonym: int(synonym[0])):
            synfile.write(form % synonym)
    os.rename(synpath + '.new', synpath)
	# The reasons are very interesting, but not to the average OTT builder
    # print 'unaccept_reasons', sorted(unaccept_reasons.values())

def suppress(taxon):
    # (id, parent_id, name, rank, flags) = taxon
    if taxon[0] in has_children:
        return False
    name = taxon[2]
    parent_id = taxon[1]
    flags = taxon[4]
    taxa = taxa_by_name[name]
    if len(taxa) == 1:
        return False
    elif parent_id in map(grandparent_id, taxa):
        # print 'suppress', taxon   -- 145 of these
        return True
    elif 'hidden' in flags:
        # e.g. Podascon chevreuxi - 211 of these
        return True
    else:
        return False

def grandparent_id(taxon):
    parent_id = taxon[1]    # OTT form
    parent = taxa.get(parent_id)
    if parent == None: return None
    gp_id = parent[1]
    return gp_id

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

sub = re.compile('([A-Za-z]+) \\(([A-Za-z]+)\\)(.*)')

def process_records(inpath):
    with open(inpath, 'r') as infile:
        reader = csv.reader(infile)
        reader.next()           # header row
        for row in reader:
            # id,name,authority,rank,status,unacceptreason,valid,extinct
            # 0 ,1   ,2        ,3   ,4     ,5             ,6    ,7
            id = row[0]
            valid_id = row[6]
            rank = row[3].lower()
            name = fix_name(row[1], rank)
            if id == valid_id or valid_id == '':
                taxon = record_to_taxon(row, rank, name)
                if taxon != None:
                    taxa[id] = taxon
            elif valid_id != '':
                # synonym, original combination, original rank as subgenus
                unaccept_reason = row[5].strip()
                lower = unaccept_reason.lower()
                if 'invalid' in lower:
                    typ = 'invalid'
                elif 'objective synonym' in lower:
                    typ = 'objective synonym'
                elif 'transferred' in lower:
                    typ = 'objective synonym'
                elif 'subjective synonym' in lower:
                    typ = 'subjective synonym'
                elif lower.startswith('type species'):
                    typ = 'objective synonym'
                elif 'spelling' in lower:
                    typ = 'misspelling'
                elif 'incorrect' in lower:
                    typ = 'misspelling'
                elif 'gender' in lower:
                    typ = 'gender variant'
                elif len(unaccept_reason) > 20:
                    typ = 'long story'
                else:
                    typ = unaccept_reason
                    unaccept_reasons[lower] = unaccept_reason
                synonyms[id] = (valid_id, name, typ, id)

unaccept_reasons = {}

def record_to_taxon(row, rank, name):
    id = row[0]

    parent = parents.get(id)
    if parent == None:
        print 'missing parent', id
        parent = ''

    # Determine flags
    flags = []
    if row[7] == '1':
        flags.append('extinct')
    status = row[4]
    if status == 'accepted':
        True
    elif status == 'interim unpublished':
        True
    elif id in has_children:     # This works
        True
    else:
        flags.append('hidden')

    taxon = (id, parent, name, rank, ','.join(flags))
    others = taxa_by_name.get(name)
    if others == None:
        taxa_by_name[name] = [taxon]
    else:
        others.append(taxon)
    return taxon

#unassigned = '[unassigned] '

def fix_name(name, rank):
    #if name.startswith(unassigned):
    #    name = name[len(unassigned):]
    m = sub.match(name)
    if m != None:
        # Four cases:
        #  Gg (Sub) sp  -> Gg sp
        #  Gg (Sub)     -> Sub
        #  Gs (Gs) sp   -> Gs sp
        #  Gs (Gs)      -> Gs subgenus Gs
        if m.group(3) != '':
            name = ('%s%s' % (m.group(1), m.group(3)))
        elif m.group(1) == m.group(2):
            name = ('%s %s %s%s' %
                    (m.group(1), rank, m.group(2), m.group(3)))
        else:
            name = m.group(2)
    return name

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('digest')
    parser.add_argument('out')
    args = parser.parse_args()
    process_worms(args.digest, args.out)
