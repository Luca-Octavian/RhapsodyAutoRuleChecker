package org.rhapsodychecker.rhapsodyruleverifier.core.config;

public enum AliasKind {
    DESCRIPTION,
    NAME,
    TAGGED_VALUE,
    PORT_TYPE,          // the classifier/type assigned to a port
    PORT_DIRECTION,     // in/out/inout/none
    PORT_MULTIPLICITY;  // multiplicity string (e.g., "1", "1..1", "0..*")

    public static AliasKind fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("AliasKind must not be null/empty");
        }
        switch (value.trim().toLowerCase()) {
            case "description":         return DESCRIPTION;
            case "name":                return NAME;
            case "taggedvalue":
            case "tagged_value":        return TAGGED_VALUE;
            case "porttype":
            case "port_type":           return PORT_TYPE;
            case "portdirection":
            case "port_direction":      return PORT_DIRECTION;
            case "portmultiplicity":
            case "port_multiplicity":   return PORT_MULTIPLICITY;
            default:
                throw new IllegalArgumentException("Unknown AliasKind: '" + value + "'. "
                        + "Allowed: description, name, taggedValue, "
                        + "portType, portDirection, portMultiplicity");
        }
    }
}