// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/ModelLoadService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCache;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.cache.update.DiffResult;
import org.rhapsodychecker.rhapsodyruleverifier.cache.update.IncrementalCacheUpdater;
import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PhaseTimer;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PipelineProfiler;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.detection.DetectionFacade;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.PortCapabilities;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.ProfileSummary;
import org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody.PortProbeService;
import org.rhapsodychecker.rhapsodyruleverifier.ui.PackageNode;

import java.io.File;
import java.util.*;

/**
 * Business logic for model loading — no Swing dependency.
 * Can be called from UI, tests, or CLI.
 */
public final class ModelLoadService {

    private ModelLoadService() {}

    /**
     * Immutable result of a model load operation.
     */
    public static final class LoadResult {
        private final RhapsodyModelSnapshot snapshot;
        private final ElementIndex index;
        private final PackageNode packageTree;
        private final FastDetectionResult detectionResult;
        private final boolean fromCache;
        private final String statusMessage;

        public LoadResult(RhapsodyModelSnapshot snapshot, ElementIndex index,
                          PackageNode packageTree, FastDetectionResult detectionResult,
                          boolean fromCache, String statusMessage) {
            this.snapshot = snapshot;
            this.index = index;
            this.packageTree = packageTree;
            this.detectionResult = detectionResult;
            this.fromCache = fromCache;
            this.statusMessage = statusMessage;
        }

        public RhapsodyModelSnapshot snapshot() { return snapshot; }
        public ElementIndex index() { return index; }
        public PackageNode packageTree() { return packageTree; }
        public FastDetectionResult detectionResult() { return detectionResult; }
        public boolean isFromCache() { return fromCache; }
        public String statusMessage() { return statusMessage; }
    }

    /**
     * Load model from JSON cache.
     */
    public static LoadResult loadFromCache(File cacheFile) throws Exception {
        PipelineProfiler profiler = new PipelineProfiler("loadFromCache");
        profiler.startPipeline();

        PhaseTimer tRead = profiler.startPhase("JSON deserialization");
        ModelCacheManager.CacheLoadResult result = ModelCacheManager.readCache(cacheFile);
        RhapsodyModelSnapshot snapshot = result.snapshot();
        CacheMetadata metadata = result.metadata();
        tRead.stop().items(snapshot.records().size());
        profiler.record(tRead);

        PhaseTimer tIndex = profiler.startPhase("ElementIndex.build()");
        ElementIndex index = ElementIndex.build(snapshot.records());
        tIndex.stop().items(snapshot.records().size());
        profiler.record(tIndex);

        PhaseTimer tTree = profiler.startPhase("buildPackageTree");
        PackageNode packageTree = buildPackageTreeFromRecords(
                snapshot.records(), metadata.getProjectName());
        tTree.stop();
        profiler.record(tTree);

        PhaseTimer tDetect = profiler.startPhase("buildDetectionFromRecords");
        FastDetectionResult detectionResult = buildDetectionFromRecords(snapshot.records());
        tDetect.stop().items(snapshot.records().size());
        profiler.record(tDetect);

        profiler.stopPipeline();
        AppLogger.info(profiler.summary());

        AppLogger.logCacheLoad(cacheFile.getAbsolutePath(),
                snapshot.records().size(), metadata.getCachedAt());

        String status = "Loaded from cache (" + metadata.getCachedAt()
                + ") \u2014 " + snapshot.records().size() + " elements";
        return new LoadResult(snapshot, index, packageTree, detectionResult, true, status);
    }

