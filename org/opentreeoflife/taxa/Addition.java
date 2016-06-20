package org.opentreeoflife.taxa;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.io.File;
import java.io.FilenameFilter;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import org.json.simple.JSONObject; 
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.QualifiedId;

public class Addition {

    // Mint an id for each taxon in the taxon list.
    // Parents must occur before their children in the taxon list.

    public static void assignNewIds(List<Taxon> nodes, long maxid, String additionsPath) {
        // Give each node a tag
        int counter = 0;
        Map<Taxon, String> taxonToTag = new HashMap<Taxon, String>();
        Map<String, Taxon> tagToTaxon = new HashMap<String, Taxon>();
        for (Taxon node : nodes) {
            String tag = "taxon" + Integer.toString(++counter);
            taxonToTag.put(node, tag);
            tagToTaxon.put(tag, node);
        }
        // compose the additions request per 
        // https://github.com/OpenTreeOfLife/germinator/wiki/Taxonomic-service-for-adding-new-taxa
        Map<String, Object> request = generateRequest(nodes, taxonToTag, counter);

        if (false) {            // for debugging
            try {
                PrintStream out = Taxonomy.openw(additionsPath + "/request.json.tmp");
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
                JSONObject.writeJSONString(request, pw);
                pw.close();
                out.close();
            } catch (Exception e) {
                // IOException, UnsupportedEncodingException
                e.printStackTrace();
            }
        }

        // Fake service - convert request to response.
        // It would be more realistic if it went over the request blob.

        Map<String, String> idAssignments = null;

        try {
            Object response = invokeAdditionService(request, counter, maxid+1, additionsPath);
            Map responseMap = (Map)response; // maps tag to OTT id
            Object err = responseMap.get("error");
            if (err != null)
                System.out.format("** Error from service: %s\n", err);
            else {
                idAssignments = new HashMap<String, String>();
                for (Object tag : responseMap.keySet())
                    idAssignments.put((String)tag, Long.toString((Long)responseMap.get(tag)));
                System.out.format("| Got %s tag/id assignments\n", idAssignments.size());
            }
        } catch (Exception e) {
            System.err.format("** Exception in assignNewIds: %s %s\n", e.getClass().getName(), e.getMessage());
            e.printStackTrace();
        }
        if (idAssignments == null) {
            // cheat
            idAssignments = new HashMap<String, String>();
            for (Taxon node : taxonToTag.keySet())
                idAssignments.put(taxonToTag.get(node), Long.toString(++maxid));
        }

        // Process result of calling service (idAssignments)

        if (idAssignments.size() > 0) {
            long least = Long.MAX_VALUE;
            long greatest = Long.MIN_VALUE;
            for (String tag : idAssignments.keySet()) {
                Taxon node = tagToTaxon.get(tag);
                if (node == null) {
                    System.out.format("** No node with tag %s\n", tag);
                    continue;
                }
                String id = idAssignments.get(tag);
                node.setId(id);
                node.markEvent("new-id");
                long lid = Long.parseLong(id);
                if (lid < least) least = lid;
                if (lid > greatest) greatest = lid;
            }
            if (greatest > least)
                System.out.format("| New ids run from %s to %s\n", least, greatest);
        }
    }

    // Invoke either as a shell script, or as a web service

    static Object invokeAdditionService(final Object request, int count, long minid, String additionsPath)
        throws IOException, InterruptedException, ParseException
    {
        ProcessBuilder pb =
            new ProcessBuilder("/usr/bin/python", "util/process_addition_request.py",
                               "--dir", additionsPath,
                               "--count", Integer.toString(count),
                               "--min", Long.toString(minid));
        Process p = pb.start();
        final PrintWriter pw = new PrintWriter(new OutputStreamWriter(p.getOutputStream()));
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        InputStream er = p.getErrorStream();

        if (false) {
            Thread th = new Thread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject.writeJSONString((Map)request, pw);
                            pw.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            th.start();
        } else {
            JSONObject.writeJSONString((Map)request, pw);
            pw.close();
        }

        // debugging
        if (false) {
            byte[] foo = new byte[1000];
            for (int i = 0; i <= 3; ++i) {
                try {
                    System.out.print('.');
                    Thread.sleep(1000);            //1000 milliseconds is one second.
                } catch(InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                if (er.available() > 0) {
                    int n = er.read(foo);
                    System.out.print(new String(foo, 0, n));
                }
            }
        }

        Object obj = (new JSONParser()).parse(br);
        p.waitFor();
        return obj;
    }

    // Generate the JSON blob to go in the taxon-addition service request.
    // Returns map from tag to node.

    public static Map<String, Object> generateRequest(List<Taxon> nodes, Map<Taxon, String> taxonToTag, int count) {
        Map<String, Object> m = new HashMap<String, Object>();
        List<Object> descriptions = new ArrayList<Object>();
        for (Taxon node : nodes) {
            Map<String, Object> description = new HashMap<String, Object>();
            description.put("tag", taxonToTag.get(node));
            if (node.name != null)
                description.put("name", node.name);
            if (node.rank != Rank.NO_RANK)
                description.put("rank", node.rank);
            if (node.parent.id != null)
                description.put("parent", node.parent.id);
            else {
                String parentTag = taxonToTag.get(node.parent);
                if (parentTag != null)
                    description.put("parent_tag", parentTag);
                else
                    System.out.format("** Parent %s has neither id nor tag\n", node.parent);
            }
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
        m.put("original", Boolean.FALSE);
        return m;
    }

    public static List<File> listAdditionDocuments(String dir) {
        return listAdditionDocuments(new File(dir));
    }

    public static List<File> listAdditionDocuments(File dir) {
        FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("additions-") && name.endsWith(".json");
                }
            };
        File[] files = dir.listFiles(filter);
        if (files == null)      // directory doesn't exist
            return new ArrayList<File>();
        List<File> listOfFiles = Arrays.asList(files);
        listOfFiles.sort(compareFiles);
        return listOfFiles;
    }

