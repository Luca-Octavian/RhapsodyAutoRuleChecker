// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/FullPipelineSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.List;

public class FullPipelineSmokeTest {

    public static void main(String[] args) {
        String rpyxPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        String yamlPath = "src/main/resources/myfile.yaml";

        if (args.length >= 1) rpyxPath = args[0];
        if (args.length >= 2) yamlPath = args[1];

        System.out.println("=== Full Pipeline Smoke Test ===");
        System.out.println("Project: " + rpyxPath);
        System.out.println("Config:  " + yamlPath);
        System.out.println();

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // 1) Connect
            System.out.println("[Step 1] Connecting to Rhapsody...");
            conn.connect(rpyxPath);
            System.out.println("[PASS] Connected to project: " + conn.getProject().getName());

            // 2) Load model
            System.out.println("\n[Step 2] Loading model...");
            RhapsodyModelSnapshot snapshot = new RhapsodyModelLoader().loadModel(conn.getProject());
            System.out.println("[PASS] Loaded " + snapshot.records().size() + " elements");

            // 3) Build index
            System.out.println("\n[Step 3] Building index...");
            ElementIndex index = ElementIndex.build(snapshot.records());
            System.out.println("[PASS] Index built: " + index.repository().allRecords().size() + " elements indexed");

            // 4) Load config
            System.out.println("\n[Step 4] Loading config from: " + yamlPath);
            RuleCheckerConfig config = ConfigLoader.load(Paths.get(yamlPath));
            System.out.println("[PASS] Config loaded: " + config);

            // 5) Wire components
            System.out.println("\n[Step 5] Wiring components...");
            RhapsodyAliasResolver aliasResolver = new RhapsodyAliasResolver(config, snapshot);
            ElementSelector selector = new ElementSelector(index, config);
            RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                    aliasResolver, snapshot, config, index, selector);
            RuleEngine engine = new RuleEngine(config, selector, context);
            System.out.println("[PASS] Engine ready");

            // 6) Evaluate
            System.out.println("\n[Step 6] Running evaluation...");
            RuleEngine.EvaluationSummary summary = engine.evaluateWithSummary();
            System.out.println("[PASS] Evaluation complete: " + summary);

            // 7) Results
            System.out.println("\n=== Results ===");
            System.out.println("Total:   " + summary.totalCount());
            System.out.println("Passed:  " + summary.passCount());
            System.out.println("Failed:  " + summary.failCount());
            System.out.println("Skipped: " + summary.skipCount());
            
