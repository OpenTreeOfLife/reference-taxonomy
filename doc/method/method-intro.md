
# Method

The conventional approach to meeting the requirements stated in the introduction
would have been to create a database, copy the first taxonomy into it, then
somehow merge the second taxonomy into that, repeating for further sources if
necessary.  However, it is not clear how to meet the the ongoing update
requirement under this approach.  As the source taxonomies change, we would like
for the combined taxonomy to contain only information derived from the latest
versions of the sources, without residual information from previous versions.  Many
changes to the sources are corrections, and we do not want to hang on to or even
be influenced by information known to be incorrect.  

Rather than maintain a database of taxonomic information, we instead developed a
process assembling a taxonomy from two or more taxonomic sources.  With a
repeatable process, we can generate a new combined taxonomy version from new
source taxonomy versions de novo, and do so frequently.  There are additional
benefits as well, such as the ability to add additional sources relatively
easily, and to use the tool for other purposes.

In the following, any definite claims or measurements apply to the
Open Tree reference taxonomy version 2.11.

## Terminology

  * source taxonomy = imported taxonomic source (NCBI taxonomy, etc.)
  * workspace = data structure for creation of the reference
    taxonomy
  * node = a taxon record, either from a source taxonomy or the workspace.
    records primary name-string, provenance,
    parent node, optional rank, optional annotations
  * parent (node) = the nearest enclosing node within a given node's taxonomy
  * tip = a node that is not the parent of any node
  * homonym = where a single name-string belongs to multiple nodes
    within the same taxonomy.  This is close to the nontechnical meaning of 'homonym'
    and is not to be confused with 'homonym' in the nomenclatural sense, 
    which only applies within a single nomenclatural code.
    Nomenclatural homonyms, hemihomonyms, and misspellings are all homonyms in this sense,
    when recorded in a given taxonomy.
  * primary = the non-synonym name-string of a node, as opposed to one of the synonyms.
  * image (of a node n') = the workspace node corresponding to n'
  * _incertae sedis_: taxon A is _incertae sedis_ in taxon B if A is in B
    but is not known to be outside of A's non-_incertae-sedis_ children.  That is,
    if we had more information, it might turn out that B is a
    member of one of the other children of A.


## Assembly overview

To produce the Open Tree reference taxonomy, 
we developed a tool for assembling taxonomies from two or more
taxonomic sources.  By using an automated process, we can generate a
new combined taxonomy version from new source taxonomy versions at any
point.

This section is an overview of the method. Several
generalities stated here are simplifications; the actual method used
is a bit more involved, as described later on.

We start with a sequence of source taxonomies S1, S2, ..., Sn, ordered
by priority.  Priority means that if S is judged more accurate or
otherwise "better" than S', then S occurs earlier in the sequence than
S' and its information supersedes that from later sources.  Priority
judgements are made by curators on the project based on their taxonomic
expertise.

We define an operator for combining taxonomies pairwise, written
schematically as U = S + S', and apply it from left to right:

> U1 = S1  
> U2 = U1 + S2  
> U3 = U2 + S3  
> ...

The combination S + S' is formed in two steps:

 1. A mapping or _alignment_ step that identifies all
    nodes in S' that can be equated with nodes in S. There will often be nodes
    in S' that cannot be aligned to S.
 2. A _merge_ step that creates the combination U = S + S', by adding to S the unaligned
    taxa from S'. The attachment position of unaligned nodes from step 1
    is determined from nearby aligned nodes, either as a _graft_
    or an _insertion_. Examples of these two cases are given in
    [point to figure(s)].

As a simple example, consider a genus represented in both
taxonomies, but containing different species in the two:

> [this would be a figure]   S = (b,c,d)a,  S' = (c,d,e)a

S and S' each have four nodes.  Suppose c, d, and a in S' are aligned
to c, d, and a in S.  The only unaligned node is e, which is a
sibling of c and d and therefore grafted as a child of a.  After the merge
step, we have:

> [figure] S + S' = (b,c,d,e)a

One might call this merge heuristic 'my sibling's sibling is my
sibling' or 'transitivity of siblinghood'.

This is a very common pattern.  For example: in NCBI taxonomy, take a
= _Bufo_, b = _B. spinosis_, c = _B. bufo_, d = _B. japonicus_, and
in GBIF take a = _Bufo_, c = _B. bufo_, d = _B. japonicus_, e =
_B. luchunnicus_.  There is no _B. spinosis_ in GBIF and no
_B. luchunnicus_ in NCBI.  The _Bufo_ in the combined taxonomy has as
its children copies of species records from both sources.  There are
probably hundreds of thousands of similar simple grafting events (hard
to count).

[In the presentation we could forego the schematic letters a, b,
etc. in favor of the concrete _Bufo_ example.  I think the figures would be
harder to read with real taxon names compared with schematic names
(letters), but will take advice.]

The other merge method is an _insertion_, where the unaligned
node has descendants that are in S. This [KC: usually? almost always? JR: always.]
occurs when S' has greater resolution than S. For example, this case
inserts the unaligned nodes E and H:

> [figure] (M,C,S,P)F + ((M,C)H,(S,P)E)F = ((M,C)H,(S,P)E)F

This is a case in which S' (part of WoRMS) provides greater resolution
than S (NCBI taxonomy): it divides the family F into subfamilies H and
E, where S doesn't.  H and E are 'inserted' into S in a way that adds
information without disrupting existing relationships.  For example, C
is contained in F both before and after the insertion event.

(Key: F = Fissurellidae, M = Montfortula, C = Clypidina, S = Scutus, P
= Puncturella, H = Hemotominae, E = Emarginulinae)

The vast majority of alignment and merge situations simple, similar to the 
above. However, a few cause serious problems.  Ambiguities
caused by synonyms and homonyms create most of the difficulties, with
inconsistent or unclear higher taxon membership creating the rest.

The following sections describe the source taxonomies, and then go
into the taxonomy combination method in detail.


## Taxonomic sources

We build the taxonomy from ten sources. Some of these sources are from
taxonomy projects, while others were manually assembled based on
recent publications.  OTT assembly is dependent on the input order of
the sources - higher ranked inputs take priority over lower ranked
inputs.
