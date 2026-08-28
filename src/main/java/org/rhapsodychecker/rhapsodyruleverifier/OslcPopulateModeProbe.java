package org.rhapsodychecker.rhapsodyruleverifier;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.BenchmarkStatistics;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PhaseTimer;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PipelineProfiler;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.SnapshotEquivalence;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.*;

/**
 * Smoke test / probe for OSLC remote requirements populate mode.
 *
 * Connects to Rhapsody, discovers remote resource packages and their current
 * populate mode, then loads the model under three modes ("All", "Linked", "None")
 * and compares:
 *   - Total element count and OSLC element count
 *   - Load time (profiled with PipelineProfiler)
 *   - Rule evaluation results (pass/fail/skip) with detailed diff
 *
 * Also wraps each load with allowBrowserRefresh(0)/allowGERefresh(0) to
 * benchmark that optimization simultaneously.
 *
 * Run from Eclipse with the Rhapsody native library on the path.
 */
public class OslcPopulateModeProbe {

    private static final String[] MODES = {"All", "Linked", "None"};
    

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        String yamlPath = "src/main/resources/OslcProbeTest.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        System.out.println("=== OSLC Populate Mode Probe ===\n");

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
        Map<String, String> originalModes = new LinkedHashMap<String, String>();

        try {
            // ── Phase 0: Connect & discover remote resource packages ──────────
            System.out.println("[Phase 0] Connecting to Rhapsody...");
            conn.connect(rpyxPath);
            IRPProject project = conn.getProject();
            IRPApplication app = conn.getApplication();

            System.out.println("  Project: " + project.getName());
            System.out.println("  GUID:    " + project.getGUID());

            System.out.println("\n[Phase 0] Discovering remote resource packages...");
            List<IRPPackage> remotePkgs = discoverRemotePackages(project);

            if (remotePkgs.isEmpty()) {
                System.out.println("  No remote resource packages found.");
                System.out.println("  This project does not appear to have DOORS Next / RMM links.");
                System.out.println("  The OSLC populate-mode optimization does not apply here.");
                System.out.println("\n  Proceeding with a single baseline load + browser-refresh test...\n");

                // Still useful: run a single profiled load with browser-refresh suppression
                runBaselineWithRefreshTest(project, app, yamlPath);
                return;
            }

            System.out.println("  Found " + remotePkgs.size() + " remote resource package(s):\n");
            System.out.printf("  %-40s %-10s %s%n", "Package Name", "Mode", "GUID");
            System.out.println("  " + repeat("-", 80));

            for (IRPPackage pkg : remotePkgs) {
                String name = pkg.getName();
                String guid = pkg.getGUID();
                String mode = safeGetPopulateMode(pkg);
                originalModes.put(guid, mode);
                System.out.printf("  %-40s %-10s %s%n", truncate(name, 40), mode, guid);
            }

            // ── Load YAML config once ──────────────────────────────────────────
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("\n  Rules config: " + yamlPath);

            // ── Phase 1-3: Load under each mode ───────────────────────────────
            Map<String, ModeResult> results = new LinkedHashMap<String, ModeResult>();

            for (String mode : MODES) {
                System.out.println("\n" + repeat("═", 78));
                System.out.println("[Phase] Loading with populate mode: " + mode);
                System.out.println(repeat("═", 78));

                // Set mode on all remote packages
                for (IRPPackage pkg : remotePkgs) {
                    try {
                        pkg.setRemoteRequirementsPopulateMode(mode);
                        System.out.println("  Set " + truncate(pkg.getName(), 30) + " → " + mode);
                    } catch (Throwable t) {
                        System.out.println("  [WARN] Failed to set mode on " + pkg.getName() + ": " + t.getMessage());
                    }
                }

                // Profiled load with browser-refresh suppression
                ModeResult mr = profiledLoad(project, app, config, mode, true);
                results.put(mode, mr);
            }

            // ── Phase 4: Restore original modes ──────────────────────────────
            System.out.println("\n[Phase 4] Restoring original populate modes...");
            for (IRPPackage pkg : remotePkgs) {
                String origMode = originalModes.get(pkg.getGUID());
                if (origMode != null) {
                    try {
                        pkg.setRemoteRequirementsPopulateMode(origMode);
                        System.out.println("  Restored " + truncate(pkg.getName(), 30) + " → " + origMode);
                    } catch (Throwable t) {
                        System.out.println("  [WARN] Failed to restore " + pkg.getName() + ": " + t.getMessage());
                    }
                }
            }

            // ── Phase 5: Comparison report ───────────────────────────────────
            printComparisonReport(results);

        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();

            // Best-effort restore on failure
            restoreOriginalModes(conn, originalModes);
        } finally {
            conn.shutdown();
        }
    }

    // ── Discovery ────────────────────────────────────────────────────────────

    private static List<IRPPackage> discoverRemotePackages(IRPProject project) {
        List<IRPPackage> result = new ArrayList<IRPPackage>();
        try {
            IRPCollection remotePkgs = project.getRemoteResourcePackages();
            if (remotePkgs != null) {
                int remotePackageCount = remotePkgs.getCount();
                for (int i = 1; i <= remotePackageCount; i++) {
                    Object o = remotePkgs.getItem(i);
                    if (o instanceof IRPPackage) {
                        result.add((IRPPackage) o);
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("  [WARN] getRemoteResourcePackages() failed: " + t.getMessage());
            System.out.println("  This API may not be available in your Rhapsody version.");
        }
        return result;
    }

    private static String safeGetPopulateMode(IRPPackage pkg) {
        try {
            return pkg.getRemoteRequirementsPopulateMode();
        } catch (Throwable t) {
            return "(unknown)";
        }
    }

    // ── Profiled load ────────────────────────────────────────────────────────

    private static ModeResult profiledLoad(IRPProject project, IRPApplication app,
                                            RuleCheckerConfig config, String modeName,
                                            boolean suppressRefresh) {
        PipelineProfiler profiler = new PipelineProfiler("loadModel [" + modeName + "]");
        profiler.startPipeline();

        // Track each global refresh control separately. If the second call fails,
        // the first one is still restored in finally.
        boolean browserRefreshSuppressed = false;
        boolean geRefreshSuppressed = false;
        if (suppressRefresh) {
            try {
                app.allowBrowserRefresh(0);
                browserRefreshSuppressed = true;
            } catch (Throwable t) {
                System.out.println("  [WARN] Could not suppress browser refresh: " + t.getMessage());
            }
            try {
                app.allowGERefresh(0);
                geRefreshSuppressed = true;
            } catch (Throwable t) {
                System.out.println("  [WARN] Could not suppress GE refresh: " + t.getMessage());
            }
            System.out.println("  Refresh suppression requested (browser="
                    + browserRefreshSuppressed + ", GE=" + geRefreshSuppressed + ")");
        } else {
            System.out.println("  Refresh suppression disabled (true control run)");
        }

        RhapsodyModelSnapshot snapshot;
        try {
            PhaseTimer tLoad = profiler.startPhase("loadModel (COM)");
            RhapsodyModelLoader loader = new RhapsodyModelLoader();
            snapshot = loader.loadModel(project);
            tLoad.stop().items(snapshot.records().size());
            profiler.record(tLoad);
        } finally {
            // The API has no documented getters for the previous refresh state.
            // Restore only controls that this run successfully disabled.
            if (geRefreshSuppressed) {
                try { app.allowGERefresh(1); }
                catch (Throwable t) {
                    System.out.println("  [WARN] Could not restore GE refresh: " + t.getMessage());
                }
            }
            if (browserRefreshSuppressed) {
                try { app.allowBrowserRefresh(1); }
                catch (Throwable t) {
                    System.out.println("  [WARN] Could not restore browser refresh: " + t.getMessage());
                }
            }
        }

        // Index
        PhaseTimer tIndex = profiler.startPhase("ElementIndex.build()");
        ElementIndex index = ElementIndex.build(snapshot.records());
        tIndex.stop().items(snapshot.records().size());
        profiler.record(tIndex);

        // Evaluate rules
        PhaseTimer tEval = profiler.startPhase("Rule evaluation");
        RhapsodyAliasResolver resolver = new RhapsodyAliasResolver(snapshot);
        ElementSelector selector = new ElementSelector(index, config);
        RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                resolver, snapshot, index, selector);
        RuleEngine engine = new RuleEngine(config, selector, context);
        List<RuleResult> ruleResults = engine.evaluateAll();
        tEval.stop().items(ruleResults.size());
        profiler.record(tEval);

        profiler.stopPipeline();
        System.out.println(profiler.summary());

        // Count OSLC elements
        int oslcCount = 0;
        for (ElementRecord r : snapshot.records()) {
            if (RhapsodyModelLoader.isRemoteOslcResource(r.guid(), r.name())) {
                oslcCount++;
            }
        }

        System.out.println("  Total elements: " + snapshot.records().size());
        System.out.println("  OSLC elements:  " + oslcCount);
        System.out.println("  Rule results:   " + ruleResults.size());

        return new ModeResult(modeName,
                snapshot.records().size(), oslcCount,
                profiler.wallTimeMs(),
                getLcomPhaseMs(profiler),
                snapshot, ruleResults);
    }

    private static double getLcomPhaseMs(PipelineProfiler profiler) {
        for (org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PhaseTimer p : profiler.phases()) {
            if (p.name().contains("loadModel")) return p.durationMs();
        }
        return 0;
    }

    // ── Baseline + refresh test (for projects without OSLC) ──────────────────

    private static void runBaselineWithRefreshTest(IRPProject project, IRPApplication app,
                                                    String yamlPath) {
        try {
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            int rounds = Math.max(1, Integer.getInteger("rhapsody.probe.rounds", 1));
            System.out.println("  Measured AB/BA rounds: " + rounds
                    + " (override with -Drhapsody.probe.rounds=N)");
            System.out.println("  Each round produces two samples per mode and alternates order.");

            List<ModeResult> withoutRuns = new ArrayList<ModeResult>();
            List<ModeResult> withRuns = new ArrayList<ModeResult>();
            ModeResult fidelityBaseline = null;
            boolean equivalent = true;

            for (int round = 0; round < rounds; round++) {
                boolean[] order = round % 2 == 0
                        ? new boolean[] { false, true, true, false }   // ABBA
                        : new boolean[] { true, false, false, true }; // BAAB

                for (int position = 0; position < order.length; position++) {
                    boolean suppress = order[position];
                    String label = suppress ? "WithRefreshSuppress" : "NoRefreshSuppress";
                    System.out.println("\n[Round " + (round + 1) + "/" + rounds
                            + ", run " + (position + 1) + "/4] " + label);

                    ModeResult run = profiledLoad(project, app, config, label, suppress);
                    if (suppress) withRuns.add(run); else withoutRuns.add(run);

                    if (fidelityBaseline == null) {
                        fidelityBaseline = run;
                    } else {
                        SnapshotEquivalence.Report snapshotReport =
                                SnapshotEquivalence.compare(
                                        fidelityBaseline.snapshot, run.snapshot);
                        SnapshotEquivalence.Report ruleReport =
                                SnapshotEquivalence.compareRuleResults(
                                        fidelityBaseline.ruleResults, run.ruleResults);
                        System.out.println("  " + snapshotReport.summary());
                        System.out.println("  RULE EQUIVALENCE: "
                                + (ruleReport.isEquivalent() ? "PASS" : "FAIL")
                                + " — " + ruleReport.differenceCount() + " difference(s)");
                        if (!snapshotReport.isEquivalent() || !ruleReport.isEquivalent()) {
                            equivalent = false;
                            for (String detail : ruleReport.details()) {
                                System.out.println("    - " + detail);
                            }
                        }
                    }
                }
            }

            BenchmarkStatistics withoutStats =
                    BenchmarkStatistics.of(loadTimes(withoutRuns));
            BenchmarkStatistics withStats =
                    BenchmarkStatistics.of(loadTimes(withRuns));

            System.out.println("\n" + repeat("═", 78));
            System.out.println("  Browser/GE refresh suppression comparison:");
            System.out.println("  Without: " + withoutStats.formatMillis());
            System.out.println("  With:    " + withStats.formatMillis());

            double delta = withoutStats.median() - withStats.median();
            double pct = withoutStats.median() > 0
                    ? delta / withoutStats.median() * 100.0 : 0.0;
            System.out.printf(Locale.ROOT,
                    "  Median delta: %.1f ms (%+.1f%%; positive means suppression is faster)%n",
                    delta, pct);
            System.out.println("  DATA EQUIVALENCE: " + (equivalent ? "PASS" : "FAIL"));
            System.out.println("  PERFORMANCE: "
                    + (equivalent ? "MEASURED — judge median against stddev/noise"
                                  : "REJECTED — data differed"));
            System.out.println("  PROMOTION SAFE: " + (equivalent ? "DATA-SAFE" : "NO"));
        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static List<Double> loadTimes(List<ModeResult> runs) {
        List<Double> values = new ArrayList<Double>(runs.size());
        for (ModeResult run : runs) values.add(run.loadModelMs);
        return values;
    }

    // ── Comparison report ────────────────────────────────────────────────────

    private static void printComparisonReport(Map<String, ModeResult> results) {
        System.out.println("\n" + repeat("═", 78));
        System.out.println("  OSLC POPULATE MODE COMPARISON REPORT");
        System.out.println(repeat("═", 78));

        // Summary table
        System.out.printf("%n  %-10s %10s %10s %12s %12s %15s%n",
                "Mode", "Elements", "OSLC Elts", "Load (ms)", "Total (ms)", "Pass/Fail/Skip");
        System.out.println("  " + repeat("-", 75));

        ModeResult baseline = results.get("All");

        for (String mode : MODES) {
            ModeResult mr = results.get(mode);
            if (mr == null) continue;

            long[] counts = countByStatus(mr.ruleResults);
            String pfs = String.format("%d/%d/%d", counts[0], counts[1], counts[2]);

            System.out.printf("  %-10s %10d %10d %12.1f %12.1f %15s%n",
                    mr.modeName, mr.totalElements, mr.oslcElements,
                    mr.loadModelMs, mr.totalMs, pfs);
        }

        // Detailed diff vs baseline
        if (baseline != null) {
            for (String mode : MODES) {
                if ("All".equals(mode)) continue;
                ModeResult mr = results.get(mode);
                if (mr == null) continue;

                System.out.println("\n  --- Diff: \"" + mode + "\" vs \"All\" (baseline) ---");

                // Element count delta
                int elemDelta = mr.totalElements - baseline.totalElements;
                int oslcDelta = mr.oslcElements - baseline.oslcElements;
                System.out.printf("  Elements: %+d (total), %+d (OSLC)%n", elemDelta, oslcDelta);

                // Time delta
                double timeDelta = mr.loadModelMs - baseline.loadModelMs;
                double timePct = baseline.loadModelMs > 0
                        ? (timeDelta / baseline.loadModelMs) * 100 : 0;
                System.out.printf("  Load time: %+.1f ms (%+.1f%%)%n", timeDelta, timePct);

                SnapshotEquivalence.Report snapshotReport =
                        SnapshotEquivalence.compare(baseline.snapshot, mr.snapshot);
                SnapshotEquivalence.Report ruleReport =
                        SnapshotEquivalence.compareRuleResults(
                                baseline.ruleResults, mr.ruleResults);
                System.out.println("  " + snapshotReport.summary());
                System.out.println("  RULE EQUIVALENCE: "
                        + (ruleReport.isEquivalent() ? "PASS" : "FAIL")
                        + " — " + ruleReport.differenceCount() + " difference(s)");
                for (String detail : ruleReport.details()) {
                    System.out.println("    - " + detail);
                }

                if (snapshotReport.isEquivalent() && ruleReport.isEquivalent()) {
                    System.out.println("  ✓ Full data and rule-result equivalence passed for \""
                            + mode + "\"");
                } else {
                    System.out.println("  ✗ \"" + mode
                            + "\" is not safe: full-fidelity comparison failed");
                }
            }
        }

        System.out.println("\n" + repeat("═", 78));
        System.out.println("  RECOMMENDATION:");

        if (baseline != null) {
            ModeResult linked = results.get("Linked");
            if (linked != null) {
                boolean linkedSafe =
                        SnapshotEquivalence.compare(baseline.snapshot, linked.snapshot).isEquivalent()
                        && SnapshotEquivalence.compareRuleResults(
                                baseline.ruleResults, linked.ruleResults).isEquivalent();
                double speedup = baseline.loadModelMs - linked.loadModelMs;

                if (linkedSafe && speedup > 1000) {
                    System.out.printf("  → Switch to \"Linked\" — saves %.1fs with ZERO rule result changes%n", speedup / 1000.0);
                } else if (linkedSafe) {
                    System.out.printf("  → \"Linked\" is safe (0 diffs) but only saves %.1fs — marginal gain%n", speedup / 1000.0);
                } else {
                    System.out.println("  → \"Linked\" has rule differences — review the diff above before switching");
                }
            }
        }

        System.out.println(repeat("═", 78));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static long[] countByStatus(List<RuleResult> results) {
        long[] counts = new long[3]; // pass, fail, skip
        for (RuleResult r : results) {
            switch (r.status()) {
                case PASS: counts[0]++; break;
                case FAIL: counts[1]++; break;
                case SKIPPED: counts[2]++; break;
            }
        }
        return counts;
    }

    private static void restoreOriginalModes(RhapsodyConnectionManager conn,
                                              Map<String, String> originalModes) {
        if (originalModes.isEmpty()) return;
        try {
            if (!conn.isConnected()) return;
            IRPProject project = conn.getProject();
            IRPCollection remotePkgs = project.getRemoteResourcePackages();
            if (remotePkgs == null) return;
            int remotePackageCount = remotePkgs.getCount();
            for (int i = 1; i <= remotePackageCount; i++) {
                Object o = remotePkgs.getItem(i);
                if (o instanceof IRPPackage) {
                    IRPPackage pkg = (IRPPackage) o;
                    String origMode = originalModes.get(pkg.getGUID());
                    if (origMode != null) {
                        try {
                            pkg.setRemoteRequirementsPopulateMode(origMode);
                        } catch (Throwable t) { /* best effort */ }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[WARN] Failed to restore modes in cleanup: " + t.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }

    // ── Result holder ────────────────────────────────────────────────────────

    private static final class ModeResult {
        final String modeName;
        final int totalElements;
        final int oslcElements;
        final double totalMs;
        final double loadModelMs;
        final RhapsodyModelSnapshot snapshot;
        final List<RuleResult> ruleResults;

        ModeResult(String modeName, int totalElements, int oslcElements,
                   double totalMs, double loadModelMs,
                   RhapsodyModelSnapshot snapshot, List<RuleResult> ruleResults) {
            this.modeName = modeName;
            this.totalElements = totalElements;
            this.oslcElements = oslcElements;
            this.totalMs = totalMs;
            this.loadModelMs = loadModelMs;
            this.snapshot = snapshot;
            this.ruleResults = ruleResults;
        }
    }
}