# Load EOL page ids into a taxonomy under construction.

# Test with:
#  bin/jython util/load_eol_page_ids.py t/tax/aster/ r/eol-HEAD/resource/eol-mappings.csv ast2eol.csv astreport.csv
#  bin/jython util/load_eol_page_ids.py r/ott-NEW/source/ r/eol-NEW/resource/eol-mappings.csv ott2eol.csv ereport.csv

# Input = path to EOL digest file (see process_eol.py).  Digest file looks like
# 5559,if,10000001
# 5577,if,10000002
# 46582741,worms,250736
# 46582742,worms,250737

import csv, sys, argparse
from org.opentreeoflife.taxa import Taxonomy, QualifiedId
from java.lang import System, Runtime

def load_eol_page_ids(inpath, tax):
    (page_to_nodes, node_to_pages) = load_eol_ids(inpath, tax)
    add_page_ids_to_nodes(tax, node_to_pages, page_to_nodes)
    return (page_to_nodes, node_to_pages)

def load_eol_ids(inpath, tax):
    System.gc()
    rt = Runtime.getRuntime()
    print '# Memory', rt.freeMemory()/(1024*1024), rt.totalMemory()/(1024*1024)

    page_to_nodes = {}
    node_to_pages = {}
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
            if node != None:   # and node.isPotentialOtu():

                nodes = page_to_nodes.get(page_id)
                if nodes == None:
                    nodes = [(node, qid)]
                    page_to_nodes[page_id] = nodes
                elif not in_alist(node, nodes):
                    nodes.append((node, qid))

                pages = node_to_pages.get(node)
                if pages == None:
                    pages = [page_id]
                    node_to_pages[node] = pages
                elif not page_id in pages:
                    pages.append(page_id)

        print '| OTT nodes having at least one EOL page: %s' % len(node_to_pages)
        print '| EOL page ids having at least one OTT node: %s' % len(page_to_nodes)

    # Sort page ids for each OTT node (will use smallest one)
    for node in node_to_pages:
        pages = node_to_pages[node]
        if len(pages) > 1:
            pages.sort(key=int)

    return (page_to_nodes, node_to_pages)

def add_page_ids_to_nodes(tax, node_to_pages, page_to_nodes):

    # Sort for sake of deterministic output (?) and unique choice
    for page_id in page_to_nodes:
        nodes = page_to_nodes[page_id]
        if len(nodes) > 1:
            nodes.sort(key=lambda(node, qid): node.id)

    # Assign an EOL page id to as many nodes as possible
    for node in node_to_pages:
        pages = node_to_pages[node]
        page_id = pages[0]
        (node2, qid) = page_to_nodes[page_id][0]
        if node2 is node:
            node.addSourceId(QualifiedId('eol', page_id))
        # 1237 cases where node2 is not node

def dump_mapping(node_to_pages, outpath):
    with open(outpath, 'w') as outfile:
        writer = csv.writer(outfile)
        writer.writerow(['ott', 'eol'])
        for node in sorted(node_to_pages.keys(), key=lambda node:int(node.id)):
            writer.writerow([node.id, node_to_pages[node][0]])

def report(page_to_nodes, reportpath):
    # Report on errors EOL homonym errors / OTT missing synonyms

    pages_to_report = []

    # Sort for sake of deterministic output (?) and unique choice
    for page_id in page_to_nodes:
        nodes = page_to_nodes[page_id]
        if len(nodes) > 1:
            pages_to_report.append(page_id)

    if len(pages_to_report) == 0:
        print '| Nothing to report'

    else:
        print '| Writing %s (%s events)' % (reportpath, len(pages_to_report))
        pages_to_report.sort(key=int)
        with open(reportpath, 'w') as outfile:
            writer = csv.writer(outfile)

            for page_id in pages_to_report:
                # Multiple OTT nodes for a single EOL page id.
                # Maybe a synonym known to EOL but missing from OTT.
                # Or, maybe an incorrect synonym in EOL.

                # Two lines of evidence to consider:
                # 1. Distance in tree
                # 2. Epithet comparison

                # If near, or same epithet, then more likely an OTT error.
                # If distant, then more likely an EOL error.

                nodes = page_to_nodes[page_id]
                (node1, qid1) = nodes[0]
                (node2, qid2) = nodes[1]

                similarity = 0
                if node1.name == None:
                    print '# nameless:', node1
                elif node2.name == None:
                    print '# nameless:', node2
                elif node1.name == node2.name:
                    similarity = 3
                elif (node1.name.startswith(node2.name) or
                      node2.name.startswith(node1.name)):
                    similarity = 2
                elif same_epithet(node1.name, node2.name):
                    similarity = 1
                if similarity != 2:
                    if node1.getDivision() == None:
                        div1 = ''
                    else:
                        div1 = node1.getDivision().name
                    if node2.getDivision() == None:
                        div2 = ''
                    else:
                        div2 = node2.getDivision().name
                    writer.writerow([page_id,
                                     qid1, node1.name, div1,
                                     qid2, node2.name, div2,
                                     node1.rank.name, node1.mrca(node2).count(), similarity])

def in_alist(node, nodes):
    for (xnode, qid) in nodes:
        if node is xnode:
            return True
    return False

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
    epi = s[-1]
    if epi.endswith('us'): return epi[0:-2]
    if epi.endswith('um'): return epi[0:-2]
    if epi.endswith('a'): return epi[0:-1]
    return epi

if __name__ == '__main__':
    taxpath = sys.argv[1]
    inpath = sys.argv[2]
    outpath = sys.argv[3]
    reportpath = sys.argv[4]

    tax = Taxonomy.getRawTaxonomy(taxpath, 'ott')
    tax.startQidIndex()
    print 'ncbi:98683 =', tax.lookupQid(QualifiedId('ncbi', '98683'))
    (page_to_nodes, node_to_pages) = load_eol_page_ids(inpath, tax)
    dump_mapping(node_to_pages, outpath)
    report(page_to_nodes, reportpath)
