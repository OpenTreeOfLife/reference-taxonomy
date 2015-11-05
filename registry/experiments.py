import sys
import csv

from registry_test import run_tests
# from tabulate import tabulate

def tabulate(table, headers, writer):
    def printrow(row):
        writer.writerow(row)
    printrow(headers)
    for row in table:
        printrow(row)

if __name__ == '__main__':

    if False:
        pairs = [('aster-ott28/', 'aster-ott29/'),
                 ('aster-ott29/', 'aster-synth4/'),]
        numbers = [2, 32]
    else:
        pairs = [('plants-ott28/', 'plants-ott29/'),
                 ('plants-ott28/', 'plants-synth3/'),
                 ('plants-ott29/', 'plants-synth4/'),
                 ('plants-synth3/', 'plants-synth4/'),]
        numbers = [2, 8, 32, 128]

    writer = csv.writer(sys.stdout)

    stats = ['unique',
             'ambiguous',
             'unsatisfiable',
             'sample error']

    headers = [' ', 'first', 'second'] + numbers

    tables = []

    for pair in pairs:
        reports = []
        for n in numbers:
            reports.append(run_tests(pair, n))

        pretable = map(list, zip(*reports))
        table = map(lambda (stat, row): [stat] + list(pair) + row, zip(stats, pretable))
        tables.append(table)

    print

    for table in tables:
        tabulate(table, headers, writer)
