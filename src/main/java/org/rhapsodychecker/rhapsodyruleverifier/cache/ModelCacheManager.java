// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/ModelCacheManager.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Reads and writes model cache to/from JSON files.
 * Converts between ElementRecord/RelationInfo and their cached representations.
 *
 * <p><b>Cache identity.</b> The cache file name is derived from a hash of the
 * model's canonical path, not just its base name. Two different projects both
 * called {@code System.rpy} used to collide on one temp file and silently
 * overwrite each other; they now get distinct cache files.
 *
 * <p><b>Crash safety.</b> Writes go to a sibling {@code .tmp} file which is
 * atomically moved into place only after serialization succeeded. A cancelled
 * or crashed run can therefore never leave a half-written cache that later
 * fails to parse.
 */
public final class ModelCacheManager {

    // INDENT_OUTPUT removed: this file is a machine-only cache (written and read only by
    // this class, never hand-edited), so pretty-printing only adds CPU on every write/read
    // and inflates the file on disk. If you want a human-readable dump for debugging,
    // add a separate debug export method that enables INDENT_OUTPUT locally.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CACHE_DIR_NAME = ".rhapsody-cache";
    private static final String CACHE_SUFFIX = "-cache.json";

    private ModelCacheManager() {}

    // ══════════════════════════════════════════════════════════════════════
    // Write
    // ══════════════════════════════════════════════════════════════════════

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
        writeCache(snapshot, projectName, projectGuid, cacheFile, modelPath, null, true);
    }

    /**
     * Write snapshot to a cache file with full freshness metadata.
     *
     * @param saveUnitTimestamps Rhapsody save-unit markers (may be null/empty when unavailable)
     * @param snapshotComplete   false when the producing scan was known to be incomplete
     */
    public static void writeCache(RhapsodyModelSnapshot snapshot,
                                  String projectName, String projectGuid,
                                  File cacheFile, String modelPath,
                                  Map<String, String> saveUnitTimestamps,
                                  boolean snapshotComplete) throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());

        CacheMetadata metadata = new CacheMetadata(
                projectName, projectGuid,
                timestamp,
                snapshot.records().size());

        metadata.setSnapshotComplete(snapshotComplete);

        // Capture model file timestamps for fast-skip detection
        if (modelPath != null) {
            metadata.setFileTimestamps(captureFileTimestamps(modelPath));
            metadata.setCanonicalModelPath(canonicalPath(modelPath));
        }

        if (saveUnitTimestamps != null && !saveUnitTimestamps.isEmpty()) {
            metadata.setSaveUnitTimestamps(new LinkedHashMap<String, String>(saveUnitTimestamps));
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

        writeAtomically(cache, cacheFile);
    }

    /**
     * Serialize to a temp file next to the target, then atomically move it into
     * place. If anything fails mid-write the previous (valid) cache survives
     * untouched and the partial file is deleted.
     */
    private static void writeAtomically(ModelCache cache, File cacheFile) throws IOException {
        File parent = cacheFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        // Unique temp name so two concurrent processes can't clobber each other's
        // in-progress write (the final move is still last-writer-wins, but neither
        // process can ever observe a torn file).
        File tmp = new File(parent, cacheFile.getName() + "." + UUID.randomUUID() + ".tmp");

        try {
            MAPPER.writeValue(tmp, cache);

            try {
                Files.move(tmp.toPath(), cacheFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicUnsupported) {
                // Some filesystems (certain network shares) reject ATOMIC_MOVE.
                // A plain replace is still far better than writing in place.
                Files.move(tmp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Read
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Read cache from file and reconstruct a snapshot (without IRPModelElement handles).
     *
     * @throws CacheReadException with a specific {@link CacheStatus} when the cache
     *         exists but is unusable (corrupt / wrong version)
     */
    public static CacheLoadResult readCache(File cacheFile) throws IOException {
        return readCache(cacheFile, null);
    }

    /**
     * Read cache and, when {@code modelPath} is provided, additionally verify the
     * cache actually belongs to that model.
     *
     * @param modelPath model whose cache this is expected to be; null skips the check
     */
    public static CacheLoadResult readCache(File cacheFile, String modelPath) throws IOException {
        ModelCache cache = parseCache(cacheFile);
        verifyVersion(cache, cacheFile);
        verifyIdentity(cache.getMetadata(), modelPath, cacheFile);

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
        ModelCache cache = parseCache(cacheFile);
        verifyVersion(cache, cacheFile);
        return cache;
    }

    /**
     * Read <i>only</i> the metadata block, without materializing elements,
     * relations or references.
     *
     * <p>The previous "fast" freshness check deserialized the whole cache — on a
     * large model that meant parsing tens of MB just to look at a handful of
     * fields. This streams tokens and stops as soon as the metadata object has
     * been consumed.
     *
     * @return metadata, or null if the file has no metadata block
     */
    public static CacheMetadata readMetadataOnly(File cacheFile) throws IOException {
        JsonParser parser = null;
        try {
            parser = MAPPER.getFactory().createParser(cacheFile);

            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new CacheReadException(CacheStatus.CACHE_CORRUPT,
                        "Cache file is not a JSON object: " + cacheFile.getAbsolutePath());
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.getCurrentName();
                parser.nextToken(); // advance to the value
                if ("metadata".equals(field)) {
                    return MAPPER.readValue(parser, CacheMetadata.class);
                }
                // Skip whole element/relation/reference arrays without building them
                parser.skipChildren();
            }
            return null;
        } catch (CacheReadException e) {
            throw e;
        } catch (IOException e) {
            throw new CacheReadException(CacheStatus.CACHE_CORRUPT,
                    "Cache metadata is unreadable: " + e.getMessage(), e);
        } finally {
            if (parser != null) {
                try { parser.close(); } catch (IOException ignored) { /* best effort */ }
            }
        }
    }

    private static ModelCache parseCache(File cacheFile) throws IOException {
        try {
            ModelCache cache = MAPPER.readValue(cacheFile, ModelCache.class);
            if (cache == null || cache.getMetadata() == null) {
                throw new CacheReadException(CacheStatus.CACHE_CORRUPT,
                        "Cache file has no metadata block: " + cacheFile.getAbsolutePath());
            }
            if (cache.getElements() == null) {
                throw new CacheReadException(CacheStatus.CACHE_CORRUPT,
                        "Cache file has no element list: " + cacheFile.getAbsolutePath());
            }
            return cache;
        } catch (CacheReadException e) {
            throw e;
        } catch (IOException e) {
            // Truncated / malformed JSON, typically from an interrupted legacy write
            throw new CacheReadException(CacheStatus.CACHE_CORRUPT,
                    "Cache file could not be parsed: " + e.getMessage(), e);
        }
    }

    private static void verifyVersion(ModelCache cache, File cacheFile) throws CacheReadException {
        int version = cache.getMetadata().getCacheVersion();
        if (version != CacheMetadata.CURRENT_VERSION) {
            throw new CacheReadException(CacheStatus.CACHE_INVALID_VERSION,
                    "Cache version mismatch: expected " + CacheMetadata.CURRENT_VERSION
                            + ", got " + version + " (" + cacheFile.getName() + ")");
        }
    }

    private static void verifyIdentity(CacheMetadata metadata, String modelPath, File cacheFile)
            throws CacheReadException {
        if (modelPath == null) return;

        String cached = metadata.getCanonicalModelPath();
        if (cached == null || cached.isEmpty()) {
            // Written before identity tracking existed; nothing to compare against.
            return;
        }

        String current = canonicalPath(modelPath);
        if (!cached.equals(current)) {
            throw new CacheReadException(CacheStatus.CACHE_INVALID_IDENTITY,
                    "Cache belongs to a different model.\n  Cache: " + cached
                            + "\n  Requested: " + current
                            + "\n  File: " + cacheFile.getName());
        }
    }

    /**
     * Check if a cache file exists and is readable.
     */
    public static boolean cacheExists(File cacheFile) {
        return cacheFile.exists() && cacheFile.isFile() && cacheFile.canRead();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Freshness helpers
    // ══════════════════════════════════════════════════════════════════════

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
     * <p>Note this only reflects <i>on-disk</i> state. Rhapsody keeps unsaved edits
     * in memory, so a "fresh" answer here does not prove the live model matches —
     * that requires the project/save-unit checks performed by the load service.
     *
     * @param modelPath the .rpy file path
     * @param cacheFile the cache file to check
     * @return true if cache is fresh and can be reused without COM scan
     */
    public static boolean isCacheFresh(String modelPath, File cacheFile) {
        if (!cacheExists(cacheFile)) return false;
        try {
            CacheMetadata metadata = readMetadataOnly(cacheFile);
            if (metadata == null) return false;
            if (metadata.getCacheVersion() != CacheMetadata.CURRENT_VERSION) return false;
            if (!metadata.isSnapshotComplete()) return false;

            Map<String, Long> storedTimestamps = metadata.getFileTimestamps();
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
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Compare freshly read save-unit markers against those stored in the cache.
     *
     * @return the unit paths whose marker differs (added, removed or changed).
     *         Empty means every unit still matches. Never null.
     */
    public static List<String> changedSaveUnits(CacheMetadata metadata,
                                                Map<String, String> currentMarkers) {
        List<String> changed = new ArrayList<String>();
        if (metadata == null || currentMarkers == null) return changed;

        Map<String, String> stored = metadata.getSaveUnitTimestamps();
        if (stored == null) stored = Collections.emptyMap();

        for (Map.Entry<String, String> entry : currentMarkers.entrySet()) {
            String storedValue = stored.get(entry.getKey());
            if (storedValue == null || !storedValue.equals(entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        for (String storedKey : stored.keySet()) {
            if (!currentMarkers.containsKey(storedKey)) {
                changed.add(storedKey);
            }
        }
        return changed;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Cache file location
    // ══════════════════════════════════════════════════════════════════════

    /** The directory holding all cache files. */
    public static File cacheDir() {
        String tempDir = System.getProperty("java.io.tmpdir");
        return new File(tempDir, CACHE_DIR_NAME);
    }

    /**
     * Get the cache file path for a given model path.
     * Stored in the current user's temp directory to avoid polluting the model directory.
     *
     * <p>The name includes a short hash of the model's canonical path so that two
     * models sharing a base name (e.g. {@code System.rpy} in two different
     * folders) do not share — and overwrite — one cache file.
     *
     * <p><b>Both</b> the readable prefix and the hash are derived from the same
     * canonical path. Deriving the prefix from the caller's raw string instead
     * would reintroduce the very collision this method exists to prevent:
     * {@code System.rpy} and {@code SYSTEM.RPY} hash identically but would yield
     * two differently-named cache files, so one model would silently end up with
     * two divergent caches. The name is therefore lowercase — readability is
     * worth less here than the guarantee that it is a deterministic function of
     * the model.
     *
     * <p>e.g. {@code %TEMP%\.rhapsody-cache\system-3f9a1c22-cache.json}
     */
    public static File defaultCacheFile(String modelPath) {
        String canonical = canonicalPath(modelPath);
        String baseName = sanitize(stripExtension(new File(canonical).getName()));
        String hash = shortHash(canonical);
        return new File(cacheDir(), baseName + "-" + hash + CACHE_SUFFIX);
    }

    /**
     * The pre-hash cache name ({@code &lt;base-name&gt;-cache.json}) used by
     * earlier builds. Kept only so an existing cache can still be found once;
     * the next successful write lands on the new unique path.
     */
    public static File legacyCacheFile(String modelPath) {
        String baseName = stripExtension(new File(modelPath).getName());
        return new File(cacheDir(), baseName + CACHE_SUFFIX);
    }

    /**
     * Resolve which cache file to read for this model: the unique one if it
     * exists, otherwise a legacy-named one, otherwise the unique path (so
     * callers can use it as the write target).
     */
    public static File resolveCacheFile(String modelPath) {
        File preferred = defaultCacheFile(modelPath);
        if (cacheExists(preferred)) return preferred;

        File legacy = legacyCacheFile(modelPath);
        if (cacheExists(legacy)) return legacy;

        return preferred;
    }

    /** Canonical, case-normalized absolute path used as the model's identity. */
    public static String canonicalPath(String path) {
        if (path == null) return "";
        File f = new File(path);
        String resolved;
        try {
            resolved = f.getCanonicalPath();
        } catch (IOException e) {
            resolved = f.getAbsolutePath();
        }
        // Windows paths are case-insensitive; normalize so C:\M.rpy and c:\m.rpy
        // are recognized as the same model instead of producing two caches.
        return resolved.replace('/', '\\').toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String fileName) {
        return fileName.replaceAll("\\.[^.]+$", "");
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /** First 8 hex chars of the SHA-256 of the input — enough to separate models. */
    private static String shortHash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // Never fail cache naming over a hashing problem
            return String.format("%08x", input.hashCode());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Conversion helpers
    // ══════════════════════════════════════════════════════════════════════

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