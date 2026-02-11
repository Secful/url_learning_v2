package salt.security.trie;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Path Template Trie for resolving concrete HTTP paths to parameterized API templates.
 *
 * This trie provides O(k) lookup time where k is the number of path segments,
 * making it significantly faster than iterating over N compiled regex patterns.
 *
 * Thread-safe: supports concurrent reads with exclusive writes via ReadWriteLock.
 */
public class PathTemplateTrie {
    private final TrieNode root;
    private final ReadWriteLock lock;

    /**
     * Creates a new empty path template trie.
     */
    public PathTemplateTrie() {
        this.root = new TrieNode();
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Inserts a template into the trie.
     *
     * Template format: "/users/{name}/posts/{id}"
     * Segments starting with { and ending with } are treated as wildcards.
     *
     * @param template the template string to insert
     * @param validators map from parameter names to validators (optional)
     */
    public void insert(String template, Map<String, SegmentValidator> validators) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("Template cannot be null or empty");
        }

        if (validators == null) {
            validators = Collections.emptyMap();
        }

        lock.writeLock().lock();
        try {
            String[] segments = splitPath(template);
            TrieNode node = root;

            for (String segment : segments) {
                if (isWildcard(segment)) {
                    // Extract parameter name from {paramName}
                    String paramName = extractParamName(segment);
                    SegmentValidator validator = validators.getOrDefault(paramName, SegmentValidator.ANY);

                    // Check if a wildcard child with this validator already exists
                    WildcardChild existingWildcard = node.findWildcardChild(validator);

                    if (existingWildcard != null) {
                        // Reuse existing wildcard path with the same validator
                        node = existingWildcard.node();
                    } else {
                        // Create new wildcard child with this validator
                        TrieNode newWildcardNode = new TrieNode();
                        WildcardDef wildcardDef = new WildcardDef(paramName, validator);
                        WildcardChild newWildcard = new WildcardChild(newWildcardNode, wildcardDef);
                        node.addWildcardChild(newWildcard);
                        node = newWildcardNode;
                    }
                } else {
                    // Literal segment - store lowercase for case-insensitive matching
                    String key = segment.toLowerCase();
                    node = node.getLiterals().computeIfAbsent(key, k -> new TrieNode());
                }
            }

            // Mark leaf node with template
            node.setTemplate(template);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Inserts a template with no validators (all wildcards use ANY validator).
     *
     * @param template the template string to insert
     */
    public void insert(String template) {
        insert(template, Collections.emptyMap());
    }

    /**
     * Looks up a concrete path in the trie and returns the matching template.
     *
     * Prioritizes literal matches over wildcard matches at each level.
     * Backtracks if a branch leads to a dead end.
     *
     * @param path the concrete path to look up (e.g., "/users/jack/posts/123")
     * @return a MatchResult containing the template and captured parameters, or null if no match
     */
    public MatchResult lookup(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        lock.readLock().lock();
        try {
            String[] segments = splitPath(path);
            Map<String, String> params = new HashMap<>();
            return doLookup(root, segments, 0, params);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Recursive lookup implementation with backtracking.
     */
    private MatchResult doLookup(TrieNode node, String[] segments, int depth, Map<String, String> params) {
        // Base case: reached end of path
        if (depth == segments.length) {
            if (node.isLeaf()) {
                return new MatchResult(node.getTemplate(), new HashMap<>(params));
            }
            return null;
        }

        String segment = segments[depth];

        // Priority 1: Try literal match (case-insensitive)
        TrieNode literalChild = node.getLiterals().get(segment.toLowerCase());
        if (literalChild != null) {
            MatchResult result = doLookup(literalChild, segments, depth + 1, params);
            if (result != null) {
                return result;
            }
        }

        // Priority 2: Try all wildcard matches
        for (WildcardChild wildcardChild : node.getWildcardChildren()) {
            WildcardDef wildcardDef = wildcardChild.def();
            if (wildcardDef.validator().test(segment)) {
                // Capture parameter
                String paramName = wildcardDef.paramName();
                params.put(paramName, segment);

                MatchResult result = doLookup(wildcardChild.node(), segments, depth + 1, params);
                if (result != null) {
                    return result;  // Found a matching path!
                }

                // Backtrack: remove parameter if this path didn't work
                params.remove(paramName);
            }
        }

        return null;
    }

    /**
     * Removes a template from the trie.
     *
     * This operation is complex as it requires cleanup of unused nodes.
     * For simplicity, this implementation marks the leaf as non-leaf but doesn't
     * remove intermediate nodes (they will be reused if similar paths are inserted).
     *
     * @param template the template to remove
     * @return true if the template was found and removed, false otherwise
     */
    public boolean remove(String template) {
        if (template == null || template.isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            String[] segments = splitPath(template);
            TrieNode node = root;

            // Navigate to the leaf node
            for (String segment : segments) {
                if (isWildcard(segment)) {
                    // NOTE: With multiple wildcards, this uses the first one.
                    // A proper implementation would require validators to disambiguate.
                    List<WildcardChild> wildcards = node.getWildcardChildren();
                    if (wildcards.isEmpty()) {
                        return false; // Template not found
                    }
                    node = wildcards.get(0).node();
                } else {
                    String key = segment.toLowerCase();
                    node = node.getLiterals().get(key);
                    if (node == null) {
                        return false; // Template not found
                    }
                }
            }

            // Check if this is actually a leaf with the matching template
            if (node.isLeaf() && node.getTemplate().equals(template)) {
                node.setTemplate(null);
                return true;
            }

            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a list of all templates stored in the trie.
     *
     * This method is useful for persistence: dump all templates to a file/database
     * and re-insert on startup.
     *
     * @return a list of all template strings
     */
    public List<String> listTemplates() {
        lock.readLock().lock();
        try {
            List<String> templates = new ArrayList<>();
            collectTemplates(root, templates);
            return templates;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Recursively collects all templates from the trie.
     */
    private void collectTemplates(TrieNode node, List<String> templates) {
        if (node.isLeaf()) {
            templates.add(node.getTemplate());
        }

        // Collect from literal children
        for (TrieNode child : node.getLiterals().values()) {
            collectTemplates(child, templates);
        }

        // Collect from all wildcard children
        for (WildcardChild wildcardChild : node.getWildcardChildren()) {
            collectTemplates(wildcardChild.node(), templates);
        }
    }

    /**
     * Splits a path into segments, filtering out empty segments.
     *
     * Examples:
     *   "/users/jack" -> ["users", "jack"]
     *   "/api/v1/" -> ["api", "v1"]
     *   "/" -> []
     */
    private String[] splitPath(String path) {
        if (path.equals("/")) {
            return new String[0];
        }

        // Remove leading and trailing slashes, then split
        String trimmed = path.replaceAll("^/+|/+$", "");
        if (trimmed.isEmpty()) {
            return new String[0];
        }

        return trimmed.split("/");
    }

    /**
     * Checks if a segment is a wildcard (e.g., "{name}", "{id}").
     */
    private boolean isWildcard(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    /**
     * Extracts the parameter name from a wildcard segment.
     *
     * Example: "{name}" -> "name"
     */
    private String extractParamName(String segment) {
        if (!isWildcard(segment)) {
            throw new IllegalArgumentException("Segment is not a wildcard: " + segment);
        }
        return segment.substring(1, segment.length() - 1);
    }

    /**
     * Returns the root node of the trie (useful for testing/debugging).
     */
    protected TrieNode getRoot() {
        return root;
    }
}
