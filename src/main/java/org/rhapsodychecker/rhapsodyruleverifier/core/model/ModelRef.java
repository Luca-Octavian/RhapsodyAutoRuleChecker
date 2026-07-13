package org.rhapsodychecker.rhapsodyruleverifier.core.model;

/**
 * Tool-agnostic lightweight reference to the original model element.
 * Implementations (e.g., RhapsodyModelRef) live in adapter modules.
 */
public interface ModelRef {
    String tool();  // e.g., "Rhapsody"
    String id();    // stable identifier (e.g., GUID)
}