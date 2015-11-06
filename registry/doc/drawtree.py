# In SVG, (0,0) is the upper left of a rectangle, with y increasing downwards

from org.opentreeoflife.taxa import Taxonomy

def node_height(node):
    if node.children == None:
        return 0
    else:
        h = 0
        for child in node.children:
            g = node_height(child)
            if g > h:           # max
                h = g
        return h + 1

# (0,0) is lower left, I think

text_height = 20
text_gap = 8
stroke_width = 3.0

# Returns a string

def drawtree(tree):

    root = tree.roots().get(0)
    width = root.tipCount() * 50
    height = node_height(root) * 50

    (elements, stempos) = drawnode(root, (0, text_height), width, height)

    elements = (['<svg width="%s" height="%s">' % (width + 10, height + 10 + text_height + text_gap)]
                + elements +
                ['</svg>'])

    return '\n'.join(elements)

# draw the stem and, if nonterminal, the crossbar
# returns (elements, stempos)

def drawnode(node, startpos, wd, ht):
    (startx, starty) = startpos # Upper left corner of box in which to draw
    if node.children == None:
        midx = startx + (wd / 2)
        elements = [path_element('M %s %s L %s %s' % (midx, starty + ht, midx, starty)),
                    text_element(node.name, midx, starty - text_gap, wd, text_height)]
        # TBD: label
        return (elements, midx)
    else:
        tierheight = ht / (node_height(node) + 1)
        tipwidth = wd / node.tipCount()
        bottomy = starty + ht
        bary = bottomy - tierheight
        # tips at top (starty), stem at bottom (bottomy)
        barleft = None
        barright = None
        elements = []
        childx = startx
        for child in node.children:
            childwd = tipwidth * child.tipCount()
            (elts, stempos) = drawnode(child, (childx, starty), childwd, ht - tierheight)
            childx += childwd
            if barleft == None: barleft = stempos
            barright = stempos
            elements += elts

        # crossbar
        elements.append(path_element('M %s %s L %s %s' % (barleft - stroke_width/2, bary, barright + stroke_width/2, bary)))
        # stem
        stemx = (barleft + barright) / 2
        elements.append(path_element('M %s %s L %s %s' % (stemx, bottomy, stemx, bary)))
        if (node.name != None):
            elements.append(text_element(node.name, stemx + 10, bary + text_height, tipwidth, text_height))

    return (elements, stemx)

# Manipulating XML as strings is a well known BAD PRACTICE

def path_element(d):
    return ('<path d="%s" stroke="black" stroke-width="%s" fill="none" />' % (d, stroke_width))

def text_element(string, x, y, wd, ht):
    return ('''<text x="%s" y="%s" width="%s" height="%s" text-anchor="middle">%s</text>''' %
            (x, y, wd, ht, string))


outfile = open('tmp.html', 'w')


# Introduction: Taxonomy-like polytomy with outgroup
print >>outfile, ('''

<p>These are some notes intended to help understand the proposal to
create a system of node identifiers that are defined according to
"splits," or inclusion/exclusion set pairs.</p>

<h2>Node identifiers based on sampled inclusion/exclusion</h2>

<p>Consider the following tree:</p>

%s

<p>Suppose the system generates an identifier - say, 327 -
meant to be used to refer to node m.  Suppose id 327 is registered with
inclusions c, d, e and exclusions x and y (i.e. the "split" {c d e | x
y}).</p>

<p>Below we consider a series of possible "changes" to the tree,
i.e. trees that are variants on the original one given above.  These
might be made as a result of incorporating new taxonomic or
phylogenetic information.  After the change, we ask whether id 327 is
effective at determining a node in the changed tree that we would want to consider
the "same" as m in any sense.  </p>

<p>All of the hazards listed below are inherent in the sampling
approach to internal node identifier definition.</p>''' %

    drawtree(Taxonomy.getTaxonomy(u'((a,b,c,d,e)m,(x,y));')))


