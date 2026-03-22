package salt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import salt.security.trie.PathTemplateTrie;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrieHttpServerTest {

    private static TrieHttpServer server;
    private static HttpClient client;
    private static String baseUrl;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void startServer() throws Exception {
        PathTemplateTrie trie = new PathTemplateTrie();
        server = new TrieHttpServer(trie, 0); // port 0 = random available port
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Order(1)
    void healthCheck() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals("healthy", body.get("status"));
    }

    @Test
    @Order(2)
    void insertTemplate() throws Exception {
        String json = mapper.writeValueAsString(Map.of("template", "/users/{name}/posts/{id}", "validators", Map.of("id", "NUMERIC")));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/templates"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals("created", body.get("status"));
        assertEquals("/users/{name}/posts/{id}", body.get("template"));
    }

    @Test
    @Order(3)
    void listTemplatesContainsInserted() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/templates"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals(1, body.get("count"));
        @SuppressWarnings("unchecked")
        List<String> templates = (List<String>) body.get("templates");
        assertTrue(templates.contains("/users/{name}/posts/{id}"));
    }

    @Test
    @Order(4)
    void lookupMatch() throws Exception {
        String json = mapper.writeValueAsString(Map.of("path", "/users/jack/posts/123"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/lookup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals(true, body.get("matched"));
        assertEquals("/users/{name}/posts/{id}", body.get("template"));
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) body.get("params");
        assertEquals("jack", params.get("name"));
        assertEquals("123", params.get("id"));
    }

    @Test
    @Order(5)
    void lookupMiss() throws Exception {
        String json = mapper.writeValueAsString(Map.of("path", "/unknown/path"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/lookup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals(false, body.get("matched"));
    }

    @Test
    @Order(6)
    void insertWithValidatorEnforcesValidation() throws Exception {
        // Insert with UUID validator
        String insertJson = mapper.writeValueAsString(Map.of(
                "template", "/api/v1/orders/{id}",
                "validators", Map.of("id", "UUID")));
        HttpRequest insertReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/templates"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(insertJson))
                .build();
        client.send(insertReq, HttpResponse.BodyHandlers.ofString());

        // Lookup with valid UUID - should match
        String lookupJson = mapper.writeValueAsString(Map.of("path", "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000"));
        HttpRequest lookupReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/lookup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(lookupJson))
                .build();
        HttpResponse<String> matchResp = client.send(lookupReq, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> matchBody = parseJson(matchResp.body());
        assertEquals(true, matchBody.get("matched"));

        // Lookup with non-UUID - should not match
        String missJson = mapper.writeValueAsString(Map.of("path", "/api/v1/orders/not-a-uuid"));
        HttpRequest missReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/lookup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(missJson))
                .build();
        HttpResponse<String> missResp = client.send(missReq, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> missBody = parseJson(missResp.body());
        assertEquals(false, missBody.get("matched"));
    }

    @Test
    @Order(7)
    void removeTemplate() throws Exception {
        String json = mapper.writeValueAsString(Map.of("template", "/users/{name}/posts/{id}"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/templates"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals("removed", body.get("status"));

        // Verify it's gone by looking it up
        String lookupJson = mapper.writeValueAsString(Map.of("path", "/users/jack/posts/123"));
        HttpRequest lookupReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/lookup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(lookupJson))
                .build();
        HttpResponse<String> lookupResp = client.send(lookupReq, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> lookupBody = parseJson(lookupResp.body());
        assertEquals(false, lookupBody.get("matched"));
    }

    @Test
    @Order(8)
    void removeNonExistent() throws Exception {
        String json = mapper.writeValueAsString(Map.of("template", "/does/not/exist"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/templates"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals("not_found", body.get("status"));
    }

    @Test
    @Order(9)
    void badRequestMissingPath() throws Exception {
        String json = "{}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/trie/lookup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals("bad_request", body.get("error"));
    }

    @Test
    @Order(10)
    void unknownRoute() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/no/such/route"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        Map<String, Object> body = parseJson(response.body());
        assertEquals("not_found", body.get("error"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws Exception {
        return mapper.readValue(json, Map.class);
    }
}
