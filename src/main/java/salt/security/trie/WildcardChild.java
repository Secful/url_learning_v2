package salt.security.trie;

/**
 * Represents a wildcard child in the trie.
 * <p>
 * This class encapsulates a wildcard node along with its definition (parameter name and validator).
 * Multiple WildcardChild instances can exist at the same trie level, allowing different validators
 * for the same path position. This enables support for:
 * - Multiple API versions with different ID formats
 * - Migration scenarios where old and new formats coexist
 * - Different validation rules for the same parameter position
 * <p>
 * Example:
 * Path position: /api/users/{id}/profile
 * WildcardChild 1: validator=NUMERIC  → matches /api/users/123/profile
 * WildcardChild 2: validator=UUID     → matches /api/users/550e8400-.../profile
 */
public record WildcardChild(TrieNode node, WildcardDef def) {
    /**
     * Creates a wildcard child with the given node and definition.
     *
     * @param node the trie node representing the continuation of this wildcard path
     * @param def  the wildcard definition containing parameter name and validator
     */
    public WildcardChild {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (def == null) {
            throw new IllegalArgumentException("WildcardDef cannot be null");
        }
    }

    /**
     * Returns the trie node for this wildcard child.
     *
     * @return the trie node
     */
    @Override
    public TrieNode node() {
        return node;
    }

    /**
     * Returns the wildcard definition for this child.
     *
     * @return the wildcard definition
     */
    @Override
    public WildcardDef def() {
        return def;
    }

    @Override
    public String toString() {
        return "WildcardChild{" + def.toString() + "}";
    }

    /**
     * Checks if this wildcard child matches the given validator.
     * Used to determine if an existing wildcard child can be reused.
     *
     * @param validator the validator to check
     * @return true if this wildcard child uses the same validator
     */
    public boolean matchesValidator(SegmentValidator validator) {
        return this.def.validator().equals(validator);
    }
}
