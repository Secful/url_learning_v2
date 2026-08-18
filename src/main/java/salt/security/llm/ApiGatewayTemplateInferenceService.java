package salt.security.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import salt.security.trie.SegmentValidator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Template inference service that delegates to a Bedrock Lambda via AWS API Gateway.
 *
 * Auth: HTTP Basic (identifier:hybridToken) validated by a Lambda authorizer using
 * the same hybrid-token scheme as the Big Data Gateway service.
 */
public class ApiGatewayTemplateInferenceService implements TemplateInferenceService {

    private static final Logger logger = Logger.getLogger(ApiGatewayTemplateInferenceService.class.getName());

    private final String endpointUrl;
    private final Map<String, String> authHeaders;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * No-arg constructor for reflection-based instantiation via TemplateInferenceServiceFactory.
     *
     * Required env vars:
     *   API_GW_URL               — full endpoint URL
     *   API_GW_AUTH_HEADER_NAME  — auth header name  (e.g. "secret" for fake auth,
     *                                                   "Authorization" for hybrid token)
     *   API_GW_AUTH_HEADER_VALUE — auth header value (e.g. "77", or "Basic base64(...)")
     */
    public ApiGatewayTemplateInferenceService() {
        this(
            requireEnv("API_GW_URL"),
            Map.of(requireEnv("API_GW_AUTH_HEADER_NAME"), requireEnv("API_GW_AUTH_HEADER_VALUE"))
        );
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required env var not set: " + name);
        }
        return value;
    }

    /**
     * Real auth: hybrid token validated via gRPC company service (Big Data GW scheme).
     *
     * @param endpointUrl  Full API GW URL
     * @param identifier   Caller identifier (Basic auth username)
     * @param hybridToken  Hybrid token (Basic auth password)
     */
    public ApiGatewayTemplateInferenceService(String endpointUrl, String identifier, String hybridToken) {
        this(endpointUrl, Map.of(
            "Authorization",
            "Basic " + Base64.getEncoder().encodeToString((identifier + ":" + hybridToken).getBytes())
        ));
    }

    /**
     * Custom headers auth — use for temporary/fake auth schemes (e.g. secret header).
     *
     * @param endpointUrl  Full API GW URL
     * @param authHeaders  Headers to include on every request for authentication
     */
    public ApiGatewayTemplateInferenceService(String endpointUrl, Map<String, String> authHeaders) {
        this.endpointUrl = endpointUrl;
        this.authHeaders = authHeaders;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public TemplateInference inferTemplate(String path) {
        if (path == null || path.isEmpty()) {
            logger.warning("Cannot infer template from null or empty path");
            return null;
        }

        try {
            logger.info("Calling API Gateway to infer template for path: " + path);

            String requestBody = objectMapper.writeValueAsString(Map.of("path", path));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            authHeaders.forEach(requestBuilder::header);
            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 403) {
                logger.severe("API Gateway rejected request: invalid hybrid token or identifier");
                return null;
            }

            if (response.statusCode() != 200) {
                logger.severe("API Gateway returned HTTP " + response.statusCode() + ": " + response.body());
                return null;
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to infer template via API Gateway for path: " + path, e);
            return null;
        }
    }

    private TemplateInference parseResponse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);

        String template = root.path("template").asText();
        if (template.isEmpty()) {
            logger.warning("No template in API Gateway response");
            return null;
        }

        Map<String, SegmentValidator> validators = new HashMap<>();
        JsonNode validatorsNode = root.path("validators");
        if (validatorsNode.isObject()) {
            validatorsNode.fields().forEachRemaining(entry -> {
                SegmentValidator v = parseValidator(entry.getValue().asText());
                if (v != null) {
                    validators.put(entry.getKey(), v);
                }
            });
        }

        logger.info("Inferred template via API Gateway: " + template + " validators: " + validators);
        return new TemplateInference(template, validators);
    }

    private SegmentValidator parseValidator(String type) {
        if (type == null) return SegmentValidator.ANY;

        SegmentValidator MONGODB_ID = segment ->
                segment != null && segment.matches("^[0-9a-fA-F]{24}$");

        return switch (type.toUpperCase().replace("-", "_")) {
            case "NUMERIC"                              -> SegmentValidator.NUMERIC;
            case "UUID"                                 -> SegmentValidator.UUID;
            case "ALPHANUMERIC_ID", "ALPHANUMERIC",
                 "ALPHA_NUMERIC_ID"                     -> SegmentValidator.ALPHANUMERIC_ID;
            case "TIMESTAMP", "UNIX_TIMESTAMP",
                 "SLACK_TIMESTAMP"                      -> SegmentValidator.TIMESTAMP;
            case "OBJECTID", "MONGODB", "OBJECT"        -> MONGODB_ID;
            case "IATA_AIRPORT", "IATA",
                 "AIRPORT_IATA"                         -> SegmentValidator.IATA_AIRPORT;
            case "ICAO_AIRPORT", "ICAO",
                 "AIRPORT_ICAO"                         -> SegmentValidator.ICAO_AIRPORT;
            case "ISO_639_1", "ISO_639_1_LANGUAGE",
                 "LANGUAGE", "LANG"                     -> SegmentValidator.ISO_639_1_LANGUAGE;
            case "ISO_639_2", "ISO_639_2_LANGUAGE",
                 "LANGUAGE_3"                           -> SegmentValidator.ISO_639_2_LANGUAGE;
            case "COUNTRY_ALPHA2", "ISO_3166_ALPHA2",
                 "COUNTRY", "ISO_3166"                  -> SegmentValidator.ISO_3166_COUNTRY_ALPHA2;
            case "COUNTRY_ALPHA3", "ISO_3166_ALPHA3",
                 "COUNTRY_3"                            -> SegmentValidator.ISO_3166_COUNTRY_ALPHA3;
            case "CURRENCY", "ISO_4217",
                 "ISO_4217_CURRENCY"                    -> SegmentValidator.ISO_4217_CURRENCY;
            case "HTTP_STATUS", "HTTP_STATUS_CODE",
                 "STATUS_CODE", "STATUS"                -> SegmentValidator.HTTP_STATUS_CODE;
            case "US_STATE", "US_STATE_CODE",
                 "STATE", "STATE_CODE"                  -> SegmentValidator.US_STATE_CODE;
            case "DAY_OF_WEEK", "DAY", "WEEKDAY"        -> SegmentValidator.DAY_OF_WEEK;
            case "MONTH_NAME", "MONTH"                  -> SegmentValidator.MONTH_NAME;
            default                                     -> SegmentValidator.ANY;
        };
    }
}
