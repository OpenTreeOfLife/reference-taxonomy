
# Method

The conventional approach to meeting the requirements stated in the introduction
would be to create a database, copy the first taxonomy into it, then
somehow merge the second taxonomy into that, repeating for further sources if
necessary.  However, it is not clear how to meet the ongoing update
requirement under this approach.  As the source taxonomies change, we would like
for the combined taxonomy to contain only information derived from the latest
versions of the sources, without residual information from previous versions.  Many
changes to the sources are corrections, and we do not want to retain
information from previous versions that is known to be incorrect.  

Rather than maintain a database of taxonomic information, we instead developed a
process for assembling a taxonomy from two or more taxonomic sources.  With a
repeatable process, we can generate a new combined taxonomy version from new
source taxonomy versions _de novo_, and do so frequently.  There are additional
benefits as well, such as the ability to add additional sources relatively
easily, and to use the tool for other purposes.

In the following, any definite claims or measurements refer to the
Open Tree reference taxonomy version 3.0.

## Terminology

  * source taxonomy = imported taxonomic source (NCBI taxonomy, etc.)
  * workspace = data structure for creation of the reference
    taxonomy
  * name-string = taxonomic name considered textually, without association
    with any particular taxon or nomenclatural code
  * node = a taxon record, either from a source taxonomy or the workspace.
    Records primary name-string, provenance,
    parent node, optional rank, optional annotations
  * parent (node) = the nearest enclosing node within a given node's taxonomy
  * tip = a node that is not the parent of any node
  * homonym = where a single name-string belongs to multiple nodes
    within the same taxonomy.  This is close to the nontechnical meaning of 'homonym'
    and is not to be confused with 'homonym' in the nomenclatural sense,
    which only applies within a single nomenclatural code.
    Nomenclatural homonyms and hemihomonyms [ref 10.11646/bionomina.4.1.3] are both homonyms in this sense.
  * primary = the non-synonym name-string of a node, as opposed to one of the synonyms.
  * image (of a node n') = the workspace node corresponding to n'
  * _incertae sedis_: taxon A is _incertae sedis_ in taxon B if A is a child of B
    but is not known to be disjoint from B's non-_incertae-sedis_ children.  That is,
    if we had more information, it might turn out that A is a
    member of one of the other children of B.

## Assembly overview

This section is an overview of the taxonomy assembly method. Several
generalities stated here are simplifications; the actual method (described later)
is significantly more involved.

We start with a sequence of source taxonomies S1, S2, ..., Sn, ordered
by priority.  Priority means that if S is judged more accurate or
otherwise "better" than S', then S occurs earlier in the sequence than
S' and its information supersedes that from later sources.  Priority
judgements are made by curators (either project personnel or participants
in OpenTree workshops and online forums) based on their taxonomic
expertise.

We define an operator for combining taxonomies pairwise, written
schematically as U = S + S', and apply it from left to right:

> U0 = empty  
> U1 = U0 + S1  
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
    or an _insertion_.

Examples of these two cases are given in Figure 2.

As a simple example, consider a genus represented in both
taxonomies, but containing different species in the two:

> S = (b,c,d)a,  S' = (c,d,e)a

S and S' each have four nodes.  Suppose c, d, and a in S' are aligned
to c, d, and a in S.  The only unaligned node is e, which is a
sibling of c and d and therefore grafted as a child of a.  After the merge
step, we have:

> S + S' = (b,c,d,e)a

One might call this merge heuristic 'my sibling's sibling is my
sibling' or 'transitivity of siblinghood'.

This is a very common pattern.  Figure 2a illustrates a real life-example when combining the genus _Bufo_ across NCBI and GBIF. There are
hundreds of thousands of similar simple grafting events.

The other merge method is an _insertion_, where the unaligned
node has descendants that are in S. This always
occurs when S' has greater resolution than S. For example, see Figure 2b, where GBIF provides greater resolution than NCBI.

The vast majority of alignment and merge situations are simple, similar to the
examples shown in Figure 2. However, even a small fraction of special cases can add up to
thousands when the total number of alignments and merges measures in the
millions, so we have worked to develop heuristics that handle the most common
special cases. Ambiguities caused by synonyms and homonyms create most of the
difficulties, with inconsistent or unclear higher taxon membership creating the
rest. The development of the assembly process described here has been a driven
by trial and error - finding cases that fail and then adding / modifying
heuristics to address the underlying cause, or adding an _ad hoc_ adjustment for
cases that are rare or overly complex.

The following sections describe the source taxonomies, and then detail the
taxonomy combination method.


## Taxonomic sources

We build the taxonomy from ten sources. Some of these sources are from
taxonomy projects, while others were manually assembled based on
recent publications.  OTT assembly is dependent on the input order of
the sources - higher ranked inputs take priority over lower ranked
inputs.