    /**
     * Load model from live Rhapsody, write cache.
     */
    public static LoadResult loadFromRhapsody(String modelPath,
                                               ProgressReporter reporter) throws Exception {
        PipelineProfiler profiler = new PipelineProfiler("loadFromRhapsody");
        profiler.startPipeline();

        PhaseTimer tConn = profiler.startPhase("Rhapsody connect");
        reporter.onStepStarted(LoadingStep.CONNECTING);
        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
        conn.connect(modelPath);
        reporter.onStepCompleted(LoadingStep.CONNECTING);
        tConn.stop();
        profiler.record(tConn);

        PhaseTimer tScan = profiler.startPhase("Package scan (COM)");
        reporter.onStepStarted(LoadingStep.SCANNING_PACKAGES);
        RhapsodyPackageScanner scanner = new RhapsodyPackageScanner();
        PackageNode packageTree = scanner.scanPackages(conn.getProject());
        reporter.onStepCompleted(LoadingStep.SCANNING_PACKAGES);
        tScan.stop();
        profiler.record(tScan);

        PhaseTimer tLoad = profiler.startPhase("loadModel (COM)");
        RhapsodyModelLoader loader = new RhapsodyModelLoader(reporter);
        RhapsodyModelSnapshot snapshot = loader.loadModel(conn.getProject());
        tLoad.stop().items(snapshot.records().size());
        profiler.record(tLoad);

        PhaseTimer tIndex = profiler.startPhase("ElementIndex.build()");
        ElementIndex index = ElementIndex.build(snapshot.records());
        tIndex.stop().items(snapshot.records().size());
        profiler.record(tIndex);

        PhaseTimer tDetect = profiler.startPhase("Fast detection");
        reporter.onStepStarted(LoadingStep.FAST_DETECTION);
        RhapsodyPortInfoResolver portResolver = new RhapsodyPortInfoResolver(snapshot);
        PortProbeService portProbeService = new PortProbeService(portResolver);
        DetectionFacade detectionFacade = DetectionFacade.create(portProbeService);
        FastDetectionResult detectionResult = detectionFacade.fastScan(
                snapshot.records(), snapshot.handleByGuid(), conn.getApplication());
        reporter.onStepCompleted(LoadingStep.FAST_DETECTION);
        tDetect.stop().items(snapshot.records().size());
        profiler.record(tDetect);

        PhaseTimer tCache = profiler.startPhase("Cache write (JSON)");
        try {
            String projectName = conn.getProject().getName();
            String projectGuid = conn.getProject().getGUID();
            File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
            ModelCacheManager.writeCache(snapshot, projectName, projectGuid, cacheFile, modelPath);
            tCache.stop();
            profiler.record(tCache);
            AppLogger.logCacheWrite(cacheFile.getAbsolutePath(),
                    snapshot.records().size(), cacheFile.length() / 1024);
        } catch (Throwable cacheErr) {
            tCache.stop();
            profiler.record(tCache);
            AppLogger.warn("Cache write failed: " + cacheErr.getMessage());
        }

        profiler.stopPipeline();
        AppLogger.info(profiler.summary());

        AppLogger.logModelLoad(snapshot.records().size(),
                snapshot.dependenciesByOwner().size(),
                snapshot.referencesByElement().size(), 0);

        String status = "Model loaded from Rhapsody (cache updated): "
                + snapshot.records().size() + " elements";
        return new LoadResult(snapshot, index, packageTree, detectionResult, false, status);
    }

