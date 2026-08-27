package org.rhapsodychecker.rhapsodyruleverifier.fix;

/**
 * Types of atomic changes that the auto-fix engine can apply to a Rhapsody model element.
 */
public enum FixActionType {

    /** Change the element's name. */
    SET_NAME,

    /** Change the element's description. */
    SET_DESCRIPTION,

    /** Set or change a tagged value (profile tag). */
    SET_TAG_VALUE,

    /** Add a stereotype to the element. */
    ADD_STEREOTYPE,

    /** Remove a stereotype from the element. */
    REMOVE_STEREOTYPE,

    /** Set the initial value (for FlowProperty elements). */
    SET_INITIAL_VALUE
}