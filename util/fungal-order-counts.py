# One-off script prepared to provide data to David Hibbett and Romina Gazis.
# Lists numbers of species in each fungal order.

from org.opentreeoflife.smasher import Taxonomy
import csv, sys
from taxonomies import load_fung, load_ncbi, load_gbif, load_irmng

taxonomies = [('fung', load_fung(), 'Index Fungorum'),
              ('ncbi', load_ncbi(), 'NCBI'),
              ('gbif', load_gbif(), 'GBIF'),
              ('irmng', load_irmng(), 'IRMNG'),
              ('ott', Taxonomy.getTaxonomy('tax/ott/'), 'OTT 2.9'),
          ]

def main():
    infile = open('order-counts-orders.csv', 'r')
    reader = csv.reader(infile)
    reader.next()   #header row
    taxa = ['Fungi']
    for tuple in reader:
        taxa.append(tuple[0])
    infile.close()

    write_counts(taxa)

def write_counts(taxa):
    outfile = open('order-counts.csv', 'w')
    writer = csv.writer(outfile)
    header = ['order']
    for (name, taxonomy, label) in taxonomies:
        header += [label + ' bin', label + ' sp', label + ' tip']
    writer.writerow(header)

    for taxon in taxa:
        row = [taxon]
        for (name, taxonomy, label) in taxonomies:
            row += [do_count(taxonomy, taxon, lambda taxon: taxon.binomialCount()),
                    do_count(taxonomy, taxon, lambda taxon: taxon.speciesCount()),         
                    do_count(taxonomy, taxon, lambda taxon: taxon.tipCount())]
        writer.writerow(row)
    outfile.close()

def do_count(taxonomy, name, fun):
    taxon = taxonomy.maybeTaxon(name, 'Fungi')
    if taxon == None:
        return ''
    else:
        return fun(taxon)

main()
