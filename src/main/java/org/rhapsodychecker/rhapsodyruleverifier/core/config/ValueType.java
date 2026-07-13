package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * Data type hint for alias values. Used for parsing/validation.
 */
public enum ValueType {
    STRING,
    BOOLEAN,
    ENUM,       // restricted to a set of allowed values
    INTEGER;

    public static ValueType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return STRING; // default
        }
        switch (value.trim().toLowerCase()) {
            case "string":  return STRING;
            case "boolean": return BOOLEAN;
            case "enum":    return ENUM;
            case "integer": return INTEGER;
            default:
                throw new IllegalArgumentException("Unknown ValueType: '" + value + "'. "
                        + "Allowed: string, boolean, enum, integer");
        }
    }
}
