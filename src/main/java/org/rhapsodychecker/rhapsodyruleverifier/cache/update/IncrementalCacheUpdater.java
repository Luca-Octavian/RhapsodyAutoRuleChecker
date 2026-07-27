// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/update/IncrementalCacheUpdater.java
package org.rhapsodychecker.rhapsodyruleverifier.cache.update;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CachedDependency;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CachedElement;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCache;
import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import java.util.*;

/**
 * Incrementally updates a cached model snapshot by scanning the live Rhapsody project,
 * diffing against the cache, and only doing full reads on changed/new elements.
 *
 * <p>Phase 1: Quick scan — reads GUID, name, metaClass, stereotypes, description, tags
 *            for every element. Collects dependency info naturally.
 * <p>Phase 2: Diff — compares fingerprints against cached elements.
 * <p>Phase 3: Targeted full read — does complete reads (owner path, port info, type, refs)
 *            only for changed/new elements.
 * <p>Phase 4: Rebuild — merges unchanged cached data with fresh reads into a new snapshot.
 */
public final class IncrementalCacheUpdater {

    private final IRPProject project;
    private final ModelCache existingCache;
    private final ProgressReporter reporter;

    public IncrementalCacheUpdater(IRPProject project, ModelCache existingCache,
                                   ProgressReporter reporter) {
        this.project = Objects.requireNonNull(project, "project");
        this.existingCache = Objects.requireNonNull(existingCache, "existingCache");
        this.reporter = reporter != null ? reporter : ProgressReporter.NOOP;
    }

