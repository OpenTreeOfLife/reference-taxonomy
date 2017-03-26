# Jython script to build the "model village" taxonomy.

import os, sys
from org.opentreeoflife.taxa import Taxonomy
from org.opentreeoflife.taxa import TsvEdits, Addition, CSVReader, QualifiedId
from org.opentreeoflife.smasher import UnionTaxonomy
from java.io import FileReader
from claim import Has_child

sys.path.append("feed/eol/")
from get_eol_ids import get_eol_ids

def assemble():

    # Create model taxonomy
    tax = UnionTaxonomy.newTaxonomy('ott')
    tax.version = "this is a test"

    for name in ['Pentaphragma ellipticum',
                 'Lachnophyllum',
                 'Sipolisia',
                 'Cicerbita bourgaei',
                 'Adenophora triphylla',
                 'Artemisia vulgaris',
                 'Carlina libanotica',
    ]:
        tax.watch(name)

    # Establish homonym-resolution skeleton (not really used here)
    # skel = Taxonomy.getTaxonomy('tax/skel/', 'skel')
    # tax.setSkeleton(skel)


    # Add NCBI subset to the model taxonomy
    ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/', 'ncbi')
    # analyzeOTUs sets flags on questionable taxa ("unclassified" and so on)
    #  to allow the option of suppression downstream
    ncbi.analyzeOTUs()
    align_and_merge(tax.alignment(ncbi))

    # Add GBIF subset fo the model taxonomy
    gbif = Taxonomy.getTaxonomy('t/tax/gbif_aster/', 'gbif')
    gbif.smush()
    # analyzeMajorRankConflicts sets the "major_rank_conflict" flag when
    # intermediate ranks are missing (e.g. a family that's a child of a
    # class)
    gbif.analyzeMajorRankConflicts()
    align_and_merge(tax.alignment(gbif))

    # "Old" patch system with tab-delimited files
    TsvEdits.edit(tax, 't/edits/')

    claims = [
        Has_child('Asterales', 'Phellinaceae')
    ]

    for claim in claims:
        print claim.check(tax)

    gen = tax.newTaxon("Opentreeia", "genus", "data:testing")
    gen.take(tax.newTaxon("Opentreeia sp. C", "species", "data:testing"))
    gen.take(tax.newTaxon("Opentreeia sp. D", "species", "data:testing"))

    # Example of referring to a taxon
    fam = tax.maybeTaxon("Phellinaceae")

    if fam != None:
        # Example of how you might add a genus to the taxonomy
        fam.take(gen)

    # Test deletion feature
    sp = tax.newTaxon("Opentreeia sp. C", "species", "data:testing")
    gen.take(sp)
    sp.prune("aster.py")

    # tax.loadPreferredIds('ids-that-are-otus.tsv')

    additions_repo_path = 't/feed/amendments/amendments-0'
    new_taxa_path = 't/new_taxa'

    # Assign identifiers to the taxa in the model taxonomy.  Identifiers
    # assigned in the previous version are carried over to this version.
    ids = Taxonomy.getTaxonomy('t/tax/prev_aster/', 'ott')
    tax.carryOverIds(ids)    # performs alignment

    Addition.processAdditions(additions_repo_path, tax)

    if False:  # too slow for everyday testing purposes.
        print '-- Checking id list'
        assign_ids_from_list(tax, 'ott_id_list/by_qid.csv')

    tax.assignNewIds(new_taxa_path)

    get_eol_ids('feed/eol/out/eol-digest.csv', tax)

    # Write the model taxonomy out to a set of files
    tax.dump('t/tax/aster/', '\t|\t')

def assign_ids_from_list(tax, filename):
    count = 0
    if True:
        infile = FileReader(filename)
        r = CSVReader(infile)
        while True:
            row = r.readNext()
            if row == None: break
            [qid, ids] = row
            taxon = tax.lookupQid(QualifiedId(qid))
            if taxon != None:
                for id in ids.split(';'):
                    z = tax.lookupId(id)
                    if z == None:
                        taxon.taxonomy.addId(taxon, id)
                        count += 1
        infile.close()
    print '| Assigned %s ids from %s' % (count, filename)

def align_and_merge(alignment):
    ott = alignment.target
    ott.align(alignment)
    ott.merge(alignment)

assemble()
