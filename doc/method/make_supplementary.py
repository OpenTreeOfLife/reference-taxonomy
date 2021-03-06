# Metrics for taxonomy writeup.

# Command line arguments:
#   - directory containing the 4 json/csv summary files
#   - directory where .csv are to be stored

import sys, os, json, csv

# summaries_path is the directory containing the JSON and CSV summary files
#   (alignment_summary.json and 3 or so others)
# metrics_path is where to put the markdown

def make_supplementary(cache_path, supp_path):
    cpath = os.path.join(cache_path, 'taxonomy_summary.json')
    with open(cpath, 'r') as infile:
        metrics = json.load(infile)
        metrics['branching_factor'] = \
           "%.2f" % ((metrics['node_count']*1.0)/metrics['internal_node_count'])
        dump_metrics(metrics,
                     metrics_label_info,
                     None,
                     os.path.join(supp_path, 'taxonomy_stats.csv'))

    # Fate under alignment

    apath = os.path.join(cache_path, 'alignment_summary.json')
    with open(apath, 'r') as infile:
        summary = json.load(infile)
        (h_table, h_count) = prepare_table(summary, heuristic_label_info, None)
        h_total = table_total(h_table)
        print >>sys.stderr, '| total choices made:', h_total
        summary['by-heuristic'] = h_total
        dump_metrics(summary,
                    alignment_label_info,
                    "Total number of source nodes (other than separation taxonomy).",
                     os.path.join(supp_path, 'alignment_stats.csv'))

        # Use of heuristics in choice-making

        dump_csv(h_table, os.path.join(supp_path, 'choice_stats.csv'))

    # Fate under merge

    gpath = os.path.join(cache_path, 'merge_summary.json')
    with open(gpath, 'r') as infile:
        s = json.load(infile)
        dump_metrics(s,
                    merge_label_info,
                    "Total number of source nodes.",
                    os.path.join(supp_path, 'merge_stats.csv'))

    # Contributions from the various sources

    cpath = os.path.join(cache_path, 'contributions_summary.csv')
    with open(cpath, 'r') as infile:
        rows = []
        for row in csv.reader(infile):
            rows.append(row)
        dump_csv(rows, os.path.join(supp_path, 'contributions_stats.csv'))


# summary is the report json as generated by smasher

def dump_metrics(summary, label_info, total_description, path):
    (table, total) = prepare_table(summary, label_info, total_description)
    dump_csv(table, path)

# returns (list of (rank, label, count), total)

def prepare_table(summary, label_info, total_description):
    for key in summary:
        if not key in all_keys:
            print >>sys.stderr, '** unrecognized key: %s %s' % (key, summary[key])
    data = join(summary, label_info)
    table = [(count, label) for (rank, label, count) in
             sorted(data, key=lambda((r,l,c)): r)]
    total = 0
    if total_description != None:
        return include_total(table, total_description)
    return (table, total)

def include_total(table, total_description):
    total = table_total(table)
    table = [(total, total_description)] + table
    return (table, total)

def table_total(table):
    return sum([count for (count, label) in table])

def join(summary, label_info):
    data = []
    for key in label_info:
        count = summary.get(key, 0)
        (rank, label) = label_info[key]
        if label == None: label = key
        data.append((rank, label, count))
    return data

def dump_csv(table, path):
    with open(path, 'w') as file:
        print 'Writing', path
        writer = csv.writer(file)
        for row in table:
            if not isinstance(row[0], int) or row[0] > 0:
                writer.writerow(row)

