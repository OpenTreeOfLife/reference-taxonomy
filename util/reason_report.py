
def new_report():
    return {}

def count(alignment, reason_report):
    for taxon in alignment.source.taxa():
        ans = alignment.getAnswer(taxon)
        if ans == None:
            reason = 'none'
        else:
            reason = ans.reason
        count = reason_report.get(reason, 0)
        reason_report[reason] = count + 1

def report(r):
    print
    total = 0
    fmt = '%7d  %s'
    data = []
    for label in r:
        info = label_info.get(label, None)
        if info != None:
            (rank, newlabel) = info
            if newlabel == None: newlabel = label
        else:
            rank = 99
            newlabel = label
        data.append((rank, newlabel, r[label]))
    for (rank, label, count) in sorted(data, key=lambda((r,l,c)): r):
        print fmt % (count, label)
        total += count
    print fmt % (total, 'total')
    print

label_info = {"same/curated":          (02, 'curated alignment'),
              "same/by-division-name": (04, 'align to skeleton taxonomy'),
              "disjoint divisions":    (10, None),
              "by lineage":            (12, None),
              "overlapping membership":(14, None),
              "same division":         (16, None),
              "by name":               (18, None),
              "confirmed":             (30, None),
              "by elimination":        (32, None),
              "ambiguous tip":         (36, None),
              "ambiguous internal":    (38, None),
              "new/tip":               (50, 'new tip'),
              "new/polysemy":          (52, 'new tip (polysemous)'),
              "new/graft":             (54, 'new internal node, part of graft'),
              "new/refinement":        (56, 'refinement'),
              "reject/merged":         (60, 'merge'),
              "reject/inconsistent":   (62, 'conflict'),
}

def sort_key(label):
    return label_info.get(label, label)