# --- Unique resolution
print >>outfile, ('''

<h4>Success (unique resolution in spite of "change")</h4>

%s

<p> In the modified tree, where the polytomy has been refined, we recover m from identifier 327 because m is the only node from which all the inclusions descend and none of the exclusions descend.</p>''' %

    drawtree(Taxonomy.getTaxonomy(u'((a,((b,c),d),e)m,(x,y));')))

# --- False positive
print >>outfile, ('''

<h4>False positive</h4>

Consider a change which moves b out of the a-e polytomy:</p>

%s

<p>It may be common knowledge that b is an m, that having b as a
member is essential to m-ness.  Perhaps b is m's "type".  However, our
automated system does not know this, and in creating the identifier
327 didn't record b among the inclusion samples.  Identifier 327
resolves uniquely in the changed tree to the node here labeled 'm1', even
though b does not descend from it.  The node labeled 'm2' is probably the one
that should get the label 'm', if b has to descend from it.</p>''' %

    drawtree(Taxonomy.getTaxonomy(u'((a,c,d,e)m1,(x,b,y))m2;')))


# --- Ambiguous
print >>outfile, ('''

<h4>Ambiguous resolution</h4>

<p>If the polytomy were to be refined as follows:</p>

%s

<p> then id 327 would not resolve unambiguously to m, because 
two nodes are compatible with the split (one is the MRCA of the inclusions, and the other is the one labeled 'm').
There is no way to tell which would match the intentions of whoever decided to use the identifier in the first place.</p>

<p>(There could be other information accompanying the original reference 327, however, 
such as a name, and maybe the name could be used to decide which node is meant.)</p>''' %

    drawtree(Taxonomy.getTaxonomy(u'((a,(b,c,d,e)m?)m?,(x,y));')))


# --- Inconsistent
print >>outfile, ('''

<h4>No resolution</h4>

<p>On the other hand, the change might tell us
that x is "actually" inside of m, not outside it.  (This might happen if
node m is supposed to be an apomorphy-based clade and there had been
an earlier error implying that x did not possess the apomorphy when it
did.)</p>

%s

<p>Now there are no nodes in the tree compatible with the 327 split, i.e.
every node from which the inclusions all descend also has at least one of the exclusions descending from it.</p>

<p>(It is of course easy to find a nearby node that might serve pretty well
(for navigation purposes at least); e.g. one could use the MRCA of the inclusions.)</p>

<p>It should be clear that a problem with cross-tree node identifiers is that
it's not clear until one wants to use an identifier what meaning one
wants to give it.  At least with this approach one can say exactly
what an identifier means both inferentially and operationally: it
means a clade containing the inclusions and not containing the
exclusions.  If the inclusion and exclusion sets are made apparent to
users, and expectations are set correctly, this should be a pretty
strong system, and perhaps not worse than any other.''' %

    drawtree(Taxonomy.getTaxonomy(u'((a,b,c,x,d,e),y);')))


# --- Relocation
print >>outfile, ('''

<h4>Taxon relocation</h4>

<p>A taxon might "move" out of clade m.  If the changed tree doesn't include one of the non-samples, e.g., nothing much changes; we still get unique resolution.  This might happen either due to fixing a clerical error, i.e. when its placement in m was just a mistake.  It could also be due to scientific revision (replacing an earlier hypothesis with a newer, better one).</p>

%s

<p>A taxon could also be "deleted"; the situation is similar.</p>''' %

    drawtree(Taxonomy.getTaxonomy(u'((b,c,d,e)m,(a,(x,y)));')))


# --- Sample loss
print >>outfile, ('''

<h4>Loss of a sample</h4>

<p>Often we can lose one or more
samples without forfeiting unique resolution.</p>

%s

<p>Sample loss can also create ambiguity.  If all inclusion samples are
lost, all bets are off.</p>''' %

    drawtree(Taxonomy.getTaxonomy(u'((a,b,d,e)m,y);')))


