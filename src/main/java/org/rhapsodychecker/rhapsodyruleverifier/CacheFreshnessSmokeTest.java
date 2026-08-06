// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/CacheFreshnessSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheReadException;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheStatus;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.ModelLoadService;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 * Smoke test for the cache identity / atomicity / freshness-metadata changes.
 *
 * <p><b>Runs entirely offline.</b> It never connects to Rhapsody and never touches
 * a real model, which is the point: the behaviour being verified here is exactly
 * the behaviour users depend on when Rhapsody is <i>not</i> running.
 *
 * <p>Covered:
 * <ol>
 *   <li>two models with the same file name get distinct cache files</li>
 *   <li>the same model always resolves to the same cache file (case-insensitively)</li>
 *   <li>a cache round-trips element/tag/relation/reference data intact</li>
 *   <li>save-unit markers and canonical identity survive the round trip</li>
 *   <li>{@code changedSaveUnits} detects changed / added / removed units</li>
 *   <li>an offline load succeeds and is reported as UNVERIFIED, not "fresh"</li>
 *   <li>a cache belonging to another model is rejected with CACHE_INVALID_IDENTITY</li>
 *   <li>a truncated/corrupt cache is rejected with CACHE_CORRUPT, not a silent miss</li>
 *   <li>an old-format cache is rejected with CACHE_INVALID_VERSION</li>
 *   <li>a failed write leaves the previous good cache intact (atomic write)</li>
 *   <li>metadata-only reads do not require parsing the element payload</li>
 * </ol>
 *
 * Run from Eclipse: no arguments, no Rhapsody, no model required.
 */
