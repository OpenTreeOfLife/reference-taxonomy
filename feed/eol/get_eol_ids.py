# Argument = path to EOL digest file (see make_digest.py)

import csv
from org.opentreeoflife.taxa import QualifiedId, Rank

outpath = 'eol_conflicts.csv'

multiple_page_ids = 0   #kludge

def get_eol_ids(inpath, tax):
    page_to_ott = {}
    ott_to_page = {}
    with open(inpath, 'r') as infile:
        print '| Processing EOL page ids file %s' % (inpath)
        reader = csv.reader(infile)
        row_count = 0
        hits = 0
        for row in reader:
            row_count += 1
            [page_id, idspace, source_id] = row
            if row_count % 250000 == 0:
                print row_count, row
            qid = QualifiedId(idspace, source_id)
            node = tax.lookupQid(qid)
            if node != None:
                nodes = page_to_ott.get(page_id)
                if nodes == None:
                    nodes = [node]
                    page_to_ott[page_id] = nodes
                elif not node in nodes:
                    nodes.append(node)

                pages = ott_to_page.get(node.id)
                if pages == None:
                    pages = [page_id]
                    ott_to_page[node.id] = pages
                elif not page_id in pages:
                    pages.append(page_id)

        print '| EOL page id hits: %s, node hits: %s' % (len(page_to_ott), len(ott_to_page))

    # Sort page ids and OTT nodes

    for node_id in ott_to_page:
        pages = ott_to_page[node_id]
        if len(pages) > 1: pages.sort(key=int)

    for page_id in page_to_ott:
        nodes = page_to_ott[page_id]
        if len(nodes) > 1: nodes.sort()

    # Assign an EOL page id to as many nodes as possible

    losers = 0
    for node_id in ott_to_page:
        pages = ott_to_page[node_id]
        page_id = pages[0]
        node = tax.lookupId(node_id)

        # Multiple page ids for this node.  Pick lowest numbered one.
        # Maybe a synonym known to OTT but missing from EOL.
        # Or, maybe an incorrect synonym in OTT.
        # But no good way to tell.

        renode = page_to_ott[page_id][0]
        if renode == node:
            node.addSourceId(QualifiedId('eol', page_id))
        else:
            losers += 1
    if losers > 0:
        print '| For %s nodes, EOL page id was already taken' % losers

    # Report on errors EOL homonym errors / OTT missing synonyms

    things_to_report = 0
    for page_id in page_to_ott:
        if len(page_to_ott[page_id]) > 1:
            things_to_report += 1

    if things_to_report > 0:
        print 'Writing %s (%s events)' % (outpath, things_to_report)
        with open(outpath, 'w') as outfile:
            writer = csv.writer(outfile)

            for page_id in page_to_ott:
                nodes = page_to_ott[page_id]
                if len(nodes) > 1:
                    # Multiple OTT nodes for a single EOL page id.
                    # Maybe a synonym known to EOL but missing from OTT.
                    # Or, maybe an incorrect synonym in EOL.

                    # Two lines of evidence to consider:
                    # 1. Distance in tree
                    # 2. Epithet comparison

                    # If near, or same epithet, then more likely an OTT error.
                    # If distant, then more likely an EOL error.

                    node1 = nodes[0]
                    node2 = nodes[1]

                    similarity = 0
                    if node1.name == node2.name:
                        similarity = 3
                    elif (node1.name.startswith(node2.name) or
                          node2.name.startswith(node1.name)):
                        similarity = 2
                    elif same_epithet(node1.name, node2.name):
                        similarity = 1
                    if similarity != 2:
                        writer.writerow([page_id, node1.id, node1.name, node2.id, node2.name,
                                         node1.rank.name, node1.mrca(node2).count(), similarity])

def get_eol_qid(node):
    for qid in node.sourceIds:
        if qid.prefix == 'eol':
            return qid
    return None

def same_epithet(name1, name2):
    epi1 = epithet_stem(name1)
    if epi1 == None: return False
    epi2 = epithet_stem(name2)
    if epi2 == None: return False
    return epi1 == epi2

def epithet_stem(name):
    s = name.split(' ', 1)
    if len(s) != 2: return None
    epi = s[1]
    if epi.endswith('us'): return epi[0:-2]
    if epi.endswith('um'): return epi[0:-2]
    if epi.endswith('a'): return epi[0:-1]
    return epi

if __name__ == '__main__':
    print same_epithet('Foo mumbla', 'Foo mumblus')
