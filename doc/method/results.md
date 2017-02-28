# Results

The assembly method described above yields the reference taxonomy that
is used by the Open Tree of Life project.  The taxonomy itself, the
details of how the assembly method unrolls to generate the taxonomy,
and the degree to which the taxonomy meets the goals set out for it
are all of interest in assessing how, and how well, the method works.
We will address each of these three aspects of
the method in turn.

## Summary of Open Tree Taxonomy

The methods and results presented here are for version 3.0 of the Open Tree Taxonomy (which follows five previous releases using the automated assembly method). The taxonomy contains 3,592,827 total taxa, 3,271,403 tips and 276,375 internal nodes. 2,335,143 of the nodes have a Linnean binomial of the form Genus epithet. There are 2,027,352 synonym records and 8,154 homonyms (name-strings for which there are multiple nodes). A longer list of metrics is in supplementary data.
<!-- requires update -->

## Results of assembly procedure

As OTT is assembled, the alignment procedure processes every source node, either choosing an alignment target for it in the workspace based on the results of the heuristics, or leaving it unaligned. Figure 4 illustrates the results of the alignment phase. The presence of a single candidate node does not automatically align the two nodes - we still apply the heuristics to ensure a match (and occasionally reject the single candidate).  

We counted the frequency of success for each heuristic, i.e. the number of times that a particular heuristic was the one that accepted the winning candidate from among two or more candidates. Table X shows these results. Separation (do not align taxa in disjoint separation taxa; used first), Lineage (align taxa with shared lineage; used midway through) and Same-name-string (prefer candidates who primary name-string matches; used last) were by far the most frequent.

## Results of merge procedure

After assembly, the next step in the method is to merge the unaligned nodes into the workspace taxonomy. Of the 3,774,509 unaligned nodes, the vast majority (92%) are inserted into the workspace. Grafting accounts for 7% of the merge operations, and less than 1% are either absorptions or remain unmerged due to ambiguities.  
<!-- numbers require update-->

## Evaluating the taxonomy relative to requirements

The introduction sets out requirements for an Open Tree taxonomy.
How well are these requirements met?

### OTU coverage

We set out to cover the OTUs in the Open Tree corpus of phylogenetic trees. The
corpus contains published studies (each study with one or more phylogenetic
trees) that are manually uploaded and annotated by Open Tree curators. The user
interface contains tools that help curators map the OTUs in a study to taxa in
OTT. Of the 3,242 studies in the Open Tree database, 2,871 have at least 50% of
OTUs mapped to OTT.  (A lower overall mapping rate usually indicates incomplete
curation, not an inability to map to OTT.)  These 2,871 studies contain 538,728
OTUs, and curators have mapped 514,346 to OTT taxa, or 95.5%.

To assess the reason for the remaining 4.5% of OTUs being unmapped, we
investigated a random sample of ten OTUs.  In three cases, the label
was a genus name in OTT followed by "sp" (e.g. "_Euglena sp_"),
suggesting the curator's unwillingness to take the genus as the correct mapping for the OTU.
In the remaining seven cases, the taxon was
already in OTT, and additional curator effort would have found it.
Two of these were misspellings in the phylogeny source; one was
present under a slightly different name-string (subspecies in OTT,
species in study, the study reflecting a very recent
reclassification); and in the remaining four cases, either the taxon
was added to OTT after the study was curated, or the curation task was
left incomplete.
None in the sample reflected a coverage gap.

Of the 194,100 OTT records that are the targets of OTUs, 188,581
(97.2%) are represented in NCBI Taxonomy.  If the Open Tree project
had simply adopted NCBI Taxonomy instead of OTT, it would have met its
OTU coverage requirement (but not the taxonomic coverage requirement).
By comparison, GBIF covers 87.6%, and IRMNG covers 62.8%.
The high coverage by NCBI reflects a preference among our curators for molecular
phylogenetic evidence over other kinds.

<!--
[JAR: measure of how many mapped OTUs come from NCBI, i.e. how close NCBI
gets us to the mapping requirement: `../../bin/jython measure_coverage.py` =
NCBI 190084, OTT 195355 = 0.9730
-->

### Phylogeneticaly informed classification

Assessing whether OTT is more 'phylogenetically informed' than it
otherwise might be is difficult.  The phylogenetic quality of
the taxonomy is determined from by the taxonomic sources and their priority order.
We have relied on the project's curators, who have a strong
phylogenetic interest, to provide guidance on order.  Following are examples of
curator decision-making:

 * For microbes, SILVA is considered more phylogenetically sound than
   NCBI taxonomy, because the SILVA taxonomy is based on a recent
   comprehensive phylogenetic analysis.

 * Priority of NCBI Taxonomy over the GBIF backbone is suggested by
   NCBI's apparent interest in phylogeny, reflected in NCBI Taxonomy's
   much higher resolution, its inclusion of phylogenetically important
   non-Linnaean groups such as Eukaryota, and by its avoidance of known
   paraphyletic groupings such as Protozoa.

 * The Hibbett 2007 upper fungal taxonomy reflects, by construction,
   results from the most recent phylogenetic studies of Fungi.

Ideally we would have a measure of 'phylogenetically informed' that we
could use to compare OTT to other taxonomies, to test alternative
constructions of OTT, and to check the forward progress of OTT.  It is
not clear what one would use as a standard against which to judge.
The Open Tree project's supertree of life is a candidate, but is not
without issues (such as its own possible errors, and the fact that OTT
is itself use in construction the supertree).  Ensuring that
comparisons are meaningful, and comparable with one another, would be
a technical challenge.


### Taxonomic coverage

OTT has 2.3M binomials (presumptive valid species names), vs. 1.6M for
Catalogue of Life (CoL).  Since the GBIF source we used includes the Catalogue of Life [ref], OTT includes all species in CoL.The number is larger in part because the
combination of the inputs has greater coverage than CoL, and in part
because OTT has many names that are either not valid or not currently
accepted.

This level of coverage would seem to meet Open Tree's taxonomic
coverage requirement as well as any other available taxonomic source.

### Ongoing update

We aimed for a procedure that would allow simple re-building from sources, and also easy incorporation of new versions of sources. Re-building OTT version 3.0 from sources requires 17 minutes of real time. Our process currently runs on a machine with 16GB of memory; 8GB is not sufficient.

In the upgrade from 2.10 to 3.0, we added new versions of both NCBI
and GBIF. NCBI updates frequently, so changes tend to be manageable
and incorporating the new version was simple. In contrast, the
version from GBIF represented both a major change in their taxonomy
synthesis method. Many taxa disappeared, requiring changes to our ad
hoc patches during the normalization stage. In addition, the new
version of GBIF used a different taxonomy file format, which requires
extensive changes to our import code (most notably, handling taxon
name-strings that now included authority information).

We estimate the update from OTT 2.10 to OTT 3.0 required approximately three days of development time
related to source taxonomy changes. This was greater than previous updates due to the changes required to handle the major changes in GBIF content and format.  

### Open data

As the Open Tree project did not enter into any data use agreements
in order to obtain OTT's
taxonomic sources, it is not obliged to require any such agreement
from users of OTT.  (A data use agreement is sometimes called 'terms
of use'.  Legally, a DUA is a kind of contract.)
Therefore, users are not restricted in this way.
In addition, the taxonomy is not creative expression, so copyright
controls do not apply [ref Patterson2014].  Therefore, to the best of our knowledge, use of OTT is
unrestricted.
