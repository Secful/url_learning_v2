package salt.security.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import salt.security.trie.SegmentValidator;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Template inference service using AWS Bedrock Converse API.
 * Model-agnostic: works with any Bedrock-supported model (Claude, Llama, Mistral, Nova, etc.).
 * Swap BEDROCK_MODEL_ID env var to change model, no code change required.
 */
public class BedrockTemplateInferenceService implements TemplateInferenceService {
    private static final Logger logger = Logger.getLogger(BedrockTemplateInferenceService.class.getName());

    private final BedrockRuntimeClient bedrockClient;
    private final String modelId;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = PromptLoader.get();

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
            logger.info("Invoking Bedrock Converse API to infer template for path: " + path);

            ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.builder().text(SYSTEM_PROMPT).build())
                .messages(Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.builder().text("Path: " + path).build())
                    .build())
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(500).build())
                .build();

            ConverseResponse response = bedrockClient.converse(request);
            String text = response.output().message().content().get(0).text();

            logger.fine("Bedrock response: " + text);
            return parseInferenceResponse(text);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to infer template from Bedrock for path: " + path, e);
            return null;
        }
    }

    private TemplateInference parseInferenceResponse(String text) throws Exception {
        String jsonContent = extractJSON(text);
        if (jsonContent == null) {
            logger.warning("Could not extract JSON from model response");
            return null;
        }

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
