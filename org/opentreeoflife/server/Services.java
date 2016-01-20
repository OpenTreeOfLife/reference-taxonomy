// Web services.
// Had to rewrite this because the original was CC-BY-SA.

package org.opentreeoflife.server;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
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
import org.opentreeoflife.taxa.Taxonomy;
import org.opentreeoflife.taxa.Taxon;
import org.opentreeoflife.taxa.Nexson;

public class Services {

    private static final int BACKLOG = 10;
    private static final int STATUS_OK = 200;
    private static final int STATUS_METHOD_NOT_ALLOWED = 405;

    private Taxonomy referenceTaxonomy;
    private Taxonomy syntheticTree;

    public static void main(final String... args) throws IOException {
        new Services(args.length > 0 ? Taxonomy.getTaxonomy(args[0]) : null,
                     args.length > 1 ? Taxonomy.getTaxonomy(args[1]) : null)
            .serve("localhost", 8081);
    }

    public Services(Taxonomy reftax, Taxonomy synth) {
        this.referenceTaxonomy = reftax;
        this.syntheticTree = synth;
    }

    // Does not return
    public void serve(String hostname, int port) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(hostname, port), BACKLOG);
        server.createContext("/conflict-status", conflictStatus);
        server.start();
    }

    private HttpHandler conflictStatus =
        exchange -> {
        try {
            if (exchange.getRequestMethod().toUpperCase().equals("GET")) {
                final Map<String, String> parameters = getParameters(exchange.getRequestURI());

                // exchange.getRequestHeaders();
                Map result = conflictStatus(parameters.get("tree1"),
                                            parameters.get("tree2"));
                
                final Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", String.format("application/json; charset=%s",
                                                          StandardCharsets.UTF_8));

                JSONObject.writeJSONString(result, new PrintWriter(exchange.getResponseBody()));
            } else
                nonget(exchange);
        } finally {
            exchange.close();
        }
    };
    
    private JSONObject conflictStatus(String treespec1, String treespec2) throws IOException {
        Taxonomy tree1 = specToTree(treespec1);
        if (tree1 == null) {
            System.err.format("** Can't find %s\n", treespec1);
            return new JSONObject();
        }
        Taxonomy tree2 = specToTree(treespec2);
        if (tree2 == null) {
            System.err.format("** Can't find %s\n", treespec2);
            return new JSONObject();
        }
        boolean flipped = false;
        Taxonomy input = tree1, ref = tree2;
        if (tree2.count() < tree1.count()) { // heuristic!
            input = tree2; ref = tree1; flipped = true;
        }
        ConflictAnalysis c = new ConflictAnalysis(input, ref);
        JSONObject result = new JSONObject();
        Taxon start = flipped ? c.inducedRoot : c.ingroup;
        for (Taxon node : start.descendants(true)) {
            String tag = null;
            switch (c.disposition(node)) {
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
                Taxon w = c.witness(node);
                if (w != null)
                    info.put("witness", w.id);
                info.put("status", tag);
                result.put(node.id, info);
            }
        }
        return result;
    }

    public Taxonomy specToTree(String spec) throws IOException {
        String[] parts = spec.split("#");
        if (parts.length == 0)
            return null;
        else if (parts.length == 1) {
            // Otherwise, use saved ott or synth
            return getReferenceTree(parts[0]);
        } else {
            try {
                return getSourceTree(parts[0], parts[1]);
            } catch (ParseException e) {
                System.err.format("** JSON parse exception for %s\n", spec);
                return null;
            }
        }
    }

    private Taxonomy getReferenceTree(String spec) {
        if (spec.equals("ott"))
            return referenceTaxonomy;
        else if (spec.equals("synth"))
            return syntheticTree;
        else
            return null;
    }

    private Taxonomy getSourceTree(String studyId, String treeId) throws IOException, ParseException {
        JSONObject study = getStudy(studyId);
        Taxonomy tree = Nexson.importTree(Nexson.getTrees(study).get(treeId), Nexson.getOtus(study), treeId);
        tree.idspace = studyId;
        return tree;
    }

    private String singleCachedStudyId = null;
    private JSONObject singleCachedStudy = null;

    private JSONObject getStudy(String studyId) throws IOException, ParseException {
        if (studyId.equals(singleCachedStudyId))
            return singleCachedStudy;
        else {
            URL url = new URL("https://api.opentreeoflife.org/v2/study/" + studyId + "?output_nexml2json=1.2.1");
            HttpURLConnection conn = (HttpURLConnection)(url.openConnection());
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                JSONParser parser = new JSONParser();
                JSONObject envelope = (JSONObject)parser.parse(reader);
                JSONObject nexson = (JSONObject)envelope.get("data");
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
