// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/OslcCacheSmokeTest.java
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
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

/**
 * Smoke test: loads model from cache (cold start) and from live Rhapsody (full reload),
 * runs all rules from PPTTest.yaml on both, and compares per-rule pass/fail/skip counts.
 *
 * <p>This definitively shows whether OSLC GUID rotation affects rule evaluation.
 *
 * <p>Run from Eclipse with the Rhapsody native library on the path.
 * Requires a running Rhapsody instance with the model open.
 */
public class OslcCacheSmokeTest {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        String yamlPath = "C:\\Users\\uik11305\\Desktop\\yaml configs\\PPTTest.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        System.out.println("=== OSLC Cache vs Live Rule Evaluation Comparison ===\n");
        System.out.println("Model: " + rpyxPath);
        System.out.println("Config: " + yamlPath);

        File cacheFile = ModelCacheManager.defaultCacheFile(rpyxPath);
        if (!ModelCacheManager.cacheExists(cacheFile)) {
            System.out.println("\nERROR: No cache file found at: " + cacheFile.getAbsolutePath());
            System.out.println("Run a full model load first to create the cache.");
            return;
        }

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // ── Phase 1: Load from cache (cold start) ───────────────────────
            System.out.println("\n[Phase 1] Loading from cache (cold start)...");
            ModelCacheManager.CacheLoadResult cacheResult = ModelCacheManager.readCache(cacheFile);
            RhapsodyModelSnapshot cachedSnapshot = cacheResult.snapshot();
            System.out.println("  Cached elements: " + cachedSnapshot.records().size());
            System.out.println("  Cached at: " + cacheResult.metadata().getCachedAt());

            int cachedOslcCount = 0;
            for (ElementRecord r : cachedSnapshot.records()) {
                if (isUrl(r.guid()) || isUrl(r.name())) cachedOslcCount++;
            }
            System.out.println("  OSLC proxy elements: " + cachedOslcCount);

            // ── Phase 2: Load from live Rhapsody (full reload) ──────────────
            System.out.println("\n[Phase 2] Loading from live Rhapsody (full reload)...");
            conn.connect(rpyxPath);
            RhapsodyModelSnapshot liveSnapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            System.out.println("  Live elements: " + liveSnapshot.records().size());

            int liveOslcCount = 0;
            for (ElementRecord r : liveSnapshot.records()) {
                if (isUrl(r.guid()) || isUrl(r.name())) liveOslcCount++;
            }
            System.out.println("  OSLC proxy elements: " + liveOslcCount);

            // ── Phase 3: Load config ────────────────────────────────────────
            System.out.println("\n[Phase 3] Loading config: " + yamlPath);
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("  Rules loaded: " + config.rules().size());

            // ── Phase 4: Run evaluation on CACHED snapshot ──────────────────
            System.out.println("\n[Phase 4] Running evaluation on CACHED snapshot...");
            ElementIndex cachedIndex = ElementIndex.build(cachedSnapshot.records());
            RhapsodyAliasResolver cachedResolver = new RhapsodyAliasResolver(config, cachedSnapshot);
            ElementSelector cachedSelector = new ElementSelector(cachedIndex, config);
            RhapsodyEvaluationContext cachedContext = new RhapsodyEvaluationContext(
                    cachedResolver, cachedSnapshot, config, cachedIndex, cachedSelector);
            RuleEngine cachedEngine = new RuleEngine(config, cachedSelector, cachedContext);
            List<RuleResult> cachedResults = cachedEngine.evaluateAll();
            System.out.println("  Cached results: " + cachedResults.size()
                    + " (FAIL: " + countStatus(cachedResults, RuleStatus.FAIL) + ")");

