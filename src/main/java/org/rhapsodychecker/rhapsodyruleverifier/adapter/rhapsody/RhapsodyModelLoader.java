package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import java.util.*;

/**
 * Loads a Rhapsody project into a neutral representation (ElementRecord).
 * Single responsibility: interact with Rhapsody API and build a stable snapshot.
 */
public final class RhapsodyModelLoader {

    private final ProgressReporter reporter;

    public RhapsodyModelLoader() {
        this(ProgressReporter.NOOP);
    }

    public RhapsodyModelLoader(ProgressReporter reporter) {
        this.reporter = reporter != null ? reporter : ProgressReporter.NOOP;
    }

    /**
     * Traverse the project and build a snapshot containing:
     * - a list of ElementRecord (tool-agnostic)
     * - a GUID -> IRPModelElement map (adapter use only)
     */
    public RhapsodyModelSnapshot loadModel(IRPProject project) {
        Objects.requireNonNull(project, "project must not be null");

        List<ElementRecord> records = new ArrayList<>();
        Map<String, IRPModelElement> handleByGuid = new HashMap<>(4096);

        // ── Step 1: collect all elements ─────────────────────────────────────
        reporter.onStepStarted(LoadingStep.LOADING_ELEMENTS);

        IRPCollection all = safeGetNestedElementsRecursive(project);
        int count = all != null ? all.getCount() : 0;

        for (int i = 1; i <= count; i++) {
            IRPModelElement elt = safeGetItem(all, i);
            if (elt == null) continue;

            String guid = safeStr(elt.getGUID());
            String name = safeStr(elt.getName());
            if (guid.isEmpty() || name.isEmpty()) continue;

            String metaClass  = safeStr(elt.getMetaClass());
            String ownerPath  = computeOwnerPath(elt);
            String ownerGuid  = null;
            try {
                IRPModelElement owner = elt.getOwner();
                if (owner != null && !(owner instanceof IRPProject)) {
                    ownerGuid = safeStr(owner.getGUID());
                }
            } catch (Throwable t) { /* ignore */ }

            Set<String> stereotypes = readStereotypeNames(elt);
            String description      = safeGetDescription(elt);

            String typeGuid = null;
            String typeName = null;
            if (elt instanceof IRPAttribute) {
                IRPClassifier t = safeAttributeType((IRPAttribute) elt);
                if (t != null) { typeGuid = safeStr(t.getGUID()); typeName = safeStr(t.getName()); }
            } else if (elt instanceof IRPPort) {
                IRPClassifier t = safePortType((IRPPort) elt);
                if (t != null) { typeGuid = safeStr(t.getGUID()); typeName = safeStr(t.getName()); }
            }

            ElementKind kind = classify(metaClass, stereotypes);

            records.add(ElementRecord.builder()
                    .guid(guid).name(name).metaClass(metaClass).kind(kind)
                    .ownerGuid(ownerGuid).ownerPath(ownerPath)
                    .stereotypes(stereotypes)
                    .typeGuid(typeGuid).typeName(typeName)
                    .description(description)
                    .build());
            handleByGuid.put(guid, elt);

            // Report progress every 50 elements to avoid flooding EDT
            if (i % 50 == 0 || i == count) {
                reporter.onProgress(i, count);
            }
        }

        reporter.onStepCompleted(LoadingStep.LOADING_ELEMENTS);

        // ── Step 2: fetch parts (Objects) owned by Blocks/InterfaceBlocks ────
        reporter.onStepStarted(LoadingStep.BUILDING_INDEX);

        Map<String, Integer> guidToIndex = new HashMap<>();
        for (int idx = 0; idx < records.size(); idx++) {
            guidToIndex.put(records.get(idx).guid(), idx);
        }

        for (ElementRecord blockRec : new ArrayList<>(records)) {
            if (!blockRec.kind().isBlockLike()) continue;
            IRPModelElement blockElt = handleByGuid.get(blockRec.guid());
            if (blockElt == null || !(blockElt instanceof IRPClassifier)) continue;

            IRPClassifier classifier = (IRPClassifier) blockElt;
            List<ElementRecord> partRecords = fetchOwnedParts(classifier, blockRec);
            for (ElementRecord partRec : partRecords) {
                Integer existingIdx = guidToIndex.get(partRec.guid());
                if (existingIdx != null) {
                    records.set(existingIdx, partRec);
                } else {
                    records.add(partRec);
                    guidToIndex.put(partRec.guid(), records.size() - 1);
                }
                if (!handleByGuid.containsKey(partRec.guid())) {
                    IRPModelElement partHandle = safeFindNestedElement(classifier, partRec.guid());
                    if (partHandle != null) handleByGuid.put(partRec.guid(), partHandle);
                }
            }
        }

        records.sort(Comparator
                .comparing((ElementRecord r) -> r.ownerPath().orElse(""))
                .thenComparing(ElementRecord::name));

        reporter.onStepCompleted(LoadingStep.BUILDING_INDEX);
        reporter.onDone();

        return new RhapsodyModelSnapshot(
                Collections.unmodifiableList(records),
                Collections.unmodifiableMap(handleByGuid));
    }

