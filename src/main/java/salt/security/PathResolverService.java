package salt.security;

import salt.security.llm.TemplateInferenceService;
import salt.security.trie.MatchResult;
import salt.security.trie.PathTemplateTrie;

import java.util.logging.Logger;

/**
 * High-level service for resolving concrete HTTP paths to parameterized templates.
 *
 * This service combines the trie cache with LLM inference:
 * 1. On lookup, first try the trie (fast, O(k) where k = number of segments)
 * 2. On cache miss, invoke the LLM to infer the template
 * 3. Insert the inferred template into the trie for future lookups
 * 4. Over time, the trie converges toward full coverage, driving LLM calls to near-zero
 *
 * Thread-safe: the underlying trie supports concurrent reads with exclusive writes.
 */
public class PathResolverService {
    private static final Logger logger = Logger.getLogger(PathResolverService.class.getName());

    private final PathTemplateTrie trie;
    private final TemplateInferenceService llmService;

    /**
     * Creates a path resolver with the given trie and LLM service.
     *
     * @param trie the path template trie cache
     * @param llmService the LLM service for inferring templates on cache miss
     */
    public PathResolverService(PathTemplateTrie trie, TemplateInferenceService llmService) {
        if (trie == null) {
            throw new IllegalArgumentException("Trie cannot be null");
        }
        if (llmService == null) {
            throw new IllegalArgumentException("LLM service cannot be null");
        }
        this.trie = trie;
        this.llmService = llmService;
    }

    /**
     * Resolves a concrete path to its parameterized template.
     *
     * Flow:
     * 1. Lookup in trie (cache hit -> return immediately)
     * 2. Cache miss -> invoke LLM to infer template
     * 3. Insert inferred template into trie
     * 4. Return the result
     *
     * @param path the concrete HTTP path (e.g., "/users/jack/posts/123")
     * @return a MatchResult with template and parameters, or null if resolution fails
     */
    public MatchResult resolve(String path) {
        if (path == null || path.isEmpty()) {
            logger.warning("Cannot resolve null or empty path");
            return null;
        }

        // Step 1: Try trie lookup (cache hit)
        MatchResult cached = trie.lookup(path);
        if (cached != null) {
            logger.fine("Cache HIT for path: " + path + " -> " + cached.getTemplate());
            return cached;
        }

        // Step 2: Cache miss - invoke LLM
        logger.info("Cache MISS for path: " + path + " - invoking LLM");
        TemplateInferenceService.TemplateInference inference = llmService.inferTemplate(path);

        if (inference == null) {
            logger.warning("LLM failed to infer template for path: " + path);
            return null;
        }

        // Step 3: Insert inferred template into trie
        String template = inference.template();
        logger.info("LLM inferred template: " + template + " for path: " + path);

        try {
            trie.insert(template, inference.validators());
            logger.info("Inserted template into trie: " + template);
        } catch (Exception e) {
            logger.warning("Failed to insert template into trie: " + template + " - " + e.getMessage());
            // Continue anyway - we can still return the result
        }

        // Step 4: Lookup again to get the MatchResult with captured parameters
        MatchResult result = trie.lookup(path);
        if (result != null) {
            logger.info("Successfully resolved path: " + path + " -> " + result.getTemplate());
            return result;
        } else {
            logger.warning("Failed to resolve path after LLM inference: " + path);
            return null;
        }
    }

    /**
     * Pre-loads a known template into the trie.
     * Useful for bootstrapping the cache with known API patterns.
     *
     * @param template the template to pre-load (e.g., "/users/{id}")
     */
    public void preloadTemplate(String template) {
        trie.insert(template);
        logger.info("Pre-loaded template: " + template);
    }

    /**
     * Returns the underlying trie for advanced operations.
     *
     * @return the path template trie
     */
    public PathTemplateTrie getTrie() {
        return trie;
    }

    /**
     * Returns statistics about the trie cache.
     *
     * @return a string with cache statistics
     */
    public String getCacheStats() {
        int templateCount = trie.listTemplates().size();
        return "Trie contains " + templateCount + " templates";
    }
}
