package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import salt.security.PathResolverService;
import salt.security.llm.TemplateInferenceServiceFactory;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using actual API trace data from DuckDB production traces.
 * Tests use PathResolverService - the production API.
 * Demonstrates the complete flow: empty cache → LLM inference → populate cache → cache hit.
 */
class RealWorldTracesTest {
    private static final Logger logger = Logger.getLogger(RealWorldTracesTest.class.getName());

    private PathResolverService resolver;

    // Sample paths from DuckDB traces database
    private static final String[] SAMPLE_PATHS = {
        "/api/v2/companies/64b7f64b848b4d06689cd28c/insights/status",
        "/api/v2/companies/645d4369eb31790784df4dc0/unified-inventory/host-names",
        "/api/v2/companies/57bc705ad5e37220d012ce10/agentic-entities/agentic-entity",
        "/api/v2/apis/68a227867fdcd43dc8d2666a/endpoints/count",
        "/api/v2/admin/api/682e1ef3422f9d62a516d27d/config/score",
        "/api/v2/admin/companies/67a3aedf356c41315b102159/config/detection",
        "/api/v2/companies/64ee5489963ae65b03f2c2c4/surface/endpoints",
        "/api/v2/companies/645d4369eb31790784df4dc0/posturegaps",
        "/api/v2/companies/62ab26fe8f53ec660f26af54/correlators",
        "/api/v2/recon/organizations/68befe54c85efb6b109d1716/rescan",
        "/api/v2/organizations/6942ee0239e26535bd60fcf2/account/id",
        "/api/v2/companies/60e446f4582b63b558b0149f/posturegaps/csv",
        "/api/v2/companies/654bc386246a8b65e779ec64/sensitive/data/grouping/parameter",
        "/api/v2/integrations/instances/68cd26af17b8820ea658fac8",
        "/api/v2/companies/68af3287aa9ad44acdf6372f/timelinesteps/696f4b52803c910ea8a10df8",
        "/api/v2/validation-rules/rules/6847c8c41b0000935dc44d38/toggle-activation",
        "/api/v1/public/integrations/attackers",
        "/api/v2/apis/695c1b63e426fd72d902d3ee/traffic",
        "/api/v2/management/organizations/606c808f120000c127d3a2a1/idp",
        "/live",
        "/api/v2/69032542ef91e41967322ea7/onboarding/onboarding-enabled",
        "/api/v2/authz/permissions/users/me/organizations/64ee546f963ae65b03f2c2c1",
        "/api/v2/companies/6842e695581a705f347522ec/apis/mask",
        "/api/v2/companies/60e446f4582b63b558b0149f/config/data-categories/df2d2dfa-99d3-430f-a888-291d5d7b1d4b",
        "/api/v2/token"
    };

    @BeforeEach
    void setUp() {
        // Create resolver with empty cache
        resolver = createResolver();
        logger.info("===== Test Setup: Empty cache, will use LLM to infer templates =====");
    }

    /**
     * Creates a PathResolverService with default configuration.
     * Implementation detail: Uses Bedrock Claude for LLM inference.
     */
    private PathResolverService createResolver() {
        PathTemplateTrie trie = new PathTemplateTrie();
        return new PathResolverService(trie, TemplateInferenceServiceFactory.create());
    }

    @Test
    @DisplayName("Should use LLM to resolve path with company ID on cache miss")
    void testResolveSinglePathWithLLM() {
        String path = "/api/v2/companies/64b7f64b848b4d06689cd28c/insights/status";

        logger.info("Trie is empty. Resolving: " + path);
        assertTrue(resolver.getCacheStats().contains("0 templates"), "Cache should start empty");

        MatchResult result = resolver.resolve(path);

        assertNotNull(result, "LLM should infer a template for: " + path);
        logger.info("LLM inferred template: " + result.getTemplate());

        // Verify template was cached
        assertTrue(resolver.getCacheStats().contains("1 template"), "Template should be cached");
        assertTrue(result.getTemplate().contains("{"), "Template should have parameters");
    }

    @Test
    @DisplayName("Should resolve multiple similar paths - first calls LLM, rest hit cache")
    void testCacheConvergence() {
        String[] companyPaths = {
            "/api/v2/companies/64b7f64b848b4d06689cd28c/insights/status",
            "/api/v2/companies/645d4369eb31790784df4dc0/unified-inventory/host-names",
            "/api/v2/companies/57bc705ad5e37220d012ce10/agentic-entities/agentic-entity",
            "/api/v2/companies/62ab26fe8f53ec660f26af54/correlators"
        };

        logger.info("=== Testing cache convergence ===");
        assertTrue(resolver.getCacheStats().contains("0 templates"), "Cache should start empty");

        for (String path : companyPaths) {
            logger.info("Resolving: " + path);
            MatchResult result = resolver.resolve(path);
            assertNotNull(result, "Should resolve: " + path);
            logger.info("  Resolved to: " + result.getTemplate());
        }

        logger.info("Final cache state: " + resolver.getCacheStats());

        // Show all cached templates
        logger.info("Cached templates:");
        for (String template : resolver.getTrie().listTemplates()) {
            logger.info("  " + template);
        }
    }