    private IRPCollection safeGetNestedElementsRecursive(IRPProject project) {
        try { return project.getNestedElementsRecursive(); } catch (Throwable t) { return null; }
    }

    private IRPModelElement safeGetItem(IRPCollection c, int index1Based) {
        try {
            Object o = c.getItem(index1Based);
            return (o instanceof IRPModelElement) ? (IRPModelElement) o : null;
        } catch (Throwable t) { return null; }
    }

    private Set<String> readStereotypeNames(IRPModelElement elt) {
        Set<String> result = new LinkedHashSet<>();
        try {
            IRPCollection sts = elt.getStereotypes();
            if (sts != null) {
                int count = sts.getCount();
                for (int i = 1; i <= count; i++) {
                    Object o = sts.getItem(i);
                    String n = null;
                    if (o instanceof IRPStereotype) {
                        n = ((IRPStereotype) o).getName();
                    } else if (o instanceof IRPModelElement) {
                        n = ((IRPModelElement) o).getName();
                    } else if (o != null) {
                        try { n = (String) o.getClass().getMethod("getName").invoke(o); }
                        catch (Throwable ignore) {}
                    }
                    if (n != null) { String v = n.trim(); if (!v.isEmpty()) result.add(v); }
                }
            }
        } catch (Throwable t) {}
        return result.isEmpty() ? Collections.emptySet() : result;
    }

    private String computeOwnerPath(IRPModelElement elt) {
        Deque<String> parts = new ArrayDeque<>();
        try {
            IRPModelElement curr = elt.getOwner();
            while (curr != null && !(curr instanceof IRPProject)) {
                String n = curr.getName();
                if (n != null && !n.trim().isEmpty()) parts.addFirst(n.trim());
                curr = curr.getOwner();
            }
        } catch (Throwable t) {}
        return parts.isEmpty() ? null : String.join("::", parts);
    }

    private IRPClassifier safeAttributeType(IRPAttribute a) {
        try { return a.getType(); } catch (Throwable t) { return null; }
    }

    private IRPClassifier safePortType(IRPPort p) {
        try { return p.getOtherClass(); } catch (Throwable t) { return null; }
    }

    private String safeStr(String s) { return s == null ? "" : s.trim(); }

