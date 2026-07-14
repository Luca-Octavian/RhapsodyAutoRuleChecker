// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyEvaluationContext.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ConfigMode;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.EvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RelationExistsRule.RelationQuery;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.util.*;

/**
 * Rhapsody-specific implementation of EvaluationContext.
 * Provides alias resolution and relation counting via the Rhapsody API.
 */
public final class RhapsodyEvaluationContext implements EvaluationContext {

    private final RhapsodyAliasResolver aliasResolver;
    private final RhapsodyModelSnapshot snapshot;
    private final RuleCheckerConfig config;
    private final ElementIndex index;
    private final ElementSelector selector;

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
        IRPModelElement handle = snapshot.handleByGuid().get(element.guid());
        if (handle == null) return 0;

        Set<String> targetGuids = null;
        if (query.targetSet() != null) {
            targetGuids = selector.resolveElementSet(query.targetSet());
        }

        int count = 0;
        List<RelationInfo> relations = collectRelations(handle, element.guid());

        for (RelationInfo rel : relations) {
            // Filter by relation kind
            if (query.kind() != null && query.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationKind.ANY) {
                if (!matchesKind(rel.metaClass, query.kind())) continue;
            }

            // Filter by direction
            if (query.direction() != null && query.direction() != org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationDirection.ANY) {
                if (!matchesDirection(rel, element.guid(), query.direction())) continue;
            }

            // Filter by relation stereotypes
            if (query.relationStereotypes() != null && !query.relationStereotypes().isEmpty()) {
                if (!matchesAnyStereotype(rel.stereotypes, query.relationStereotypes())) continue;
            }

            // If we have a target set/type filter AND we have the other end GUID, check it
            // If otherEndGuid is null (external link like Jazz), the relation still counts
            // as long as the stereotype filter passed
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

    // ---- Relation collection from Rhapsody ----

    private List<RelationInfo> collectRelations(IRPModelElement handle, String elementGuid) {
        List<RelationInfo> relations = new ArrayList<>();

        // Method 1: getDependencies() — outgoing dependencies owned by this element
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

        // Method 2: search nested Dependency elements owned by this element
        // These are satisfy/refine links stored as children of the block
        try {
            IRPCollection nested = handle.getNestedElements();
            if (nested != null) {
                for (int i = 1; i <= nested.getCount(); i++) {
                    Object o = nested.getItem(i);
                    if (!(o instanceof IRPModelElement)) continue;
                    IRPModelElement nestedElt = (IRPModelElement) o;
                    String meta = safeStr(nestedElt.getMetaClass());
                    if (!"Dependency".equals(meta)) continue;

                    RelationInfo info = new RelationInfo();
                    info.metaClass = meta;
                    info.stereotypes = readStereotypes(nestedElt);
                    info.sourceGuid = elementGuid;
                    info.direction = "outgoing";

                    // Try to get the other end
                    if (nestedElt instanceof IRPDependency) {
                        try {
                            IRPModelElement dependsOn = ((IRPDependency) nestedElt).getDependsOn();
                            if (dependsOn != null) {
                                info.otherEndGuid = safeStr(dependsOn.getGUID());
                            }
                        } catch (Throwable t) {
                            info.otherEndGuid = null;
                        }
                    }

                    // If we couldn't get otherEndGuid via getDependsOn,
                    // the relation still counts (e.g., external Jazz requirement links)
                    relations.add(info);
                }
            }
        } catch (Throwable t) { /* ignore */ }

        // Method 3: getReferences() — incoming references
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
        // Try common methods to find the other end of a relation
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

    // ---- Internal data holder ----

    private static final class RelationInfo {
        String metaClass;
        Set<String> stereotypes = Collections.emptySet();
        String sourceGuid;
        String otherEndGuid;
        String direction; // "outgoing", "incoming", "any"
    }
}
