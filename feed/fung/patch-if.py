
# Version 4 of the Index Fungorum conversion is missing many parent
# pointers.  This script recovers them from versions 1, 2, and 3
# and writes out a revised IF taxonomy.

# Run this with:  python feed/fung/patch-if.py

import csv, os

rank_order = {}
i = 0
for rank in ['kingdom', 'phylum', 'subphylum', 'class', 'subclass', 'order', 'family', 'genus', 'species']:
    rank_order[rank] = i
    i += 1

all_ranks = {}

def get_taxonomy(dirname, tag):
    return Taxonomy(dirname)

def fixit(ofung, use_names):
    count = 0
    added = 0
    for id in ofung.taxa:
        otaxon = ofung.taxa[id]
        taxon = fung.maybeTaxon(otaxon.id)
        taxon_by_name = None
        if otaxon.name in fung.taxa_by_name:
            taxon_by_name = fung.taxa_by_name[otaxon.name]
        danger = False

        if taxon == None:
            # Might be a new one, but don't create homonyms
            if taxon_by_name == True:
                # We have a homonym already, and no way to match.  Skip it.
                continue

            if taxon_by_name == None:
                # Really a new one.  Create it
                copy_taxon(otaxon, fung)
                added += 1
                continue

            if not use_names:
                continue

            # Don't create a homonym, but do transfer information over from taxon 
            # that has same name

            if (int(taxon_by_name.id) - int(otaxon.id)) > 2:
                danger = True
            taxon = taxon_by_name

        if taxon.flags == '':
            taxon.flags = otaxon.flags

        if taxon.parent_id == otaxon.parent_id:
            continue   # No new information here

        # Merge parent pointer from the supplementary record into the main record.
        if (taxon.parent_id == None or
            not taxon.parent_id in fung.taxa):
            taxon.parent_id = otaxon.parent_id
            if danger:
                print "Targeting taxon %s with id %s (id in supplemental taxonomy is %s)"%(taxon.name, taxon.id, otaxon.id)
            count += 1

    print 'Added', added, '/ fixed', count

def copy_taxon(otaxon, fung):
    if otaxon.name in fung.taxa_by_name:
        # Do not create homonyms
        return
    taxon = fung.new_taxon(otaxon.name, otaxon.rank, None)
    taxon.id = otaxon.id
    fung.enter(taxon)
    fung.taxa[taxon.id] = taxon
    taxon.parent_id = otaxon.parent_id
    taxon.flags = otaxon.flags

class Taxonomy:
    def __init__(self, dirname):
        print 'Loading', dirname
        self.taxa = {}
        self.taxa_by_name = {}
        infile = open(dirname + "taxonomy.tsv")
        for line in infile:
            fields = (line.strip() + '\t\t|\tx').split('\t|\t')
            id = fields[0]
            parent_id = fields[1]
            name = fields[2]
            rank = fields[3].strip()
            flags = fields[4].strip()
            if id == 'uid': continue
            if rank == '' or rank == 'no rank': rank = None
            rec = Taxon(id, parent_id, name, rank, flags, self)
            self.enter(rec)
        infile.close()

    def enter(self, rec):
        self.taxa[rec.id] = rec
        if rec.name in self.taxa_by_name:
            self.taxa_by_name[rec.name] = True
        else:
            self.taxa_by_name[rec.name] = rec


    def dump(self, dirname):

        # Decide which records to keep
        keep = {}
        for rec in fung.taxa.values():
            if rec.parent_id in fung.taxa:
                keep[rec.id] = True
                keep[rec.parent_id] = True
        print 'Flushing', len(fung.taxa) - len(keep)

        if not os.path.exists(dirname):
            os.makedirs(dirname)
        outfile = open(dirname + "taxonomy.tsv", 'w')
        writer = csv.writer(outfile, delimiter='\t')
        writer.writerow(('uid', 'parent_uid', 'name', 'rank', 'flags'))
        missing = 0
        badrank = 0
        for rec in sorted(self.taxa.values(), key=lambda rec:int(rec.id)):
            if rec.id in keep:
                if not rec.parent_id in keep:
                    if rec.name == 'Fungi':
                        rec.parent_id = ''
                    else:
                        rec.parent_id = 'not found'
                        missing += 1
                parent = rec.getParent()
                if (parent != None and
                    parent.rank in rank_order and
                    rec.rank in rank_order and
                    rank_order[parent.rank] >= rank_order[rec.rank]):
                    print ('Detaching %s %s %s from %s %s %s' %
                           (rec.name, rec.id, rec.rank, parent.name, parent.id, parent.rank))
                    badrank += 1
                    rec.parent_id = None
                rec.dump(writer)
        outfile.close()
        print 'Missing', missing, 'parents /', badrank, 'upranks'

    def maybeTaxon(self, id_or_name):
        if id_or_name in self.taxa:
            return self.taxa[id_or_name]
        if id_or_name in self.taxa_by_name:
            rec = self.taxa_by_name[id_or_name]
            if rec == True:
                return None
            else:
                return rec

    def new_taxon(self, name, rank, sources):
        tax = Taxon(None, '', name, rank, '', self)
        return tax