    /**
     * Build a FastDetectionResult from cached ElementRecords.
     * Aggregates stereotypes, metaClasses, kinds, ownerPaths, description stats,
     * and port capabilities from the pre-loaded element data.
     */
    public static FastDetectionResult buildDetectionFromRecords(List<ElementRecord> records) {
        Map<String, Long> countsByMetaClass = new LinkedHashMap<String, Long>();
        Map<String, Long> countsByStereotype = new LinkedHashMap<String, Long>();
        Map<String, Long> countsByKind = new LinkedHashMap<String, Long>();
        Set<String> topOwnerPaths = new TreeSet<String>();
        long totalElements = records.size();
        long elementsWithDescription = 0;

        // Port capability counters
        int directedSampled = 0, directedOkType = 0, directedOkDir = 0, directedOkMult = 0;
        int standardSampled = 0, standardOkType = 0, standardOkDir = 0, standardOkMult = 0;

        boolean hasAsilHints = false;

        for (ElementRecord r : records) {
            // MetaClass counts
            String mc = r.metaClass();
            Long mcCount = countsByMetaClass.get(mc);
            countsByMetaClass.put(mc, mcCount != null ? mcCount + 1 : 1);

            // Kind counts
            String kindName = r.kind().name();
            Long kindCount = countsByKind.get(kindName);
            countsByKind.put(kindName, kindCount != null ? kindCount + 1 : 1);

            // Stereotype counts
            for (String st : r.stereotypes()) {
                Long stCount = countsByStereotype.get(st);
                countsByStereotype.put(st, stCount != null ? stCount + 1 : 1);
                if (!hasAsilHints && st.toUpperCase().contains("ASIL")) {
                    hasAsilHints = true;
                }
            }

            // Owner paths (top-level only = first segment)
            String ownerPath = r.ownerPath().orElse(null);
            if (ownerPath != null && !ownerPath.isEmpty()) {
                int sep = ownerPath.indexOf("::");
                topOwnerPaths.add(sep > 0 ? ownerPath.substring(0, sep) : ownerPath);
            }

            // Description stats
            if (r.description().isPresent() && !r.description().get().isEmpty()) {
                elementsWithDescription++;
            }

            // Port capabilities
            if (r.kind().isPortKind()) {
                boolean isDirected = r.kind() == ElementKind.PORT_FLOW
                        || r.kind() == ElementKind.PORT_PROXY;
                if (isDirected) {
                    directedSampled++;
                    if (r.typeName().isPresent() && !r.typeName().get().isEmpty()) directedOkType++;
                    if (r.portDirection().isPresent() && !r.portDirection().get().isEmpty()) directedOkDir++;
                    if (r.portMultiplicity().isPresent() && !r.portMultiplicity().get().isEmpty()) directedOkMult++;
                } else {
                    standardSampled++;
                    if (r.typeName().isPresent() && !r.typeName().get().isEmpty()) standardOkType++;
                    if (r.portDirection().isPresent() && !r.portDirection().get().isEmpty()) standardOkDir++;
                    if (r.portMultiplicity().isPresent() && !r.portMultiplicity().get().isEmpty()) standardOkMult++;
                }
            }
        }

        ProfileSummary profile = new ProfileSummary(Collections.<String>emptySet(), hasAsilHints);
        PortCapabilities ports = new PortCapabilities(
                directedSampled, directedOkType, directedOkDir, directedOkMult,
                standardSampled, standardOkType, standardOkDir, standardOkMult);

        return new FastDetectionResult(
                countsByMetaClass, countsByStereotype, countsByKind,
                topOwnerPaths, totalElements, elementsWithDescription,
                profile, ports);
    }

    /**
     * Load model via incremental update: scan live Rhapsody, diff against cache,
     * only fully read changed/new elements.
     *
     * <p>If Rhapsody is unavailable (not running, connection fails), falls back
     * gracefully to loading from cache — preserving all data including OSLC proxies.
     */
    public static LoadResult loadIncrementalUpdate(String modelPath, File cacheFile,
                                                    ProgressReporter reporter) throws Exception {
        // Always run the incremental scan — Rhapsody keeps changes in memory
        // and may not flush to disk, so file timestamps are unreliable.
        // The incremental scan is still fast: it only does full reads on changed elements.

        // Read existing cache
        ModelCache existingCache = ModelCacheManager.readCacheRaw(cacheFile);

        // Connect to Rhapsody — if unavailable, fall back to cache
        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
        try {
            reporter.onStepStarted(LoadingStep.CONNECTING);
            conn.connect(modelPath);
            reporter.onStepCompleted(LoadingStep.CONNECTING);
        } catch (Throwable connErr) {
            reporter.onStepCompleted(LoadingStep.CONNECTING);
            AppLogger.warn("Rhapsody unavailable for incremental update: " + connErr.getMessage()
                    + " — falling back to cached data");
            LoadResult cached = loadFromCache(cacheFile);
            String fallbackStatus = "Loaded from cache (Rhapsody unavailable) \u2014 "
                    + cached.snapshot().records().size() + " elements";
            return new LoadResult(cached.snapshot(), cached.index(), cached.packageTree(),
                    cached.detectionResult(), true, fallbackStatus);
        }

        // Warm up: scan packages so Rhapsody finishes internal model loading.
        // Without this, getNestedElementsRecursive() may return incomplete results
        // on a cold start (first connection after app launch / cache-only load).
        reporter.onStepStarted(LoadingStep.SCANNING_PACKAGES);
        RhapsodyPackageScanner scanner = new RhapsodyPackageScanner();
        PackageNode packageTree = scanner.scanPackages(conn.getProject());
        reporter.onStepCompleted(LoadingStep.SCANNING_PACKAGES);

        // Run incremental update
        IncrementalCacheUpdater updater = new IncrementalCacheUpdater(
                conn.getProject(), existingCache, reporter);
        IncrementalCacheUpdater.UpdateResult updateResult = updater.update();

        RhapsodyModelSnapshot snapshot = updateResult.snapshot();
        DiffResult diff = updateResult.diff();

        ElementIndex index = ElementIndex.build(snapshot.records());
        String projectName = conn.getProject().getName();

        // Use the live-scanned package tree (already built during warm-up)
        // instead of rebuilding from records, since we have it from the scanner
        FastDetectionResult detectionResult = buildDetectionFromRecords(snapshot.records());

        // Write updated cache (with file timestamps for fast-skip) — but only if
        // the scan was complete. An incomplete scan should not overwrite the cache
        // because it would lose the OSLC proxy elements that weren't scanned.
        if (!diff.wasIncomplete()) {
            try {
                String projectGuid = conn.getProject().getGUID();
                ModelCacheManager.writeCache(snapshot, projectName, projectGuid, cacheFile, modelPath);
                AppLogger.logCacheWrite(cacheFile.getAbsolutePath(),
                        snapshot.records().size(), cacheFile.length() / 1024);
            } catch (Throwable cacheErr) {
                AppLogger.warn("Cache write failed after incremental update: " + cacheErr.getMessage());
            }
        } else {
            AppLogger.info("Skipping cache write — scan was incomplete ("
                    + diff.deferredRemovalCount() + " removals deferred)");
        }

        AppLogger.logIncrementalUpdate(
                diff.changedGuids().size(), diff.newGuids().size(),
                diff.removedGuids().size(), diff.unchangedCount(),
                diff.totalScanned(), updateResult.durationMs());

        String status = "Smart Update: " + diff.summary()
                + " (" + formatDuration(updateResult.durationMs()) + ")";
        return new LoadResult(snapshot, index, packageTree, detectionResult, true, status);
    }

