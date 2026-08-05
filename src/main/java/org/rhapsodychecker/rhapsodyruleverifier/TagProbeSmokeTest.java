package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyAliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyEvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.*;

/**
 * Tag Probe Smoke Test — diagnoses why the fail count changed.
 *
 * <p>Answers two questions:
 * <ol>
 *   <li>What tags does the loader actually store on each element kind?
 *       (top tag names, coverage, sample values)</li>
 *   <li>How do those tags affect rule evaluation?
 *       (per-rule pass/fail/skip counts + failure-reason breakdown)</li>
 * </ol>
 *
 * <p>Usage (Eclipse run config):
 * <pre>
 *   arg[0] = path to .rpyx   (optional, defaults to embedded path)
 *   arg[1] = path to .yaml   (optional, defaults to embedded path)
 * </pre>
 *
 * <p>Pure diagnostic output — no assertions, no JVM exit codes.
 * Read the per-rule FAIL counts to find which rules account for the extra failures.
 */
public final class TagProbeSmokeTest {

    // ── defaults ─────────────────────────────────────────────────────────────
    private static final String DEFAULT_RPYX =
    		"C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
    private static final String DEFAULT_YAML =
            "src/main/resources/OslcProbeTest.yaml";

    /** Max distinct sample values to collect per tag name (Phase 3). */
    private static final int SAMPLE_LIMIT = 3;
    /** Max failing elements to print per rule (Phase 6). */
    private static final int FAIL_SAMPLE = 8;

