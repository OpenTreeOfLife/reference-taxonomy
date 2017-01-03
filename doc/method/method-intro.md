
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
  * union taxonomy = data structure for creation of the reference
    taxonomy [change to 'workspace'?]
  * node = a taxon record, either from a source taxonomy or the union taxonomy;
    giving name-string, source information,
    parent node, optional rank, optional annotations
  * tip = a node that is not the parent of any node
  * polysemy [homonymy] = where a single name-string belongs to multiple nodes
    (within the same taxonomy); in
    nomenclatural terms, either a homonym, hemihomonym, or
    clerical error


## *NMF suggestion on how to explain all this*

1. Is it possible to assume an ideal case where merging two or more OTT input taxonomies requires no or only very minimal conflict/noise/ambiguity resolution? And the result is almost unambiguously correct? If so, perhaps you start your "assembly process" description by imaging/introducing such a case, and your core pipeline in relation to it. That is then out of the way - a scenario that OTT can handle well and easily.

   [JAR: I think this is now handled by 'assembly overview' below.  Maybe the examples are too simple? Let me know.]

2. Complications, 1 - those complications that through various profound or pragmatic solution you can address to a fairly large degree of satisfaction. Outcome -- still rather sound OTT, but drawing now on a full scope of things you've added because you've had to given case 1. was not what the input looked like.

   [JAR: I think this is what the alignment and merge sections are about.]

3. Complications, 2 - issues that you either handle not to your own satisfaction, or simply cannot handle at all.

   [JAR: yes, but this belongs in the discussion section.]

I guess I am suggesting this because 1 & 2 give you an opportunity to shine first, and somewhat conclusively, for a significant subset of the input trees. At least for the purpose of mounting the narrative. Clearly any complete OTT assembly job will encounter everything. But you may not have to write such that you directly follow what I assume may be real -- every input taxonomy has instances 1, 2, 3 represented to varying degrees, or they arise as the OTT grows. Instead you could pretend that some input taxonomies are clean (1), individually and jointly. Or clean enough (2) - because of your work. And only 3 is the tough stuff - but tough for anybody, etc.

So, I wonder what would happen if you did this kind of thing. "For the sake of making this assembly process accessible to a wide readership, we first illustrate the entire pipeline when acting on two or more input taxonomies that are highly internally consistent, and also pose minimal conflict among them. Here the assembly works well from A to Z, as we show and exemplify.

"A second category are complications that occur frequently but for which we have developed adequate diagnosis and repair/resolution mechanisms. We show how we do this, and also show what else could be done for even better performance".

"A third category contains lingering challenges that point to future solution analysis/development needs. And we suggest ..."

(and of course you'd say that in reality, every input may be a mix of 1-3)


## Assembly overview

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
 2. A _merge_ step that creates the union, U = S + S', by adding to S the unaligned
    taxa from S'. The attachment position of unaligned nodes from step 1 is
    is determined from nearby aligned nodes, most often either as an _graft_
    or a _insertion_. Examples of these two cases are given in
    [point to figure(s)].

As a simple example, consider a genus represented in both
taxonomies, but containing different species in the two:

> [this would be a figure]   S = (c,d,e)a,  S' = (d,e,f)a

S and S' each have four nodes.  Suppose d, e, and a in S' are aligned
to d, e, and a in S.  The only unaligned node is f, which is a
sibling of d and e and therefore grafted as a child of a.  After the merge
step, we have:

> [figure] S + S' = (c,d,e,f)a

One might call this merge heuristic 'my sibling's sibling is my
sibling' or 'transitivity of siblinghood'.

[what happened to b?]

[Real example: a = Bufo, c,d,e = Bufo bufo / spinosis / japonicus
(etc.) in NCBI, d,e,f = Bufo bufo / japonicus / luchunnicus (etc.) in
GBIF.  There are probably hundreds of thousands of other simple
grafting events (hard to count).  I think the figures would be harder
to read with real taxon names compared with schematic names (letters),
but will take advice.]

The other common merge method is an _insertion_, where the unaligned
node has descendants that are in S. This [KC: usually? almost always?]
occurs when S' has greater resolution than S. For example, this case
inserts the unaligned nodes E and H:

> [figure] (M,C,S,P)F + ((M,C)H,(S,P)E)F = ((M,C)H,(S,P)E)F

This is a real example in which S' (part of WoRMS) provides greater
resolution than S (NCBI taxonomy): it divides the family F into
subfamilies H and E, where S doesn't.  H and E are 'inserted' into S
in a way that adds information without disrupting existing
relationships.  For example, C is contained in F both before and after
the insertion event.

[F = Fissurellidae, M = Montfortula, etc.]

The vast majority of alignment and merge situations are simple grafts and
insertions. However, a few cause serious problems.  Ambiguities
caused by synonyms and homonyms create most of the difficulties, with
inconsistent or unclear higher taxon membership creating the rest.

The following sections describe the source taxonomies, and then go
into the taxonomy combination method in detail.