    /**
     * Run the incremental update. Returns the result with diff stats and updated snapshot.
     */
    public UpdateResult update() {
        long startMs = System.currentTimeMillis();

        // ── Phase 1: Quick scan ─────────────────────────────────────────────
        reporter.onStepStarted(LoadingStep.INCREMENTAL_SCANNING);

        Map<String, ElementFingerprint> fingerprints = new LinkedHashMap<String, ElementFingerprint>();
        Map<String, IRPModelElement> scannedHandles = new HashMap<String, IRPModelElement>(4096);
        Map<String, List<RhapsodyModelSnapshot.DependencyInfo>> freshDeps =
                new HashMap<String, List<RhapsodyModelSnapshot.DependencyInfo>>();

        IRPCollection all = RhapsodyModelLoader.safeGetNestedElementsRecursive(project);
        int count = all != null ? all.getCount() : 0;

        for (int i = 1; i <= count; i++) {
            IRPModelElement elt = RhapsodyModelLoader.safeGetItem(all, i);
            if (elt == null) continue;

            String guid = RhapsodyModelLoader.safeStr(elt.getGUID());
            String name = RhapsodyModelLoader.safeStr(elt.getName());
            if (guid.isEmpty() || name.isEmpty()) continue;

            String metaClass = RhapsodyModelLoader.safeStr(elt.getMetaClass());
            Set<String> stereotypes = RhapsodyModelLoader.readStereotypeNames(elt);
            String description = RhapsodyModelLoader.safeGetDescription(elt);
            Map<String, String> tagValues = RhapsodyModelLoader.readAllTags(elt);

            fingerprints.put(guid, new ElementFingerprint(
                    guid, name, metaClass, stereotypes, description, tagValues));
            scannedHandles.put(guid, elt);

            // Collect dependency info (free — already reading metaClass)
            if ("Dependency".equals(metaClass)) {
                String ownerGuid = null;
                try {
                    IRPModelElement owner = elt.getOwner();
                    if (owner != null && !(owner instanceof IRPProject)) {
                        ownerGuid = RhapsodyModelLoader.safeStr(owner.getGUID());
                    }
                } catch (Throwable t) { /* ignore */ }

                if (ownerGuid != null && !ownerGuid.isEmpty()) {
                    String otherEndGuid = null;
                    if (elt instanceof IRPDependency) {
                        try {
                            IRPModelElement dependsOn = ((IRPDependency) elt).getDependsOn();
                            if (dependsOn != null) {
                                otherEndGuid = RhapsodyModelLoader.safeStr(dependsOn.getGUID());
                            }
                        } catch (Throwable t) { /* ignore */ }
                    }

                    RhapsodyModelSnapshot.DependencyInfo depInfo =
                            new RhapsodyModelSnapshot.DependencyInfo(guid, stereotypes, otherEndGuid);

                    List<RhapsodyModelSnapshot.DependencyInfo> ownerDeps = freshDeps.get(ownerGuid);
                    if (ownerDeps == null) {
                        ownerDeps = new ArrayList<RhapsodyModelSnapshot.DependencyInfo>();
                        freshDeps.put(ownerGuid, ownerDeps);
                    }
                    ownerDeps.add(depInfo);
                }
            }

            if (i % 50 == 0 || i == count) {
                reporter.onProgress(i, count);
            }
        }

        // ── Phase 1b: Parts second pass ─────────────────────────────────────
        // getNestedElementsRecursive() does NOT return Parts (metaClass=Object)
        // owned by Blocks. We must scan them separately via getNestedElements()
        // on each Block/InterfaceBlock, exactly like RhapsodyModelLoader does.
        for (Map.Entry<String, IRPModelElement> entry : new ArrayList<Map.Entry<String, IRPModelElement>>(
                scannedHandles.entrySet())) {
            ElementFingerprint ownerFp = fingerprints.get(entry.getKey());
            if (ownerFp == null) continue;

            // Check if this is a Block or InterfaceBlock
            String mc = ownerFp.metaClass();
            if (!"Class".equals(mc)) continue;
            Set<String> stereos = ownerFp.stereotypes();
            boolean isBlock = false;
            for (String s : stereos) {
                if ("Block".equals(s) || "InterfaceBlock".equals(s)) {
                    isBlock = true;
                    break;
                }
            }
            if (!isBlock) continue;

            IRPModelElement blockElt = entry.getValue();
            if (!(blockElt instanceof IRPClassifier)) continue;

            try {
                IRPCollection nested = ((IRPClassifier) blockElt).getNestedElements();
                if (nested == null) continue;
                int nestedCount = nested.getCount();
                for (int ni = 1; ni <= nestedCount; ni++) {
                    Object no = nested.getItem(ni);
                    if (!(no instanceof IRPModelElement)) continue;
                    IRPModelElement partElt = (IRPModelElement) no;
                    String partMeta = RhapsodyModelLoader.safeStr(partElt.getMetaClass());
                    if (!"Object".equals(partMeta)) continue;

                    String partGuid = RhapsodyModelLoader.safeStr(partElt.getGUID());
                    String partName = RhapsodyModelLoader.safeStr(partElt.getName());
                    if (partGuid.isEmpty() || partName.isEmpty()) continue;

                    // Skip if already seen in the recursive scan
                    if (fingerprints.containsKey(partGuid)) continue;

                    Set<String> partStereos = RhapsodyModelLoader.readStereotypeNames(partElt);
                    String partDesc = RhapsodyModelLoader.safeGetDescription(partElt);
                    Map<String, String> partTags = RhapsodyModelLoader.readAllTags(partElt);

                    fingerprints.put(partGuid, new ElementFingerprint(
                            partGuid, partName, partMeta, partStereos, partDesc, partTags));
                    scannedHandles.put(partGuid, partElt);
                }
            } catch (Throwable t) { /* ignore */ }
        }

        reporter.onStepCompleted(LoadingStep.INCREMENTAL_SCANNING);

        // ── Phase 2: Diff ───────────────────────────────────────────────────
        reporter.onStepStarted(LoadingStep.INCREMENTAL_DIFFING);

        // Build lookup of cached elements by GUID
        Map<String, CachedElement> cachedByGuid = new LinkedHashMap<String, CachedElement>();
        for (CachedElement ce : existingCache.getElements()) {
            cachedByGuid.put(ce.getGuid(), ce);
        }

        List<String> newGuids = new ArrayList<String>();
        List<String> changedGuids = new ArrayList<String>();
        List<String> removedGuids = new ArrayList<String>();
        int unchangedCount = 0;

        // Check each scanned element against cache
        for (Map.Entry<String, ElementFingerprint> entry : fingerprints.entrySet()) {
            String guid = entry.getKey();
            ElementFingerprint fp = entry.getValue();
            CachedElement cached = cachedByGuid.get(guid);

            if (cached == null) {
                newGuids.add(guid);
            } else if (!fp.matches(cached)) {
                changedGuids.add(guid);
                AppLogger.debug("Changed: " + fp.name() + " [" + guid + "] - "
                        + fp.diffSummary(cached));
            } else {
                unchangedCount++;
            }
        }

        // Check for removed elements (in cache but not in scan)
        for (String cachedGuid : cachedByGuid.keySet()) {
            if (!fingerprints.containsKey(cachedGuid)) {
                removedGuids.add(cachedGuid);
                CachedElement removed = cachedByGuid.get(cachedGuid);
                AppLogger.debug("Removed: " + (removed != null ? removed.getName() : cachedGuid));
            }
        }

        // Check if dependency changes affect additional elements
        // If a dependency's owner or target changed, mark those elements too
        checkDependencyChanges(freshDeps, existingCache.getDependenciesByOwner(),
                cachedByGuid, fingerprints, changedGuids);

        DiffResult diff = new DiffResult(newGuids, removedGuids, changedGuids,
                unchangedCount, fingerprints.size());

        reporter.onStepCompleted(LoadingStep.INCREMENTAL_DIFFING);

        // ── Phase 3: Targeted full read ─────────────────────────────────────
        reporter.onStepStarted(LoadingStep.INCREMENTAL_UPDATING);

        Set<String> needsFullRead = new LinkedHashSet<String>();
        needsFullRead.addAll(newGuids);
        needsFullRead.addAll(changedGuids);

        Map<String, String> ownerPathCache = new HashMap<String, String>(2048);
        RhapsodyModelLoader loader = new RhapsodyModelLoader(); // for computeOwnerPathCached

        // Build updated element records
        List<ElementRecord> updatedRecords = new ArrayList<ElementRecord>();
        Map<String, IRPModelElement> updatedHandles = new HashMap<String, IRPModelElement>();
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> updatedRefs =
                new HashMap<String, List<RhapsodyModelSnapshot.ReferenceInfo>>();

        int fullReadDone = 0;
        int totalFullReads = needsFullRead.size();

        for (String guid : needsFullRead) {
            IRPModelElement elt = scannedHandles.get(guid);
            if (elt == null) continue;

            ElementRecord record = readFullElement(elt, loader, ownerPathCache);
            if (record != null) {
                updatedRecords.add(record);
                updatedHandles.put(guid, elt);

                // Read references for changed/new elements
                readReferences(elt, guid, updatedRefs);
            }

            fullReadDone++;
            if (fullReadDone % 10 == 0 || fullReadDone == totalFullReads) {
                reporter.onProgress(fullReadDone, totalFullReads);
            }
        }

        // Merge: keep unchanged cached elements, add/replace with updated ones
        Map<String, ElementRecord> updatedByGuid = new LinkedHashMap<String, ElementRecord>();
        for (ElementRecord rec : updatedRecords) {
            updatedByGuid.put(rec.guid(), rec);
        }

        // Build final record list: unchanged from cache + updated from live
        List<ElementRecord> finalRecords = new ArrayList<ElementRecord>();
        Set<String> removedSet = new HashSet<String>(removedGuids);

        // Add unchanged elements from cache (converting CachedElement → ElementRecord)
        for (CachedElement ce : existingCache.getElements()) {
            if (removedSet.contains(ce.getGuid())) continue;
            if (updatedByGuid.containsKey(ce.getGuid())) continue;

            // Keep the cached version as-is
            finalRecords.add(toElementRecord(ce));
        }

        // Add updated/new elements
        finalRecords.addAll(updatedRecords);

        // Sort
        finalRecords.sort(Comparator
                .comparing(new java.util.function.Function<ElementRecord, String>() {
                    @Override
                    public String apply(ElementRecord r) {
                        return r.ownerPath().orElse("");
                    }
                })
                .thenComparing(new java.util.function.Function<ElementRecord, String>() {
                    @Override
                    public String apply(ElementRecord r) {
                        return r.name();
                    }
                }));

        // Merge references: keep cached refs for unchanged elements, use fresh for updated
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> finalRefs =
                new HashMap<String, List<RhapsodyModelSnapshot.ReferenceInfo>>();

        // Copy cached references for unchanged elements
        if (existingCache.getReferencesByElement() != null) {
            for (Map.Entry<String, List<org.rhapsodychecker.rhapsodyruleverifier.cache.CachedReference>> entry
                    : existingCache.getReferencesByElement().entrySet()) {
                String elGuid = entry.getKey();
                if (removedSet.contains(elGuid)) continue;
                if (updatedByGuid.containsKey(elGuid)) continue;

                List<RhapsodyModelSnapshot.ReferenceInfo> refInfos =
                        new ArrayList<RhapsodyModelSnapshot.ReferenceInfo>();
                for (org.rhapsodychecker.rhapsodyruleverifier.cache.CachedReference cr : entry.getValue()) {
                    refInfos.add(new RhapsodyModelSnapshot.ReferenceInfo(
                            cr.getGuid(), cr.getMetaClass(),
                            cr.getStereotypes() != null ? cr.getStereotypes() : Collections.<String>emptySet()));
                }
                finalRefs.put(elGuid, refInfos);
            }
        }
        // Add fresh references for updated elements
        finalRefs.putAll(updatedRefs);

        reporter.onStepCompleted(LoadingStep.INCREMENTAL_UPDATING);

        // ── Phase 4: Build snapshot ─────────────────────────────────────────
        RhapsodyModelSnapshot snapshot = new RhapsodyModelSnapshot(
                Collections.unmodifiableList(finalRecords),
                Collections.<String, IRPModelElement>emptyMap(), // no handles in cache mode
                Collections.unmodifiableMap(freshDeps),
                Collections.unmodifiableMap(finalRefs));

        long durationMs = System.currentTimeMillis() - startMs;

        return new UpdateResult(snapshot, diff, durationMs);
    }

