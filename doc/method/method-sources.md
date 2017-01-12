<!-- follows table -->

Key to 'reasons' column: O = added in order to improve OTU coverage; P
= added in order to improve phylogenetic classification; T = added in
order to improve taxonomic coverage.

[NMF asks: What do the source taxonomies look like ("sampling", in
terms of depth (# of levels, breadth (overlap? are they
"comprehensive?"), homogeneity)?]

[add a column to the table for number of binomials?]

**Open Tree curated taxa**  
It is not uncommon to have taxa as OTUs in
phylogenetic studies that do not occur in OTT.  This can be due to a
delay in curation by NCBI itself, a delay in importing a fresh NCBI
version into OTT, a morphological study with otherwise unknown
species, or other causes.  To handle this situation, we developed
a user interface that allows curators to create new taxon records along with relevant
documentation (publications, databases, and so on).  New taxon records
are saved into a specific GitHub repository, and these records are then
linked from the OTT taxonomy files and user interfaces so that
provenance is always available.

**Barrier taxonomy**  
This is a small curated tree containing 27 major groups such
as animals, plants, and fungi.  Its purpose is to establish barriers
between taxa as an aid to separating homonyms.  If a node
is found in one of these barrier groups, then it will not match a
node in a disjoint barrier group, even if the name matches.

**ARB-SILVA taxonomy processing**  
The terminal taxa in the SILVA taxonomy are algorithmically-generated
clusters of RNA sequences derived from GenBank records.  Rather than
incorporate these idiosyncratic, fine-grained groupings into OTT, we
use sequence record metadata to group the clusters into larger groups
corresponding to NCBI taxa, and include those larger groups in OTT.

We excluded SILVA's plant, animal, and fungal branches from OTT
because these groups are well covered by other sources and poorly
represented in SILVA.  For example, SILVA has only 299 taxa in
Metazoa, compared with over 500,000 taxa under Metazoa in NCBI Taxonomy.

**Extinct / extant annotations**  
Curators requested information about whether taxa were extinct
vs. extant.  (See below for the reason this was so important.) This
information was not explicitly present in any of our other sources, so we imported IRMNG,
which logs the extinct / extant status of taxa.

As a secondary heuristic, records from GBIF that originate from
PaleoDB, and do not come from any other taxonomic source, are
annotated extinct.  This is not completely reliable, as some PaleoDB taxa are extant.

**Suppressed records**  
Not all source taxonomy records are used.  The following are ignored:

* GBIF backbone records that originate from IRMNG (IRMNG is imported separately)
* GBIF backbone records that originate from IPNI
* GBIF backbone records whose taxonomic status is 'doubtful'
* GBIF backbone records for infraspecific taxa (subspecies, variety, form)
* IRMNG records whose nomenclatural status is 'nudum', 'invalid', or any of
  about 25 similar designations
* NCBI Taxonomy records that cannot correspond to natural groupings:
  those with names containing 'uncultured', 'unidentified', 'insertion
  sequences', or any of about 15 similar designations