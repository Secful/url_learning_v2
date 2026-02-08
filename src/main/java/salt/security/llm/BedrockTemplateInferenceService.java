package salt.security.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import salt.security.trie.SegmentValidator;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Template inference service using Claude on AWS Bedrock.
 * Invokes Claude to infer parameterized templates from concrete HTTP paths.
 */
public class BedrockTemplateInferenceService implements TemplateInferenceService {
    private static final Logger logger = Logger.getLogger(BedrockTemplateInferenceService.class.getName());

    private final BedrockRuntimeClient bedrockClient;
    private final String modelId;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT =
        "You are an API pattern analyzer. Given a concrete HTTP path, infer the parameterized template.\n" +
        "Rules:\n" +
        "1. Replace dynamic segments with {paramName} placeholders\n" +
        "2. Use semantic parameter names (e.g., {id}, {name}, {userId}, {companyId}, {lang}, {country}, {currency})\n" +
        "3. Identify segment types:\n" +
        "   - NUMERIC: digits only (e.g., 123, 456)\n" +
        "   - UUID: standard format (e.g., 550e8400-e29b-41d4-a716-446655440000)\n" +
        "   - MONGODB: 24 hex characters (e.g., 64e7a294e854ff2eb3550075)\n" +
        "   - IATA_AIRPORT: 3-letter airport code (e.g., JFK, LAX, LHR, CDG)\n" +
        "   - ICAO_AIRPORT: 4-letter airport code (e.g., KJFK, EGLL, LFPG)\n" +
        "   - ISO_639_1: 2-letter language code (e.g., en, es, fr, de, zh)\n" +
        "   - ISO_639_2: 3-letter language code (e.g., eng, spa, fra, deu)\n" +
        "   - COUNTRY_ALPHA2: 2-letter country code (e.g., US, GB, FR, DE)\n" +
        "   - COUNTRY_ALPHA3: 3-letter country code (e.g., USA, GBR, FRA)\n" +
        "   - CURRENCY: 3-letter currency code (e.g., USD, EUR, GBP, JPY)\n" +
        "   - HTTP_STATUS: 3-digit HTTP status code (e.g., 200, 404, 500)\n" +
        "   - ANY: any other non-empty string\n" +
        "4. Return ONLY valid JSON, no explanatory text\n" +
        "5. Keep literal segments as-is (e.g., /api, /users, /posts)\n" +
        "6. Prefer specific validators over generic ones when pattern is clear\n" +
        "Format: {\"template\": \"/path/{param}\", \"validators\": {\"param\": \"type\"}}\n" +
        "Examples:\n" +
        "- /users/123 -> {\"template\": \"/users/{id}\", \"validators\": {\"id\": \"NUMERIC\"}}\n" +
        "- /content/en/articles -> {\"template\": \"/content/{lang}/articles\", \"validators\": {\"lang\": \"ISO_639_1\"}}\n" +
        "- /flights/JFK/departures -> {\"template\": \"/flights/{airport}/departures\", \"validators\": {\"airport\": \"IATA_AIRPORT\"}}\n" +
        "- /api/v2/countries/US/users -> {\"template\": \"/api/v2/countries/{country}/users\", \"validators\": {\"country\": \"COUNTRY_ALPHA2\"}}\n" +
        "- /prices/USD/products -> {\"template\": \"/prices/{currency}/products\", \"validators\": {\"currency\": \"CURRENCY\"}}\n" +
        "- /status/404/info -> {\"template\": \"/status/{code}/info\", \"validators\": {\"code\": \"HTTP_STATUS\"}}\n" +
        "- /api/orders/550e8400-e29b-41d4-a716-446655440000 -> {\"template\": \"/api/orders/{id}\", \"validators\": {\"id\": \"UUID\"}}\n" +
        "- /api/companies/64e7a294e854ff2eb3550075/config -> {\"template\": \"/api/companies/{companyId}/config\", \"validators\": {\"companyId\": \"MONGODB\"}}";

    /**
     * Creates a Bedrock inference service with default configuration.
     * Uses default AWS credentials and us-east-1 region.
     */
    public BedrockTemplateInferenceService() {
        this(Region.US_EAST_1, "us.anthropic.claude-3-5-sonnet-20241022-v2:0");
    }

    /**
     * Creates a Bedrock inference service with custom configuration.
     *
     * @param region the AWS region
     * @param modelId the Bedrock model ID (e.g., "anthropic.claude-3-5-sonnet-20241022-v2:0")
     */
    public BedrockTemplateInferenceService(Region region, String modelId) {
        this.bedrockClient = BedrockRuntimeClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
        this.modelId = modelId;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public TemplateInference inferTemplate(String path) {
        if (path == null || path.isEmpty()) {
            logger.warning("Cannot infer template from null or empty path");
            return null;
        }

        try {
            logger.info("Invoking Claude on Bedrock to infer template for path: " + path);

            // Construct the request payload for Claude
            String requestBody = buildClaudeRequest(path);

            // Invoke Bedrock
            InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseBody = response.body().asUtf8String();

            logger.fine("Bedrock response: " + responseBody);

            // Parse the response
            return parseClaudeResponse(responseBody);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to infer template from Bedrock for path: " + path, e);
            return null;
        }
    }

    /**
     * Builds the Claude API request payload in the Messages API format.
     */
    private String buildClaudeRequest(String path) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("anthropic_version", "bedrock-2023-05-31");
        request.put("max_tokens", 500);
        request.put("system", SYSTEM_PROMPT);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "Path: " + path);