    // ---- Full element read (Phase 3) ----

    private ElementRecord readFullElement(IRPModelElement elt,
                                          RhapsodyModelLoader loader,
                                          Map<String, String> ownerPathCache) {
        try {
            String guid = RhapsodyModelLoader.safeStr(elt.getGUID());
            String name = RhapsodyModelLoader.safeStr(elt.getName());
            String metaClass = RhapsodyModelLoader.safeStr(elt.getMetaClass());
            if (guid.isEmpty() || name.isEmpty()) return null;

            String ownerPath = loader.computeOwnerPathCached(elt, ownerPathCache);
            String ownerGuid = null;
            try {
                IRPModelElement owner = elt.getOwner();
                if (owner != null && !(owner instanceof IRPProject)) {
                    ownerGuid = RhapsodyModelLoader.safeStr(owner.getGUID());
                }
            } catch (Throwable t) { /* ignore */ }

            Set<String> stereotypes = RhapsodyModelLoader.readStereotypeNames(elt);
            String description = RhapsodyModelLoader.safeGetDescription(elt);
            Map<String, String> tagValues = RhapsodyModelLoader.readAllTags(elt);

            String typeGuid = null;
            String typeName = null;
            if (elt instanceof IRPAttribute) {
                IRPClassifier cls = RhapsodyModelLoader.safeAttributeType((IRPAttribute) elt);
                if (cls != null) {
                    typeGuid = RhapsodyModelLoader.safeStr(cls.getGUID());
                    typeName = RhapsodyModelLoader.safeStr(cls.getName());
                }
            } else if (elt instanceof IRPPort) {
                IRPClassifier cls = RhapsodyModelLoader.safePortType((IRPPort) elt);
                if (cls != null) {
                    typeGuid = RhapsodyModelLoader.safeStr(cls.getGUID());
                    typeName = RhapsodyModelLoader.safeStr(cls.getName());
                }
            }

            ElementKind kind = RhapsodyModelLoader.classify(metaClass, stereotypes);

            String portDirection = null;
            String portMultiplicity = null;
            if (kind.isPortKind()) {
                portDirection = RhapsodyModelLoader.safeCallString(elt, "getPortDirection");
                if (portDirection == null) {
                    portDirection = RhapsodyModelLoader.safeCallString(elt, "getDirection");
                }
                portMultiplicity = RhapsodyModelLoader.safeCallString(elt, "getMultiplicity");

                if (typeGuid == null || typeGuid.isEmpty()) {
                    try {
                        java.lang.reflect.Method getType = elt.getClass().getMethod("getType");
                        Object cls = getType.invoke(elt);
                        if (cls instanceof IRPModelElement) {
                            typeGuid = RhapsodyModelLoader.safeStr(((IRPModelElement) cls).getGUID());
                            typeName = RhapsodyModelLoader.safeStr(((IRPModelElement) cls).getName());
                        }
                    } catch (Throwable t) { /* ignore */ }
                }
            }

            return ElementRecord.builder()
                    .guid(guid).name(name).metaClass(metaClass).kind(kind)
                    .ownerGuid(ownerGuid).ownerPath(ownerPath)
                    .stereotypes(stereotypes)
                    .typeGuid(typeGuid).typeName(typeName)
                    .description(description)
                    .portDirection(portDirection)
                    .portMultiplicity(portMultiplicity)
                    .tagValues(tagValues)
                    .build();
        } catch (Throwable t) {
            AppLogger.warn("Failed to read element: " + t.getMessage());
            return null;
        }
    }

