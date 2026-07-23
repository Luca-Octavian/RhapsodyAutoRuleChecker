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

    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid,
                                 Map<String, List<DependencyInfo>> dependenciesByOwner) {
        this.records = Objects.requireNonNull(records, "records");
        this.handleByGuid = Objects.requireNonNull(handleByGuid, "handleByGuid");
        this.dependenciesByOwner = Objects.requireNonNull(dependenciesByOwner, "dependenciesByOwner");
    }

    // Backwards-compatible constructor
    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid) {
        this(records, handleByGuid, Collections.emptyMap());
    }

    public List<ElementRecord> records() { return records; }
    public Map<String, IRPModelElement> handleByGuid() { return handleByGuid; }
    public Map<String, List<DependencyInfo>> dependenciesByOwner() { return dependenciesByOwner; }

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
}
