package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all built-in validators (IATA, ICAO, ISO codes, currencies, HTTP status).
 *
 * These validators are optimized for real-world closed sets like:
 * - Airport codes (JFK, LAX, LHR)
 * - Language codes (en, es, fr)
 * - Country codes (US, GB, FR)
 * - Currency codes (USD, EUR, GBP)
 * - HTTP status codes (200, 404, 500)
 */
class BuiltInValidatorsTest {

    private PathTemplateTrie trie;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
    }

    // =========================================================================
    // AIRPORT CODE TESTS
    // =========================================================================

    @Test
    @DisplayName("Should validate IATA airport codes (3-letter)")
    void testIATAAirportValidator() {
        trie.insert("/flights/{origin}/{destination}/schedule",
            Map.of(
                "origin", SegmentValidator.IATA_AIRPORT,
                "destination", SegmentValidator.IATA_AIRPORT
            ));

        // Valid IATA codes (US airports)
        assertMatch("/flights/JFK/LAX/schedule", "JFK", "LAX");
        assertMatch("/flights/ORD/ATL/schedule", "ORD", "ATL");
        assertMatch("/flights/SFO/SEA/schedule", "SFO", "SEA");

        // Valid IATA codes (International)
        assertMatch("/flights/LHR/CDG/schedule", "LHR", "CDG");
        assertMatch("/flights/DXB/HND/schedule", "DXB", "HND");
        assertMatch("/flights/SIN/HKG/schedule", "SIN", "HKG");

        // Case insensitive
        assertMatch("/flights/jfk/lax/schedule", "jfk", "lax");

        // Invalid codes (wrong length or not in set)
        assertNull(trie.lookup("/flights/ABCD/XYZ/schedule")); // 4 letters
        assertNull(trie.lookup("/flights/XYZ/ABC/schedule")); // Not real codes
    }

    @Test
    @DisplayName("Should validate ICAO airport codes (4-letter)")
    void testICAOAirportValidator() {
        trie.insert("/aviation/{airport}/weather",
            Map.of("airport", SegmentValidator.ICAO_AIRPORT));

        // Valid ICAO codes
        assertMatch("/aviation/KJFK/weather", "KJFK");
        assertMatch("/aviation/EGLL/weather", "EGLL");
        assertMatch("/aviation/LFPG/weather", "LFPG");
        assertMatch("/aviation/RJTT/weather", "RJTT");

        // Case insensitive
        assertMatch("/aviation/kjfk/weather", "kjfk");

        // Invalid
        assertNull(trie.lookup("/aviation/JFK/weather")); // 3 letters
        assertNull(trie.lookup("/aviation/XXXX/weather")); // Not real
    }

    // =========================================================================
    // LANGUAGE CODE TESTS
    // =========================================================================

    @Test
    @DisplayName("Should validate ISO 639-1 language codes (2-letter)")
    void testISO639_1LanguageValidator() {
        trie.insert("/content/{lang}/articles",
            Map.of("lang", SegmentValidator.ISO_639_1_LANGUAGE));

        // Valid 2-letter codes
        assertMatch("/content/en/articles", "en");
        assertMatch("/content/es/articles", "es");
        assertMatch("/content/fr/articles", "fr");
        assertMatch("/content/de/articles", "de");
        assertMatch("/content/zh/articles", "zh");
        assertMatch("/content/ja/articles", "ja");
        assertMatch("/content/ar/articles", "ar");

        // Case insensitive
        assertMatch("/content/EN/articles", "EN");

        // Invalid
        assertNull(trie.lookup("/content/eng/articles")); // 3 letters
        assertNull(trie.lookup("/content/xyz/articles")); // Not real
    }

    @Test
    @DisplayName("Should validate ISO 639-2 language codes (3-letter)")
    void testISO639_2LanguageValidator() {
        trie.insert("/i18n/{lang}/translations",
            Map.of("lang", SegmentValidator.ISO_639_2_LANGUAGE));

        // Valid 3-letter codes
        assertMatch("/i18n/eng/translations", "eng");
        assertMatch("/i18n/spa/translations", "spa");
        assertMatch("/i18n/fra/translations", "fra");
        assertMatch("/i18n/deu/translations", "deu");
        assertMatch("/i18n/zho/translations", "zho");
        assertMatch("/i18n/jpn/translations", "jpn");

        // Case insensitive
        assertMatch("/i18n/ENG/translations", "ENG");

        // Invalid
        assertNull(trie.lookup("/i18n/en/translations")); // 2 letters
        assertNull(trie.lookup("/i18n/xyz/translations")); // Not real
    }

    // =========================================================================
    // COUNTRY CODE TESTS
    // =========================================================================

    @Test
    @DisplayName("Should validate ISO 3166 country codes (2-letter)")
    void testISO3166Alpha2CountryValidator() {
        trie.insert("/api/countries/{country}/users",
            Map.of("country", SegmentValidator.ISO_3166_COUNTRY_ALPHA2));

        // Valid 2-letter codes
        assertMatch("/api/countries/US/users", "US");
        assertMatch("/api/countries/GB/users", "GB");
        assertMatch("/api/countries/FR/users", "FR");
        assertMatch("/api/countries/DE/users", "DE");
        assertMatch("/api/countries/CN/users", "CN");
        assertMatch("/api/countries/JP/users", "JP");
        assertMatch("/api/countries/BR/users", "BR");

        // Case insensitive
        assertMatch("/api/countries/us/users", "us");

        // Invalid
        assertNull(trie.lookup("/api/countries/USA/users")); // 3 letters
        assertNull(trie.lookup("/api/countries/XX/users")); // Not real
    }

    @Test
    @DisplayName("Should validate ISO 3166 country codes (3-letter)")
    void testISO3166Alpha3CountryValidator() {
        trie.insert("/api/countries/{country}/stats",
            Map.of("country", SegmentValidator.ISO_3166_COUNTRY_ALPHA3));

        // Valid 3-letter codes
        assertMatch("/api/countries/USA/stats", "USA");
        assertMatch("/api/countries/GBR/stats", "GBR");
        assertMatch("/api/countries/FRA/stats", "FRA");
        assertMatch("/api/countries/DEU/stats", "DEU");
        assertMatch("/api/countries/CHN/stats", "CHN");
        assertMatch("/api/countries/JPN/stats", "JPN");

        // Case insensitive
        assertMatch("/api/countries/usa/stats", "usa");

        // Invalid
        assertNull(trie.lookup("/api/countries/US/stats")); // 2 letters
        assertNull(trie.lookup("/api/countries/XXX/stats")); // Not real
    }

    // =========================================================================
    // CURRENCY CODE TESTS
    // =========================================================================

    @Test
    @DisplayName("Should validate ISO 4217 currency codes")
    void testISO4217CurrencyValidator() {
        trie.insert("/prices/{currency}/products",
            Map.of("currency", SegmentValidator.ISO_4217_CURRENCY));

        // Major currencies
        assertMatch("/prices/USD/products", "USD");
        assertMatch("/prices/EUR/products", "EUR");
        assertMatch("/prices/GBP/products", "GBP");
        assertMatch("/prices/JPY/products", "JPY");
        assertMatch("/prices/CNY/products", "CNY");
        assertMatch("/prices/CHF/products", "CHF");
        assertMatch("/prices/CAD/products", "CAD");
        assertMatch("/prices/AUD/products", "AUD");

        // Crypto (if supported)
        assertMatch("/prices/BTC/products", "BTC");
        assertMatch("/prices/ETH/products", "ETH");

        // Case insensitive
        assertMatch("/prices/usd/products", "usd");

        // Invalid
        assertNull(trie.lookup("/prices/XXX/products")); // Not real
        assertNull(trie.lookup("/prices/US/products")); // Country, not currency
    }

    // =========================================================================
    // HTTP STATUS CODE TESTS
    // =========================================================================

    @Test
    @DisplayName("Should validate HTTP status codes")
    void testHTTPStatusCodeValidator() {
        trie.insert("/errors/{status}/info",
            Map.of("status", SegmentValidator.HTTP_STATUS_CODE));

        // 2xx Success
        assertMatch("/errors/200/info", "200");
        assertMatch("/errors/201/info", "201");
        assertMatch("/errors/204/info", "204");

        // 3xx Redirection
        assertMatch("/errors/301/info", "301");
        assertMatch("/errors/302/info", "302");
        assertMatch("/errors/304/info", "304");

        // 4xx Client Errors
        assertMatch("/errors/400/info", "400");
        assertMatch("/errors/401/info", "401");
        assertMatch("/errors/403/info", "403");
        assertMatch("/errors/404/info", "404");
        assertMatch("/errors/429/info", "429");

        // 5xx Server Errors
        assertMatch("/errors/500/info", "500");
        assertMatch("/errors/502/info", "502");
        assertMatch("/errors/503/info", "503");

        // Invalid
        assertNull(trie.lookup("/errors/999/info")); // Not a standard code
        assertNull(trie.lookup("/errors/20/info")); // Only 2 digits
        assertNull(trie.lookup("/errors/2000/info")); // 4 digits
    }

    // =========================================================================
    // COMBINED VALIDATORS TEST
    // =========================================================================

    @Test
    @DisplayName("Should support multiple built-in validators in same path")
    void testMultipleBuiltInValidators() {
        trie.insert("/flights/{origin}/{destination}/prices/{currency}",
            Map.of(
                "origin", SegmentValidator.IATA_AIRPORT,
                "destination", SegmentValidator.IATA_AIRPORT,
                "currency", SegmentValidator.ISO_4217_CURRENCY
            ));

        // Valid combinations
        MatchResult result = trie.lookup("/flights/JFK/LAX/prices/USD");
        assertNotNull(result);
        assertEquals("JFK", result.getParams().get("origin"));
        assertEquals("LAX", result.getParams().get("destination"));
        assertEquals("USD", result.getParams().get("currency"));

        // International flight with EUR
        result = trie.lookup("/flights/LHR/CDG/prices/EUR");
        assertNotNull(result);
        assertEquals("LHR", result.getParams().get("origin"));
        assertEquals("CDG", result.getParams().get("destination"));
        assertEquals("EUR", result.getParams().get("currency"));

        // Invalid airport code
        assertNull(trie.lookup("/flights/XYZ/LAX/prices/USD"));

        // Invalid currency
        assertNull(trie.lookup("/flights/JFK/LAX/prices/XXX"));
    }

    @Test
    @DisplayName("Should support internationalized content paths")
    void testInternationalizedContentPath() {
        trie.insert("/content/{lang}/{country}/news",
            Map.of(
                "lang", SegmentValidator.ISO_639_1_LANGUAGE,
                "country", SegmentValidator.ISO_3166_COUNTRY_ALPHA2
            ));

        // Valid combinations
        assertMatch("/content/en/US/news", "en", "US");
        assertMatch("/content/es/ES/news", "es", "ES");
        assertMatch("/content/fr/FR/news", "fr", "FR");
        assertMatch("/content/de/DE/news", "de", "DE");
        assertMatch("/content/zh/CN/news", "zh", "CN");

        // Invalid language
        assertNull(trie.lookup("/content/xyz/US/news"));

        // Invalid country
        assertNull(trie.lookup("/content/en/XX/news"));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void assertMatch(String path, String... expectedValues) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);

        if (expectedValues.length > 0) {
            assertEquals(expectedValues.length, result.getParams().size(),
                "Expected " + expectedValues.length + " parameters");

            // Check that all expected values are present (order doesn't matter)
            for (String expectedValue : expectedValues) {
                assertTrue(result.getParams().containsValue(expectedValue),
                    "Expected parameter value: " + expectedValue);
            }
        }
    }
}