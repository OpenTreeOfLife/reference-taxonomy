/**
   Utility for converting a tree in a Nexson file to a Taxonomy object.

   Warning - this code is preliminary, and very brittle.  If you feed
   it something that's not valid Nexson HBF 1.2, you'll get a null
   pointer or class cast exception.
*/


package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.IOException;

import org.json.simple.JSONValue; 
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;



public class Nexson {

    // json is what the JSON parser returned for the whole file.
    //  looks like {"nexml": {..., "treesById": {...}, ...}}
    // return value is a set of subnodes of that.
    public static Map<String, JSONObject> getTrees(JSONObject obj) {
        JSONObject nexmlContent = (JSONObject)(obj.get("nexml"));
        if (nexmlContent == null)
            throw new RuntimeException("No 'nexml' element in json blob");
        JSONObject treesById = (JSONObject)(nexmlContent.get("treesById"));
        /* treesById is {"trees1": {
                                    "@otus": "otus1", 
                                    "^ot:treeElementOrder": ["tree1"], 
                                    "treeById": {
                                                 "tree1": { ...} ...} ...} ...}
        */
        Map<String, JSONObject> result = new HashMap<String, JSONObject>();
        if (treesById != null)
            for (Object treeses : treesById.values()) {
                JSONObject trees = (JSONObject)(((JSONObject)treeses).get("treeById"));
                if (trees == null) {
                    System.err.format("** Missing trees\n");
                    return null;
                }
                for (Object id : trees.keySet()) {
                    result.put((String)id, (JSONObject)(trees.get(id)));
                }
            }
        return result;
    }

    public static Map<String, JSONObject> getOtus(JSONObject obj) {
        JSONObject nexmlContent = (JSONObject)(obj.get("nexml"));
        JSONObject otusById = (JSONObject)(nexmlContent.get("otusById"));
        Map<String, JSONObject> result = new HashMap<String, JSONObject>();
        for (Object otuses : otusById.values()) {
            JSONObject otus = (JSONObject)(((JSONObject)otuses).get("otuById"));
            for (Object id : otus.keySet()) {
                result.put((String)id, (JSONObject)(otus.get(id)));
            }
        }
        return result;
    }


    // JSONObject treeson = getTrees(json).get(treeid);

    /* A 'treeson' looks like this:
      {
         "@label": "Bayesian 50% maj-rul consensus", 
         "@xsi:type": "nex:FloatTree", 
         "^ot:branchLengthDescription": "", 
         "^ot:branchLengthMode": "ot:substitutionCount", 
         "^ot:branchLengthTimeUnit": "", 
         "^ot:curatedType": "Bayesian inference ", 
         "^ot:inGroupClade": "node2", 
         "^ot:messages": ..., 
         "^ot:outGroupEdge": "", 
         "^ot:rootNodeId": "node1", 
         "^ot:specifiedRoot": "node1", 
         "^ot:tag": ..., 
         "^ot:unrootedTree": false, 
         "edgeBySourceId": {
                    "node1": {
                              "edge1": {
                                        "@length": 0.05753, 
                                        "@source": "node1", 
                                        "@target": "node2"
                                        }, 
                              "edge84": {
                                         "@length": 0.028765, 
                                         "@source": "node1", 
                                         "@target": "node85"
                                         }
                              }, ...}
         "nodeById": {
              "node1": {
                        "@root": true
                        }, 
              "node10": {}, 
              "node13": {
                         "@otu": "otu1"
                         }, 
              ...
         }
     }
     */
    /*
        {
        "^ot:originalLabel": "Palaemon pacificus", 
        "^ot:ottId": 512598, 
        "^ot:ottTaxonName": "Palaemon pacificus"
        }
    */
    public static SourceTaxonomy importTree(JSONObject treeson, Map<String, JSONObject> otus, String tag) {
        JSONObject nodes = (JSONObject)treeson.get("nodeById");
        JSONObject sources = (JSONObject)treeson.get("edgeBySourceId");
        SourceTaxonomy tax = new SourceTaxonomy(tag); // arg is idspace, should be study id
        tax.setTag(tag);

        // Make a Taxon object for each NeXML node in the tree
        for (Object idObj : nodes.keySet()) {
            String id = (String)idObj;
            // make one Taxon for every node
            Taxon taxon = new Taxon(tax, null);    // name set later
            taxon.setId(id);
        }
        // Transfer edges over from NeXML to Taxonomy instance
        for (Object idObj : sources.keySet()) {
            String id = (String)idObj;
            Taxon src = tax.lookupId(id);
            for (Object edgeObj : ((JSONObject)sources.get(id)).values()) {
                String targetNodeId = ((String)(((JSONObject)edgeObj).get("@target")));
                src.addChild(tax.lookupId(targetNodeId));
            }
        }

        // Set the ingroup
        tax.ingroupId = (String)treeson.get("^ot:inGroupClade");

        // Set the root
        String rootid = (String)treeson.get("^ot:rootNodeId");
        if (rootid != null && rootid.length() == 0) rootid = null;
        if (rootid != null) {
            Taxon node = tax.lookupId(rootid);
            if (node != null) {
                String specid = (String)treeson.get("^ot:specifiedRoot");
                if (specid != null && !specid.equals(rootid)) {
                    // Specified root is not the represented root
                    Taxon spec = tax.lookupId(specid);
                    if (spec != null) {
                        System.out.format("** Specified root %s not= represented root %s - rerooting NYI (%s)\n",
                                          specid, rootid, tag);
                        Taxon ingroup = tax.ingroupId == null ? null : tax.lookupId(tax.ingroupId);
                        if (ingroup != null && !ingroup.descendsFrom(spec))
                            System.out.format("** BAD: Ingroup %s does not descend from specified root %s (%s)\n",
                                              tax.ingroupId, specid, tag);
                    }
                }
                tax.addRoot(node);
            } else
                System.out.format("** Root node %s not found in %s\n", rootid, tag);
        } else
            System.out.format("** No root node found for %s\n", tag);

        // Store tip labels as Taxon names, OTT ids as sources
        for (Taxon taxon : tax.taxa()) {
            if (taxon.children == null) {
                JSONObject node = (JSONObject)nodes.get(taxon.id);
                /*
                  "Tn10272676": {
                  "@otu": "Tl1140566", 
                  "^ot:isTaxonExemplar": false
                  }, 
                */
                Object otuIdObj = node.get("@otu");
                if (otuIdObj != null) {
                    String otuId = ((String)otuIdObj);
                    JSONObject otu = ((JSONObject)otus.get(otuId));
                    Object label = otu.get("^ot:originalLabel");
                    if (label == null)
                        System.out.format("** No label for terminal node %s, otu = %s (%s)\n", taxon.id, otu, tag);
                    taxon.setName((String)label);

                    Object isExemplar = node.get("^ot:isTaxonExemplar");
                    if (isExemplar != Boolean.FALSE) {
                        Object ottidObj = otu.get("^ot:ottId"); // an integer
                        if (ottidObj != null)
                            taxon.addSourceId(new QualifiedId("ott", ottidObj.toString()));
                    }
                } else {
                    System.out.format("** No @otu for terminal node %s in %s\n",
                                      taxon.id,
                                      tag);
                }
            }
        }
        return tax;
    }

    public static JSONObject load(String path) throws IOException {
		BufferedReader fr = Taxonomy.fileReader(path);
		JSONParser parser = new JSONParser();
		try {
			return (JSONObject)parser.parse(fr);
		} catch (ParseException e) {
			System.err.println(e);
            return null;
		}
    }

}
