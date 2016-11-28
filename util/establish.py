
# This is very similar to what processAdditionDocument (in Addition.java) has to do.

def establish(name, taxonomy, rank=None, parent=None, ancestor=None, division=None, ott_id=None, source=None):
    taxon = None
    anc = None
    placed = False
    if parent != None:
        anc = taxonomy.unique(parent)
        placed = True
        taxon2 = taxonomy.maybeTaxon(name, parent)
        if taxon2 != None:
            if taxon != None and taxon2 != taxon:
                print '** conflicting taxon determination (parent)', taxon, taxon2, parent
            else:
                taxon = taxon2
    if ancestor != None:
        if anc == None: anc = taxonomy.unique(ancestor)
        taxon2 = taxonomy.maybeTaxon(name, ancestor)
        if taxon2 != None:
            if taxon != None and taxon2 != taxon:
                print '** conflicting taxon determination (ancestor)', taxon, taxon2, ancestor
            else:
                taxon = taxon2
    if division != None:
        if anc == None: anc = taxonomy.unique(division)
        taxon2 = taxonomy.maybeTaxon(name, division)
        if taxon2 != None:
            if taxon != None and taxon2 != taxon:
                print '** conflicting taxon determination (division)', taxon, taxon2, division
            else:
                taxon = taxon2
    if ott_id != None:
        ott_id = str(ott_id)
        taxon2 = taxonomy.lookupId(ott_id)
        if taxon2 != None:
            if taxon != None and taxon2 != taxon:
                print '** conflicting taxon determination (id)', taxon, taxon2, ott_id
                taxon = None
            else:
                taxon = taxon2
        else:
            if taxon != None and taxon.id == None:
                taxon.setId(ott_id)
            else:
                taxon = None
    if taxon == None:
        taxon = taxonomy.newTaxon(name, rank, source)
        if anc != None:
            anc.take(taxon)
            if not placed:
                taxon.incertaeSedis()
        else:
            print '** no ancestor to attach new node to', name
        if ott_id != None:
            taxon.setId(ott_id)
    return taxon