    private static String formatDuration(long ms) {
        long secs = ms / 1000;
        long mins = secs / 60;
        if (mins > 0) return String.format("%dm %ds", mins, secs % 60);
        return String.format("%ds", secs);
    }

    /**
     * Build package tree from element records (for cache mode).
     */
    public static PackageNode buildPackageTreeFromRecords(
            List<ElementRecord> records, String projectName) {
        PackageNode root = new PackageNode("",
                projectName != null ? projectName : "Project", "");

        // Build GUID-based lookup of all package elements
        Set<String> packageGuids = new HashSet<String>();
        Map<String, ElementRecord> packagesByGuid = new LinkedHashMap<String, ElementRecord>();
        for (ElementRecord r : records) {
            if (r.kind() == ElementKind.PACKAGE) {
                packageGuids.add(r.guid());
                packagesByGuid.put(r.guid(), r);
            }
        }

        // Build PackageNodes keyed by GUID
        Map<String, PackageNode> nodesByGuid = new LinkedHashMap<String, PackageNode>();
        for (ElementRecord r : packagesByGuid.values()) {
            // Only include packages whose owner is the project root (no ownerGuid)
            // or another package — skip packages nested inside Blocks/Classes/etc.
            String ownerGuid = r.ownerGuid().orElse(null);
            if (ownerGuid != null && !packageGuids.contains(ownerGuid)) {
                continue;
            }

            String ownerPath = r.ownerPath().orElse("");
            String qualifiedPath = ownerPath.isEmpty()
                    ? r.name() : ownerPath + "::" + r.name();
            nodesByGuid.put(r.guid(),
                    new PackageNode(r.guid(), r.name(), qualifiedPath));
        }

        // Link parent-child using ownerGuid (GUID-based, not path-based)
        for (Map.Entry<String, PackageNode> entry : nodesByGuid.entrySet()) {
            String guid = entry.getKey();
            PackageNode node = entry.getValue();
            ElementRecord rec = packagesByGuid.get(guid);
            String ownerGuid = rec.ownerGuid().orElse(null);

            if (ownerGuid == null) {
                // Top-level package (owner is project)
                root.addChild(node);
            } else {
                PackageNode parentNode = nodesByGuid.get(ownerGuid);
                if (parentNode != null) {
                    parentNode.addChild(node);
                }
                // If parent was filtered out, skip this package entirely
                // (matches live RhapsodyPackageScanner behavior)
            }
        }

        return root;
    }
}