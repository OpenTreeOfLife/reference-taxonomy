package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.opentreeoflife.taxa.QualifiedId;

public class Addition {

    // Create new nodes as needed per addition document

    static void processAdditions(Object json, Taxonomy tax) {
        if (json instanceof Map) {
            Map top = (Map)json;
            Object taxaObj = top.get("taxa");
            List taxa = (List)taxaObj;
            Map<String, Taxon> tagToTaxon = new HashMap<String, Taxon>();
            for (Object descriptionObj : taxa) {
                Map description = (Map)descriptionObj;
                String tag = (String)(description.get("tag"));
                String ott_id = (String)(description.get("ott_id"));
                String name = (String)(description.get("name"));
                String parentId = (String)(description.get("parent"));
                Taxon parent = tax.lookupId(parentId);
                if (parent == null) {
                    System.out.format("** parent %s not found in addition %s\n", parentId, name);
                    continue;
                }
                Taxon target = null;
                // Find existing node - one with same name and parent
                for (Node node : tax.lookup(name)) {
                    if (node.taxon().parent == parent) {
                        if (target == null)
                            target = node.taxon();
                        else
                            // malformed taxonomy
                            System.out.format("** ambiguous taxon determination in addition: %s %s %s %s\n",
                                               name, parentId, node.taxon(), target);
                    }
                }
                if (target == null) {
                    target = new Taxon(tax, name);
                    String rank = (String)description.get("rank");
                    if (rank != null && Rank.getRank(rank) != null)
                        target.rank = rank; // should complain if not
                    // tbd: deal with sources
                    List sources = (List)(description.get("sources"));
                    for (Object sourceStuff : sources) {
                        Map sourceDescription = (Map)sourceStuff;
                        String source = (String)(sourceDescription.get("source"));
                        target.addSourceId(new QualifiedId(source));
                    }
                }
                tagToTaxon.put(tag, target);
            }
        } else
            throw new RuntimeException("bad json for addition");
    }
    
    // Generate the JSON blob to go in the taxon-addition service request

    public static Object generateAdditions(List<Taxon> nodes, Map<Taxon, String> tagAssignments) {
        Map<String, Object> m = new HashMap<String, Object>();
        List<Object> descriptions = new ArrayList<Object>();
        for (Taxon node : nodes) {
            Map<String, Object> description = new HashMap<String, Object>();
            description.put("tag", tagAssignments.get(node));
            if (node.name != null)
                description.put("name", node.name);
            if (node.rank != Rank.NO_RANK)
                description.put("rank", node.rank);
            description.put("parent", node.parent.id);
            if (node.sourceIds != null) {
                List<Object> sources = new ArrayList<Object>();
                for (QualifiedId qid : node.sourceIds) {
                    Map<String, Object> sourceDescription = new HashMap<String, Object>();
                    sourceDescription.put("source", qid.toString());
                    sources.add(sourceDescription);
                }
                description.put("sources", sources);
            }
            descriptions.add(description);
        }
        m.put("taxa", descriptions);
        return m;
    }

}
