# scp -p files:"files.opentreeoflife.org/synthesis/current/output/phylo_snapshot/*@*.json" synth-nexson-tmp/
# time bin/jython util/ids_in_synthesis.py --dir synth-nexson-tmp --outfile ids_in_synthesis.tsv

# was:
# time python util/ids_in_synthesis.py --dir ../files.opentreeoflife.org/preprocessed/v3.0/trees --outfile ids-in-synthesis.tsv

import re, argparse, os, csv

from org.opentreeoflife.taxa import Nexson

# preprocessed/v3.0/trees

def doit(trees_dir_name, out_file_name):
    tre_regex = re.compile('^(.*)_([0-9]+)\\.tre$')
    nexson_regex = re.compile('^(.+)@(.+)\\.json$')
    count = 0
    with open(out_file_name, 'w') as outfile:
        writer = csv.writer(outfile, delimiter='\t')
        for name in os.listdir(trees_dir_name):
            m = tre_regex.match(name)
            if m != None:
                study = m.group(1)
                tree = m.group(2)
                answer = ott_ids_in(os.path.join(trees_dir_name, name))
                for (id, name) in answer:
                    writer.writerow((id, study, name))
                    count += 1
            else:
                m = nexson_regex.match(name)
                if m != None:
                    study = m.group(1)
                    tree = m.group(2)
                    nexson = Nexson.load(os.path.join(trees_dir_name, name))
                    for otu in Nexson.getOtus(nexson).values():
                        id = otu.get('^ot:ottId')
                        if id != None:
                            writer.writerow((id, study))
                            count += 1
    print count

def ott_ids_in(tree_file_name):
    splitx = re.compile('[),]')
    regex1 = re.compile('[(]*([^(]*)_ott([0-9]+)')
    regex2 = re.compile('[(]*([0-9]+)')
    answer = []
    with open(tree_file_name, 'r') as infile:
        for line in infile:
            for chunk in splitx.split(line):
                m = regex1.match(chunk)
                if m != None:
                    id = m.group(2)
                    name = m.group(1)
                    answer.append((id, name))
                    print chunk, id, name
                    continue
                m2 = regex2.match(chunk)
                if m2 != None:
                    id = m2.group(1)
                    answer.append((id, None))
    print len(answer)
    return answer

if __name__ == '__main__':
    argparser = argparse.ArgumentParser(description='Get OTT ids out of newick files.')
    argparser.add_argument('--file', dest='file', help='look at one file')
    argparser.add_argument('--dir', dest='dir', help='look at a directory')
    argparser.add_argument('--outfile', dest='outfile', help='where to put ids')
    args = argparser.parse_args()
    if args.file != None:
        print len(ott_ids_in(args.file, args.outfile))
    if args.dir != None:
        doit(args.dir, args.outfile)

#doit(sys.argv[1])
