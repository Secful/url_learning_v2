package salt.security.llm;

/**
 * Creates a TemplateInferenceService from config.
 *
 * Required env var:
 *   INFERENCE_SERVICE_CLASS — fully qualified class name of the implementation, e.g.:
 *     salt.security.llm.ApiGatewayTemplateInferenceService
 *     salt.security.llm.BedrockTemplateInferenceService
 *
 * Each implementation reads its own config from env vars via its no-arg constructor.
 */
public class TemplateInferenceServiceFactory {

    public static final String CLASS_ENV_VAR = "INFERENCE_SERVICE_CLASS";

    public static TemplateInferenceService create() {
        String fqcn = System.getenv(CLASS_ENV_VAR);
        if (fqcn == null || fqcn.isBlank()) {
            throw new IllegalStateException(
                CLASS_ENV_VAR + " env var not set. " +
                "Example: salt.security.llm.ApiGatewayTemplateInferenceService"
            );
        }
        try {
            Class<?> clazz = Class.forName(fqcn);
            return (TemplateInferenceService) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + fqcn, e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(fqcn + " does not implement TemplateInferenceService", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + fqcn, e);
        }
    }
}