            // ── Phase 5: Run evaluation on LIVE snapshot ────────────────────
            System.out.println("\n[Phase 5] Running evaluation on LIVE snapshot...");
            ElementIndex liveIndex = ElementIndex.build(liveSnapshot.records());
            RhapsodyAliasResolver liveResolver = new RhapsodyAliasResolver(config, liveSnapshot);
            ElementSelector liveSelector = new ElementSelector(liveIndex, config);
            RhapsodyEvaluationContext liveContext = new RhapsodyEvaluationContext(
                    liveResolver, liveSnapshot, config, liveIndex, liveSelector);
            RuleEngine liveEngine = new RuleEngine(config, liveSelector, liveContext);
            List<RuleResult> liveResults = liveEngine.evaluateAll();
            System.out.println("  Live results: " + liveResults.size()
                    + " (FAIL: " + countStatus(liveResults, RuleStatus.FAIL) + ")");

            // ── Phase 6: Compare per-rule ───────────────────────────────────
            System.out.println("\n[Phase 6] Per-rule comparison (Pass/Fail/Skip):");

            Map<String, long[]> cachedByRule = countByRule(cachedResults);
            Map<String, long[]> liveByRule = countByRule(liveResults);

            Set<String> allRules = new TreeSet<String>();
            allRules.addAll(cachedByRule.keySet());
            allRules.addAll(liveByRule.keySet());

            System.out.printf("  %-40s %15s %15s %s%n", "Rule", "Cached", "Live", "Match?");
            System.out.println("  " + repeat("-", 90));

            boolean allMatch = true;
            for (String ruleId : allRules) {
                long[] cc = cachedByRule.get(ruleId);
                if (cc == null) cc = new long[]{0, 0, 0};
                long[] lc = liveByRule.get(ruleId);
                if (lc == null) lc = new long[]{0, 0, 0};
                boolean ruleMatch = Arrays.equals(cc, lc);
                if (!ruleMatch) allMatch = false;
                System.out.printf("  %-40s %4d/%4d/%4d %4d/%4d/%4d %s%n",
                        ruleId, cc[0], cc[1], cc[2], lc[0], lc[1], lc[2],
                        ruleMatch ? "[MATCH]" : "[MISMATCH]");
            }

            // ── Phase 7: Show differing failures ────────────────────────────
            Map<String, String> cachedNames = buildGuidToNameMap(cachedSnapshot.records());
            Map<String, String> liveNames = buildGuidToNameMap(liveSnapshot.records());

