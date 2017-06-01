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

Smasher is a Java program so it requires some version of Java to be
installed.  It has been tested with Java 1.6, 1.7, and 1.8.  To
compile Smasher:

    make

If you make changes to Smasher and want to test that it still functions:

    make test

Smasher is invoked as follows

    bin/jython script.py

where the current directory is the home directory of the repository
clone, and `script.py` is the name of a script file.  

If you like you can skip the `script.py` parameter, and you'll get an interactive jython prompt.

    $ bin/jython
    Jython 2.7.0 (default:9987c746f838, Apr 29 2015, 02:25:11) 
    [Java HotSpot(TM) 64-Bit Server VM (Oracle Corporation)] on java1.8.0_05
    Type "help", "copyright", "credits" or "license" for more information.
    >>> 

You may have a need to set the Java memory limit, which might be too large or too small for your purposes.  To do  this, edit JAVAFLAGS in the bin/jython script (or edit the Makefile and force re-creation of bin/jython).  The default is currently 14G.  For memory-intensive runs it should be set near the actual physical memory available on the machine.

## Using the library

The first step in any build script is to gain access to the Taxonomy
modules:

    from org.opentreeoflife.taxa import Taxonomy
    from org.opentreeoflife.smasher import UnionTaxonomy

## Taxonomies

If you want to synthesize a new taxonomy, initiate the build by creating a new UnionTaxonomy object:

    winner = UnionTaxonomy.newTaxonomy('winner')

`winner` is just an arbitrary name; pick one that's appropriate for
your project.
The argument gives the taxonomy's 'idspace', which is a prefix applied to node
identifiers in log files and certain other places.  E.g. for OTT, this
is `'ott'`.

