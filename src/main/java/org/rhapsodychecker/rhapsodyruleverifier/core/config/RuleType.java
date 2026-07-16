package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * Built-in generic rule types that can be driven entirely from config.
 */
public enum RuleType {
    REQUIRED_VALUE,
    REQUIRED_STEREOTYPE,
    REQUIRED_STEREOTYPE_ONE_OF,
    NAMING_PATTERN,
    RELATION_EXISTS,
    OWNER_STEREOTYPE_CONSTRAINT;

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
            case "ownerstereotypeconstraint":
            case "owner_stereotype_constraint": return OWNER_STEREOTYPE_CONSTRAINT;
            default:
                throw new IllegalArgumentException("Unknown RuleType: '" + value + "'. "
                        + "Allowed: RequiredValue, RequiredStereotype, RequiredStereotypeOneOf, NamingPattern, RelationExists, OwnerStereotypeConstraint");
        }
    }
}


