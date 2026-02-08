package salt.security.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import salt.security.PathResolverService;
import salt.security.llm.BedrockTemplateInferenceService;
import salt.security.llm.TemplateInferenceService.TemplateInference;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests based on the Petstore API (https://petstore3.swagger.io/)
 *
 * This test suite demonstrates how the PathTemplateTrie handles a real-world REST API
 * with multiple resource types, different ID formats, and query patterns.
 *
 * Petstore API Endpoints:
 * - Pet operations: /pet/{petId}, /pet/findByStatus, /pet/findByTags
 * - Store operations: /store/order/{orderId}, /store/inventory
 * - User operations: /user/{username}, /user/login, /user/logout
 */
class PetStoreApiTest {
    private static final Logger logger = Logger.getLogger(PetStoreApiTest.class.getName());

    private PathTemplateTrie trie;

    @BeforeEach
    void setUp() {
        trie = new PathTemplateTrie();
        logger.info("========== Petstore API Test Setup ==========");
    }

    @Test
    @DisplayName("Pet endpoints: CRUD operations with numeric IDs")
    void testPetEndpoints() {
        logger.info("\n=== Petstore: Pet Endpoints ===\n");

        // Pet CRUD operations
        trie.insert("/pet/{petId}",
            Map.of("petId", SegmentValidator.NUMERIC));
        trie.insert("/pet/{petId}/uploadImage",
            Map.of("petId", SegmentValidator.NUMERIC));

        // Special query endpoints (literal paths)
        trie.insert("/pet/findByStatus");
        trie.insert("/pet/findByTags");

        logger.info("Testing pet operations:");

        // Get pet by ID
        assertPetMatch("/pet/123", "123");
        assertPetMatch("/pet/456789", "456789");

        // Upload image for pet
        assertPetMatch("/pet/789/uploadImage", "789");

        // Query endpoints (no parameters)
        MatchResult statusResult = trie.lookup("/pet/findByStatus");
        assertNotNull(statusResult);
        assertEquals("/pet/findByStatus", statusResult.getTemplate());
        logger.info("  ✓ /pet/findByStatus → query endpoint");

        MatchResult tagsResult = trie.lookup("/pet/findByTags");
        assertNotNull(tagsResult);
        assertEquals("/pet/findByTags", tagsResult.getTemplate());
        logger.info("  ✓ /pet/findByTags → query endpoint");

        // Non-numeric should not match pet ID path
        assertNull(trie.lookup("/pet/fluffy"));
        logger.info("  ✓ /pet/fluffy → correctly rejected (not numeric)");

        logger.info("✓ Pet endpoints work correctly\n");
    }

    @Test
    @DisplayName("Store endpoints: Orders with numeric order IDs")
    void testStoreEndpoints() {
        logger.info("\n=== Petstore: Store Endpoints ===\n");

        // Store/Order operations
        trie.insert("/store/order/{orderId}",
            Map.of("orderId", SegmentValidator.NUMERIC));
        trie.insert("/store/inventory");

        logger.info("Testing store operations:");

        // Get order by ID
        assertOrderMatch("/store/order/12345", "12345");
        assertOrderMatch("/store/order/67890", "67890");
        assertOrderMatch("/store/order/1", "1");

        // Get inventory
        MatchResult inventoryResult = trie.lookup("/store/inventory");
        assertNotNull(inventoryResult);
        assertEquals("/store/inventory", inventoryResult.getTemplate());
        assertTrue(inventoryResult.getParams().isEmpty());
        logger.info("  ✓ /store/inventory → inventory endpoint");

        logger.info("✓ Store endpoints work correctly\n");
    }

    @Test
    @DisplayName("User endpoints: Operations with username strings")
    void testUserEndpoints() {
        logger.info("\n=== Petstore: User Endpoints ===\n");

        // User operations (username is a string, not numeric)
        trie.insert("/user/{username}",
            Map.of("username", SegmentValidator.ANY));

        // Special authentication endpoints
        trie.insert("/user/login");
        trie.insert("/user/logout");
        trie.insert("/user/createWithList");

        logger.info("Testing user operations:");

        // Get user by username
        assertUserMatch("/user/john", "john");
        assertUserMatch("/user/alice123", "alice123");
        assertUserMatch("/user/mary-jane", "mary-jane");
        assertUserMatch("/user/user_admin", "user_admin");

        // Login/Logout endpoints
        MatchResult loginResult = trie.lookup("/user/login");
        assertNotNull(loginResult);
        assertEquals("/user/login", loginResult.getTemplate());
        logger.info("  ✓ /user/login → auth endpoint");

        MatchResult logoutResult = trie.lookup("/user/logout");
        assertNotNull(logoutResult);
        assertEquals("/user/logout", logoutResult.getTemplate());
        logger.info("  ✓ /user/logout → auth endpoint");

        MatchResult createResult = trie.lookup("/user/createWithList");
        assertNotNull(createResult);
        assertEquals("/user/createWithList", createResult.getTemplate());
        logger.info("  ✓ /user/createWithList → batch creation endpoint");

        logger.info("✓ User endpoints work correctly\n");
    }

    @Test
    @DisplayName("Path priority: Literals vs wildcards")
    void testPathPriority() {
        logger.info("\n=== Petstore: Path Priority (Literals vs Wildcards) ===\n");

        // Add user endpoints in different order
        trie.insert("/user/{username}",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/user/login");
        trie.insert("/user/logout");

        logger.info("Testing path priority:");

        // Literal paths should take priority over wildcard
        MatchResult loginResult = trie.lookup("/user/login");
        assertEquals("/user/login", loginResult.getTemplate());
        assertTrue(loginResult.getParams().isEmpty());
        logger.info("  ✓ /user/login → matched literal (not wildcard)");

        MatchResult logoutResult = trie.lookup("/user/logout");
        assertEquals("/user/logout", logoutResult.getTemplate());
        assertTrue(logoutResult.getParams().isEmpty());
        logger.info("  ✓ /user/logout → matched literal (not wildcard)");

        // Other usernames should match wildcard
        MatchResult wildcardResult = trie.lookup("/user/john");
        assertEquals("/user/{username}", wildcardResult.getTemplate());
        assertEquals("john", wildcardResult.getParams().get("username"));
        logger.info("  ✓ /user/john → matched wildcard");

        logger.info("✓ Literal paths correctly take priority over wildcards\n");
    }

    @Test
    @DisplayName("Complete Petstore API: All endpoints together")
    void testCompletePetStoreApi() {
        logger.info("\n=== Complete Petstore API ===\n");

        // Insert all Petstore endpoints
        insertPetStoreEndpoints();

        logger.info("Testing complete API coverage:");

        // Pet operations
        assertApiMatch("/pet/123", "/pet/{petId}", "123");
        assertApiMatch("/pet/456/uploadImage", "/pet/{petId}/uploadImage", "456");
        assertLiteralMatch("/pet/findByStatus", "/pet/findByStatus");
        assertLiteralMatch("/pet/findByTags", "/pet/findByTags");

        // Store operations
        assertApiMatch("/store/order/789", "/store/order/{orderId}", "789");
        assertLiteralMatch("/store/inventory", "/store/inventory");

        // User operations
        assertApiMatch("/user/john", "/user/{username}", "john");
        assertLiteralMatch("/user/login", "/user/login");
        assertLiteralMatch("/user/logout", "/user/logout");
        assertLiteralMatch("/user/createWithList", "/user/createWithList");

        logger.info("✓ Complete Petstore API works correctly\n");
    }

    @Test
    @DisplayName("UUID support for pet IDs (alternative ID format)")
    void testPetIdWithUUID() {
        logger.info("\n=== Petstore: UUID Pet IDs ===\n");

        // Some APIs use UUIDs instead of numeric IDs
        trie.insert("/pet/{petId}",
            Map.of("petId", SegmentValidator.UUID));

        logger.info("Testing UUID pet IDs:");

        String uuid1 = "550e8400-e29b-41d4-a716-446655440000";
        String uuid2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

        assertPetMatch("/pet/" + uuid1, uuid1);
        assertPetMatch("/pet/" + uuid2, uuid2);

        // Non-UUID should not match
        assertNull(trie.lookup("/pet/123"));
        assertNull(trie.lookup("/pet/not-a-uuid"));
        logger.info("  ✓ Non-UUID values correctly rejected");

        logger.info("✓ UUID pet IDs work correctly\n");
    }

    @Test
    @DisplayName("Mixed ID types: Different validators for different resources")
    void testMixedIdTypes() {
        logger.info("\n=== Petstore: Mixed ID Types ===\n");

        // Different resources can use different ID formats
        trie.insert("/pet/{petId}",
            Map.of("petId", SegmentValidator.NUMERIC));
        trie.insert("/store/order/{orderId}",
            Map.of("orderId", SegmentValidator.UUID));
        trie.insert("/user/{username}",
            Map.of("username", SegmentValidator.ANY));

        logger.info("Testing mixed ID types:");

        // Numeric pet IDs
        assertApiMatch("/pet/123", "/pet/{petId}", "123");

        // UUID order IDs
        String orderUuid = "550e8400-e29b-41d4-a716-446655440000";
        assertApiMatch("/store/order/" + orderUuid, "/store/order/{orderId}", orderUuid);

        // String usernames
        assertApiMatch("/user/john", "/user/{username}", "john");

        // Wrong ID types should not match
        assertNull(trie.lookup("/pet/not-numeric"));
        assertNull(trie.lookup("/store/order/123")); // Not a UUID
        logger.info("  ✓ Wrong ID types correctly rejected");

        logger.info("✓ Mixed ID types work correctly\n");
    }

    @Test
    @DisplayName("API versioning: Multiple API versions")
    void testApiVersioning() {
        logger.info("\n=== Petstore: API Versioning ===\n");

        // v1 API (older, numeric IDs)
        trie.insert("/api/v1/pet/{petId}",
            Map.of("petId", SegmentValidator.NUMERIC));

        // v2 API (newer, UUID IDs)
        trie.insert("/api/v2/pet/{petId}",
            Map.of("petId", SegmentValidator.UUID));

        // v3 API (newest, different structure)
        trie.insert("/api/v3/pets/{petId}",
            Map.of("petId", SegmentValidator.UUID));

        logger.info("Testing API versioning:");

        // v1 with numeric ID
        MatchResult v1 = trie.lookup("/api/v1/pet/123");
        assertNotNull(v1);
        assertEquals("/api/v1/pet/{petId}", v1.getTemplate());
        assertEquals("123", v1.getParams().get("petId"));
        logger.info("  ✓ v1 API: numeric ID works");

        // v2 with UUID
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        MatchResult v2 = trie.lookup("/api/v2/pet/" + uuid);
        assertNotNull(v2);
        assertEquals("/api/v2/pet/{petId}", v2.getTemplate());
        assertEquals(uuid, v2.getParams().get("petId"));
        logger.info("  ✓ v2 API: UUID works");

        // v3 with different path structure
        MatchResult v3 = trie.lookup("/api/v3/pets/" + uuid);
        assertNotNull(v3);
        assertEquals("/api/v3/pets/{petId}", v3.getTemplate());
        logger.info("  ✓ v3 API: different structure works");

        // Wrong ID format for version
        assertNull(trie.lookup("/api/v1/pet/not-numeric"));
        assertNull(trie.lookup("/api/v2/pet/123")); // v2 expects UUID
        logger.info("  ✓ Wrong ID formats correctly rejected");

        logger.info("✓ API versioning works correctly\n");
    }

    @Test
    @DisplayName("Performance: Realistic Petstore load")
    void testPerformanceWithPetStorePatterns() {
        logger.info("\n=== Performance: Petstore API ===\n");

        insertPetStoreEndpoints();

        String[] testPaths = {
            "/pet/123",
            "/pet/456/uploadImage",
            "/pet/findByStatus",
            "/store/order/789",
            "/store/inventory",
            "/user/john",
            "/user/login",
            "/pet/999",
            "/store/order/111",
            "/user/alice"
        };

        // Warmup
        for (int i = 0; i < 10000; i++) {
            for (String path : testPaths) {
                trie.lookup(path);
            }
        }

        // Measure
        int iterations = 100000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (String path : testPaths) {
                MatchResult result = trie.lookup(path);
                assertNotNull(result);
            }
        }
        long end = System.nanoTime();

        long totalLookups = iterations * testPaths.length;
        double avgNanos = (end - start) / (double) totalLookups;
        double throughput = 1_000_000_000.0 / avgNanos;

        logger.info(String.format("Total lookups: %,d", totalLookups));
        logger.info(String.format("Average time: %.2f nanoseconds", avgNanos));
        logger.info(String.format("Throughput: %,.0f lookups/second", throughput));

        assertTrue(avgNanos < 2000, "Lookup should be under 2 microseconds");

        logger.info("✓ Performance meets requirements\n");
    }

    @Test
    @DisplayName("Edge cases: Similar paths with different endings")
    void testSimilarPaths() {
        logger.info("\n=== Petstore: Similar Paths ===\n");

        // Paths that are similar but distinct
        trie.insert("/user/{username}",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/user/{username}/profile",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/user/{username}/settings",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/user/{username}/posts/{postId}",
            Map.of(
                "username", SegmentValidator.ANY,
                "postId", SegmentValidator.NUMERIC
            ));

        logger.info("Testing similar paths:");

        // Each should match its own template
        MatchResult user = trie.lookup("/user/john");
        assertEquals("/user/{username}", user.getTemplate());
        logger.info("  ✓ /user/john → base user template");

        MatchResult profile = trie.lookup("/user/john/profile");
        assertEquals("/user/{username}/profile", profile.getTemplate());
        logger.info("  ✓ /user/john/profile → profile template");

        MatchResult settings = trie.lookup("/user/john/settings");
        assertEquals("/user/{username}/settings", settings.getTemplate());
        logger.info("  ✓ /user/john/settings → settings template");

        MatchResult post = trie.lookup("/user/john/posts/123");
        assertEquals("/user/{username}/posts/{postId}", post.getTemplate());
        assertEquals("john", post.getParams().get("username"));
        assertEquals("123", post.getParams().get("postId"));
        logger.info("  ✓ /user/john/posts/123 → posts template");

        logger.info("✓ Similar paths correctly distinguished\n");
    }

    @Test
    @DisplayName("FULL CYCLE: LLM inference → Trie insertion → Path matching")
    void testFullCycleWithLLM() {
        logger.info("\n=== FULL CYCLE: Petstore with LLM Integration ===\n");
        logger.info("This test demonstrates the complete workflow:");
        logger.info("1. Concrete path → LLM inference");
        logger.info("2. LLM returns template + validators");
        logger.info("3. Template inserted into trie");
        logger.info("4. New paths matched against template\n");

        BedrockTemplateInferenceService llmService = new BedrockTemplateInferenceService();

        try {
            // ============================================================
            // Scenario 1: Pet with numeric ID
            // ============================================================
            logger.info("--- Scenario 1: Pet Endpoint ---");
            String concretePetPath = "/pet/12345";
            logger.info("Input: " + concretePetPath);

            TemplateInference petInference = llmService.inferTemplate(concretePetPath);
            assertNotNull(petInference, "LLM should infer a template");
            logger.info("LLM Output: " + petInference.template());
            logger.info("Validators: " + petInference.validators().keySet());

            // Insert the inferred template
            trie.insert(petInference.template(), petInference.validators());

            // Now test that other pet IDs match
            MatchResult pet1 = trie.lookup("/pet/67890");
            assertNotNull(pet1, "Should match inferred pet template");
            assertEquals(petInference.template(), pet1.getTemplate());
            // LLM might infer parameter name as "id" or "petId" - use whichever it chose
            String paramName = petInference.validators().keySet().iterator().next();
            assertEquals("67890", pet1.getParams().get(paramName));
            logger.info("✓ /pet/67890 → matched template, " + paramName + "=67890");

            MatchResult pet2 = trie.lookup("/pet/999");
            assertNotNull(pet2);
            assertEquals("999", pet2.getParams().get(paramName));
            logger.info("✓ /pet/999 → matched template, " + paramName + "=999\n");

            // ============================================================
            // Scenario 2: Store order with ID
            // ============================================================
            logger.info("--- Scenario 2: Store Order Endpoint ---");
            String concreteOrderPath = "/store/order/54321";
            logger.info("Input: " + concreteOrderPath);

            TemplateInference orderInference = llmService.inferTemplate(concreteOrderPath);
            assertNotNull(orderInference);
            logger.info("LLM Output: " + orderInference.template());
            logger.info("Validators: " + orderInference.validators().keySet());

            trie.insert(orderInference.template(), orderInference.validators());

            MatchResult order1 = trie.lookup("/store/order/11111");
            assertNotNull(order1);
            assertEquals(orderInference.template(), order1.getTemplate());
            String orderParamName = orderInference.validators().keySet().iterator().next();
            assertEquals("11111", order1.getParams().get(orderParamName));
            logger.info("✓ /store/order/11111 → matched template, " + orderParamName + "=11111\n");

            // ============================================================
            // Scenario 3: User with username
            // ============================================================
            logger.info("--- Scenario 3: User Endpoint ---");
            String concreteUserPath = "/user/john_doe";
            logger.info("Input: " + concreteUserPath);

            TemplateInference userInference = llmService.inferTemplate(concreteUserPath);
            assertNotNull(userInference);
            logger.info("LLM Output: " + userInference.template());
            logger.info("Validators: " + userInference.validators().keySet());

            trie.insert(userInference.template(), userInference.validators());

            MatchResult user1 = trie.lookup("/user/alice");
            assertNotNull(user1);
            assertEquals(userInference.template(), user1.getTemplate());
            assertTrue(user1.getParams().containsValue("alice"));
            logger.info("✓ /user/alice → matched template, username=alice");

            MatchResult user2 = trie.lookup("/user/bob-admin");
            assertNotNull(user2);
            assertTrue(user2.getParams().containsValue("bob-admin"));
            logger.info("✓ /user/bob-admin → matched template, username=bob-admin\n");

            // ============================================================
            // Scenario 4: UUID-based pet ID
            // ============================================================
            logger.info("--- Scenario 4: Pet with UUID ---");
            String uuidPetPath = "/pet/550e8400-e29b-41d4-a716-446655440000";
            logger.info("Input: " + uuidPetPath);

            TemplateInference uuidInference = llmService.inferTemplate(uuidPetPath);
            assertNotNull(uuidInference);
            logger.info("LLM Output: " + uuidInference.template());
            logger.info("Validators: " + uuidInference.validators().keySet());

            // Clear trie and insert UUID version
            PathTemplateTrie uuidTrie = new PathTemplateTrie();
            uuidTrie.insert(uuidInference.template(), uuidInference.validators());

            String testUuid = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
            MatchResult uuidResult = uuidTrie.lookup("/pet/" + testUuid);
            assertNotNull(uuidResult, "Should match UUID pet template");
            assertTrue(uuidResult.getParams().containsValue(testUuid));
            logger.info("✓ /pet/" + testUuid.substring(0, 8) + "... → matched UUID template\n");

            // ============================================================
            // Scenario 5: Nested path with multiple parameters
            // ============================================================
            logger.info("--- Scenario 5: Nested Endpoint ---");
            String nestedPath = "/pet/123/uploadImage";
            logger.info("Input: " + nestedPath);

            TemplateInference nestedInference = llmService.inferTemplate(nestedPath);
            assertNotNull(nestedInference);
            logger.info("LLM Output: " + nestedInference.template());
            logger.info("Validators: " + nestedInference.validators().keySet());

            PathTemplateTrie nestedTrie = new PathTemplateTrie();
            nestedTrie.insert(nestedInference.template(), nestedInference.validators());

            MatchResult nested1 = nestedTrie.lookup("/pet/456/uploadImage");
            assertNotNull(nested1);
            assertEquals(nestedInference.template(), nested1.getTemplate());
            logger.info("✓ /pet/456/uploadImage → matched template\n");

            // ============================================================
            // Summary
            // ============================================================
            logger.info("=== FULL CYCLE SUMMARY ===");
            logger.info("✅ LLM successfully inferred templates from concrete paths");
            logger.info("✅ Templates inserted into trie with correct validators");
            logger.info("✅ New paths matched against inferred templates");
            logger.info("✅ Different ID types (numeric, UUID, string) handled correctly");
            logger.info("✅ Complete workflow validated end-to-end\n");

        } catch (Exception e) {
            logger.warning("LLM test requires AWS credentials and Bedrock access");
            logger.warning("Skipping full cycle test: " + e.getMessage());
            // Don't fail the test if AWS is not configured
            // This allows local development without AWS setup
        } finally {
            llmService.close();
        }
    }

    @Test
    @DisplayName("Production flow: PathResolverService with LLM fallback")
    void testProductionFlowWithLLMFallback() {
        logger.info("\n=== PRODUCTION FLOW: PathResolverService ===\n");
        logger.info("Using PathResolverService - the production API");
        logger.info("Flow: resolve() → trie lookup → LLM fallback → learn → cache hit\n");

        // Setup: Create the resolver service (internally uses LLM for fallback)
        PathResolverService resolver = createResolver();

        try {
            // ============================================================
            // Scenario 1: First request for a new endpoint pattern
            // ============================================================
            logger.info("--- First Request: /api/products/12345 ---");
            logger.info("Cache state: " + resolver.getCacheStats());

            // Call resolve() - it will:
            // 1. Try trie lookup (miss)
            // 2. Invoke LLM
            // 3. Insert template
            // 4. Return result
            MatchResult result = resolver.resolve("/api/products/12345");

            assertNotNull(result, "Resolver should return a result");
            logger.info("✓ Resolved: /api/products/12345 → " + result.getTemplate());
            logger.info("  Captured params: " + result.getParams());
            logger.info("  Cache state: " + resolver.getCacheStats() + "\n");

            // ============================================================
            // Scenario 2: Subsequent requests with different IDs
            // ============================================================
            logger.info("--- Subsequent Requests: Same pattern, different IDs ---");
            logger.info("These should be CACHE HITS (no LLM calls)");

            result = resolver.resolve("/api/products/67890");
            assertNotNull(result, "Should resolve from cache");
            assertTrue(result.getParams().containsValue("67890"));
            logger.info("✓ /api/products/67890 → " + result.getTemplate() + " (cache hit)");

            result = resolver.resolve("/api/products/999");
            assertNotNull(result, "Should resolve from cache");
            assertTrue(result.getParams().containsValue("999"));
            logger.info("✓ /api/products/999 → " + result.getTemplate() + " (cache hit)");

            result = resolver.resolve("/api/products/42");
            assertNotNull(result, "Should resolve from cache");
            assertTrue(result.getParams().containsValue("42"));
            logger.info("✓ /api/products/42 → " + result.getTemplate() + " (cache hit)\n");

            // ============================================================
            // Scenario 3: Different endpoint pattern
            // ============================================================
            logger.info("--- New Pattern: /api/users/alice/profile ---");
            logger.info("This will trigger another LLM call (cache miss)");

            result = resolver.resolve("/api/users/alice/profile");
            assertNotNull(result, "Should resolve new pattern");
            assertTrue(result.getParams().containsValue("alice"));
            logger.info("✓ Resolved: /api/users/alice/profile → " + result.getTemplate());
            logger.info("  Captured params: " + result.getParams());
            logger.info("  Cache state: " + resolver.getCacheStats() + "\n");

            // Subsequent requests with same pattern are cache hits
            result = resolver.resolve("/api/users/bob/profile");
            assertNotNull(result);
            assertTrue(result.getParams().containsValue("bob"));
            logger.info("✓ /api/users/bob/profile → cache hit");

            result = resolver.resolve("/api/users/charlie/profile");
            assertNotNull(result);
            assertTrue(result.getParams().containsValue("charlie"));
            logger.info("✓ /api/users/charlie/profile → cache hit\n");

            // ============================================================
            // Scenario 4: Preloading known patterns
            // ============================================================
            logger.info("--- Pre-loading Known Pattern: /store/order/{orderId} ---");
            resolver.preloadTemplate("/store/order/{orderId}");
            logger.info("✓ Pre-loaded template (no LLM needed)");
            logger.info("  Cache state: " + resolver.getCacheStats());

            // This should be a cache hit (no LLM call)
            result = resolver.resolve("/store/order/99999");
            assertNotNull(result);
            assertTrue(result.getParams().containsValue("99999"));
            logger.info("✓ /store/order/99999 → cache hit (pre-loaded template)\n");

            // ============================================================
            // Summary
            // ============================================================
            logger.info("=== PRODUCTION FLOW SUMMARY ===");
            logger.info("✅ PathResolverService handles all complexity");
            logger.info("✅ Cache misses trigger LLM inference automatically");
            logger.info("✅ Learned patterns cached for subsequent requests");
            logger.info("✅ Pre-loading supported for known patterns");
            logger.info("✅ Final cache state: " + resolver.getCacheStats());
            logger.info("\nProduction characteristics:");
            logger.info("- API: Single resolve() method for all cases");
            logger.info("- Fast path: Sub-microsecond cache hits");
            logger.info("- Slow path: ~2-4s LLM calls (amortized over many requests)");
            logger.info("- Learning: Trie converges toward full coverage");
            logger.info("- Result: LLM calls approach zero over time\n");

        } catch (Exception e) {
            logger.warning("LLM test requires AWS credentials and Bedrock access");
            logger.warning("Skipping production flow test: " + e.getMessage());
            // Don't fail the test if AWS is not configured
        }
    }

    /**
     * Creates a PathResolverService with default configuration.
     * Hides the implementation detail of which LLM service to use.
     */
    private PathResolverService createResolver() {
        // Implementation detail: Using Bedrock Claude for LLM inference
        BedrockTemplateInferenceService llmService = new BedrockTemplateInferenceService();
        return new PathResolverService(trie, llmService);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void insertPetStoreEndpoints() {
        // Pet endpoints
        trie.insert("/pet/{petId}",
            Map.of("petId", SegmentValidator.NUMERIC));
        trie.insert("/pet/{petId}/uploadImage",
            Map.of("petId", SegmentValidator.NUMERIC));
        trie.insert("/pet/findByStatus");
        trie.insert("/pet/findByTags");

        // Store endpoints
        trie.insert("/store/order/{orderId}",
            Map.of("orderId", SegmentValidator.NUMERIC));
        trie.insert("/store/inventory");

        // User endpoints
        trie.insert("/user/{username}",
            Map.of("username", SegmentValidator.ANY));
        trie.insert("/user/login");
        trie.insert("/user/logout");
        trie.insert("/user/createWithList");
    }

    private void assertPetMatch(String path, String expectedPetId) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        assertEquals(expectedPetId, result.getParams().get("petId"));
        logger.info(String.format("  ✓ %s → petId=%s", path, expectedPetId));
    }

    private void assertOrderMatch(String path, String expectedOrderId) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        assertEquals(expectedOrderId, result.getParams().get("orderId"));
        logger.info(String.format("  ✓ %s → orderId=%s", path, expectedOrderId));
    }

    private void assertUserMatch(String path, String expectedUsername) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        assertEquals(expectedUsername, result.getParams().get("username"));
        logger.info(String.format("  ✓ %s → username=%s", path, expectedUsername));
    }

    private void assertApiMatch(String path, String expectedTemplate, String expectedParamValue) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        assertEquals(expectedTemplate, result.getTemplate());
        assertTrue(result.getParams().containsValue(expectedParamValue));
        logger.info(String.format("  ✓ %s → %s", path, expectedTemplate));
    }

    private void assertLiteralMatch(String path, String expectedTemplate) {
        MatchResult result = trie.lookup(path);
        assertNotNull(result, "Expected path to match: " + path);
        assertEquals(expectedTemplate, result.getTemplate());
        assertTrue(result.getParams().isEmpty());
        logger.info(String.format("  ✓ %s → %s (literal)", path, expectedTemplate));
    }
}
