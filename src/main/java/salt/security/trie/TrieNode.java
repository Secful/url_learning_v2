package salt.security.trie;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.List;

/**
 * A node in the path template trie.
 * Each node represents a depth level in the path hierarchy.
 *
 * MULTI-VALIDATOR SUPPORT:
 * This node now supports multiple wildcard children with different validators.
 * This allows the same path position to accept different ID formats:
 * - /api/users/123/profile (numeric ID)
 * - /api/users/550e8400-.../profile (UUID)
 * Both can coexist and will be tried in order during lookup.
 */
public class TrieNode {
    /**
     * Map of literal segment values to child nodes.
     * Keys are lowercased for case-insensitive matching.
     */
    private final Map<String, TrieNode> literals;

    /**
     * List of wildcard children (multiple validators supported).
     * Each wildcard child has its own validator and continuation path.
     * During lookup, all wildcard children are tried until one matches.
     */
    private final List<WildcardChild> wildcardChildren;

    /**
     * The template string stored at this leaf node.
     * Null for non-leaf nodes.
     */
    private String template;

    /**
     * Creates a new empty trie node.
     */
    public TrieNode() {
        this.literals = new ConcurrentHashMap<>();
        this.wildcardChildren = new CopyOnWriteArrayList<>();  // Thread-safe list
        this.template = null;
    }

    /**
     * Returns the map of literal children.
     *
     * @return a map from lowercased segment strings to child nodes
     */
    public Map<String, TrieNode> getLiterals() {
        return literals;
    }

    /**
     * Returns the list of wildcard children.
     * Each wildcard child represents a different validator for the same path position.
     *
     * @return an unmodifiable list of wildcard children
     */
    public List<WildcardChild> getWildcardChildren() {
        return wildcardChildren;
    }

    /**
     * Adds a new wildcard child to this node.
     *
     * @param wildcardChild the wildcard child to add
     */
    public void addWildcardChild(WildcardChild wildcardChild) {
        if (wildcardChild == null) {
            throw new IllegalArgumentException("WildcardChild cannot be null");
        }
        this.wildcardChildren.add(wildcardChild);
    }

    /**
     * Finds an existing wildcard child that matches the given validator.
     * Returns null if no matching wildcard child exists.
     *
     * @param validator the validator to match
     * @return the matching WildcardChild, or null if not found
     */
    public WildcardChild findWildcardChild(SegmentValidator validator) {
        for (WildcardChild wc : wildcardChildren) {
            if (wc.matchesValidator(validator)) {
                return wc;
            }
        }
        return null;
    }

    /**
     * Returns the template stored at this node.
     *
     * @return the template string, or null if this is not a leaf node
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Sets the template for this node, marking it as a leaf.
     *
     * @param template the template string (e.g., "/users/{name}")
     */
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
     * Returns whether this node is a leaf (has a template).
     *
     * @return true if this node has a template, false otherwise
     */
    public boolean isLeaf() {
        return template != null;
    }
}
