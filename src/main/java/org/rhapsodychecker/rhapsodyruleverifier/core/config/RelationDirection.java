package org.rhapsodychecker.rhapsodyruleverifier.core.config;

public enum RelationDirection {
    OUTGOING,
    INCOMING,
    ANY;

    public static RelationDirection fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ANY;
        }
        switch (value.trim().toLowerCase()) {
            case "outgoing":    return OUTGOING;
            case "incoming":    return INCOMING;
            case "any":         return ANY;
            default:
                throw new IllegalArgumentException("Unknown RelationDirection: '" + value + "'. "
                        + "Allowed: outgoing, incoming, any");
        }
    }
}
