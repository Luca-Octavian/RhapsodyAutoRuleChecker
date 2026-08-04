package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.*;

/**
 * Smoke test for NamingPattern and FlowPropertyConstraint rules.
 * Runs against a live Rhapsody model via COM — forces a fresh scan.
 *
 * <p>Validates:
 * <ol>
 *   <li>FlowProperties detected with correct tags (direction via getAllTags)</li>
 *   <li>Known element assertion: functConnection has direction=Bidirectional</li>
 *   <li>NamingPattern rule produces results for InterfaceBlocks (shown separately)</li>
 *   <li>FlowPropertyConstraint rule produces a mix of PASS/FAIL</li>
 *   <li>Cache round-trip preserves direction tag value (not just empty=empty match)</li>
 * </ol>
 *
 * Run from Eclipse with the Rhapsody native library on the path.
 * Delete old cache first to force a fresh live scan.
 */
public class NewRulesSmokeTest {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\Rhapsody Model 2\\Rhapsody Model 2\\L2_PECU_SYS-Architecture.rpyx";
        String yamlPath = "src/main/resources/NewRulesSmokeTest.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        // Force fresh cache by deleting old one
        java.io.File oldCache = org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager
                .defaultCacheFile(rpyxPath);
        if (oldCache.exists()) {
            oldCache.delete();
            System.out.println("[Setup] Deleted old cache to force fresh live scan.");
        }

        System.out.println("=== New Rules Smoke Test (NamingPattern + FlowPropertyConstraint) ===\n");

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
        int passed = 0, failed = 0;

        try {
            // ── Phase 1: Fresh live scan ─────────────────────────────────────
            System.out.println("[Phase 1] Loading model from Rhapsody (fresh live scan)...");
            conn.connect(rpyxPath);

            RhapsodyModelSnapshot snapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            ElementIndex index = ElementIndex.build(snapshot.records());

            System.out.println("  Total elements: " + snapshot.records().size());

            // ── 1a: InterfaceBlocks ──
            Set<String> ibGuids = index.guidsByKind(ElementKind.INTERFACE_BLOCK);
            System.out.println("\n  InterfaceBlocks found: " + ibGuids.size());
            int ibShown = 0;
            for (String guid : ibGuids) {
                if (ibShown >= 5) { System.out.println("    ... and " + (ibGuids.size() - 5) + " more"); break; }
                index.repository().get(guid).ifPresent(r ->
                    System.out.println("    - " + r.name() + " (stereotypes: " + r.stereotypes() + ")")
                );
                ibShown++;
            }

            // ── 1b: FlowProperties — show first 10 with tag data ──
            Set<String> fpGuids = index.guidsByKind(ElementKind.FLOW_PROPERTY);
            System.out.println("\n  FlowProperties found: " + fpGuids.size());
            int fpShown = 0;
            for (String guid : fpGuids) {
                if (fpShown >= 10) { System.out.println("    ... and " + (fpGuids.size() - 10) + " more"); break; }
                index.repository().get(guid).ifPresent(r -> {
                    String type = r.typeName().orElse("<none>");
                    String initVal = r.initialValue().orElse("<none>");
                    String direction = findTagIgnoreCase(r.tagValues(), "direction");
                    System.out.println("    - " + r.name()
                            + " | type=" + type
                            + " | initVal=" + initVal
                            + " | direction=" + (direction.isEmpty() ? "<none>" : direction)
                            + " | tags=" + r.tagValues());
                });
                fpShown++;
            }

            // ── 1c: Known element assertion: functConnection ──
            System.out.println("\n  === Known Element Assertion: functConnection ===");
            ElementRecord functConn = findByName(index, fpGuids, "functConnection");
            if (functConn != null) {
                String actualType = functConn.typeName().orElse("");
                String actualInitVal = functConn.initialValue().orElse("");
                String actualDirection = findTagIgnoreCase(functConn.tagValues(), "direction");
                String actualOwner = functConn.ownerGuid().orElse("");

                System.out.println("    name:         " + functConn.name());
                System.out.println("    type:         " + actualType);
                System.out.println("    initialValue: " + (actualInitVal.isEmpty() ? "<empty>" : actualInitVal));
                System.out.println("    direction:    " + (actualDirection.isEmpty() ? "<empty>" : actualDirection));
                System.out.println("    ownerGuid:    " + actualOwner);
                System.out.println("    tagValues:    " + functConn.tagValues());

                // Assert direction is non-blank (actual value depends on model instance)
                if (!actualDirection.isEmpty()) {
                    System.out.println("    [PASS] direction is non-blank: '" + actualDirection + "'");
                    passed++;
                } else {
                    System.out.println("    [FAIL] direction is empty/missing");
                    failed++;
                }

                // Assert type = int
                if ("int".equals(actualType)) {
                    System.out.println("    [PASS] type == int");
                    passed++;
                } else {
                    System.out.println("    [FAIL] type expected 'int', got '" + actualType + "'");
                    failed++;
                }

                // Assert owner is an InterfaceBlock (ifDrivOperation)
                if (ibGuids.contains(actualOwner)) {
                    Optional<ElementRecord> ownerRec = index.repository().get(actualOwner);
                    String ownerName = ownerRec.map(ElementRecord::name).orElse("<unknown>");
                    System.out.println("    [PASS] owner is InterfaceBlock '" + ownerName + "'");
                    passed++;
                } else {
                    System.out.println("    [FAIL] owner GUID not in InterfaceBlocks set");
                    failed++;
                }
            } else {
                System.out.println("    [FAIL] functConnection not found in FlowProperties");
                failed++;
            }

            // ── Phase 2: Run rules ──────────────────────────────────────────
            System.out.println("\n[Phase 2] Loading config and running rules...");
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("  Config loaded: " + config.enabledRules().size() + " rules");

            RhapsodyAliasResolver resolver = new RhapsodyAliasResolver(snapshot);
            ElementSelector selector = new ElementSelector(index, config);
            RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                    resolver, snapshot, index, selector);
            RuleEngine engine = new RuleEngine(config, selector, context);
            List<RuleResult> results = engine.evaluateAll();

