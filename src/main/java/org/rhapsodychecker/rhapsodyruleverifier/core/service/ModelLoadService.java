// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/ModelLoadService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
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
        ModelCacheManager.CacheLoadResult result = ModelCacheManager.readCache(cacheFile);
        RhapsodyModelSnapshot snapshot = result.snapshot();
        CacheMetadata metadata = result.metadata();
        ElementIndex index = ElementIndex.build(snapshot.records());
        PackageNode packageTree = buildPackageTreeFromRecords(
                snapshot.records(), metadata.getProjectName());

        FastDetectionResult detectionResult = buildDetectionFromRecords(snapshot.records());

        String status = "Loaded from cache (" + metadata.getCachedAt()
                + ") \u2014 " + snapshot.records().size() + " elements";
        return new LoadResult(snapshot, index, packageTree, detectionResult, true, status);
    }

    /**
     * Load model from live Rhapsody, write cache.
     */
    public static LoadResult loadFromRhapsody(String modelPath,
                                               ProgressReporter reporter) throws Exception {
        reporter.onStepStarted(LoadingStep.CONNECTING);
        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
        conn.connect(modelPath);
        reporter.onStepCompleted(LoadingStep.CONNECTING);

        reporter.onStepStarted(LoadingStep.SCANNING_PACKAGES);
        RhapsodyPackageScanner scanner = new RhapsodyPackageScanner();
        PackageNode packageTree = scanner.scanPackages(conn.getProject());
        reporter.onStepCompleted(LoadingStep.SCANNING_PACKAGES);

        RhapsodyModelLoader loader = new RhapsodyModelLoader(reporter);
        RhapsodyModelSnapshot snapshot = loader.loadModel(conn.getProject());
        ElementIndex index = ElementIndex.build(snapshot.records());

        reporter.onStepStarted(LoadingStep.FAST_DETECTION);
        RhapsodyPortInfoResolver portResolver = new RhapsodyPortInfoResolver(snapshot);
        PortProbeService portProbeService = new PortProbeService(portResolver);
        DetectionFacade detectionFacade = DetectionFacade.create(portProbeService);
        FastDetectionResult detectionResult = detectionFacade.fastScan(
                snapshot.records(), snapshot.handleByGuid(), conn.getApplication());
        reporter.onStepCompleted(LoadingStep.FAST_DETECTION);

        try {
            String projectName = conn.getProject().getName();
            String projectGuid = conn.getProject().getGUID();
            File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
            ModelCacheManager.writeCache(snapshot, projectName, projectGuid, cacheFile);
        } catch (Throwable cacheErr) {
            System.err.println("Warning: cache write failed: " + cacheErr.getMessage());
        }

        String status = "Model loaded from Rhapsody (cache updated): "
                + snapshot.records().size() + " elements";
        return new LoadResult(snapshot, index, packageTree, detectionResult, false, status);
    }

    /**
     * Build a FastDetectionResult from cached ElementRecords.
     * Aggregates stereotypes, metaClasses, kinds, ownerPaths, description stats,
     * and port capabilities from the pre-loaded element data.
     */
    static FastDetectionResult buildDetectionFromRecords(List<ElementRecord> records) {
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
     * Build package tree from element records (for cache mode).
     */
    public static PackageNode buildPackageTreeFromRecords(
            List<ElementRecord> records, String projectName) {
        PackageNode root = new PackageNode("",
                projectName != null ? projectName : "Project", "");

        Map<String, PackageNode> nodesByPath = new LinkedHashMap<String, PackageNode>();
        for (ElementRecord r : records) {
            if (r.kind() != ElementKind.PACKAGE) continue;
            String ownerPath = r.ownerPath().orElse("");
            String qualifiedPath = ownerPath.isEmpty()
                    ? r.name() : ownerPath + "::" + r.name();
            nodesByPath.put(qualifiedPath,
                    new PackageNode(r.guid(), r.name(), qualifiedPath));
        }

        for (Map.Entry<String, PackageNode> entry : nodesByPath.entrySet()) {
            String path = entry.getKey();
            PackageNode node = entry.getValue();
            int lastSep = path.lastIndexOf("::");
            if (lastSep < 0) {
                root.addChild(node);
            } else {
                PackageNode parent = nodesByPath.get(path.substring(0, lastSep));
                if (parent != null) parent.addChild(node);
                else root.addChild(node);
            }
        }
        return root;
    }
}