package salt.security.trie;

/**
 * Metadata for wildcard nodes in the trie.
 * Contains the parameter name and an optional validator for the segment value.
 */
public record WildcardDef(String paramName, SegmentValidator validator) {
    /**
     * Creates a wildcard definition with the given parameter name and validator.
     *
     * @param paramName the name of the parameter (e.g., "id", "name")
     * @param validator the validator to apply to segment values (e.g., NUMERIC, UUID)
     */
    public WildcardDef {
        if (paramName == null || paramName.isEmpty()) {
            throw new IllegalArgumentException("Parameter name cannot be null or empty");
        }
        if (validator == null) {
            throw new IllegalArgumentException("Validator cannot be null");
        }
    }

    /**
     * Creates a wildcard definition with the given parameter name and the ANY validator.
     *
     * @param paramName the name of the parameter
     */
    public WildcardDef(String paramName) {
        this(paramName, SegmentValidator.ANY);
    }

    @Override
    public String toString() {
        return "{" + paramName + "}";
    }
}