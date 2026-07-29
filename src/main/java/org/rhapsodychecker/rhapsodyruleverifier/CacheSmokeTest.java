// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/CacheSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

/**
 * Smoke test: loads a model from Rhapsody (live), writes to cache, reads back,
 * runs evaluation on both snapshots, and compares results.
 *
 * Run from Eclipse with the Rhapsody native library on the path.
 */
public class CacheSmokeTest {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        String yamlPath = "src/main/resources/PPTTest.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        System.out.println("=== Cache vs Live Comparison Test ===\n");

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // ── Phase 1: Load from Rhapsody (live) ──
            System.out.println("[Phase 1] Loading from Rhapsody...");
            conn.connect(rpyxPath);
            String projectName = conn.getProject().getName();
            String projectGuid = conn.getProject().getGUID();

            RhapsodyModelSnapshot liveSnapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            System.out.println("  Live elements: " + liveSnapshot.records().size());
            System.out.println("  Live relations indexed: " + liveSnapshot.relationsByOwner().size() + " owners");

            // Print a few sample tags to verify pre-loading works
            int taggedCount = 0;
            for (ElementRecord r : liveSnapshot.records()) {
                if (!r.tagValues().isEmpty()) {
                    taggedCount++;
                    if (taggedCount <= 3) {
                        System.out.println("  Sample tags on '" + r.name() + "': " + r.tagValues());
                    }
                }
            }
            System.out.println("  Elements with tags: " + taggedCount);

            // ── Phase 2: Write cache ──
            File cacheFile = ModelCacheManager.defaultCacheFile(rpyxPath);
            System.out.println("\n[Phase 2] Writing cache to: " + cacheFile.getAbsolutePath());
            ModelCacheManager.writeCache(liveSnapshot, projectName, projectGuid, cacheFile);
            System.out.println("  Cache size: " + (cacheFile.length() / 1024) + " KB");

            // ── Phase 3: Read cache ──
            System.out.println("\n[Phase 3] Reading from cache...");
            ModelCacheManager.CacheLoadResult cacheResult = ModelCacheManager.readCache(cacheFile);
            RhapsodyModelSnapshot cachedSnapshot = cacheResult.snapshot();
            System.out.println("  Cached elements: " + cachedSnapshot.records().size());
            System.out.println("  Cached at: " + cacheResult.metadata().getCachedAt());

            // ── Phase 4: Compare element counts ──
            System.out.println("\n[Phase 4] Comparing element data...");
            

            if (liveSnapshot.records().size() != cachedSnapshot.records().size()) {
                System.out.println("  [MISMATCH] Element count: live=" + liveSnapshot.records().size()
                        + " cached=" + cachedSnapshot.records().size());
                
            } else {
                System.out.println("  [MATCH] Element count: " + liveSnapshot.records().size());
            }

            // Compare elements field by field
            int compared = 0;
            int mismatches = 0;
            int limit = Math.min(liveSnapshot.records().size(), cachedSnapshot.records().size());
            for (int i = 0; i < limit; i++) {
                ElementRecord live = liveSnapshot.records().get(i);
                ElementRecord cached = cachedSnapshot.records().get(i);
                List<String> diffs = compareElements(live, cached);
                if (!diffs.isEmpty()) {
                    if (mismatches < 5) {
                        System.out.println("  [MISMATCH] Element '" + live.name() + "': " + diffs);
                    }
                    mismatches++;
                }
                compared++;
            }
            System.out.println("  Compared " + compared + " elements, " + mismatches + " mismatches");

