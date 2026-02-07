package salt.security.llm;

import salt.security.trie.SegmentValidator;
import java.util.Map;

/**
 * Service for inferring parameterized templates from concrete paths using an LLM.
 * Used as a fallback when the trie cache misses.
 */
public interface TemplateInferenceService {

    /**
     * Infers a parameterized template from a concrete path.
     *
     * Example:
     *   Input: "/users/jack/posts/123"
     *   Output: TemplateInference{template="/users/{name}/posts/{id}", validators={id: NUMERIC}}
     *
     * @param path the concrete path to analyze
     * @return the inferred template and validators, or null if inference fails
     */
    TemplateInference inferTemplate(String path);

    /**
         * Result of template inference containing the template string and validators.
         */
        record TemplateInference(String template, Map<String, SegmentValidator> validators) {

        @Override
            public String toString() {
                return "TemplateInference{template='" + template + "', validators=" + validators + "}";
            }
        }
}
