// detection/SuggestionsService.java
package org.rhapsodychecker.rhapsodyruleverifier.detection;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derivă sugestii de stereotipuri din rezultatele detecției.
 * Logică pură — zero apeluri native, zero dependențe pe Rhapsody API.
 */
public final class SuggestionsService {

    // ── Stereotype suggestions ────────────────────────────────────────────────

    /**
     * Sugerează stereotipurile găsite pe elementele de un kind specific.
     * Filtrat pe kind — nu returnează stereotipuri globale irelevante.
     *
     * @param kind       kind-ul pentru care vrem sugestii (ex: BLOCK)
     * @param allRecords toate elementele din snapshot
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
     * Sugestii pentru porturi directed (PORT_FLOW + PORT_PROXY combinate).
     * Utile pentru wizard cand userul configureaza reguli pe porturi cu directie.
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
     * Sugerează valorile observate pentru un tag specific.
     * Folosit la RequiredValue cu operator in/not_in.
     */
    public List<String> suggestValuesForTag(String tagName, TagDiscoveryResult tags) {
        return new ArrayList<>(tags.valuesFor(tagName));
    }

    // ── ElementKind suggestions ───────────────────────────────────────────────

    /**
     * Returnează toate ElementKind-urile ca strings.
     * Folosit pentru OwnerStereotypeConstraint → allowedKinds.
     */
    public List<String> availableElementKinds() {
        return Arrays.stream(ElementKind.values())
                .map(ElementKind::name)
                .collect(Collectors.toList());
    }

    // ── ProfileDetector fix helper ────────────────────────────────────────────

    /**
     * Verifica daca un profil sugereaza context de safety/ASIL.
     * Folosit de ProfileDetector — separat aici pentru testabilitate.
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