	static Comparator<File> compareFiles = new Comparator<File>() {
		public int compare(File x, File y) {
            String a = x.getName();
            String b = y.getName();
            int compareLengths = a.length() - b.length();
            if (compareLengths != 0) return compareLengths;
            return a.compareTo(b);
		}
	};

    // Deal with one additions document.  Get ids for existing nodes, or (if
    // the taxa are "original" with this document) create 
    // new nodes as needed.

    public static void processAdditionDocument(File file, Taxonomy tax) throws IOException, ParseException {
		BufferedReader fr = Taxonomy.fileReader(file);
		JSONParser parser = new JSONParser();
        Object obj = parser.parse(fr);
        processAdditionDocument(obj, tax);
    }

    public static void processAdditionDocument(Object json, Taxonomy tax) throws ParseException {
        if (json instanceof Map) {
            Map top = (Map)json;
            Object originalObj = top.get("original");
            boolean originalp = (originalObj == null || (Boolean)(originalObj));
            String additionSource = (String)top.get("id");
            Object taxaObj = top.get("taxa");
            List taxa = (List)taxaObj;
            Map<String, Taxon> tagToTaxon = new HashMap<String, Taxon>();
            for (Object descriptionObj : taxa) {
                Map description = (Map)descriptionObj;
                String ott_id = toId(description.get("ott_id"));
                String tag = (String)(description.get("tag"));
                String name = (String)(description.get("name"));
                String parentId = toId(description.get("parent"));
                String parentTag = (String)(description.get("parent_tag"));

                if (tag == null) {
                    System.out.format("** Missing tag\n");
                    continue;
                }
                if (name == null) {
                    System.out.format("** Missing name for %s\n", tag);
                    continue;
                }
                if (ott_id == null) {
                    System.out.format("** Missing OTT id for %s\n", name);
                    continue;
                }

                // Get parent taxon
                Taxon parent;
                if (parentId != null) {
                    parent = tax.lookupId(parentId);
                    if (parent == null) {
                        System.out.format("** Parent %s not found for added taxon %s\n", parentId, name);
                        continue;
                    }
                } else if (parentTag != null) {
                    parent = tagToTaxon.get(parentTag);
                    if (parent == null) {
                        System.out.format("** Parent %s not found for added taxon %s\n", parentTag, name);
                        continue;
                    }
                } else {
                    System.out.format("** No parent specified for %s\n", name);
                    continue;
                }

                // Get target taxon
                Taxon target = tax.lookupId(ott_id);
                if (target != null) {
                    // Seems to be already there!  See if when we found matches what we expect.
                    if (!name.equals(target.name))
                        System.out.format("** Requested name %s not same as prior name %s for %s\n",
                                          name, target.name, ott_id);
                    if (!parentId.equals(target.parent.id))
                        System.out.format("** Requested parent %s not same as prior parent %s for %s %s\n",
                                          parentId, target.parent.id, name, ott_id);
                } else {
                    // Find existing node - one with same name and parent
                    List<Node> nodes = tax.lookup(name);
                    if (nodes != null)
                        for (Node node : nodes) {
                            if (node.taxon().parent == parent) {
                                if (target == null)
                                    target = node.taxon(); // id might be different
                                else
                                    // Malformed taxonomy
                                    System.out.format("** Ambiguous taxon determination in addition: %s %s %s %s\n",
                                                      name, parent, node.taxon(), target);
                            }
                        }
                    if (target != null) {
                        if (target.id == null)
                            target.setId(ott_id);
                        else
                            target.taxonomy.idIndex.put(ott_id, target);
                    } else {
                        // if (!originalp) continue;
                        target = new Taxon(tax, name);
                        target.setId(ott_id);
                        parent.addChild(target);
                        String rank = (String)description.get("rank");
                        if (rank != null && Rank.getRank(rank) != null)
                            target.rank = rank; // should complain if not
                        if (additionSource != null)
                            target.addSourceId(new QualifiedId(additionSource, ott_id));
                        else {
                            List sources = (List)(description.get("sources"));
                            for (Object sourceStuff : sources) {
                                Map sourceDescription = (Map)sourceStuff;
                                String source = (String)(sourceDescription.get("source"));
                                target.addSourceId(new QualifiedId(source));
                            }
                        }
                        if (name.equals("Albizia"))
                            System.out.format("| debug %s\n", target);
                    }
                }
                // For backward references
                tagToTaxon.put(tag, target);
            }
        } else
            throw new RuntimeException("bad json for addition");
    }
    
    static String toId(Object ottIdObj) {
        if (ottIdObj instanceof Long)
            return Long.toString((Long)ottIdObj);
        else if (ottIdObj instanceof Integer)
            return Integer.toString((Integer)ottIdObj);
        else
            return (String)ottIdObj;
    }

}
