// detection/rhapsody/PortProbeService.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyPortInfoResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.Multiplicity;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.PortDirection;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.PortInfo;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.PortCapabilities;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Eșantionează porturi și testează rezolvabilitatea proprietăților
 * type/direction/multiplicity prin RhapsodyPortInfoResolver.
 *
 * Porturi directed (PORT_FLOW + PORT_PROXY): au direction, pot avea type.
 * Porturi standard (PORT): de obicei fara direction/type explicit.
 * Probe-ul e separat ca wizard-ul sa stie ce optiuni sa ofere per tip.
 */
public final class PortProbeService {

    private static final Logger LOG = Logger.getLogger(PortProbeService.class.getName());

    private final RhapsodyPortInfoResolver resolver;

    public PortProbeService(RhapsodyPortInfoResolver resolver) {
        this.resolver = resolver;
    }

    public PortCapabilities probe(List<ElementRecord> allPortRecords, int sampleLimit) {
        List<ElementRecord> directed = allPortRecords.stream()
                .filter(r -> r.kind() == ElementKind.PORT_FLOW
                          || r.kind() == ElementKind.PORT_PROXY)
                .collect(Collectors.toList());

        List<ElementRecord> standard = allPortRecords.stream()
                .filter(r -> r.kind() == ElementKind.PORT)
                .collect(Collectors.toList());

        int[] directedCounts = probeSubset(directed, sampleLimit);
        int[] standardCounts = probeSubset(standard, sampleLimit);

        return new PortCapabilities(
                directedCounts[0], directedCounts[1], directedCounts[2], directedCounts[3],
                standardCounts[0], standardCounts[1], standardCounts[2], standardCounts[3]
        );
    }

    /**
     * @return int[4]: { sampled, okType, okDirection, okMultiplicity }
     */
    private int[] probeSubset(List<ElementRecord> records, int sampleLimit) {
        int sampled = 0, okType = 0, okDir = 0, okMult = 0;

        for (ElementRecord port : records) {
            if (sampled >= sampleLimit) break;
            sampled++;

            // type: disponibil din snapshot fara apel nativ
            if (port.typeName().isPresent()) {
                okType++;
            }

            try {
                PortInfo info = resolver.resolve(port);

                if (info.direction() != PortDirection.UNKNOWN
                        && info.direction() != PortDirection.NONE) {
                    okDir++;
                }

                Multiplicity mult = info.multiplicity();
                if (mult.lower().isPresent() || mult.upper().isPresent()) {
                    okMult++;
                }

            } catch (Exception e) {
                LOG.log(Level.FINE, "Port probe failed for element: " + port.guid(), e);
            }
        }

        return new int[]{ sampled, okType, okDir, okMult };
    }
}
