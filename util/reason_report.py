# Metrics for taxonomy writeup.

def new_report():
    return {}

# Call at end of assembly (before ids are assigned, if you like) to
# print the report.

def report(taxonomy):
    print
    report_part(taxonomy.alignmentSummary)
    print
    report_part(taxonomy.mergeSummary)
    print

def report_part(summary):
    total = 0
    fmt = '%7d  %s'
    data = []
    for label in summary:
        info = label_info.get(label, None)
        if info != None:
            (rank, newlabel) = info
            if newlabel == None: newlabel = label
        else:
            rank = 99
            newlabel = label
        data.append((rank, newlabel, summary[label]))
    for (rank, label, count) in sorted(data, key=lambda((r,l,c)): r):
        print fmt % (count, label)
        total += count
    print fmt % (total, 'total')

label_info = {"same/curated":          (02, 'curated alignment'),
              "same/by-division-name": (04, 'align to skeleton taxonomy'),
              "disjoint divisions":    (10, None),
              "disparate ranks":       (11, None),
              "by lineage":            (12, None),
              "overlapping membership":(14, None),
              "same division":         (16, None),
              "by name":               (18, None),
              "confirmed":             (30, None),
              "by elimination":        (32, None),
              "ambiguous tip":         (36, None),
              "ambiguous internal":    (38, None),
              "rejected":              (39, 'rejected'),
              "not-same/disjoint-xmrca": (40, 'target does not contain descendants'),
              "none":                  (43, 'not aligned'),

              "aligned/tip":            (46, 'aligned tip'),
              "aligned/internal":       (48, 'aligned internal node'),
              "new/tip":               (50, 'new tip'),
              "new/polysemy":          (52, 'new tip (polysemous)'),
              "new/graft":             (54, 'new internal node, part of graft'),
              "new/refinement":        (56, 'refinement'),
              "reject/merged":         (60, 'absorbed into larger taxon'),
              "reject/inconsistent":   (62, 'absorbed into larger taxon due to conflict'),
              "reject/wayward":        (64, 'source parent does not descend from nearest aligned'),
}

def sort_key(label):
    return label_info.get(label, label)