        request.put("messages", new Object[]{message});

        return objectMapper.writeValueAsString(request);
    }

    /**
     * Parses Claude's response and extracts the template and validators.
     */
    private TemplateInference parseClaudeResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Extract content from Claude's response
        JsonNode contentArray = root.path("content");
        if (contentArray.isEmpty() || !contentArray.isArray()) {
            logger.warning("No content in Claude response");
            return null;
        }

        String textContent = contentArray.get(0).path("text").asText();
        if (textContent.isEmpty()) {
            logger.warning("Empty text content in Claude response");
            return null;
        }

        logger.fine("Claude response text: " + textContent);

        // Extract JSON from the response (may contain explanatory text)
        String jsonContent = extractJSON(textContent);
        if (jsonContent == null) {
            logger.warning("Could not extract JSON from Claude's response");
            return null;
        }

        // Parse the JSON from Claude's response
        // Claude should return: {"template": "/path/{param}", "validators": {"param": "NUMERIC"}}
        JsonNode inference = objectMapper.readTree(jsonContent);

        String template = inference.path("template").asText();
        if (template.isEmpty()) {
            logger.warning("No template in Claude's inference");
            return null;
        }

        // Parse validators
        Map<String, SegmentValidator> validators = new HashMap<>();
        JsonNode validatorsNode = inference.path("validators");
        if (validatorsNode.isObject()) {
            validatorsNode.fields().forEachRemaining(entry -> {
                String paramName = entry.getKey();
                String validatorType = entry.getValue().asText();
                SegmentValidator validator = parseValidator(validatorType);
                if (validator != null) {
                    validators.put(paramName, validator);
                }
            });
        }

        logger.info("Inferred template: " + template + " with validators: " + validators);
        return new TemplateInference(template, validators);
    }

    /**
     * Extracts JSON object from text that may contain explanatory content.
     * Looks for the first { and matching } to extract the JSON block.
     */
    private String extractJSON(String text) {
        int startIndex = text.indexOf('{');
        if (startIndex == -1) {
            return null;
        }

        int braceCount = 0;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return text.substring(startIndex, i + 1);
                }
            }
        }

        return null;
    }

    /**
     * Parses a validator type string into a SegmentValidator.
     * Supports all built-in validators including pattern-based and set-based validators.
     */
    private SegmentValidator parseValidator(String type) {
        if (type == null) {
            return SegmentValidator.ANY;
        }

        // MongoDB ObjectID validator (24 hex characters)
        SegmentValidator MONGODB_ID = segment ->
            segment != null && segment.matches("^[0-9a-fA-F]{24}$");

        String upperType = type.toUpperCase().replace("-", "_");

        return switch (upperType) {
            // Pattern-based validators
            case "NUMERIC" -> SegmentValidator.NUMERIC;
            case "UUID" -> SegmentValidator.UUID;
            case "OBJECTID", "MONGODB", "OBJECT" -> MONGODB_ID;

            // Airport codes
            case "IATA_AIRPORT", "IATA", "AIRPORT_IATA" -> SegmentValidator.IATA_AIRPORT;
            case "ICAO_AIRPORT", "ICAO", "AIRPORT_ICAO" -> SegmentValidator.ICAO_AIRPORT;

            // Language codes
            case "ISO_639_1", "ISO_639_1_LANGUAGE", "LANGUAGE", "LANG" -> SegmentValidator.ISO_639_1_LANGUAGE;
            case "ISO_639_2", "ISO_639_2_LANGUAGE", "LANGUAGE_3" -> SegmentValidator.ISO_639_2_LANGUAGE;

            // Country codes
            case "COUNTRY_ALPHA2", "ISO_3166_ALPHA2", "COUNTRY", "ISO_3166" -> SegmentValidator.ISO_3166_COUNTRY_ALPHA2;
            case "COUNTRY_ALPHA3", "ISO_3166_ALPHA3", "COUNTRY_3" -> SegmentValidator.ISO_3166_COUNTRY_ALPHA3;

            // Currency codes
            case "CURRENCY", "ISO_4217", "ISO_4217_CURRENCY" -> SegmentValidator.ISO_4217_CURRENCY;

            // HTTP status codes
            case "HTTP_STATUS", "HTTP_STATUS_CODE", "STATUS_CODE", "STATUS" -> SegmentValidator.HTTP_STATUS_CODE;

            // Default
            default -> SegmentValidator.ANY;
        };
    }

    /**
     * Closes the Bedrock client.
     */
    public void close() {
        if (bedrockClient != null) {
            bedrockClient.close();
        }
    }
}