    private void readReferences(IRPModelElement elt, String guid,
                                Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> refMap) {
        try {
            IRPCollection refs = elt.getReferences();
            if (refs != null && refs.getCount() > 0) {
                List<RhapsodyModelSnapshot.ReferenceInfo> refList =
                        new ArrayList<RhapsodyModelSnapshot.ReferenceInfo>();
                for (int ri = 1; ri <= refs.getCount(); ri++) {
                    Object ro = refs.getItem(ri);
                    if (ro instanceof IRPModelElement) {
                        IRPModelElement refElt = (IRPModelElement) ro;
                        String refGuid = RhapsodyModelLoader.safeStr(refElt.getGUID());
                        if (!refGuid.isEmpty()) {
                            String refMeta = RhapsodyModelLoader.safeStr(refElt.getMetaClass());
                            Set<String> refStereos = RhapsodyModelLoader.readStereotypeNames(refElt);
                            refList.add(new RhapsodyModelSnapshot.ReferenceInfo(
                                    refGuid, refMeta, refStereos));
                        }
                    }
                }
                if (!refList.isEmpty()) {
                    refMap.put(guid, refList);
                }
            }
        } catch (Throwable t) { /* ignore */ }
    }

    // ---- Dependency diff helper ----

    private void checkDependencyChanges(
            Map<String, List<RhapsodyModelSnapshot.DependencyInfo>> freshDeps,
            Map<String, List<CachedDependency>> cachedDeps,
            Map<String, CachedElement> cachedByGuid,
            Map<String, ElementFingerprint> fingerprints,
            List<String> changedGuids) {

        if (cachedDeps == null) return;

        Set<String> alreadyChanged = new HashSet<String>(changedGuids);

        // Check each owner's dependency list
        Set<String> allOwnerGuids = new HashSet<String>();
        allOwnerGuids.addAll(freshDeps.keySet());
        allOwnerGuids.addAll(cachedDeps.keySet());

        for (String ownerGuid : allOwnerGuids) {
            if (alreadyChanged.contains(ownerGuid)) continue;
            if (!fingerprints.containsKey(ownerGuid) && !cachedByGuid.containsKey(ownerGuid)) continue;

            List<RhapsodyModelSnapshot.DependencyInfo> freshList = freshDeps.get(ownerGuid);
            List<CachedDependency> cachedList = cachedDeps.get(ownerGuid);

            if (freshList == null) freshList = Collections.emptyList();
            if (cachedList == null) cachedList = Collections.emptyList();

            if (!depsMatch(freshList, cachedList)) {
                if (fingerprints.containsKey(ownerGuid)) {
                    changedGuids.add(ownerGuid);
                    alreadyChanged.add(ownerGuid);
                    AppLogger.debug("Dependency change detected for owner: " + ownerGuid);
                }
            }
        }
    }