            // ── Phase 5: Run evaluation on both and compare results ──
            System.out.println("\n[Phase 5] Running evaluation on both snapshots...");
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));

            // Live evaluation
            ElementIndex liveIndex = ElementIndex.build(liveSnapshot.records());
            RhapsodyAliasResolver liveResolver = new RhapsodyAliasResolver(config, liveSnapshot);
            ElementSelector liveSelector = new ElementSelector(liveIndex, config);
            RhapsodyEvaluationContext liveContext = new RhapsodyEvaluationContext(
                    liveResolver, liveSnapshot, config, liveIndex, liveSelector);
            RuleEngine liveEngine = new RuleEngine(config, liveSelector, liveContext);
            List<RuleResult> liveResults = liveEngine.evaluateAll();

            // Cached evaluation
            ElementIndex cachedIndex = ElementIndex.build(cachedSnapshot.records());
            RhapsodyAliasResolver cachedResolver = new RhapsodyAliasResolver(config, cachedSnapshot);
            ElementSelector cachedSelector = new ElementSelector(cachedIndex, config);
            RhapsodyEvaluationContext cachedContext = new RhapsodyEvaluationContext(
                    cachedResolver, cachedSnapshot, config, cachedIndex, cachedSelector);
            RuleEngine cachedEngine = new RuleEngine(config, cachedSelector, cachedContext);
            List<RuleResult> cachedResults = cachedEngine.evaluateAll();

            // Compare results
            System.out.println("\n  Live results:   " + liveResults.size());
            System.out.println("  Cached results: " + cachedResults.size());

            Map<String, long[]> liveByRule = countByRule(liveResults);
            Map<String, long[]> cachedByRule = countByRule(cachedResults);

            System.out.println("\n  Per-rule comparison (Pass/Fail/Skip):");
            System.out.printf("  %-40s %15s %15s %s%n", "Rule", "Live", "Cached", "Match?");
            System.out.println("  " + repeat("-", 85));

            Set<String> allRules = new TreeSet<String>();
            allRules.addAll(liveByRule.keySet());
            allRules.addAll(cachedByRule.keySet());

            boolean allMatch = true;
            for (String ruleId : allRules) {
                long[] lc = liveByRule.get(ruleId);
                if (lc == null) lc = new long[]{0, 0, 0};
                long[] cc = cachedByRule.get(ruleId);
                if (cc == null) cc = new long[]{0, 0, 0};
                boolean ruleMatch = Arrays.equals(lc, cc);
                if (!ruleMatch) allMatch = false;
                System.out.printf("  %-40s %4d/%4d/%4d %4d/%4d/%4d %s%n",
                        ruleId, lc[0], lc[1], lc[2], cc[0], cc[1], cc[2],
                        ruleMatch ? "[MATCH]" : "[MISMATCH]");
            }

            System.out.println("\n" + (allMatch ? "[PASS] All rules match!" : "[FAIL] Some rules differ"));

        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }

    private static List<String> compareElements(ElementRecord live, ElementRecord cached) {
        List<String> diffs = new ArrayList<String>();
        if (!live.guid().equals(cached.guid())) diffs.add("guid");
        if (!live.name().equals(cached.name())) diffs.add("name");
        if (!live.metaClass().equals(cached.metaClass())) diffs.add("metaClass");
        if (live.kind() != cached.kind()) diffs.add("kind");
        if (!Objects.equals(live.description().orElse(null), cached.description().orElse(null))) diffs.add("description");
        if (!Objects.equals(live.ownerPath().orElse(null), cached.ownerPath().orElse(null))) diffs.add("ownerPath");
        if (!live.stereotypes().equals(cached.stereotypes())) diffs.add("stereotypes");
        if (!Objects.equals(live.portDirection().orElse(null), cached.portDirection().orElse(null))) diffs.add("portDirection");
        if (!Objects.equals(live.portMultiplicity().orElse(null), cached.portMultiplicity().orElse(null))) diffs.add("portMultiplicity");
        if (!live.tagValues().equals(cached.tagValues())) diffs.add("tagValues");
        return diffs;
    }

    private static Map<String, long[]> countByRule(List<RuleResult> results) {
        Map<String, long[]> counts = new LinkedHashMap<String, long[]>();
        for (RuleResult r : results) {
            long[] c = counts.get(r.ruleId());
            if (c == null) {
                c = new long[]{0, 0, 0};
                counts.put(r.ruleId(), c);
            }
            switch (r.status()) {
                case PASS: c[0]++; break;
                case FAIL: c[1]++; break;
                case SKIPPED: c[2]++; break;
            }
        }
        return counts;
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }
}