metrics_label_info = {
    'node_count':
     (0, """Number of taxon records (nodes)."""),
    'synonym_count':
     (2, """Number of synonym records."""),
    'tip_count':
     (4, """Number of tips."""),
    'internal_node_count':
     (3, """Number of internal (non-tip) nodes."""),
    'species':
     (5, """Number of nodes with a known rank of 'species'."""),
    'binomials':
     (6, """Number of nodes whose name-string has the form of a Linnaean binomial <em>Genus epithet</em>.  
This measurement serves a proxy for the number of described species in the taxonomy, as
opposed to ad hoc names (e.g. <em>bacterium 7A7</em>) assigned by NCBI."""),
    'homonym_count':
     (7, """Number of homonym name-strings, i.e. those belonging to more than one node."""),
    'species_homonym_count':
     (8, """Number of homonym name-strings where the nodes have species rank."""),
    'genus_homonym_count':
     (9, """Number of homonym name-strings where the nodes have genus rank."""),
    'max_depth':
     (10, """Maximum nesting depth of any node in the taxonomy."""),
    'max_children':
    (11, """Maximum number of children for any node in the taxonomy."""),
    'branching_factor':
    (20, """Branching factor (average number of children per internal node)."""),

    'absorbed':
     (70, """Record-keeping placeholders for source taxa that were absorbed into a larger taxon."""),
    'incertae_sedis_count':
     (72, """Number of nodes marked <em>incertae sedis</em> or equivalent."""),
    'extinct_count':
     (74, """Number of nodes annotated as being for an extinct taxon."""),
    'infraspecific_count':
     (76, """Number of nodes below the rank of species (e.g. subspecies, variety)."""),
    'barren':
     (78, """Number of nodes above the rank of species that subtend no node of rank species."""),
    'none of the above':
    (99, "UNCATEGORIZED")
}

alignment_label_info = {
    "same/curated":
    (01, """Alignment particularly established by a curator, usually to
            repair a mistake made by automatic alignment."""),
    "same/by-division-name":
    (03, """Aligned to separation taxonomy, to establish locations of separation taxa in the source taxonomy."""),
    "none":
    (05, """There were no candidates for this source taxon."""),
    "by-heuristic":
    (10, """A choice was made between two or more candidates using heuristics (for breakdown see below)."""),
    "confirmed":
    (30, """There was only a single candidate, and it was confirmed
            by a 'yes' answer from one of the heuristics."""),
    "by elimination":
    (32, """There was only a single candidate, and it was not confirmed by any
heuristic (the match involved a synonym)."""),
    "ambiguous tip":
    (36, """The heuristics were unable to choose from among multiple
candidates; no alignment is recorded for the source node, which is a tip."""),
    "ambiguous internal":
    (38, """The heuristics were unable to choose from among multiple
candidates; no alignment is recorded for the source node, which is internal."""),
    "rejected":
    (39, """Every candidate was rejected by a 'no' from one of the heuristics."""),
}

# names to agree with those in method-details.md

heuristic_label_info = {
    "disjoint divisions":    (10, 'Separation'),
    "disparate ranks":       (11, "Disparate ranks"),
    "by lineage":            (12, 'Lineage'),
    "overlapping membership":(14, 'Overlap'),
    "same-division-weak":    (16, 'Proximity'),
    "by name":               (18, 'Same name-string'),
}

merge_label_info = {
    "mapped/tip":
    (66, """Source node (a tip) is aligned to a workspace node.  No action, except that the 
source's extinctness flag, if any, is copied to the workspace node."""), #was aligned/tip
    "mapped/internal":
    (68, """Similarly, internal node"""), #was aligned/internal

    "new/graft":
    (70, """Root of unaligned source subtree copied (grafted) to existing workspace node."""),

    "new/in-graft":
    (70, """Part of an unaligned source subtree copied to workspace."""),

    # "graft/tip":
    # (71, """Source tip internal to a graft."""),
    # "graft/polysemy":
    # (72, """Similarly, but there is already another node with this name-string in the workspace."""),
    # "graft/internal":
    # (74, """Internal node internal to a graft."""),

    "new/refinement":
    (76, 'Source node inserted into the hierarchy above one workspace node and below another.'),
    "reject/absorbed":
    (80, 'Source node absorbed into larger workspace taxon; no conflict with classification.'),
    "reject/inconsistent":
    (82, 'Source node absorbed into larger workspace taxon; conflicts with classification.'),
    "ambiguous/redundant":
    (86, 'Ambiguous source node, not aligned and not merged.')
}

all_keys = {}
for i in [metrics_label_info, heuristic_label_info, alignment_label_info, merge_label_info]:
    for key in i:
        all_keys[key] = True

def sort_key(label):
    return label_info.get(label, label)

# Single argument is directory containing the 4 json/csv summary files.

if __name__ == '__main__':
    make_supplementary(sys.argv[1], sys.argv[2])
