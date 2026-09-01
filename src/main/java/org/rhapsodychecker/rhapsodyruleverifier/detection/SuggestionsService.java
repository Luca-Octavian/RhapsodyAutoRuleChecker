// detection/SuggestionsService.java
package org.rhapsodychecker.rhapsodyruleverifier.detection;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derives stereotype suggestions from detection results.
 * Pure logic - zero native calls, zero Rhapsody API dependencies.
 */
public final class SuggestionsService {

    // ── Stereotype suggestions ────────────────────────────────────────────────

    /**
     * Suggests stereotypes found on elements of a specific kind.
     * Filtered by kind - does not return irrelevant global stereotypes.
     *
     * @param kind       the kind to suggest stereotypes for (e.g. BLOCK)
     * @param allRecords all element records from the snapshot
     */
    public List<String> suggestStereotypesForKind(
            ElementKind         kind,
            List<ElementRecord> allRecords
    ) {
        return allRecords.stream()
                .filter(r -> r.kind() == kind)
                .flatMap(r -> r.stereotypes().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Suggestions for directed ports (PORT_FLOW + PORT_PROXY combined).
     * Useful for the wizard when the user configures rules on ports with direction.
     */
    public List<String> suggestStereotypesForDirectedPorts(List<ElementRecord> allRecords) {
        return allRecords.stream()
                .filter(r -> r.kind() == ElementKind.PORT_FLOW
                          || r.kind() == ElementKind.PORT_PROXY)
                .flatMap(r -> r.stereotypes().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ── Tag value suggestions ─────────────────────────────────────────────────

    /**
     * Suggests observed values for a specific tag.
     * Used by RequiredValue with operator in/not_in.
     */
    public List<String> suggestValuesForTag(String tagName, TagDiscoveryResult tags) {
        return new ArrayList<>(tags.valuesFor(tagName));
    }

    // ── ElementKind suggestions ───────────────────────────────────────────────

    /**
     * Returns all ElementKind values as strings.
     * Used by OwnerStereotypeConstraint for allowedKinds.
     */
    public List<String> availableElementKinds() {
        return Arrays.stream(ElementKind.values())
                .map(ElementKind::name)
                .collect(Collectors.toList());
    }

    // ── ProfileDetector fix helper ────────────────────────────────────────────

    /**
     * Checks whether a profile name suggests a safety/ASIL context.
     * Used by ProfileDetector - separated here for testability.
     */
    public static boolean isAsilProfileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("asil")
                || lower.contains("iso26262")
                || lower.contains("safety")
                || lower.contains("hara")
                || lower.contains("fmea");
    }

}