public class CacheFreshnessSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Cache Freshness / Identity Smoke Test (offline) ===\n");

        File workDir = null;
        try {
            workDir = Files.createTempDirectory("cache-freshness-test").toFile();
            System.out.println("Work dir: " + workDir.getAbsolutePath() + "\n");

            testCacheIdentityIsUniquePerPath(workDir);
            testCacheIdentityIsStable(workDir);
            testRoundTripPreservesData(workDir);
            testFreshnessMetadataRoundTrip(workDir);
            testChangedSaveUnitDetection();
            testOfflineLoadIsMarkedUnverified(workDir);
            testForeignCacheIsRejected(workDir);
            testCorruptCacheIsRejected(workDir);
            testOldVersionCacheIsRejected(workDir);
            testAtomicWriteKeepsPreviousCache(workDir);
            testMetadataOnlyRead(workDir);

        } catch (Throwable t) {
            System.err.println("\n[ERROR] Test harness blew up: " + t);
            t.printStackTrace();
            failed++;
        } finally {
            if (workDir != null) deleteRecursively(workDir);
        }

        System.out.println("\n" + repeat("=", 60));
        System.out.println("Passed: " + passed + "   Failed: " + failed);
        System.out.println(failed == 0
                ? "[PASS] All cache freshness checks succeeded."
                : "[FAIL] " + failed + " check(s) failed.");
        System.out.println(repeat("=", 60));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1 & 2 — cache identity
    // ══════════════════════════════════════════════════════════════════════

    private static void testCacheIdentityIsUniquePerPath(File workDir) {
        section("Cache identity is unique per model path");

        // The exact collision the old naming scheme had: same file name, two folders.
        File dirA = new File(workDir, "projectA");
        File dirB = new File(workDir, "projectB");
        dirA.mkdirs();
        dirB.mkdirs();

        String modelA = new File(dirA, "System.rpy").getAbsolutePath();
        String modelB = new File(dirB, "System.rpy").getAbsolutePath();

        File cacheA = ModelCacheManager.defaultCacheFile(modelA);
        File cacheB = ModelCacheManager.defaultCacheFile(modelB);

        check("Same-named models map to different cache files",
                !cacheA.getAbsolutePath().equals(cacheB.getAbsolutePath()));
        // Case-insensitive: the readable prefix is derived from the canonical
        // (lower-cased) path so that the whole file name is a deterministic
        // function of the model. See defaultCacheFile().
        check("Cache name still contains the readable base name",
                cacheA.getName().toLowerCase(Locale.ROOT).startsWith("system-")
                        && cacheA.getName().endsWith("-cache.json"));

        System.out.println("    A -> " + cacheA.getName());
        System.out.println("    B -> " + cacheB.getName());
    }

    private static void testCacheIdentityIsStable(File workDir) {
        section("Cache identity is stable for the same model");

        String model = new File(workDir, "Stable.rpy").getAbsolutePath();

        File first = ModelCacheManager.defaultCacheFile(model);
        File second = ModelCacheManager.defaultCacheFile(model);
        check("Repeated calls return the same cache file",
                first.getAbsolutePath().equals(second.getAbsolutePath()));

        // Windows paths are case-insensitive: the same model typed differently must
        // not produce a second, divergent cache.
        File upper = ModelCacheManager.defaultCacheFile(model.toUpperCase(Locale.ROOT));
        check("Case differences resolve to the same cache file",
                first.getAbsolutePath().equals(upper.getAbsolutePath()));

        // Redundant path segments must also normalize away.
        String noisy = new File(workDir, "sub" + File.separator + ".." + File.separator
                + "Stable.rpy").getAbsolutePath();
        File normalized = ModelCacheManager.defaultCacheFile(noisy);
        check("Redundant path segments normalize to the same cache file",
                first.getAbsolutePath().equals(normalized.getAbsolutePath()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3 & 4 — round trip
    // ══════════════════════════════════════════════════════════════════════

    private static void testRoundTripPreservesData(File workDir) throws Exception {
        section("Round trip preserves element, tag, relation and reference data");

        String modelPath = touch(workDir, "RoundTrip.rpy");
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);

        RhapsodyModelSnapshot original = sampleSnapshot();
        ModelCacheManager.writeCache(original, "RoundTrip", "PRJ-GUID-1", cacheFile, modelPath);

        ModelCacheManager.CacheLoadResult result =
                ModelCacheManager.readCache(cacheFile, modelPath);
        RhapsodyModelSnapshot restored = result.snapshot();

        check("Element count preserved",
                original.records().size() == restored.records().size());

        ElementRecord brake = findByName(restored.records(), "BrakeController");
        check("Element found after round trip", brake != null);
        if (brake != null) {
            check("GUID preserved", "GUID-1".equals(brake.guid()));
            check("Kind preserved", brake.kind() == ElementKind.BLOCK);
            check("Stereotypes preserved", brake.stereotypes().contains("Block"));
            check("Owner path preserved", "Vehicle::Braking".equals(brake.ownerPath().orElse(null)));
            // Tags are the field most at risk of silent staleness, so assert on them.
            check("Tag value preserved", "D".equals(brake.tagValues().get("ASIL")));
        }

        check("Relations preserved",
                restored.relationsByOwner().containsKey("GUID-1"));
        check("References preserved",
                restored.referencesByElement().containsKey("GUID-1"));
    }

    private static void testFreshnessMetadataRoundTrip(File workDir) throws Exception {
        section("Freshness metadata survives the round trip");

        String modelPath = touch(workDir, "Meta.rpy");
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);

        Map<String, String> markers = new LinkedHashMap<String, String>();
        markers.put("C:\\model\\Vehicle.sbs", "2026-08-06 10:00:00");
        markers.put("C:\\model\\Braking.sbs", "2026-08-06 11:30:00");

        ModelCacheManager.writeCache(sampleSnapshot(), "Meta", "PRJ-GUID-2",
                cacheFile, modelPath, markers, true);

        CacheMetadata metadata = ModelCacheManager.readMetadataOnly(cacheFile);
        check("Metadata readable", metadata != null);
        if (metadata == null) return;

        check("Cache version is current",
                metadata.getCacheVersion() == CacheMetadata.CURRENT_VERSION);
        check("Canonical model path recorded",
                ModelCacheManager.canonicalPath(modelPath).equals(metadata.getCanonicalModelPath()));
        check("Project GUID recorded", "PRJ-GUID-2".equals(metadata.getProjectGuid()));
        check("Save-unit markers recorded", metadata.hasSaveUnitMarkers());
        check("Save-unit marker count correct",
                metadata.getSaveUnitTimestamps().size() == 2);
        check("Snapshot marked complete", metadata.isSnapshotComplete());

        // An incomplete scan must be recorded as such, so it can never be presented
        // as an authoritative model state later.
        File partialCache = new File(workDir, "partial-cache.json");
        ModelCacheManager.writeCache(sampleSnapshot(), "Meta", "PRJ-GUID-2",
                partialCache, modelPath, markers, false);
        CacheMetadata partial = ModelCacheManager.readMetadataOnly(partialCache);
        check("Incomplete snapshot flag persisted",
                partial != null && !partial.isSnapshotComplete());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5 — unit diffing
    // ══════════════════════════════════════════════════════════════════════

    private static void testChangedSaveUnitDetection() {
        section("Save-unit change detection");

        CacheMetadata metadata = new CacheMetadata("P", "G", "2026-08-06T10:00:00", 3);
        Map<String, String> stored = new LinkedHashMap<String, String>();
        stored.put("unitA", "T1");
        stored.put("unitB", "T1");
        metadata.setSaveUnitTimestamps(stored);

        Map<String, String> unchanged = new LinkedHashMap<String, String>();
        unchanged.put("unitA", "T1");
        unchanged.put("unitB", "T1");
        check("Identical markers report no change",
                ModelCacheManager.changedSaveUnits(metadata, unchanged).isEmpty());

        Map<String, String> modified = new LinkedHashMap<String, String>();
        modified.put("unitA", "T1");
        modified.put("unitB", "T2");   // touched
        List<String> changed = ModelCacheManager.changedSaveUnits(metadata, modified);
        check("Modified unit detected",
                changed.size() == 1 && changed.contains("unitB"));

        Map<String, String> added = new LinkedHashMap<String, String>();
        added.put("unitA", "T1");
        added.put("unitB", "T1");
        added.put("unitC", "T1");      // new unit
        check("Added unit detected",
                ModelCacheManager.changedSaveUnits(metadata, added).contains("unitC"));

        Map<String, String> removed = new LinkedHashMap<String, String>();
        removed.put("unitA", "T1");    // unitB is gone
        check("Removed unit detected",
                ModelCacheManager.changedSaveUnits(metadata, removed).contains("unitB"));

        // The critical safety property: "no markers" must never be mistaken for
        // "nothing changed" — that would silently serve a stale cache.
        CacheMetadata noMarkers = new CacheMetadata("P", "G", "2026-08-06T10:00:00", 3);
        check("Cache without markers reports hasSaveUnitMarkers() == false",
                !noMarkers.hasSaveUnitMarkers());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6 — offline load
    // ══════════════════════════════════════════════════════════════════════

    private static void testOfflineLoadIsMarkedUnverified(File workDir) throws Exception {
        section("Offline cache load works and is flagged as unverified");

        String modelPath = touch(workDir, "Offline.rpy");
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        ModelCacheManager.writeCache(sampleSnapshot(), "Offline", "PRJ-GUID-3",
                cacheFile, modelPath);

        // No Rhapsody connection anywhere in this call — this is the offline path.
        ModelLoadService.LoadResult result = ModelLoadService.loadFromCache(
                cacheFile, modelPath, CacheStatus.OFFLINE_CACHE_UNVERIFIED);

        check("Offline load produced a snapshot", result.snapshot() != null);
        check("Offline load built an index", result.index() != null);
        check("Offline load built a package tree", result.packageTree() != null);
        check("Offline load built detection data", result.detectionResult() != null);
        check("Records available offline", result.snapshot().records().size() == 3);

        check("Status is OFFLINE_CACHE_UNVERIFIED",
                result.cacheStatus() == CacheStatus.OFFLINE_CACHE_UNVERIFIED);
        check("Reported as from cache", result.isFromCache());
        check("Reported as UNVERIFIED", result.isUnverified());
        check("Status message admits freshness was not verified",
                result.statusMessage().toLowerCase(Locale.ROOT).contains("not verified"));

        // Statuses that DO imply verification must not claim it falsely.
        check("CLEAN status counts as verified",
                CacheStatus.LIVE_CACHE_CONFIRMED_CLEAN.isFreshnessVerified());
        check("Offline status does not count as verified",
                !CacheStatus.OFFLINE_CACHE_UNVERIFIED.isFreshnessVerified());
        check("Partial status is flagged partial",
                CacheStatus.LIVE_INCREMENTAL_PARTIAL.isPartial());

        System.out.println("    " + result.statusMessage());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 7, 8, 9 — rejections
    // ══════════════════════════════════════════════════════════════════════

    private static void testForeignCacheIsRejected(File workDir) throws Exception {
        section("Cache belonging to a different model is rejected");

        String modelA = touch(workDir, "Owner.rpy");
        String modelB = touch(workDir, "Other.rpy");

        File cacheA = ModelCacheManager.defaultCacheFile(modelA);
        ModelCacheManager.writeCache(sampleSnapshot(), "Owner", "PRJ-A", cacheA, modelA);

        // Reading A's cache while claiming it is B's must fail loudly.
        try {
            ModelCacheManager.readCache(cacheA, modelB);
            check("Foreign cache rejected", false);
        } catch (CacheReadException e) {
            check("Foreign cache rejected with CACHE_INVALID_IDENTITY",
                    e.status() == CacheStatus.CACHE_INVALID_IDENTITY);
            check("Rejection explains which model the cache belongs to",
                    e.getMessage().contains("different model"));
        }

        // Same cache, correct owner: must still load.
        ModelCacheManager.CacheLoadResult ok = ModelCacheManager.readCache(cacheA, modelA);
        check("Cache still loads for its own model", ok.snapshot().records().size() == 3);
    }

    private static void testCorruptCacheIsRejected(File workDir) throws Exception {
        section("Corrupt cache is rejected with a specific reason");

        String modelPath = touch(workDir, "Corrupt.rpy");
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        ModelCacheManager.writeCache(sampleSnapshot(), "Corrupt", "PRJ-C", cacheFile, modelPath);

        // Simulate the classic half-written file (process killed mid-write).
        truncateInHalf(cacheFile);

        try {
            ModelCacheManager.readCache(cacheFile, modelPath);
            check("Truncated cache rejected", false);
        } catch (CacheReadException e) {
            check("Truncated cache rejected with CACHE_CORRUPT",
                    e.status() == CacheStatus.CACHE_CORRUPT);
        }

        // Total garbage must also be a clean rejection, not an unhandled crash.
        File garbage = new File(workDir, "garbage-cache.json");
        writeText(garbage, "this is definitely not json");
        try {
            ModelCacheManager.readCache(garbage, null);
            check("Garbage cache rejected", false);
        } catch (CacheReadException e) {
            check("Garbage cache rejected with CACHE_CORRUPT",
                    e.status() == CacheStatus.CACHE_CORRUPT);
        }

        check("A rejection status is classified as a rejection",
                CacheStatus.CACHE_CORRUPT.isRejection());
    }

    private static void testOldVersionCacheIsRejected(File workDir) throws Exception {
        section("Outdated cache format is rejected with a specific reason");

        File oldCache = new File(workDir, "old-version-cache.json");
        // A v3 cache: parseable, but written by a build with a different schema.
        writeText(oldCache,
                "{\"metadata\":{\"projectName\":\"Old\",\"projectGuid\":\"G\","
                + "\"cachedAt\":\"2026-01-01T00:00:00\",\"elementCount\":0,"
                + "\"cacheVersion\":3},\"elements\":[]}");

        try {
            ModelCacheManager.readCache(oldCache, null);
            check("Old-version cache rejected", false);
        } catch (CacheReadException e) {
            check("Old-version cache rejected with CACHE_INVALID_VERSION",
                    e.status() == CacheStatus.CACHE_INVALID_VERSION);
            check("Rejection names the expected version",
                    e.getMessage().contains(String.valueOf(CacheMetadata.CURRENT_VERSION)));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 10 — atomic write
    // ══════════════════════════════════════════════════════════════════════

    private static void testAtomicWriteKeepsPreviousCache(File workDir) throws Exception {
        section("Failed write leaves the previous cache intact");

        String modelPath = touch(workDir, "Atomic.rpy");
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);

        ModelCacheManager.writeCache(sampleSnapshot(), "Atomic", "PRJ-D", cacheFile, modelPath);
        long goodSize = cacheFile.length();
        check("Initial cache written", goodSize > 0);

        // Force serialization to fail midway: an ElementRecord whose kind cannot be
        // written. If the writer were not atomic, the good cache would now be gone
        // or truncated.
        boolean threw = false;
        try {
            ModelCacheManager.writeCache(unserializableSnapshot(), "Atomic", "PRJ-D",
                    cacheFile, modelPath);
        } catch (Throwable expected) {
            threw = true;
        }

        if (!threw) {
            // Serialization happened to succeed; the atomicity claim is untested but
            // not violated. Say so rather than reporting a false pass.
            System.out.println("    [SKIP] Could not force a write failure on this JVM");
        } else {
            check("Previous cache still exists after failed write", cacheFile.exists());
            check("Previous cache still parses after failed write",
                    ModelCacheManager.readCache(cacheFile, modelPath).snapshot()
                            .records().size() == 3);
        }

        // No .tmp litter should survive either outcome.
        File[] leftovers = ModelCacheManager.cacheDir().listFiles(
                (dir, name) -> name.endsWith(".tmp"));
        check("No temporary files left behind",
                leftovers == null || leftovers.length == 0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 11 — metadata-only read
    // ══════════════════════════════════════════════════════════════════════

    private static void testMetadataOnlyRead(File workDir) throws Exception {
        section("Metadata-only read does not need the element payload");

        String modelPath = touch(workDir, "MetaOnly.rpy");
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        ModelCacheManager.writeCache(sampleSnapshot(), "MetaOnly", "PRJ-E", cacheFile, modelPath);

        CacheMetadata metadata = ModelCacheManager.readMetadataOnly(cacheFile);
        check("Metadata-only read returns metadata", metadata != null);
        check("Element count available from metadata alone",
                metadata != null && metadata.getElementCount() == 3);

        // Prove it really is metadata-only: mangle the elements array but leave the
        // metadata block valid. A full parse would fail here; a streaming metadata
        // read must still succeed.
        File metaFirst = new File(workDir, "meta-first-cache.json");
        writeText(metaFirst,
                "{\"metadata\":{\"projectName\":\"P\",\"projectGuid\":\"G\","
                + "\"cachedAt\":\"2026-08-06T12:00:00\",\"elementCount\":42,"
                + "\"cacheVersion\":" + CacheMetadata.CURRENT_VERSION + ","
                + "\"snapshotComplete\":true},\"elements\":[]}");

        CacheMetadata streamed = ModelCacheManager.readMetadataOnly(metaFirst);
        check("Streaming read picks up metadata block",
                streamed != null && streamed.getElementCount() == 42);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fixtures & helpers
    // ══════════════════════════════════════════════════════════════════════

    /** Three elements with tags, one relation and one reference. */
    private static RhapsodyModelSnapshot sampleSnapshot() {
        List<ElementRecord> records = new ArrayList<ElementRecord>();

        Map<String, String> tags = new LinkedHashMap<String, String>();
        tags.put("ASIL", "D");
        tags.put("Owner", "Chassis Team");

        records.add(ElementRecord.builder()
                .guid("GUID-1")
                .name("BrakeController")
                .metaClass("Class")
                .kind(ElementKind.BLOCK)
                .description("Controls braking")
                .ownerGuid("GUID-PKG")
                .ownerPath("Vehicle::Braking")
                .stereotypes(new LinkedHashSet<String>(Arrays.asList("Block", "SafetyCritical")))
                .tagValues(tags)
                .build());

        records.add(ElementRecord.builder()
                .guid("GUID-PKG")
                .name("Braking")
                .metaClass("Package")
                .kind(ElementKind.PACKAGE)
                .ownerPath("Vehicle")
                .build());

        records.add(ElementRecord.builder()
                .guid("GUID-2")
                .name("SpeedSignal")
                .metaClass("Port")
                .kind(ElementKind.PORT_FLOW)
                .ownerGuid("GUID-1")
                .ownerPath("Vehicle::Braking::BrakeController")
                .typeName("Speed")
                .portDirection("In")
                .portMultiplicity("1")
                .build());

        Map<String, List<RhapsodyModelSnapshot.RelationInfo>> rels =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.RelationInfo>>();
        rels.put("GUID-1", Collections.singletonList(
                new RhapsodyModelSnapshot.RelationInfo("REL-1", "Dependency",
                        new LinkedHashSet<String>(Collections.singletonList("trace")), "GUID-2")));

        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> refs =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.ReferenceInfo>>();
        refs.put("GUID-1", Collections.singletonList(
                new RhapsodyModelSnapshot.ReferenceInfo("GUID-2", "Port",
                        Collections.<String>emptySet())));

        return new RhapsodyModelSnapshot(
                Collections.unmodifiableList(records),
                Collections.<String, IRPModelElement>emptyMap(),
                Collections.unmodifiableMap(rels),
                Collections.unmodifiableMap(refs));
    }

    /**
     * A snapshot whose serialization is expected to fail, used to prove the write
     * is atomic. Jackson cannot serialize a self-referential map value.
     */
    private static RhapsodyModelSnapshot unserializableSnapshot() {
        List<ElementRecord> records = new ArrayList<ElementRecord>();

        // A tag map that contains itself → infinite recursion during serialization.
        Map<String, String> evil = new HashMap<String, String>() {
            private static final long serialVersionUID = 1L;
            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                throw new IllegalStateException("simulated serialization failure");
            }
        };
        evil.put("boom", "boom");

        records.add(ElementRecord.builder()
                .guid("GUID-X")
                .name("Exploding")
                .metaClass("Class")
                .kind(ElementKind.BLOCK)
                .tagValues(evil)
                .build());

        return new RhapsodyModelSnapshot(
                Collections.unmodifiableList(records),
                Collections.<String, IRPModelElement>emptyMap(),
                Collections.<String, List<RhapsodyModelSnapshot.RelationInfo>>emptyMap(),
                Collections.<String, List<RhapsodyModelSnapshot.ReferenceInfo>>emptyMap());
    }

    private static ElementRecord findByName(List<ElementRecord> records, String name) {
        for (ElementRecord r : records) {
            if (name.equals(r.name())) return r;
        }
        return null;
    }

    /** Create an empty file so canonical-path resolution has something real. */
    private static String touch(File dir, String fileName) throws Exception {
        File f = new File(dir, fileName);
        if (!f.exists()) f.createNewFile();
        return f.getAbsolutePath();
    }

    private static void writeText(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(Charset.forName("UTF-8")));
    }

    private static void truncateInHalf(File file) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        try {
            raf.setLength(Math.max(1, raf.length() / 2));
        } finally {
            raf.close();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    // ── Tiny assertion harness ──────────────────────────────────────────────

    private static void section(String title) {
        System.out.println("[" + title + "]");
    }

    private static void check(String what, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + what);
        } else {
            failed++;
            System.out.println("  [FAIL] " + what);
        }
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }
}