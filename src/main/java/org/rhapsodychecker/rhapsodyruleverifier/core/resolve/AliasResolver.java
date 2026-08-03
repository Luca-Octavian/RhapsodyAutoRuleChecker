package org.rhapsodychecker.rhapsodyruleverifier.core.resolve;

import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

/**
 * Resolves configured targets (e.g., description, tagged values, port properties)
 * to actual values on an element.
 * Implementations live in the adapter layer (e.g., Rhapsody).
 */
public interface AliasResolver {

    /**
     * Resolve a target specification to a canonical value for the given element.
     * Never throw; return an absent value via ResolvedValue.isPresent()==false if not found.
     */
    ResolvedValue resolveValue(ElementRecord element, TargetSpec target);
}