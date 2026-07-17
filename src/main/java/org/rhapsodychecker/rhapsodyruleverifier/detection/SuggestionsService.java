// detection/SuggestionsService.java
package org.rhapsodychecker.rhapsodyruleverifier.detection;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.*;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.AliasGuess.DescriptionSource;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derivă sugestii de alias-uri și stereotipuri din rezultatele detecției.
 * Logică pură — zero apeluri native, zero dependențe pe Rhapsody API.
 */
public final class SuggestionsService {

    private static final double DESCRIPTION_FILL_THRESHOLD = 0.6;

    private static final Set<String> ASIL_TAG_KEYWORDS = Set.of(
            "asil", "safetylevel", "safety", "hara", "safetygoal", "qs"
    );

    private static final Set<String> ASIL_STEREO_KEYWORDS = Set.of(
            "asil", "asil_qm", "asil_a", "asil_b", "asil_c", "asil_d", "qm"
    );

    private static final Set<String> DESCRIPTION_TAG_KEYWORDS = Set.of(
            "description", "desc", "comment", "text", "documentation", "note"
    );

    // ── Alias guessing ────────────────────────────────────────────────────────

    public AliasGuess guessAliases(
            FastDetectionResult          fast,
            Optional<TagDiscoveryResult> tags,
            PortCapabilities             ports
    ) {
        DescriptionSource descSource  = resolveDescriptionSource(fast, tags);
        String            descTagName = descSource == DescriptionSource.TAG
                ? findDescriptionTagName(tags.get()).orElse(null)
                : null;

        List<String> asilTagCandidates = tags
                .map(t -> t.tagNames().stream()
                        .filter(n -> ASIL_TAG_KEYWORDS.contains(n.toLowerCase()))
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        List<String> asilStereoCandidates = fast.countsByStereotype().keySet().stream()
                .filter(s -> ASIL_STEREO_KEYWORDS.contains(s.toLowerCase()))
                .collect(Collectors.toList());

        return AliasGuess.builder()
                .descriptionSource(descSource)
                .descriptionTagName(descTagName)
                .asilTagCandidates(asilTagCandidates)
                .asilStereoCandidates(asilStereoCandidates)
                // directed = PORT_FLOW + PORT_PROXY
                .portTypeResolvable(ports.directedTypeResolvable())
                .portDirectionResolvable(ports.directedDirectionResolvable())
                .portMultiplicityResolvable(ports.directedMultiplicityResolvable())
                .build();
    }

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

    // ── Private helpers ───────────────────────────────────────────────────────

    private DescriptionSource resolveDescriptionSource(
            FastDetectionResult          fast,
            Optional<TagDiscoveryResult> tags
    ) {
        if (fast.descriptionFillRate() >= DESCRIPTION_FILL_THRESHOLD) {
            return DescriptionSource.NATIVE_DESCRIPTION;
        }
        if (tags.isPresent() && findDescriptionTagName(tags.get()).isPresent()) {
            return DescriptionSource.TAG;
        }
        return DescriptionSource.UNKNOWN;
    }

    private Optional<String> findDescriptionTagName(TagDiscoveryResult tags) {
        return tags.tagNames().stream()
                .filter(n -> DESCRIPTION_TAG_KEYWORDS.contains(n.toLowerCase()))
                .findFirst();
    }
}
