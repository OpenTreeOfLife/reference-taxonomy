import sys, re

def doit():

    alts = '|'.join(['set_parent', 'take', 'rename', 'clobberName',
                     'notCalled', 'Has_child', 'Whether_same',
                     'fix_names',
                     'extinct', 'extant', 'set_extant', 'Whether_extant',
                     'setDivision',
                     'same', 'synonym', 'prune', 'absorb', 'elide'])

    pat = re.compile('(%s)\\(' % (alts,))

    counts = {}

    for filename in sys.argv[1:]:
        with open(filename, 'r') as file:
            for line in file:
                m = pat.search(line)
                if m:
                    key = m.group(1)
                    counts[key] = counts.get(key, 0) + 1

    total = 0
    for key in counts:
        print_item(key, counts[key])
        total += counts[key]

    print_item('TOTAL', total)

def print_item(key, count):
    print '%3s %s' % (count, key)

doit()
