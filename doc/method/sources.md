<
### Taxonomy sources

*NCBI Taxonomy.*  The first objective of the Open Tree taxonomy is to
align OTUs across phylogenetic studies.  This need is largely met by
using the NCBI taxonomy, since (1) most phylogenies are molecular, (2)
publishers require molecular sequences used in studies to be deposited
in Genbank, (3) every Genbank deposit is annotated with a taxon from
NCBI taxonomy.  The NCBI taxonomy therefore forms the nucleus of OTT.
The particular version of OTT described below (OTT 2.10) was downloaded on
[date], but we have retrieved a fresh version with every OTT build.

*GBIF backbone taxonomy.*  The NCBI taxonomy has only [NNN] species
records with standard binomial names.  In order for Open Tree to
improve its coverage of the tree of life (the second objective), we
needed a second source taxonomy providing species going beyond NCBI's
[NNN] and reaching toward the [MMM] species that are described in the
literature [citation needed].

An initial attempt to use a global taxonomy in progress from another
project
([Union4](https://web.archive.org/web/20130823172016/http://gnaclr.globalnames.org/classifications))
was suspended over concerns about future support as well as a number
of technical difficulties.

The natural place to look was Catalog of Life, a successful ongoing
effort to develop a taxonomy of all life.  CoL was unavailable for
direct use due to unsuitable contract terms, but much of its content
was available in the GBIF backbone taxonomy [reference].  All in all,
because the GBIF backbone draws from a number of sources (including
IRMNG and Index Fungorum), has ongoing institutional support, and no
special terms of use, it seemed a good choice for the goal of coverage.

Although we started with an earlier version, the version of the GBIF
backbone in OTT 2.10 the one released in July 2003.  A successor was
only released on [date], as this report was being prepared.

Beyond NCBI and GBIF, which together meet the objectives, additional
development of the taxonomy has been driven unsystematically by
curator requests.

*SILVA:* Our microbe curators were unhappy about the conventional
classification of prokaryotes and unicellular Eukaryotes and provided
a script to import the SILVA taxonomy, which is based on molecular
evidence.  We incorporated SILVA version 115 into OTT 2.10.  We did
not include SILVA's plant, animal, or fungi branches in OTT.
[references]

We gave SILVA higher priority than NCBI or GBIF at curator request.

*Lamiales:* Plant curators requested a revision of order Lamiales based
on a recent publication about this group.
[reference, see release notes]

We gave the Lamiales higher priority than NCBI or GBIF at curator request.

*Index Fungorum:* The Open Tree Fungi curators expressed concern over
the the quality of the combined NCBI/GBIF coverage of Fungi and
requested we use Index Fungorum.  Around [date] we obtained database
table dumps for a recent version of IF (the one in GBIF was [XXX]
years older).  [reference]

We gave the Fungi branch of Index Fungorum higher priority than NCBI
or GBIF, at curator request.  There was no real call to keep the rest
of IF, which is not substantial, but we kept it at low priority
(between NCBI and GBIF) in case it could provide any useful
information.

*Fungal orders from Hibbett 2007:* The Fungi curators provided a
higher taxonomy of Fungi down to order.  [reference] We gave this
taxonomy higher priority than other sources at curator request.  To
ensure that all of our sources were public, curators deposited this
taxonomy with Figshare.

*WoRMS:* The Open Tree decapod curators observed that our combined
taxonomy had only about 80% coverage of decapod species [confirm that
this is true], and suggested we obtain the decapod taxonomic tree from
WoRMS.  Given questions over terms of use of the dump files for WoRMS,
which were only available on request, we obtained the taxonomy via the
web API, around [date].  [reference]

We gave the decapod branch of WoRMS higher priority than other sources
at curator request.  Mostly due to unfamiliarity with WoRMS, we felt
that there was some risk in having the rest of WoRMS also override
NCBI, so we kept it with lower priority (between NCBI and GBIF).

*IRMNG:* The GBIF backbone did not come with information about whether
taxa were extinct vs. extant.  (See elsewhere for discussion of the
importance of this information.)  For extinctness information we
decided to import IRMNG.  Initial we included all IRMNG records but
learned through reviewer comments that it was necessary to exclude
names known to be invalid, which fortunately is information present in
the IRMNG dump.  [reference]

*Additions:* For various reasons some taxa occurring as OTUs in
phylogenetic studies do not occur in OTT.  This could be due to a
delay in curation by NCBI itself, a delay in importing a fresh NCBI
version into OTT, a morphological study with otherwise unknown
species, or other causes.  To handle this situation we have developed
a user interface for creating new taxon records with relevant
documentation (publications, databases, and so on).  New taxon records
are entered into a repository assigned to this purpose, which is
linked from the OTT taxonomy files and user interfaces so that
provenance is always available.

