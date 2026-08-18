package salt.security.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import salt.security.PathResolverService;
import salt.security.llm.TemplateInferenceService;
import salt.security.llm.TemplateInferenceServiceFactory;
import salt.security.trie.MatchResult;
import salt.security.trie.PathTemplateTrie;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for template inference.
 * Implementation selected via INFERENCE_SERVICE_CLASS env var.
 *
 * Example (API Gateway):
 *   INFERENCE_SERVICE_CLASS=salt.security.llm.ApiGatewayTemplateInferenceService
 *   API_GW_URL=https://<id>.execute-api.eu-north-1.amazonaws.com/prod/infer
 *   API_GW_AUTH_HEADER_NAME=secret
 *   API_GW_AUTH_HEADER_VALUE=77
 *
 * Example (Bedrock direct):
 *   INFERENCE_SERVICE_CLASS=salt.security.llm.BedrockTemplateInferenceService
 *   BEDROCK_REGION=eu-north-1
 *   BEDROCK_MODEL_ID=eu.anthropic.claude-sonnet-4-6
 *
 * Run with: mvn test -Dtest=BedrockIntegrationTest
 */
class BedrockIntegrationTest {
    private static final Logger logger = Logger.getLogger(BedrockIntegrationTest.class.getName());

    private PathTemplateTrie trie;
    private TemplateInferenceService llmService;
    private PathResolverService resolver;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        llmService = TemplateInferenceServiceFactory.create();
        resolver = new PathResolverService(trie, llmService);

