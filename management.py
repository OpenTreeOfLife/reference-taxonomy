import os, json

# File name and property file munging.  Coordinates with Makefile.

def issue_root(spec):
    head = os.path.join('r', spec)
    if os.path.isdir(head):
        return head
    return os.path.join('r', spec + '-HEAD')

def resource_path(spec):
    return os.path.join(issue_root(spec), 'resource', '')

def source_path(spec):
    return os.path.join(issue_root(spec), 'source', '')

def properties_path(spec):
    return os.path.join(issue_root(spec), 'properties.json')

def get_property(spec, selector):
    with open(properties_path(spec), 'r') as infile:
        blob = json.load(infile)
    return blob[selector]

def set_property(spec, selector, value):
    props_path = properties_path(spec)
    with open(props_path, 'r') as infile:
        blob = json.load(infile)
    blob[selector] = value
    with open(props_path, 'w') as outfile:
        json.dump(blob, outfile)
        outfile.write('\n')
