package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * Built-in generic rule types that can be driven entirely from config.
 */
public enum RuleType {
    REQUIRED_VALUE,         // property/tag must exist and optionally match allowed values
    REQUIRED_STEREOTYPE,    // element must have a specific stereotype
    REQUIRED_STEREOTYPE_ONE_OF, // element must have exactly one from a set
    NAMING_PATTERN,         // element name must match a regex
    RELATION_EXISTS;        // element must have a specific relationship (future)

    public static RuleType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("RuleType must not be null/empty");
        }
        switch (value.trim().toLowerCase()) {
            case "requiredvalue":
            case "required_value":      return REQUIRED_VALUE;
            case "requiredstereotype":
            case "required_stereotype": return REQUIRED_STEREOTYPE;
            case "requiredstereotypeoneof":
            case "required_stereotype_one_of": return REQUIRED_STEREOTYPE_ONE_OF;
            case "namingpattern":
            case "naming_pattern":      return NAMING_PATTERN;
            case "relationexists":
            case "relation_exists":     return RELATION_EXISTS;
            default:
                throw new IllegalArgumentException("Unknown RuleType: '" + value + "'. "
                        + "Allowed: RequiredValue, RequiredStereotype, RequiredStereotypeOneOf, NamingPattern, RelationExists");
        }
    }
}
