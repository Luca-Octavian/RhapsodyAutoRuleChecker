package org.rhapsodychecker.rhapsodyruleverifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyPortInfoResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.SetOps;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.PortInfo;
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

            // 2.5) Build the index over the loaded records
            ElementIndex index = ElementIndex.build(snapshot.records());
            System.out.println("[PASS] Index built from " + index.repository().allRecords().size() + " elements");

            // 3) Verify element kinds are classified (manual loop, kept for cross-check)
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

            // 3.5) Same counts, but computed via the index instead of manual iteration
            int blocksViaIndex = index.guidsByKind(ElementKind.BLOCK).size()
                    + index.guidsByKind(ElementKind.INTERFACE_BLOCK).size();
            int portsViaIndex = index.guidsByKind(ElementKind.PORT_FULL).size()
                    + index.guidsByKind(ElementKind.PORT_PROXY).size()
                    + index.guidsByKind(ElementKind.PORT_FLOW).size()
                    + index.guidsByKind(ElementKind.PORT).size();
            int partsViaIndex = index.guidsByKind(ElementKind.PART).size();
            int otherViaIndex = snapshot.records().size() - blocksViaIndex - portsViaIndex - partsViaIndex;

            System.out.println("\n--- Kind counts: manual loop vs index (should match) ---");
            System.out.println("  Blocks: manual=" + blocks + " | index=" + blocksViaIndex
                    + (blocks == blocksViaIndex ? "  [MATCH]" : "  [MISMATCH]"));
            System.out.println("  Ports:  manual=" + ports + " | index=" + portsViaIndex
                    + (ports == portsViaIndex ? "  [MATCH]" : "  [MISMATCH]"));
            System.out.println("  Parts:  manual=" + parts + " | index=" + partsViaIndex
                    + (parts == partsViaIndex ? "  [MATCH]" : "  [MISMATCH]"));
            System.out.println("  Other:  manual=" + other + " | index=" + otherViaIndex
                    + (other == otherViaIndex ? "  [MATCH]" : "  [MISMATCH]"));

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

            // --- New: Port direction & multiplicity ---
            Set<String> portGuids = new LinkedHashSet<>();
            portGuids.addAll(index.guidsByKind(ElementKind.PORT));
            portGuids.addAll(index.guidsByKind(ElementKind.PORT_FULL));
            portGuids.addAll(index.guidsByKind(ElementKind.PORT_PROXY));
            portGuids.addAll(index.guidsByKind(ElementKind.PORT_FLOW));
            List<ElementRecord> portRecords = index.toRecords(portGuids);

            RhapsodyPortInfoResolver portResolver = new RhapsodyPortInfoResolver(snapshot);

            int in = 0, out = 0, inout = 0, noneDir = 0, unknown = 0;
            int limit = Math.min(20, portRecords.size());
            System.out.println("\n--- Port direction/multiplicity (first " + limit + ") ---");
            for (int i = 0; i < limit; i++) {
                ElementRecord pr = portRecords.get(i);
                PortInfo info = portResolver.resolve(pr);
                switch (info.direction()) {
                    case IN: in++; break;
                    case OUT: out++; break;
                    case INOUT: inout++; break;
                    case NONE: noneDir++; break;
                    default: unknown++; break;
                }
                System.out.println("  " + pr.name()
                        + " | kind=" + pr.kind()
                        + " | dir=" + info.direction() + " [" + info.directionSource() + "]"
                        + " | mult=" + info.multiplicity() + " [" + info.multiplicitySource() + "]"
                        + " | owner=" + pr.ownerPath().orElse("<root>"));
            }
            System.out.println("[INFO] Port direction counts (sample " + limit + "): "
                    + "IN=" + in + ", OUT=" + out + ", INOUT=" + inout + ", NONE=" + noneDir + ", UNKNOWN=" + unknown);
            
            System.out.println("\n--- Port stereotype/interface summary ---");
            int withPortStereotype = 0, withProvidedIface = 0, withRequiredIface = 0;
            for (ElementRecord pr : portRecords) {
                if (pr.kind() != ElementKind.PORT) {
                    withPortStereotype++; // has FullPort/ProxyPort/FlowPort stereotype
                }
                IRPModelElement handle = snapshot.handleByGuid().get(pr.guid());
                if (handle instanceof IRPPort) {
                    IRPPort p = (IRPPort) handle;
                    try {
                        IRPCollection prov = p.getProvidedInterfaces();
                        if (prov != null && prov.getCount() > 0) withProvidedIface++;
                    } catch (Throwable ignored) {}
                    try {
                        IRPCollection req = p.getRequiredInterfaces();
                        if (req != null && req.getCount() > 0) withRequiredIface++;
                    } catch (Throwable ignored) {}
                }
            }
            System.out.println("  Ports with FullPort/ProxyPort/FlowPort stereotype: " + withPortStereotype + " / " + portRecords.size());
            System.out.println("  Ports with provided interfaces: " + withProvidedIface);
            System.out.println("  Ports with required interfaces: " + withRequiredIface);
            if (withPortStereotype == 0 && withProvidedIface == 0 && withRequiredIface == 0) {
                System.out.println("  [INFO] Direction may be encoded in naming convention (C_IN_*, C_OUT_*) or not applicable for this model.");
            }

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
            
         // Debug: inspect how Flow Ports appear in the model
            System.out.println("\n--- Flow Port investigation ---");
            int inspected = 0;
            for (ElementRecord r : snapshot.records()) {
                if (!r.kind().isPortKind()) continue;
                IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle == null) continue;

                // Check interface name (reveals the actual Rhapsody type)
                String ifaceName = "";
                try { ifaceName = handle.getInterfaceName(); } catch (Throwable t) {}

                // Check metaClass more carefully
                String metaClass = "";
                try { metaClass = handle.getMetaClass(); } catch (Throwable t) {}

                // Check all user-defined properties/tags
                String allTags = "";
                try {
                    java.lang.reflect.Method m = handle.getClass().getMethod("getUserDefinedMetaClass");
                    Object udmc = m.invoke(handle);
                    if (udmc != null) allTags = udmc.toString();
                } catch (Throwable t) {
                    allTags = "<no userDefinedMetaClass>";
                }

                // Print first 30 ports with full details
                if (inspected < 30) {
                    System.out.println("  name=" + r.name()
                            + " | metaClass=" + metaClass
                            + " | interfaceName=" + ifaceName
                            + " | userDefinedMeta=" + allTags
                            + " | stereo=" + r.stereotypes()
                            + " | owner=" + r.ownerPath().orElse("<root>"));
                }
                inspected++;
            }
            System.out.println("[INFO] Total ports inspected: " + inspected);

            // Also: search ALL elements for anything with "flow" in name/stereotype/metaclass
            System.out.println("\n--- Elements with 'flow' in meta/stereotype/name ---");
            int flowFound = 0;
            for (ElementRecord r : snapshot.records()) {
                boolean match = r.metaClass().toLowerCase().contains("flow")
                        || r.name().toLowerCase().contains("flow");
                if (!match) {
                    for (String s : r.stereotypes()) {
                        if (s.toLowerCase().contains("flow")) { match = true; break; }
                    }
                }
                if (match) {
                    System.out.println("  name=" + r.name()
                            + " | meta=" + r.metaClass()
                            + " | kind=" + r.kind()
                            + " | stereo=" + r.stereotypes()
                            + " | owner=" + r.ownerPath().orElse("<root>"));
                    flowFound++;
                    if (flowFound >= 30) {
                        System.out.println("  ... (showing first 30)");
                        break;
                    }
                }
            }
            if (flowFound == 0) {
                System.out.println("  None found. Flow ports may use a different naming/classification in this model.");
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

            long withDescription = snapshot.records().stream()
                    .filter(r -> r.description().isPresent() && !r.description().get().isEmpty())
                    .count();
            System.out.println("[INFO] Elements with description: " + withDescription + " / " + snapshot.records().size());


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

            // 8) Compound index query demo: elements that are metaclass "Class"
            Set<String> classGuids = index.guidsByMetaClass("Class", true);
            Set<String> blockOrComponent = SetOps.union(List.of(
                    index.guidsByStereotype("Block", true),
                    index.guidsByStereotype("Component", true)
            ));
            Set<String> archElementGuids = SetOps.intersect(classGuids, blockOrComponent);
            List<ElementRecord> archElements = index.toRecords(archElementGuids);

            System.out.println("\n--- Compound index query: Class + (Block or Component) stereotype ---");
            System.out.println("[INFO] Architecture elements found: " + archElements.size());
            int archPrinted = 0;
            for (ElementRecord r : archElements) {
                System.out.println("  " + r.name() + " | path=" + r.ownerPath().orElse("<root>"));
                archPrinted++;
                if (archPrinted >= 20) {
                    System.out.println("  ... (showing first 20)");
                    break;
                }
            }

            // 9) Quick GUID lookup sanity check via the index/repository
            if (!snapshot.records().isEmpty()) {
                String sampleGuid = snapshot.records().get(0).guid();
                boolean found = index.repository().get(sampleGuid).isPresent();
                System.out.println("\n[INFO] Repository GUID lookup sanity check for '" + sampleGuid
                        + "': " + (found ? "[PASS] found" : "[FAIL] not found"));
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