            if (!allMatch) {
                System.out.println("\n[Phase 7] Differing failures detail...");

                // Build sets of "ruleId + elementGuid" for FAIL results
                Map<String, RuleResult> cachedFails = new LinkedHashMap<String, RuleResult>();
                for (RuleResult r : cachedResults) {
                    if (r.status() == RuleStatus.FAIL) {
                        cachedFails.put(r.ruleId() + "|" + r.elementGuid(), r);
                    }
                }
                Map<String, RuleResult> liveFails = new LinkedHashMap<String, RuleResult>();
                for (RuleResult r : liveResults) {
                    if (r.status() == RuleStatus.FAIL) {
                        liveFails.put(r.ruleId() + "|" + r.elementGuid(), r);
                    }
                }

                // Failures in cached but not live
                int cachedOnlyCount = 0;
                for (Map.Entry<String, RuleResult> entry : cachedFails.entrySet()) {
                    if (!liveFails.containsKey(entry.getKey())) {
                        cachedOnlyCount++;
                        if (cachedOnlyCount <= 20) {
                            RuleResult r = entry.getValue();
                            String eName = lookupName(cachedNames, r.elementGuid());
                            System.out.println("  CACHED-ONLY FAIL: " + r.ruleId()
                                    + " | " + abbreviate(eName, 40)
                                    + " | guid=" + abbreviate(r.elementGuid(), 50)
                                    + " | " + abbreviate(r.message(), 60));
                        }
                    }
                }
                if (cachedOnlyCount > 20) {
                    System.out.println("  ... and " + (cachedOnlyCount - 20) + " more cached-only failures");
                }
                System.out.println("  Total cached-only failures: " + cachedOnlyCount);

                // Failures in live but not cached
                int liveOnlyCount = 0;
                for (Map.Entry<String, RuleResult> entry : liveFails.entrySet()) {
                    if (!cachedFails.containsKey(entry.getKey())) {
                        liveOnlyCount++;
                        if (liveOnlyCount <= 20) {
                            RuleResult r = entry.getValue();
                            String eName = lookupName(liveNames, r.elementGuid());
                            System.out.println("  LIVE-ONLY FAIL: " + r.ruleId()
                                    + " | " + abbreviate(eName, 40)
                                    + " | guid=" + abbreviate(r.elementGuid(), 50)
                                    + " | " + abbreviate(r.message(), 60));
                        }
                    }
                }
                if (liveOnlyCount > 20) {
                    System.out.println("  ... and " + (liveOnlyCount - 20) + " more live-only failures");
                }
                System.out.println("  Total live-only failures: " + liveOnlyCount);

                // Check if differing elements are OSLC proxies
                int oslcRelatedDiffs = 0;
                for (Map.Entry<String, RuleResult> entry : cachedFails.entrySet()) {
                    if (!liveFails.containsKey(entry.getKey())) {
                        RuleResult r = entry.getValue();
                        String eName = lookupName(cachedNames, r.elementGuid());
                        if (isUrl(r.elementGuid()) || isUrl(eName)) {
                            oslcRelatedDiffs++;
                        }
                    }
                }
                for (Map.Entry<String, RuleResult> entry : liveFails.entrySet()) {
                    if (!cachedFails.containsKey(entry.getKey())) {
                        RuleResult r = entry.getValue();
                        String eName = lookupName(liveNames, r.elementGuid());
                        if (isUrl(r.elementGuid()) || isUrl(eName)) {
                            oslcRelatedDiffs++;
                        }
                    }
                }
                System.out.println("\n  OSLC-related diffs: " + oslcRelatedDiffs
                        + " out of " + (cachedOnlyCount + liveOnlyCount) + " total diffs");
            }

            // ── Summary ─────────────────────────────────────────────────────
            System.out.println("\n=== SUMMARY ===");
            System.out.println("  Cached elements: " + cachedSnapshot.records().size()
                    + " (" + cachedOslcCount + " OSLC)");
            System.out.println("  Live elements:   " + liveSnapshot.records().size()
                    + " (" + liveOslcCount + " OSLC)");
            System.out.println("  Cached FAIL: " + countStatus(cachedResults, RuleStatus.FAIL));
            System.out.println("  Live FAIL:   " + countStatus(liveResults, RuleStatus.FAIL));
            System.out.println("  " + (allMatch ? "✓ All rules match!" : "⚠ Some rules differ"));

            System.out.println("\n=== DONE ===");

        } catch (Throwable t) {
            System.err.println("ERROR: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static boolean isUrl(String s) {
        return s != null && (s.startsWith("http://") || s.startsWith("https://"));
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static long countStatus(List<RuleResult> results, RuleStatus status) {
        long count = 0;
        for (RuleResult r : results) {
            if (r.status() == status) count++;
        }
        return count;
    }

    private static Map<String, long[]> countByRule(List<RuleResult> results) {
        Map<String, long[]> counts = new LinkedHashMap<String, long[]>();
        for (RuleResult r : results) {
            long[] c = counts.get(r.ruleId());
            if (c == null) {
                c = new long[]{0, 0, 0};
                counts.put(r.ruleId(), c);
            }
            if (r.status() == RuleStatus.PASS) c[0]++;
            else if (r.status() == RuleStatus.FAIL) c[1]++;
            else if (r.status() == RuleStatus.SKIPPED) c[2]++;
        }
        return counts;
    }

    private static String lookupName(Map<String, String> guidToName, String guid) {
        if (guid == null) return "(null)";
        String name = guidToName.get(guid);
        return name != null ? name : guid;
    }

    private static Map<String, String> buildGuidToNameMap(List<ElementRecord> records) {
        Map<String, String> map = new HashMap<String, String>();
        for (ElementRecord r : records) {
            map.put(r.guid(), r.name());
        }
        return map;
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }
}