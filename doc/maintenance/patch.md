
# Patch language

There have been three patch systems for OTT, which I call versions 1,
2, and 3.

* [Version
  1](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Interim-taxonomy-patch-feature)
  expresses taxonomy modifications and updates as rows of a tabular
  file, and was intended for direct use by Open Tree curators.  It
  turns out biologists are unable to create or understand TSV files,
  so this turned out to not work very well.  In addition, the format
  was limited and inflexible, and trying to shoehorn all possible
  types of change into a common set of columns was awkward.

* [Version
  2](https://github.com/OpenTreeOfLife/reference-taxonomy/blob/master/doc/scripting.md)
  consists of direct manipulation of the tree data structures using 
  Python code.  This method is used extensively in `adjustments.py`
  and `amendments.py`.  The difficulty is that patches that work
  initially will fail when sources such as NCBI are revised, merely
  because the error that was repaired by the patch is no longer
  present in the revised source.  And the failure happens in an awful
  way, by aborting the OTT assembly process, often after 15 minutes of
  processing.  The assembly process has to be started over, so fixing
  many issues like this is a very slow process.

* Version 3 (described here) is again a Python library, but instead of
  describing editing operations on trees, one describes goal states,
  that is, properties that the tree should have.  If the goal cannot
  be achieved, an error is reported, but the assembly does not
  terminate, so many errors can be found and fixed or ignored for each
  trial asssembly.  Some early thoughts on this approach are [in the wiki](https://github.com/OpenTreeOfLife/reference-taxonomy/wiki/Thoughts-on-%27third-generation%27-community-taxonomy-editing-system).

I have started migrating from version 2 to version 3, and this
migration should continue, for the sake of robustness and abstraction.

## Propositions

A patch (or amendment) is given as a proposition construction,
consisting of a proposition-yielding function (predicate) applied to a
set of argument designators (usually taxon designators).  This will
make sense after some examples are shown.

### Taxon record designators

    taxon(name)
    taxon(name, ancestorname)
    taxon(name, descendant=descandantname)

These indicate a taxon, or more precisely a taxon record.

* `taxon(name)` refers to the unique taxon of the given name in the
  taxonomy in question.  The patch is not applied if the name is ambiguous.
* `taxon(name, ancestorname)` or `taxon(name, ancestor=ancestorname)` refers 
  to the unique taxon of the given
  name that descends from a taxon that has name ancestorname.
* `taxon(name, descendant=descendantname)` refers to the unique taxon of the
  given name that has a descendant having name descendantname.
* `taxon(id=tid)` refers to the unique taxon having the taxon record identifier tid.

Examples:

    taxon('Pantoea ananatis LMG 20103')
    taxon('Rotalites', 'Foraminifera')

### Proposition identifiers

    otc(nnn)

Each proposition (the ones written down in .py files, or wherever)
should get a unique identifier so that it can be referred to.  I'm not
sure how to do this right, but currently there is a function `otc(-)`
for specifying an "Open Tree curation" identifier.  The idea is that
eventually these will be linked from the taxonomy, so that users of
OTT can navigate from a point in the taxonomy to the text of the
particular patch directives that affected the taxonomy at that point.

You have to allocate these numbers sequentially.  Currently that means
looking at the `adjustments.py` and `amendments.py` files and finding
some positive integer not used in any `otc(-)` in those files.

Usually when there is an `otc(-)` there is also a URL, DOI, github
issue, or other external reference(s).  TBD: Add these as arguments to
the otc(-) function, and eventually use this structure for navigation.


### Predicates

    has_parent(child, parent, propid)

e.g.

    has_parent(taxon('Rotalites', 'Foraminifera'),
               taxon('Rotalia', 'Foraminifera'),
               otc(49))

This is true (or becomes true) when the child taxon is a direct child
of the parent taxon.

Other predicates:

    synonym_of(synonym, primary, kind, propid)

This is true when the given synonym is a synonym (not primary name) of
the taxon given by primary.  This operator can be used for adding a
synonym, for changing the primary name of a taxon, for turning a
synonym into a primary name, and for merging taxon records.

The `kind` parameter describes what kind of synonym is involved.  It
is not yet drawn from a controlled vocabulary (although it should be).
Possible values are `'objective synonym'`, `'subjective synonym'`,
`'spelling variant'`, `'misspelling'`.  If all else fails just put
`'synonym'` meaning an unknown kind of synonym.

    is_extinct(taxon, propid)

This says that the taxon is (or should be known to be) extinct.

    has_rank(taxon, rank, propid)

This says that the taxon has (or should have) the specified rank.


### Enacting a proposition

    proclaim(taxonomy, proposition)

e.g.

    proclaim(gbif, has_parent(taxon('Rotalites', 'Foraminifera'),
                              taxon('Rotalia', 'Foraminifera'),
                              otc(49)))

The imperative operator `proclaim` alters the given taxonomy, if
necessary, in order to cause the proposition (goal) to be true.


### Testing a proposition

Proposition objects also support a `.check(taxonomy)` method for
determining whether the proposition is true, false, or undetermined in
the given taxonomy.  In the future this should be useful for writing
tests.

Just to give an idea of the flexibility of this approach: If a
proposition is given a name (e.g. is assigned to a python variable),
then it might be proclaimed in one taxonomy (e.g. `gbif`), and then
checked in another (e.g. `ott`).  This would act as a check that no
intermediate patch has canceled out an earlier one.

