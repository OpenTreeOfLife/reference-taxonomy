# Convert the original Ruggiero taxonomy file (CSV format) to Open
# Tree exchange format

import sys, os, csv, codecs

taxondict = {}

infilename = sys.argv[1]
outdir = sys.argv[2]

if not os.path.isdir(outdir):
    os.makedirs(outdir)

taxfile = codecs.open(os.path.join(outdir, 'taxonomy.tsv'), 'w', 'utf-8')
synfile = codecs.open(os.path.join(outdir, 'synonyms.tsv'), 'w', 'utf-8')

life_id = 0

def process():
    with open(infilename, 'r') as infile:
        emit("uid", "parent_uid", "name", "rank")
        emit(life_id, "", "life", "")
        emitsyn("name", "uid")
        id = 0
        lineage = None          # list of ids
        reader = csv.reader(infile)
        for row in reader:
            # id is line number in source file
            id += 1
            if lineage == None:
                lineage = [None for x in row]
            col_num = 0
            name = ''
            for field in row:
                field = field.strip()
                if field != '':
                    name = field.decode('utf-8')
                    break
                col_num += 1
            if name == '':
                # last row of file is empty
                continue
            parts = name.split(' ', 1)
            rank = parts[0].lower()
            name = parts[1]
            if ' [=' in name:
                names = name.split(' [=')
                if not name.startswith('N.N.'):
                    name = names[0]
                syn = names[1]
                if syn.isupper():
                    syn = syn.capitalize()
                emitsyn(syn.replace(']', ''), id)
            if '  [' in name:
                names = name.split('  [')
                if not name.startswith('N.N.'):
                    name = names[0]
            if ' (e.g.' in name and not 'N.N.' in name:
                names = name.split(' (e.g.')
                name = names[0]
            if not name[0].isalpha() and not name[-1].isalpha():
                name = name[1:-1]
            if name[1].isupper():
                name = name.capitalize()
            if name == 'N.N.':
                name = ''
            if col_num > 0:
                q = col_num - 1
                # ugh
                (parentid, parentname) = (-1, '???')
                while q >= 0:
                    l = lineage[q]
                    if l != None:
                        (parentid, parentname) = l
                        break
                    q -= 1
            else:
                parentid = life_id
                parentname = 'life'
            if name != parentname:
                # Suppress parent/child homonyms
                emit(id, parentid, name, rank)
                lineage[col_num] = (id, name)
                for i in range(col_num+1, len(lineage)):
                    lineage[i] = None

def emit(id, parentid, name, rank):
    taxfile.write("%s\t%s\t%s\t%s\n"%(id, parentid, name.strip(), rank))

def emitsyn(syn, id):
    synfile.write("%s\t%s\n"%(syn.strip(),id))

process()
taxfile.close()
synfile.close()