class Taxon:
    def __init__(self, id, parent_id, name, rank, flags, taxonomy):
        if parent_id == '': parent_id = None
        self.id = id
        self.parent_id = parent_id
        self.name = name
        self.rank = rank
        self.flags = flags
        self.taxonomy = taxonomy
        if rank != None:
            all_ranks[rank] = True

    def getParent(self):
        if self.parent_id in self.taxonomy.taxa:
            return self.taxonomy.taxa[self.parent_id]
        else:
            return None

    def dump(self, writer):
        parent_id = self.parent_id
        if parent_id == None: parent_id = ''
        if self.rank == None: self.rank = 'no rank'
        writer.writerow((self.id, parent_id, self.name, self.rank, self.flags))


fung  = get_taxonomy('feed/fung/if.4/', 'if')

fungi = fung.maybeTaxon('90156')
if fungi == None:
    fungi = fung.new_taxon('Fungi', 'kingdom', 'if:90156')
    fungi.id = '90156'   #kludge
    fung.enter(fungi)

ascomycota = fung.maybeTaxon('90031')
if ascomycota == None:
    ascomycota = fung.new_taxon('Ascomycota', 'phylum', 'if:90031')
    ascomycota.id = '90031'
    fung.enter(ascomycota)
ascomycota.parent_id = fungi.id

tina = fung.maybeTaxon('501470')
tina.parent_id = ascomycota.id

#                Saccharomycetes class 90791
#  is in         Saccharomycotina subphylum 501470
#  which is in   Ascomycota phylum 90031
sacc = fung.maybeTaxon('90791')
if sacc == None:
    sacc = fung.new_taxon('Saccharomycetes', 'class', 'if:90791')
    sacc.id = '90791'
    fung.enter(sacc)
sacc.parent_id = tina.id

print 'Testing:' , fung.maybeTaxon('Saccharomycetes')

fixit(get_taxonomy('feed/fung/if.3/', 'if'), True)
fixit(get_taxonomy('feed/fung/if.2/', 'if'), True)
fixit(get_taxonomy('feed/fung/if.1/', 'if'), False)

sacc.flags = ''

destdir = 'tax/hackedfung/'
fung.dump(destdir)

print all_ranks.keys()

print "Don't forget to cp -p feed/fung/if.4/synonyms.tsv %ssynonyms.tsv" % destdir


# --- End of file ---

def oldmethod(ofung):
    for id in fung.taxa:
        taxon = fung.taxa[id]
        danger = False
        if not (taxon in changes) and taxon.getParent() == None:
            # Missing a parent pointer.
            otaxon = ofung.maybeTaxon(taxon.id)
            if otaxon == None:
                otaxon = ofung.maybeTaxon(taxon.name)
                if otaxon != None and abs(int(otaxon.id) - int(taxon.id)) > 2:
                    danger = True
            if otaxon != None and otaxon.getParent() != None:
                # Get parent id from supplementary taxonomy file
                moredanger = False
                oparent = otaxon.getParent()
                if oparent.id == '90156':
                    parent = fungi
                elif oparent.id == '90031':
                    parent = ascomycota
                else:
                    parent = fung.maybeTaxon(oparent.id)
                    if parent == None:
                        parent = fung.maybeTaxon(oparent.name)
                        if parent != None and abs(int(oparent.id) - int(parent.id)) > 2:
                            moredanger = True
                if parent != None:
                    changes[taxon] = parent
                    if danger:
                        print "%s: Used old taxon %s with id %s (wanted %s)"%(taxon.id, otaxon.name, otaxon.id, taxon.id)
                    if moredanger:
                        print "%s: Used new parent %s with id %s (wanted %s)"%(taxon.id, parent.name, parent.id, oparent.id)
