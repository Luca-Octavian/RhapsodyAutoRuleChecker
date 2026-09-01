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
    OWNER_STEREOTYPE_CONSTRAINT,
    FLOW_PROPERTY_CONSTRAINT,
    UNIQUE_NAME,
    CHILD_COUNT,
    MAX_DEPTH;

    /**
     * Whether this rule type requires an appliesTo specification.
     * Rules that operate on ALL elements globally (e.g. UniqueNameRule, MaxDepthRule)
     * do not require an element set.
     */
    public boolean requiresAppliesTo() {
        switch (this) {
            case UNIQUE_NAME:
            case MAX_DEPTH:
                return false;
            default:
                return true;
        }
    }

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
            case "flowpropertyconstraint":
            case "flow_property_constraint": return FLOW_PROPERTY_CONSTRAINT;
            case "uniquename":
            case "unique_name":             return UNIQUE_NAME;
            case "childcount":
            case "child_count":             return CHILD_COUNT;
            case "maxdepth":
            case "max_depth":               return MAX_DEPTH;
            default:
                throw new IllegalArgumentException("Unknown RuleType: '" + value + "'. "
                        + "Allowed: RequiredValue, RequiredStereotype, RequiredStereotypeOneOf, NamingPattern, RelationExists, OwnerStereotypeConstraint, FlowPropertyConstraint, UniqueName, ChildCount, MaxDepth");
        }
    }
}