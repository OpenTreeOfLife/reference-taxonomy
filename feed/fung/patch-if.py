
# Version 4 of the Index Fungorum conversion is missing many parent
# pointers.  This script recovers them from versions 1, 2, and 3
# and writes out a revised IF taxonomy.

# Run this with smash --jython feed/if/patch-if.py where 
# alias smash='java -classpath ".:lib/*" -Xmx10g org.opentreeoflife.smasher.Smasher'

from org.opentreeoflife.smasher import Taxonomy

fung  = Taxonomy.getTaxonomy('tax/if.4/', 'if')

fungi = fung.newTaxon('Fungi', 'kingdom', 'if:90156')
fungi.id = '90156'   #kludge

ascomycota = fung.newTaxon('Ascomycota', 'phylum', 'if:90031')
ascomycota.id = '90031'

changes = {}
losers = {}

def fixit(ofung):
	for taxon in fung:
		danger = False
		if not (taxon in changes) and taxon.getParent() == None:
			otaxon = ofung.maybeTaxon(taxon.id)
			if otaxon == None:
				otaxon = ofung.maybeTaxon(taxon.name)
				if otaxon != None and abs(int(otaxon.id) - int(taxon.id)) > 2:
					danger = True
			if otaxon != None and otaxon.parent != None:
				moredanger = False
				oparent = otaxon.parent
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
				else:
					losers[oparent.id] = (otaxon, taxon)
	print "Fixed", len(changes)

fixit(Taxonomy.getTaxonomy('tax/if.3/', 'if'))
fixit(Taxonomy.getTaxonomy('tax/if.2/', 'if'))
fixit(Taxonomy.getTaxonomy('tax/if.1/', 'if'))

for taxon in changes:
	taxon.changeParent(changes[taxon])
for id in losers:
   	(otaxon, taxon) = losers[id]
   	print "%s: Lost parent %s of %s"%(taxon.id, otaxon.parent.name, taxon.name)

fung.dump("tax/hackedfung/")

