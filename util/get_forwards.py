
import csv, sys

reader = csv.reader(sys.stdin, delimiter='\t')

header = reader.next()
idcolumn = header.index('id')
repcolumn = header.index('replacement')

writer = csv.writer(sys.stdout, delimiter='\t')

writer.writerow(('id', 'replacement'))

for row in reader:
    writer.writerow((row[idcolumn], row[repcolumn]))
