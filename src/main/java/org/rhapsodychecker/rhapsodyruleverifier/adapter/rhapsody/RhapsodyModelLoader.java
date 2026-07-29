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
        Map<String, List<RhapsodyModelSnapshot.RelationInfo>> relationsByOwner = new HashMap<>();
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> referencesByElement = new HashMap<>();

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

            collectRelation(metaClass, guid, ownerGuid, stereotypes, elt, relationsByOwner);

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
                    .ownerGuid(ownerGuid).ownerPath(null)
                    .stereotypes(stereotypes)
                    .typeGuid(typeGuid).typeName(typeName)
                    .description(description)
                    .portDirection(portDirection)
                    .portMultiplicity(portMultiplicity)
                    .tagValues(tagValues)
                    .build());
            handleByGuid.put(guid, elt);

            // Pre-index incoming references
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

        // ── Step 1b: compute ownerPath locally from GUID relationships ───────
        // Instead of walking getOwner().getName() up the COM hierarchy for each
        // element, we build ownerPath entirely in Java memory from the
        // guid→name and guid→ownerGuid maps already collected.
        {
            Map<String, String> guidToName = new HashMap<>(records.size());
            Map<String, String> guidToOwnerGuid = new HashMap<>(records.size());
            for (ElementRecord r : records) {
                guidToName.put(r.guid(), r.name());
                r.ownerGuid().ifPresent(og -> guidToOwnerGuid.put(r.guid(), og));
            }

            // Cache: ownerGuid → computed path for that owner (avoids recomputing for siblings)
            Map<String, String> ownerPathCache = new HashMap<>(2048);

            for (int idx = 0; idx < records.size(); idx++) {
                ElementRecord r = records.get(idx);
                String og = r.ownerGuid().orElse(null);
                if (og == null) continue; // project-level element, no ownerPath

                String path = buildOwnerPathLocal(og, guidToName, guidToOwnerGuid, ownerPathCache);
                if (path != null) {
                    records.set(idx, r.withOwnerPath(path));
                }
            }
        }

        // ── Step 2: local part classification ────────────────────────────────
        // Objects (metaClass=Object) owned by Blocks/InterfaceBlocks are parts.
        // getNestedElementsRecursive() returns these Objects, so we classify
        // them locally by checking ownerGuid against the known Block set —
        // no per-Block getNestedElements() COM calls needed.
        reporter.onStepStarted(LoadingStep.BUILDING_INDEX);

        {
            // Build set of Block/InterfaceBlock GUIDs
            Set<String> blockGuids = new HashSet<>();
            for (ElementRecord r : records) {
                if (r.kind().isBlockLike()) {
                    blockGuids.add(r.guid());
                }
            }

            // Reclassify Objects owned by Blocks as PART, load type info via handle
            for (int idx = 0; idx < records.size(); idx++) {
                ElementRecord r = records.get(idx);
                if (!"Object".equals(r.metaClass())) continue;

                String ownerGuid = r.ownerGuid().orElse(null);
                if (ownerGuid == null || !blockGuids.contains(ownerGuid)) continue;

                // This Object is owned by a Block → reclassify as PART
                String typeGuid = r.typeGuid().orElse(null);
                String typeName = r.typeName().orElse(null);

                // Load type info from IRPInstance.getOtherClass() if not already present
                if (typeGuid == null || typeGuid.isEmpty()) {
                    IRPModelElement handle = handleByGuid.get(r.guid());
                    if (handle instanceof IRPInstance) {
                        try {
                            IRPClassifier otherClass = ((IRPInstance) handle).getOtherClass();
                            if (otherClass != null) {
                                typeGuid = safeStr(otherClass.getGUID());
                                typeName = safeStr(otherClass.getName());
                            }
                        } catch (Throwable t) { /* ignore */ }
                    }
                }

                records.set(idx, r.withKindAndType(ElementKind.PART, typeGuid, typeName));
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
                Collections.unmodifiableMap(relationsByOwner),
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

    /**
     * Returns true if the GUID or name represents a remote OSLC/Jazz resource proxy
     * rather than a normal persisted Rhapsody model element.
     * These are lazily resolved by Rhapsody and may not appear consistently
     * in getNestedElementsRecursive() across cold/warm starts.
     */
    public static boolean isRemoteOslcResource(String guid) {
        return guid != null
                && (guid.startsWith("http://") || guid.startsWith("https://"));
    }

    /**
     * Check both GUID and name — OSLC proxies may have a normal GUID but a URL-format name.
     */
    public static boolean isRemoteOslcResource(String guid, String name) {
        if (guid != null && (guid.startsWith("http://") || guid.startsWith("https://"))) return true;
        if (name != null && (name.startsWith("http://") || name.startsWith("https://"))) return true;
        return false;
    }

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

    /**
     * Builds ownerPath entirely in Java memory by walking the ownerGuid chain.
     * No COM calls needed — uses the guid→name and guid→ownerGuid maps
     * collected during the initial scan.
     *
     * @param ownerGuid   the immediate owner's GUID
     * @param guidToName  map of GUID → element name (from scan)
     * @param guidToOwnerGuid map of GUID → that element's ownerGuid
     * @param cache       memoization cache: ownerGuid → its computed path
     * @return the owner path string (e.g. "Pkg::SubPkg::Block"), or null if owner not in scan
     */
    public static String buildOwnerPathLocal(String ownerGuid,
                                              Map<String, String> guidToName,
                                              Map<String, String> guidToOwnerGuid,
                                              Map<String, String> cache) {
        if (ownerGuid == null || ownerGuid.isEmpty()) return null;

        // Check cache first
        if (cache.containsKey(ownerGuid)) {
            return cache.get(ownerGuid);
        }

        // Walk the chain upward, collecting names
        Deque<String> parts = new ArrayDeque<>();
        Set<String> visited = new HashSet<>(); // cycle guard
        String current = ownerGuid;

        while (current != null && !current.isEmpty()) {
            if (!visited.add(current)) break; // cycle detected
            String name = guidToName.get(current);
            if (name == null) {
                // Owner not in our scan (e.g. project root or external) — stop here
                break;
            }
            parts.addFirst(name);
            current = guidToOwnerGuid.get(current); // walk up
        }

        String path = parts.isEmpty() ? null : join(parts);
        cache.put(ownerGuid, path);
        return path;
    }

    /** Java 8 compatible String.join for Deque */
    private static String join(Deque<String> parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (!first) sb.append("::");
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    /**
     * Fetches owned parts (Objects) from a Block/InterfaceBlock classifier,
     * collecting both the ElementRecord AND the IRPModelElement handle in a single
     * getNestedElements() traversal. This eliminates the separate safeFindNestedElement()
     * call which previously re-traversed the same collection.
     */
    private void fetchOwnedPartsWithHandles(IRPClassifier classifier,
                                             ElementRecord ownerRec,
                                             List<ElementRecord> records,
                                             Map<String, Integer> guidToIndex,
                                             Map<String, IRPModelElement> handleByGuid) {
        try {
            IRPCollection nested = classifier.getNestedElements();
            if (nested == null) return;
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

                ElementRecord partRec = ElementRecord.builder()
                        .guid(guid).name(name).metaClass(safeStr(elt.getMetaClass()))
                        .kind(ElementKind.PART)
                        .ownerGuid(ownerRec.guid()).ownerPath(ownerPath)
                        .stereotypes(stereotypes)
                        .typeGuid(typeGuid).typeName(typeName)
                        .description(description)
                        .tagValues(partTagValues)
                        .build();

                Integer existingIdx = guidToIndex.get(guid);
                if (existingIdx != null) {
                    records.set(existingIdx, partRec);
                } else {
                    records.add(partRec);
                    guidToIndex.put(guid, records.size() - 1);
                }

                // Store handle directly — no need for separate safeFindNestedElement()
                if (!handleByGuid.containsKey(guid)) {
                    handleByGuid.put(guid, elt);
                }
            }
        } catch (Throwable t) {}
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

    // ---- Relation collection (SRP: single method handles all relation metaclasses) ----

    /**
     * Detects whether the given element is a relation (Dependency, Generalization,
     * Association) and, if so, indexes it in the relationsByOwner map.
     *
     * <p>This method is intentionally static-compatible and reusable from
     * {@link org.rhapsodychecker.rhapsodyruleverifier.cache.update.IncrementalCacheUpdater}.
     *
     * @param metaClass        the element's metaClass
     * @param guid             the element's GUID
     * @param ownerGuid        the owner's GUID (null if project-level)
     * @param stereotypes      the element's stereotypes
     * @param elt              the live Rhapsody element handle
     * @param relationsByOwner the map to populate
     */
    public static void collectRelation(String metaClass, String guid, String ownerGuid,
                                        Set<String> stereotypes, IRPModelElement elt,
                                        Map<String, List<RhapsodyModelSnapshot.RelationInfo>> relationsByOwner) {
        if (ownerGuid == null || ownerGuid.isEmpty()) return;

        String otherEndGuid = null;

        if ("Dependency".equals(metaClass)) {
            if (elt instanceof IRPDependency) {
                try {
                    IRPModelElement dependsOn = ((IRPDependency) elt).getDependsOn();
                    if (dependsOn != null) {
                        otherEndGuid = safeStr(dependsOn.getGUID());
                    }
                } catch (Throwable t) { /* ignore */ }
            }
        } else if ("Generalization".equals(metaClass)) {
            // Generalization: child (owner) → parent (base class)
            // IRPGeneralization.getBaseClass() returns the parent classifier
            otherEndGuid = safeCallReflect(elt, "getBaseClass");
        } else if ("Association".equals(metaClass) || "AssociationEnd".equals(metaClass)) {
            // Association: try getOtherClass() for the far end
            otherEndGuid = safeCallReflect(elt, "getOtherClass");
        } else {
            // Not a relation metaclass we track
            return;
        }

        RhapsodyModelSnapshot.RelationInfo relInfo =
                new RhapsodyModelSnapshot.RelationInfo(guid, metaClass, stereotypes, otherEndGuid);

        List<RhapsodyModelSnapshot.RelationInfo> list = relationsByOwner.get(ownerGuid);
        if (list == null) {
            list = new ArrayList<RhapsodyModelSnapshot.RelationInfo>();
            relationsByOwner.put(ownerGuid, list);
        }
        list.add(relInfo);
    }

    /**
     * Reflectively calls a no-arg method that returns an IRPModelElement,
     * and extracts the GUID from it. Used for Generalization.getBaseClass()
     * and Association.getOtherClass() which may not be in the compile-time API.
     */
    private static String safeCallReflect(IRPModelElement elt, String methodName) {
        try {
            java.lang.reflect.Method m = elt.getClass().getMethod(methodName);
            Object result = m.invoke(elt);
            if (result instanceof IRPModelElement) {
                return safeStr(((IRPModelElement) result).getGUID());
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }
}
