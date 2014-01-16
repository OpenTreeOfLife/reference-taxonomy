
The taxonomy build is drive by scripts written in jython.  You can see
examples in these two locations:

    t/aster.py
    feed/ott/ott.py

The first step in a script is to gain access to the needed scripting
primitives:

    from org.opentreeoflife.smasher import Taxonomy

The build process is invoked as follows

    java -classpath ".:lib/*" org.opentreeoflife.smasher.Smasher --jython script.py

Builds of the entire reference taxonomy require a fair amount of
memory; I've been using -Xmx12G.  See the Makefile for more information.

You can also interact with the jython interpreter and do all of the
below operations at the command line, just by invoking Smasher without
arguments.

Taxonomies
==========

Initiate the build by creating a new Taxonomy object:

    ott = Taxonomy.newTaxonomy()

Taxonomies are usually built starting with one or more existing
taxonomies (although they needn't be):

    ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/')

fetches a taxonomy from files (in the format specified by this tool,
see wiki).  There can be as many of these retrievals as you like.

'absorb' merges the given taxonomy into the one under construction.

    ott.absorb(ncbi, 'ncbi')

The second argument to 'absorb' will be the prefix to be used to
notate identifiers that came from the ncbi taxonomy, e.g. 'ncbi:1234'

To write the taxonomy to a directory:

    ott.dump("mytaxonomy/")

Taxa
====

A number of scripting commands take taxa as parameters.  There are two
ways to specify a taxon: by finding it in a taxonomy, or by creating
it anew.

taxon() looks up a taxon in a taxonomy.  It takes two forms:

    ott.taxon("Pseudacris")
    ott.taxon("Pseudacris", "Anura")

Use the first form if that name is unique within the taxonomy.  If the
name is ambiguous (a homonym), use the second form, which provides
context.  The context can be any ancestor of the intended taxon.

It is also possible to use a taxon identifier:

    ott.taxon("173133")

To add a new taxon, just provide its name, rank, and source
information.  The source information should be a URL that is specific
to that taxon.

    ott.newTaxon("Euacris", "genus", "http://mongotax.org/12345")

Surgery
=======

Whenever doing surgery please leave a comment in the script with a
pointer (i.e. a URL) to some evidence or source of evidence for the
correctness of the change.

Add a new taxon as a daughter of a given one: (would be used with newTaxon)

    taxon.add(othertaxon)
    e.g. ott.taxon("Parentia").add(ott.newTaxon("Parentia daughtera"))

Detach an existing taxon as a daughter from its current location,
adding it as a daughter of a different parent:

    taxon.steal(othertaxon)
    e.g. ott.taxon("Parentia").add(ott.taxon("Parentia daughtera"))

Unify two taxa, pooling their children and adding the name of the
second as a synonym for the first

    taxon.absorb(othertaxon)
    e.g. ott.taxon("Parentia").add(ott.taxon("Parentiola"))

Delete a taxon and all of its descendents

    taxon.prune()

Delete a taxon, moving all of its children up one level (e.g. delete a
subfamily making all of its genus children children of the family)

    taxon.elide()

Select a subset of a taxonomy:

    taxonomy.select(taxon)

This returns a new taxonomy whose root is (a copy of) the given taxon.

Alignment
=========

When merging taxonomies it may be desirable to manually specify that
taxon X in taxonomy A is the same as taxon Y in taxonomy B.
Ordinarily this happens automatically, when the names and topology
match, but sometimes doing it manually is needed.

    same(tax1, tax2)

One of the arguments should be from a taxonomy that is about to be
'absorbed' but hasn't been yet, and the other should be from the
taxonomy under construction, after it has had other source taxonomies
absorbed into it.

    same(gbif.taxon("Plantae"), ott.taxon("Viridiplantae"))
    ott.absorb(gbif)


Annotation
==========

Add a synonym

    taxon.synonym("Alternate name")

Rename a taxon, leaving old name behind as a synonym

    taxon.rename("Newname")

Mark a taxon as being 'incertae sedis' i.e. not classified.  It will
be retained for use in OTU matching but will not show up in the
browsable tree unless mentioned in a source tree.

    taxon.incertaeSedis()

Force an 'incertae sedis' taxon to become visible in spite of previous
information.

    taxon.forceVisible()
