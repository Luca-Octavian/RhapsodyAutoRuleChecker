package org.rhapsodychecker.rhapsodyruleverifier.core.resolve;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.Optional;

/**
 * Resolves configured aliases (e.g., SAFETY_ASIL, ELEMENT_DESCRIPTION) to actual values on an element.
 * Implementations live in the adapter layer (e.g., Rhapsody).
 */
public interface AliasResolver {

    /**
     * Resolve a configured alias to a canonical value for the given element.
     * Never throw; return an absent value via ResolvedValue.isPresent()==false if not found.
     */
    ResolvedValue resolveValue(ElementRecord element, String aliasId);

    /**
     * Check presence of a stereotype by a configured alias or raw name, depending on your config strategy.
     * This is a convenience; rules can also use resolveValue(...) if stereotypes are modeled as aliases.
     */
    default boolean hasStereotype(ElementRecord element, String stereotypeAliasOrName) {
        // Default implementation: try to resolve as a boolean alias; adapters can override for efficiency.
        ResolvedValue rv = resolveValue(element, stereotypeAliasOrName);
        return rv.isPresent() && rv.asBoolean().orElse(false);
    }

    /**
     * Optional direct access to the element's raw stereotype set, normalized (adapter may override).
     */
    default Optional<java.util.Set<String>> getStereotypes(ElementRecord element) {
        return Optional.empty();
    }
}