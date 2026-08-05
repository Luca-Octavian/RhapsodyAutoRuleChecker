// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/ModelCacheManager.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Reads and writes model cache to/from JSON files.
 * Converts between ElementRecord/RelationInfo and their cached representations.
 */
public final class ModelCacheManager {

    // INDENT_OUTPUT removed: this file is a machine-only cache (written and read only by
    // this class, never hand-edited), so pretty-printing only adds CPU on every write/read
    // and inflates the file on disk. If you want a human-readable dump for debugging,
    // add a separate debug export method that enables INDENT_OUTPUT locally.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModelCacheManager() {}

    /**
     * Write snapshot to a cache file.
     */
    public static void writeCache(RhapsodyModelSnapshot snapshot,
                                  String projectName, String projectGuid,
                                  File cacheFile) throws IOException {
        writeCache(snapshot, projectName, projectGuid, cacheFile, null);
    }

    /**
     * Write snapshot to a cache file, optionally capturing model file timestamps.
     *
     * @param modelPath if non-null, captures .rpy and .sbs file timestamps for fast-skip
     */
    public static void writeCache(RhapsodyModelSnapshot snapshot,
                                  String projectName, String projectGuid,
                                  File cacheFile, String modelPath) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());

        CacheMetadata metadata = new CacheMetadata(
                projectName, projectGuid,
                timestamp,
                snapshot.records().size());

        // Capture model file timestamps for fast-skip detection
        if (modelPath != null) {
            metadata.setFileTimestamps(captureFileTimestamps(modelPath));
        }

        List<CachedElement> elements = new ArrayList<CachedElement>();
        for (ElementRecord r : snapshot.records()) {
            elements.add(toCachedElement(r));
        }

        Map<String, List<CachedRelation>> rels = new LinkedHashMap<String, List<CachedRelation>>();
        for (Map.Entry<String, List<RhapsodyModelSnapshot.RelationInfo>> entry
                : snapshot.relationsByOwner().entrySet()) {
            List<CachedRelation> cachedRels = new ArrayList<CachedRelation>();
            for (RhapsodyModelSnapshot.RelationInfo r : entry.getValue()) {
                cachedRels.add(new CachedRelation(r.guid(), r.metaClass(), r.stereotypes(), r.otherEndGuid()));
            }
            rels.put(entry.getKey(), cachedRels);
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

        ModelCache cache = new ModelCache(metadata, elements, rels, refs);

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

        Map<String, List<RhapsodyModelSnapshot.RelationInfo>> rels =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.RelationInfo>>();
        if (cache.getRelationsByOwner() != null) {
            for (Map.Entry<String, List<CachedRelation>> entry
                    : cache.getRelationsByOwner().entrySet()) {
                List<RhapsodyModelSnapshot.RelationInfo> infos =
                        new ArrayList<RhapsodyModelSnapshot.RelationInfo>();
                for (CachedRelation cr : entry.getValue()) {
                    infos.add(new RhapsodyModelSnapshot.RelationInfo(
                            cr.getGuid(), cr.getMetaClass(), cr.getStereotypes(), cr.getOtherEndGuid()));
                }
                rels.put(entry.getKey(), infos);
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
                Collections.unmodifiableMap(rels),
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
     * Capture file timestamps for the project and its unit files.
     * Used for fast-skip: if no file has changed, the cache is definitely fresh.
     */
    public static Map<String, Long> captureFileTimestamps(String modelPath) {
        Map<String, Long> timestamps = new LinkedHashMap<String, Long>();
        File rpyFile = new File(modelPath);
        if (rpyFile.exists()) {
            timestamps.put(rpyFile.getAbsolutePath(), rpyFile.lastModified());

            // Also capture .sbs unit files in the same directory
            File dir = rpyFile.getParentFile();
            if (dir != null && dir.isDirectory()) {
                File[] sbsFiles = dir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File d, String name) {
                        return name.endsWith(".sbs");
                    }
                });
                if (sbsFiles != null) {
                    for (File sbs : sbsFiles) {
                        timestamps.put(sbs.getAbsolutePath(), sbs.lastModified());
                    }
                }
            }
        }
        return timestamps;
    }

    /**
     * Check if model files have changed since the cache was written.
     * Returns true if the cache is still fresh (no files changed).
     *
     * @param modelPath the .rpy file path
     * @param cacheFile the cache file to check
     * @return true if cache is fresh and can be reused without COM scan
     */
    public static boolean isCacheFresh(String modelPath, File cacheFile) {
        if (!cacheExists(cacheFile)) return false;
        try {
            // Read just the metadata (fast — don't deserialize elements)
            ModelCache cache = MAPPER.readValue(cacheFile, ModelCache.class);
            if (cache.getMetadata() == null) return false;
            if (cache.getMetadata().getCacheVersion() != CacheMetadata.CURRENT_VERSION) return false;

            Map<String, Long> storedTimestamps = cache.getMetadata().getFileTimestamps();
            if (storedTimestamps == null || storedTimestamps.isEmpty()) return false;

            // Check current timestamps against stored
            Map<String, Long> currentTimestamps = captureFileTimestamps(modelPath);

            // All stored files must exist with same timestamp
            for (Map.Entry<String, Long> entry : storedTimestamps.entrySet()) {
                Long current = currentTimestamps.get(entry.getKey());
                if (current == null || !current.equals(entry.getValue())) {
                    return false;
                }
            }

            // Check for new .sbs files not in stored timestamps
            for (String currentPath : currentTimestamps.keySet()) {
                if (!storedTimestamps.containsKey(currentPath)) {
                    return false;
                }
            }

            return true;
        } catch (Throwable t) {
            return false;
        }
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
        c.setInitialValue(r.initialValue().orElse(null));
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
                .initialValue(c.getInitialValue())
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