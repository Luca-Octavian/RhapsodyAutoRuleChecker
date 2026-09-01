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
 * Single entry point for all detection operations.
 *
 * Cost strategy:
 *   fastScan()         - runs at startup, instant (zero heavyweight native calls)
 *   discoverTags()     - on-demand, scoped to the kind/scope selected in the wizard
 */
public final class DetectionFacade {

    private static final int DEFAULT_PORT_SAMPLE  = 100;

    private final FastModelScan       fastModelScan;
    private final ProfileDetector     profileDetector;
    private final TagDiscoveryService tagDiscoveryService;
    private final PortProbeService    portProbeService;

    public DetectionFacade(
            FastModelScan       fastModelScan,
            ProfileDetector     profileDetector,
            TagDiscoveryService tagDiscoveryService,
            PortProbeService    portProbeService,
            @SuppressWarnings("unused") SuggestionsService suggestionsService
    ) {
        this.fastModelScan       = fastModelScan;
        this.profileDetector     = profileDetector;
        this.tagDiscoveryService = tagDiscoveryService;
        this.portProbeService    = portProbeService;
    }

    // ------------------------------------------------------------------
    // PHASE 1 - startup, runs in a SwingWorker to avoid blocking the UI
    // ------------------------------------------------------------------

    /**
     * Fast scan at model load time.
     * Native calls are limited to: profile reading + probing N ports.
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
    // PHASE 2 - on-demand, when the user enters a wizard step
    // ------------------------------------------------------------------

    /**
     * Discovers tags for an arbitrary scope.
     * Runs in a SwingWorker - do not call from the EDT.
     */
    public TagDiscoveryResult discoverTags(
            List<ElementRecord>          allRecords,
            Predicate<ElementRecord>     scopeFilter,
            Map<String, IRPModelElement> handleByGuid
    ) {
        return tagDiscoveryService.discover(allRecords, scopeFilter, handleByGuid);
    }

    /** Shortcut: discovers tags for a specific ElementKind only. */
    public TagDiscoveryResult discoverTagsForKind(
            List<ElementRecord>          allRecords,
            ElementKind                  kind,
            Map<String, IRPModelElement> handleByGuid
    ) {
        return discoverTags(allRecords, r -> r.kind() == kind, handleByGuid);
    }

    // ------------------------------------------------------------------
    // PHASE 3 - pure logic, no native calls
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Factory for implicit wiring
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
