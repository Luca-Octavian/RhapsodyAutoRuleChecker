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
        String yamlPath = "src/main/resources/PPTTest.yaml";

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
            char[] tmp = new char[68];
            java.util.Arrays.fill(tmp, '─');
            System.out.println("  " + new String(tmp));

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
         // Debug: dump real stereotypes on blocks (why does BLOCK_MUST_HAVE_STEREOTYPE only pass 4?)
            System.out.println("\n--- Real stereotypes on first 25 blocks ---");
            int blockShown = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.BLOCK
                        && r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.INTERFACE_BLOCK) continue;

                System.out.println("  [" + r.kind() + "] " + r.name() + " | stereo=" + r.stereotypes());

                blockShown++;
                if (blockShown >= 25) break;
            }

            // Frequency table across ALL blocks
            java.util.Map<String, Integer> stereoFrequency = new java.util.TreeMap<>();
            int totalBlocks = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.BLOCK
                        && r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.INTERFACE_BLOCK) continue;
                totalBlocks++;
                if (r.stereotypes().isEmpty()) {
                    stereoFrequency.merge("<NONE>", 1, Integer::sum);
                }
                for (String s : r.stereotypes()) {
                    stereoFrequency.merge(s, 1, Integer::sum);
                }
            }
            System.out.println("\n--- Stereotype frequency across all " + totalBlocks + " blocks ---");
            for (Map.Entry<String, Integer> e : stereoFrequency.entrySet()) {
                System.out.println("  " + e.getKey() + " : " + e.getValue());
            }
         // Debug: check FlowPort direction methods
            System.out.println("\n--- FlowPort direction debug ---");
            int dirChecked = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.PORT_FLOW) continue;
                com.telelogic.rhapsody.core.IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle == null) continue;

                System.out.println("  " + r.name() + ":");

                // Try common direction methods
                String[] methods = {"getDirection", "getPortDirection", "getFlowDirection"};
                for (String m : methods) {
                    try {
                        java.lang.reflect.Method method = handle.getClass().getMethod(m);
                        Object val = method.invoke(handle);
                        System.out.println("    " + m + "=" + val);
                    } catch (NoSuchMethodException e) {
                        System.out.println("    " + m + "=<no method>");
                    } catch (Throwable t) {
                        System.out.println("    " + m + "=<error: " + t.getMessage() + ">");
                    }
                }

                // Try getPropertyValue for direction
                String[] props = {"direction", "Direction", "flowDirection", "FlowDirection", "portDirection"};
                for (String p : props) {
                    try {
                        String val = handle.getPropertyValue(p);
                        if (val != null && !val.trim().isEmpty()) {
                            System.out.println("    property[" + p + "]=" + val);
                        }
                    } catch (Throwable t) { /* skip */ }
                }

                // Try tags
                try {
                    java.lang.reflect.Method getTags = handle.getClass().getMethod("getTags");
                    Object tagsObj = getTags.invoke(handle);
                    if (tagsObj instanceof com.telelogic.rhapsody.core.IRPCollection) {
                        com.telelogic.rhapsody.core.IRPCollection tags = (com.telelogic.rhapsody.core.IRPCollection) tagsObj;
                        int tagCount = tags.getCount();
                        if (tagCount > 0) {
                            System.out.println("    tags count=" + tagCount);
                            for (int i = 1; i <= Math.min(tagCount, 5); i++) {
                                Object tag = tags.getItem(i);
                                if (tag instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                    com.telelogic.rhapsody.core.IRPModelElement tagElt = (com.telelogic.rhapsody.core.IRPModelElement) tag;
                                    String tagName = tagElt.getName();
                                    String tagVal = "";
                                    try {
                                        java.lang.reflect.Method getVal = tagElt.getClass().getMethod("getValue");
                                        Object v = getVal.invoke(tagElt);
                                        tagVal = v != null ? v.toString() : "<null>";
                                    } catch (Throwable t) { tagVal = "<no getValue>"; }
                                    System.out.println("      tag[" + tagName + "]=" + tagVal);
                                }
                            }
                        }
                    }
                } catch (Throwable t) { /* skip */ }

                dirChecked++;
                if (dirChecked >= 5) break;
            }

         // Debug: check first 10 flow ports for type and direction
            System.out.println("\n--- FlowPort type/direction debug ---");
            int fpChecked = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.PORT_FLOW) continue;
                
                System.out.println("  " + r.name()
                        + " | typeName=" + r.typeName().orElse("<none>")
                        + " | typeGuid=" + r.typeGuid().orElse("<none>")
                        + " | stereo=" + r.stereotypes()
                        + " | owner=" + r.ownerPath().orElse("<root>"));

                // Check via API
                com.telelogic.rhapsody.core.IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle != null) {
                    // Try getOtherClass
                    try {
                        java.lang.reflect.Method m = handle.getClass().getMethod("getOtherClass");
                        Object cls = m.invoke(handle);
                        System.out.println("    getOtherClass=" + (cls != null ? ((com.telelogic.rhapsody.core.IRPModelElement)cls).getName() : "<null>"));
                    } catch (Throwable t) { System.out.println("    getOtherClass=<error: " + t.getMessage() + ">"); }

                    // Try getType (may not exist)
                    try {
                        java.lang.reflect.Method m = handle.getClass().getMethod("getType");
                        Object cls = m.invoke(handle);
                        System.out.println("    getType=" + (cls != null ? ((com.telelogic.rhapsody.core.IRPModelElement)cls).getName() : "<null>"));
                    } catch (Throwable t) { System.out.println("    getType=<no method>"); }

                    // Check nested elements for type info
                    try {
                        com.telelogic.rhapsody.core.IRPCollection nested = handle.getNestedElements();
                        int count = nested != null ? nested.getCount() : 0;
                        if (count > 0) {
                            System.out.println("    nested elements: " + count);
                            for (int i = 1; i <= Math.min(count, 5); i++) {
                                Object o = nested.getItem(i);
                                if (o instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                    com.telelogic.rhapsody.core.IRPModelElement n = (com.telelogic.rhapsody.core.IRPModelElement) o;
                                    System.out.println("      [" + i + "] meta=" + n.getMetaClass() + " | name=" + n.getName());
                                }
                            }
                        }
                    } catch (Throwable t) { /* ignore */ }
                }

                fpChecked++;
                if (fpChecked >= 10) break;
            }

            


        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }
}