    private ElementKind classify(String metaClass, Set<String> stereotypes) {
        String mc = metaClass == null ? "" : metaClass.trim();
        if ("Class".equals(mc)) {
            if (containsStereo(stereotypes, "Block"))          return ElementKind.BLOCK;
            if (containsStereo(stereotypes, "InterfaceBlock")) return ElementKind.INTERFACE_BLOCK;
            return ElementKind.OTHER;
        }
        if ("Object".equals(mc))    return ElementKind.OTHER;
        if ("Attribute".equals(mc)) {
            if (containsStereoAnyCase(stereotypes, "Part")) return ElementKind.PART;
            return ElementKind.OTHER;
        }
        if ("Port".equals(mc)) {
            if (containsStereoAnyCase(stereotypes, "FullPort"))  return ElementKind.PORT_FULL;
            if (containsStereoAnyCase(stereotypes, "ProxyPort")) return ElementKind.PORT_PROXY;
            if (containsStereoAnyCase(stereotypes, "FlowPort"))  return ElementKind.PORT_FLOW;
            return ElementKind.PORT;
        }
        if ("SysMLPort".equals(mc)) {
            if (containsStereoAnyCase(stereotypes, "flowPort"))  return ElementKind.PORT_FLOW;
            if (containsStereoAnyCase(stereotypes, "FullPort"))  return ElementKind.PORT_FULL;
            if (containsStereoAnyCase(stereotypes, "ProxyPort")) return ElementKind.PORT_PROXY;
            return ElementKind.PORT;
        }
        if ("Package".equals(mc))     return ElementKind.PACKAGE;
        if ("Interface".equals(mc))   return ElementKind.INTERFACE;
        if ("Requirement".equals(mc)) return ElementKind.REQUIREMENT;
        if ("Connector".equals(mc))   return ElementKind.CONNECTOR;
        return ElementKind.OTHER;
    }

    private List<ElementRecord> fetchOwnedParts(IRPClassifier classifier, ElementRecord ownerRec) {
        List<ElementRecord> result = new ArrayList<>();
        try {
            IRPCollection nested = classifier.getNestedElements();
            if (nested == null) return result;
            int count = nested.getCount();
            for (int i = 1; i <= count; i++) {
                Object o = nested.getItem(i);
                if (!(o instanceof IRPModelElement)) continue;
                IRPModelElement elt = (IRPModelElement) o;
                if (!"Object".equals(safeStr(elt.getMetaClass()))) continue;

                String guid = safeStr(elt.getGUID());
                String name = safeStr(elt.getName());
                if (guid.isEmpty() || name.isEmpty()) continue;

                Set<String> stereotypes = readStereotypeNames(elt);
                String description = safeGetDescription(elt);
                String ownerPath = ownerRec.ownerPath()
                        .map(p -> p + "::" + ownerRec.name())
                        .orElse(ownerRec.name());

                String typeGuid = null, typeName = null;
                if (elt instanceof IRPInstance) {
                    try {
                        IRPClassifier otherClass = ((IRPInstance) elt).getOtherClass();
                        if (otherClass != null) {
                            typeGuid = safeStr(otherClass.getGUID());
                            typeName = safeStr(otherClass.getName());
                        }
                    } catch (Throwable t) {}
                }

                result.add(ElementRecord.builder()
                        .guid(guid).name(name).metaClass(safeStr(elt.getMetaClass()))
                        .kind(ElementKind.PART)
                        .ownerGuid(ownerRec.guid()).ownerPath(ownerPath)
                        .stereotypes(stereotypes)
                        .typeGuid(typeGuid).typeName(typeName)
                        .description(description)
                        .build());
            }
        } catch (Throwable t) {}
        return result;
    }

    private IRPModelElement safeFindNestedElement(IRPClassifier owner, String guid) {
        try {
            IRPCollection nested = owner.getNestedElements();
            if (nested == null) return null;
            for (int i = 1; i <= nested.getCount(); i++) {
                Object o = nested.getItem(i);
                if (o instanceof IRPModelElement && guid.equals(((IRPModelElement) o).getGUID()))
                    return (IRPModelElement) o;
            }
        } catch (Throwable t) {}
        return null;
    }

    private String safeGetDescription(IRPModelElement elt) {
        try { String d = elt.getDescription(); return d == null ? null : d.trim(); }
        catch (Throwable t) { return null; }
    }

    private boolean containsStereo(Set<String> set, String exact) {
        return set != null && set.contains(exact);
    }

    private boolean containsStereoAnyCase(Set<String> set, String name) {
        if (set == null || name == null) return false;
        for (String s : set) { if (s.equalsIgnoreCase(name)) return true; }
        return false;
    }
}
