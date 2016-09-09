# Three cases
#  - remote stateful  --auth=authpath
#  - local stateful (local clone update)  --clone=path
#  - local stateless   (no param)

# Process an additions request by (a) adding ott ids to the request
# blob yielding archivable blob, (b) returning tag-to-id mapping.

# Input: request blob (standard input)
# Outputs: addition doc blob (for/from archive), response blob (tag/id mapping, stdout)

"""
Test:
echo '{"taxa": [{"tag": "taxon277", "name": "Norman", "parent": 12345, "sources": []}]}' | \
python util/process_addition_request.py --clone additions-test --nextid 25 && \
cat additions-test/next_ott_id.json 
"""

# Sample --auth file:
#   {"author_name": auth_stuff["author_name"],"Jonathan Rees",
#    "author_email": "jar398@mumble.net",
#    "auth_token": "REDACTED"}

import sys, os, json, argparse, requests

# Returns tag to id dict
# firstid is advisory, not prescriptive

url = 'https://api.opentreeoflife.org/v3/amendment'

def service_request_using_web_service(request, url, auth):
    with open(auth, 'r') as authfile:
        auth_stuff = json.load(authfile)
    r = requests.request('POST',
                         url,
                         params={"author_name": auth_stuff["author_name"],
                                 "author_email": auth_stuff["author_email"],
                                 "auth_token": auth_stuff["auth_token"]},
                         data=request,
                         headers={'Content-type': 'application/json'},
                         allow_redirects=True)
    r.raise_for_status()
    # see https://github.com/OpenTreeOfLife/germinator/wiki/Taxonomic-service-for-adding-new-taxa
    return r.json()

def service_request_locally(request, firstid, clone):
    taxa = request["taxa"]         # fail fast
    if not os.path.isdir(clone):
        os.makedirs(clone)
    docdir = os.path.join(clone, 'amendments')
    if not os.path.isdir(docdir):
        os.makedirs(docdir)
    (doc, tag_to_id) = make_additions_document(request, firstid, clone)    # increments next_ott_id
    docpath = os.path.join(docdir, doc["id"] + '.json')
    with open(docpath, 'w') as docfile:
        json.dump(doc, docfile, indent=2)
    sys.stderr.write('Wrote %s\n' % docpath)
    response = {"id": doc["id"],
                "tag_to_ottid": tag_to_id}
    return response

# Create json blob that might be stored in amendments repository.
# No need to make this if storing to local repo clone.

def make_additions_document(request, firstid, dir):
    taxa = request["taxa"]         # fail fast
    count = 0
    for taxon in taxa:
        if not "ott_id" in taxon:
            count += 1
    (first_id, last_id) = get_id_range(count, firstid, dir)
    tag_to_id = assign_ids(taxa, first_id)
    docid = 'additions-%s-%s' % (first_id, last_id)
    doc = {}
    for key in request.keys():
        doc[key] = request[key]
    for taxon in doc["taxa"]:
        del taxon["tag"]
    doc["id"] = docid
    return (doc, tag_to_id)

# Allocate a range of ids (from clone) by modifying next_ott_id.json

def get_id_range(count, firstid, dir):
    if not os.path.exists(dir):
        os.makedirs(dir)
    idpath = os.path.join(dir, 'next_ott_id.json')
    id = 0
    stuff = {}
    if os.path.exists(idpath):
        with open(idpath, 'r') as idfile:
            stuff = json.load(idfile)
            id = stuff["next_ott_id"]
    if firstid != None and id < firstid:
        id = firstid
    bump = id + count
    stuff["next_ott_id"] = bump
    with open(idpath, 'w') as idfile:
        json.dump(stuff, idfile)
        idfile.write('\n')
    return (id, id + count - 1)

def assign_ids(taxa, firstid):
    tag_to_id = {}
    first_id = firstid
    last_id = None
    id = first_id
    for taxon in taxa:
        if not "ott_id" in taxon:
            taxon["ott_id"] = id
            tag_to_id[taxon["tag"]] = id
            last_id = id
            id += 1
            p = taxon.get("parent_tag")
            if p != None:
                taxon["parent"] = tag_to_id[p]
    return tag_to_id

def validate(request):
    for taxon in request["taxa"]:
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
    return None

# User might want to change this - use --firstid parameter
default_firstid = 9000000

if __name__ == '__main__':

    sys.stderr.write('Processing addition request...\n')
    sys.stderr.flush()

    argparser = argparse.ArgumentParser(description='Allocate OTT ids and record an addition request.  Request on stdin, alist on stdout.')
    argparser.add_argument('--url', dest='url', default=None, help='web service URL e.g. https://api.opentreeoflife.org/v3/amendment')
    argparser.add_argument('--auth', dest='auth', default=None, help='file containing oauth credentials as json (use web service)')
    argparser.add_argument('--clone', dest='clone', default=None, help='path to local clone of amendments repo (local operation)')
    argparser.add_argument('--nextid', dest='nextid', default=8000000, help='next id to use, if none found in clone')
    args = argparser.parse_args()

    request = json.load(sys.stdin)
    sys.stderr.write('Got request...\n')
    sys.stderr.flush()

    if validate(request) != None:
        # error
        print >>sys.stderr, request
        sys.exit(1)

    if args.auth != None:
        tag_to_id = service_request_using_web_service(request, args.url, args.auth)
    elif args.clone != None:
        tag_to_id = service_request_locally(request, int(args.nextid), args.clone)
    else:
        print >>sys.stderr, 'Need to specify either --auth or --clone'
        sys.exit(1)

    sys.stderr.write('Serviced request...\n')
    sys.stderr.flush()

    json.dump(tag_to_id, sys.stdout, indent=2)
    sys.stdout.write('\n')
    sys.stdout.flush()
    sys.exit(0)
