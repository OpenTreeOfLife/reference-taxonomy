<!-- follows table -->

Table 1: List of sources used in synthesis of Open Tree Taxonomy 3.0. See text for explanation of 'Open Tree curation' and 'separation taxa'. Detailed provenance information for each source can be found in the accompanying data package. Key to 'reasons' column: O = added in order to improve OTU coverage; P
= added in order to improve phylogenetic classification; T = added in
order to improve taxonomic coverage.

**Open Tree curation**  
It is not uncommon to have taxa as OTUs in
phylogenetic studies that do not occur in OTT.  This can be due to a
delay in curation by the source taxonomy, a delay in importing a fresh source
version into OTT, a morphological study containing otherwise unknown
species, or other causes.  To handle this situation, we developed
a user interface that allows curators to create new taxon records along with relevant
documentation (publications, databases, and so on).  New taxon records
are saved into a public GitHub repository, and these records are then
linked from the OTT taxonomy files and user interfaces so that
provenance is always available.

**Separation taxa**  
This is a small curated tree containing 31 major groups such
as Fungi, Metazoa, and Lepidoptera.  Its purpose is to assist
in separating homonyms.  If a node
is found in one of these separation groups, then it will not match a
node in a disjoint separation group, absent other evidence (details below).

**ARB-SILVA taxonomy processing**  
The terminal taxa in the SILVA taxonomy are algorithmically generated
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
vs. extant.  With the exception of limited data from WoRMS and Index Fungorum, this
information was not explicitly present in our other sources, so we imported IRMNG,
which logs the extinct / extant status of taxa.

As a secondary heuristic, records from GBIF that originate from
PaleoDB, and do not come from any other taxonomic source, are
annotated extinct.  This is not completely reliable, as some PaleoDB taxa are extant.

**Suppressed records**  
We suppress the following source taxonomy records:

* animals, plants, fungi in SILVA
* GBIF backbone records that originate from IRMNG (IRMNG is imported separately)
* GBIF backbone records that originate from IPNI
* GBIF backbone records whose taxonomic status is 'doubtful'
* GBIF backbone records for infraspecific taxa (subspecies, variety, form)
* IRMNG records whose nomenclatural status is 'nudum', 'invalid', or any of
  about 25 similar designations
* NCBI Taxonomy records that cannot correspond to taxa:
  those with names containing 'uncultured', 'unidentified', 'insertion
  sequences', or any of about 15 similar designations

The IPNI and IRMNG derived GBIF records are suppressed because they include many
invalid names.  We pick up most of the valid names from other sources,
such as the direct IRMNG import, so this is not a great loss.  Although
GBIF's original taxonomic sources indicate which names are known to be
invalid, this information is not provided
by the GBIF backbone.  Note that the GBIF backbone might
import the same name from more than one source, but its provenance
information only lists one of the sources.  We suppress the record if
that one source is IPNI or IRMNG.
