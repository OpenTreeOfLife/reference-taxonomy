### Taxonomy sources

We build the present version of OTT from nine sources. Five of these are curated online taxonomies : NCBI, GBIF, IRMNG, Index Fungorum, and WoRMS. The remaining four are an automatically generation classification based on sequence clustering (SILVA); a family-specific classification based on a published phylogeny (Lamiales); an order-level classification of Fungi (Hibbett2007); and a set of user-generated taxonomy additions. The primary inputs are NCBI and GBIF, while incorporation of other taxonomies has been driven by curator requests where NCBI and GBIF did not provide sufficient coverage and / or had out-of-date classifications.

The choice and ranking of input taxonomies reflects the need for 1) mapping tips from phylogenies to taxa in taxonomies; 2) a backbone hierarchy in the absence of phylogenies that cover a particular clade; and 3) coverage of the synthetic tree beyond the tips present in the input trees to the [MMM] species species that are described in the literature [citation needed]. We also limit our sources to taxonomies that are available for download and licensed for re-use [re-wording probably required, and decide if we want to specifically mention taxonomies that we wanted to use but can't].

OTT assembly is dependent on the input order of the sources - higher ranked inputs take priority over lower ranked inputs. The ranking of inputs is as follows (with detail and justification given below): 1) SILVA; 2) Lamiales; 3) Hibbett2007; 4) Index Fungorum (fungi only); 5) WoRMS (decapods only); 6) NCBI; 7) IRMNG; 8) GBIF; 9) OTT additions.

*NCBI*  
The first objective of the Open Tree taxonomy is to
align OTUs across phylogenetic studies.  This need is largely met by
using the NCBI taxonomy, since (1) most phylogenies are molecular, (2)
publishers require molecular sequences used in studies to be deposited
in Genbank, (3) every Genbank deposit is annotated with a taxon from
NCBI taxonomy.  NCBI also tends to be more phylogenetically-informed than other taxonomies (see Results, below), which makes it a good candidate as a default backbone. The NCBI taxonomy therefore forms the nucleus of OTT.

The disadvantage of the NCBI taxonomy is that it has only [NNN] species
records with standard binomial names. This represents taxa that have sequence information. NCBI therefore does not alone provide sufficient coverage for OpenTree.

The particular version of NCBI taxonomy used in the OTT assembly described below (OTT 2.10) was downloaded on
[date], but we have retrieved a fresh version with every OTT build.

*GBIF*  
The GBIF taxonomy provides much greater coverage, in terms of number of binomials, than NCBI. The GBIF backbone draws from a number of sources (including
IRMNG and Index Fungorum), has ongoing institutional support, and no
special terms of use, making it a good choice for the goal of coverage. It provides much of the content available in Catalog of Life, but with terms of use that are suitable  for OpenTree.

The GBIF version used in OTT 2.10 was released in July 2003.  A successor was released on [date], as this report was being prepared, and is scheduled for incorporation into OTT.

*SILVA*  
The classification of prokaryotes and unicellular Eukaryotes in NCBI and GBIF is not consistent with current phylogenetic thinking. We therefore imported the SILVA taxonomy, which is built algorithmically based on clustering of molecular
sequences.  We incorporated SILVA version 115 into OTT 2.10.  We did
not include SILVA's plant, animal, or fungi branches in OTT
[references]. SILVA has higher priority than NCBI or GBIF in order to capture the deep relationships in the tree.

*Lamiales*  
OpenTree plant curators provided a taxonomy of the order Lamiales based
on a recent publication
[reference, see release notes]. This phylogenetically-informed source is higher-ranked that NCBI or GBIF.

*Index Fungorum*  
We incorporated Index Fungorum to improve the coverage and classification of Fungi. We obtained database dumps of Index Fungorum around [date] (noting that the version of IF in in GBIF was [XXX]
years older).  [reference]

We gave the Fungi branch of Index Fungorum higher priority than NCBI
or GBIF.  We kept the non-fungal data at low priority
(between NCBI and GBIF) in case it could provide any useful
information.

*Hibbett 2007*
The OpenTree Fungi curators provided am order-level
higher taxonomy of Fungi.  [reference] We gave this
taxonomy higher priority than other sources of fungal data. This taxonomy is deposited in Figshare [reference]

*WoRMS*  
We incorporated WoRMS in order to improve coverage in decapods. Combined, NCBI and GBIF contained only about 80%  of decapod species [confirm that
this is true]. We obtained the WoRMS taxonomy via the API around [date].  [reference]

On advice of curators, the decapod branch of WoRMS is ranked higher than other sources of decapod data.  The remainder or WoRMS has priority between NCBI and GBIF.

*IRMNG:* The GBIF backbone did not come with information about whether
taxa were extinct vs. extant.  (See elsewhere for discussion of the
importance of this information.) To obtain this information,
we imported IRMNG, which logs the extinct / extant status of taxa. Initial versions of OTT included all IRMNG records, but after review, we excluded names labeled
as invalid in
the IRMNG dump.  [reference]

*Additions:* It is not uncommon to have taxa as OTUs in
phylogenetic studies that do not occur in OTT.  This can be due to a
delay in curation by NCBI itself, a delay in importing a fresh NCBI
version into OTT, a morphological study with otherwise unknown
species, or other causes.  To handle this situation, we have developed
a user interface that allows curators to create new taxon records along with relevant
documentation (publications, databases, and so on).  New taxon records
are saved into a specific GitHub repository, and these records are then
linked from the OTT taxonomy files and user interfaces so that
provenance is always available.
