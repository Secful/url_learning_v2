package salt.security.trie;

import java.util.Collections;
import java.util.Map;

/**
 * Result of a successful path lookup in the trie.
 * Contains the matched template string and any captured path parameters.
 */
public class MatchResult {
    private final String template;
    private final Map<String, String> params;

    /**
     * Creates a match result with the given template and captured parameters.
     *
     * @param template the matched template (e.g., "/users/{name}/posts/{id}")
     * @param params the captured parameter values (e.g., {"name": "jack", "id": "123"})
     */
    public MatchResult(String template, Map<String, String> params) {
        if (template == null) {
            throw new IllegalArgumentException("Template cannot be null");
        }
        this.template = template;
        this.params = params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
    }

    /**
     * Returns the matched template string.
     *
     * @return the template (e.g., "/users/{name}/posts/{id}")
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Returns the captured path parameters.
     *
     * @return an unmodifiable map of parameter names to values
     */
    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "MatchResult{template='" + template + "', params=" + params + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchResult that = (MatchResult) o;
        return template.equals(that.template) && params.equals(that.params);
    }

    @Override
    public int hashCode() {
        return template.hashCode() * 31 + params.hashCode();
    }
}