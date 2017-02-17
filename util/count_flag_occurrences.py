import sys


def insert_into_dict(flag,dict):
    if flag in flagdict:
        flagdict[flag] += 1
    else:
        flagdict[flag] = 1

if __name__ == '__main__':
    taxonomy_file = sys.argv[1]
    counter = 0
    flagdict = {}
    with open(taxonomy_file, 'r') as infile:
        # next(infile) # skip first line
        for line in infile:
            cols = line.split('\t|\t')
            flagcol = cols[6]
            if (counter==0):
                assert flagcol=='flags'
            else:
                if len(flagcol)>0:
                    flags=flagcol.split(',')
                    for i in flags:
                        # insert into flag map
                        insert_into_dict(i,flagdict)
            counter += 1
            # if counter > 100:
            #     break
    print "flag\tcount"
    for k in flagdict:
        print "{flag}\t{count}".format(flag=k,count=flagdict[k])
