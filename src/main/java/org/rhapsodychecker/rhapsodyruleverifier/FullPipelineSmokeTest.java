package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.ResultFormatter;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;
import org.rhapsodychecker.rhapsodyruleverifier.export.ExcelReportExporter;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class FullPipelineSmokeTest {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        String yamlPath = "src/main/resources/bigtest.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        System.out.println("=== Rule Evaluation ===");
        System.out.println("Project: " + rpyxPath);
        System.out.println("Config:  " + yamlPath);
        System.out.println();

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // 1) Connect
            conn.connect(rpyxPath);
            System.out.println("[OK] Connected to: " + conn.getProject().getName());

            // 2) Load model
            RhapsodyModelSnapshot snapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            System.out.println("[OK] Loaded " + snapshot.records().size() + " elements");

            // 3) Index
            ElementIndex index = ElementIndex.build(snapshot.records());
            System.out.println("[OK] Index built");

            // 4) Config
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("[OK] Config loaded: " + config.enabledRules().size() + " rules enabled");

            // 5) Wire and run
            RhapsodyAliasResolver aliasResolver = new RhapsodyAliasResolver(config, snapshot);
            ElementSelector selector = new ElementSelector(index, config);
            RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                    aliasResolver, snapshot, config, index, selector);
            RuleEngine engine = new RuleEngine(config, selector, context);

            System.out.println("[OK] Running evaluation...\n");
            RuleEngine.EvaluationSummary summary = engine.evaluateWithSummary();

            // 6) Formatted report
            ResultFormatter formatter = new ResultFormatter(index);
            System.out.println(formatter.formatSummary(summary));

            // 7) Per-rule breakdown
            System.out.println("── Per-rule breakdown ────────────────────────────────────────");
            System.out.printf("  %-40s %7s %7s %7s %7s%n", "Rule ID", "Total", "Pass", "Fail", "Skip");
            System.out.println("  " + "─".repeat(68));

            Map<String, int[]> perRule = new LinkedHashMap<>();
            for (RuleResult r : summary.allResults()) {
                int[] counts = perRule.computeIfAbsent(r.ruleId(), k -> new int[3]);
                switch (r.status()) {
                    case PASS:    counts[0]++; break;
                    case FAIL:    counts[1]++; break;
                    case SKIPPED: counts[2]++; break;
                }
            }
            for (Map.Entry<String, int[]> entry : perRule.entrySet()) {
                int[] c = entry.getValue();
                int total = c[0] + c[1] + c[2];
                System.out.printf("  %-40s %7d %7d %7d %7d%n",
                        entry.getKey(), total, c[0], c[1], c[2]);
            }
            System.out.println();
            String excelOutputPath = "rule-failures-report.xlsx";
            ExcelReportExporter.exportFailures(summary.allResults(), index, excelOutputPath);
            System.out.println("[OK] Excel report exported to: " + Paths.get(excelOutputPath).toAbsolutePath());


        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }
}
