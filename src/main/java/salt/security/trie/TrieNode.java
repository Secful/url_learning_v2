package salt.security.trie;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * A node in the path template trie.
 * Each node represents a depth level in the path hierarchy.
 */
public class TrieNode {
    /**
     * Map of literal segment values to child nodes.
     * Keys are lowercased for case-insensitive matching.
     */
    private final Map<String, TrieNode> literals;

    /**
     * Single wildcard child node (matches any segment).
     */
    private TrieNode wildcardChild;

    /**
     * Metadata for the wildcard child (parameter name and validator).
     */
    private WildcardDef wildcardDef;

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
        this.wildcardChild = null;
        this.wildcardDef = null;
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
     * Returns the wildcard child node, if one exists.
     *
     * @return the wildcard child, or null if none exists
     */
    public TrieNode getWildcardChild() {
        return wildcardChild;
    }

    /**
     * Sets the wildcard child node.
     *
     * @param wildcardChild the wildcard child node
     */
    public void setWildcardChild(TrieNode wildcardChild) {
        this.wildcardChild = wildcardChild;
    }

    /**
     * Returns the wildcard definition for this node.
     *
     * @return the wildcard definition, or null if this node has no wildcard child
     */
    public WildcardDef getWildcardDef() {
        return wildcardDef;
    }

    /**
     * Sets the wildcard definition for this node.
     *
     * @param wildcardDef the wildcard definition
     */
    public void setWildcardDef(WildcardDef wildcardDef) {
        this.wildcardDef = wildcardDef;
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
