package org.rhapsodychecker.rhapsodyruleverifier.core.model;

/**
 * Lightweight classification of model elements used by rules and indexes.
 * Tool-agnostic; mapping from tool-specific meta types/stereotypes happens in the adapter.
 */
public enum ElementKind {
    BLOCK,
    INTERFACE_BLOCK,
    PART,
    PORT_FULL,
    PORT_PROXY,
    PORT_FLOW,
    PORT,           // generic port if no specific port stereotype is present
    INTERFACE,      // optional: interface elements if you need them
    PACKAGE,        // optional: package elements
    REQUIREMENT,    // optional: requirement elements
    CONNECTOR,      // optional: connectors/links
    FLOW_PROPERTY,  // FlowProperty attributes inside InterfaceBlocks
    OTHER;

    public boolean isPortKind() {
        switch (this) {
            case PORT_FULL:
            case PORT_PROXY:
            case PORT_FLOW:
            case PORT:
                return true;
            default:
                return false;
        }
    }

    public boolean isBlockLike() {
        return this == BLOCK || this == INTERFACE_BLOCK;
    }

    public boolean isPart() {
        return this == PART;
    }
}