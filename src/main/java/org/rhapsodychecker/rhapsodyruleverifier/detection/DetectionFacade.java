// detection/DetectionFacade.java
package org.rhapsodychecker.rhapsodyruleverifier.detection;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.*;
import org.rhapsodychecker.rhapsodyruleverifier.detection.core.FastModelScan;
import org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Punct unic de intrare pentru toate operațiile de detecție.
 *
 * Strategia de cost:
 *   fastScan()         → rulează la startup, instant (zero apeluri native grele)
 *   discoverTags()     → on-demand, scoped la kind/scope selectat în wizard
 *   guessAliases()     → logică pură, după ce avem datele
 */
public final class DetectionFacade {

    private static final int DEFAULT_PORT_SAMPLE  = 100;

    private final FastModelScan       fastModelScan;
    private final ProfileDetector     profileDetector;
    private final TagDiscoveryService tagDiscoveryService;
    private final PortProbeService    portProbeService;
    private final SuggestionsService  suggestionsService;

    public DetectionFacade(
            FastModelScan       fastModelScan,
            ProfileDetector     profileDetector,
            TagDiscoveryService tagDiscoveryService,
            PortProbeService    portProbeService,
            SuggestionsService  suggestionsService
    ) {
        this.fastModelScan       = fastModelScan;
        this.profileDetector     = profileDetector;
        this.tagDiscoveryService = tagDiscoveryService;
        this.portProbeService    = portProbeService;
        this.suggestionsService  = suggestionsService;
    }

    // ------------------------------------------------------------------
    // FAZA 1 — startup, rulează în SwingWorker pentru a nu bloca UI-ul
    // ------------------------------------------------------------------

    /**
     * Scanare rapidă la încărcarea modelului.
     * Apelurile native sunt limitate la: citire profiluri + probe N porturi.
     */
    public FastDetectionResult fastScan(
            List<ElementRecord>          allRecords,
            Map<String, IRPModelElement> handleByGuid,
            IRPApplication               app
    ) {
        ProfileSummary profile = profileDetector.detect(app);

        List<ElementRecord> allPorts = allRecords.stream()
                .filter(r -> r.kind().isPortKind())
                .collect(Collectors.toList());

        PortCapabilities portCaps = portProbeService.probe(allPorts, DEFAULT_PORT_SAMPLE);


        return fastModelScan.scan(allRecords, profile, portCaps);
    }

    // ------------------------------------------------------------------
    // FAZA 2 — on-demand, când utilizatorul intră pe un pas din wizard
    // ------------------------------------------------------------------

    /**
     * Descoperă tag-urile pentru un scope arbitrar.
     * Rulează în SwingWorker — nu apela de pe EDT.
     */
    public TagDiscoveryResult discoverTags(
            List<ElementRecord>          allRecords,
            Predicate<ElementRecord>     scopeFilter,
            Map<String, IRPModelElement> handleByGuid
    ) {
        return tagDiscoveryService.discover(allRecords, scopeFilter, handleByGuid);
    }

    /** Shortcut: descoperă tag-uri doar pentru un ElementKind specific. */
    public TagDiscoveryResult discoverTagsForKind(
            List<ElementRecord>          allRecords,
            ElementKind                  kind,
            Map<String, IRPModelElement> handleByGuid
    ) {
        return discoverTags(allRecords, r -> r.kind() == kind, handleByGuid);
    }

    // ------------------------------------------------------------------
    // FAZA 3 — logică pură, fără apeluri native
    // ------------------------------------------------------------------

    public AliasGuess guessAliases(
            FastDetectionResult          fast,
            Optional<TagDiscoveryResult> tags
    ) {
        return suggestionsService.guessAliases(fast, tags, fast.ports());
    }

    // ------------------------------------------------------------------
    // Factory pentru wiring implicit
    // ------------------------------------------------------------------

    public static DetectionFacade create(PortProbeService portProbeService) {
        return new DetectionFacade(
                new FastModelScan(),
                new ProfileDetector(),
                new TagDiscoveryService(200),
                portProbeService,
                new SuggestionsService()
        );
    }
}