    public static void main(String[] args) {
        String rpyxPath = args.length >= 1 ? args[0] : DEFAULT_RPYX;
        String yamlPath = args.length >= 2 ? args[1] : DEFAULT_YAML;

        System.out.println("=== Tag Probe Smoke Test ===");
        System.out.println("  model : " + rpyxPath);
        System.out.println("  config: " + yamlPath);
        System.out.println();

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
        try {
            // ── Phase 1: load model ──────────────────────────────────────────
            System.out.println("--- Phase 1: Loading model ---");
            conn.connect(rpyxPath);
            RhapsodyModelSnapshot snapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            Collection<ElementRecord> records = snapshot.records();
            System.out.println("  Total elements loaded: " + records.size());

            // Build guid -> record lookup used in later phases
            Map<String, ElementRecord> byGuid = new LinkedHashMap<String, ElementRecord>();
            for (ElementRecord r : records) {
                byGuid.put(r.guid(), r);
            }

            ElementIndex index = ElementIndex.build(records);

            // ── Phase 2: tag coverage per element kind ───────────────────────
            System.out.println();
            System.out.println("--- Phase 2: Tag coverage by ElementKind ---");
            tagCoverageByKind(records);

            // ── Phase 3: top tag names across all elements ───────────────────
            System.out.println();
            System.out.println("--- Phase 3: Top tag names (all elements) ---");
            topTagNames(records, 25);

            // ── Phase 4: load config & run rules ────────────────────────────
            System.out.println();
            System.out.println("--- Phase 4: Rule evaluation ---");
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("  Rules in config (enabled): " + config.enabledRules().size());

            RhapsodyAliasResolver resolver = new RhapsodyAliasResolver(snapshot);
            ElementSelector selector = new ElementSelector(index, config);
            RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                    resolver, snapshot, index, selector);
            RuleEngine engine = new RuleEngine(config, selector, context);
            List<RuleResult> results = engine.evaluateAll();

            int totalPass = 0, totalFail = 0, totalSkip = 0;
            for (RuleResult r : results) {
                switch (r.status()) {
                    case PASS:    totalPass++; break;
                    case FAIL:    totalFail++; break;
                    case SKIPPED: totalSkip++; break;
                    default:      break;
                }
            }
            System.out.println("  Results: total=" + results.size()
                    + "  PASS=" + totalPass
                    + "  FAIL=" + totalFail
                    + "  SKIP=" + totalSkip);

            // ── Phase 5: per-rule breakdown ──────────────────────────────────
            System.out.println();
            System.out.println("--- Phase 5: Per-rule breakdown ---");
            perRuleBreakdown(results, config);

            // ── Phase 6: TAGGED_VALUE rules — tag presence analysis ──────────
            System.out.println();
            System.out.println("--- Phase 6: TAGGED_VALUE rule — tag presence analysis ---");
            taggedValueRuleAnalysis(results, config, byGuid);

            // ── Phase 7: failure-message frequency ──────────────────────────
            System.out.println();
            System.out.println("--- Phase 7: Failure message frequency ---");
            failureMessageFrequency(results);

        } catch (Throwable t) {
            System.err.println("[FATAL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }

    // ── Phase 2 ──────────────────────────────────────────────────────────────

    private static void tagCoverageByKind(Collection<ElementRecord> records) {
        Map<ElementKind, int[]> stats = new LinkedHashMap<ElementKind, int[]>();
        for (ElementKind k : ElementKind.values()) {
            stats.put(k, new int[2]);
        }
        for (ElementRecord r : records) {
            int[] s = stats.get(r.kind());
            s[1]++;
            if (!r.tagValues().isEmpty()) s[0]++;
        }
        System.out.printf("  %-30s %8s %8s %8s%n", "Kind", "Total", "HasTags", "%");
        System.out.println("  " + dashes(58));
        for (Map.Entry<ElementKind, int[]> e : stats.entrySet()) {
            int[] s = e.getValue();
            if (s[1] == 0) continue;
            double pct = 100.0 * s[0] / s[1];
            System.out.printf("  %-30s %8d %8d %7.1f%%%n",
                    e.getKey(), s[1], s[0], pct);
        }
    }

    // ── Phase 3 ──────────────────────────────────────────────────────────────

    private static void topTagNames(Collection<ElementRecord> records, int topN) {
        Map<String, int[]> freq = new LinkedHashMap<String, int[]>();
        Map<String, List<String>> samples = new LinkedHashMap<String, List<String>>();

        for (ElementRecord r : records) {
            for (Map.Entry<String, String> tv : r.tagValues().entrySet()) {
                String name = tv.getKey();
                int[] cnt = freq.get(name);
                if (cnt == null) { cnt = new int[1]; freq.put(name, cnt); }
                cnt[0]++;

                List<String> sv = samples.get(name);
                if (sv == null) { sv = new ArrayList<String>(); samples.put(name, sv); }
                if (sv.size() < SAMPLE_LIMIT) {
                    String val = tv.getValue();
                    if (val != null && !val.isEmpty() && !sv.contains(val)) {
                        sv.add(val);
                    }
                }
            }
        }

        List<Map.Entry<String, int[]>> sorted = new ArrayList<Map.Entry<String, int[]>>(freq.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, int[]>>() {
            public int compare(Map.Entry<String, int[]> a, Map.Entry<String, int[]> b) {
                return b.getValue()[0] - a.getValue()[0];
            }
        });

        System.out.printf("  %-40s %8s  %s%n", "Tag name", "Count", "Sample values");
        System.out.println("  " + dashes(80));
        int shown = 0;
        for (Map.Entry<String, int[]> e : sorted) {
            if (shown++ >= topN) break;
            List<String> sv = samples.get(e.getKey());
            System.out.printf("  %-40s %8d  %s%n",
                    e.getKey(), e.getValue()[0], sv);
        }
        if (freq.size() > topN) {
            System.out.println("  ... and " + (freq.size() - topN) + " more tag names");
        }
        System.out.println("  (Total distinct tag names: " + freq.size() + ")");
    }

    // ── Phase 5 ──────────────────────────────────────────────────────────────

    private static void perRuleBreakdown(List<RuleResult> results, RuleCheckerConfig config) {
        // ruleId -> [pass, fail, skip]
        Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
        for (RuleResult r : results) {
            int[] c = counts.get(r.ruleId());
            if (c == null) { c = new int[3]; counts.put(r.ruleId(), c); }
            switch (r.status()) {
                case PASS:    c[0]++; break;
                case FAIL:    c[1]++; break;
                case SKIPPED: c[2]++; break;
                default:      break;
            }
        }

        // ruleId -> title string
        Map<String, String> titles = new LinkedHashMap<String, String>();
        for (RuleSpec s : config.enabledRules()) {
            String t = s.title().isPresent() ? s.title().get() : s.id();
            titles.put(s.id(), t);
        }

        System.out.printf("  %-45s %6s %6s %6s  %s%n",
                "Rule ID", "PASS", "FAIL", "SKIP", "Title");
        System.out.println("  " + dashes(95));
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int[] c = e.getValue();
            String title = titles.containsKey(e.getKey()) ? titles.get(e.getKey()) : "";
            System.out.printf("  %-45s %6d %6d %6d  %s%n",
                    e.getKey(), c[0], c[1], c[2], title);
        }
    }

    // ── Phase 6 ──────────────────────────────────────────────────────────────

    private static void taggedValueRuleAnalysis(List<RuleResult> results,
                                                 RuleCheckerConfig config,
                                                 Map<String, ElementRecord> byGuid) {
        // Identify TAGGED_VALUE rules
        Set<String> tvRuleIds = new LinkedHashSet<String>();
        Map<String, String> ruleToTag = new LinkedHashMap<String, String>();

        for (RuleSpec spec : config.enabledRules()) {
            if (!spec.target().isPresent()) continue;
            TargetSpec target = spec.target().get();
            if (target.kind() == AliasKind.TAGGED_VALUE) {
                tvRuleIds.add(spec.id());
                ruleToTag.put(spec.id(),
                        target.tagName().isPresent() ? target.tagName().get() : "<unknown>");
            }
        }

        if (tvRuleIds.isEmpty()) {
            System.out.println("  No TAGGED_VALUE rules found in config.");
            return;
        }
        System.out.println("  TAGGED_VALUE rules: " + tvRuleIds);

        // Group results by ruleId
        Map<String, List<RuleResult>> byRule = new LinkedHashMap<String, List<RuleResult>>();
        for (String id : tvRuleIds) byRule.put(id, new ArrayList<RuleResult>());
        for (RuleResult r : results) {
            if (tvRuleIds.contains(r.ruleId())) {
                byRule.get(r.ruleId()).add(r);
            }
        }

        for (Map.Entry<String, List<RuleResult>> entry : byRule.entrySet()) {
            String ruleId = entry.getKey();
            String tagName = ruleToTag.get(ruleId);
            List<RuleResult> ruleResults = entry.getValue();

            int pass = 0, fail = 0, skip = 0;
            for (RuleResult r : ruleResults) {
                switch (r.status()) {
                    case PASS: pass++; break;
                    case FAIL: fail++; break;
                    case SKIPPED: skip++; break;
                    default: break;
                }
            }

            System.out.println();
            System.out.println("  Rule: " + ruleId + "  (tag=\"" + tagName + "\")");
            System.out.println("  PASS=" + pass + "  FAIL=" + fail + "  SKIP=" + skip);

            int absentCount = 0, wrongValueCount = 0;
            int shownFails = 0;

            System.out.println("  Sample failing elements:");
            for (RuleResult r : ruleResults) {
                if (r.status() != RuleStatus.FAIL) continue;

                ElementRecord rec = byGuid.get(r.elementGuid());

                boolean tagPresent = false;
                String tagValue = null;
                if (rec != null) {
                    tagValue = findTagIgnoreCase(rec.tagValues(), tagName);
                    tagPresent = tagValue != null && !tagValue.isEmpty();
                }

                if (!tagPresent) {
                    absentCount++;
                } else {
                    wrongValueCount++;
                }

                if (shownFails < FAIL_SAMPLE) {
                    String name = rec != null ? rec.name() : r.elementGuid();
                    ElementKind kind = rec != null ? rec.kind() : null;
                    Map<String, String> tags = rec != null
                            ? rec.tagValues()
                            : Collections.<String, String>emptyMap();
                    System.out.println("    [FAIL] " + name
                            + " (" + kind + ")"
                            + " | tag '" + tagName + "'="
                            + (tagPresent ? "'" + tagValue + "'" : "<absent>")
                            + " | allTags=" + truncateTags(tags, 5)
                            + " | msg: " + truncate(r.message(), 120));
                    shownFails++;
                }
            }
            if (fail > FAIL_SAMPLE) {
                System.out.println("    ... and " + (fail - FAIL_SAMPLE) + " more failures");
            }
            System.out.println("  -> Tag absent: " + absentCount
                    + "  Tag present but wrong value: " + wrongValueCount);
        }
    }

    // ── Phase 7 ──────────────────────────────────────────────────────────────

    private static void failureMessageFrequency(List<RuleResult> results) {
        // key = ruleId + " >> " + truncated message
        Map<String, Integer> freq = new LinkedHashMap<String, Integer>();
        for (RuleResult r : results) {
            if (r.status() != RuleStatus.FAIL) continue;
            String key = r.ruleId() + " >> " + truncate(r.message(), 100);
            Integer prev = freq.get(key);
            freq.put(key, prev == null ? 1 : prev + 1);
        }

        List<Map.Entry<String, Integer>> sorted =
                new ArrayList<Map.Entry<String, Integer>>(freq.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });

        System.out.printf("  %8s  %s%n", "Count", "RuleId >> Message");
        System.out.println("  " + dashes(90));
        int shown = 0;
        for (Map.Entry<String, Integer> e : sorted) {
            if (shown++ >= 30) break;
            System.out.printf("  %8d  %s%n", e.getValue(), e.getKey());
        }
        if (freq.size() > 30) {
            System.out.println("  ... and " + (freq.size() - 30) + " more patterns");
        }
        System.out.println("  (Total distinct failure patterns: " + freq.size() + ")");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String findTagIgnoreCase(Map<String, String> tags, String tagName) {
        if (tagName == null) return null;
        String exact = tags.get(tagName);
        if (exact != null) return exact;
        String lower = tagName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : tags.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "<null>";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String truncateTags(Map<String, String> tags, int max) {
        if (tags.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : tags.entrySet()) {
            if (i++ >= max) { sb.append(", ...(").append(tags.size() - max).append(" more)"); break; }
            if (i > 1) sb.append(", ");
            sb.append(e.getKey()).append("=").append(truncate(e.getValue(), 30));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String dashes(int n) {
        char[] c = new char[n];
        Arrays.fill(c, '-');
        return new String(c);
    }
}
