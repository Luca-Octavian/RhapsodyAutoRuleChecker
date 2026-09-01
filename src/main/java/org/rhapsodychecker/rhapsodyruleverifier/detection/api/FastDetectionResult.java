// detection/api/FastDetectionResult.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aggregation of all results from the fast scan phase (startup).
 * Contains in-memory statistics + ProfileDetector results + PortProbeService results.
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
    	this.countsByMetaClass  = Collections.unmodifiableMap(new LinkedHashMap<>(countsByMetaClass));
    	this.countsByStereotype = Collections.unmodifiableMap(new LinkedHashMap<>(countsByStereotype));
    	this.countsByKind       = Collections.unmodifiableMap(new LinkedHashMap<>(countsByKind));
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
