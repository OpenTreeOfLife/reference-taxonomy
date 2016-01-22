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

    private static final String idspace = "ott";

    private Taxonomy referenceTaxonomy;
    private Taxonomy syntheticTree;

    public static void main(final String... args) throws IOException {
        new Services(args.length > 0 ? Taxonomy.getTaxonomy(args[0], idspace) : null,
                     args.length > 1 ? Taxonomy.getTaxonomy(args[1], idspace) : null)
            .serve("localhost", 8081);
    }

    public Services(Taxonomy reftax, Taxonomy synth) {
        this.referenceTaxonomy = reftax;
        this.syntheticTree = synth;
    }

    // Does not return
    public void serve(String hostname, int port) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), BACKLOG);
        server.createContext("/conflict-status", conflictStatus);
        System.out.format("Starting HTTP server\n");
        server.start();
    }

    interface CGItoJSON {
        JSONObject run(Map<String, String> parameters);
    }

    HttpHandler wrapCGItoJSON(CGItoJSON fun) {
        return exchange -> {
            try {
                if (exchange.getRequestMethod().toUpperCase().equals("GET")) {
                    final Map<String, String> parameters = getParameters(exchange.getRequestURI());
                    Map result = fun.run(parameters);
                    final Headers headers = exchange.getResponseHeaders();
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
                } else
                    nonget(exchange);
            } catch(Exception e) {
                final Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", String.format("text/plain; charset=%s",
                                                          StandardCharsets.UTF_8));
                ByteArrayOutputStream ba = new ByteArrayOutputStream();
                PrintWriter pw = new PrintWriter(ba);
                e.printStackTrace(pw);
                pw.close();

                System.out.println(ba.size());
                exchange.sendResponseHeaders(599, ba.size());
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

    private JSONObject conflictStatus(String treespec1, String treespec2, boolean useCache) {
        try {
            Taxonomy tree1 = specToTree(treespec1, useCache);
            if (tree1 == null) {
                System.err.format("** Can't find %s\n", treespec1);
                return new JSONObject();
            }
            Taxonomy tree2 = specToTree(treespec2, useCache);
            if (tree2 == null) {
                System.err.format("** Can't find %s\n", treespec2);
                return new JSONObject();
            }
            return conflictStatus(tree1, tree2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static JSONObject conflictStatus(Taxonomy tree1, Taxonomy tree2) {
        boolean flipped = false;
        Taxonomy input = tree1, ref = tree2;
        if (tree2.count() < tree1.count()) { // heuristic!
            input = tree2; ref = tree1; flipped = true;
        }
        ConflictAnalysis c = new ConflictAnalysis(input, ref);
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
            case CONGRUENT:
                tag = "=";
                break;
            case REFINES:
                tag = "<";
                break;
            case CONFLICTS:
                tag = "<>";
                break;
            }
            if (tag != null) {
                JSONObject info = new JSONObject();
                Taxon w = a.witness;
                if (w != null && w.id != null)
                    info.put("witness", w.id);
                info.put("status", tag);
                result.put(node.id, info);
            }
        }
        return result;
    }

    public Taxonomy specToTree(String spec, boolean useCache) throws IOException {
        String[] parts = spec.split("#");
        if (parts.length == 0)
            return null;
        else if (parts.length == 1) {
            // Otherwise, use saved ott or synth
            return getReferenceTree(parts[0]);
        } else {
            try {
                return getSourceTree(parts[0], parts[1], useCache);
            } catch (ParseException e) {
                System.err.format("** JSON parse exception for %s\n", spec);
                return null;
            }
        }
    }

    private Taxonomy getReferenceTree(String spec) {
        if (spec.startsWith(idspace))
            return referenceTaxonomy;
        else if (spec.startsWith("synth"))
            return syntheticTree;
        else
            return null;
    }

    private Taxonomy getSourceTree(String studyId, String treeId, boolean useCache)
        throws IOException, ParseException {
        JSONObject study = getStudy(studyId, useCache);
        Taxonomy tree = Nexson.importTree(Nexson.getTrees(study).get(treeId), Nexson.getOtus(study), treeId);
        tree.idspace = studyId;
        return tree;
    }

    private String singleCachedStudyId = null;
    private JSONObject singleCachedStudy = null;

    private JSONObject getStudy(String studyId, boolean useCache) throws IOException, ParseException {
        if (!useCache)
            singleCachedStudyId = null; // Flush it
        if (studyId.equals(singleCachedStudyId))
            return singleCachedStudy;
        else {
            URL url = new URL("https://api.opentreeoflife.org/v2/study/" + studyId + "?output_nexml2json=1.2.1");
            HttpURLConnection conn = (HttpURLConnection)(url.openConnection());
            if (conn.getResponseCode() == STATUS_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                JSONParser parser = new JSONParser();
                JSONObject envelope = (JSONObject)parser.parse(reader);
                JSONObject nexson = (JSONObject)envelope.get("data");
                // JSONObject sha = (JSONObject)envelope.get("sha");
                singleCachedStudyId = studyId;
                singleCachedStudy = nexson; // also "sha" and other stuff
                return nexson;
            } else
                System.err.format("** GET %s yielded %s\n", url, conn.getResponseCode());
                return null;
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
                        System.err.println("UTF-8 is an unsupported encoding");
                    }
                }
            }
        }
        return parameters;
    }

    private static final String ALLOWED_METHODS = "OPTIONS,GET";

    void nonget(HttpExchange exchange) throws IOException {
        final Headers headers = exchange.getResponseHeaders();
        headers.set("Allow", "GET,OPTIONS");
        if (exchange.getRequestMethod().toUpperCase().equals("OPTIONS"))
            exchange.sendResponseHeaders(STATUS_OK, -1);
        else
            exchange.sendResponseHeaders(STATUS_METHOD_NOT_ALLOWED, -1);
    }

}
