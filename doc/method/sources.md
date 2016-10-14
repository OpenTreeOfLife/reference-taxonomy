# Materials and methods

This publication describes a particular version of the Open Tree taxonomy
(version 2.10), but one should keep in mind that the taxonomy is
constantly evolving as sources are updated, methods improve, and users request
additional functionality and patches.

## Taxonomic sources

We build the taxonomy from nine sources. Some of these sources are from
taxonomy projects, while others were manually assembled based on recent
publications.
OTT assembly is dependent on the input order of the sources - higher ranked inputs take priority over lower ranked inputs.
[see table]

[The following should be a table:]

 * the ARB-SILVA taxonomy
 * Hibbett 2007
 * Index Fungorum
 * Schaferhoff 2010
 * WoRMS
 * the NCBI taxonomy
 * the GBIF backbone taxonomy
 * IRMNG
 * additional taxa provided by Open Tree study curators

References for all sources could be a column in the table.

The root clade could be a column in the table.

The ranking can be a column in the table (although if the table is in
rank order, it would just be sequential numbers).

The following information might also go in the table: total number of
taxa, number of exclusive taxa.

The following information might also go in the table: number of binomials,
number of exclusive binomials (i.e. coming only from that source).

    irmng,1111550,313261
    worms,258378,53233
    silva,13953,44
    gbif,1629523,486411
    ncbi,360455,88358
    if,237482,18291
    addition,15,15

Maybe some measure of resolution, like maximum depth.

end table]

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

*NCBI taxonomy*  
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


*GBIF backbone taxonomy*  
The GBIF backbone taxonomy provides good taxonomic coverage - 1.6 million
species (binomials).  The GBIF backbone draws from a number of sources
(including IRMNG and Index Fungorum), has ongoing institutional
support for maintenance and growth, and provides access without
agreement to terms of use, making it a good choice for the project. It
provides much of the content available in Catalog of Life and other
closed sources, without violating Open Tree's no-terms-of-use requirement.

The GBIF backbone version used in OTT 2.10 was downloaded in July
2013.  A successor was released on [date], as this report was being
prepared, and is scheduled for incorporation into OTT.



*SILVA taxonomy*  
The classification of prokaryotes and unicellular Eukaryotes in NCBI and GBIF is
not consistent with current phylogenetic thinking. We therefore imported the
SILVA taxonomy, which is a curated, phylogenetically informed classification.
The OTUs in the SILVA  taxonomy are algorithmically-generated clusters of RNA
sequences derived from GenBank.

We incorporated SILVA version 115 into OTT 2.10, downloaded on [xxxx].
We did not include SILVA's plant, animal, or fungal branches in OTT
[references]. SILVA has higher priority than NCBI or GBIF in order to
capture the deep relationships in the tree.


*Schaferhoff 2010 (Lamiales)*  
Open Tree plant curators provided a taxonomy of the order Lamiales based
on a recent publication [for reference, see release notes].


*Hibbett 2007 (Fungi)*  
The OpenTree Fungi curators provided an order-level
higher taxonomy of Fungi.  [reference] We gave this
taxonomy higher priority than other sources of fungal data. This taxonomy
is deposited in Figshare [reference].


*Index Fungorum (Fungi)*  
We incorporated Index Fungorum to improve the coverage and
classification of Fungi. We obtained database dumps of Index Fungorum
around [date] (noting that the version of IF in in GBIF was [XXX]).


*WoRMS (Decapoda)*  
We incorporated WoRMS in order to improve coverage in
decapods.  We obtained the WoRMS taxonomy
via its Web API around [date].  [reference]


*IRMNG*  
Curators requested information about whether taxa were extinct
vs. extant.  (See elsewhere for discussion of the importance of this
information.) This was not present in any of our other sources, so we
imported IRMNG, which logs the extinct / extant status of
taxa.
[reference]


*Additions*  
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
