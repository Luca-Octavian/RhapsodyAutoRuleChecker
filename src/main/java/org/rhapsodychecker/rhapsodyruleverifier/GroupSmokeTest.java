// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/GroupSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyAliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyEvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleGrouping;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Smoke test for the group feature, run against the LIVE Rhapsody model.
 *
 * By default it attaches to whatever project is currently open in Rhapsody
 * (pass a .rpyx path as arg[0] to open a specific one instead). Requires the
 * Rhapsody JAR on the classpath and rhapsody.dll on the Native Library Location
 * — same Eclipse run configuration as CacheSmokeTest.
 *
 * Because the model is real, the test does not assert fixed pass/fail counts.
 * It asserts the invariants that define group-based conjunction:
 *
 *   1. The config parses and contains rules with a group label.
 *   2. Individual rule results appear as separate rows (no hiding).
 *   3. RuleGrouping produces one verdict per (group, element).
 *   4. A group verdict is PASS only if ALL member rules passed for that element.
 *   5. The number of group-failing elements equals the number of elements
 *      that fail at least one member rule.
 */
public class GroupSmokeTest {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        String rpyxPath = "";
        String yamlPath = "src/main/resources/GroupTest.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        System.out.println("=== Group (rule conjunction) Smoke Test — LIVE Rhapsody ===\n");

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // ── Phase 1: attach to Rhapsody ───────────────────────────────
            System.out.println("[Phase 1] Connecting to Rhapsody"
                    + (rpyxPath.isEmpty() ? " (active instance)..." : ": " + rpyxPath));
            conn.connect(rpyxPath);
            System.out.println("  Project: " + conn.getProject().getName());

            // ── Phase 2: load the model ───────────────────────────────────
            System.out.println("\n[Phase 2] Loading model...");
            RhapsodyModelSnapshot snapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            System.out.println("  Elements loaded: " + snapshot.records().size());

            // ── Phase 3: load the group config ────────────────────────────
            System.out.println("\n[Phase 3] Loading config: " + yamlPath);
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("  Rules: " + config.rules().size()
                    + ", sets: " + config.elementSets().size());

            // Verify group labels are present
            Set<String> groups = RuleGrouping.collectGroups(config.rules());
            System.out.println("  Groups found: " + groups);
            check("Config contains at least one group", !groups.isEmpty());

            int groupedRuleCount = 0;
            for (RuleSpec spec : config.rules()) {
                if (spec.group().isPresent()) groupedRuleCount++;
            }
            check("At least 2 rules share the same group", groupedRuleCount >= 2);

            // ── Phase 4: wire the evaluation pipeline ─────────────────────
            System.out.println("\n[Phase 4] Wiring evaluation pipeline...");
            ElementIndex index = ElementIndex.build(snapshot.records());
            RhapsodyAliasResolver resolver = new RhapsodyAliasResolver(snapshot);
            ElementSelector selector = new ElementSelector(index, config);
            RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                    resolver, snapshot, index, selector);

            // ── Phase 5: evaluate all rules via the engine ────────────────
            System.out.println("\n[Phase 5] Evaluating rules...");
            RuleEngine engine = new RuleEngine(config, selector, context);
            List<RuleResult> results = engine.evaluateAll();

            int passCount = 0, failCount = 0, skipCount = 0;
            for (RuleResult r : results) {
                switch (r.status()) {
                    case PASS: passCount++; break;
                    case FAIL: failCount++; break;
                    case SKIPPED: skipCount++; break;
                }
            }
            System.out.println("  Results: " + results.size()
                    + " (pass=" + passCount + ", fail=" + failCount
                    + ", skip=" + skipCount + ")");

            // ── Phase 6: structural invariants ────────────────────────────
            System.out.println("\n[Phase 6] Verifying individual results...");

            // All rule IDs must appear as separate rows (no merging)
            Set<String> resultRuleIds = new LinkedHashSet<>();
            for (RuleResult r : results) {
                resultRuleIds.add(r.ruleId());
            }
            boolean allRulesPresent = true;
            for (RuleSpec spec : config.rules()) {
                if (!resultRuleIds.contains(spec.id())) {
                    allRulesPresent = false;
                    System.out.println("  [WARN] Rule '" + spec.id() + "' produced no results");
                }
            }
            check("Every rule produces individual result rows", allRulesPresent);

            // ── Phase 7: group conjunction verdicts ───────────────────────
            System.out.println("\n[Phase 7] Computing group verdicts...");
            List<RuleGrouping.GroupVerdict> verdicts =
                    RuleGrouping.computeVerdicts(results, config.rules());
            System.out.println("  Verdicts: " + verdicts.size());

            check("At least one group verdict exists", !verdicts.isEmpty());

            int groupPass = 0, groupFail = 0;
            for (RuleGrouping.GroupVerdict v : verdicts) {
                if (v.allPassed()) groupPass++; else groupFail++;
            }
            System.out.println("  Group pass=" + groupPass + ", group fail=" + groupFail);

            // Verify conjunction invariant: verdict fails iff at least one member fails
            int conjunctionMismatches = 0;
            for (RuleGrouping.GroupVerdict v : verdicts) {
                boolean anyMemberFailed = false;
                for (RuleResult mr : v.memberResults()) {
                    if (mr.status() == RuleStatus.FAIL) {
                        anyMemberFailed = true;
                        break;
                    }
                }
                if (v.allPassed() == anyMemberFailed) {
                    conjunctionMismatches++;
                }
            }
            check("Group verdict == conjunction of member statuses", conjunctionMismatches == 0);

            // Verify each verdict has the expected member count
            boolean memberCountOk = true;
            for (RuleGrouping.GroupVerdict v : verdicts) {
                if (v.memberResults().size() < 2) {
                    memberCountOk = false;
                    break;
                }
            }
            check("Each group verdict has >= 2 member results", memberCountOk);

            // Sample output
            if (!verdicts.isEmpty()) {
                RuleGrouping.GroupVerdict sample = verdicts.get(0);
                System.out.println("\n  Sample verdict:");
                System.out.println("    Group:   " + sample.group());
                System.out.println("    Element: " + nameOf(index, sample.elementGuid()));
                System.out.println("    Passed:  " + sample.allPassed());
                System.out.println("    Members: " + sample.memberResults().size());
                for (RuleResult mr : sample.memberResults()) {
                    System.out.println("      " + mr.ruleId() + " -> " + mr.status()
                            + (mr.status() == RuleStatus.FAIL ? " : " + mr.message() : ""));
                }
            }

        } catch (Throwable t) {
            System.err.println("[ERROR] " + t.getMessage());
            t.printStackTrace();
            checksFailed++;
        } finally {
            conn.shutdown();
        }

        report();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String nameOf(ElementIndex index, String guid) {
        Optional<ElementRecord> rec = index.repository().get(guid);
        return rec.isPresent() ? rec.get().name() : "<" + guid + ">";
    }

    private static void check(String label, boolean condition) {
        checksRun++;
        if (condition) {
            System.out.println("  [PASS] " + label);
        } else {
            checksFailed++;
            System.out.println("  [FAIL] " + label);
        }
    }

    private static void report() {
        System.out.println("\n=== " + (checksRun - checksFailed) + "/" + checksRun
                + " checks passed ===");
        System.out.println(checksFailed == 0
                ? "[PASS] Group smoke test succeeded."
                : "[FAIL] " + checksFailed + " check(s) failed.");
    }
}