         // Debug: inspect relations on first 5 architecture blocks
            System.out.println("\n--- Relation debug (first 5 blocks) ---");
            int debugged = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.BLOCK) continue;
                com.telelogic.rhapsody.core.IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle == null) continue;

                System.out.println("Block: " + r.name() + " (path=" + r.ownerPath().orElse("<root>") + ")");

                // Outgoing dependencies
                try {
                    com.telelogic.rhapsody.core.IRPCollection deps = handle.getDependencies();
                    int depCount = deps != null ? deps.getCount() : 0;
                    System.out.println("  getDependencies() count: " + depCount);
                    for (int i = 1; i <= Math.min(depCount, 10); i++) {
                        Object o = deps.getItem(i);
                        if (o instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                            com.telelogic.rhapsody.core.IRPModelElement dep = (com.telelogic.rhapsody.core.IRPModelElement) o;
                            String meta = dep.getMetaClass();
                            String name = dep.getName();

                            // Stereotypes of the relation itself
                            java.util.List<String> stereos = new java.util.ArrayList<>();
                            try {
                                com.telelogic.rhapsody.core.IRPCollection sts = dep.getStereotypes();
                                if (sts != null) {
                                    for (int j = 1; j <= sts.getCount(); j++) {
                                        Object so = sts.getItem(j);
                                        if (so instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                            stereos.add(((com.telelogic.rhapsody.core.IRPModelElement) so).getName());
                                        }
                                    }
                                }
                            } catch (Throwable t) { stereos.add("<error>"); }

                            // Other end
                            String otherEnd = "<unknown>";
                            if (o instanceof com.telelogic.rhapsody.core.IRPDependency) {
                                try {
                                    com.telelogic.rhapsody.core.IRPModelElement dependsOn =
                                            ((com.telelogic.rhapsody.core.IRPDependency) o).getDependsOn();
                                    if (dependsOn != null) {
                                        otherEnd = dependsOn.getName() + " (meta=" + dependsOn.getMetaClass() + ")";
                                    }
                                } catch (Throwable t) { otherEnd = "<error>"; }
                            }

                            System.out.println("    [" + i + "] meta=" + meta
                                    + " | name=" + name
                                    + " | stereo=" + stereos
                                    + " | dependsOn=" + otherEnd);
                        }
                    }
                } catch (Throwable t) {
                    System.out.println("  getDependencies() error: " + t.getMessage());
                }

                // Incoming references
                try {
                    com.telelogic.rhapsody.core.IRPCollection refs = handle.getReferences();
                    int refCount = refs != null ? refs.getCount() : 0;
                    System.out.println("  getReferences() count: " + refCount);
                    for (int i = 1; i <= Math.min(refCount, 10); i++) {
                        Object o = refs.getItem(i);
                        if (o instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                            com.telelogic.rhapsody.core.IRPModelElement ref = (com.telelogic.rhapsody.core.IRPModelElement) o;
                            String meta = ref.getMetaClass();
                            String name = ref.getName();

                            java.util.List<String> stereos = new java.util.ArrayList<>();
                            try {
                                com.telelogic.rhapsody.core.IRPCollection sts = ref.getStereotypes();
                                if (sts != null) {
                                    for (int j = 1; j <= sts.getCount(); j++) {
                                        Object so = sts.getItem(j);
                                        if (so instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                            stereos.add(((com.telelogic.rhapsody.core.IRPModelElement) so).getName());
                                        }
                                    }
                                }
                            } catch (Throwable t) { stereos.add("<error>"); }

                            System.out.println("    [" + i + "] meta=" + meta
                                    + " | name=" + name
                                    + " | stereo=" + stereos);
                        }
                    }
                } catch (Throwable t) {
                    System.out.println("  getReferences() error: " + t.getMessage());
                }

                debugged++;
                if (debugged >= 5) break;
            }
            
         // Debug: inspect dependencies on Requirements
            System.out.println("\n--- Requirement relations debug ---");
            int reqDebugged = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (!"Requirement".equals(r.metaClass())) continue;
                com.telelogic.rhapsody.core.IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle == null) continue;

                // Only show requirements that have dependencies
                try {
                    com.telelogic.rhapsody.core.IRPCollection deps = handle.getDependencies();
                    int depCount = deps != null ? deps.getCount() : 0;
                    if (depCount == 0) continue;

                    System.out.println("Requirement: " + r.name() + " (path=" + r.ownerPath().orElse("<root>") + ")");
                    System.out.println("  getDependencies() count: " + depCount);
                    for (int i = 1; i <= Math.min(depCount, 10); i++) {
                        Object o = deps.getItem(i);
                        if (o instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                            com.telelogic.rhapsody.core.IRPModelElement dep = (com.telelogic.rhapsody.core.IRPModelElement) o;

                            java.util.List<String> stereos = new java.util.ArrayList<>();
                            try {
                                com.telelogic.rhapsody.core.IRPCollection sts = dep.getStereotypes();
                                if (sts != null) {
                                    for (int j = 1; j <= sts.getCount(); j++) {
                                        Object so = sts.getItem(j);
                                        if (so instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                            stereos.add(((com.telelogic.rhapsody.core.IRPModelElement) so).getName());
                                        }
                                    }
                                }
                            } catch (Throwable t) { stereos.add("<error>"); }

                            String otherEnd = "<unknown>";
                            if (o instanceof com.telelogic.rhapsody.core.IRPDependency) {
                                try {
                                    com.telelogic.rhapsody.core.IRPModelElement dependsOn =
                                            ((com.telelogic.rhapsody.core.IRPDependency) o).getDependsOn();
                                    if (dependsOn != null) {
                                        otherEnd = dependsOn.getName()
                                                + " (meta=" + dependsOn.getMetaClass()
                                                + ", stereo=" + dependsOn.getInterfaceName() + ")";
                                    }
                                } catch (Throwable t) { otherEnd = "<error>"; }
                            }

                            System.out.println("    [" + i + "] meta=" + dep.getMetaClass()
                                    + " | name=" + dep.getName()
                                    + " | stereo=" + stereos
                                    + " | dependsOn=" + otherEnd);
                        }
                    }
                    reqDebugged++;
                    if (reqDebugged >= 10) break;
                } catch (Throwable t) {
                    // ignore
                }
            }
            if (reqDebugged == 0) {
                System.out.println("  No requirements with dependencies found.");
                System.out.println("  Satisfy/refine links may use a different mechanism in this model.");
            }

         // Debug: find ANY element with satisfy/refine stereotype anywhere in the model
            System.out.println("\n--- Global satisfy/refine search ---");
            int found = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                boolean hasSatisfy = false;
                for (String s : r.stereotypes()) {
                    if (s.toLowerCase().contains("satisfy") || s.toLowerCase().contains("refine")
                            || s.toLowerCase().contains("trace") || s.toLowerCase().contains("derive")) {
                        hasSatisfy = true;
                        break;
                    }
                }
                if (hasSatisfy) {
                    System.out.println("  meta=" + r.metaClass()
                            + " | name=" + r.name()
                            + " | stereo=" + r.stereotypes()
                            + " | kind=" + r.kind()
                            + " | path=" + r.ownerPath().orElse("<root>"));
                    found++;
                    if (found >= 30) {
                        System.out.println("  ... (showing first 30)");
                        break;
                    }
                }
            }
            if (found == 0) {
                System.out.println("  None found in indexed elements.");

                // Try searching ALL nested elements (including ones we may have filtered)
                System.out.println("\n  Searching raw Rhapsody collection for satisfy/refine...");
                com.telelogic.rhapsody.core.IRPCollection all = conn.getProject().getNestedElementsRecursive();
                int rawFound = 0;
                int total = all != null ? all.getCount() : 0;
                for (int i = 1; i <= total; i++) {
                    try {
                        Object o = all.getItem(i);
                        if (!(o instanceof com.telelogic.rhapsody.core.IRPModelElement)) continue;
                        com.telelogic.rhapsody.core.IRPModelElement elt = (com.telelogic.rhapsody.core.IRPModelElement) o;
                        String meta = elt.getMetaClass();

                        // Check if it's a Dependency type element
                        if (meta != null && (meta.contains("Dependency") || meta.contains("dependency"))) {
                            java.util.List<String> stereos = new java.util.ArrayList<>();
                            try {
                                com.telelogic.rhapsody.core.IRPCollection sts = elt.getStereotypes();
                                if (sts != null) {
                                    for (int j = 1; j <= sts.getCount(); j++) {
                                        Object so = sts.getItem(j);
                                        if (so instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                            stereos.add(((com.telelogic.rhapsody.core.IRPModelElement) so).getName());
                                        }
                                    }
                                }
                            } catch (Throwable t) { stereos.add("<error>"); }

                            boolean relevant = false;
                            for (String s : stereos) {
                                if (s.toLowerCase().contains("satisfy") || s.toLowerCase().contains("refine")
                                        || s.toLowerCase().contains("trace") || s.toLowerCase().contains("derive")) {
                                    relevant = true;
                                    break;
                                }
                            }
                            if (relevant) {
                                String depName = elt.getName();
                                String otherEnd = "<unknown>";
                                if (elt instanceof com.telelogic.rhapsody.core.IRPDependency) {
                                    try {
                                        com.telelogic.rhapsody.core.IRPModelElement dep =
                                                ((com.telelogic.rhapsody.core.IRPDependency) elt).getDependsOn();
                                        if (dep != null) {
                                            otherEnd = dep.getName() + " (meta=" + dep.getMetaClass() + ")";
                                        }
                                    } catch (Throwable t) { otherEnd = "<error>"; }
                                }
                                // Also get the owner (source of the dependency)
                                String owner = "<unknown>";
                                try {
                                    com.telelogic.rhapsody.core.IRPModelElement own = elt.getOwner();
                                    if (own != null) {
                                        owner = own.getName() + " (meta=" + own.getMetaClass() + ")";
                                    }
                                } catch (Throwable t) { owner = "<error>"; }

                                System.out.println("    meta=" + meta
                                        + " | name=" + depName
                                        + " | stereo=" + stereos
                                        + " | owner(source)=" + owner
                                        + " | dependsOn(target)=" + otherEnd);
                                rawFound++;
                                if (rawFound >= 30) {
                                    System.out.println("    ... (showing first 30)");
                                    break;
                                }
                            }
                        }
                    } catch (Throwable t) { /* skip */ }
                }
                if (rawFound == 0) {
                    System.out.println("  No satisfy/refine dependencies found in raw collection either.");
                    System.out.println("  This model may not use satisfy/refine links, or they are stored differently.");
                }
            }
            
         // Debug: check if blocks have nested Dependencies (direct children)
            System.out.println("\n--- Nested dependency debug ---");
            int blocksChecked = 0;
            for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                if (r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.BLOCK) continue;
                com.telelogic.rhapsody.core.IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle == null) continue;

                int depCount = 0;
                int totalNested = 0;
                try {
                    com.telelogic.rhapsody.core.IRPCollection nested = handle.getNestedElements();
                    if (nested != null) {
                        totalNested = nested.getCount();
                        for (int i = 1; i <= totalNested; i++) {
                            Object o = nested.getItem(i);
                            if (o instanceof com.telelogic.rhapsody.core.IRPModelElement) {
                                String meta = ((com.telelogic.rhapsody.core.IRPModelElement) o).getMetaClass();
                                if ("Dependency".equals(meta)) depCount++;
                            }
                        }
                    }
                } catch (Throwable t) { /* ignore */ }

                if (depCount > 0 || blocksChecked < 5) {
                    System.out.println("  " + r.name()
                            + " | nested=" + totalNested
                            + " | dependencies=" + depCount
                            + " | path=" + r.ownerPath().orElse("<root>"));
                }
                if (depCount > 0) blocksChecked++;
                if (blocksChecked >= 10) break;
            }
            if (blocksChecked == 0) {
                System.out.println("  No blocks have direct nested Dependencies.");
                
                // Check: are Dependencies owned by Parts/sub-elements of blocks?
                System.out.println("\n  Checking which elements own satisfy/refine Dependencies...");
                int ownerChecked = 0;
                for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                    if (!"Dependency".equals(r.metaClass())) continue;
                    boolean relevant = false;
                    for (String s : r.stereotypes()) {
                        if (s.equalsIgnoreCase("satisfy") || s.equalsIgnoreCase("refine")) {
                            relevant = true;
                            break;
                        }
                    }
                    if (!relevant) continue;

                    // Find the owner
                    com.telelogic.rhapsody.core.IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                    if (handle == null) continue;
                    String ownerName = "<unknown>";
                    String ownerMeta = "<unknown>";
                    String ownerGuid = "<unknown>";
                    try {
                        com.telelogic.rhapsody.core.IRPModelElement owner = handle.getOwner();
                        if (owner != null) {
                            ownerName = owner.getName();
                            ownerMeta = owner.getMetaClass();
                            ownerGuid = owner.getGUID();
                        }
                    } catch (Throwable t) { /* ignore */ }

                    System.out.println("    dep=" + r.name()
                            + " | stereo=" + r.stereotypes()
                            + " | ownerName=" + ownerName
                            + " | ownerMeta=" + ownerMeta);
                    ownerChecked++;
                    if (ownerChecked >= 15) {
                        System.out.println("    ... (showing first 15)");
                        break;
                    }
                }
            }
            
         // Quick check: manually test collectRelations on a block we KNOW has dependencies
            System.out.println("\n--- Manual relation count test ---");
            String[] testBlocks = {"SE_VDU_Logical", "Damping_Chain_Logical", "HSM_SW_Execution", "Cross_Domain_Realization"};
            for (String blockName : testBlocks) {
                for (org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord r : snapshot.records()) {
                    if (!r.name().equals(blockName) || r.kind() != org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.BLOCK) continue;
                    
                    int relCount = context.countMatchingRelations(r,
                            new org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RelationExistsRule.RelationQuery(
                                    org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationKind.DEPENDENCY,
                                    org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationDirection.ANY,
                                    java.util.Arrays.asList("satisfy", "refine"),
                                    null, null, null, null
                            ));
                    System.out.println("  " + blockName + " | satisfy/refine count=" + relCount
                            + " | path=" + r.ownerPath().orElse("<root>"));
                    break;
                }
            }


            // Show failures (first 30)
            List<RuleResult> failures = summary.failed();
            if (!failures.isEmpty()) {
                int limit = Math.min(30, failures.size());
                System.out.println("\n--- Failures (first " + limit + " of " + failures.size() + ") ---");
                for (int i = 0; i < limit; i++) {
                    RuleResult r = failures.get(i);
                    String elementName = index.repository().get(r.elementGuid())
                            .map(rec -> rec.name())
                            .orElse("<unknown>");
                    String elementPath = index.repository().get(r.elementGuid())
                            .flatMap(rec -> rec.ownerPath())
                            .orElse("<root>");
                    System.out.println("  [FAIL] " + r.ruleId()
                            + " | " + elementName
                            + " | path=" + elementPath
                            + " | " + r.message());
                }
                if (failures.size() > limit) {
                    System.out.println("  ... (" + (failures.size() - limit) + " more)");
                }
            }

            // Show skipped (first 10)
            List<RuleResult> skipped = summary.skipped();
            if (!skipped.isEmpty()) {
                int limit = Math.min(10, skipped.size());
                System.out.println("\n--- Skipped (first " + limit + " of " + skipped.size() + ") ---");
                for (int i = 0; i < limit; i++) {
                    RuleResult r = skipped.get(i);
                    String elementName = index.repository().get(r.elementGuid())
                            .map(rec -> rec.name())
                            .orElse("<unknown>");
                    System.out.println("  [SKIP] " + r.ruleId()
                            + " | " + elementName
                            + " | " + r.message());
                }
            }

            // Show passes count per rule
            System.out.println("\n--- Pass count per rule ---");
            summary.passed().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            RuleResult::ruleId, java.util.stream.Collectors.counting()))
                    .forEach((ruleId, count) ->
                            System.out.println("  " + ruleId + ": " + count + " passed"));

            // Show fail count per rule
            System.out.println("\n--- Fail count per rule ---");
            summary.failed().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            RuleResult::ruleId, java.util.stream.Collectors.counting()))
                    .forEach((ruleId, count) ->
                            System.out.println("  " + ruleId + ": " + count + " failed"));

            System.out.println("\n[DONE] Full pipeline smoke test complete.");

        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }
}
