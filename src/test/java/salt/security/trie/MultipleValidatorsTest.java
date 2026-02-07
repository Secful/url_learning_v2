package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for multiple validators at the same path position (Option 3 implementation).
 *
 * This demonstrates the new capability: the same API endpoint can accept
 * different ID formats simultaneously. Perfect for:
 * - API versioning (v1 uses numeric, v2 uses UUID)
 * - Migration scenarios (old and new formats coexist)
 * - Multiple client types with different ID schemes
 */
class MultipleValidatorsTest {
    private static final Logger logger = Logger.getLogger(MultipleValidatorsTest.class.getName());

    private PathTemplateTrie trie;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        logger.info("========== Test Setup: Empty trie ==========");
    }

    @Test
    @DisplayName("Should support numeric and UUID IDs at same path position")
    void testMultipleValidatorsAtSamePosition() {
        logger.info("\n=== Testing Multiple Validators at Same Position ===\n");

        // Insert template with NUMERIC validator
        trie.insert("/api/users/{userId}/profile",
            Map.of("userId", SegmentValidator.NUMERIC));
        logger.info("Inserted: /api/users/{userId}/profile with NUMERIC validator");

        // Insert SAME template with UUID validator (creates second wildcard path)
        trie.insert("/api/users/{userId}/profile",
            Map.of("userId", SegmentValidator.UUID));
        logger.info("Inserted: /api/users/{userId}/profile with UUID validator");

        // Test numeric ID
        MatchResult result1 = trie.lookup("/api/users/12345/profile");
        assertNotNull(result1, "Should match numeric ID");
        assertEquals("/api/users/{userId}/profile", result1.getTemplate());
        assertEquals("12345", result1.getParams().get("userId"));
        logger.info("✓ Numeric ID matched: " + result1.getParams());

        // Test UUID
        MatchResult result2 = trie.lookup("/api/users/550e8400-e29b-41d4-a716-446655440000/profile");
        assertNotNull(result2, "Should match UUID");
        assertEquals("/api/users/{userId}/profile", result2.getTemplate());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result2.getParams().get("userId"));
        logger.info("✓ UUID matched: " + result2.getParams());

        // Test invalid format (alphabetic)
        MatchResult result3 = trie.lookup("/api/users/alice/profile");
        assertNull(result3, "Should NOT match alphabetic string");
        logger.info("✓ Alphabetic string correctly rejected");

        logger.info("\n=== Test passed! Multiple validators coexist successfully ===\n");
    }

    @Test
    @DisplayName("Should support API versioning with different ID formats")
    void testAPIVersioningScenario() {
        logger.info("\n=== Testing API Versioning Scenario ===\n");

        // v1 API uses numeric IDs
        trie.insert("/api/v1/orders/{orderId}/details",
            Map.of("orderId", SegmentValidator.NUMERIC));
        logger.info("v1 API: Numeric IDs");

        // v2 API uses UUID IDs
        trie.insert("/api/v2/orders/{orderId}/details",
            Map.of("orderId", SegmentValidator.UUID));
        logger.info("v2 API: UUID IDs");

        // v1 request with numeric ID
        MatchResult v1Result = trie.lookup("/api/v1/orders/123/details");
        assertNotNull(v1Result, "v1 should accept numeric IDs");
        assertEquals("123", v1Result.getParams().get("orderId"));
        logger.info("✓ v1 with numeric ID: " + v1Result.getParams());

        // v2 request with UUID
        MatchResult v2Result = trie.lookup("/api/v2/orders/550e8400-e29b-41d4-a716-446655440000/details");
        assertNotNull(v2Result, "v2 should accept UUIDs");
        assertEquals("550e8400-e29b-41d4-a716-446655440000", v2Result.getParams().get("orderId"));
        logger.info("✓ v2 with UUID: " + v2Result.getParams());

        // Cross-version requests should fail
        MatchResult v1WithUUID = trie.lookup("/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/details");
        assertNull(v1WithUUID, "v1 should reject UUIDs");
        logger.info("✓ v1 correctly rejects UUID");

        MatchResult v2WithNumeric = trie.lookup("/api/v2/orders/123/details");
        assertNull(v2WithNumeric, "v2 should reject numeric IDs");
        logger.info("✓ v2 correctly rejects numeric ID");

        logger.info("\n=== API Versioning test passed! ===\n");
    }

    @Test
    @DisplayName("Should handle migration scenario with both ID formats")
    void testMigrationScenario() {
        logger.info("\n=== Testing Migration Scenario ===\n");

        // During migration, both old (numeric) and new (UUID) IDs are valid
        trie.insert("/api/products/{productId}/reviews",
            Map.of("productId", SegmentValidator.NUMERIC));
        logger.info("Added support for legacy numeric IDs");

        trie.insert("/api/products/{productId}/reviews",
            Map.of("productId", SegmentValidator.UUID));
        logger.info("Added support for new UUID IDs");

        // Old clients using numeric IDs
        MatchResult legacyResult = trie.lookup("/api/products/789/reviews");
        assertNotNull(legacyResult, "Legacy numeric IDs should still work");
        logger.info("✓ Legacy client (numeric): " + legacyResult.getParams());

        // New clients using UUIDs
        MatchResult newResult = trie.lookup("/api/products/123e4567-e89b-12d3-a456-426614174000/reviews");
        assertNotNull(newResult, "New UUID IDs should work");
        logger.info("✓ New client (UUID): " + newResult.getParams());

        logger.info("\n=== Migration scenario supported! ===\n");
    }

    @Test
    @DisplayName("Should handle three different validators at same position")
    void testThreeValidatorsAtSamePosition() {
        logger.info("\n=== Testing Three Validators at Same Position ===\n");

        // MongoDB ObjectId pattern (24 hex chars)
        SegmentValidator MONGO_ID = segment ->
            segment != null && segment.matches("^[0-9a-fA-F]{24}$");

        // Add three different validators for the same path position
        trie.insert("/api/items/{itemId}",
            Map.of("itemId", SegmentValidator.NUMERIC));
        logger.info("Added NUMERIC validator");

        trie.insert("/api/items/{itemId}",
            Map.of("itemId", SegmentValidator.UUID));
        logger.info("Added UUID validator");

        trie.insert("/api/items/{itemId}",
            Map.of("itemId", MONGO_ID));
        logger.info("Added MongoDB ObjectId validator");

        // Test numeric
        MatchResult r1 = trie.lookup("/api/items/123");
        assertNotNull(r1, "Should match numeric");
        logger.info("✓ Numeric: " + r1.getParams());

        // Test UUID
        MatchResult r2 = trie.lookup("/api/items/550e8400-e29b-41d4-a716-446655440000");
        assertNotNull(r2, "Should match UUID");
        logger.info("✓ UUID: " + r2.getParams());

        // Test MongoDB ObjectId
        MatchResult r3 = trie.lookup("/api/items/507f1f77bcf86cd799439011");
        assertNotNull(r3, "Should match MongoDB ObjectId");
        logger.info("✓ MongoDB ObjectId: " + r3.getParams());

        // Test invalid format
        MatchResult r4 = trie.lookup("/api/items/invalid-id");
        assertNull(r4, "Should reject invalid format");
        logger.info("✓ Invalid format correctly rejected");

        logger.info("\n=== Three validators coexist successfully! ===\n");
    }

    @Test
    @DisplayName("Should not create duplicate wildcard paths")
    void testNoDuplicateWildcardPaths() {
        logger.info("\n=== Testing No Duplicate Wildcard Paths ===\n");

        // Insert same template with same validator twice
        trie.insert("/api/users/{id}/data",
            Map.of("id", SegmentValidator.NUMERIC));

        trie.insert("/api/users/{id}/data",
            Map.of("id", SegmentValidator.NUMERIC));

        // Should reuse existing wildcard path, not create duplicate
        MatchResult result = trie.lookup("/api/users/123/data");
        assertNotNull(result);

        // Verify by checking template list
        var templates = trie.listTemplates();
        assertEquals(1, templates.size(), "Should have only one template, not duplicates");
        logger.info("✓ No duplicate paths created");

        logger.info("\n=== Deduplication works correctly! ===\n");
    }
}
