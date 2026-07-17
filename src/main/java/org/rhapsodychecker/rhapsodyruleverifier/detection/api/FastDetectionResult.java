// detection/api/FastDetectionResult.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Agregarea tuturor rezultatelor din faza de fast scan (startup).
 * Conține statistici in-memory + rezultate ProfileDetector + PortProbeService.
 */
public final class FastDetectionResult {

    private final Map<String, Long> countsByMetaClass;
    private final Map<String, Long> countsByStereotype;
    private final Map<String, Long> countsByKind;
    private final Set<String>       topOwnerPaths;
    private final long              totalElements;
    private final long              elementsWithDescription;
    private final ProfileSummary    profile;
    private final PortCapabilities  ports;

    public FastDetectionResult(
            Map<String, Long> countsByMetaClass,
            Map<String, Long> countsByStereotype,
            Map<String, Long> countsByKind,
            Set<String>       topOwnerPaths,
            long              totalElements,
            long              elementsWithDescription,
            ProfileSummary    profile,
            PortCapabilities  ports
    ) {
        this.countsByMetaClass       = Map.copyOf(countsByMetaClass);
        this.countsByStereotype      = Map.copyOf(countsByStereotype);
        this.countsByKind            = Map.copyOf(countsByKind);
        this.topOwnerPaths           = Collections.unmodifiableSet(new TreeSet<>(topOwnerPaths));
        this.totalElements           = totalElements;
        this.elementsWithDescription = elementsWithDescription;
        this.profile                 = profile;
        this.ports                   = ports;
    }

    public Map<String, Long> countsByMetaClass()       { return countsByMetaClass; }
    public Map<String, Long> countsByStereotype()      { return countsByStereotype; }
    public Map<String, Long> countsByKind()            { return countsByKind; }
    public Set<String>       topOwnerPaths()           { return topOwnerPaths; }
    public long              totalElements()           { return totalElements; }
    public long              elementsWithDescription() { return elementsWithDescription; }
    public ProfileSummary    profile()                 { return profile; }
    public PortCapabilities  ports()                   { return ports; }

    public double descriptionFillRate() {
        return totalElements == 0 ? 0d : (double) elementsWithDescription / totalElements;
    }
}
