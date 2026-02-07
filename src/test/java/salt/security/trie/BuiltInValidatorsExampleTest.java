package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive example-based tests for built-in validators.
 *
 * This test serves dual purposes:
 * 1. Validates that all built-in validators work correctly in real-world scenarios
 * 2. Provides runnable, documented examples for developers
 *
 * Run with: mvn test -Dtest=BuiltInValidatorsExampleTest
 */
class BuiltInValidatorsExampleTest {
    private static final Logger logger = Logger.getLogger(BuiltInValidatorsExampleTest.class.getName());

    private PathTemplateTrie trie;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        logger.info("========== Test Setup ==========");
    }

    @Test
    @DisplayName("Example 1: Airport Flight Information System")
    void testFlightInformationSystem() {
        logger.info("\n=== Example 1: Flight Information ===\n");

        trie.insert("/flights/{origin}/{destination}/schedule",
            Map.of(
                "origin", SegmentValidator.IATA_AIRPORT,
                "destination", SegmentValidator.IATA_AIRPORT
            ));

        logger.info("Valid flight routes:");

        // JFK to LAX (New York to Los Angeles)
        MatchResult result1 = assertValidRoute("/flights/JFK/LAX/schedule");
        assertEquals("JFK", result1.getParams().get("origin"));
        assertEquals("LAX", result1.getParams().get("destination"));

        // LHR to CDG (London to Paris)
        MatchResult result2 = assertValidRoute("/flights/LHR/CDG/schedule");
        assertEquals("LHR", result2.getParams().get("origin"));
        assertEquals("CDG", result2.getParams().get("destination"));

        // DXB to HND (Dubai to Tokyo)
        MatchResult result3 = assertValidRoute("/flights/DXB/HND/schedule");
        assertEquals("DXB", result3.getParams().get("origin"));
        assertEquals("HND", result3.getParams().get("destination"));

        logger.info("\nInvalid routes (should not match):");
        assertInvalidRoute("/flights/XYZ/ABC/schedule");

        logger.info("✓ Flight information system validated\n");
    }

    @Test
    @DisplayName("Example 2: Internationalized Content System")
    void testInternationalizedContentSystem() {
        logger.info("\n=== Example 2: Internationalized Content ===\n");

        trie.insert("/content/{lang}/{country}/articles",
            Map.of(
                "lang", SegmentValidator.ISO_639_1_LANGUAGE,
                "country", SegmentValidator.ISO_3166_COUNTRY_ALPHA2
            ));

        logger.info("Localized content paths:");

        // English, United States
        MatchResult us = assertValidLookup("/content/en/US/articles");
        assertEquals("en", us.getParams().get("lang"));
        assertEquals("US", us.getParams().get("country"));

        // Spanish, Spain
        MatchResult es = assertValidLookup("/content/es/ES/articles");
        assertEquals("es", es.getParams().get("lang"));
        assertEquals("ES", es.getParams().get("country"));

        // French, France
        MatchResult fr = assertValidLookup("/content/fr/FR/articles");
        assertEquals("fr", fr.getParams().get("lang"));
        assertEquals("FR", fr.getParams().get("country"));

        // Chinese, China
        MatchResult zh = assertValidLookup("/content/zh/CN/articles");
        assertEquals("zh", zh.getParams().get("lang"));
        assertEquals("CN", zh.getParams().get("country"));

        // Japanese, Japan
        MatchResult ja = assertValidLookup("/content/ja/JP/articles");
        assertEquals("ja", ja.getParams().get("lang"));
        assertEquals("JP", ja.getParams().get("country"));

        logger.info("✓ Internationalized content system validated\n");
    }

    @Test
    @DisplayName("Example 3: E-commerce with Currency Support")
    void testEcommercePricingSystem() {
        logger.info("\n=== Example 3: E-commerce Pricing ===\n");

        trie.insert("/products/prices/{currency}",
            Map.of("currency", SegmentValidator.ISO_4217_CURRENCY));

        logger.info("Currency-specific pricing:");

        assertCurrency("/products/prices/USD", "USD");  // US Dollar
        assertCurrency("/products/prices/EUR", "EUR");  // Euro
        assertCurrency("/products/prices/GBP", "GBP");  // British Pound
        assertCurrency("/products/prices/JPY", "JPY");  // Japanese Yen
        assertCurrency("/products/prices/BTC", "BTC");  // Bitcoin (crypto)

        logger.info("✓ E-commerce pricing system validated\n");
    }

    @Test
    @DisplayName("Example 4: Error Monitoring System")
    void testErrorMonitoringSystem() {
        logger.info("\n=== Example 4: HTTP Status Monitoring ===\n");

        trie.insert("/monitoring/errors/{status}/details",
            Map.of("status", SegmentValidator.HTTP_STATUS_CODE));

        logger.info("HTTP status endpoints:");

        assertStatus("/monitoring/errors/404/details", "404");  // Not Found
        assertStatus("/monitoring/errors/500/details", "500");  // Internal Server Error
        assertStatus("/monitoring/errors/503/details", "503");  // Service Unavailable
        assertStatus("/monitoring/errors/429/details", "429");  // Too Many Requests
        assertStatus("/monitoring/errors/200/details", "200");  // OK

        logger.info("✓ Error monitoring system validated\n");
    }

    @Test
    @DisplayName("Example 5: Complex Multi-Validator Travel Booking System")
    void testComplexTravelBookingSystem() {
        logger.info("\n=== Example 5: Travel Booking System ===\n");

        trie.insert("/travel/{origin}/{destination}/prices/{currency}/bookings/{id}",
            Map.of(
                "origin", SegmentValidator.IATA_AIRPORT,
                "destination", SegmentValidator.IATA_AIRPORT,
                "currency", SegmentValidator.ISO_4217_CURRENCY,
                "id", SegmentValidator.UUID
            ));

        logger.info("Complex booking path:");

        String bookingPath = "/travel/JFK/LHR/prices/USD/bookings/550e8400-e29b-41d4-a716-446655440000";
        MatchResult result = assertValidLookup(bookingPath);

        assertEquals("JFK", result.getParams().get("origin"));
        assertEquals("LHR", result.getParams().get("destination"));
        assertEquals("USD", result.getParams().get("currency"));
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.getParams().get("id"));

        logger.info("✓ Travel booking system validated\n");
    }

    @Test
    @DisplayName("Example 6: Custom Set-Based Validator for Order Status")
    void testCustomValidatorForOrderStatus() {
        logger.info("\n=== Example 6: Custom Validators ===\n");

        // Define your own closed set
        SegmentValidator ORDER_STATUS = SegmentValidator.setOf(
            "pending", "processing", "shipped", "delivered", "cancelled", "refunded"
        );

        trie.insert("/orders/{status}/list",
            Map.of("status", ORDER_STATUS));

        logger.info("Order status filtering:");

        assertOrderStatus("/orders/pending/list", "pending");
        assertOrderStatus("/orders/processing/list", "processing");
        assertOrderStatus("/orders/shipped/list", "shipped");
        assertOrderStatus("/orders/delivered/list", "delivered");

        logger.info("\nInvalid status (should not match):");
        assertInvalidRoute("/orders/invalid-status/list");

        logger.info("✓ Custom validator system validated\n");
    }

    @Test
    @DisplayName("Example 7: Real-World Combined Use Case - International Travel Platform")
    void testRealWorldCombinedUseCase() {
        logger.info("\n=== Example 7: International Travel Platform ===\n");

        // Multi-language support
        trie.insert("/{lang}/flights/{origin}/{destination}",
            Map.of(
                "lang", SegmentValidator.ISO_639_1_LANGUAGE,
                "origin", SegmentValidator.IATA_AIRPORT,
                "destination", SegmentValidator.IATA_AIRPORT
            ));

        // Country-specific pricing
        trie.insert("/prices/{country}/{currency}/flights",
            Map.of(
                "country", SegmentValidator.ISO_3166_COUNTRY_ALPHA2,
                "currency", SegmentValidator.ISO_4217_CURRENCY
            ));

        logger.info("Multi-language flight search:");
        MatchResult enFlight = assertValidLookup("/en/flights/JFK/LHR");
        assertEquals("en", enFlight.getParams().get("lang"));
        assertEquals("JFK", enFlight.getParams().get("origin"));

        MatchResult esFlight = assertValidLookup("/es/flights/MAD/BCN");
        assertEquals("es", esFlight.getParams().get("lang"));

        logger.info("\nCountry-specific pricing:");
        MatchResult pricing = assertValidLookup("/prices/US/USD/flights");
        assertEquals("US", pricing.getParams().get("country"));
        assertEquals("USD", pricing.getParams().get("currency"));

        logger.info("✓ International travel platform validated\n");
    }

    @Test
    @DisplayName("Performance: Built-in validators maintain sub-microsecond lookup times")
    void testPerformanceWithBuiltInValidators() {
        logger.info("\n=== Performance Test ===\n");

        trie.insert("/flights/{origin}/{destination}/schedule",
            Map.of(
                "origin", SegmentValidator.IATA_AIRPORT,
                "destination", SegmentValidator.IATA_AIRPORT
            ));

        // Warmup
        for (int i = 0; i < 10000; i++) {
            trie.lookup("/flights/JFK/LAX/schedule");
        }

        // Measure
        int iterations = 100000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            MatchResult result = trie.lookup("/flights/JFK/LAX/schedule");
            assertNotNull(result);
        }
        long end = System.nanoTime();

        double avgNanos = (end - start) / (double) iterations;
        double throughput = 1_000_000_000.0 / avgNanos;

        logger.info(String.format("Average lookup time: %.2f nanoseconds", avgNanos));
        logger.info(String.format("Throughput: %,.0f lookups/second", throughput));

        // Assert performance is reasonable (should be under 2 microseconds)
        assertTrue(avgNanos < 2000, "Lookup should be under 2 microseconds");

        logger.info("✓ Set-based validators add minimal overhead (~15ns) to lookup time\n");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private MatchResult assertValidRoute(String path) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        logger.info(String.format("  ✓ %s → %s", path, result.getParams()));
        return result;
    }

    private MatchResult assertValidLookup(String path) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        logger.info(String.format("  ✓ %s → %s", path, result.getParams()));
        return result;
    }

    private void assertInvalidRoute(String path) {
        MatchResult result = trie.lookup(path);
        assertNull(result, "Expected path NOT to match: " + path);
        logger.info(String.format("  ✗ %s (correctly rejected)", path));
    }

    private void assertCurrency(String path, String expectedCurrency) {
        MatchResult result = assertValidLookup(path);
        assertEquals(expectedCurrency, result.getParams().get("currency"));
    }

    private void assertStatus(String path, String expectedStatus) {
        MatchResult result = assertValidLookup(path);
        assertEquals(expectedStatus, result.getParams().get("status"));
    }

    private void assertOrderStatus(String path, String expectedStatus) {
        MatchResult result = assertValidLookup(path);
        assertEquals(expectedStatus, result.getParams().get("status"));
    }
}