# --- New taxa
print >>outfile, ('''

<h4>Gaining new taxa</h4>

<p>Often the addition of a taxon will make no difference, but a new
taxon can create an ambiguity for an identifier when it's inserted above the node the identifier originally resolved to:</p>

%s

<p></p>''' %

    drawtree(Taxonomy.getTaxonomy(u'(((a,b,c,d,e)m?,z)m?,(x,y));')))

# --- More

print >>outfile, ('''

<h2>Discussion</h2>

<h3>Identifier succession</h3>

<p>When resolving an ambiguity, it may be useful to create a new
identifier for each candidate node.  Then we can use the new
identifiers to refer unambiguously to those nodes, and 'retire' the
original identifier.</p>

<p>When creating a new identifier from an old one, it may be useful
for the inclusion set of the new id be a superset of the inclusion set
of the ambiguous id, or the same as it, and similarly for the exclusion sets.</p>

<p>When this is done, it would make sense to record the fact that id2
derives from id1, since this might come in handy (e.g. when searching
an annotation database).</p>

<p>If there is a way to figure out which of the new identifiers is the
proper "heir" to id1 in a new tree, then one might say that the "heir"
id2 is a new "version" of id1.  But there doesn't seem to be any good
way to designate an "heir", since such a relationship would be
dependent on which pair of trees one was talking about.  So it's not
clear (to me) how useful or coherent this idea is (even though I had
championed it earlier).</p>

<p>So although we had talked about versioning, I'm not sure now that
we need it, and if we don't need it, we don't need to figure out how
to make it make sense.</p>

<h3>Names</h3>

<p>We currently have procedures for assigning names (labels) to
internal nodes.  The names come from the source taxonomies.</p>

<p>There is no need for the id registry to get involved with names
associated with identifiers, since they are not needed for identifier
assignment or resolution, and the names can be assigned independently
by the taxonomy manager (or whatever process it is that generates the
tree).</p>

<p>We could store one or more names in the table that defines the
identifiers, but as these change over time this would only be for
debugging purposes.</p>

<p>Since name assignment and identifier assignment are orthogonal, it
will frequently be the case that the "same" node as judged by name
and/or metadata will have different ids in two trees (either because
membership changes or because of an ambiguity), and that the "same"
node as judged by id will have different names in two trees (usually
because of taxonomic information updates).</p>

<p>E.g. the genus <i>Anolis</i> is subject to lumping and splitting.
After each lumping or splitting event, one can expect the id
associated with the name <i>Anolis</i> to change.  The different ids
will refer variously to larger or smaller groups of species.  </p>

<p>Similarly it might be discovered from identical identifier
assignment that two names (e.g. from older and newer versions of a
tree) name the same group, even though no there was no synonymy stated
in the source taxonomies.</p>


<h3>Assigning identifiers to nodes in trees</h3>

<p>Both the taxonomy manager and the synthetic tree pipeline create
internal nodes with attached metadata.  The registry manager is
perfectly capable of assigning identifiers to all internal nodes,
without appeal to any of the metadata.</p>

<p>These identifiers can then be used in forming URLs, placed in data
structures, and so on, and later resolved to nodes in the same tree or
a different tree, by looking up the samples and performing MRCA
operations.  As seen above, most of the time there will be a unique
node in the tree for the identifier, but sometimes there will be more
than one, or zero.  These situations can be handled in a
discrimination UI, or at the API level, exceptions can be raised,
flags can control what happens in the exceptional cases, etc.</p>

<p>Another boundary case worth noting is that two different
identifiers might resolve uniquely to the same node.  (And they might
resolve ambiguously in another tree, or to different nodes in yet
another.)  These aliases shouldn't matter for the purpose to which
these identifiers are put, which is referring to nodes.</p>

''')

outfile.close()