    private boolean depsMatch(List<RhapsodyModelSnapshot.DependencyInfo> fresh,
                              List<CachedDependency> cached) {
        if (fresh.size() != cached.size()) return false;

        // Compare by GUID sets (order doesn't matter)
        Set<String> freshGuids = new LinkedHashSet<String>();
        for (RhapsodyModelSnapshot.DependencyInfo d : fresh) {
            freshGuids.add(d.guid());
        }
        Set<String> cachedGuids = new LinkedHashSet<String>();
        for (CachedDependency d : cached) {
            cachedGuids.add(d.getGuid());
        }
        if (!freshGuids.equals(cachedGuids)) return false;

        // Also check otherEndGuid and stereotypes for each
        Map<String, RhapsodyModelSnapshot.DependencyInfo> freshByGuid =
                new LinkedHashMap<String, RhapsodyModelSnapshot.DependencyInfo>();
        for (RhapsodyModelSnapshot.DependencyInfo d : fresh) {
            freshByGuid.put(d.guid(), d);
        }
        for (CachedDependency cd : cached) {
            RhapsodyModelSnapshot.DependencyInfo fd = freshByGuid.get(cd.getGuid());
            if (fd == null) return false;

            String freshOther = fd.otherEndGuid();
            String cachedOther = cd.getOtherEndGuid();
            if (freshOther == null && cachedOther != null) return false;
            if (freshOther != null && !freshOther.equals(cachedOther)) return false;

            Set<String> freshStereos = fd.stereotypes();
            Set<String> cachedStereos = cd.getStereotypes();
            if (freshStereos == null) freshStereos = Collections.emptySet();
            if (cachedStereos == null) cachedStereos = Collections.emptySet();
            if (!freshStereos.equals(cachedStereos)) return false;
        }

        return true;
    }

