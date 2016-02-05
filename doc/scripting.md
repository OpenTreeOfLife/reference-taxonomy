# Writing scripts that manipulate taxonomies

The 'smasher' program does things with taxonomies.  It was written to support
builds of the Open Tree Reference Taxonomy, but is a general purpose
tool.  It is scriptable using Python via the Java-based Python interpreter 'jython'.  You
can see examples of taxonomy build scripts in these two locations in the repository:

    t/aster.py
    make-ott.py

## Running Smasher

To use Smasher, you should clone the reference-taxonomy repository on github:

    git clone git@github.com:OpenTreeOfLife/reference-taxonomy.git

You can of course clone using https: instead of ssh, see 
[here](https://github.com/OpenTreeOfLife/reference-taxonomy).

Smasher is a Java program so it requires some version of Java to be installed.  It has been tested with Java 1.6 and 1.7.  To compile Smasher:

    make compile

(Don't just say 'make' unless you want to build the Open Tree reference
taxonomy!  That takes a while and is not to be done casually.)

You can test that Smasher functions with

    make test

Smasher is invoked as follows

    bin/jython script.py

where the current directory is the home directory of the repository
clone, and script.py is the name of a script file.  
Or if you like you can skip the script.py parameter, and you'll get an interactive jython prompt.

You may have a need to set the Java memory limit, which might be too large or too small for your purposes.  To do  this, edit JAVAFLAGS in the bin/jython script (or edit the Makefile and force re-creation of bin/jython).  The default is currently 14G.  I like to set it a bit smaller than the actual physical memory available on the machine.

## Using the library

The first step in any build script is to gain access to the Taxonomy
modules:

    from org.opentreeoflife.taxa import Taxonomy
    from org.opentreeoflife.smasher import UnionTaxonomy


## Taxonomies

If you want to synthesize a new taxonomy, initiate the build by creating a new UnionTaxonomy object:

    tax = UnionTaxonomy.newTaxonomy()

Taxonomies are usually built starting with one or more existing
taxonomies (although they needn't be), obtained as follows:

    ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/', 'ncbi')

which fetches a taxonomy from files (in the format specified by this tool,
see wiki).  There can be as many of these retrievals as you like.

The first argument is a directory name and must end in a '/'.  
The directory must contain a file 'taxonomy.tsv'.

The second argument is a short tag that will appear in the
'sourceinfo' column of the final taxonomy file to notate the source of taxa
that came from that taxonomy, e.g. 'ncbi:1234'.

The format of taxonomy files (taxonomy.tsv and so on) is given [here](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format).

Taxonomies can also be read and written in Newick syntax.

    h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')
    
See [wikipedia](https://en.wikipedia.org/wiki/Newick_format) for a description of Newick format.  Smasher supports an idiosyncratic syntax for specifying rank, e.g. 

    ('Subclass=Sordariomycetidae','Subclass=Hypocreomycetidae')

'absorb' merges the given taxonomy (e.g. ncbi) into the one under construction (e.g. the reference taxonomy).

    tax.absorb(ncbi)

To write the taxonomy to a directory:

    tax.dump('mytaxonomy/')

The directory name must end with '/'.  The taxonomy is written to
'mytaxonomy/taxonomy.tsv', synonyms file to 'mytaxonomy/synonyms.tsv',
and so on.

The one-argument form dump(filename) generates files with columns
separated by tab-verticalbar-tab.  To generate with columns simply
separated by tabs, use

    tax.dump('mytaxonomy/', '\t')

Taxonomies can also be written as Newick:

    tax.dumpNewick('mytaxonomy.tre')

but beware that this loses all information about synonyms and sources.
Also beware that if the taxonomy contains homonyms, the Newick file
will contain multiple nodes with the same label, and most tools that
consume Newick don't like this.

## Referring to taxa

A number of scripting commands take taxa as parameters.  There are two
ways to specify a taxon: by finding it in a taxonomy, or by creating
it anew.

The taxon() method looks up a taxon in a taxonomy.  It takes two
forms:

    ott.taxon('Pseudacris')
    ott.taxon('Pseudacris', 'Anura')

Use the first form if that name is unique within the taxonomy.  If the
name is ambiguous (a homonym), use the second form, which provides
context.  The context can be any ancestor of the intended taxon that is not shared with the other homonyms.

A variant on this is to specify any descendent of the taxon, as
opposed to ancestor:

    ott.taxonThatContains('Anura', 'Pseudacris') #designates Anura

It is also possible to use a taxon identifier in a source taxonomy:

    ncbi.taxon('173133')

but this is brittle as identifiers may change from one version of a
source taxonomy to another.

To add a new taxon, provide its name, rank, and source information to
the newTaxon() method.  The source information should be a URL or
CURIE that is specific to that taxon.

    ott.newTaxon('Euacris', 'genus', 'http://mongotax.org/12345')

If the taxon has no particular rank, put 'no rank'.

## Counts

    taxon.count()  =>  integer
    taxon.tipCount()  =>  integer
    
count() returns the number of taxa (nodes) tipward of the given taxon.

tipCount() returns the number of tips (leaf nodes) tipward of the given taxon.

## Surgery

Whenever making ad hoc modifications to the taxonomy please leave a pointer (i.e. a URL) to some
evidence or source of evidence for the correctness of the change.  If
the evidence doesn't go in as the source information in a newTaxon() call, put
it in a comment in the script file.  (Probably the evidence should be an argument to the
various surgery commands; maybe later.)

Add a new taxon as a daughter of a given one: (would be used with newTaxon)

    taxon.add(othertaxon)
    e.g.
    ott.taxon('Parentia').add(ott.newTaxon('Parentia daughtera', 
       'species', 'http://www.marinespecies.org/aphia.php?p=taxdetails&id=557120'))

Detach an existing taxon from its current location, and add it as a
daughter of a different parent:

    taxon.take(othertaxon)
    e.g. 
    # From http://www.marinespecies.org/aphia.php?p=taxdetails&id=556811
    ott.taxon('Ammoniinae').take(ott.taxon('Asiarotalia'))

Move the children of taxon A into taxon B, and make B be a synonym of
A:  (I.e. the names are synonyms, but not previously recorded as
such):

    taxon.absorb(othertaxon)
    e.g. 
    # From http://www.marinespecies.org/aphia.php?p=taxdetails&id=557120
    ott.taxon('Parentia').absorb(ott.taxon('Parentiola'))

Delete a taxon and all of its descendants:

    taxon.prune()

Delete all the descendants of a given taxon: (this is useful for grafting one taxonomy into another)

    taxon.trim()

Delete a taxon, moving all of its children up one level (e.g. delete a
subfamily making all of its genus children children of the family):

    taxon.elide()

Select a subset of a taxonomy:

    taxonomy.select(taxon)

This returns a new taxonomy whose root is (a copy of) the given taxon.

(TBD: Need a way to add a root to the forest, or change the root.)

## Alignment

Taxonomy alignment ('absorb') establishes correspondences between taxa in taxonomy A with taxa in taxonomy B, based on taxon names and topology.  Most of the complexity of this operation has to do with the handling of homonyms.
Sometimes the automatic alignment
logic makes mistakes.  It is then desirable to manually specify that
taxon X in taxonomy A is the same as taxon X in taxonomy B (they are not homonyms), or not (they *are* homonyms).

    tax.same(tax1, tax2)
    tax.notSame(tax1, tax2)
      e.g.
    tax.same(A.taxon("X"), B.taxon("X"))

These methods are a bit fussy.
One of the arguments to same or notSame should be a taxon from a taxonomy that is about to be
'absorbed' but hasn't been yet, and the other should be from the
taxonomy under construction, after it has had other source taxonomies
absorbed into it.  (Equivalently it is possible to specify a taxon in a taxonomy that has already been 'absorbed'.)  The taxa may occur in either order.

    same(gbif.taxon('Plantae'), ott.taxon('Viridiplantae'))
    ott.absorb(gbif)

Should the need arise you can find out what a source taxon maps to, using image():

    tax2.absorb(tax1)
    tax2.image(tax1.taxon('Sample'))


## Annotation

Add a synonym:

    taxon.synonym('Alternate name')

Rename a taxon, leaving old name behind as a synonym:

    taxon.rename('Newname')

Mark a taxon as being 'incertae sedis' i.e. not classified.  It will
be retained for use in OTU matching but will not show up in the
browsable tree unless mentioned in a source tree:

    taxon.incertaeSedis()

Mark as extinct:

    taxon.extinct()

Force an 'incertae sedis' or otherwise hidden taxon to become visible
in spite of other information:

    taxon.forceVisible()

Mark a taxon as 'hidden' so that it can be suppressed by tools downstream:

    taxon.hide()
    
## Looking at taxonomies

Taxonomies are iterable.

    for taxon in taxonomy: ...
    
Taxa have lots of properties you might want to look at in a script.
    
    taxon.parent
    taxon.children
    taxon.isHidden()
    
You can select only the visible (non-hidden) taxa:

    taxonomy.selectVisible("my taxonomy but only visible")

## Debugging

Not much here yet, just

    taxon.show()

Displays information about this taxon: its lineage and children, sources,
flags, etc.
