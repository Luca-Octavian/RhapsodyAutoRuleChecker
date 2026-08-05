// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyModelLoader.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load.ModelRecordPostProcessor;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load.RhapsodyElementReader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load.RhapsodyReferenceReader;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.ComLoadDiagnostics;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import org.rhapsodychecker.rhapsodyruleverifier.core.util.ReflectiveMethodCache;

import java.util.*;
import java.util.Locale;

public final class RhapsodyModelLoader {

    private final ProgressReporter reporter;
    private final ComLoadDiagnostics diagnostics;

    public RhapsodyModelLoader() {
        this(ProgressReporter.NOOP);
    }

    public RhapsodyModelLoader(ProgressReporter reporter) {
        this(reporter, ComLoadDiagnostics.disabled());
    }

    public RhapsodyModelLoader(ProgressReporter reporter,
                               ComLoadDiagnostics diagnostics) {
        this.reporter = reporter != null ? reporter : ProgressReporter.NOOP;
        this.diagnostics = diagnostics != null
                ? diagnostics : ComLoadDiagnostics.disabled();
    }

    public RhapsodyModelSnapshot loadModel(IRPProject project) {
        Objects.requireNonNull(project, "project must not be null");

        List<ElementRecord> records = new ArrayList<>();
        Map<String, IRPModelElement> handleByGuid = new HashMap<>(4096);
        Map<String, List<RhapsodyModelSnapshot.RelationInfo>> relationsByOwner = new HashMap<>();
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> referencesByElement = new HashMap<>();

        // ── Step 1: collect all elements ─────────────────────────────────────
        reporter.onStepStarted(LoadingStep.LOADING_ELEMENTS);

        long recursiveStarted = diagnostics.start();
        IRPCollection all = safeGetNestedElementsRecursive(project);
        diagnostics.success("recursive collection", recursiveStarted);

        long countStarted = diagnostics.start();
        int count = all != null ? all.getCount() : 0;
        diagnostics.success("collection getCount", countStarted);

        RhapsodyElementReader elementReader =
                new RhapsodyElementReader(diagnostics);
        for (int i = 1; i <= count; i++) {
            long itemStarted = diagnostics.start();
            IRPModelElement element = safeGetItem(all, i);
            diagnostics.success("collection item", itemStarted);

            ElementRecord record =
                    elementReader.read(element, relationsByOwner);
            if (record != null) {
                records.add(record);
                handleByGuid.put(record.guid(), element);
            }

            if (i % 50 == 0 || i == count) {
                reporter.onProgress(i, count);
            }
        }

        reporter.onStepCompleted(LoadingStep.LOADING_ELEMENTS);

        // References are hydrated only after every element's metadata is known.
        // This preserves all references while avoiding repeated metaclass and
        // stereotype reads for references to elements already in the model.
        reporter.onStepStarted(LoadingStep.READING_REFERENCES);
        referencesByElement.putAll(
                new RhapsodyReferenceReader(diagnostics)
                        .readAll(records, handleByGuid));
        reporter.onStepCompleted(LoadingStep.READING_REFERENCES);

        reporter.onStepStarted(LoadingStep.BUILDING_INDEX);
        ModelRecordPostProcessor.resolveOwnerPaths(records);
        ModelRecordPostProcessor.classifyOwnedParts(records, handleByGuid);

        records.sort(Comparator
                .comparing((ElementRecord r) -> r.ownerPath().orElse(""))
                .thenComparing(ElementRecord::name));

        reporter.onStepCompleted(LoadingStep.BUILDING_INDEX);

        if (diagnostics.isEnabled()) {
            System.out.println(diagnostics.summary());
        }

        return new RhapsodyModelSnapshot(
                Collections.unmodifiableList(records),
                Collections.unmodifiableMap(handleByGuid),
                Collections.unmodifiableMap(relationsByOwner),
                Collections.unmodifiableMap(referencesByElement));
    }

    // ---- Public helpers (reused by IncrementalCacheUpdater) ----

