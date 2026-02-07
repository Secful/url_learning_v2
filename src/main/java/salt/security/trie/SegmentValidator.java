package salt.security.trie;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Validator for wildcard segments in path templates.
 * Provides built-in validators for common use cases.
 */
public interface SegmentValidator extends Predicate<String> {

    /**
     * Matches any non-empty segment.
     */
    SegmentValidator ANY = segment -> segment != null && !segment.isEmpty();

    /**
     * Matches segments containing only digits.
     */
    SegmentValidator NUMERIC = segment ->
        segment != null && !segment.isEmpty() && segment.matches("\\d+");

    /**
     * Matches segments in standard UUID format.
     */
    SegmentValidator UUID = segment -> {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        Pattern uuidPattern = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        );
        return uuidPattern.matcher(segment).matches();
    };
}