
# Name
# Release data / download date / version
# Number of taxon records in source (terminal only?)
# Number of taxon records used in Open Tree
# Number of taxon records used in Open Tree that are binomials ?
# Number of synonym records used in Open Tree
# Priority
# Reference number(s) - full reference in article's reference list
# Maximum depth

header = ['name', 'reference', 'version', 'taxa', 'synonyms', 'priority']

table = [
    {'name': 'ARB-SILVA',
     'reference': '10.1093/nar/gks1219',
     'version' : '2016-06-29',
     'priority': 1,
     'taxa': 78687,
     'synonyms': 0},
    {'name': 'Hibbett 2007',
     'reference': '10.6084/m9.figshare.1465038.v4',
     'version' : 'v4',
     'priority': 2,
     'taxa': 227,
     'synonyms': 0},
    {'name': 'Index Fungorum',
     'reference': 'x',
     'version' : '2014-04-07',
     'priority': 3,
     'taxa': 284973,
     'synonyms': 157734},
    {'name': 'Sch&auml;ferhoff 2010',
     'reference': 'x',
     'version' : '2013-11-23',
     'priority': 4,
     'taxa': 119,
     'synonyms': 0},
    {'name': 'WoRMS',
     'reference': 'x',
     'version' : '2015-10-01',
     'priority': 5,
     'taxa': 330412,
     'synonyms': 223196},
    {'name': 'NCBI',
     'reference': 'x',
     'version' : '2016-06-29',
     'priority': 6,
     # These numbers come from running process_ncbi_taxonomy.py.
     # Need to review - names.dmp seems to have 2207998 rows, much bigger
     # (some of those will be authorities, etc.)
     # (+ 1488029 404511)
     'taxa': 1488029,   # Boils down to 1488019
     'synonyms': 719526},  # Careful about how to count.
    {'name': 'GBIF',
     'reference': 'x',
     'version' : '2013-07-02',
     'priority': 7,
     # These numbers come from running process_gbif_taxonomy.py
     'taxa': 3273321,     # Boils down to 1863834
     'synonyms': 1143026},  # Boils down to 879745
    {'name': 'IRMNG',
     'reference': 'x',
     'version' : '2014-01-31',
     'priority': 8,
     # These numbers come from running process_irmng.py, I think
     'taxa': 1706655,    # Boils down to 1685134
     'synonyms': 685983} # Boils down to 659851
]

def cell(val):
    print '    <td>'
    print '   ', val
    print '    </td>'


def do_row(cells):
    print '  <tr>'
    for val in cells:
        cell(val)
    print '  </tr>'


print '### (Table 1)'

print '<table>'
do_row(header)
for row in table:
    do_row([row['name'], row['reference'], row['version'], row['taxa'], row['synonyms'], row['priority']])
print '</table>'

"""
The root clade could be a column in the table?  No.

Maybe put number of binomials in the table?

    silva,13953
    if,237482
    worms,258378
    ncbi,360455
    gbif,1629523
    irmng,1111550
    addition,15

Maybe some measure of resolution, like maximum depth?

end table]
"""
