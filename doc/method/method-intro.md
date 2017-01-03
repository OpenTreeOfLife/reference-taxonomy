
# Method

In the following, any definite claims or measurements apply to the
Open Tree reference taxonomy version 2.11.


## Terminology

  * source taxonomy = imported taxonomic source (NCBI taxonomy, etc.)
  * workspace = data structure for creation of the reference
    taxonomy
  * node = a taxon record, either from a source taxonomy or the workspace;
    giving name-string, source information,
    parent node, optional rank, optional annotations
  * tip = a node that is not the parent of any node
  * homonymy = where a single name-string belongs to multiple nodes
    (within the same taxonomy).  This is the nontechnical meaning of 'homonym'
    and is not to be confused with 'homonym' in the nomenclatural sense, 
    which only applies within a single nomenclatural code.
    Nomenclatural homonyms, hemihomonyms, misspellings are all homonyms in this sense.


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

We developed a tool for assembling a taxonomy from two or more
taxonomic sources.  By using an automated process, we can generate a
new combined taxonomy version from new source taxonomy versions at any
point.

Following is a simplified description of the method. Several
generalities stated here are simplifications; the actual method used
is a bit more involved, as described later on.

We start with a sequence of source taxonomies S1, S2, ..., Sn, ordered
by priority.  Priority means that if S is judged more accurate or
otherwise "better" than S', then S occurs earlier in the sequence than
S' and its information supersedes that from later sources.  Priority
judgements are made by curators on the project.

We define an operator for combining taxonomies pairwise, written
schematically as U = S + S', and apply it from left to right:

> U1 = S1  
> U2 = U1 + S2  
> U3 = U2 + S3  
> ...

The combination S + S' is formed in two steps (details below).

 1. A mapping or _alignment_ from S' to S is found, identifying all
    nodes in S' that can be equated with nodes in S.  Not all nodes in S' 
    are mapped; the unaligned nodes are the ones that will enlarge S
    yielding U = S + S'.
 2. The result U = S + S' is formed by _merging_ S' into
    S, i.e. by starting with S and adding unaligned taxa from S' to it.
    The positions in S where the unaligned nodes are to be attached
    is determined from nearby aligned nodes.

As a simple example, consider a genus represented in both
taxonomies, but containing different species in the two:

> [this would be a figure]   S = (b,c,d)a,  S' = (c,d,e)a

Taxonomies S and S' each have four nodes.  Suppose c, d, and a in S'
are aligned to c, d, and a in S.  The only unaligned node in S' is e,
and it can be placed as a sibling of c and d.  (e is also a child of a, which
is aligned, giving a different way to place it.  The result is the same in
this case, but isn't always.)  This yields

> [figure] S + S' = (b,c,d,e)a

One might call this merge heuristic 'my sibling's sibling is my
sibling' or 'transitivity of siblinghood'.

This is a very common pattern.  For example: in NCBI taxonomy, take a
= _Bufo_, b = _B.  spinosis_, c = _B. bufo_, d = _B. japonicus_, and
in GBIF take a = _Bufo_, c = _B. bufo_, d = _B. japonicus_, e =
_B. luchunnicus_.  There is no _B. spinosis_ in GBIF and no
_B. luchunnicus_ in NCBI.  The _Bufo_ in the combined taxonomy has as
its children copies of species records from both sources.  There are
probably hundreds of thousands of similar simple grafting events (hard
to count).

[In the presentation we could forego the schematic letters a, b,
etc. in favor of the concrete example.  I think the figures would be
harder to read with real taxon names compared with schematic names
(letters), but will take advice.]

There are two ways in which an unaligned node from S' gets added to S:
as a graft (as in the previous example), and as an 'insertion', where
it has some descendants (in S') aligned to nodes in S.  As an example of the
latter, consider

> [figure] (M,C,S,P)F + ((M,C)H,(S,P)E)F = ((M,C)H,(S,P)E)F

This is a case in which S' (part of WoRMS) provides greater resolution
than S (NCBI taxonomy): it divides the family F into subfamilies H and
E, where S doesn't.  H and E are 'inserted' into S in a way that adds
information without disrupting existing relationships.  For example, C
is contained in F both before and after the insertion event.

(Key: F = Fissurellidae, M = Montfortula, C = Clypidina, S = Scutus, P
= Puncturella, H = Hemotominae, E = Emarginulinae)

The vast majority of alignment and merge situations are just like the
above examples.  However, a few cause serious problems.  Ambiguities
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
