// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/ModelCacheManager.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Reads and writes model cache to/from JSON files.
 * Converts between ElementRecord/DependencyInfo and their cached representations.
 */
public final class ModelCacheManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ModelCacheManager() {}

    /**
     * Write snapshot to a cache file.
     */
    public static void writeCache(RhapsodyModelSnapshot snapshot,
                                  String projectName, String projectGuid,
                                  File cacheFile) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());

        CacheMetadata metadata = new CacheMetadata(
                projectName, projectGuid,
                timestamp,
                snapshot.records().size());

        List<CachedElement> elements = new ArrayList<CachedElement>();
        for (ElementRecord r : snapshot.records()) {
            elements.add(toCachedElement(r));
        }

        Map<String, List<CachedDependency>> deps = new LinkedHashMap<String, List<CachedDependency>>();
        for (Map.Entry<String, List<RhapsodyModelSnapshot.DependencyInfo>> entry
                : snapshot.dependenciesByOwner().entrySet()) {
            List<CachedDependency> cachedDeps = new ArrayList<CachedDependency>();
            for (RhapsodyModelSnapshot.DependencyInfo d : entry.getValue()) {
                cachedDeps.add(new CachedDependency(d.guid(), d.stereotypes(), d.otherEndGuid()));
            }
            deps.put(entry.getKey(), cachedDeps);
        }

        // References
        Map<String, List<CachedReference>> refs = new LinkedHashMap<String, List<CachedReference>>();
        for (Map.Entry<String, List<RhapsodyModelSnapshot.ReferenceInfo>> entry
                : snapshot.referencesByElement().entrySet()) {
            List<CachedReference> cachedRefs = new ArrayList<CachedReference>();
            for (RhapsodyModelSnapshot.ReferenceInfo r : entry.getValue()) {
                cachedRefs.add(new CachedReference(r.guid(), r.metaClass(),
                        r.stereotypes().isEmpty() ? null : new LinkedHashSet<String>(r.stereotypes())));
            }
            refs.put(entry.getKey(), cachedRefs);
        }

        ModelCache cache = new ModelCache(metadata, elements, deps, refs);

        cacheFile.getParentFile().mkdirs();
        MAPPER.writeValue(cacheFile, cache);
    }

    /**
     * Read cache from file and reconstruct a snapshot (without IRPModelElement handles).
     */
    public static CacheLoadResult readCache(File cacheFile) throws IOException {
        ModelCache cache = MAPPER.readValue(cacheFile, ModelCache.class);

        if (cache.getMetadata().getCacheVersion() != CacheMetadata.CURRENT_VERSION) {
            throw new IOException("Cache version mismatch: expected "
                    + CacheMetadata.CURRENT_VERSION + ", got " + cache.getMetadata().getCacheVersion());
        }

        List<ElementRecord> records = new ArrayList<ElementRecord>();
        for (CachedElement c : cache.getElements()) {
            records.add(toElementRecord(c));
        }

        Map<String, List<RhapsodyModelSnapshot.DependencyInfo>> deps =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.DependencyInfo>>();
        if (cache.getDependenciesByOwner() != null) {
            for (Map.Entry<String, List<CachedDependency>> entry
                    : cache.getDependenciesByOwner().entrySet()) {
                List<RhapsodyModelSnapshot.DependencyInfo> infos =
                        new ArrayList<RhapsodyModelSnapshot.DependencyInfo>();
                for (CachedDependency cd : entry.getValue()) {
                    infos.add(new RhapsodyModelSnapshot.DependencyInfo(
                            cd.getGuid(), cd.getStereotypes(), cd.getOtherEndGuid()));
                }
                deps.put(entry.getKey(), infos);
            }
        }

        // References
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> refs =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.ReferenceInfo>>();
        if (cache.getReferencesByElement() != null) {
            for (Map.Entry<String, List<CachedReference>> entry
                    : cache.getReferencesByElement().entrySet()) {
                List<RhapsodyModelSnapshot.ReferenceInfo> infos =
                        new ArrayList<RhapsodyModelSnapshot.ReferenceInfo>();
                for (CachedReference cr : entry.getValue()) {
                    infos.add(new RhapsodyModelSnapshot.ReferenceInfo(
                            cr.getGuid(), cr.getMetaClass(),
                            cr.getStereotypes() != null ? cr.getStereotypes() : Collections.<String>emptySet()));
                }
                refs.put(entry.getKey(), infos);
            }
        }

        // Snapshot without handle map (no live COM objects when loading from cache)
        RhapsodyModelSnapshot snapshot = new RhapsodyModelSnapshot(
                Collections.unmodifiableList(records),
                Collections.<String, com.telelogic.rhapsody.core.IRPModelElement>emptyMap(),
                Collections.unmodifiableMap(deps),
                Collections.unmodifiableMap(refs));

        return new CacheLoadResult(snapshot, cache.getMetadata());
    }

    /**
     * Read cache from file and return the raw ModelCache (for incremental updates).
     */
    public static ModelCache readCacheRaw(File cacheFile) throws IOException {
        ModelCache cache = MAPPER.readValue(cacheFile, ModelCache.class);

        if (cache.getMetadata().getCacheVersion() != CacheMetadata.CURRENT_VERSION) {
            throw new IOException("Cache version mismatch: expected "
                    + CacheMetadata.CURRENT_VERSION + ", got " + cache.getMetadata().getCacheVersion());
        }

        return cache;
    }

    /**
     * Check if a cache file exists and is readable.
     */
    public static boolean cacheExists(File cacheFile) {
        return cacheFile.exists() && cacheFile.isFile() && cacheFile.canRead();
    }

    /**
     * Get the default cache file path for a given model path.
     * Stored in the current user's temp directory to avoid polluting the model directory.
     * e.g. C:\Users\<user>\AppData\Local\Temp\.rhapsody-cache\<ProjectName>-cache.json
     */
    public static File defaultCacheFile(String modelPath) {
        File modelFile = new File(modelPath);
        String tempDir = System.getProperty("java.io.tmpdir");
        File cacheDir = new File(tempDir, ".rhapsody-cache");
        String baseName = modelFile.getName().replaceAll("\\.[^.]+$", "");
        return new File(cacheDir, baseName + "-cache.json");
    }

    // ---- Conversion helpers ----

    private static CachedElement toCachedElement(ElementRecord r) {
        CachedElement c = new CachedElement();
        c.setGuid(r.guid());
        c.setName(r.name());
        c.setMetaClass(r.metaClass());
        c.setKind(r.kind().name());
        c.setDescription(r.description().orElse(null));
        c.setOwnerGuid(r.ownerGuid().orElse(null));
        c.setOwnerPath(r.ownerPath().orElse(null));
        c.setTypeGuid(r.typeGuid().orElse(null));
        c.setTypeName(r.typeName().orElse(null));
        c.setStereotypes(r.stereotypes().isEmpty() ? null : new LinkedHashSet<String>(r.stereotypes()));
        c.setPortDirection(r.portDirection().orElse(null));
        c.setPortMultiplicity(r.portMultiplicity().orElse(null));
        c.setTagValues(r.tagValues().isEmpty() ? null : new LinkedHashMap<String, String>(r.tagValues()));
        return c;
    }

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

    /**
     * Result of loading from cache, includes metadata for display.
     */
    public static final class CacheLoadResult {
        private final RhapsodyModelSnapshot snapshot;
        private final CacheMetadata metadata;

        public CacheLoadResult(RhapsodyModelSnapshot snapshot, CacheMetadata metadata) {
            this.snapshot = snapshot;
            this.metadata = metadata;
        }

        public RhapsodyModelSnapshot snapshot() { return snapshot; }
        public CacheMetadata metadata() { return metadata; }
    }
}