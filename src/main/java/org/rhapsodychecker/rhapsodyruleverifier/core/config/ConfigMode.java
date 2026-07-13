package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * How the engine treats missing/absent values globally.
 */
public enum ConfigMode {
    LENIENT,    // missing values -> SKIPPED/NotApplicable
    STRICT;     // missing values -> FAIL

    public static ConfigMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LENIENT; // default
        }
        switch (value.trim().toLowerCase()) {
            case "lenient": return LENIENT;
            case "strict":  return STRICT;
            default:
                throw new IllegalArgumentException("Unknown ConfigMode: '" + value + "'. "
                        + "Allowed: lenient, strict");
        }
    }
}
