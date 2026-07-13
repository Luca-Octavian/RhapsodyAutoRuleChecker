package org.rhapsodychecker.rhapsodyruleverifier.core.resolve;

import java.util.List;
import java.util.Optional;

/**
 * Canonical representation of a resolved alias value for an element.
 * Supports scalar and multi-valued cases with basic typing.
 */
public interface ResolvedValue {

    /**
     * True if a value exists (present in model); false if absent.
     */
    boolean isPresent();

    /**
     * Scalar string view of the value, if applicable (normalized).
     */
    Optional<String> asString();

    /**
     * Boolean view of the value, if applicable.
     */
    Optional<Boolean> asBoolean();

    /**
     * Multi-valued view (e.g., list of strings for multi-select tags).
     * Empty list if not present or not multi-valued.
     */
    List<String> asList();

    /**
     * Where the value came from (e.g., "taggedValue", "stereotype", "description").
     * Useful for messages and debugging.
     */
    Optional<String> source();
}