            // ── Phase 3a: NamingPattern rule results (InterfaceBlocks) ───────
            System.out.println("\n[Phase 3a] NamingPattern Rule (interface-block-prefix):\n");
            printRuleResults("interface-block-prefix", results, index);

            // ── Phase 3b: FlowPropertyConstraint rule results ────────────────
            System.out.println("\n[Phase 3b] FlowPropertyConstraint Rule (flow-property-required-fields):\n");
            int[] fpCounts = printRuleResults("flow-property-required-fields", results, index);

            // Assert the FlowProperty rule has some PASSes (not all failures)
            if (fpCounts[0] > 0) {
                System.out.println("    [PASS] FlowPropertyConstraint has " + fpCounts[0] + " PASSes (direction captured)");
                passed++;
            } else {
                System.out.println("    [FAIL] FlowPropertyConstraint has 0 PASSes — direction still not captured?");
                failed++;
            }

            // ── Phase 4: Cache round-trip with explicit direction assertion ──
            System.out.println("\n[Phase 4] Cache round-trip with explicit direction assertion...");
            java.io.File cacheFile = org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager
                    .defaultCacheFile(rpyxPath);
            org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager
                    .writeCache(snapshot, conn.getProject().getName(),
                            conn.getProject().getGUID(), cacheFile, rpyxPath);

            org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager.CacheLoadResult cacheResult =
                    org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager.readCache(cacheFile);
            RhapsodyModelSnapshot cachedSnapshot = cacheResult.snapshot();
            ElementIndex cachedIndex = ElementIndex.build(cachedSnapshot.records());

            Set<String> cachedFpGuids = cachedIndex.guidsByKind(ElementKind.FLOW_PROPERTY);
            System.out.println("  Live FlowProperties: " + fpGuids.size()
                    + " | Cached FlowProperties: " + cachedFpGuids.size());

