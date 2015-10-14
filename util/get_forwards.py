import csv, sys

forwards = {}

# Load legacy forwards

def load_forwards(filename):
    infile = open(filename, 'r')
    load_forwards_from_stream(infile)
    infile.close()

def load_forwards_from_stream(infile):
    reader = csv.reader(infile, delimiter='\t')
    idcolumn = 0
    repcolumn = 1
    for row in reader:
        if row[idcolumn].isdigit():
            forwards[row[idcolumn]] = row[repcolumn]
        else:
            idcolumn = row.index('id')
            repcolumn = row.index('replacement')

# load_forwards(sys.argv[1])
# load_forwards(sys.argv[2])

# want binary mode for output...

def dump_forwards_to_stream(outfile):
    writer = csv.writer(outfile, delimiter='\t')
    writer.writerow(('id', 'replacement'))

    for id in forwards:
        target = forwards[id]
        i = 0
        while target in forwards:
            i += 1
            if i > 100:
                print '** probably cycle', id
                break
            target = forwards[target]
        writer.writerow((id, target))

load_forwards_from_stream(sys.stdin)

outfile = open(sys.argv[1], 'wb')
dump_forwards_to_stream(outfile)
outfile.close()
