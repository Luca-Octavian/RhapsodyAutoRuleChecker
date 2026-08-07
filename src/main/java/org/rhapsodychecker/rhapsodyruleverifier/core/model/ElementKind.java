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

    /**
     * Rhapsody "Connector" — the element returned by Rhapsody's Ctrl+F search
     * for "Connector". Per API tracing this maps exclusively to
     * {@code IRPConnector} instances whose {@code getConnectorType()} returns
     * {@code "Junction"}. These appear in statecharts and activity diagrams as
     * merge/decision nodes and in IBDs as connector junctions.
     */
    CONNECTOR,

    /**
     * Non-junction statechart / activity pseudostate (Rhapsody metaClass
     * "Connector", API type {@code IRPConnector}). Covers Condition, Diagram,
     * EnterExit, Fork, History, Join, Termination, InPin, OutPin and
     * InOutPin — all connector types except Junction.
     */
    STATE_CONNECTOR,

    /**
     * Instance-level structural link (Rhapsody metaClass "Link", API type
     * {@code IRPLink}). Joins Parts and/or Ports in an IBD. This is NOT what
     * Rhapsody's Ctrl+F calls a "Connector".
     */
    LINK,

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

    /**
     * True for the Rhapsody-visible "Connector" category (Junction pseudostates).
     */
    public boolean isConnectorKind() {
        return this == CONNECTOR;
    }

    /**
     * True for structural links between parts and ports (IRPLink).
     */
    public boolean isLinkKind() {
        return this == LINK;
    }

    public boolean isPart() {
        return this == PART;
    }
}