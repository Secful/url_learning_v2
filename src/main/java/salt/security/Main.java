package salt.security;

import salt.security.llm.BedrockTemplateInferenceService;
import salt.security.trie.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length > 0 && "llm".equals(args[0])) {
            demoWithLLM();
        } else {
            demoTrieOnly();
        }
    }

    /**
     * Demonstrates the trie with LLM fallback integration.
     * Run with: java Main llm
     */
    private static void demoWithLLM() {
        logger.info("Path Resolver Service Demo with LLM Fallback");

        // Create trie and LLM service
        PathTemplateTrie trie = new PathTemplateTrie();
        BedrockTemplateInferenceService llmService = new BedrockTemplateInferenceService();
        PathResolverService resolver = new PathResolverService(trie, llmService);

        // Pre-load some known templates
        logger.info("Pre-loading known templates...");
        resolver.preloadTemplate("/health");
        resolver.preloadTemplate("/api/v1/orders/summary");

        logger.info(resolver.getCacheStats());

        // Test path resolution
        logger.info("Testing path resolution...");

        // This should hit the cache
        testResolve(resolver, "/health");

        // This should hit the cache
        testResolve(resolver, "/api/v1/orders/summary");

        // This should MISS the cache and invoke LLM
        testResolve(resolver, "/users/jack/posts/123");

        // This should now HIT the cache (LLM result was cached)
        testResolve(resolver, "/users/alice/posts/456");

        // Another cache miss - invoke LLM
        testResolve(resolver, "/api/v1/products/550e8400-e29b-41d4-a716-446655440000");

        logger.info("Final cache stats: " + resolver.getCacheStats());
        logger.info("All templates:");
        for (String template : resolver.getTrie().listTemplates()) {
            logger.info("  " + template);
        }

        // Clean up
        llmService.close();
    }

    /**
     * Demonstrates the trie without LLM (basic functionality).
     * Run with: java Main
     */
    private static void demoTrieOnly() {
        logger.info("Path Template Trie Demo (Trie Only)");

        // Create trie instance
        PathTemplateTrie trie = new PathTemplateTrie();

        // Insert templates from README examples
        logger.info("Inserting templates...");
        trie.insert("/users/{name}");

        Map<String, SegmentValidator> validators = new HashMap<>();
        validators.put("id", SegmentValidator.NUMERIC);
        trie.insert("/users/{name}/posts/{id}", validators);

        trie.insert("/users/{name}/settings");

        validators.clear();
        validators.put("id", SegmentValidator.UUID);
        trie.insert("/api/v1/orders/{id}", validators);

        trie.insert("/api/v1/orders/summary");
        trie.insert("/health");

        logger.info("Inserted " + trie.listTemplates().size() + " templates");

        // Test lookups
        logger.info("Testing Lookups");

        testLookup(trie, "/users/jack");
        testLookup(trie, "/users/jack/posts/123");
        testLookup(trie, "/users/alice/settings");
        testLookup(trie, "/api/v1/orders/summary");
        testLookup(trie, "/api/v1/orders/550e8400-e29b-41d4-a716-446655440000");
        testLookup(trie, "/health");

        // Test mismatches
        logger.info("Testing Mismatches");
        testLookup(trie, "/api/v1/orders/not-a-uuid");
        testLookup(trie, "/users/bob/posts/not-numeric");
        testLookup(trie, "/unknown/path");

        // List all templates
        logger.info("All Templates:");
        for (String template : trie.listTemplates()) {
            logger.info("  " + template);
        }
    }

    private static void testLookup(PathTemplateTrie trie, String path) {
        MatchResult result = trie.lookup(path);
        if (result != null) {
            logger.info("MATCH: " + path);
            logger.info("  Template: " + result.getTemplate());
            if (!result.getParams().isEmpty()) {
                logger.info("  Params: " + result.getParams());
            }
        } else {
            logger.info("NO MATCH: " + path);
        }
    }

    private static void testResolve(PathResolverService resolver, String path) {
        logger.info("Resolving: " + path);
        MatchResult result = resolver.resolve(path);
        if (result != null) {
            logger.info("  Resolved to: " + result.getTemplate());
            if (!result.getParams().isEmpty()) {
                logger.info("  Params: " + result.getParams());
            }
        } else {
            logger.info("  Failed to resolve");
        }
    }
}