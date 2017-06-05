# This script is responsible for changing the older metadata format
# (files resources.json and captures.json stored in the resources/
# directory) to the newer format (a properties.json file stored with
# every artifact archive).

import json, os, subprocess, argparse

res = {}
cap = {}

# Read old resources and captures files

def explode():

    with open('old-metadata/resources.json', 'r') as infile:
        resources = json.load(infile)

    with open('old-metadata/captures.json', 'r') as infile:
        captures = json.load(infile)

    for blob in resources["resources"]:
        res[blob["name"]] = blob

    for blob in captures["captures"]:
        cap[blob["name"]] = blob

    print '%s resources, %s versions' % (len(res), len(cap))

# Turn 2014-01-30 into 20140130
def squash_date(value):
    if not (isinstance(value, str) or isinstance(value, unicode)):
        return value
    if (len(value) == 10 and
        value[4] == '-' and
        value[7] == '-' and
        value[0:4].isdigit() and
        value[5:7].isdigit() and
        value[8:10].isdigit()):
        return value[0:4] + value[5:7] + value[8:10]
    else:
        return value

def convert(blob):
    series = res[blob["capture_of"]]
    props = {}
    if "ott_idspace" in series:
        props["ott_idspace"] = series["ott_idspace"]
    if "description" in series:
        props["description"] = series["description"]
    if "doi" in series:
        props["doi"] = series["doi"]
    props["description"] = series["description"]

    name = blob["name"]
    for (key, val) in blob.items():
        value = squash_date(val)
        if key == "capture_of":
            props["series"] = value
        elif key == "last_modified":
            props["origin_date"] = value
        elif key == "label":
            props["version"] = value
        elif key == "filename":
            props["archive_file"] = value
            if not value.startswith(name):
                print 'wha??', value, name
            props["suffix"] = value[len(name):]
        elif key == "_type":
            True
        elif key == "sources":
            if "if" in value and not "fung" in value:
                value["fung"] = value["if"]
                del value["if"]
            props["sources"] = value
        else:
            props[key] = value

    if (name.startswith(blob["capture_of"]) and
        name.endswith(blob["label"])):
        props["separator"] = name[len(blob["capture_of"]):-len(blob["label"])]
    return props

# Write properties files locally to properties/ directory

local_root = 'properties'

def write_properties():

    print 'writing to', os.path.join(local_root, '')
    for (name, blob) in cap.items():
        if name.startswith('h2007'): continue
        props = convert(blob)

        dir = os.path.join(local_root, name)
        if not os.path.isdir(dir):
            os.makedirs(dir)
        path = os.path.join(dir, 'properties.json')
        with open(path, 'w') as outfile:
            # print 'Writing', path
            json.dump(props, outfile, indent=2, sort_keys=True)
            outfile.write('\n')

# Use scp to copy to files server

def write_to_server():

    # Cf. store-archive
    prefix = os.environ.get('SSH_PATH_PREFIX')
    if prefix == None:
        prefix = 'files.opentreeoflife.org:files.opentreeoflife.org'

    psplit = prefix.split(':', 1)

    if len(psplit) > 1:
        host = psplit[0]
        root = psplit[1]
    else:
        host = None    # localhost
        root = prefix

    for (name, blob) in cap.items():
        if name.startswith('h2007'): continue
        props = convert(blob)
        dir = os.path.join(local_root, name)
        path = os.path.join(dir, 'properties.json')

        hpath = '%s/%s/%s/properties.json' % (root, props["series"], name)
        if host != None:
            spath = '%s:%s' % (host, hpath)
            command = ['scp', '-q', '-p', path, spath]
            print ' '.join(command)
            status = subprocess.call(command)
            if status != 0:
                print '*** scp failed (%s) to %s' % (status, spath)
            else:
                # Check to see if it happened
                command = ['ssh', host, 'test', '-e', hpath]
                if subprocess.call(command) != 0:
                    print '*** ?? File not found after scp: %s ***' % hpath
        else:
            command = ['cp', '-p', path, hpath]
            status = subprocess.call(command)
            if status != 0:
                print '*** cp failed (%s) to %s' % (status, hpath)

# Copy properties files locally from properties/ to r/
# Careful not to write the file when it is unchanged - this is for
# the sake of 'make' dependencies.

def write_to_r():

    for (name, blob) in cap.items():
        dir = os.path.join(local_root, name)
        path = os.path.join(dir, 'properties.json')
        dir2 = os.path.join('r', name)
        if os.path.isdir(dir2):
            path2 = os.path.join(dir2, 'properties.json')
            if not os.path.exists(path2) or subprocess.call(['cmp', '-s', path, path2]) != 0:
                print 'Copying %s to %s' % (path, path2)
                subprocess.call(['cp', '-p', path, path2])

parser = argparse.ArgumentParser(description='Properties tool')
parser.add_argument('--local', dest='local', action='store_true',
                    help='store properties files locally into r/ directories')
parser.add_argument('--publish', dest='publish', action='store_true',
                    help='store properties files onto server')
args = parser.parse_args()

explode()
write_properties()
if args.local: write_to_r()
if args.publish: write_to_server()
