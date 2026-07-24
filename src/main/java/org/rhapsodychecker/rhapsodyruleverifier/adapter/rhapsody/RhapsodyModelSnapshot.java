// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyModelSnapshot.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

public final class RhapsodyModelSnapshot {

    private final List<ElementRecord> records;
    private final Map<String, IRPModelElement> handleByGuid;

    // Pre-built: ownerGuid -> list of dependency records owned by that element
    private final Map<String, List<DependencyInfo>> dependenciesByOwner;

    // Pre-built: elementGuid -> list of incoming references to that element
    private final Map<String, List<ReferenceInfo>> referencesByElement;

    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid,
                                 Map<String, List<DependencyInfo>> dependenciesByOwner,
                                 Map<String, List<ReferenceInfo>> referencesByElement) {
        this.records = Objects.requireNonNull(records, "records");
        this.handleByGuid = Objects.requireNonNull(handleByGuid, "handleByGuid");
        this.dependenciesByOwner = Objects.requireNonNull(dependenciesByOwner, "dependenciesByOwner");
        this.referencesByElement = Objects.requireNonNull(referencesByElement, "referencesByElement");
    }

    // Backwards-compatible 3-arg constructor
    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid,
                                 Map<String, List<DependencyInfo>> dependenciesByOwner) {
        this(records, handleByGuid, dependenciesByOwner, Collections.<String, List<ReferenceInfo>>emptyMap());
    }

    // Backwards-compatible 2-arg constructor
    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid) {
        this(records, handleByGuid, Collections.<String, List<DependencyInfo>>emptyMap());
    }

    public List<ElementRecord> records() { return records; }
    public Map<String, IRPModelElement> handleByGuid() { return handleByGuid; }
    public Map<String, List<DependencyInfo>> dependenciesByOwner() { return dependenciesByOwner; }
    public Map<String, List<ReferenceInfo>> referencesByElement() { return referencesByElement; }

    /**
     * Lightweight pre-indexed dependency info collected during model loading.
     */
    public static final class DependencyInfo {
        private final String guid;
        private final Set<String> stereotypes;
        private final String otherEndGuid; // may be null for external links

        public DependencyInfo(String guid, Set<String> stereotypes, String otherEndGuid) {
            this.guid = guid;
            this.stereotypes = stereotypes != null ? stereotypes : Collections.emptySet();
            this.otherEndGuid = otherEndGuid;
        }

        public String guid() { return guid; }
        public Set<String> stereotypes() { return stereotypes; }
        public String otherEndGuid() { return otherEndGuid; }
    }

    /**
     * Lightweight pre-indexed reference info collected during model loading.
     * Represents an element that references (points to) the keyed element.
     */
    public static final class ReferenceInfo {
        private final String guid;       // GUID of the referencing element
        private final String metaClass;  // metaClass of the referencing element
        private final Set<String> stereotypes;

        public ReferenceInfo(String guid, String metaClass, Set<String> stereotypes) {
            this.guid = guid;
            this.metaClass = metaClass != null ? metaClass : "";
            this.stereotypes = stereotypes != null ? stereotypes : Collections.<String>emptySet();
        }

        public String guid() { return guid; }
        public String metaClass() { return metaClass; }
        public Set<String> stereotypes() { return stereotypes; }
    }
}
