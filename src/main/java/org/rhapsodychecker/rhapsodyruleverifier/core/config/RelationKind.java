package org.rhapsodychecker.rhapsodyruleverifier.core.config;

public enum RelationKind {
    DEPENDENCY,
    ASSOCIATION,
    GENERALIZATION,
    LINK,
    FLOW,
    ANY;

    public static RelationKind fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ANY;
        }
        switch (value.trim().toLowerCase()) {
            case "dependency":      return DEPENDENCY;
            case "association":     return ASSOCIATION;
            case "generalization":  return GENERALIZATION;
            case "link":            return LINK;
            case "flow":            return FLOW;
            case "any":             return ANY;
            default:
                throw new IllegalArgumentException("Unknown RelationKind: '" + value + "'. "
                        + "Allowed: dependency, association, generalization, link, flow, any");
        }
    }
}