        logger.info("Integration test setup — impl: " +
            System.getenv(TemplateInferenceServiceFactory.CLASS_ENV_VAR));
    }

    @Test
    @DisplayName("Should invoke LLM to infer template for unknown path with company ID")
    void testLLMInferenceForCompanyPath() {
        logger.info("TEST: LLM inference for company path");

        // This path is NOT in the trie, so it should trigger an LLM call
        String path = "/api/v2/companies/64e7a294e854ff2eb3550075/attackers/count";

        logger.info("Resolving path: " + path);
        logger.info("Cache is empty - this WILL call Bedrock");

        MatchResult result = resolver.resolve(path);

        assertNotNull(result, "LLM should have inferred a template");
        logger.info("LLM inferred template: " + result.getTemplate());
        logger.info("Captured params: " + result.getParams());

        // Verify the template makes sense
        assertTrue(result.getTemplate().contains("{"),
            "Template should contain parameter placeholder");
        assertTrue(result.getTemplate().startsWith("/api/v2/companies/"),
            "Template should start with /api/v2/companies/");

        // Second lookup should hit the cache (no LLM call)
        logger.info("Second lookup - should HIT cache (no LLM call)");
        MatchResult cachedResult = resolver.resolve(path);
        assertNotNull(cachedResult);
        assertEquals(result.getTemplate(), cachedResult.getTemplate(),
            "Second lookup should return same template from cache");
    }

    @Test
    @DisplayName("Should invoke LLM for path with UUID parameter")
    void testLLMInferenceForUUIDPath() {
        logger.info("TEST: LLM inference for UUID path");

        String path = "/api/v2/companies/60e446f4582b63b558b0149f/surface/endpoints/d77ba257-1651-4539-9820-72e2866e5bb8";

        logger.info("Resolving path: " + path);
        logger.info("This WILL call Bedrock to infer the template");

        MatchResult result = resolver.resolve(path);

        assertNotNull(result, "LLM should have inferred a template");
        logger.info("LLM inferred template: " + result.getTemplate());
        logger.info("Captured params: " + result.getParams());

        // Verify the inference
        assertTrue(result.getTemplate().contains("{"),
            "Template should contain parameter placeholders");
    }

    @Test
    @DisplayName("Should invoke LLM for complex nested path")
    void testLLMInferenceForComplexPath() {
        logger.info("TEST: LLM inference for complex nested path");

        String path = "/api/v2/companies/688a61d2c978ef082f0114a1/label-management/rules/0079751d-53d5-4663-a1c3-d89cf0f650b8";

        logger.info("Resolving path: " + path);
        logger.info("This WILL call Bedrock");

        MatchResult result = resolver.resolve(path);

        assertNotNull(result, "LLM should have inferred a template");
        logger.info("LLM inferred template: " + result.getTemplate());
        logger.info("Captured params: " + result.getParams());

        // The template should capture both IDs
        int paramCount = result.getParams().size();
        assertTrue(paramCount >= 2,
            "Template should capture at least 2 parameters, got: " + paramCount);
    }

    @Test
    @DisplayName("Should invoke LLM for multiple different paths and cache them")
    void testLLMCacheConvergence() {
        logger.info("TEST: LLM cache convergence");

        String[] paths = {
            "/api/v2/attackers/68a38916820100ce19e2ee41",
            "/api/v2/apis/696f8cf7a6e58839f043faea",
            "/api/v2/timelinesteps/6960f29feaf4945e6c7ba076"
        };

        int initialTemplateCount = trie.listTemplates().size();
        logger.info("Initial template count: " + initialTemplateCount);

        for (String path : paths) {
            logger.info("Resolving: " + path + " (will call LLM)");
            MatchResult result = resolver.resolve(path);
            assertNotNull(result, "Should resolve path: " + path);
            logger.info("  -> Template: " + result.getTemplate());
        }

        int finalTemplateCount = trie.listTemplates().size();
        logger.info("Final template count: " + finalTemplateCount);

        assertTrue(finalTemplateCount > initialTemplateCount,
            "Trie should have grown with new templates from LLM");

        // Verify cache hit on second round (no LLM calls)
        logger.info("Second round - should all hit cache (NO LLM calls)");
        for (String path : paths) {
            logger.info("Resolving from cache: " + path);
            MatchResult result = resolver.resolve(path);
            assertNotNull(result, "Should resolve from cache: " + path);
        }
    }

    @Test
    @DisplayName("Should invoke LLM directly via inference service")
    void testDirectLLMInference() {
        logger.info("TEST: Direct LLM inference (bypassing cache)");

        String path = "/api/v2/reporting/organization/674f0decae62a9326ff77576/report/694ee7641b50805a5ae32032";

        logger.info("Calling LLM directly for path: " + path);
        logger.info("This WILL invoke Bedrock");

        TemplateInferenceService.TemplateInference inference = llmService.inferTemplate(path);

        assertNotNull(inference, "LLM should return an inference");
        assertNotNull(inference.template(), "Inference should contain a template");
        assertNotNull(inference.validators(), "Inference should contain validators");

        logger.info("LLM returned template: " + inference.template());
        logger.info("Validators: " + inference.validators());

        assertTrue(inference.template().startsWith("/api/v2/reporting/"),
            "Template should preserve the path structure");
    }

    @Test
    @DisplayName("Demonstrate cache miss vs cache hit logging")
    void testCacheMissAndHitLogging() {
        logger.info("TEST: Cache miss vs cache hit demonstration");

        String path1 = "/api/v2/companies/5cb75bfb40000069a91df29b/attackTypes";
        String path2 = "/api/v2/companies/677d31e0753d64480c174dd0/attackTypes";

        logger.info("=== FIRST PATH (CACHE MISS - WILL CALL LLM) ===");
        MatchResult result1 = resolver.resolve(path1);
        assertNotNull(result1);
        logger.info("Result: " + result1.getTemplate());

        logger.info("=== SECOND PATH (CACHE HIT - NO LLM CALL) ===");
        logger.info("This path should match the same template, hitting cache");
        MatchResult result2 = resolver.resolve(path2);
        assertNotNull(result2);
        logger.info("Result: " + result2.getTemplate());

        assertEquals(result1.getTemplate(), result2.getTemplate(),
            "Both paths should resolve to the same template");
    }

    @Test
    @DisplayName("Should invoke LLM to infer template for flight deals SEO path")
    void testLLMInferenceForFlightDealsPath() {
        logger.info("TEST: LLM inference for flight deals SEO-friendly path");

        // SEO-friendly flight booking path with country, language, description, airport codes, and travel class
        String path = "/us/en/flight-deals/flights-from-honolulu-to-melbourne.html/hnl/mel/economy";

        logger.info("Resolving path: " + path);
        logger.info("Cache is empty - this WILL call Bedrock to infer template");

        MatchResult result = resolver.resolve(path);

        assertNotNull(result, "LLM should have inferred a template for flight deals path");
        logger.info("LLM inferred template: " + result.getTemplate());
        logger.info("Captured params: " + result.getParams());

        // Verify the template structure
        assertTrue(result.getTemplate().contains("{"),
            "Template should contain parameter placeholders");

        // Test that similar flight paths hit the cache
        logger.info("\n=== Testing cache hit with similar flight path ===");
        String similarPath = "/us/en/flight-deals/flights-from-new-york-to-london.html/jfk/lhr/business";
        logger.info("Resolving similar path: " + similarPath);
        logger.info("This should HIT cache (no LLM call) if template generalizes well");

        MatchResult cachedResult = resolver.resolve(similarPath);
        assertNotNull(cachedResult, "Similar path should resolve using cached template");
        logger.info("Result: " + cachedResult.getTemplate());
        logger.info("Params: " + cachedResult.getParams());

        // Test with different country/language
        logger.info("\n=== Testing with different country/language ===");
        String intlPath = "/gb/en/flight-deals/flights-from-london-to-paris.html/lhr/cdg/first";
        logger.info("Resolving: " + intlPath);

        MatchResult intlResult = resolver.resolve(intlPath);
        assertNotNull(intlResult, "International path should resolve");
        logger.info("Result: " + intlResult.getTemplate());
        logger.info("Params: " + intlResult.getParams());

        // Show final cache state
        logger.info("\n=== Final cache state ===");
        logger.info("Templates in cache:");
        for (String template : trie.listTemplates()) {
            logger.info("  " + template);
        }
    }
}
