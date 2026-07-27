// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyModelLoader.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import java.lang.reflect.Method;
import java.util.*;

public final class RhapsodyModelLoader {

    private final ProgressReporter reporter;

    public RhapsodyModelLoader() {
        this(ProgressReporter.NOOP);
    }

    public RhapsodyModelLoader(ProgressReporter reporter) {
        this.reporter = reporter != null ? reporter : ProgressReporter.NOOP;
    }

    public RhapsodyModelSnapshot loadModel(IRPProject project) {
        Objects.requireNonNull(project, "project must not be null");

        List<ElementRecord> records = new ArrayList<>();
        Map<String, IRPModelElement> handleByGuid = new HashMap<>(4096);
        Map<String, List<RhapsodyModelSnapshot.DependencyInfo>> dependenciesByOwner = new HashMap<>();
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> referencesByElement = new HashMap<>();

        // Owner path cache: avoids re-walking the same owner chain for siblings
        Map<String, String> ownerPathCache = new HashMap<>(2048);

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
            String ownerPath  = computeOwnerPathCached(elt, ownerPathCache);
            String ownerGuid  = null;
            try {
                IRPModelElement owner = elt.getOwner();
                if (owner != null && !(owner instanceof IRPProject)) {
                    ownerGuid = safeStr(owner.getGUID());
                }
            } catch (Throwable t) { /* ignore */ }

            Set<String> stereotypes = readStereotypeNames(elt);
            String description      = safeGetDescription(elt);
            Map<String, String> tagValues = readAllTags(elt);

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

            if ("Dependency".equals(metaClass)) {
                if (ownerGuid != null && !ownerGuid.isEmpty()) {
                    String otherEndGuid = null;
                    if (elt instanceof IRPDependency) {
                        try {
                            IRPModelElement dependsOn = ((IRPDependency) elt).getDependsOn();
                            if (dependsOn != null) {
                                otherEndGuid = safeStr(dependsOn.getGUID());
                            }
                        } catch (Throwable t) { /* ignore */ }
                    }

                    RhapsodyModelSnapshot.DependencyInfo depInfo =
                            new RhapsodyModelSnapshot.DependencyInfo(guid, stereotypes, otherEndGuid);

                    dependenciesByOwner
                            .computeIfAbsent(ownerGuid, k -> new ArrayList<>())
                            .add(depInfo);
                }
            }

            // ── Pre-load port info to avoid COM calls during evaluation ──
            String portDirection = null;
            String portMultiplicity = null;

            if (kind.isPortKind()) {
                portDirection = safeCallString(elt, "getPortDirection");
                if (portDirection == null) portDirection = safeCallString(elt, "getDirection");

                portMultiplicity = safeCallString(elt, "getMultiplicity");

                if (typeGuid == null || typeGuid.isEmpty()) {
                    try {
                        java.lang.reflect.Method getType = elt.getClass().getMethod("getType");
                        Object cls = getType.invoke(elt);
                        if (cls instanceof IRPModelElement) {
                            typeGuid = safeStr(((IRPModelElement) cls).getGUID());
                            typeName = safeStr(((IRPModelElement) cls).getName());
                        }
                    } catch (Throwable t) { /* ignore */ }
                }
            }

            records.add(ElementRecord.builder()
                    .guid(guid).name(name).metaClass(metaClass).kind(kind)
                    .ownerGuid(ownerGuid).ownerPath(ownerPath)
                    .stereotypes(stereotypes)
                    .typeGuid(typeGuid).typeName(typeName)
                    .description(description)
                    .portDirection(portDirection)
                    .portMultiplicity(portMultiplicity)
                    .tagValues(tagValues)
                    .build());
            handleByGuid.put(guid, elt);

            // ── Pre-index incoming references ──
            try {
                IRPCollection refs = elt.getReferences();
                if (refs != null && refs.getCount() > 0) {
                    List<RhapsodyModelSnapshot.ReferenceInfo> refList =
                            new ArrayList<RhapsodyModelSnapshot.ReferenceInfo>();
                    for (int ri = 1; ri <= refs.getCount(); ri++) {
                        Object ro = refs.getItem(ri);
                        if (ro instanceof IRPModelElement) {
                            IRPModelElement refElt = (IRPModelElement) ro;
                            String refGuid = safeStr(refElt.getGUID());
                            if (!refGuid.isEmpty()) {
                                String refMeta = safeStr(refElt.getMetaClass());
                                Set<String> refStereos = readStereotypeNames(refElt);
                                refList.add(new RhapsodyModelSnapshot.ReferenceInfo(
                                        refGuid, refMeta, refStereos));
                            }
                        }
                    }
                    if (!refList.isEmpty()) {
                        referencesByElement.put(guid, refList);
                    }
                }
            } catch (Throwable t) { /* ignore */ }

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
                Collections.unmodifiableMap(handleByGuid),
                Collections.unmodifiableMap(dependenciesByOwner),
                Collections.unmodifiableMap(referencesByElement));
    }

    // ---- Public helpers (reused by IncrementalCacheUpdater) ----

    public static String safeCallString(IRPModelElement elt, String methodName) {
        try {
            java.lang.reflect.Method m = elt.getClass().getMethod(methodName);
            Object val = m.invoke(elt);
            if (val != null) {
                String s = val.toString().trim();
                return s.isEmpty() ? null : s;
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }

    public static IRPCollection safeGetNestedElementsRecursive(IRPProject project) {
        try { return project.getNestedElementsRecursive(); } catch (Throwable t) { return null; }
    }

    public static IRPModelElement safeGetItem(IRPCollection c, int index1Based) {
        try {
            Object o = c.getItem(index1Based);
            return (o instanceof IRPModelElement) ? (IRPModelElement) o : null;
        } catch (Throwable t) { return null; }
    }

    public static Set<String> readStereotypeNames(IRPModelElement elt) {
        Set<String> result = new LinkedHashSet<>();
        try {
            IRPCollection sts = elt.getStereotypes();
            if (sts != null) {
                int cnt = sts.getCount();
                for (int i = 1; i <= cnt; i++) {
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

    /**
     * Compute owner path with caching — avoids re-walking the same chain for siblings.
     * Uses the owner's GUID as cache key.
     */
    public String computeOwnerPathCached(IRPModelElement elt,
                                          Map<String, String> cache) {
        try {
            IRPModelElement owner = elt.getOwner();
            if (owner == null || owner instanceof IRPProject) return null;

            String ownerGuid = safeStr(owner.getGUID());
            if (ownerGuid.isEmpty()) return computeOwnerPath(elt);

            if (cache.containsKey(ownerGuid)) {
                String cachedOwnerPath = cache.get(ownerGuid);
                String ownerName = safeStr(owner.getName());
                if (ownerName.isEmpty()) return cachedOwnerPath;
                return cachedOwnerPath == null || cachedOwnerPath.isEmpty()
                        ? ownerName : cachedOwnerPath + "::" + ownerName;
            }

            String fullPath = computeOwnerPath(elt);

            if (fullPath != null) {
                cache.put(ownerGuid, fullPath);
            }

            return fullPath;
        } catch (Throwable t) {
            return computeOwnerPath(elt);
        }
    }

    public static String computeOwnerPath(IRPModelElement elt) {
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

    public static IRPClassifier safeAttributeType(IRPAttribute a) {
        try { return a.getType(); } catch (Throwable t) { return null; }
    }

    public static IRPClassifier safePortType(IRPPort p) {
        try { return p.getOtherClass(); } catch (Throwable t) { return null; }
    }

    public static String safeStr(String s) { return s == null ? "" : s.trim(); }

    public static ElementKind classify(String metaClass, Set<String> stereotypes) {
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
            int cnt = nested.getCount();
            for (int i = 1; i <= cnt; i++) {
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

                Map<String, String> partTagValues = readAllTags(elt);

                result.add(ElementRecord.builder()
                        .guid(guid).name(name).metaClass(safeStr(elt.getMetaClass()))
                        .kind(ElementKind.PART)
                        .ownerGuid(ownerRec.guid()).ownerPath(ownerPath)
                        .stereotypes(stereotypes)
                        .typeGuid(typeGuid).typeName(typeName)
                        .description(description)
                        .tagValues(partTagValues)
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

    /**
     * Reads all tagged values from a model element.
     */
    public static Map<String, String> readAllTags(IRPModelElement elt) {
        Map<String, String> tags = new LinkedHashMap<String, String>();

        try {
            Method getTags = elt.getClass().getMethod("getTags");
            Object tagsObj = getTags.invoke(elt);
            if (tagsObj instanceof IRPCollection) {
                IRPCollection tagColl = (IRPCollection) tagsObj;
                for (int i = 1; i <= tagColl.getCount(); i++) {
                    Object item = tagColl.getItem(i);
                    if (item == null) continue;

                    String tagName = null;
                    String tagValue = null;

                    if (item instanceof IRPTag) {
                        IRPTag tag = (IRPTag) item;
                        try { tagName = tag.getName(); } catch (Throwable t) { continue; }
                        try { tagValue = tag.getValue(); } catch (Throwable t) { /* no value */ }
                    } else if (item instanceof IRPModelElement) {
                        IRPModelElement tagElt = (IRPModelElement) item;
                        try { tagName = tagElt.getName(); } catch (Throwable t) { continue; }
                        try {
                            Method getVal = tagElt.getClass().getMethod("getValue");
                            Object v = getVal.invoke(tagElt);
                            if (v instanceof String) {
                                tagValue = (String) v;
                            }
                        } catch (Throwable t) { /* no value */ }
                    } else {
                        try { tagName = (String) item.getClass().getMethod("getName").invoke(item); }
                        catch (Throwable t) { continue; }
                        try {
                            Object v = item.getClass().getMethod("getValue").invoke(item);
                            tagValue = v != null ? v.toString() : null;
                        } catch (Throwable t) { /* no value */ }
                    }

                    if (tagName != null && !tagName.trim().isEmpty()) {
                        String trimmedValue = (tagValue != null) ? tagValue.trim() : "";
                        tags.put(tagName.trim(), trimmedValue);
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }

        return tags.isEmpty() ? Collections.<String, String>emptyMap() : tags;
    }

    public static String safeGetDescription(IRPModelElement elt) {
        try { String d = elt.getDescription(); return d == null ? null : d.trim(); }
        catch (Throwable t) { return null; }
    }

    private static boolean containsStereo(Set<String> set, String exact) {
        return set != null && set.contains(exact);
    }

    private static boolean containsStereoAnyCase(Set<String> set, String name) {
        if (set == null || name == null) return false;
        for (String s : set) { if (s.equalsIgnoreCase(name)) return true; }
        return false;
    }
}