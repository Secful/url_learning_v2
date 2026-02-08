package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for regional and time-based validators:
 * - US_STATE_CODE: Regional API routing
 * - DAY_OF_WEEK: Scheduling and recurring events
 * - MONTH_NAME: Time-series and reporting APIs
 *
 * These validators are commonly used in:
 * - E-commerce (regional pricing, shipping)
 * - Scheduling systems (calendar, availability)
 * - Reporting APIs (monthly reports, weekly summaries)
 * - Analytics (time-based segmentation)
 */
class RegionalAndTimeValidatorTest {
    private static final Logger logger = Logger.getLogger(RegionalAndTimeValidatorTest.class.getName());

    private PathTemplateTrie trie;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        logger.info("========== Test Setup: Empty trie ==========");
    }

    @Test
    @DisplayName("Should validate US state codes for regional APIs")
    void testUSStateCodeValidator() {
        logger.info("\n=== Testing US State Code Validator ===\n");

        // Example: Regional pricing API
        trie.insert("/api/pricing/{state}/rates",
            Map.of("state", SegmentValidator.US_STATE_CODE));

        // Valid states
        MatchResult ca = trie.lookup("/api/pricing/CA/rates");
        assertNotNull(ca, "Should accept California (CA)");
        assertEquals("CA", ca.getParams().get("state"));
        logger.info("✓ California (CA): " + ca.getParams());

        MatchResult ny = trie.lookup("/api/pricing/NY/rates");
        assertNotNull(ny, "Should accept New York (NY)");
        assertEquals("NY", ny.getParams().get("state"));
        logger.info("✓ New York (NY): " + ny.getParams());

        MatchResult tx = trie.lookup("/api/pricing/TX/rates");
        assertNotNull(tx, "Should accept Texas (TX)");
        logger.info("✓ Texas (TX): " + tx.getParams());

        // DC and territories
        MatchResult dc = trie.lookup("/api/pricing/DC/rates");
        assertNotNull(dc, "Should accept District of Columbia (DC)");
        logger.info("✓ District of Columbia (DC): " + dc.getParams());

        MatchResult pr = trie.lookup("/api/pricing/PR/rates");
        assertNotNull(pr, "Should accept Puerto Rico (PR)");
        logger.info("✓ Puerto Rico (PR): " + pr.getParams());

        // Case insensitive
        MatchResult lowercase = trie.lookup("/api/pricing/ca/rates");
        assertNotNull(lowercase, "Should accept lowercase 'ca'");
        logger.info("✓ Case insensitive: ca → " + lowercase.getParams());

        // Invalid state code
        MatchResult invalid = trie.lookup("/api/pricing/ZZ/rates");
        assertNull(invalid, "Should reject invalid state code 'ZZ'");
        logger.info("✓ Invalid code 'ZZ' correctly rejected");

        logger.info("\n=== US State validation passed! ===\n");
    }

    @Test
    @DisplayName("Should validate day of week for scheduling APIs")
    void testDayOfWeekValidator() {
        logger.info("\n=== Testing Day of Week Validator ===\n");

        // Example: Availability scheduling API
        trie.insert("/api/availability/{day}/slots",
            Map.of("day", SegmentValidator.DAY_OF_WEEK));

        // Full names
        MatchResult monday = trie.lookup("/api/availability/monday/slots");
        assertNotNull(monday, "Should accept 'monday'");
        assertEquals("monday", monday.getParams().get("day"));
        logger.info("✓ Monday (full): " + monday.getParams());

        MatchResult friday = trie.lookup("/api/availability/friday/slots");
        assertNotNull(friday, "Should accept 'friday'");
        logger.info("✓ Friday (full): " + friday.getParams());

        // 3-letter abbreviations
        MatchResult mon = trie.lookup("/api/availability/mon/slots");
        assertNotNull(mon, "Should accept 'mon'");
        assertEquals("mon", mon.getParams().get("day"));
        logger.info("✓ Monday (abbreviated): " + mon.getParams());

        MatchResult fri = trie.lookup("/api/availability/fri/slots");
        assertNotNull(fri, "Should accept 'fri'");
        logger.info("✓ Friday (abbreviated): " + fri.getParams());

        // 2-letter abbreviations
        MatchResult mo = trie.lookup("/api/availability/mo/slots");
        assertNotNull(mo, "Should accept 'mo'");
        logger.info("✓ Monday (2-letter): " + mo.getParams());

        // Case insensitive
        MatchResult uppercase = trie.lookup("/api/availability/MONDAY/slots");
        assertNotNull(uppercase, "Should accept 'MONDAY' (case insensitive)");
        logger.info("✓ Case insensitive: MONDAY → " + uppercase.getParams());

        // Invalid day
        MatchResult invalid = trie.lookup("/api/availability/notaday/slots");
        assertNull(invalid, "Should reject invalid day 'notaday'");
        logger.info("✓ Invalid day 'notaday' correctly rejected");

        logger.info("\n=== Day of week validation passed! ===\n");
    }

    @Test
    @DisplayName("Should validate month names for time-series APIs")
    void testMonthNameValidator() {
        logger.info("\n=== Testing Month Name Validator ===\n");

        // Example: Monthly report API
        trie.insert("/api/reports/{year}/{month}/summary",
            Map.of(
                "year", SegmentValidator.NUMERIC,
                "month", SegmentValidator.MONTH_NAME
            ));

        // Full names
        MatchResult january = trie.lookup("/api/reports/2024/january/summary");
        assertNotNull(january, "Should accept 'january'");
        assertEquals("january", january.getParams().get("month"));
        assertEquals("2024", january.getParams().get("year"));
        logger.info("✓ January (full): " + january.getParams());

        MatchResult december = trie.lookup("/api/reports/2024/december/summary");
        assertNotNull(december, "Should accept 'december'");
        logger.info("✓ December (full): " + december.getParams());

        // 3-letter abbreviations
        MatchResult jan = trie.lookup("/api/reports/2024/jan/summary");
        assertNotNull(jan, "Should accept 'jan'");
        assertEquals("jan", jan.getParams().get("month"));
        logger.info("✓ January (abbreviated): " + jan.getParams());

        MatchResult dec = trie.lookup("/api/reports/2024/dec/summary");
        assertNotNull(dec, "Should accept 'dec'");
        logger.info("✓ December (abbreviated): " + dec.getParams());

        // Case insensitive
        MatchResult uppercase = trie.lookup("/api/reports/2024/MARCH/summary");
        assertNotNull(uppercase, "Should accept 'MARCH' (case insensitive)");
        logger.info("✓ Case insensitive: MARCH → " + uppercase.getParams());

        // Invalid month
        MatchResult invalid = trie.lookup("/api/reports/2024/notamonth/summary");
        assertNull(invalid, "Should reject invalid month 'notamonth'");
        logger.info("✓ Invalid month 'notamonth' correctly rejected");

        logger.info("\n=== Month name validation passed! ===\n");
    }

    @Test
    @DisplayName("Should handle complex regional and time-based routing")
    void testComplexRegionalTimeRouting() {
        logger.info("\n=== Testing Complex Regional + Time Routing ===\n");

        // Example: Regional delivery schedule API
        trie.insert("/api/delivery/{state}/{day}/windows",
            Map.of(
                "state", SegmentValidator.US_STATE_CODE,
                "day", SegmentValidator.DAY_OF_WEEK
            ));

        // Valid combination
        MatchResult caMonday = trie.lookup("/api/delivery/CA/monday/windows");
        assertNotNull(caMonday, "Should accept CA + monday");
        assertEquals("CA", caMonday.getParams().get("state"));
        assertEquals("monday", caMonday.getParams().get("day"));
        logger.info("✓ CA + Monday: " + caMonday.getParams());

        MatchResult nyFri = trie.lookup("/api/delivery/NY/fri/windows");
        assertNotNull(nyFri, "Should accept NY + fri");
        assertEquals("NY", nyFri.getParams().get("state"));
        assertEquals("fri", nyFri.getParams().get("day"));
        logger.info("✓ NY + Friday: " + nyFri.getParams());

        // Invalid state, valid day
        MatchResult invalidState = trie.lookup("/api/delivery/ZZ/monday/windows");
        assertNull(invalidState, "Should reject invalid state even with valid day");
        logger.info("✓ Invalid state rejected");

        // Valid state, invalid day
        MatchResult invalidDay = trie.lookup("/api/delivery/CA/notaday/windows");
        assertNull(invalidDay, "Should reject invalid day even with valid state");
        logger.info("✓ Invalid day rejected");

        logger.info("\n=== Complex routing validation passed! ===\n");
    }

    @Test
    @DisplayName("Should handle analytics API with state, month, and day")
    void testAnalyticsAPIWithMultipleValidators() {
        logger.info("\n=== Testing Analytics API (State + Month + Day) ===\n");

        // Example: Analytics breakdown by region, month, and day of week
        trie.insert("/api/analytics/{state}/{month}/{day}/metrics",
            Map.of(
                "state", SegmentValidator.US_STATE_CODE,
                "month", SegmentValidator.MONTH_NAME,
                "day", SegmentValidator.DAY_OF_WEEK
            ));

        // Valid combination
        MatchResult result = trie.lookup("/api/analytics/CA/jan/monday/metrics");
        assertNotNull(result, "Should accept CA + jan + monday");
        assertEquals("CA", result.getParams().get("state"));
        assertEquals("jan", result.getParams().get("month"));
        assertEquals("monday", result.getParams().get("day"));
        logger.info("✓ Full match: " + result.getParams());

        // Another valid combination
        MatchResult result2 = trie.lookup("/api/analytics/NY/december/fri/metrics");
        assertNotNull(result2, "Should accept NY + december + fri");
        logger.info("✓ Mixed formats: " + result2.getParams());

        logger.info("\n=== Analytics API validation passed! ===\n");
    }
}
