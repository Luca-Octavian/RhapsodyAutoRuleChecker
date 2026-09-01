// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/rule/EvaluationContext.java
package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RelationExistsRule.RelationQuery;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides services and configuration to rules during evaluation.
 */
public interface EvaluationContext {

    /**
     * Resolver for target specifications (description, tags, port properties, etc.).
     */
    AliasResolver aliases();

    /**
     * Count relations on an element that match the given query filters.
     * Implemented by the adapter layer.
     */
    int countMatchingRelations(ElementRecord element, RelationQuery query);

    /**
     * Global option for evaluation configuration.
     */
    Optional<Object> getOption(String key);
    Optional<ElementRecord> findElementByGuid(String guid);

    /**
     * Find all elements whose ownerGuid matches the given GUID.
     * Used by rules that need sibling/child lookups (e.g. UniqueNameRule, ChildCountRule).
     */
    default List<ElementRecord> findElementsByOwnerGuid(String ownerGuid) {
        return Collections.emptyList();
    }
}
