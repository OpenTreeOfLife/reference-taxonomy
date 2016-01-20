from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.registry import Registry, Correspondence

import sys

if False:
    synth = Taxonomy.getNewick('draftversion3.tre', 'synth')
else:
    synth = Taxonomy.getTaxonomy('aster-synth4/', 'synth')

n_inclusions = 2
n_exclusions = 0

r = Registry()

corr = Correspondence(r, synth, n_inclusions, n_exclusions)
print '--- Assigning extisting registrations to nodes in', synth
corr.resolve()
print '--- Extending registry with new registrations for', synth
corr.extend()

r.dump('registry.csv')


def assignLocators():
    i = 0
    for node in synth:
        if node.id == None:
            # 1. Get registration
            reg = corr.assignments.get(node)
            if reg == None:
                print 'no registration:', node
            else:
                # 2. Get inclusions (-> list of Terminals)
                inclusions = reg.samples
                # 3. Get OTT ids for inclusions
                print '-'.join([inc.id for inc in inclusions])
            i += 1
            if i > 30: break

assignLocators()
