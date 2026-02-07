package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for set-based validators using SegmentValidator.setOf().
 *
 * Set-based validators are optimal for:
 * - Language codes (en, es, fr, de, etc.)
 * - Airport codes (JFK, LAX, CDG, etc.)
 * - Country codes (US, GB, FR, etc.)
 * - Status values (active, pending, closed, etc.)
 * - Any closed set of known values
 *
 * Performance: ~15-20 nanoseconds (faster than regex!)
 */
class SetBasedValidatorTest {
    private static final Logger logger = Logger.getLogger(SetBasedValidatorTest.class.getName());

    private PathTemplateTrie trie;

    // Example: Language code validator
    private static final SegmentValidator LANGUAGES = SegmentValidator.setOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ar"
    );

    // Example: Airport code validator (IATA)
    private static final SegmentValidator AIRPORTS = SegmentValidator.setOf(
        "JFK", "LAX", "ORD", "DFW", "ATL", "CDG", "LHR", "DXB", "HND", "FRA"
    );

    // Example: Status validator
    private static final SegmentValidator STATUS = SegmentValidator.setOf(
        "active", "pending", "completed", "cancelled", "failed"
    );

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        logger.info("========== Test Setup: Empty trie ==========");
    }

    @Test
    @DisplayName("Should validate language codes using set-based validator")
    void testLanguageCodeValidator() {
        logger.info("\n=== Testing Language Code Validator ===\n");

        trie.insert("/api/content/{lang}/articles",
            Map.of("lang", LANGUAGES));

        // Valid language codes
        MatchResult en = trie.lookup("/api/content/en/articles");
        assertNotNull(en, "Should accept 'en'");
        assertEquals("en", en.getParams().get("lang"));
        logger.info("✓ English (en): " + en.getParams());

        MatchResult es = trie.lookup("/api/content/es/articles");
        assertNotNull(es, "Should accept 'es'");
        logger.info("✓ Spanish (es): " + es.getParams());

        // Case insensitive
        MatchResult EN = trie.lookup("/api/content/EN/articles");
        assertNotNull(EN, "Should accept 'EN' (case insensitive)");
        logger.info("✓ Case insensitive: EN → " + EN.getParams());

        // Invalid language code
        MatchResult invalid = trie.lookup("/api/content/xyz/articles");
        assertNull(invalid, "Should reject invalid language code 'xyz'");
        logger.info("✓ Invalid code 'xyz' correctly rejected");

        logger.info("\n=== Language validation passed! ===\n");
    }

    @Test
    @DisplayName("Should validate airport codes using set-based validator")
    void testAirportCodeValidator() {
        logger.info("\n=== Testing Airport Code Validator ===\n");

        trie.insert("/api/flights/{origin}/{destination}",
            Map.of(
                "origin", AIRPORTS,
                "destination", AIRPORTS
            ));

        // Valid flight route
        MatchResult flight1 = trie.lookup("/api/flights/JFK/LAX");
        assertNotNull(flight1, "Should accept JFK → LAX");
        assertEquals("JFK", flight1.getParams().get("origin"));
        assertEquals("LAX", flight1.getParams().get("destination"));
        logger.info("✓ Valid route: " + flight1.getParams());

        // Invalid airport code
        MatchResult invalid = trie.lookup("/api/flights/XYZ/ABC");
        assertNull(invalid, "Should reject invalid airport codes");
        logger.info("✓ Invalid airports correctly rejected");

        logger.info("\n=== Airport validation passed! ===\n");
    }
}
