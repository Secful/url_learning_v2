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
        "\n" +
        "CRITICAL RULE - ONE PARAMETER PER SEGMENT:\n" +
        "Each path segment (text between '/' delimiters) must be EITHER:\n" +
        "  - A literal: 'users', 'api', 'search'\n" +
        "  - A single parameter: '{id}', '{slug}', '{recordId}'\n" +
        "  - NEVER multiple parameters: '{countryCode}{number}' ❌ INVALID!\n" +
        "\n" +
        "Even if a segment contains multiple semantic parts (e.g., 'US12345678'),\n" +
        "treat it as ONE parameter with ONE validator.\n" +
        "Example: /records/US12345678 → /records/{recordId} with validator ALPHANUMERIC_ID\n" +
        "NOT: /records/{country}{number} ❌\n" +
        "\n" +
        "ANALYSIS PROCESS:\n" +
        "Step 1: Split path by '/' into segments\n" +
        "\n" +
        "Step 2: For each segment, ask: 'Could this value change between different API calls?'\n" +
        "  - YES → Dynamic parameter (use ONE {placeholder} for the entire segment)\n" +
        "  - NO → Literal segment (keep as-is)\n" +
        "\n" +
        "Step 3: If dynamic, select the MOST SPECIFIC validator that matches the ENTIRE segment:\n" +
        "  - Match exact format first (UUID, NUMERIC, MONGODB, ALPHANUMERIC_ID, etc.)\n" +
        "  - Then try semantic validators (IATA_AIRPORT, ISO_639_1, CURRENCY, etc.)\n" +
        "  - Default to ANY only when no specific pattern applies\n" +
        "\n" +
        "Step 4: Verify your template can match ALL variations of this endpoint\n" +
        "\n" +
        "═══════════════════════════════════════════════════════════════════════════════\n" +
        "LITERAL vs DYNAMIC DECISION (apply in priority order):\n" +
        "\n" +
        "A segment is LITERAL if it matches ANY of these:\n" +
        "1. REST operations: search, list, all, create, update, delete, get, post, put, patch, find\n" +
        "2. API structure: api, rest, graphql, apis\n" +
        "3. API versions ONLY when NOT last: v1, v2, v3, v4 (if last segment → use {version})\n" +
        "4. Resource collections (typically plurals): users, items, orders, products, posts, comments\n" +
        "5. Action endpoints: info, details, settings, config, status, health, version, ping, metrics\n" +
        "\n" +
        "A segment is DYNAMIC if it matches ANY of these:\n" +
        "1. Has a recognizable ID format:\n" +
        "   - Pure numbers: 123, 456789\n" +
        "   - UUIDs: 550e8400-e29b-41d4-a716-446655440000\n" +
        "   - MongoDB IDs: 64e7a294e854ff2eb3550075\n" +
        "   - Alphanumeric IDs: CHMA0000000001, AC1234567890, cus_123abc\n" +
        "2. Contains hyphens/underscores indicating user-generated content:\n" +
        "   - Slugs: getting-started, api-reference, how-to-guide\n" +
        "   - Usernames: john_doe, jane-smith\n" +
        "3. Single value after a collection noun: /users/john, /posts/my-first-post\n" +
        "4. Parameterizable values: en, US, USD, JFK, monday, 404\n" +
        "\n" +
        "DEFAULT: When uncertain, treat as DYNAMIC (safer to over-parameterize than under-parameterize)\n" +
        "\n" +
        "═══════════════════════════════════════════════════════════════════════════════\n" +
        "AVAILABLE VALIDATORS:\n" +
        "\n" +
        "Exact Format Validators (highest priority):\n" +
        "- NUMERIC: digits only (e.g., 123, 456789)\n" +
        "- UUID: standard 8-4-4-4-12 format (e.g., 550e8400-e29b-41d4-a716-446655440000)\n" +
        "- MONGODB: exactly 24 hex characters (e.g., 64e7a294e854ff2eb3550075)\n" +
        "- ALPHANUMERIC_ID: letter prefix + optional underscore + hex digits (e.g., CHMA0000000001, cus_123456789, C1234567890)\n" +
        "- TIMESTAMP: Unix timestamp with microseconds - 10 digits + dot + 6 digits (e.g., 1765701919.171019, 1234567890.123456)\n" +
        "\n" +
        "Semantic Validators (use when format + context match):\n" +
        "- IATA_AIRPORT: 3 uppercase letters (e.g., JFK, LAX, LHR)\n" +
        "- ICAO_AIRPORT: 4 uppercase letters (e.g., KJFK, EGLL, LFPG)\n" +
        "- ISO_639_1: 2 lowercase letters - language codes (e.g., en, es, fr, de)\n" +
        "- ISO_639_2: 3 lowercase letters - language codes (e.g., eng, spa, fra)\n" +
        "- COUNTRY_ALPHA2: 2 uppercase letters - country codes (e.g., US, GB, FR)\n" +
        "- COUNTRY_ALPHA3: 3 uppercase letters - country codes (e.g., USA, GBR, FRA)\n" +
        "- CURRENCY: 3 uppercase letters - currency codes (e.g., USD, EUR, GBP, JPY)\n" +
        "- HTTP_STATUS: 3 digits 100-599 (e.g., 200, 404, 500)\n" +
        "- US_STATE: 2 uppercase letters - US state codes (e.g., CA, NY, TX, FL)\n" +
        "- DAY_OF_WEEK: day names/abbreviations (e.g., monday, mon, friday, fri)\n" +
        "- MONTH_NAME: month names/abbreviations (e.g., january, jan, december, dec)\n" +
        "\n" +
        "Generic Validator (fallback):\n" +
        "- ANY: any non-empty string (use when no specific pattern matches)\n" +
        "\n" +
        "VALIDATOR SELECTION RULES:\n" +
        "1. Always choose the MOST SPECIFIC validator that matches\n" +
        "2. Use semantic validators only when context supports them (e.g., /flights/JFK → IATA_AIRPORT)\n" +
        "3. If segment is purely numeric, use NUMERIC (not ANY)\n" +
        "4. If format matches UUID/MONGODB/ALPHANUMERIC_ID exactly, use that (not NUMERIC or ANY)\n" +
        "5. Use ANY as last resort for unstructured strings (slugs, usernames, arbitrary text)\n" +
        "\n" +
        "═══════════════════════════════════════════════════════════════════════════════\n" +
        "COMMON MISTAKES TO AVOID:\n" +
        "\n" +
        "❌ /records/US12345678 → {\"template\": \"/records/{countryCode}{number}\", ...}\n" +
        "   ✓ /records/US12345678 → {\"template\": \"/records/{recordId}\", \"validators\": {\"recordId\": \"ALPHANUMERIC_ID\"}}\n" +
        "   (NEVER split one segment into multiple parameters! One segment = one parameter max)\n" +
        "\n" +
        "❌ /api/products/search → {\"template\": \"/api/products/{action}\", ...}\n" +
        "   ✓ /api/products/search → {\"template\": \"/api/products/search\", \"validators\": {}}\n" +
        "   (search is a literal REST operation, not a parameter!)\n" +
        "\n" +
        "❌ /users/john_doe → {\"template\": \"/users/john_doe\", \"validators\": {}}\n" +
        "   ✓ /users/john_doe → {\"template\": \"/users/{username}\", \"validators\": {\"username\": \"ANY\"}}\n" +
        "   (john_doe varies between users, it's dynamic!)\n" +
        "\n" +
        "❌ /flights/JFK/arrivals → {\"template\": \"/flights/{code}/arrivals\", \"validators\": {\"code\": \"ANY\"}}\n" +
        "   ✓ /flights/JFK/arrivals → {\"template\": \"/flights/{airport}/arrivals\", \"validators\": {\"airport\": \"IATA_AIRPORT\"}}\n" +
        "   (JFK is 3 uppercase letters in flight context → use IATA_AIRPORT, not ANY!)\n" +
        "\n" +
        "❌ /api/orders/550e8400-e29b-41d4-a716-446655440000 → {..., \"validators\": {\"id\": \"NUMERIC\"}}\n" +
        "   ✓ /api/orders/550e8400-e29b-41d4-a716-446655440000 → {..., \"validators\": {\"id\": \"UUID\"}}\n" +
        "   (matches UUID format exactly → use UUID, not NUMERIC!)\n" +
        "\n" +
        "❌ /content/en/articles → {\"template\": \"/content/{lang}/articles\", \"validators\": {\"lang\": \"ANY\"}}\n" +
        "   ✓ /content/en/articles → {\"template\": \"/content/{lang}/articles\", \"validators\": {\"lang\": \"ISO_639_1\"}}\n" +
        "   (2-letter code in i18n context → use ISO_639_1, not ANY!)\n" +
        "\n" +
        "═══════════════════════════════════════════════════════════════════════════════\n" +
        "EXAMPLES:\n" +
        "\n" +
        "Input: /users/12345\n" +
        "Output: {\"template\": \"/users/{id}\", \"validators\": {\"id\": \"NUMERIC\"}}\n" +
        "\n" +
        "Input: /api/orders/550e8400-e29b-41d4-a716-446655440000\n" +
        "Output: {\"template\": \"/api/orders/{orderId}\", \"validators\": {\"orderId\": \"UUID\"}}\n" +
        "\n" +
        "Input: /api/companies/64e7a294e854ff2eb3550075/config\n" +
        "Output: {\"template\": \"/api/companies/{companyId}/config\", \"validators\": {\"companyId\": \"MONGODB\"}}\n" +
        "\n" +
        "Input: /api/v1/rest/character/CHMA0000000001\n" +
        "Output: {\"template\": \"/api/v1/rest/character/{id}\", \"validators\": {\"id\": \"ALPHANUMERIC_ID\"}}\n" +
        "\n" +
        "Input: /content/en/articles\n" +
        "Output: {\"template\": \"/content/{lang}/articles\", \"validators\": {\"lang\": \"ISO_639_1\"}}\n" +
        "\n" +
        "Input: /flights/JFK/departures\n" +
        "Output: {\"template\": \"/flights/{airport}/departures\", \"validators\": {\"airport\": \"IATA_AIRPORT\"}}\n" +
        "\n" +
        "Input: /pricing/USD/products\n" +
        "Output: {\"template\": \"/pricing/{currency}/products\", \"validators\": {\"currency\": \"CURRENCY\"}}\n" +
        "\n" +
        "Input: /docs/getting-started\n" +
        "Output: {\"template\": \"/docs/{slug}\", \"validators\": {\"slug\": \"ANY\"}}\n" +
        "\n" +
        "Input: /api/animal/search\n" +
        "Output: {\"template\": \"/api/animal/search\", \"validators\": {}}\n" +
        "\n" +
        "Input: /users/john_doe/settings\n" +
        "Output: {\"template\": \"/users/{username}/settings\", \"validators\": {\"username\": \"ANY\"}}\n" +
        "\n" +
        "Input: /api/reports/2024/january/summary\n" +
        "Output: {\"template\": \"/api/reports/{year}/{month}/summary\", \"validators\": {\"year\": \"NUMERIC\", \"month\": \"MONTH_NAME\"}}\n" +
        "\n" +
        "Input: /api/channels/C1234567890/messages/1765701919.171019\n" +
        "Output: {\"template\": \"/api/channels/{channelId}/messages/{messageId}\", \"validators\": {\"channelId\": \"ALPHANUMERIC_ID\", \"messageId\": \"TIMESTAMP\"}}\n" +
        "\n" +
        "═══════════════════════════════════════════════════════════════════════════════\n" +
        "SELF-VERIFICATION CHECKLIST (before outputting):\n" +
        "\n" +
        "□ Does every {parameter} in the template have a corresponding validator?\n" +
        "□ Are literal segments truly invariant across all API calls?\n" +
        "□ Did I choose the MOST SPECIFIC validator possible (not defaulting to ANY/NUMERIC)?\n" +
        "□ Would this template match ALL variations of this endpoint pattern?\n" +
        "\n" +
        "═══════════════════════════════════════════════════════════════════════════════\n" +
        "OUTPUT FORMAT:\n" +
        "\n" +
        "Return ONLY valid JSON in this exact format:\n" +
        "{\"template\": \"/path/{param}\", \"validators\": {\"param\": \"VALIDATOR_TYPE\"}}\n" +
        "\n" +
        "Rules:\n" +
        "- Use semantic parameter names: {id}, {userId}, {slug}, {lang}, {country}, etc.\n" +
        "- validators object maps parameter names to validator types\n" +
        "- If no parameters, use empty validators: {\"template\": \"/literal/path\", \"validators\": {}}\n" +
        "- NO explanatory text, NO markdown, ONLY the JSON object\n";

    /**
     * No-arg constructor for reflection-based instantiation via TemplateInferenceServiceFactory.
     *
     * Optional env vars (both have defaults):
     *   BEDROCK_REGION    — AWS region (default: us-east-1)
     *   BEDROCK_MODEL_ID  — model/inference-profile ID
     *                       (default: us.anthropic.claude-3-5-sonnet-20241022-v2:0)
     */
    public BedrockTemplateInferenceService() {
        this(
            Region.of(getEnvOrDefault("BEDROCK_REGION", "us-east-1")),
            getEnvOrDefault("BEDROCK_MODEL_ID", "us.anthropic.claude-3-5-sonnet-20241022-v2:0")
        );
    }

    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
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
            case "ALPHANUMERIC_ID", "ALPHANUMERIC", "ALPHA_NUMERIC_ID" -> SegmentValidator.ALPHANUMERIC_ID;
            case "TIMESTAMP", "UNIX_TIMESTAMP", "SLACK_TIMESTAMP" -> SegmentValidator.TIMESTAMP;
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

            // US state codes
            case "US_STATE", "US_STATE_CODE", "STATE", "STATE_CODE" -> SegmentValidator.US_STATE_CODE;

            // Day of week
            case "DAY_OF_WEEK", "DAY", "WEEKDAY" -> SegmentValidator.DAY_OF_WEEK;

            // Month names
            case "MONTH_NAME", "MONTH" -> SegmentValidator.MONTH_NAME;

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
