# Method

In the following, any definite claims or measurements apply to the
Open Tree taxonomy (version 2.11), but keep in mind that the taxonomy
is constantly evolving as sources are updated, assembly methods
improve, and errors are fixed.

## Taxonomic sources

We build the taxonomy from nine sources. Some of these sources are from
taxonomy projects, while others were manually assembled based on recent
publications.
OTT assembly is dependent on the input order of the sources - higher ranked inputs take priority over lower ranked inputs.

[see table at the end of this section]


The choice and ranking of input taxonomies are driven by the three
requirements listed in the introduction: 1) OTU coverage (mapping OTUs
from phylogenies to taxa in the taxonomy); 2) a phylogenetically-informed
backbone hierarchy; and 3) taxonomic coverage
of the tree of life beyond the OTUs present in the input trees.

As an open science project, Open Tree of Life only uses information
sources that are not subject to terms of use (data use agreement).
This policy decision imposes an additional requirement on the
taxonomy, ruling out some sources that would otherwise be a good fit
for the project.


Details of sources: [in what order? the rank order is in the table,
use rank order or something different?  historical? by number of
taxa?]

**Barrier taxonomy**  
This is a small tree, hand curated, that contains 27 major groups such
as animals, plants, and fungi.  Its purpose is to establish barriers
between major groups, as an aid to separating polysemies.  If a genus
name is found in one of these groups, then it cannot be unified with a
genus of the same name in a disjoint group.

**NCBI taxonomy**  
The first requirement of the taxonomy is to align OTUs across
phylogenetic studies.  This need is largely met by using the NCBI
taxonomy, since (1) most modern phylogenetic studies are molecular,
(2) publishers require molecular sequences used in studies to be
deposited in GenBank, and (3) every GenBank deposit is annotated with
a taxon in the NCBI taxonomy.  NCBI taxonomy also tends to be more
phylogenetically informed than other taxonomies (see Results, below),
which makes it a good backbone for Open Tree's purposes. The NCBI
taxonomy therefore forms the nucleus of OTT.

However, since NCBI taxonomy only includes taxa that have sequence
information, it is relatively small, containing only 360455 records
with standard binomial species
names. It therefore does not meet the taxonomic coverage requirement.

The particular version of NCBI taxonomy used in OTT 2.10 was
downloaded from NCBI on [date].  [maybe put dates in table??]


**GBIF backbone taxonomy**  
The GBIF backbone taxonomy provides good taxonomic coverage - 1.6
million species (binomials).  The GBIF backbone is an automated
synthesis drawing from 40 sources (including Catalog of Life, IRMNG,
Index Fungorum), including some that are unavailable without agreement
to terms of use.  Like the NCBI taxonomy, it has ongoing institutional
support for maintenance and growth, and provides access without
agreement to terms of use, making it a good choice for Open Tree.

The GBIF backbone version used in OTT 2.10 was downloaded in July
2013.  A successor was released on [date], as this report was being
prepared, and is scheduled for incorporation into OTT.



**SILVA taxonomy**  
The classifications of prokaryotes and unicellular eukaryotes in NCBI and GBIF are
not consistent with current phylogenetic thinking. We therefore imported the
SILVA taxonomy, which is a curated, phylogenetically informed classification.
The OTUs in the SILVA  taxonomy are algorithmically-generated clusters of RNA
sequences derived from GenBank.

We incorporated SILVA version 115 into OTT 2.10, downloaded on [xxxx].
We did not include SILVA's plant, animal, or fungal branches in OTT
[references]. SILVA has higher priority than NCBI or GBIF in order to
capture the deep relationships in the tree.


**Schaferhoff 2010 (Lamiales)**  
Open Tree plant curators provided a taxonomy of the order Lamiales based
on a recent publication [for reference, see release notes].


**Hibbett 2007 (Fungi)**  
The OpenTree Fungi curators provided an order-level
higher taxonomy of Fungi.  [reference] We gave this
taxonomy higher priority than other sources of fungal data. This taxonomy
is based on [ref Hibbett] but adds revisions from the literature.
It is deposited in Figshare [reference].


**Index Fungorum (Fungi)**  
We incorporated Index Fungorum in order to improve the coverage and
classification of Fungi. We obtained database dumps of Index Fungorum
around [date] (noting that the version of IF in in GBIF was [XXX]).


**WoRMS (Decapoda)**  
We incorporated WoRMS in order to improve classificatio and coverage of
decapods.  We obtained the WoRMS taxonomy
via its Web API around [date].  [reference]


**IRMNG**  
Curators requested information about whether taxa were extinct
vs. extant.  (See below for the reason this was so important.) This
was not present in any of our other sources, so we imported IRMNG,
which logs the extinct / extant status of taxa.
[reference]


**Curation**  
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


### Characterization of sources

[NMF asks: What do the source taxonomies look like ("sampling", in
terms of depth (# of levels, breadth (overlap? are they
"comprehensive?"), homogeneity)?]

[organizational note: this is not for the results section, but an
assessment of the inputs to the method.  Some of this information
could go in the table.]
