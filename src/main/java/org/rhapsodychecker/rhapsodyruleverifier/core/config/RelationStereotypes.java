package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * Known SysML/UML relationship stereotypes.
 * Not an enum — keep as strings since profiles can define custom ones.
 * This class provides constants for common ones and validation.
 */
public final class RelationStereotypes {
    public static final String SATISFY = "satisfy";
    public static final String REFINE = "refine";
    public static final String DERIVE_REQT = "deriveReqt";
    public static final String TRACE = "trace";
    public static final String VERIFY = "verify";
    public static final String ALLOCATE = "allocate";
    public static final String COPY = "copy";

    private RelationStereotypes() {}
}
