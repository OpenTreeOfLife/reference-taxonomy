# In SVG, (0,0) is the upper left of a rectangle, with y increasing downwards

import sys
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

    elements = (['<svg width="%s" height="%s" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">' %
                 (width + 10, height + 10 + text_height + text_gap)]
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


def newick_to_svg(newick, outfilename):
    outfile = open(outfilename, 'w')
    print >>outfile, '''<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">'''
    print >>outfile, drawtree(Taxonomy.getTaxonomy(newick))
    outfile.close()

newick_to_svg(u'((a,b,c,d,e)m,(x,y));', 'svg/example.svg')
newick_to_svg(u'(((a,(b,c)),(d,e))m,(x,y));', 'svg/unique-resolution.svg')
newick_to_svg(u'((a,c,d,e)m1,(x,b,y))m2;', 'svg/false-positive.svg')
newick_to_svg(u'((a,(b,c,d,e)m1)m2,(x,y));', 'svg/ambiguous-resolution.svg')
newick_to_svg(u'((a,b,c,x,d,e),y);', 'svg/no-resolution.svg')
newick_to_svg(u'((b,c,d,e)m,(a,(x,y)));', 'svg/relocation.svg')
newick_to_svg(u'((a,b,d,e)m,y);', 'svg/sample-loss.svg')
newick_to_svg(u'(((a,b,c,d,e)m?,w)m?,(x,y));', 'svg/new-taxon.svg')