    public static String safeCallString(IRPModelElement elt, String methodName) {
        Object val = ReflectiveMethodCache.invokeOrNull(elt, methodName);
        if (val != null) {
            String s = val.toString().trim();
            return s.isEmpty() ? null : s;
        }
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
            // Check InterfaceBlock BEFORE Block — "InterfaceBlock" contains "Block"
            if (containsStereoContainsIgnoreCase(stereotypes, "interfaceblock")) return ElementKind.INTERFACE_BLOCK;
            if (containsStereo(stereotypes, "Block"))          return ElementKind.BLOCK;
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

    public static String safeGetDescription(IRPModelElement elt) {
        try { String d = elt.getDescription(); return d == null ? null : d.trim(); }
        catch (Throwable t) { return null; }
    }

    /**
     * Reads the user-defined metaclass from a model element (e.g. "FlowProperty" for
     * IRPAttribute elements that represent SysML FlowProperties).
     * Returns null if not available or on error.
     */
    public static String safeGetUserDefinedMetaClass(IRPModelElement elt) {
        try {
            // Declared directly on IRPModelElement — no reflection needed.
            String result = elt.getUserDefinedMetaClass();
            if (result != null) {
                String s = result.trim();
                return s.isEmpty() ? null : s;
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }

    /**
     * Reads the default (initial) value from an IRPAttribute.
     * Returns null if not available, empty, or on error.
     */
    public static String safeGetDefaultValue(IRPAttribute attr) {
        try {
            // getDefaultValue() is declared on IRPVariable, which IRPAttribute extends —
            // no reflection needed.
            String result = attr.getDefaultValue();
            if (result != null) {
                String s = result.trim();
                return s.isEmpty() ? null : s;
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }

    private static boolean containsStereo(Set<String> set, String exact) {
        return set != null && set.contains(exact);
    }

    private static boolean containsStereoAnyCase(Set<String> set, String name) {
        if (set == null || name == null) return false;
        for (String s : set) { if (s.equalsIgnoreCase(name)) return true; }
        return false;
    }

    /**
     * Case-insensitive substring match against stereotype names.
     * Handles stereotype names like "SysML::InterfaceBlock", "interfaceBlock", etc.
     */
    private static boolean containsStereoContainsIgnoreCase(Set<String> set, String substring) {
        if (set == null || substring == null) return false;
        String lower = substring.toLowerCase(Locale.ROOT);
        for (String s : set) {
            if (s != null && s.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }

    // ---- Port fields resolution (shared by loadModel and IncrementalCacheUpdater) ----

    /** Holds the fields resolved by {@link #resolvePortFields}. */
    public static final class PortFields {
        public final String portDirection;
        public final String portMultiplicity;
        public final String typeGuid;
        public final String typeName;
        public PortFields(String portDirection, String portMultiplicity, String typeGuid, String typeName) {
            this.portDirection = portDirection;
            this.portMultiplicity = portMultiplicity;
            this.typeGuid = typeGuid;
            this.typeName = typeName;
        }
    }

    /**
     * Resolves port direction, multiplicity, and (if not already known) type for a
     * Port/SysMLPort element, preferring compile-time direct calls over reflection.
     * Shared by loadModel() and IncrementalCacheUpdater.readFullElement().
     */
    public static PortFields resolvePortFields(IRPModelElement elt, String existingTypeGuid, String existingTypeName) {
        String portDirection = null;
        String typeGuid = existingTypeGuid;
        String typeName = existingTypeName;

        if (elt instanceof IRPSysMLPort) {
            IRPSysMLPort sysMlPort = (IRPSysMLPort) elt;
            try {
                String d = sysMlPort.getPortDirection();
                portDirection = (d == null || d.trim().isEmpty()) ? null : d.trim();
            } catch (Throwable t) { /* ignore */ }

            if (typeGuid == null || typeGuid.isEmpty()) {
                try {
                    IRPClassifier t = sysMlPort.getType();
                    if (t != null) {
                        typeGuid = safeStr(t.getGUID());
                        typeName = safeStr(t.getName());
                    }
                } catch (Throwable t) { /* ignore */ }
            }
        } else {
            portDirection = safeCallString(elt, "getPortDirection");
            if (portDirection == null) portDirection = safeCallString(elt, "getDirection");

            if (typeGuid == null || typeGuid.isEmpty()) {
                Object cls = ReflectiveMethodCache.invokeOrNull(elt, "getType");
                if (cls instanceof IRPModelElement) {
                    typeGuid = safeStr(((IRPModelElement) cls).getGUID());
                    typeName = safeStr(((IRPModelElement) cls).getName());
                }
            }
        }

        String portMultiplicity;
        if (elt instanceof IRPRelation) {
            String m = null;
            try { m = ((IRPRelation) elt).getMultiplicity(); } catch (Throwable t) { /* ignore */ }
            portMultiplicity = (m == null || m.trim().isEmpty()) ? null : m.trim();
        } else {
            portMultiplicity = safeCallString(elt, "getMultiplicity");
        }

        return new PortFields(portDirection, portMultiplicity, typeGuid, typeName);
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
            // Generalization: child (owner) → parent (base class).
            // IRPGeneralization.getBaseClass() is declared directly — no reflection needed.
            if (elt instanceof IRPGeneralization) {
                try {
                    IRPClassifier base = ((IRPGeneralization) elt).getBaseClass();
                    if (base != null) otherEndGuid = safeStr(base.getGUID());
                } catch (Throwable t) { /* ignore */ }
            }
        } else if ("Association".equals(metaClass) || "AssociationEnd".equals(metaClass)) {
            // getOtherClass() is declared on IRPRelation. Cast directly when the concrete
            // element implements it; otherwise fall back to the cached reflective probe.
            if (elt instanceof IRPRelation) {
                try {
                    IRPClassifier other = ((IRPRelation) elt).getOtherClass();
                    if (other != null) otherEndGuid = safeStr(other.getGUID());
                } catch (Throwable t) { /* ignore */ }
            } else {
                otherEndGuid = safeCallReflect(elt, "getOtherClass");
            }
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
            Object result = ReflectiveMethodCache.invokeOrNull(elt, methodName);
            if (result instanceof IRPModelElement) {
                return safeStr(((IRPModelElement) result).getGUID());
            }
        } catch (Throwable t) { /* ignore */ }
        return null;
    }
}