    // ---- CachedElement → ElementRecord conversion ----

    private static ElementRecord toElementRecord(CachedElement c) {
        return ElementRecord.builder()
                .guid(c.getGuid())
                .name(c.getName())
                .metaClass(c.getMetaClass())
                .kind(ElementKind.valueOf(c.getKind()))
                .description(c.getDescription())
                .ownerGuid(c.getOwnerGuid())
                .ownerPath(c.getOwnerPath())
                .typeGuid(c.getTypeGuid())
                .typeName(c.getTypeName())
                .stereotypes(c.getStereotypes() != null ? c.getStereotypes() : Collections.<String>emptySet())
                .portDirection(c.getPortDirection())
                .portMultiplicity(c.getPortMultiplicity())
                .tagValues(c.getTagValues() != null ? c.getTagValues() : Collections.<String, String>emptyMap())
                .build();
    }

    // ---- Result holder ----

    /**
     * Result of an incremental update operation.
     */
    public static final class UpdateResult {
        private final RhapsodyModelSnapshot snapshot;
        private final DiffResult diff;
        private final long durationMs;

        public UpdateResult(RhapsodyModelSnapshot snapshot, DiffResult diff, long durationMs) {
            this.snapshot = snapshot;
            this.diff = diff;
            this.durationMs = durationMs;
        }

        public RhapsodyModelSnapshot snapshot() { return snapshot; }
        public DiffResult diff() { return diff; }
        public long durationMs() { return durationMs; }
    }
}