    @Test
    @DisplayName("Should resolve paths with UUIDs using LLM")
    void testResolvePathsWithUUIDs() {
        String path = "/api/v2/companies/60e446f4582b63b558b0149f/config/data-categories/df2d2dfa-99d3-430f-a888-291d5d7b1d4b";

        logger.info("Resolving path with UUID: " + path);
        MatchResult result = resolver.resolve(path);

        assertNotNull(result, "Should resolve path with UUID");
        logger.info("Template: " + result.getTemplate());
        logger.info("Params: " + result.getParams());

        assertTrue(result.getParams().size() >= 2, "Should capture multiple parameters");
    }

    @Test
    @DisplayName("Should resolve admin API paths")
    void testResolveAdminPaths() {
        String[] adminPaths = {
            "/api/v2/admin/api/682e1ef3422f9d62a516d27d/config/score",
            "/api/v2/admin/companies/67a3aedf356c41315b102159/config/detection"
        };

        for (String path : adminPaths) {
            logger.info("Resolving admin path: " + path);
            MatchResult result = resolver.resolve(path);
            assertNotNull(result, "Should resolve: " + path);
            logger.info("  Template: " + result.getTemplate());
        }
    }

    @Test
    @DisplayName("Should resolve simple literal paths")
    void testResolveLiteralPaths() {
        String[] literalPaths = {"/live", "/api/v2/token", "/api/v1/public/integrations/attackers"};

        for (String path : literalPaths) {
            logger.info("Resolving literal path: " + path);
            MatchResult result = resolver.resolve(path);
            assertNotNull(result, "Should resolve: " + path);
            logger.info("  Template: " + result.getTemplate());

            // Literal paths should match themselves
            assertTrue(result.getTemplate().equals(path) || result.getTemplate().contains("{"),
                "Should be literal or have parameters");
        }
    }

    @Test
    @DisplayName("Should resolve complex nested paths from traces")
    void testResolveComplexNestedPaths() {
        String[] complexPaths = {
            "/api/v2/companies/645d4369eb31790784df4dc0/posturegaps",
            "/api/v2/companies/654bc386246a8b65e779ec64/sensitive/data/grouping/parameter",
            "/api/v2/companies/68af3287aa9ad44acdf6372f/timelinesteps/696f4b52803c910ea8a10df8",
            "/api/v2/recon/organizations/68befe54c85efb6b109d1716/rescan",
            "/api/v2/validation-rules/rules/6847c8c41b0000935dc44d38/toggle-activation"
        };

        logger.info("=== Testing complex nested paths ===");

        for (String path : complexPaths) {
            logger.info("Resolving: " + path);
            MatchResult result = resolver.resolve(path);
            assertNotNull(result, "Should resolve complex path: " + path);
            logger.info("  Template: " + result.getTemplate());
            logger.info("  Params: " + result.getParams());
        }
    }

    @Test
    @DisplayName("Should demonstrate LLM call on first path, cache hit on similar paths")
    void testCacheMissVsCacheHit() {
        logger.info("=== Demonstrating cache miss vs cache hit ===");

        String path1 = "/api/v2/apis/68a227867fdcd43dc8d2666a/endpoints/count";
        String path2 = "/api/v2/apis/695c1b63e426fd72d902d3ee/traffic";

        logger.info("FIRST PATH (CACHE MISS - WILL CALL LLM)");
        logger.info("Resolving: " + path1);
        MatchResult result1 = resolver.resolve(path1);
        assertNotNull(result1);
        logger.info("Result: " + result1.getTemplate());

        logger.info("SECOND PATH (MAY HIT CACHE if patterns match)");
        logger.info("Resolving: " + path2);
        MatchResult result2 = resolver.resolve(path2);
        assertNotNull(result2);
        logger.info("Result: " + result2.getTemplate());

        logger.info("Cache state: " + resolver.getCacheStats());
    }

    @Test
    @DisplayName("Should resolve all sample paths from traces database")
    void testResolveAllSamplePaths() {
        logger.info("=== Resolving all " + SAMPLE_PATHS.length + " sample paths ===");

        int successCount = 0;
        for (String path : SAMPLE_PATHS) {
            logger.info("Resolving: " + path);
            MatchResult result = resolver.resolve(path);
            if (result != null) {
                successCount++;
                logger.info("  OK: " + result.getTemplate());
            } else {
                logger.warning("  FAILED to resolve: " + path);
            }
        }

        logger.info("Successfully resolved " + successCount + " out of " + SAMPLE_PATHS.length + " paths");
        logger.info("Final cache state: " + resolver.getCacheStats());

        // Should resolve at least most paths
        assertTrue(successCount >= SAMPLE_PATHS.length * 0.8,
            "Should resolve at least 80% of paths, resolved: " + successCount + "/" + SAMPLE_PATHS.length);

        // Show final cached templates
        logger.info("=== Final cached templates ===");
        for (String template : resolver.getTrie().listTemplates()) {
            logger.info("  " + template);
        }
    }
}
