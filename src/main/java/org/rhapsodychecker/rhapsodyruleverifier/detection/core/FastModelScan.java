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
 * Scanare rapidă, zero apeluri native.
 * Agregează statistici din lista de ElementRecord deja încărcată în memorie.
 * Rulează instant, indiferent de mărimea modelului.
 */
public final class FastModelScan {

    // Limitează numărul de owner paths stocate pentru a nu umple memoria
    private static final int MAX_OWNER_PATHS = 500;

    /**
     * @param allRecords lista completă de ElementRecord din snapshot
     * @param profile    rezultatul ProfileDetector (citit separat)
     * @param ports      rezultatul PortProbeService (eșantion porturi)
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

        // Numărăm câte elemente există per metaClass (ex: "Class"->131, "Port"->44)
        Map<String, Long> byMeta = allRecords.stream()
                .map(ElementRecord::metaClass)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Numărăm frecvența fiecărui stereotip (ex: "Block"->120, "ASIL_A"->15)
        Map<String, Long> byStereo = allRecords.stream()
                .flatMap(r -> r.stereotypes().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Numărăm per ElementKind (ex: "BLOCK"->120, "PORT_FLOW"->30)
        Map<String, Long> byKind = allRecords.stream()
                .map(r -> r.kind().name())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Primele N owner paths — utile pentru sugestii includePackages/excludePackages
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
