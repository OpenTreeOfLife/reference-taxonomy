
from org.opentreeoflife.smasher import Taxonomy

sourcenames = ['if.4', 'if.2', 'if.1']

sources = [Taxonomy.getTaxonomy('old/' + name + '/', name) for name in sourcenames]

fung = Taxonomy.getTaxonomy('old/if.7/', 'if')

def fix_root():
    root = establish('Fungi', '90156', 'kingdom')
    establish('Ascomycota', '90031', 'phylum')
    return root

def establish(name, id, rank):
    taxon = fung.maybeTaxon(name)
    if taxon == None:
        taxon = fung.newTaxon(name, rank, None)
        taxon.setId(id)
    else:
        if taxon.id != id:
            print '** unexpected id %s for %s' % (taxon.id, name)
    return taxon

def fix_parents(root):
    changes = {}
    for taxon in fung:
        if taxon.getParent() == None:
            # See if the taxon in a previous version
            recovered = None
            for source in sources:
                parent = attempt(taxon, source.maybeTaxon(taxon.id))
                if parent != None:
                    changes[taxon] = parent
                else:
                    otaxa = source.lookup(taxon.name)
                    if otaxa != None and len(otaxa) == 1:
                        parent = attempt(taxon, otaxa[0])
                        if parent != None:
                            changes[taxon] = parent
    for taxon in changes:
        taxon.changeParent(changes[taxon])
    print 'Recovered %s parents' % len(changes)

def attempt(taxon, otaxon):
    if otaxon != None and otaxon.parent != None:
        # Return it to target taxonomy.  First try by id
        pid = otaxon.parent.id
        if pid == '99027': pid = '90031'
        parent = fung.maybeTaxon(pid)
        if parent != None:
            return parent
        # Then try by name
        probe = fung.lookup(otaxon.parent.name)
        if probe != None and len(probe) == 1:
            return probe[0]
    return None

# Look for additional synonyms

def fix_synonyms():
    name_maps = [(source, source.makeNameMap()) for source in sources]
    syns = {}
    for taxon in fung:
        for (source, nmap) in name_maps:
            probe = source.idIndex[taxon.id]
            if probe != None:
                for name in nmap[probe]:
                    if name[0:len(taxon.name)] == taxon.name:
                        # Authority, subspecies, etc.
                        continue
                    if not name in syns:
                        already = fung.lookup(name)
                        if already == None:
                            syns[name] = taxon
    for name in syns:
        syns[name].synonym(name)
    print 'Recovered %s synonyms' % len(syns)


root = fix_root()
fix_parents(root)
fix_synonyms()

fung.dump("hackedfung/")

