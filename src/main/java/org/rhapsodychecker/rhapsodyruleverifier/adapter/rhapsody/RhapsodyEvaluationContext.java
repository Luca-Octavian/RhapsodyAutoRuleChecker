// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyEvaluationContext.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.EvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RelationExistsRule.RelationQuery;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.util.*;

public final class RhapsodyEvaluationContext implements EvaluationContext {

    private final RhapsodyAliasResolver aliasResolver;
    private final RhapsodyModelSnapshot snapshot;
    private final ElementIndex index;
    private final ElementSelector selector;

    // Cache relation info per element GUID to avoid repeated lookups
    private final Map<String, List<ResolvedRelation>> relationCache = new HashMap<>();

    public RhapsodyEvaluationContext(RhapsodyAliasResolver aliasResolver,
                                     RhapsodyModelSnapshot snapshot,
                                     ElementIndex index,
                                     ElementSelector selector) {
        this.aliasResolver = Objects.requireNonNull(aliasResolver);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.index = Objects.requireNonNull(index);
        this.selector = Objects.requireNonNull(selector);
    }

    @Override
    public AliasResolver aliases() {
        return aliasResolver;
    }

    @Override
    public int countMatchingRelations(ElementRecord element, RelationQuery query) {
        List<ResolvedRelation> relations = getRelationsCached(element.guid());

        if (relations.isEmpty()) return 0;

        Set<String> targetGuids = null;
        if (query.targetSet() != null) {
            targetGuids = selector.resolveElementSet(query.targetSet());
        }

        int count = 0;

        for (ResolvedRelation rel : relations) {
            if (query.kind() != null && query.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationKind.ANY) {
                if (!matchesKind(rel.metaClass, query.kind())) continue;
            }

            if (query.direction() != null && query.direction() != org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationDirection.ANY) {
                if (!matchesDirection(rel, element.guid(), query.direction())) continue;
            }

            if (query.relationStereotypes() != null && !query.relationStereotypes().isEmpty()) {
                if (!matchesAnyStereotype(rel.stereotypes, query.relationStereotypes())) continue;
            }

            if (rel.otherEndGuid != null && !rel.otherEndGuid.isEmpty()) {
                if (targetGuids != null && !targetGuids.contains(rel.otherEndGuid)) continue;

                if (query.targetTypes() != null && !query.targetTypes().isEmpty()) {
                    Optional<ElementRecord> otherRec = index.repository().get(rel.otherEndGuid);
                    if (!otherRec.isPresent()) continue;
                    if (!containsIgnoreCase(query.targetTypes(), otherRec.get().metaClass())) continue;
                }

                if (query.targetStereotypes() != null && !query.targetStereotypes().isEmpty()) {
                    Optional<ElementRecord> otherRec = index.repository().get(rel.otherEndGuid);
                    if (!otherRec.isPresent()) continue;
                    if (!matchesAnyStereotype(otherRec.get().stereotypes(), query.targetStereotypes())) continue;
                }
            }

            count++;
        }

        return count;
    }

    @Override
    public Optional<Object> getOption(String key) {
        return Optional.empty();
    }

    @Override
    public Optional<ElementRecord> findElementByGuid(String guid) {
        if (guid == null || guid.isEmpty()) return Optional.empty();
        return index.repository().get(guid);
    }

    // ---- Cached relation lookup ----

    private List<ResolvedRelation> getRelationsCached(String elementGuid) {
        return relationCache.computeIfAbsent(elementGuid, k -> buildRelationsFromIndex(k));
    }

    /**
     * Build relation info from the pre-indexed relation data in the snapshot.
     * Uses snapshot's RelationInfo directly — no duplicate data class needed.
     * This works for both live and cached snapshots — no COM calls needed.
     */
    private List<ResolvedRelation> buildRelationsFromIndex(String elementGuid) {
        List<ResolvedRelation> relations = new ArrayList<>();

        // Outgoing: relations owned by this element
        List<RhapsodyModelSnapshot.RelationInfo> outgoing =
                snapshot.relationsByOwner().get(elementGuid);
        if (outgoing != null) {
            for (RhapsodyModelSnapshot.RelationInfo rel : outgoing) {
                relations.add(new ResolvedRelation(
                        rel.metaClass(), rel.stereotypes(),
                        elementGuid, rel.otherEndGuid(), "outgoing"));
            }
        }

        // Incoming: use reverse index for O(1) lookup instead of full scan
        List<RhapsodyModelSnapshot.RelationInfo> incoming =
                snapshot.relationsByTarget().get(elementGuid);
        if (incoming != null) {
            for (RhapsodyModelSnapshot.RelationInfo rel : incoming) {
                // rel.otherEndGuid() is the ownerGuid of the source element
                String sourceGuid = rel.otherEndGuid();
                if (sourceGuid != null && !sourceGuid.equals(elementGuid)) {
                    relations.add(new ResolvedRelation(
                            rel.metaClass(), rel.stereotypes(),
                            sourceGuid, sourceGuid, "incoming"));
                }
            }
        }

        // Incoming: pre-indexed references (getReferences() captured during model loading)
        // This covers non-dependency relations like diagrams, parts, connectors, etc.
        List<RhapsodyModelSnapshot.ReferenceInfo> preIndexedRefs =
                snapshot.referencesByElement().get(elementGuid);
        if (preIndexedRefs != null) {
            for (RhapsodyModelSnapshot.ReferenceInfo ref : preIndexedRefs) {
                // Avoid duplicates with relation-based incoming entries
                boolean alreadyFound = false;
                for (ResolvedRelation existing : relations) {
                    if (ref.guid().equals(existing.otherEndGuid)
                            && "incoming".equals(existing.direction)) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (alreadyFound) continue;

                relations.add(new ResolvedRelation(
                        ref.metaClass(), ref.stereotypes(),
                        elementGuid, ref.guid(), "incoming"));
            }
        }

        return relations;
    }

    // ---- Helpers ----

    private boolean matchesKind(String metaClass, org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationKind kind) {
        if (metaClass == null) return false;
        String mc = metaClass.toLowerCase();
        switch (kind) {
            case DEPENDENCY:      return mc.contains("dependency");
            case ASSOCIATION:     return mc.contains("association");
            case GENERALIZATION:  return mc.contains("generalization");
            case LINK:            return mc.contains("link");
            case FLOW:            return mc.contains("flow");
            case ANY:             return true;
            default:              return false;
        }
    }

    private boolean matchesDirection(ResolvedRelation rel, String elementGuid,
                                     org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationDirection dir) {
        switch (dir) {
            case OUTGOING:  return "outgoing".equals(rel.direction);
            case INCOMING:  return "incoming".equals(rel.direction);
            case ANY:       return true;
            default:        return true;
        }
    }

    private boolean matchesAnyStereotype(Set<String> actual, List<String> required) {
        for (String req : required) {
            for (String act : actual) {
                if (act.equalsIgnoreCase(req)) return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        for (String item : list) {
            if (item.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    /**
     * Flattened relation record used only within this evaluation context.
     * Combines relation metadata with resolved direction for query matching.
     */
    private static final class ResolvedRelation {
        final String metaClass;
        final Set<String> stereotypes;
        final String otherEndGuid;
        final String direction;

        ResolvedRelation(String metaClass, Set<String> stereotypes,
                         @SuppressWarnings("unused") String sourceGuid, String otherEndGuid, String direction) {
            this.metaClass = metaClass;
            this.stereotypes = stereotypes != null ? stereotypes : Collections.<String>emptySet();
            this.otherEndGuid = otherEndGuid;
            this.direction = direction;
        }
    }
}