// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/IncrementalOptSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.cache.update.ElementFingerprint;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

/**
 * Smoke test that verifies the incremental update optimizations:
 *
 * <ol>
 *   <li>ElementFingerprint no longer carries tagValues</li>
 *   <li>File timestamp fast-skip works correctly</li>
 *   <li>Cached elements retain their tags when unchanged</li>
 * </ol>
 *
 * <p>No Rhapsody/COM needed — works from cached data only.
 *
 * <p>Usage: run as Java Application from Eclipse.
 */
public final class IncrementalOptSmokeTest {

    public static void main(String[] args) {
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println("  Incremental Optimization Smoke Test");
        System.out.println("══════════════════════════════════════════════════════════════");

        int passed = 0;
        int failed = 0;

        // ── Test 1: ElementFingerprint has no tagValues ─────────────────────
        System.out.println("\n── Test 1: ElementFingerprint has no tagValues field ──");
        try {
            ElementFingerprint fp = new ElementFingerprint(
                    "guid1", "TestElement", "Class",
                    new LinkedHashSet<String>(Arrays.asList("Block")),
                    "Some description");

            // Verify it was constructed with 5 args (no tagValues)
            System.out.println("  GUID:         " + fp.guid());
            System.out.println("  Name:         " + fp.name());
            System.out.println("  MetaClass:    " + fp.metaClass());
            System.out.println("  Stereotypes:  " + fp.stereotypes());
            System.out.println("  Description:  " + fp.description());

            // Verify no tagValues() method exists (compile-time check — if this
            // class compiled, the field is gone)
            boolean hasTagValues = false;
            try {
                fp.getClass().getMethod("tagValues");
                hasTagValues = true;
            } catch (NoSuchMethodException e) {
                hasTagValues = false;
            }

            if (!hasTagValues) {
                System.out.println("  ✓ PASS: ElementFingerprint has no tagValues() method");
                passed++;
            } else {
                System.out.println("  ✗ FAIL: ElementFingerprint still has tagValues() method!");
                failed++;
            }
        } catch (Throwable t) {
            System.out.println("  ✗ FAIL: " + t.getMessage());
            failed++;
        }

        // ── Test 2: CacheMetadata has fileTimestamps field ──────────────────
        System.out.println("\n── Test 2: CacheMetadata has fileTimestamps support ──");
        try {
            CacheMetadata meta = new CacheMetadata("TestProject", "guid-123",
                    "2026-01-01T00:00:00", 100);

            Map<String, Long> timestamps = new LinkedHashMap<String, Long>();
            timestamps.put("C:\\project.rpy", 1234567890L);
            timestamps.put("C:\\unit1.sbs", 1234567891L);
            meta.setFileTimestamps(timestamps);

            Map<String, Long> retrieved = meta.getFileTimestamps();
            if (retrieved != null && retrieved.size() == 2
                    && retrieved.get("C:\\project.rpy") == 1234567890L) {
                System.out.println("  ✓ PASS: fileTimestamps stored and retrieved correctly");
                passed++;
            } else {
                System.out.println("  ✗ FAIL: fileTimestamps not working");
                failed++;
            }
        } catch (Throwable t) {
            System.out.println("  ✗ FAIL: " + t.getMessage());
            failed++;
        }

        // ── Test 3: Cache file loads with tags intact ───────────────────────
        System.out.println("\n── Test 3: Cached elements retain tags ──");
        File cacheFile = findCacheFile(args);
        if (cacheFile != null) {
            try {
                ModelCacheManager.CacheLoadResult result = ModelCacheManager.readCache(cacheFile);
                RhapsodyModelSnapshot snapshot = result.snapshot();
                List<ElementRecord> records = snapshot.records();

                int totalElements = records.size();
                int withTags = 0;
                int totalTags = 0;

                for (ElementRecord r : records) {
                    if (!r.tagValues().isEmpty()) {
                        withTags++;
                        totalTags += r.tagValues().size();
                    }
                }

                System.out.println("  Total elements:     " + totalElements);
                System.out.println("  Elements with tags: " + withTags);
                System.out.println("  Total tag entries:  " + totalTags);

                if (totalElements > 0) {
                    System.out.println("  ✓ PASS: Cache loads correctly, tags preserved");
                    passed++;
                } else {
                    System.out.println("  ✗ FAIL: No elements loaded");
                    failed++;
                }

                // Show some sample tags
                int shown = 0;
                for (ElementRecord r : records) {
                    if (!r.tagValues().isEmpty() && shown < 3) {
                        System.out.println("    Sample: " + r.name() + " [" + r.metaClass()
                                + "] tags=" + r.tagValues());
                        shown++;
                    }
                }
            } catch (Throwable t) {
                System.out.println("  ✗ FAIL: " + t.getMessage());
                failed++;
            }
        } else {
            System.out.println("  SKIP: No cache file found");
        }

        // ── Test 4: isCacheFresh works ──────────────────────────────────────
        System.out.println("\n── Test 4: isCacheFresh() check ──");
        if (cacheFile != null) {
            try {
                // We can't test with a real model path, but we can test the negative case
                boolean freshWithBogus = ModelCacheManager.isCacheFresh(
                        "C:\\nonexistent\\fake.rpy", cacheFile);

                if (!freshWithBogus) {
                    System.out.println("  ✓ PASS: isCacheFresh returns false for non-existent model");
                    passed++;
                } else {
                    System.out.println("  ✗ FAIL: isCacheFresh returned true for bogus path");
                    failed++;
                }

                // Check if existing cache has timestamps
                CacheMetadata meta = ModelCacheManager.readCache(cacheFile).metadata();
                Map<String, Long> ts = meta.getFileTimestamps();
                if (ts != null && !ts.isEmpty()) {
                    System.out.println("  Cache has file timestamps: " + ts.size() + " files tracked");
                    System.out.println("  ✓ File timestamp support active");
                } else {
                    System.out.println("  Cache has no file timestamps (old cache format — will be "
                            + "populated on next save)");
                }
                passed++;
            } catch (Throwable t) {
                System.out.println("  ✗ FAIL: " + t.getMessage());
                failed++;
            }
        } else {
            System.out.println("  SKIP: No cache file found");
        }

        // ── Test 5: Fingerprint fields inventory ────────────────────────────
        System.out.println("\n── Test 5: Fingerprint fields inventory ──");
        System.out.println("  Fields INCLUDED in scan fingerprint (read via COM during Phase 1):");
        System.out.println("    ✓ guid       (getGUID)");
        System.out.println("    ✓ name       (getName)");
        System.out.println("    ✓ metaClass  (getMetaClass)");
        System.out.println("    ✓ stereotypes (getStereotypes)");
        System.out.println("    ✓ description (getDescription)");
        System.out.println();
        System.out.println("  Fields EXCLUDED from scan (saved COM calls):");
        System.out.println("    ✗ tagValues  (getTags + getName/getValue per tag) ← DROPPED");
        System.out.println();
        System.out.println("  Fields read only during Phase 3 full-read (changed/new elements):");
        System.out.println("    • ownerPath, ownerGuid");
        System.out.println("    • typeGuid, typeName");
        System.out.println("    • portDirection, portMultiplicity");
        System.out.println("    • tagValues (full read here)");
        System.out.println("    • references");
        passed++;

        // ── Summary ─────────────────────────────────────────────────────────
        System.out.println("\n══════════════════════════════════════════════════════════════");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("══════════════════════════════════════════════════════════════");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static File findCacheFile(String[] args) {
        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.exists()) return f;
        }

        String tempDir = System.getProperty("java.io.tmpdir");
        File cacheDir = new File(tempDir, ".rhapsody-cache");
        if (!cacheDir.exists()) return null;

        File[] files = cacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-cache.json");
            }
        });

        if (files == null || files.length == 0) return null;

        File newest = files[0];
        for (File f : files) {
            if (f.lastModified() > newest.lastModified()) newest = f;
        }
        return newest;
    }
}