package salt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import salt.security.trie.MatchResult;
import salt.security.trie.PathTemplateTrie;
import salt.security.trie.SegmentValidator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrieHttpServer {
    private static final Logger logger = Logger.getLogger(TrieHttpServer.class.getName());
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final Server server;
    private final PathTemplateTrie trie;
    private final ObjectMapper mapper = new ObjectMapper();

    public TrieHttpServer(PathTemplateTrie trie, int port) {
        this.trie = trie;
        this.server = new Server(port);
        this.server.setHandler(new TrieHandler());
    }

    public void start() throws Exception {
        server.start();
        logger.info("TrieHttpServer started on port " + getPort());
    }

    public void stop() throws Exception {
        server.stop();
        logger.info("TrieHttpServer stopped");
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public int getPort() {
        return server.getURI().getPort();
    }

    public boolean isRunning() {
        return server.isRunning();
    }

    private class TrieHandler extends Handler.Abstract {
        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            String method = request.getMethod();
            String path = Request.getPathInContext(request);

            try {
                if ("GET".equals(method) && "/health".equals(path)) {
                    handleHealth(response, callback);
                } else if ("POST".equals(method) && "/api/v1/trie/lookup".equals(path)) {
                    handleLookup(request, response, callback);
                } else if ("POST".equals(method) && "/api/v1/trie/templates".equals(path)) {
                    handleInsert(request, response, callback);
                } else if ("DELETE".equals(method) && "/api/v1/trie/templates".equals(path)) {
                    handleRemove(request, response, callback);
                } else if ("GET".equals(method) && "/api/v1/trie/templates".equals(path)) {
                    handleList(response, callback);
                } else {
                    sendJson(response, callback, 404, Map.of("error", "not_found", "message", "Unknown route: " + method + " " + path));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error handling request: " + method + " " + path, e);
                sendJson(response, callback, 500, Map.of("error", "internal_error", "message", e.getMessage()));
            }

            return true;
        }
    }

    private void handleHealth(Response response, Callback callback) throws Exception {
        sendJson(response, callback, 200, Map.of("status", "healthy"));
    }

    private void handleLookup(Request request, Response response, Callback callback) throws Exception {
        String body = Content.Source.asString(request, StandardCharsets.UTF_8);
        Map<String, Object> json = parseJsonBody(body);
        if (json == null || !json.containsKey("path")) {
            sendJson(response, callback, 400, Map.of("error", "bad_request", "message", "Missing required field: path"));
            return;
        }

        String path = (String) json.get("path");
        MatchResult result = trie.lookup(path);

        if (result != null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("matched", true);
            resp.put("template", result.getTemplate());
            resp.put("params", result.getParams());
            sendJson(response, callback, 200, resp);
        } else {
            sendJson(response, callback, 200, Map.of("matched", false));
        }
    }

    private void handleInsert(Request request, Response response, Callback callback) throws Exception {
        String body = Content.Source.asString(request, StandardCharsets.UTF_8);
        Map<String, Object> json = parseJsonBody(body);
        if (json == null || !json.containsKey("template")) {
            sendJson(response, callback, 400, Map.of("error", "bad_request", "message", "Missing required field: template"));
            return;
        }

        String template = (String) json.get("template");
        Map<String, SegmentValidator> validators = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, String> validatorStrings = (Map<String, String>) json.get("validators");
        if (validatorStrings != null) {
            for (Map.Entry<String, String> entry : validatorStrings.entrySet()) {
                validators.put(entry.getKey(), parseValidator(entry.getValue()));
            }
        }

        trie.insert(template, validators);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "created");
        resp.put("template", template);
        sendJson(response, callback, 201, resp);
    }

    private void handleRemove(Request request, Response response, Callback callback) throws Exception {
        String body = Content.Source.asString(request, StandardCharsets.UTF_8);
        Map<String, Object> json = parseJsonBody(body);
        if (json == null || !json.containsKey("template")) {
            sendJson(response, callback, 400, Map.of("error", "bad_request", "message", "Missing required field: template"));
            return;
        }

        String template = (String) json.get("template");
        boolean removed = trie.remove(template);

        if (removed) {
            sendJson(response, callback, 200, Map.of("status", "removed", "template", template));
        } else {
            sendJson(response, callback, 404, Map.of("status", "not_found", "template", template));
        }
    }

    private void handleList(Response response, Callback callback) throws Exception {
        List<String> templates = trie.listTemplates();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("count", templates.size());
        resp.put("templates", templates);
        sendJson(response, callback, 200, resp);
    }

    private void sendJson(Response response, Callback callback, int status, Object body) throws Exception {
        byte[] jsonBytes = mapper.writeValueAsBytes(body);
        response.setStatus(status);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, CONTENT_TYPE_JSON);
        response.getHeaders().put(HttpHeader.CONTENT_LENGTH, jsonBytes.length);
        response.write(true, ByteBuffer.wrap(jsonBytes), callback);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(body, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private SegmentValidator parseValidator(String type) {
        if (type == null) {
            return SegmentValidator.ANY;
        }

        SegmentValidator MONGODB_ID = segment ->
                segment != null && segment.matches("^[0-9a-fA-F]{24}$");

        String upperType = type.toUpperCase().replace("-", "_");

        return switch (upperType) {
            case "NUMERIC" -> SegmentValidator.NUMERIC;
            case "UUID" -> SegmentValidator.UUID;
            case "ALPHANUMERIC_ID", "ALPHANUMERIC", "ALPHA_NUMERIC_ID" -> SegmentValidator.ALPHANUMERIC_ID;
            case "TIMESTAMP", "UNIX_TIMESTAMP", "SLACK_TIMESTAMP" -> SegmentValidator.TIMESTAMP;
            case "OBJECTID", "MONGODB", "OBJECT" -> MONGODB_ID;
            case "IATA_AIRPORT", "IATA", "AIRPORT_IATA" -> SegmentValidator.IATA_AIRPORT;
            case "ICAO_AIRPORT", "ICAO", "AIRPORT_ICAO" -> SegmentValidator.ICAO_AIRPORT;
            case "ISO_639_1", "ISO_639_1_LANGUAGE", "LANGUAGE", "LANG" -> SegmentValidator.ISO_639_1_LANGUAGE;
            case "ISO_639_2", "ISO_639_2_LANGUAGE", "LANGUAGE_3" -> SegmentValidator.ISO_639_2_LANGUAGE;
            case "COUNTRY_ALPHA2", "ISO_3166_ALPHA2", "COUNTRY", "ISO_3166" -> SegmentValidator.ISO_3166_COUNTRY_ALPHA2;
            case "COUNTRY_ALPHA3", "ISO_3166_ALPHA3", "COUNTRY_3" -> SegmentValidator.ISO_3166_COUNTRY_ALPHA3;
            case "CURRENCY", "ISO_4217", "ISO_4217_CURRENCY" -> SegmentValidator.ISO_4217_CURRENCY;
            case "HTTP_STATUS", "HTTP_STATUS_CODE", "STATUS_CODE", "STATUS" -> SegmentValidator.HTTP_STATUS_CODE;
            case "US_STATE", "US_STATE_CODE", "STATE", "STATE_CODE" -> SegmentValidator.US_STATE_CODE;
            case "DAY_OF_WEEK", "DAY", "WEEKDAY" -> SegmentValidator.DAY_OF_WEEK;
            case "MONTH_NAME", "MONTH" -> SegmentValidator.MONTH_NAME;
            default -> SegmentValidator.ANY;
        };
    }
}
