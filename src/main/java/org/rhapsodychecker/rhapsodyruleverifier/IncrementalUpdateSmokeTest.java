// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/IncrementalUpdateSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.cache.*;
import org.rhapsodychecker.rhapsodyruleverifier.cache.update.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import java.io.File;
import java.util.*;

/**
 * Smoke test that reproduces the cold-start incremental update scenario:
 *
 * 1. Full load from Rhapsody → write cache
 * 2. Read cache back (simulates app restart + "Load from Cache")
 * 3. Fresh connection → incremental update against that cache (cold start)
 * 4. Detailed diagnostics: what was removed/added/changed and why
 *
 * Run from Eclipse with the Rhapsody native library on the path.
 */
public class IncrementalUpdateSmokeTest {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        if (args.length >= 1) rpyxPath = args[0];

        System.out.println("=== Incremental Update Cold-Start Smoke Test ===\n");
        File cacheFile = ModelCacheManager.defaultCacheFile(rpyxPath);

        try {
            // ── Step 1: Full load from Rhapsody ─────────────────────────────
            System.out.println("[Step 1] Full load from Rhapsody...");
            RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
            conn.connect(rpyxPath);

            RhapsodyModelLoader loader = new RhapsodyModelLoader();
            RhapsodyModelSnapshot fullSnapshot = loader.loadModel(conn.getProject());

            String projectName = conn.getProject().getName();
            String projectGuid = conn.getProject().getGUID();

            System.out.println("  Full load elements: " + fullSnapshot.records().size());
            printKindBreakdown("  Full load", fullSnapshot.records());

            // Count Parts specifically
            int fullParts = countByMetaClass(fullSnapshot.records(), "Object");
            System.out.println("  Parts (metaClass=Object): " + fullParts);

            // Write cache
            ModelCacheManager.writeCache(fullSnapshot, projectName, projectGuid, cacheFile);
            System.out.println("  Cache written: " + cacheFile.getAbsolutePath());
            System.out.println("  Cache size: " + (cacheFile.length() / 1024) + " KB");

            // ── Step 2: Read cache back ─────────────────────────────────────
            System.out.println("\n[Step 2] Reading cache back (simulating app restart)...");
            ModelCache rawCache = ModelCacheManager.readCacheRaw(cacheFile);
            System.out.println("  Cached elements: " + rawCache.getElements().size());

            // Count Parts in cache
            int cachedParts = 0;
            for (CachedElement ce : rawCache.getElements()) {
                if ("Object".equals(ce.getMetaClass())) cachedParts++;
            }
            System.out.println("  Cached Parts (metaClass=Object): " + cachedParts);

            // Build GUID set from cache for comparison
            Set<String> cachedGuids = new LinkedHashSet<String>();
            for (CachedElement ce : rawCache.getElements()) {
                cachedGuids.add(ce.getGuid());
            }

            // ── Step 3: Quick scan (Phase 1 only — no full update) ──────────
            // This simulates what IncrementalCacheUpdater does in Phase 1
            System.out.println("\n[Step 3] Phase 1 scan: getNestedElementsRecursive()...");

            IRPProject project = conn.getProject();
            IRPCollection all = RhapsodyModelLoader.safeGetNestedElementsRecursive(project);
            int scanCount = all != null ? all.getCount() : 0;
            System.out.println("  getNestedElementsRecursive() returned: " + scanCount + " elements");

            Set<String> scannedGuids = new LinkedHashSet<String>();
            Map<String, String> scannedMetaClasses = new LinkedHashMap<String, String>();
            int scannedParts = 0;

            for (int i = 1; i <= scanCount; i++) {
                IRPModelElement elt = RhapsodyModelLoader.safeGetItem(all, i);
                if (elt == null) continue;
                String guid = RhapsodyModelLoader.safeStr(elt.getGUID());
                String name = RhapsodyModelLoader.safeStr(elt.getName());
                if (guid.isEmpty() || name.isEmpty()) continue;
                String metaClass = RhapsodyModelLoader.safeStr(elt.getMetaClass());
                scannedGuids.add(guid);
                scannedMetaClasses.put(guid, metaClass);
                if ("Object".equals(metaClass)) scannedParts++;
            }
            System.out.println("  Valid scanned GUIDs: " + scannedGuids.size());
            System.out.println("  Scanned Parts (metaClass=Object): " + scannedParts);

            // ── Step 3b: Phase 1b Parts second pass ─────────────────────────
            System.out.println("\n[Step 3b] Phase 1b: Parts second pass on Blocks...");
            int blockCount = 0;
            int partsFromBlocks = 0;

            for (String guid : new ArrayList<String>(scannedGuids)) {
                String mc = scannedMetaClasses.get(guid);
                if (!"Class".equals(mc)) continue;

                // We can't check stereotypes easily here without reading them,
                // so let's just try getNestedElements() on every Class
                // (In the real code we check Block/InterfaceBlock stereotypes)
                IRPModelElement elt = null;
                // Find the handle — we need to re-get it
                for (int i = 1; i <= scanCount; i++) {
                    IRPModelElement candidate = RhapsodyModelLoader.safeGetItem(all, i);
                    if (candidate != null && guid.equals(RhapsodyModelLoader.safeStr(candidate.getGUID()))) {
                        elt = candidate;
                        break;
                    }
                }
                if (elt == null || !(elt instanceof IRPClassifier)) continue;

                blockCount++;
                try {
                    IRPCollection nested = ((IRPClassifier) elt).getNestedElements();
                    if (nested == null) continue;
                    for (int ni = 1; ni <= nested.getCount(); ni++) {
                        Object no = nested.getItem(ni);
                        if (!(no instanceof IRPModelElement)) continue;
                        IRPModelElement partElt = (IRPModelElement) no;
                        String partMeta = RhapsodyModelLoader.safeStr(partElt.getMetaClass());
                        if (!"Object".equals(partMeta)) continue;
                        String partGuid = RhapsodyModelLoader.safeStr(partElt.getGUID());
                        if (partGuid.isEmpty()) continue;
                        if (!scannedGuids.contains(partGuid)) {
                            scannedGuids.add(partGuid);
                            partsFromBlocks++;
                        }
                    }
                } catch (Throwable t) { /* ignore */ }
            }
            System.out.println("  Checked " + blockCount + " Class elements");
            System.out.println("  New Parts found in blocks: " + partsFromBlocks);
            System.out.println("  Total scanned after Phase 1b: " + scannedGuids.size());

            // ── Step 4: Diff ────────────────────────────────────────────────
            System.out.println("\n[Step 4] Diff: scanned vs cached...");

            Set<String> missingFromScan = new LinkedHashSet<String>();
            for (String cachedGuid : cachedGuids) {
                if (!scannedGuids.contains(cachedGuid)) {
                    missingFromScan.add(cachedGuid);
                }
            }

            Set<String> newInScan = new LinkedHashSet<String>();
            for (String scannedGuid : scannedGuids) {
                if (!cachedGuids.contains(scannedGuid)) {
                    newInScan.add(scannedGuid);
                }
            }

            System.out.println("  In cache but NOT in scan (would be 'removed'): " + missingFromScan.size());
            System.out.println("  In scan but NOT in cache (would be 'new'): " + newInScan.size());

            // Analyze what's being removed
            if (!missingFromScan.isEmpty()) {
                Map<String, Integer> removedByMeta = new LinkedHashMap<String, Integer>();
                Map<String, Integer> removedByKind = new LinkedHashMap<String, Integer>();
                for (String guid : missingFromScan) {
                    for (CachedElement ce : rawCache.getElements()) {
                        if (guid.equals(ce.getGuid())) {
                            String meta = ce.getMetaClass() != null ? ce.getMetaClass() : "null";
                            Integer mc = removedByMeta.get(meta);
                            removedByMeta.put(meta, mc != null ? mc + 1 : 1);

                            String kind = ce.getKind() != null ? ce.getKind() : "null";
                            Integer kc = removedByKind.get(kind);
                            removedByKind.put(kind, kc != null ? kc + 1 : 1);
                            break;
                        }
                    }
                }
                System.out.println("\n  'Removed' elements by metaClass:");
                for (Map.Entry<String, Integer> e : removedByMeta.entrySet()) {
                    System.out.println("    " + e.getKey() + ": " + e.getValue());
                }
                System.out.println("  'Removed' elements by kind:");
                for (Map.Entry<String, Integer> e : removedByKind.entrySet()) {
                    System.out.println("    " + e.getKey() + ": " + e.getValue());
                }

                // Print first 10 removed element names
                System.out.println("\n  First 10 'removed' elements:");
                int shown = 0;
                for (String guid : missingFromScan) {
                    if (shown >= 10) break;
                    for (CachedElement ce : rawCache.getElements()) {
                        if (guid.equals(ce.getGuid())) {
                            System.out.println("    " + ce.getName() + " [" + ce.getMetaClass()
                                    + "/" + ce.getKind() + "] guid=" + guid);
                            break;
                        }
                    }
                    shown++;
                }
            }

            // ── Step 5: Run full incremental update ─────────────────────────
            System.out.println("\n[Step 5] Running full IncrementalCacheUpdater...");
            IncrementalCacheUpdater updater = new IncrementalCacheUpdater(
                    project, rawCache, ProgressReporter.NOOP);
            IncrementalCacheUpdater.UpdateResult result = updater.update();
            DiffResult diff = result.diff();

            System.out.println("  Diff result: " + diff.summary());
            System.out.println("  Duration: " + result.durationMs() + " ms");
            System.out.println("  Final snapshot elements: " + result.snapshot().records().size());

            // Compare final count with full load
            if (result.snapshot().records().size() != fullSnapshot.records().size()) {
                System.out.println("  [MISMATCH] Full load=" + fullSnapshot.records().size()
                        + " Incremental=" + result.snapshot().records().size());
            } else {
                System.out.println("  [MATCH] Element counts match: " + fullSnapshot.records().size());
            }

            System.out.println("\n=== Test Complete ===");

        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static void printKindBreakdown(String prefix, List<ElementRecord> records) {
        Map<String, Integer> byKind = new LinkedHashMap<String, Integer>();
        for (ElementRecord r : records) {
            String k = r.kind().name();
            Integer c = byKind.get(k);
            byKind.put(k, c != null ? c + 1 : 1);
        }
        for (Map.Entry<String, Integer> e : byKind.entrySet()) {
            System.out.println(prefix + " " + e.getKey() + ": " + e.getValue());
        }
    }

    private static int countByMetaClass(List<ElementRecord> records, String metaClass) {
        int count = 0;
        for (ElementRecord r : records) {
            if (metaClass.equals(r.metaClass())) count++;
        }
        return count;
    }
}