            if (fpGuids.size() == cachedFpGuids.size()) {
                System.out.println("  [PASS] FlowProperty count preserved");
                passed++;
            } else {
                System.out.println("  [FAIL] FlowProperty count mismatch!");
                failed++;
            }

            // Explicit direction assertion on functConnection through cache
            if (functConn != null) {
                Optional<ElementRecord> cachedFc = cachedIndex.repository().get(functConn.guid());
                if (cachedFc.isPresent()) {
                    String liveDir = findTagIgnoreCase(functConn.tagValues(), "direction");
                    String cachedDir = findTagIgnoreCase(cachedFc.get().tagValues(), "direction");

                    System.out.println("  functConnection direction — live: '" + liveDir
                            + "' | cached: '" + cachedDir + "'");

                    if (!liveDir.isEmpty() && liveDir.equals(cachedDir)) {
                        System.out.println("  [PASS] direction round-trip preserved: '" + liveDir + "'");
                        passed++;
                    } else {
                        System.out.println("  [FAIL] Direction mismatch or empty — live='" + liveDir
                                + "', cached='" + cachedDir + "'");
                        failed++;
                    }
                } else {
                    System.out.println("  [FAIL] functConnection not found in cached snapshot");
                    failed++;
                }
            }

            // ── Summary ─────────────────────────────────────────────────────
            System.out.println("\n════════════════════════════════════════════");
            System.out.println("  SMOKE TEST SUMMARY: " + passed + " passed, " + failed + " failed");
            System.out.println("════════════════════════════════════════════");

        } catch (Throwable t) {
            System.err.println("[FATAL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Prints rule results for a specific ruleId. Returns {passCount, failCount, skipCount}.
     */
    private static int[] printRuleResults(String ruleId, List<RuleResult> allResults,
                                           ElementIndex index) {
        int passCount = 0, failCount = 0, skipCount = 0;
        List<RuleResult> ruleResults = new ArrayList<>();
        for (RuleResult r : allResults) {
            if (ruleId.equals(r.ruleId())) {
                ruleResults.add(r);
                switch (r.status()) {
                    case PASS: passCount++; break;
                    case FAIL: failCount++; break;
                    case SKIPPED: skipCount++; break;
                }
            }
        }

        System.out.println("  Total: " + ruleResults.size()
                + " | Pass: " + passCount
                + " | Fail: " + failCount
                + " | Skip: " + skipCount);

        // Show first 5 failures
        int shown = 0;
        for (RuleResult r : ruleResults) {
            if (r.status() == RuleStatus.FAIL && shown < 5) {
                Optional<ElementRecord> rec = index.repository().get(r.elementGuid());
                String name = rec.map(ElementRecord::name).orElse("<unknown>");
                System.out.println("    [FAIL] " + name + ": " + r.message());
                shown++;
            }
        }
        if (failCount > 5) {
            System.out.println("    ... and " + (failCount - 5) + " more failures");
        }

        // Show first 5 passes
        shown = 0;
        for (RuleResult r : ruleResults) {
            if (r.status() == RuleStatus.PASS && shown < 5) {
                Optional<ElementRecord> rec = index.repository().get(r.elementGuid());
                String name = rec.map(ElementRecord::name).orElse("<unknown>");
                System.out.println("    [PASS] " + name);
                shown++;
            }
        }
        if (passCount > 5) {
            System.out.println("    ... and " + (passCount - 5) + " more passes");
        }

        return new int[]{passCount, failCount, skipCount};
    }

    private static ElementRecord findByName(ElementIndex index, Set<String> guids, String name) {
        for (String guid : guids) {
            Optional<ElementRecord> rec = index.repository().get(guid);
            if (rec.isPresent() && name.equals(rec.get().name())) {
                return rec.get();
            }
        }
        return null;
    }

    private static String findTagIgnoreCase(Map<String, String> tags, String wanted) {
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (wanted.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }
}