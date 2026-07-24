// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyEvaluationContext.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
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
    private final RuleCheckerConfig config;
    private final ElementIndex index;
    private final ElementSelector selector;

    // Fix 3: Cache relation info per element GUID to avoid repeated COM calls
    private final Map<String, List<RelationInfo>> relationCache = new HashMap<>();

    public RhapsodyEvaluationContext(RhapsodyAliasResolver aliasResolver,
                                     RhapsodyModelSnapshot snapshot,
                                     RuleCheckerConfig config,
                                     ElementIndex index,
                                     ElementSelector selector) {
        this.aliasResolver = Objects.requireNonNull(aliasResolver);
        this.snapshot = Objects.requireNonNull(snapshot);
        this.config = Objects.requireNonNull(config);
        this.index = Objects.requireNonNull(index);
        this.selector = Objects.requireNonNull(selector);
    }

    @Override
    public AliasResolver aliases() {
        return aliasResolver;
    }

    @Override
    public int countMatchingRelations(ElementRecord element, RelationQuery query) {
        // First: use pre-indexed dependencies (works from both live and cache)
        List<RelationInfo> relations = getRelationsCached(element.guid());

        // If pre-indexed returned nothing AND we have live handles, try live COM
        if (relations.isEmpty()) {
            IRPModelElement handle = snapshot.handleByGuid().get(element.guid());
            if (handle != null) {
                relations = collectRelationsLive(handle, element.guid());
                relationCache.put(element.guid(), relations);
            }
        }

        if (relations.isEmpty()) return 0;

        Set<String> targetGuids = null;
        if (query.targetSet() != null) {
            targetGuids = selector.resolveElementSet(query.targetSet());
        }

        int count = 0;

        for (RelationInfo rel : relations) {
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
        if ("mode".equalsIgnoreCase(key)) {
            return Optional.of(config.mode());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ElementRecord> findElementByGuid(String guid) {
        if (guid == null || guid.isEmpty()) return Optional.empty();
        return index.repository().get(guid);
    }

    // ---- Cached relation lookup ----

    private List<RelationInfo> getRelationsCached(String elementGuid) {
        return relationCache.computeIfAbsent(elementGuid, k -> buildRelationsFromIndex(k));
    }

    /**
     * Build relation info from the pre-indexed dependency data in the snapshot.
     * This works for both live and cached snapshots — no COM calls needed.
     */
    private List<RelationInfo> buildRelationsFromIndex(String elementGuid) {
        List<RelationInfo> relations = new ArrayList<>();

        // Outgoing: dependencies owned by this element
        List<RhapsodyModelSnapshot.DependencyInfo> preIndexed =
                snapshot.dependenciesByOwner().get(elementGuid);
        if (preIndexed != null) {
            for (RhapsodyModelSnapshot.DependencyInfo dep : preIndexed) {
                RelationInfo info = new RelationInfo();
                info.metaClass = "Dependency";
                info.stereotypes = dep.stereotypes();
                info.sourceGuid = elementGuid;
                info.otherEndGuid = dep.otherEndGuid();
                info.direction = "outgoing";
                relations.add(info);
            }
        }

        // Incoming: find dependencies where THIS element is the otherEnd
        for (Map.Entry<String, List<RhapsodyModelSnapshot.DependencyInfo>> entry
                : snapshot.dependenciesByOwner().entrySet()) {
            String ownerGuid = entry.getKey();
            if (ownerGuid.equals(elementGuid)) continue; // skip self (already handled above)
            for (RhapsodyModelSnapshot.DependencyInfo dep : entry.getValue()) {
                if (elementGuid.equals(dep.otherEndGuid())) {
                    RelationInfo info = new RelationInfo();
                    info.metaClass = "Dependency";
                    info.stereotypes = dep.stereotypes();
                    info.sourceGuid = ownerGuid;
                    info.otherEndGuid = ownerGuid;
                    info.direction = "incoming";
                    relations.add(info);
                }
            }
        }

        // Incoming: pre-indexed references (getReferences() captured during model loading)
        // This covers non-dependency relations like diagrams, parts, connectors, etc.
        List<RhapsodyModelSnapshot.ReferenceInfo> preIndexedRefs =
                snapshot.referencesByElement().get(elementGuid);
        if (preIndexedRefs != null) {
            for (RhapsodyModelSnapshot.ReferenceInfo ref : preIndexedRefs) {
                // Avoid duplicates with dependency-based incoming relations
                boolean alreadyFound = false;
                for (RelationInfo existing : relations) {
                    if (ref.guid().equals(existing.otherEndGuid)
                            && "incoming".equals(existing.direction)) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (alreadyFound) continue;

                RelationInfo info = new RelationInfo();
                info.metaClass = ref.metaClass();
                info.stereotypes = ref.stereotypes();
                info.otherEndGuid = ref.guid();
                info.sourceGuid = elementGuid;
                info.direction = "incoming";
                relations.add(info);
            }
        }

        // If live handles are available, augment with COM data
        IRPModelElement handle = snapshot.handleByGuid().get(elementGuid);
        if (handle != null) {
            List<RelationInfo> liveRelations = collectRelationsLive(handle, elementGuid);
            // Merge live relations, avoiding duplicates with pre-indexed ones
            for (RelationInfo liveRel : liveRelations) {
                boolean alreadyFound = false;
                for (RelationInfo existing : relations) {
                    if (liveRel.otherEndGuid != null && liveRel.otherEndGuid.equals(existing.otherEndGuid)
                            && liveRel.direction.equals(existing.direction)
                            && liveRel.stereotypes.equals(existing.stereotypes)) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (!alreadyFound) {
                    relations.add(liveRel);
                }
            }
        }

        return relations;
    }

    /**
     * Collect relations via live Rhapsody COM calls.
     * Only used when live handles are available (not in cache mode).
     */
    private List<RelationInfo> collectRelationsLive(IRPModelElement handle, String elementGuid) {
        List<RelationInfo> relations = new ArrayList<>();

        // Method 1: getDependencies() — outgoing
        try {
            IRPCollection deps = handle.getDependencies();
            if (deps != null) {
                for (int i = 1; i <= deps.getCount(); i++) {
                    Object o = deps.getItem(i);
                    if (o instanceof IRPDependency) {
                        IRPDependency dep = (IRPDependency) o;
                        RelationInfo info = new RelationInfo();
                        info.metaClass = safeStr(dep.getMetaClass());
                        info.stereotypes = readStereotypes(dep);
                        info.sourceGuid = elementGuid;
                        try {
                            IRPModelElement dependent = dep.getDependsOn();
                            info.otherEndGuid = dependent != null ? safeStr(dependent.getGUID()) : null;
                        } catch (Throwable t) {
                            info.otherEndGuid = null;
                        }
                        info.direction = "outgoing";
                        relations.add(info);
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }

        // Method 2: getReferences() — incoming
        try {
            IRPCollection refs = handle.getReferences();
            if (refs != null) {
                for (int i = 1; i <= refs.getCount(); i++) {
                    Object o = refs.getItem(i);
                    if (o instanceof IRPModelElement) {
                        IRPModelElement refElt = (IRPModelElement) o;
                        RelationInfo info = new RelationInfo();
                        info.metaClass = safeStr(refElt.getMetaClass());
                        info.stereotypes = readStereotypes(refElt);
                        info.otherEndGuid = safeStr(refElt.getGUID());
                        info.sourceGuid = elementGuid;
                        info.direction = "incoming";
                        relations.add(info);
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }

        return relations;
    }

    private String tryGetOtherEnd(IRPModelElement relElt, String thisGuid) {
        String[] methods = {"getDependsOn", "getOtherClass", "getDerived", "getBaseClass"};
        for (String m : methods) {
            try {
                java.lang.reflect.Method method = relElt.getClass().getMethod(m);
                Object result = method.invoke(relElt);
                if (result instanceof IRPModelElement) {
                    String guid = safeStr(((IRPModelElement) result).getGUID());
                    if (!guid.isEmpty() && !guid.equals(thisGuid)) {
                        return guid;
                    }
                }
            } catch (Throwable t) { /* try next */ }
        }
        return null;
    }

    // ---- Helpers ----

    private Set<String> readStereotypes(IRPModelElement elt) {
        Set<String> result = new LinkedHashSet<>();
        try {
            IRPCollection sts = elt.getStereotypes();
            if (sts != null) {
                for (int i = 1; i <= sts.getCount(); i++) {
                    Object o = sts.getItem(i);
                    String name = null;
                    if (o instanceof IRPStereotype) {
                        name = ((IRPStereotype) o).getName();
                    } else if (o instanceof IRPModelElement) {
                        name = ((IRPModelElement) o).getName();
                    }
                    if (name != null && !name.trim().isEmpty()) {
                        result.add(name.trim());
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }
        return result;
    }

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

    private boolean matchesDirection(RelationInfo rel, String elementGuid,
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

    private String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    private static final class RelationInfo {
        String metaClass;
        Set<String> stereotypes = Collections.emptySet();
        String sourceGuid;
        String otherEndGuid;
        String direction;
    }
}
