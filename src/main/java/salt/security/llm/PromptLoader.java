package salt.security.llm;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class PromptLoader {

    private static final String RESOURCE = "/system_prompt.md";
    private static final String PROMPT;

    static {
        try (InputStream is = PromptLoader.class.getResourceAsStream(RESOURCE)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + RESOURCE);
            PROMPT = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private PromptLoader() {}

    public static String get() {
        return PROMPT;
    }
}
