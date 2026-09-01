package org.rhapsodychecker.rhapsodyruleverifier;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Universal Rhapsody Model Dumper.
 *
 * Connects to a running Rhapsody instance (or opens a .rpyx), loads the
 * entire model, and writes a comprehensive plain-text dump of EVERY element,
 * field, relation, reference, and profile — plus an anomaly report at the end.
 *
 * <p>Usage: Run as a Java application from Eclipse with Rhapsody on the PATH.
 * Optionally pass the .rpyx path as the first argument.
 * Output goes to {@code model-dump-<timestamp>.log} in the working directory.
 *
 * <p>This replaces the need for multiple individual smoke tests — one run,
 * one model, complete picture.
 */
public class RhapsodyModelDumper {

    public static void main(String[] args) {
        String rpyxPath = null;
        if (args.length >= 1) rpyxPath = args[0];

        String timestamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date());
        String outFile = "model-dump-" + timestamp + ".log";

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outFile)), true)) {
            out.println("╔══════════════════════════════════════════════════════════════════╗");
            out.println("║           RHAPSODY MODEL DUMPER — FULL EXTRACTION               ║");
            out.println("║           Generated: " + timestamp + "                          ║");
            out.println("╚══════════════════════════════════════════════════════════════════╝");
            out.println();

            // ── Connect ──
            out.println("Connecting to Rhapsody...");
            conn.connect(rpyxPath);
            IRPProject project = conn.getProject();

            String projectName = safe(() -> project.getName(), "<error>");
            String projectGuid = safe(() -> project.getGUID(), "<error>");
            out.println("  Project Name : " + projectName);
            out.println("  Project GUID : " + projectGuid);
            out.println("  rpyx Path    : " + (rpyxPath != null ? rpyxPath : "<active project>"));
            out.println();

            // ── Load model via the production loader ──
            out.println("Loading model via RhapsodyModelLoader...");
            long t0 = System.currentTimeMillis();
            RhapsodyModelSnapshot snapshot = new RhapsodyModelLoader().loadModel(project);
            long elapsed = System.currentTimeMillis() - t0;
            out.println("  Load time: " + elapsed + " ms");
            out.println("  Total ElementRecords: " + snapshot.records().size());
            out.println("  Relation owners indexed: " + snapshot.relationsByOwner().size());
            out.println("  Reference targets indexed: " + snapshot.referencesByElement().size());
            out.println();

            // ── Summary by metaClass ──
            Map<String, Integer> metaClassCounts = new TreeMap<>();
            Map<ElementKind, Integer> kindCounts = new TreeMap<>();
            for (ElementRecord r : snapshot.records()) {
                metaClassCounts.merge(r.metaClass(), 1, Integer::sum);
                kindCounts.merge(r.kind(), 1, Integer::sum);
            }

            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 1: SUMMARY BY METACLASS");
            out.println("════════════════════════════════════════════════════════════════════");
            for (Map.Entry<String, Integer> e : metaClassCounts.entrySet()) {
                out.printf("  %-30s  %d%n", e.getKey(), e.getValue());
            }
            out.println();

            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 2: SUMMARY BY ELEMENTKIND");
            out.println("════════════════════════════════════════════════════════════════════");
            for (Map.Entry<ElementKind, Integer> e : kindCounts.entrySet()) {
                out.printf("  %-30s  %d%n", e.getKey(), e.getValue());
            }
            out.println();

            // ── Profiles (raw COM probe) ──
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 3: PROFILES");
            out.println("════════════════════════════════════════════════════════════════════");
            dumpProfiles(project, out);
            out.println();

            // ── All Elements ──
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 4: ALL ELEMENTS (every field)");
            out.println("════════════════════════════════════════════════════════════════════");
            int idx = 0;
            for (ElementRecord r : snapshot.records()) {
                idx++;
                out.println("────────────────────────────────────────────────────────────────────");
                out.printf("  [%d/%d] %s%n", idx, snapshot.records().size(), r.name());
                out.println("────────────────────────────────────────────────────────────────────");
                out.println("    GUID          : " + r.guid());
                out.println("    Name          : " + r.name());
                out.println("    MetaClass     : " + r.metaClass());
                out.println("    ElementKind   : " + r.kind());
                out.println("    OwnerGUID     : " + r.ownerGuid().orElse("<none>"));
                out.println("    OwnerPath     : " + r.ownerPath().orElse("<none>"));
                out.println("    Description   : " + truncate(r.description().orElse("<none>"), 200));
                out.println("    TypeGUID      : " + r.typeGuid().orElse("<none>"));
                out.println("    TypeName      : " + r.typeName().orElse("<none>"));
                out.println("    PortDirection  : " + r.portDirection().orElse("<none>"));
                out.println("    PortMultiplicity: " + r.portMultiplicity().orElse("<none>"));
                out.println("    InitialValue  : " + r.initialValue().orElse("<none>"));
                out.println("    Stereotypes   : " + (r.stereotypes().isEmpty() ? "<none>" : r.stereotypes()));
                if (!r.tagValues().isEmpty()) {
                    out.println("    TagValues     :");
                    for (Map.Entry<String, String> tv : r.tagValues().entrySet()) {
                        out.println("      " + tv.getKey() + " = " + truncate(tv.getValue(), 150));
                    }
                } else {
                    out.println("    TagValues     : <none>");
                }

                // Raw COM extras: try to extract additional properties not in ElementRecord
                IRPModelElement handle = snapshot.handleByGuid().get(r.guid());
                if (handle != null) {
                    dumpRawComExtras(handle, out);
                }
                out.println();
            }

            // ── Relations ──
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 5: RELATIONS (by owner)");
            out.println("════════════════════════════════════════════════════════════════════");
            int relCount = 0;
            for (Map.Entry<String, List<RhapsodyModelSnapshot.RelationInfo>> entry :
                    snapshot.relationsByOwner().entrySet()) {
                out.println("  Owner GUID: " + entry.getKey());
                for (RhapsodyModelSnapshot.RelationInfo rel : entry.getValue()) {
                    relCount++;
                    out.printf("    → [%s] GUID=%s  stereotypes=%s  target=%s%n",
                            rel.metaClass(), rel.guid(), rel.stereotypes(),
                            rel.otherEndGuid() != null ? rel.otherEndGuid() : "<null>");
                }
            }
            out.println("  Total relations: " + relCount);
            out.println();

            // ── References ──
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 6: REFERENCES (by target element)");
            out.println("════════════════════════════════════════════════════════════════════");
            int refCount = 0;
            for (Map.Entry<String, List<RhapsodyModelSnapshot.ReferenceInfo>> entry :
                    snapshot.referencesByElement().entrySet()) {
                out.println("  Target GUID: " + entry.getKey());
                for (RhapsodyModelSnapshot.ReferenceInfo ref : entry.getValue()) {
                    refCount++;
                    out.printf("    ← [%s] GUID=%s  stereotypes=%s%n",
                            ref.metaClass(), ref.guid(), ref.stereotypes());
                }
            }
            out.println("  Total references: " + refCount);
            out.println();

            // ── Anomalies ──
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  SECTION 7: ANOMALY REPORT");
            out.println("════════════════════════════════════════════════════════════════════");
            List<String> anomalies = new ArrayList<>();

            Set<String> seenGuids = new HashSet<>();
            for (ElementRecord r : snapshot.records()) {
                // Duplicate GUIDs
                if (!seenGuids.add(r.guid())) {
                    anomalies.add("DUPLICATE GUID: " + r.guid() + " (name=" + r.name() + ")");
                }
                // Blank/null names
                if (r.name() == null || r.name().trim().isEmpty()) {
                    anomalies.add("BLANK NAME: GUID=" + r.guid() + " metaClass=" + r.metaClass());
                }
                // No owner (except project-level packages)
                if (!r.ownerGuid().isPresent() && r.kind() != ElementKind.PACKAGE) {
                    anomalies.add("NO OWNER: " + r.name() + " GUID=" + r.guid() + " kind=" + r.kind());
                }
                // OTHER kind mapping (catch-all — may indicate unmapped metaClass)
                if (r.kind() == ElementKind.OTHER) {
                    anomalies.add("OTHER/UNMAPPED KIND: metaClass=" + r.metaClass() + " name=" + r.name()
                            + " GUID=" + r.guid());
                }
                // Tag values with blank keys
                for (Map.Entry<String, String> tv : r.tagValues().entrySet()) {
                    if (tv.getKey() == null || tv.getKey().trim().isEmpty()) {
                        anomalies.add("BLANK TAG KEY: element=" + r.name() + " GUID=" + r.guid());
                    }
                    if (tv.getValue() == null) {
                        anomalies.add("NULL TAG VALUE: element=" + r.name() + " tag=" + tv.getKey());
                    }
                }
                // Port without direction
                if (r.kind() == ElementKind.PORT && !r.portDirection().isPresent()) {
                    anomalies.add("PORT WITHOUT DIRECTION: " + r.name() + " GUID=" + r.guid());
                }
                // FlowProperty without type
                if (r.kind() == ElementKind.FLOW_PROPERTY && !r.typeName().isPresent()) {
                    anomalies.add("FLOW_PROPERTY WITHOUT TYPE: " + r.name() + " GUID=" + r.guid());
                }
            }

            // Relations pointing to nonexistent elements
            for (Map.Entry<String, List<RhapsodyModelSnapshot.RelationInfo>> entry :
                    snapshot.relationsByOwner().entrySet()) {
                if (!seenGuids.contains(entry.getKey())) {
                    anomalies.add("RELATION OWNER NOT IN RECORDS: " + entry.getKey());
                }
                for (RhapsodyModelSnapshot.RelationInfo rel : entry.getValue()) {
                    if (rel.otherEndGuid() != null && !seenGuids.contains(rel.otherEndGuid())) {
                        anomalies.add("RELATION TARGET NOT IN RECORDS: " + rel.otherEndGuid()
                                + " (from owner=" + entry.getKey() + " type=" + rel.metaClass() + ")");
                    }
                }
            }

            if (anomalies.isEmpty()) {
                out.println("  ✓ No anomalies detected.");
            } else {
                out.println("  Found " + anomalies.size() + " anomalies:");
                for (int i = 0; i < anomalies.size(); i++) {
                    out.printf("  [%d] %s%n", i + 1, anomalies.get(i));
                }
            }
            out.println();

            // ── Final summary ──
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  FINAL SUMMARY");
            out.println("════════════════════════════════════════════════════════════════════");
            out.println("  Total elements    : " + snapshot.records().size());
            out.println("  Distinct metaClasses: " + metaClassCounts.size());
            out.println("  Distinct ElementKinds: " + kindCounts.size());
            out.println("  Total relations   : " + relCount);
            out.println("  Total references  : " + refCount);
            out.println("  Anomalies         : " + anomalies.size());
            out.println("  Output file       : " + new File(outFile).getAbsolutePath());
            out.println();
            out.println("Done.");

            System.out.println("Dump complete: " + new File(outFile).getAbsolutePath());
            System.out.println("Elements: " + snapshot.records().size()
                    + ", Relations: " + relCount
                    + ", References: " + refCount
                    + ", Anomalies: " + anomalies.size());

        } catch (Exception t) {
            System.err.println("FATAL: " + t.getMessage());
            t.printStackTrace();
        }
    }

    // ── Profile dumper: uses raw COM to list all profiles and their stereotypes ──
    private static void dumpProfiles(IRPProject project, PrintWriter out) {
        try {
            IRPCollection profiles = project.getNestedElementsByMetaClass("Profile", 1);
            int count = profiles != null ? profiles.getCount() : 0;
            out.println("  Found " + count + " profile(s)");
            for (int i = 1; i <= count; i++) {
                try {
                    IRPModelElement profileEl = (IRPModelElement) profiles.getItem(i);
                    String pName = safe(() -> profileEl.getName(), "<error>");
                    String pGuid = safe(() -> profileEl.getGUID(), "<error>");
                    out.println("  Profile: " + pName + "  GUID=" + pGuid);

                    // List stereotypes within this profile
                    try {
                        IRPCollection stereos = profileEl.getNestedElementsByMetaClass("Stereotype", 1);
                        int sCount = stereos != null ? stereos.getCount() : 0;
                        for (int j = 1; j <= sCount; j++) {
                            try {
                                IRPModelElement stereoEl = (IRPModelElement) stereos.getItem(j);
                                out.println("    Stereotype: " + safe(() -> stereoEl.getName(), "<error>"));
                            } catch (Exception e) {
                                out.println("    (error reading stereotype #" + j + ": " + e.getMessage() + ")");
                            }
                        }
                        if (sCount == 0) {
                            out.println("    (no stereotypes defined)");
                        }
                    } catch (Exception e) {
                        out.println("    (error reading stereotypes: " + e.getMessage() + ")");
                    }
                } catch (Exception e) {
                    out.println("  (error reading profile #" + i + ": " + e.getMessage() + ")");
                }
            }
        } catch (Exception e) {
            out.println("  (error listing profiles: " + e.getMessage() + ")");
        }
    }

    // ── Raw COM extras: extract properties beyond what ElementRecord stores ──
    private static void dumpRawComExtras(IRPModelElement el, PrintWriter out) {
        // Try methods that IRPModelElement exposes but ElementRecord may not capture
        out.println("    --- Raw COM Properties ---");
        out.println("    COM.getMetaClass()      : " + safe(() -> el.getMetaClass(), "<error>"));
        out.println("    COM.getFullPathName()    : " + safe(() -> el.getFullPathName(), "<error>"));
        out.println("    COM.getDisplayName()     : " + safe(() -> el.getDisplayName(), "<error>"));
        out.println("    COM.getInterfaceName()   : " + safe(() -> el.getInterfaceName(), "<error>"));

        // Try to get the "description" directly from COM for comparison
        out.println("    COM.getDescription()     : " + truncate(
                safe(() -> el.getDescription(), "<error>"), 200));

        // Nested element count
        try {
            IRPCollection nested = el.getNestedElements();
            out.println("    COM.nestedElements.count : " + (nested != null ? nested.getCount() : 0));
        } catch (Exception e) {
            out.println("    COM.nestedElements.count : <error: " + e.getMessage() + ">");
        }

        // References count
        try {
            IRPCollection refs = el.getReferences();
            out.println("    COM.references.count     : " + (refs != null ? refs.getCount() : 0));
        } catch (Exception e) {
            out.println("    COM.references.count     : <error: " + e.getMessage() + ">");
        }

        // Stereotypes directly from COM
        try {
            IRPCollection stereos = el.getStereotypes();
            int sc = stereos != null ? stereos.getCount() : 0;
            if (sc > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= sc; i++) {
                        try {
                            IRPModelElement s = (IRPModelElement) stereos.getItem(i);
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(safe(() -> s.getName(), "?"));
                        } catch (Exception e) {
                            sb.append("?[err: ").append(e.getMessage()).append("]");
                        }
                }
                out.println("    COM.stereotypes          : [" + sb + "]");
            } else {
                out.println("    COM.stereotypes          : <none>");
            }
        } catch (Exception e) {
            out.println("    COM.stereotypes          : <error: " + e.getMessage() + ">");
        }

        // Tags directly from COM
        try {
            IRPCollection tags = el.getAllTags();
            int tc = tags != null ? tags.getCount() : 0;
            if (tc > 0) {
                for (int i = 1; i <= tc; i++) {
                    try {
                        IRPTag tag = (IRPTag) tags.getItem(i);
                        String tagName = safe(() -> tag.getName(), "?");
                        String tagValue = safe(() -> tag.getValue(), "?");
                        out.println("    COM.tag[" + tagName + "]     = " + truncate(tagValue, 150));
                    } catch (Exception e) {
                        out.println("    COM.tag[#" + i + "]          = <error: " + e.getMessage() + ">");
                    }
                }
            } else {
                out.println("    COM.tags                 : <none>");
            }
        } catch (Exception e) {
            out.println("    COM.tags                 : <error: " + e.getMessage() + ">");
        }

        // Owner from COM
        try {
            IRPModelElement owner = el.getOwner();
            if (owner != null) {
                out.println("    COM.owner                : " + safe(() -> owner.getName(), "?")
                        + " [" + safe(() -> owner.getMetaClass(), "?") + "]"
                        + " GUID=" + safe(() -> owner.getGUID(), "?"));
            } else {
                out.println("    COM.owner                : <null>");
            }
        } catch (Exception e) {
            out.println("    COM.owner                : <error: " + e.getMessage() + ">");
        }
    }

    // ── Helpers ──

    @FunctionalInterface
    private interface SafeCall<T> {
        T call() throws Exception;
    }

    private static <T> T safe(SafeCall<T> call, T fallback) {
        try {
            return call.call();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "... [truncated, total " + s.length() + " chars]";
    }
}