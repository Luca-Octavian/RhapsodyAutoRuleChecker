// detection/core/FastModelScan.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.core;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.PortCapabilities;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.ProfileSummary;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fast scan with zero native calls.
 * Aggregates statistics from the list of ElementRecord already loaded in memory.
 * Runs instantly, regardless of model size.
 */
public final class FastModelScan {

    // Limits the number of stored owner paths to avoid filling memory
    private static final int MAX_OWNER_PATHS = 500;

    /**
     * @param allRecords the complete list of ElementRecord from the snapshot
     * @param profile    the ProfileDetector result (read separately)
     * @param ports      the PortProbeService result (port sample)
     */
    public FastDetectionResult scan(
            List<ElementRecord> allRecords,
            ProfileSummary      profile,
            PortCapabilities    ports
    ) {
        long total    = allRecords.size();
        long withDesc = allRecords.stream()
                .filter(r -> r.description().isPresent())
                .count();

        // Count how many elements exist per metaClass (e.g., "Class"->131, "Port"->44)
        Map<String, Long> byMeta = allRecords.stream()
                .map(ElementRecord::metaClass)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Count the frequency of each stereotype (e.g., "Block"->120, "ASIL_A"->15)
        Map<String, Long> byStereo = allRecords.stream()
                .flatMap(r -> r.stereotypes().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Count per ElementKind (e.g., "BLOCK"->120, "PORT_FLOW"->30)
        Map<String, Long> byKind = allRecords.stream()
                .map(r -> r.kind().name())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // First N owner paths — useful for includePackages/excludePackages suggestions
        Set<String> topOwners = allRecords.stream()
                .map(r -> r.ownerPath().orElse(null))
                .filter(Objects::nonNull)
                .limit(MAX_OWNER_PATHS)
                .collect(Collectors.toCollection(TreeSet::new));

        return new FastDetectionResult(
                byMeta, byStereo, byKind, topOwners,
                total, withDesc, profile, ports
        );
    }
}