Taxonomies are usually built starting with one or more existing
source taxonomies (although they needn't be), obtained as follows:

    ncbi = Taxonomy.getTaxonomy('t/tax/ncbi_aster/', 'ncbi')

which fetches a taxonomy from files (in the format specified by this tool,
see wiki).  There can be as many of these retrievals as you like.

The first argument is a directory name and must end in a '`/`'.  
The directory must contain a file 'taxonomy.tsv'.

The second argument is an 'idspace' prefix that will appear in the
'sourceinfo' column of the merged taxonomy file when a node derives from
this source taxonomy, e.g. 'ncbi:1234'.

The format of taxonomy files (taxonomy.tsv and so on) is given [here](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-file-format).

Taxonomies can also be read and written in Newick syntax.

    h2007 = Taxonomy.getNewick('feed/h2007/tree.tre', 'h2007')
    
See [wikipedia](https://en.wikipedia.org/wiki/Newick_format) for a description of Newick format.  Smasher supports an idiosyncratic syntax for specifying rank, e.g. 

    ('Subclass=Sordariomycetidae','Subclass=Hypocreomycetidae')

To align and merge a source taxonomy (e.g. NCBI) into the one under
construction (e.g. `winner`):

    alignment = winner.alignment(source)
    winner.align(alignment)
    winner.merge(alignment)

To write out the taxonomy:

    winner.dump('winner/')

The directory name must end with '/'.  The taxonomy is written to
'winner/taxonomy.tsv', synonyms file to 'winner/synonyms.tsv',
and so on.

The one-argument form `winner.dump(filename)` generates files with columns
separated by tab-verticalbar-tab.  To generate with columns simply
separated by tabs, use

    winner.dump('winner/', '\t')

Taxonomies can also be written as Newick:

    winner.dumpNewick('winner.tre')

but beware that this loses all information about synonyms and sources.
Also beware that if the taxonomy contains homonyms, the Newick file
will contain multiple nodes with the same label, and most tools that
consume Newick don't like this.


## Referring to nodes

A number of scripting commands take nodes as parameters.  There are two
ways to specify a node: by finding it in a taxonomy, or by creating
it anew.

The `taxon()` method looks up a node in a taxonomy by name.  It takes two
forms:

    winner.taxon('Pseudacris', 'Anura')
    winner.taxon('Pseudacris')

Use the second form if you're in a hurry and sure the name is unique
within the taxonomy.  If the name might be ambiguous (a homonym), use
the first form, which provides context.  The context can be any
ancestor of the intended node that is not shared with the other
homonyms - usually something at the class or phylum level.

If there is no such taxon, the `taxon` method throws an exception.
To return null instead, use `maybeTaxon`:

    winner.maybeTaxon('Pseudacris', 'Anura')
    winner.maybeTaxon('Pseudacris')

A variant on `taxon` is to name a descendent of the node, as opposed to
an ancestor:

    winner.taxonThatContains('Anura', 'Pseudacris') #designates Anura

It is also possible to use a node identifier relative to a source taxonomy:

    ncbi.taxon('173133')

but this is brittle as identifiers may change from one version of a
source taxonomy to the next.

To add a new node, provide its name, rank, and source information to
the `newTaxon()` method.  The source information should be a URL or
CURIE that is specific to that node.

    winner.newTaxon('Euacris', 'genus', 'http://mongotax.org/12345')

If the node has no particular rank, put 'no rank'.

## Counts

    taxon.count()  =>  integer
    taxon.tipCount()  =>  integer
    
count() returns the number of nodes tipward of the given node, including the node itself.

tipCount() returns the number of tips (leaf nodes) tipward of the given node.

## Surgery

Whenever making ad hoc modifications to the taxonomy please leave a pointer (i.e. a URL) to some
source of evidence for the correctness of the change.  If
the evidence doesn't go in as the source information in a newTaxon() call, put
it in a comment in the script file.  (Probably the evidence should be an argument to the
various surgery commands; maybe later.)

Add a new node as a daughter of a given one: (would be used with `newTaxon`)

    taxon.addChild(othertaxon)
    e.g.
    winner.taxon('Parentia').addChild(winner.newTaxon('Parentia daughtera', 
       'species', 'http://www.marinespecies.org/aphia.php?p=taxdetails&id=557120'))

Detach an existing node from its current location, and add it as a
daughter of a different parent:

    taxon.take(othertaxon)
    e.g. 
    # From http://www.marinespecies.org/aphia.php?p=taxdetails&id=556811
    winner.taxon('Ammoniinae').take(winner.taxon('Asiarotalia'))

Move the children of node A into node B, and make B be a synonym of
A:  (I.e. the names are synonyms, but not previously recorded as
such):

    taxon.absorb(othertaxon)
    e.g. 
    # From http://www.marinespecies.org/aphia.php?p=taxdetails&id=557120
    winner.taxon('Parentia').absorb(winner.taxon('Parentiola'))

Delete a node and all of its descendants:

    taxon.prune()

Delete all the descendants of a given node: (this is useful for grafting one taxonomy into another)

    taxon.trim()

Delete a node, moving all of its children up one level (e.g. delete a
subfamily making all of its genus children children of the family):

    taxon.elide()

Select a subset of a taxonomy:

    taxonomy.select(taxon)

This returns a new taxonomy whose root is (a copy of) the given node.

(TBD: Need a way to add a root to the forest, or change the root.)

## Alignment

Taxonomy alignment establishes correspondences between nodes in taxonomy A with nodes in taxonomy B, based on node names and topology.  Most of the complexity of this operation has to do with the handling of homonyms.
Sometimes the automatic alignment
logic makes mistakes.  It is then desirable to manually specify that
node X in taxonomy A is the same as node X in taxonomy B (they are not homonyms).

    alignment.same(tax1, tax2)
      e.g.
    alignment.same(ncbi.taxon('X'), winner.taxon('X'))

The first argument must be in the source taxonomy, and the second must be in the merged taxonomy.

    alignment = winner.alignment(source)
    alignment.same(gbif.taxon('Plantae'), winner.taxon('Archaeplastida'))
    winner.align(alignment)
    winner.merge(alignment)

Should the need arise you can find out what a source node maps to, using `image()`:

    alignment.image(gbif.taxon('Sample'))


## Annotation

Add a synonym:

    taxon.synonym('Alternate name')

Rename a node, leaving old name behind as a synonym:

    taxon.rename('Newname')

Mark a node as being 'incertae sedis' i.e. not fully classified:

    taxon.incertaeSedis()

Mark as extinct or extant:

    taxon.extinct()
    taxon.extant()

Mark a node as 'hidden' so that it can be suppressed by tools downstream:

    taxon.hide()
    
## Looking at taxonomies

One can iterate over the nodes of a taxonomy:

    for taxon in taxonomy.taxa(): ...
    
Nodes have lots of properties you might want to look at in a script.
    
    taxon.parent
    taxon.getChildren()     # a List of nodes
    taxon.isHidden()
    
You can iterate over the descendants of a node:

    for d in taxon.descendants(): ...

or over all nodes in a taxonomy:

    for d in taxonomy.taxa(): ...

The roots of a taxonomy (a List):

    taxonomy.roots()

You can make a copy of a taxonomy, selecting only the visible (non-hidden) nodes:

    taxonomy.selectVisible("my taxonomy but only visible")

or make a new taxonomy that is a subtree of a given one:

    rana = ncbi.select('Rana')

## Debugging

Not much here yet, just

    taxon.show()

Displays information about this node: its lineage and children, sources,
flags, etc.
