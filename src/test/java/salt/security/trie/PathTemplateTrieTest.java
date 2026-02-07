package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PathTemplateTrie based on real API trace data.
 */
class PathTemplateTrieTest {

    private PathTemplateTrie trie;

    // Custom validator for MongoDB ObjectIDs (24 hex characters)
    private static final SegmentValidator MONGODB_ID = segment ->
        segment != null && segment.matches("^[0-9a-fA-F]{24}$");

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
    }

    @Test
    @DisplayName("Should match simple literal paths")
    void testSimpleLiteralPaths() {
        // Insert templates
        trie.insert("/live");
        trie.insert("/ready");
        trie.insert("/metrics");
        trie.insert("/user-permissions");
        trie.insert("/user-tenants");
        trie.insert("/allowed/all-tenants");

        // Test exact matches
        assertMatch("/live", "/live");
        assertMatch("/ready", "/ready");
        assertMatch("/metrics", "/metrics");
        assertMatch("/user-permissions", "/user-permissions");
        assertMatch("/user-tenants", "/user-tenants");
        assertMatch("/allowed/all-tenants", "/allowed/all-tenants");
    }

    @Test
    @DisplayName("Should match paths with single parameter")
    void testSingleParameterPaths() {
        // Insert templates with MongoDB IDs
        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("company_id", MONGODB_ID);
        trie.insert("/api/v2/companies/{company_id}", validators);

        validators.clear();
        validators.put("api_id", MONGODB_ID);
        trie.insert("/api/v2/apis/{api_id}", validators);

        // Test matches
        MatchResult result = trie.lookup("/api/v2/companies/64e7a294e854ff2eb3550075");
        assertNotNull(result);
        assertEquals("/api/v2/companies/{company_id}", result.getTemplate());
        assertEquals("64e7a294e854ff2eb3550075", result.getParams().get("company_id"));

        result = trie.lookup("/api/v2/apis/696f8cf7a6e58839f043faea");
        assertNotNull(result);
        assertEquals("/api/v2/apis/{api_id}", result.getTemplate());
        assertEquals("696f8cf7a6e58839f043faea", result.getParams().get("api_id"));
    }

    @Test
    @DisplayName("Should match complex nested paths from real traces")
    void testComplexNestedPaths() {
        Map<String, SegmentValidator> validators = new HashMap<>();

        // Insert various real templates
        validators.put("company_id", MONGODB_ID);
        trie.insert("/api/v2/companies/{company_id}/attackers-widget/attackers-by-severity", validators);

        validators.clear();
        validators.put("company_id", MONGODB_ID);
        trie.insert("/api/v2/companies/{company_id}/label-management/rules/count", validators);

        validators.clear();
        validators.put("company_id", MONGODB_ID);
        trie.insert("/api/v2/companies/{company_id}/unified-inventory/endpoints/count", validators);

        // Test matches
        assertMatch(
            "/api/v2/companies/68c0a2d0c8214742daee981c/attackers-widget/attackers-by-severity",
            "/api/v2/companies/{company_id}/attackers-widget/attackers-by-severity"
        );

        assertMatch(
            "/api/v2/companies/688a61d2c978ef082f0114a1/label-management/rules/count",
            "/api/v2/companies/{company_id}/label-management/rules/count"
        );

        assertMatch(
            "/api/v2/companies/68c0a2d0c8214742daee981c/unified-inventory/endpoints/count",
            "/api/v2/companies/{company_id}/unified-inventory/endpoints/count"
        );
    }

    @Test
    @DisplayName("Should match paths with multiple parameters")
    void testMultipleParameterPaths() {
        Map<String, SegmentValidator> validators = new HashMap<>();

        // Path with two MongoDB IDs
        validators.put("organization_id", MONGODB_ID);
        validators.put("id", MONGODB_ID);
        trie.insert("/api/v2/recon/organizations/{organization_id}/{id}/domains", validators);

        // Path with MongoDB ID and UUID
        validators.clear();
        validators.put("company_id", MONGODB_ID);
        validators.put("rule_id", SegmentValidator.UUID);
        trie.insert("/api/v2/companies/{company_id}/label-management/rules/{rule_id}", validators);

        // Test matches
        MatchResult result = trie.lookup("/api/v2/recon/organizations/64ee546f963ae65b03f2c2c1/64ee5489963ae65b03f2c2c4/domains");
        assertNotNull(result);
        assertEquals("/api/v2/recon/organizations/{organization_id}/{id}/domains", result.getTemplate());
        assertEquals("64ee546f963ae65b03f2c2c1", result.getParams().get("organization_id"));
        assertEquals("64ee5489963ae65b03f2c2c4", result.getParams().get("id"));

        result = trie.lookup("/api/v2/companies/690908532a6b5f10906a6c70/label-management/rules/0079751d-53d5-4663-a1c3-d89cf0f650b8");
        assertNotNull(result);
        assertEquals("/api/v2/companies/{company_id}/label-management/rules/{rule_id}", result.getTemplate());
        assertEquals("690908532a6b5f10906a6c70", result.getParams().get("company_id"));
        assertEquals("0079751d-53d5-4663-a1c3-d89cf0f650b8", result.getParams().get("rule_id"));
    }

    @Test
    @DisplayName("Should prioritize literal matches over wildcards")
    void testLiteralPriority() {
        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("company_id", MONGODB_ID);

        // Insert wildcard template
        trie.insert("/api/v2/companies/{company_id}/apis/traffic-count", validators);

        // Insert more specific literal template
        trie.insert("/api/v2/companies/special/apis/traffic-count");

        // Wildcard should match regular IDs
        assertMatch(
            "/api/v2/companies/65966227208b5c4ad2ec9408/apis/traffic-count",
            "/api/v2/companies/{company_id}/apis/traffic-count"
        );

        // Literal should match "special"
        assertMatch(
            "/api/v2/companies/special/apis/traffic-count",
            "/api/v2/companies/special/apis/traffic-count"
        );
    }

    @Test
    @DisplayName("Should handle mixed literal and wildcard segments")
    void testMixedSegments() {
        Map<String, SegmentValidator> validators = new HashMap<>();

        validators.put("environment_id", MONGODB_ID);
        trie.insert("/api/v2/authz/permissions/users/me/environments/{environment_id}", validators);

        validators.clear();
        validators.put("organization_id", MONGODB_ID);
        trie.insert("/api/v2/authz/permissions/users/me/organizations/{organization_id}", validators);

        // Test both paths
        MatchResult result = trie.lookup("/api/v2/authz/permissions/users/me/environments/64e7a294e854ff2eb3550075");
        assertNotNull(result);
        assertEquals("/api/v2/authz/permissions/users/me/environments/{environment_id}", result.getTemplate());
        assertEquals("64e7a294e854ff2eb3550075", result.getParams().get("environment_id"));

        result = trie.lookup("/api/v2/authz/permissions/users/me/organizations/64ee546f963ae65b03f2c2c1");
        assertNotNull(result);
        assertEquals("/api/v2/authz/permissions/users/me/organizations/{organization_id}", result.getTemplate());
        assertEquals("64ee546f963ae65b03f2c2c1", result.getParams().get("organization_id"));
    }

    @Test
    @DisplayName("Should reject paths that don't match validators")
    void testValidatorRejection() {
        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("company_id", MONGODB_ID);
        trie.insert("/api/v2/companies/{company_id}/configs", validators);

        // Valid MongoDB ID should match
        assertMatch(
            "/api/v2/companies/677d31e0753d64480c174dd0/configs",
            "/api/v2/companies/{company_id}/configs"
        );

        // Invalid ID (too short) should not match
        assertNoMatch("/api/v2/companies/invalid/configs");

        // Invalid ID (not hex) should not match
        assertNoMatch("/api/v2/companies/zzzzzzzzzzzzzzzzzzzzzzz/configs");
    }

    @Test
    @DisplayName("Should handle UUID parameters correctly")
    void testUUIDParameters() {
        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("company_id", MONGODB_ID);
        validators.put("endpoint_id", SegmentValidator.UUID);
        trie.insert("/api/v2/companies/{company_id}/surface/endpoints/{endpoint_id}", validators);

        // Valid UUID should match
        MatchResult result = trie.lookup("/api/v2/companies/60e446f4582b63b558b0149f/surface/endpoints/d77ba257-1651-4539-9820-72e2866e5bb8");
        assertNotNull(result);
        assertEquals("/api/v2/companies/{company_id}/surface/endpoints/{endpoint_id}", result.getTemplate());
        assertEquals("d77ba257-1651-4539-9820-72e2866e5bb8", result.getParams().get("endpoint_id"));

        // Invalid UUID should not match
        assertNoMatch("/api/v2/companies/60e446f4582b63b558b0149f/surface/endpoints/not-a-uuid");
    }

    @Test
    @DisplayName("Should handle case-insensitive literal matching")
    void testCaseInsensitivity() {
        trie.insert("/api/v2/users/me");

        assertMatch("/api/v2/users/me", "/api/v2/users/me");
        assertMatch("/API/v2/users/me", "/api/v2/users/me");
        assertMatch("/api/V2/USERS/ME", "/api/v2/users/me");
    }

    @Test
    @DisplayName("Should list all templates")
    void testListTemplates() {
        trie.insert("/api/v2/organizations");
        trie.insert("/api/v2/users/me");
        trie.insert("/metrics");

        List<String> templates = trie.listTemplates();
        assertEquals(3, templates.size());
        assertTrue(templates.contains("/api/v2/organizations"));
        assertTrue(templates.contains("/api/v2/users/me"));
        assertTrue(templates.contains("/metrics"));
    }

    @Test
    @DisplayName("Should remove templates")
    void testRemoveTemplate() {
        trie.insert("/api/v2/organizations");
        trie.insert("/api/v2/users/me");

        assertEquals(2, trie.listTemplates().size());

        boolean removed = trie.remove("/api/v2/organizations");
        assertTrue(removed);
        assertEquals(1, trie.listTemplates().size());

        assertNoMatch("/api/v2/organizations");
        assertMatch("/api/v2/users/me", "/api/v2/users/me");

        // Removing non-existent template should return false
        removed = trie.remove("/nonexistent");
        assertFalse(removed);
    }

    @Test
    @DisplayName("Should handle complex real-world paths")
    void testComplexRealWorldPaths() {
        Map<String, SegmentValidator> validators = new HashMap<>();

        // Complex nested path with multiple segments
        validators.put("company_id", MONGODB_ID);
        validators.put("analysis_id", MONGODB_ID);
        trie.insert("/api/v2/companies/{company_id}/oas/analyses/static/{analysis_id}", validators);

        // Admin paths
        validators.clear();
        validators.put("api_id", MONGODB_ID);
        trie.insert("/api/v2/admin/api/{api_id}/config/score", validators);

        validators.clear();
        validators.put("company_id", MONGODB_ID);
        trie.insert("/api/v2/admin/companies/{company_id}/config/detection", validators);

        // Test matches
        assertMatch(
            "/api/v2/companies/66e9c0a734b0a074f96d6f37/oas/analyses/static/6717b17027f7595b0190032e",
            "/api/v2/companies/{company_id}/oas/analyses/static/{analysis_id}"
        );

        assertMatch(
            "/api/v2/admin/api/682e1ef3422f9d62a516d27d/config/score",
            "/api/v2/admin/api/{api_id}/config/score"
        );

        assertMatch(
            "/api/v2/admin/companies/69032542ef91e41967322ea7/config/detection",
            "/api/v2/admin/companies/{company_id}/config/detection"
        );
    }

    @Test
    @DisplayName("Should handle reporting paths with multiple IDs")
    void testReportingPaths() {
        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("organization_id", MONGODB_ID);
        validators.put("report_id", MONGODB_ID);
        trie.insert("/api/v2/reporting/organization/{organization_id}/report/{report_id}", validators);

        MatchResult result = trie.lookup("/api/v2/reporting/organization/674f0decae62a9326ff77576/report/694ee7641b50805a5ae32032");
        assertNotNull(result);
        assertEquals("/api/v2/reporting/organization/{organization_id}/report/{report_id}", result.getTemplate());
        assertEquals("674f0decae62a9326ff77576", result.getParams().get("organization_id"));
        assertEquals("694ee7641b50805a5ae32032", result.getParams().get("report_id"));
    }

    @Test
    @DisplayName("Should handle integration paths")
    void testIntegrationPaths() {
        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("instance_id", MONGODB_ID);
        trie.insert("/v1/integrations/instances/{instance_id}/healthcheck", validators);

        validators.clear();
        validators.put("type_id", MONGODB_ID);
        trie.insert("/v1/integrationsHub/inbound/types/{type_id}", validators);

        assertMatch(
            "/v1/integrations/instances/68adcbd992dd102949a5b627/healthcheck",
            "/v1/integrations/instances/{instance_id}/healthcheck"
        );

        assertMatch(
            "/v1/integrationsHub/inbound/types/695130f7cc95b03864c104cd",
            "/v1/integrationsHub/inbound/types/{type_id}"
        );
    }

    // Helper methods

    private void assertMatch(String path, String expectedTemplate) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path '" + path + "' to match template '" + expectedTemplate + "' but got no match");
        assertEquals(expectedTemplate, result.getTemplate(),
            "Expected template '" + expectedTemplate + "' but got '" + result.getTemplate() + "'");
    }

    private void assertNoMatch(String path) {
        MatchResult result = trie.lookup(path);
        assertNull(result, "Expected path '" + path + "' to not match any template but got: " +
            (result != null ? result.getTemplate() : "null"));
    }
}
