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
        "2. Use semantic parameter names (e.g., {id}, {name}, {userId}, {companyId})\n" +
        "3. Identify segment types:\n" +
        "   - NUMERIC: digits only (e.g., 123, 456)\n" +
        "   - UUID: standard format (e.g., 550e8400-e29b-41d4-a716-446655440000)\n" +
        "   - MONGODB: 24 hex characters (e.g., 64e7a294e854ff2eb3550075)\n" +
        "   - ANY: any other non-empty string\n" +
        "4. Return ONLY valid JSON, no explanatory text\n" +
        "5. Keep literal segments as-is (e.g., /api, /users, /posts)\n" +
        "Format: {\"template\": \"/path/{param}\", \"validators\": {\"param\": \"type\"}}\n" +
        "Examples:\n" +
        "- /users/123 -> {\"template\": \"/users/{id}\", \"validators\": {\"id\": \"NUMERIC\"}}\n" +
        "- /users/jack/posts/456 -> {\"template\": \"/users/{name}/posts/{id}\", \"validators\": {\"name\": \"ANY\", \"id\": \"NUMERIC\"}}\n" +
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
     */
    private SegmentValidator parseValidator(String type) {
        if (type == null) {
            return SegmentValidator.ANY;
        }

        // MongoDB ObjectID validator (24 hex characters)
        SegmentValidator MONGODB_ID = segment ->
            segment != null && segment.matches("^[0-9a-fA-F]{24}$");

        switch (type.toUpperCase()) {
            case "NUMERIC":
                return SegmentValidator.NUMERIC;
            case "UUID":
                return SegmentValidator.UUID;
            case "OBJECTID":
            case "MONGODB":
            case "OBJECT":
                return MONGODB_ID;
            case "ANY":
            default:
                return SegmentValidator.ANY;
        }
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
