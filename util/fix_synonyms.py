
# Get rid of all but the first same-named synonyms for each taxon

import sys

priorities = {'silva': 0,
              'if': 1,
              'worms': 2,
              'ncbi': 3,
              'gbif': 4,
              'irmng': 5,
              '': 999}

def doit():
    things = []    # list of [flag, string, source]
    seen = {}     # maps (name, uid) to [flag, string, source]
    count = 0
    override = 0
    for line in sys.stdin:
        # Consider canceling this line or some other
        fields = line.split('\t|\t')
        (name, uid, typ, uniq, sources, _) = fields
        source = sources.split(',', 1)[0].split(':', 1)[0]

        thing = [True, line, source]
        things.append(thing)

        key = (name, uid)

        if len(things) % 100000 == 0: print >>sys.stderr, key, len(seen), source

        if key in seen:
            thing2 = seen[key]
            source2 = thing2[2]
            # If priority of current line lower than previously seen line, replace previous
            if priorities[source] < priorities[source2]:
                thing2[0] = False
                seen[key] = thing
                override += 1
            else:
                # else, do not use current
                thing[0] = False
            count += 1
        else:
            seen[key] = thing
    print >>sys.stderr, 'Duplicates:', count, 'override:', override
    for thing in things:
        if thing[0]:
            sys.stdout.write(thing[1])

doit()
