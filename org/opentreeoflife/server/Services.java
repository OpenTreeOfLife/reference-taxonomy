// Web services.
// Had to rewrite this because the original was CC-BY-SA.

package org.opentreeoflife.server;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLDecoder;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser; 
import org.json.simple.parser.ParseException;

import org.opentreeoflife.conflict.ConflictAnalysis;
import org.opentreeoflife.conflict.Disposition;
import org.opentreeoflife.conflict.Articulation;
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Nexson;

public class Services {

    private static final int BACKLOG = 10;
    private static final int STATUS_OK = 200;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;
    private static final String ALLOWED_METHODS = "OPTIONS,GET";

    private static final String idspace = "ott";

    private Taxonomy referenceTaxonomy;
    private Taxonomy syntheticTree;
    private String studyBase;

    static class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }

    public static void main(final String... args) throws IOException {
        new Services(args.length > 0 ? Taxonomy.getTaxonomy(args[0], idspace) : null,
                     args.length > 1 ? Taxonomy.getTaxonomy(args[1], idspace) : null,
                     args.length > 2 ? args[2] : "https://api.opentreeoflife.org/v2/study/")
            .serve("localhost", 8081);
    }

    public Services(Taxonomy reftax, Taxonomy synth, String studyBase) {
        this.referenceTaxonomy = reftax;
        this.syntheticTree = synth;
        this.studyBase = studyBase;
    }

    // Does not return
    public void serve(String hostname, int port) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), BACKLOG);
        server.createContext("/conflict-status", conflictStatus);
        server.createContext("/compare", conflictStatus);
        System.out.format("Starting HTTP server on port %s\n", port);
        server.start();
    }

    interface CGItoJSON {
        JSONObject run(Map<String, String> parameters);
    }

    HttpHandler wrapCGItoJSON(CGItoJSON fun) {
        return exchange -> {
            final Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Methods", ALLOWED_METHODS);
            try {
                if (exchange.getRequestMethod().toUpperCase().equals("GET")) {
                    final Map<String, String> parameters = getParameters(exchange.getRequestURI());
                    Map result = fun.run(parameters);
                    headers.set("Content-Type", String.format("application/json; charset=%s",
                                                              StandardCharsets.UTF_8));
                    ByteArrayOutputStream ba = new ByteArrayOutputStream();
                    PrintWriter pw = new PrintWriter(ba);
                    JSONObject.writeJSONString(result, pw);
                    pw.close();

                    System.out.println(ba.size());
                    exchange.sendResponseHeaders(STATUS_OK, ba.size());
                    OutputStream out = exchange.getResponseBody();
                    ba.writeTo(out);
                    out.close();
                } else {
                    headers.set("Allow", "GET,OPTIONS");
                    if (exchange.getRequestMethod().toUpperCase().equals("OPTIONS")) {
                        headers.set("Access-Control-Allow-Headers", "content-type");
                        exchange.sendResponseHeaders(STATUS_OK, -1);
                    } else
                        exchange.sendResponseHeaders(STATUS_METHOD_NOT_ALLOWED, -1);
                }
                
            } catch(Exception e) {
                headers.set("Content-Type", String.format("text/plain; charset=%s",
                                                          StandardCharsets.UTF_8));
                ByteArrayOutputStream ba = new ByteArrayOutputStream();
                PrintWriter pw = new PrintWriter(ba);
                e.printStackTrace(pw);
                pw.close();

                System.out.println(ba.size());
                int status = 500;
                if (e instanceof BadRequest)
                    status = 400;
                exchange.sendResponseHeaders(status, ba.size());
                OutputStream out = exchange.getResponseBody();
                ba.writeTo(out);
                out.close();
            } finally {
                exchange.close();
            }
        };
    }

    private HttpHandler conflictStatus =
        wrapCGItoJSON(new CGItoJSON() {
                public JSONObject run(Map<String, String> parameters) {
                    boolean useCache = true;
                    String useCacheParam = parameters.get("use_cache");
                    if (useCacheParam != null && useCacheParam.equals("false"))
                        useCache = false;
                    return conflictStatus(parameters.get("tree1"),
                                          parameters.get("tree2"),
                                          useCache);
                }
            });

    public JSONObject conflictStatus(String treespec1, String treespec2, boolean useCache) {
        if (treespec1 == null) throw new BadRequest("missing tree1");
        if (treespec2 == null) throw new BadRequest("missing tree2");
        try {
            Taxonomy tree1 = specToTree(treespec1, useCache);
            if (tree1 == null)
                throw new BadRequest(String.format("Can't find %s", treespec1));
            Taxonomy tree2 = specToTree(treespec2, useCache);
            if (tree2 == null)
                throw new BadRequest(String.format("Can't find %s", treespec2));
            return conflictStatus(tree1, tree2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JSONObject conflictStatus(Taxonomy tree1, Taxonomy tree2) {
        boolean flipped = false;
        Taxonomy input = tree1, ref = tree2;
        if (tree2.count() < tree1.count()) { // heuristic!
            input = tree2; ref = tree1; flipped = true;
        }
        ConflictAnalysis c = new ConflictAnalysis(input, ref);
        if (c.inducedIngroup == null)
            throw new BadRequest("No mapped OTUs");
        JSONObject result = new JSONObject();
        Taxon start = flipped ? c.inducedRoot : c.ingroup;
        for (Taxon node : start.descendants(true)) {
            if (node.id == null) {
                System.err.format("** id-less node %s\n", node);
                continue;
            }
            Articulation a = c.articulation(node);
            if (a == null) continue;
            String tag = null;
            switch (a.disposition) {
            case NONE: break;
            case SUPPORTED_BY:
                tag = "supported_by";
                break;
            case PATH_SUPPORTED_BY:
                // https://github.com/OpenTreeOfLife/opentree/wiki/Open-Tree-of-Life-APIs-v3#conflict-api-response-node-fields
                tag = "partial_path_of";
                break;
            case RESOLVES:
                tag = "resolves";
                break;
            case CONFLICTS_WITH:
                tag = "conflicts_with";
                break;
            }
            if (tag != null) {
                JSONObject info = new JSONObject();
                Taxon w = a.witness;
                if (w != null && w.id != null && !w.id.startsWith("-")) {
                    info.put("witness", w.id);
                    if (w.name != null)
                        info.put("witness_name", w.name);
                }
                info.put("status", tag);
                result.put(node.id, info);
            }
        }
        return result;
    }

    public Taxonomy specToTree(String spec, boolean useCache) throws IOException {
        String[] parts = spec.split("#");
        if (parts.length == 0)
            throw new BadRequest("Empty tree specifier");
        else if (parts.length == 1)
            // Otherwise, use saved ott or synth
            return getReferenceTree(parts[0]);
        else if (parts.length == 2)
            return getSourceTree(parts[0], parts[1], useCache);
        else
            throw new BadRequest(String.format("Too many #'s: %s", spec));
    }

    private Taxonomy getReferenceTree(String spec) {
        if (spec.startsWith(idspace))
            return referenceTaxonomy;
        else if (spec.startsWith("synth"))
            return syntheticTree;
        else
            throw new BadRequest(String.format("Tree %s not known", spec));
    }

    public Taxonomy getSourceTree(String studyId, String treeId, boolean useCache)
        throws IOException {
        JSONObject study = getStudy(studyId, useCache);
        if (study == null)
            throw new BadRequest(String.format("Study %s not found", studyId));
        JSONObject jtree = Nexson.getTrees(study).get(treeId);
        if (jtree == null)
            throw new BadRequest(String.format("Tree %s not found in study %s", treeId, studyId));
        Map<String, JSONObject> otus = Nexson.getOtus(study);
        if (otus.size() == 0)
            throw new BadRequest(String.format("No OTUs found in study %s", studyId));
        Taxonomy tree = Nexson.importTree(jtree, otus, treeId);
        tree.idspace = studyId;
        return tree;
    }

    private String singleCachedStudyId = null;
    private JSONObject singleCachedStudy = null;

    public JSONObject getStudy(String studyId, boolean useCache) throws IOException {
        if (!useCache)
            singleCachedStudyId = null; // Flush it
        if (studyId.equals(singleCachedStudyId)) {
            System.out.format("Using cached %s %s\n", studyId, useCache);
            return singleCachedStudy;
        } else {
            URL url = new URL(studyBase + studyId + "?output_nexml2json=1.2.1");
            HttpURLConnection conn = (HttpURLConnection)(url.openConnection());
            if (conn.getResponseCode() == STATUS_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                try {
                    JSONParser parser = new JSONParser();
                    JSONObject envelope = (JSONObject)parser.parse(reader);
                    JSONObject nexson = (JSONObject)envelope.get("data");
                    if (nexson == null)
                        throw new BadRequest(String.format("No 'data' property in response to GET %s", url));
                    if (nexson.get("nexml") == null)
                        throw new BadRequest(String.format("No 'nexml' element in json data blob from %s", url));

                    // JSONObject sha = (JSONObject)envelope.get("sha");
                    singleCachedStudyId = studyId;
                    singleCachedStudy = nexson; // also "sha" and other stuff
                    System.out.format("Cached %s %s\n", studyId, useCache);
                    return nexson;
                } catch (ParseException e) {
                    System.err.format("** JSON parse exception for study %s\n", studyId);
                    throw new BadRequest(String.format("JSON parse error for study %s (see log)", studyId));
                }
            } else
                throw new BadRequest(String.format("** GET %s yielded %s\n", url, conn.getResponseCode()));
        }
    }

    private Map<String, String> getParameters(final URI requestUri) {
        final Map<String, String> parameters = new HashMap<>();
        final String requestQuery = requestUri.getRawQuery();
        if (requestQuery != null) {
            for (String nameValue : requestQuery.split("&", -1)) {
                String[] nameValuePair = nameValue.split("=", 2);
                if (nameValuePair.length > 0) {
                    try {
                        String name = URLDecoder.decode(nameValuePair[0], "UTF-8");
                        String value = nameValuePair.length > 1 ? URLDecoder.decode(nameValuePair[1], "UTF-8") : null;
                        // Last one wins
                        parameters.put(name, value);
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("UTF-8 is an unsupported encoding !?");
                    }
                }
            }
        }
        return parameters;
    }
}
