package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * The kind of property an alias points to.
 */
public enum AliasKind {
    DESCRIPTION,        // built-in description field
    NAME,               // built-in name field
    TAGGED_VALUE,       // a tagged value from a profile
    STEREOTYPE,         // presence of a single stereotype
    STEREOTYPE_SET;     // one-of from a set of stereotypes (e.g., ASIL levels)

    /**
     * Parse from YAML string, case-insensitive.
     * @throws IllegalArgumentException if unknown
     */
    public static AliasKind fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("AliasKind must not be null/empty");
        }
        switch (value.trim().toLowerCase()) {
            case "description":     return DESCRIPTION;
            case "name":            return NAME;
            case "taggedvalue":
            case "tagged_value":    return TAGGED_VALUE;
            case "stereotype":      return STEREOTYPE;
            case "stereotypeset":
            case "stereotype_set":  return STEREOTYPE_SET;
            default:
                throw new IllegalArgumentException("Unknown AliasKind: '" + value + "'. "
                        + "Allowed: description, name, taggedValue, stereotype, stereotypeSet");
        }
    }
}