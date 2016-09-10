
# Process an additions request by (a) adding ott ids to the request
# blob yielding archivable blob, (b) returning tag-to-id mapping.

# Inputs: OTT id range (command line arg), request blob (standard input)
# Outputs: addition blob (for archive), response blob (tag/id mapping)

# Test:
# echo '{"taxa": [{"tag": "taxon277", "name": "Norman", "parent": 12345, "sources": []}]}' | \
# python util/process_addition_request.py --dir additions --count 1 --min 25 && \
# cat additions/next_ott_id.json 

import sys, os, json, argparse

def get_ids(count, minid, dir, advance):
    if not os.path.exists(dir):
        os.makedirs(dir)
    idpath = os.path.join(dir, 'next_ott_id.json')
    id = 0
    stuff = {}
    if os.path.exists(idpath):
        with open(idpath, 'r') as idfile:
            stuff = json.load(idfile)
            id = stuff["next_ott_id"]
    if id < minid:
        id = minid
    if advance:
        bump = id + count
        stuff["next_ott_id"] = bump
        with open(idpath, 'w') as idfile:
            json.dump(stuff, idfile)
            idfile.write('\n')
    return id

def service_request(blob, count, minid, dir, advance):
    taxa = blob["taxa"]         # fail fast

    count_again = 0
    for taxon in taxa:
        if not "tag" in taxon:
            return {"error": "missing tag"}
        tag = taxon["tag"]
        if not "name" in taxon:
            return {"error": "missing name for %s" % tag}
        name = taxon["name"]
        if not ("parent" in taxon or "parent_tag" in taxon):
            return {"error": "missing parent for %s" % name}
        if not "sources" in taxon:
            return {"error": "missing sources for %s" % name}
        if not "ott_id" in taxon:
            count_again += 1

    if count_again != count:
        return {"error": "number of taxa %s does not match count %s" % (count_again, count)}

    id = get_ids(count, minid, dir, advance)

    tag_to_id = {}

    first_id = id
    last_id = None
    for taxon in taxa:
        if not "ott_id" in taxon:
            taxon["ott_id"] = id
            tag_to_id[taxon["tag"]] = id
            last_id = id
            id += 1
            p = taxon.get("parent_tag")
            if p != None:
                taxon["parent"] = tag_to_id[p]

    docid = 'additions-%s-%s' % (first_id, last_id)
    blob['id'] = docid

    docdir = os.path.join(dir, 'amendments')
    if not os.path.isdir(docdir):
        os.makedirs(docdir)
    docpath = os.path.join(docdir, '%s.json' % docid)
    with open(docpath, 'w') as docfile:
        json.dump(blob, docfile, indent=2)
    sys.stderr.write('Wrote %s\n' % docpath)
    return tag_to_id

if __name__ == '__main__':

    sys.stderr.write('Processing addition request...\n')
    sys.stderr.flush()

    argparser = argparse.ArgumentParser(description='Allocate OTT ids and record an addition request.  Request on stdin, alist on stdout.')
    argparser.add_argument('--dir', dest='dir', help='where to find/put addition docs and ott id counter')
    argparser.add_argument('--count', dest='count', type=int, help='how many ids to allocate')
    argparser.add_argument('--min', dest='min', type=int, help='smallest possible id')
    argparser.add_argument('--advance', dest='advance', action='store_true', help='store updated id counter')
    argparser.add_argument('--no-advance', dest='advance', action='store_false', help='do not store updated id counter')
    args = argparser.parse_args()

    blob = json.load(sys.stdin)

    sys.stderr.write('Got request...\n')
    sys.stderr.flush()

    tag_to_id = service_request(blob, int(args.count), int(args.min), args.dir, args.advance)

    sys.stderr.write('Serviced request...\n')
    sys.stderr.flush()

    json.dump(tag_to_id, sys.stdout, indent=2)
    sys.stdout.write('\n')
    sys.stdout.flush()
    sys.exit(0)
