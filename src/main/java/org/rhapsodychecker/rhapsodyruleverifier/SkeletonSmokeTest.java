package org.rhapsodychecker.rhapsodyruleverifier;

import java.util.ArrayList;
import java.util.List;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import com.telelogic.rhapsody.core.*;

public class SkeletonSmokeTest {

    public static void main(String[] args) {
        String projectPath = "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // 1) Test connection
            conn.connect(projectPath);
            System.out.println("[PASS] Connected to project: " + conn.getProject().getName());

            // 2) Test model loading
            RhapsodyModelLoader loader = new RhapsodyModelLoader();
            RhapsodyModelSnapshot snapshot = loader.loadModel(conn.getProject());
            System.out.println("[PASS] Loaded " + snapshot.records().size() + " elements");

            // 3) Verify element kinds are classified
            int blocks = 0, ports = 0, parts = 0, other = 0;
            for (ElementRecord rec : snapshot.records()) {
                switch (rec.kind()) {
                    case BLOCK:          blocks++; break;
                    case INTERFACE_BLOCK: blocks++; break;
                    case PORT_FULL:
                    case PORT_PROXY:
                    case PORT_FLOW:
                    case PORT:           ports++;  break;
                    case PART:           parts++;  break;
                    default:             other++;  break;
                }
            }
            
            System.out.println("\n--- Part details (first 20) ---");
            int partsPrinted = 0;
            for (ElementRecord r : snapshot.records()) {
                if (r.kind() == ElementKind.PART) {
                    System.out.println("  " + r.name()
                            + " | type=" + r.typeName().orElse("<no type>")
                            + " | owner=" + r.ownerPath().orElse("<root>")
                            + " | stereo=" + r.stereotypes());
                    partsPrinted++;
                    if (partsPrinted >= 20) {
                        System.out.println("  ... (showing first 20)");
                        break;
                    }
                }
            }
            if (partsPrinted == 0) {
                System.out.println("  No parts found.");
            }
            System.out.println("[INFO] Blocks: " + blocks + ", Ports: " + ports
                    + ", Parts: " + parts + ", Other: " + other);
            
         // Debug: find potential parts (Attributes or elements with "part" in stereotype/metaclass)
            System.out.println("\n--- Part candidates ---");
            int partCandidates = 0;
            for (ElementRecord r : snapshot.records()) {
                String meta = r.metaClass().toLowerCase();
                boolean stereoHasPart = false;
                for (String s : r.stereotypes()) {
                    if (s.toLowerCase().contains("part")) {
                        stereoHasPart = true;
                        break;
                    }
                }
                if (meta.contains("attribute") || meta.contains("part") || stereoHasPart) {
                    System.out.println("  meta=" + r.metaClass()
                            + " | name=" + r.name()
                            + " | stereo=" + r.stereotypes()
                            + " | kind=" + r.kind()
                            + " | path=" + r.ownerPath().orElse("<root>"));
                    partCandidates++;
                    if (partCandidates >= 20) {
                        System.out.println("  ... (showing first 20)");
                        break;
                    }
                }
            }
            if (partCandidates == 0) {
                System.out.println("  None found. Parts may not be included in getNestedElementsRecursive().");
                System.out.println("  May need a second pass: iterate blocks and call getAttributes()/getParts().");
            }
            
         // Debug: inspect attributes of first 5 blocks
            System.out.println("\n--- Block attributes debug ---");
            int blocksInspected = 0;
            for (ElementRecord r : snapshot.records()) {
                if (r.kind() != ElementKind.BLOCK) continue;
                IRPModelElement blockElt = snapshot.handleByGuid().get(r.guid());
                if (blockElt == null || !(blockElt instanceof IRPClassifier)) continue;

                IRPClassifier cls = (IRPClassifier) blockElt;
                System.out.println("Block: " + r.name() + " (path=" + r.ownerPath().orElse("<root>") + ")");

                // Check getAttributes()
                try {
                    IRPCollection attrs = cls.getAttributes();
                    int attrCount = attrs != null ? attrs.getCount() : 0;
                    System.out.println("  getAttributes() count: " + attrCount);
                    for (int i = 1; i <= Math.min(attrCount, 10); i++) {
                        Object o = attrs.getItem(i);
                        if (o instanceof IRPModelElement) {
                            IRPModelElement a = (IRPModelElement) o;
                            System.out.println("    [" + i + "] meta=" + a.getMetaClass()
                                    + " | name=" + a.getName()
                                    + " | interface=" + a.getInterfaceName());
                            // Print stereotypes
                            try {
                                IRPCollection sts = a.getStereotypes();
                                int stCount = sts != null ? sts.getCount() : 0;
                                List<String> stNames = new ArrayList<>();
                                for (int j = 1; j <= stCount; j++) {
                                    Object so = sts.getItem(j);
                                    if (so instanceof IRPModelElement) {
                                        stNames.add(((IRPModelElement) so).getName());
                                    }
                                }
                                System.out.println("      stereotypes=" + stNames);
                            } catch (Throwable t) {
                                System.out.println("      stereotypes=<error>");
                            }
                            // Print type if attribute
                            if (o instanceof IRPAttribute) {
                                try {
                                    IRPClassifier type = ((IRPAttribute) o).getType();
                                    System.out.println("      type=" + (type != null ? type.getName() : "<null>"));
                                } catch (Throwable t) {
                                    System.out.println("      type=<error>");
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    System.out.println("  getAttributes() error: " + t.getMessage());
                }

                // Also try getNestedElements() in case parts are nested differently
                try {
                    IRPCollection nested = cls.getNestedElements();
                    int nestedCount = nested != null ? nested.getCount() : 0;
                    System.out.println("  getNestedElements() count: " + nestedCount);
                    for (int i = 1; i <= Math.min(nestedCount, 10); i++) {
                        Object o = nested.getItem(i);
                        if (o instanceof IRPModelElement) {
                            IRPModelElement n = (IRPModelElement) o;
                            System.out.println("    [" + i + "] meta=" + n.getMetaClass()
                                    + " | name=" + n.getName());
                        }
                    }
                } catch (Throwable t) {
                    System.out.println("  getNestedElements() error: " + t.getMessage());
                }

                blocksInspected++;
                if (blocksInspected >= 5) break;
            }

            // 4) Print a few sample records for visual inspection
            int sampleCount = Math.min(10, snapshot.records().size());
            System.out.println("\n--- Sample elements (first " + sampleCount + ") ---");
            for (int i = 0; i < sampleCount; i++) {
                ElementRecord r = snapshot.records().get(i);
                System.out.println("  " + r.kind()
                        + " | " + r.name()
                        + " | meta=" + r.metaClass()
                        + " | stereo=" + r.stereotypes()
                        + " | path=" + r.ownerPath().orElse("<root>"));
            }

            // 5) Verify stereotypes are being read (spot check)
            long withStereotypes = snapshot.records().stream()
                    .filter(r -> !r.stereotypes().isEmpty())
                    .count();
            System.out.println("\n[INFO] Elements with stereotypes: " + withStereotypes
                    + " / " + snapshot.records().size());

            // 6) Verify no duplicate GUIDs
            long uniqueGuids = snapshot.records().stream()
                    .map(ElementRecord::guid)
                    .distinct()
                    .count();
            if (uniqueGuids == snapshot.records().size()) {
                System.out.println("[PASS] All GUIDs are unique");
            } else {
                System.out.println("[FAIL] Duplicate GUIDs detected: "
                        + snapshot.records().size() + " records, " + uniqueGuids + " unique");
            }

            // 7) Verify snapshot handle map matches records
            if (snapshot.handleByGuid().size() == snapshot.records().size()) {
                System.out.println("[PASS] Handle map size matches record count");
            } else {
                System.out.println("[WARN] Handle map size (" + snapshot.handleByGuid().size()
                        + ") differs from record count (" + snapshot.records().size() + ")");
            }

            System.out.println("\n[DONE] Smoke test complete.");

        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }
}
