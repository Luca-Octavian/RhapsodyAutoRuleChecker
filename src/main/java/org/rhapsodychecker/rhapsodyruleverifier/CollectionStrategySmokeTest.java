package org.rhapsodychecker.rhapsodyruleverifier;

import com.telelogic.rhapsody.core.IRPConnector;
import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.ComLoadDiagnostics;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Legacy entry point retained for existing Eclipse launch configurations.
 *
 * <p>The rejected toList experiment has been removed. This now runs the
 * production indexed loader with diagnostics enabled.
 */
public final class CollectionStrategySmokeTest {

    private CollectionStrategySmokeTest() {}

    public static void main(String[] args) throws Exception {
        String modelPath =
                "C:\\Users\\uik11305\\Downloads\\VDU SW Arch\\VDU SW Arch\\VDU_SW_architecture.rpyx";
        if (args.length > 0) modelPath = args[0];

        RhapsodyConnectionManager connection = RhapsodyConnectionManager.getInstance();
        try {
            connection.connect(modelPath);
            RhapsodyModelLoader loader = new RhapsodyModelLoader(
                    ProgressReporter.NOOP, new ComLoadDiagnostics(true));
            RhapsodyModelSnapshot snapshot = loader.loadModel(connection.getProject());
            System.out.println("Production indexed load complete: "
                    + snapshot.records().size() + " records");

            printKindHistogram(snapshot);
            printConnectorDetail(snapshot);
        } finally {
            connection.shutdown();
        }
    }

    /** Overall kind distribution — the headline numbers to sanity-check. */
    private static void printKindHistogram(RhapsodyModelSnapshot snapshot) {
        Map<ElementKind, Integer> byKind = new EnumMap<>(ElementKind.class);
        for (ElementRecord record : snapshot.records()) {
            byKind.merge(record.kind(), 1, Integer::sum);
        }

        System.out.println();
        System.out.println("=== Elements by kind ===");
        for (Map.Entry<ElementKind, Integer> entry : byKind.entrySet()) {
            System.out.printf("  %-18s %d%n", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Breaks connector/link kinds down so the split can be verified against
     * the model directly.
     *
     * <p>CONNECTOR should contain only Junction pseudostates — the elements
     * that Rhapsody's Ctrl+F returns when searching for "Connector".
     * STATE_CONNECTOR should contain every other IRPConnector type.
     * LINK should contain only IRPLink elements (structural links between
     * Parts/Ports).
     */
    private static void printConnectorDetail(RhapsodyModelSnapshot snapshot) {
        Map<String, Integer> connectorTypes = new TreeMap<>();
        Map<String, Integer> stateConnectorTypes = new TreeMap<>();
        int connectors = 0;
        int stateConnectors = 0;
        int links = 0;
        int linksWithResolvedEndpoint = 0;

        for (ElementRecord record : snapshot.records()) {
            if (record.kind() == ElementKind.CONNECTOR) {
                connectors++;
                connectorTypes.merge(
                        readConnectorType(snapshot, record), 1, Integer::sum);
            } else if (record.kind() == ElementKind.STATE_CONNECTOR) {
                stateConnectors++;
                stateConnectorTypes.merge(
                        readConnectorType(snapshot, record), 1, Integer::sum);
            } else if (record.kind() == ElementKind.LINK) {
                links++;
                if (hasResolvedLinkEndpoint(snapshot, record)) {
                    linksWithResolvedEndpoint++;
                }
            }
        }

        System.out.println();
        System.out.println("=== CONNECTOR (Junction pseudostate — Rhapsody 'Connector') ===");
        System.out.println("  total: " + connectors);
        for (Map.Entry<String, Integer> entry : connectorTypes.entrySet()) {
            System.out.printf("  connectorType %-14s %d%n", entry.getKey(), entry.getValue());
        }

        System.out.println();
        System.out.println("=== STATE_CONNECTOR (non-Junction pseudostates) ===");
        System.out.println("  total: " + stateConnectors);
        for (Map.Entry<String, Integer> entry : stateConnectorTypes.entrySet()) {
            System.out.printf("  connectorType %-14s %d%n", entry.getKey(), entry.getValue());
        }

        System.out.println();
        System.out.println("=== LINK (structural link / IRPLink) ===");
        System.out.println("  total: " + links);
        System.out.println("  with resolved endpoint: " + linksWithResolvedEndpoint);
    }

    /**
     * True when any relation entry whose metaClass is "Link" has a resolved
     * far-end GUID. Scans the global relation index, not per-element ownership,
     * because links are typically owned by an enclosing block.
     */
    private static boolean hasResolvedLinkEndpoint(
            RhapsodyModelSnapshot snapshot, ElementRecord record) {

        // Check outgoing
        for (Map.Entry<String, java.util.List<RhapsodyModelSnapshot.RelationInfo>> entry
                : snapshot.relationsByOwner().entrySet()) {
            for (RhapsodyModelSnapshot.RelationInfo rel : entry.getValue()) {
                if ("Link".equals(rel.metaClass()) && rel.guid().equals(record.guid())) {
                    String target = rel.otherEndGuid();
                    if (target != null && !target.isEmpty()) return true;
                }
            }
        }
        return false;
    }

    private static String readConnectorType(
            RhapsodyModelSnapshot snapshot, ElementRecord record) {

        IRPModelElement handle = snapshot.handleByGuid().get(record.guid());
        if (handle instanceof IRPConnector) {
            try {
                String type = ((IRPConnector) handle).getConnectorType();
                if (type != null && !type.trim().isEmpty()) return type.trim();
            } catch (Throwable ignored) { /* fall through */ }
        }
        return "<unknown>";
    }
}
