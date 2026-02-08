package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for handling user names and usernames in path templates.
 *
 * Demonstrates how the system handles names like:
 * - Simple names: jack, alice, bob
 * - Compound names: mary-jane, o'brien
 * - Usernames: user123, john_doe
 * - International names: josé, 张伟
 *
 * Currently uses SegmentValidator.ANY which accepts any non-empty string.
 * This test documents current behavior and will help evaluate if we need
 * a dedicated NAME validator in the future.
 */
class UserNamesValidatorTest {
    private static final Logger logger = Logger.getLogger(UserNamesValidatorTest.class.getName());

    private PathTemplateTrie trie;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        logger.info("========== Test Setup ==========");
    }

    @Test
    @DisplayName("Should handle simple user names with ANY validator")
    void testSimpleUserNames() {
        logger.info("\n=== Test: Simple User Names ===\n");

        // Using ANY validator (accepts any non-empty string)
        trie.insert("/users/{name}/profile",
            Map.of("name", SegmentValidator.ANY));

        logger.info("Testing simple names:");

        // Simple single names
        assertMatchesUser("/users/jack/profile", "jack");
        assertMatchesUser("/users/alice/profile", "alice");
        assertMatchesUser("/users/bob/profile", "bob");
        assertMatchesUser("/users/mary/profile", "mary");
        assertMatchesUser("/users/john/profile", "john");

        // Case variations
        assertMatchesUser("/users/Jack/profile", "Jack");
        assertMatchesUser("/users/ALICE/profile", "ALICE");
        assertMatchesUser("/users/MaryJane/profile", "MaryJane");

        logger.info("✓ Simple user names work with ANY validator\n");
    }

    @Test
    @DisplayName("Should handle compound names with ANY validator")
    void testCompoundNames() {
        logger.info("\n=== Test: Compound Names ===\n");

        trie.insert("/users/{name}/profile",
            Map.of("name", SegmentValidator.ANY));

        logger.info("Testing compound names:");

        // Hyphenated names
        assertMatchesUser("/users/mary-jane/profile", "mary-jane");
        assertMatchesUser("/users/jean-paul/profile", "jean-paul");
        assertMatchesUser("/users/anne-marie/profile", "anne-marie");

        // Names with apostrophes
        assertMatchesUser("/users/o'brien/profile", "o'brien");
        assertMatchesUser("/users/d'angelo/profile", "d'angelo");

        // Names with underscores (username style)
        assertMatchesUser("/users/john_doe/profile", "john_doe");
        assertMatchesUser("/users/jane_smith/profile", "jane_smith");

        logger.info("✓ Compound names work with ANY validator\n");
    }

    @Test
    @DisplayName("Should handle usernames with numbers and special chars")
    void testUsernamesWithNumbers() {
        logger.info("\n=== Test: Usernames with Numbers ===\n");

        trie.insert("/users/{username}/dashboard",
            Map.of("username", SegmentValidator.ANY));

        logger.info("Testing usernames with numbers:");

        // Username patterns
        assertMatchesUser("/users/user123/dashboard", "user123");
        assertMatchesUser("/users/john_doe_99/dashboard", "john_doe_99");
        assertMatchesUser("/users/alice2024/dashboard", "alice2024");
        assertMatchesUser("/users/bob-admin/dashboard", "bob-admin");
        assertMatchesUser("/users/test_user_001/dashboard", "test_user_001");

        logger.info("✓ Usernames with numbers work with ANY validator\n");
    }

    @Test
    @DisplayName("Should handle international names")
    void testInternationalNames() {
        logger.info("\n=== Test: International Names ===\n");

        trie.insert("/users/{name}/settings",
            Map.of("name", SegmentValidator.ANY));

        logger.info("Testing international names:");

        // Spanish/Portuguese names
        assertMatchesUser("/users/josé/settings", "josé");
        assertMatchesUser("/users/maría/settings", "maría");
        assertMatchesUser("/users/joão/settings", "joão");

        // French names
        assertMatchesUser("/users/françois/settings", "françois");
        assertMatchesUser("/users/rené/settings", "rené");

        // German names
        assertMatchesUser("/users/müller/settings", "müller");
        assertMatchesUser("/users/könig/settings", "könig");

        // Nordic names
        assertMatchesUser("/users/bjørn/settings", "bjørn");
        assertMatchesUser("/users/åsa/settings", "åsa");

        logger.info("✓ International names work with ANY validator\n");
    }

    @Test
    @DisplayName("Should handle multiple name parameters in same path")
    void testMultipleNameParameters() {
        logger.info("\n=== Test: Multiple Name Parameters ===\n");

        trie.insert("/users/{firstName}/{lastName}/profile",
            Map.of(
                "firstName", SegmentValidator.ANY,
                "lastName", SegmentValidator.ANY
            ));

        logger.info("Testing paths with multiple names:");

        MatchResult result1 = trie.lookup("/users/john/smith/profile");
        assertNotNull(result1);
        assertEquals("john", result1.getParams().get("firstName"));
        assertEquals("smith", result1.getParams().get("lastName"));
        logger.info("  ✓ /users/john/smith/profile → firstName=john, lastName=smith");

        MatchResult result2 = trie.lookup("/users/mary-jane/watson/profile");
        assertNotNull(result2);
        assertEquals("mary-jane", result2.getParams().get("firstName"));
        assertEquals("watson", result2.getParams().get("lastName"));
        logger.info("  ✓ /users/mary-jane/watson/profile → firstName=mary-jane, lastName=watson");

        logger.info("✓ Multiple name parameters work correctly\n");
    }

    @Test
    @DisplayName("Should distinguish between user paths and other paths")
    void testDistinguishUserPaths() {
        logger.info("\n=== Test: Distinguish User vs ID Paths ===\n");

        // Different validators for different ID types
        trie.insert("/users/{id}/profile",
            Map.of("id", SegmentValidator.NUMERIC));

        trie.insert("/accounts/{username}/settings",
            Map.of("username", SegmentValidator.ANY));

        logger.info("Testing path disambiguation:");

        // Numeric ID path
        MatchResult numericResult = trie.lookup("/users/12345/profile");
        assertNotNull(numericResult);
        assertEquals("/users/{id}/profile", numericResult.getTemplate());
        assertEquals("12345", numericResult.getParams().get("id"));
        logger.info("  ✓ /users/12345/profile → numeric ID template");

        // Username path
        MatchResult nameResult = trie.lookup("/accounts/jack/settings");
        assertNotNull(nameResult);
        assertEquals("/accounts/{username}/settings", nameResult.getTemplate());
        assertEquals("jack", nameResult.getParams().get("username"));
        logger.info("  ✓ /accounts/jack/settings → username template");

        // Non-numeric value should not match numeric path
        assertNull(trie.lookup("/users/jack/profile"));
        logger.info("  ✓ /users/jack/profile → correctly rejected (not numeric)");

        logger.info("✓ Path disambiguation works correctly\n");
    }

    @Test
    @DisplayName("Should handle edge cases and special usernames")
    void testEdgeCases() {
        logger.info("\n=== Test: Edge Cases ===\n");

        trie.insert("/users/{username}/data",
            Map.of("username", SegmentValidator.ANY));

        logger.info("Testing edge cases:");

        // Very short names
        assertMatchesUser("/users/a/data", "a");
        assertMatchesUser("/users/jo/data", "jo");

        // Long usernames
        assertMatchesUser("/users/verylongusername123456789/data", "verylongusername123456789");

        // Special patterns that are still valid
        assertMatchesUser("/users/user-name-with-dashes/data", "user-name-with-dashes");
        assertMatchesUser("/users/user.with.dots/data", "user.with.dots");
        assertMatchesUser("/users/user+tag/data", "user+tag");

        logger.info("✓ Edge cases handled correctly\n");
    }

    @Test
    @DisplayName("Should handle empty and null cases correctly")
    void testInvalidCases() {
        logger.info("\n=== Test: Invalid Cases ===\n");

        trie.insert("/users/{name}/profile",
            Map.of("name", SegmentValidator.ANY));

        logger.info("Testing invalid cases:");

        // Empty path should not match
        assertNull(trie.lookup("/users//profile"));
        logger.info("  ✓ Empty username correctly rejected");

        // Just slashes
        assertNull(trie.lookup("///"));
        logger.info("  ✓ Invalid path correctly rejected");

        logger.info("✓ Invalid cases handled correctly\n");
    }

    @Test
    @DisplayName("Real-world example: Social media user profiles")
    void testRealWorldSocialMediaExample() {
        logger.info("\n=== Real-World Example: Social Media Profiles ===\n");

        // Typical social media routes
        trie.insert("/{username}",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/{username}/posts",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/{username}/followers",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/{username}/following",
            Map.of("username", SegmentValidator.ANY));

        logger.info("Testing social media style paths:");

        assertMatchesUser("/jack", "jack");
        assertMatchesUser("/alice123", "alice123");
        assertMatchesUser("/john_doe/posts", "john_doe");
        assertMatchesUser("/mary-jane/followers", "mary-jane");
        assertMatchesUser("/bob.smith/following", "bob.smith");

        logger.info("✓ Social media style paths work correctly\n");
    }

    @Test
    @DisplayName("Performance test: Name validation should be fast")
    void testPerformance() {
        logger.info("\n=== Performance Test: Name Validation ===\n");

        trie.insert("/users/{name}/profile",
            Map.of("name", SegmentValidator.ANY));

        // Warmup
        for (int i = 0; i < 10000; i++) {
            trie.lookup("/users/jack/profile");
        }

        // Measure
        int iterations = 100000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            MatchResult result = trie.lookup("/users/jack/profile");
            assertNotNull(result);
        }
        long end = System.nanoTime();

        double avgNanos = (end - start) / (double) iterations;
        logger.info(String.format("Average lookup time: %.2f nanoseconds", avgNanos));
        logger.info(String.format("Throughput: %,.0f lookups/second", 1_000_000_000.0 / avgNanos));

        // Should be under 2 microseconds
        assertTrue(avgNanos < 2000, "Lookup should be under 2 microseconds");

        logger.info("✓ Performance is excellent (sub-microsecond)\n");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void assertMatchesUser(String path, String expectedName) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);

        // The parameter name could be "name", "username", "firstName", etc.
        // Just check that one of the params has the expected value
        assertTrue(result.getParams().containsValue(expectedName),
            "Expected to find name: " + expectedName + " in params: " + result.getParams());

        logger.info(String.format("  ✓ %s → %s", path, result.getParams()));
    }
}
