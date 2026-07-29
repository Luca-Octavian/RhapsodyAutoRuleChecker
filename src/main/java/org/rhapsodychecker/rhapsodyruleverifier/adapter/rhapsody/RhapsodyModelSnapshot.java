// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyModelSnapshot.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

public final class RhapsodyModelSnapshot {

    private final List<ElementRecord> records;
    private final Map<String, IRPModelElement> handleByGuid;

    // Pre-built: ownerGuid -> list of relation records owned by that element
    private final Map<String, List<RelationInfo>> relationsByOwner;

    // Pre-built: elementGuid -> list of incoming references to that element
    private final Map<String, List<ReferenceInfo>> referencesByElement;

    // Reverse index: targetGuid -> list of relations pointing AT that element (lazy-built)
    private volatile Map<String, List<RelationInfo>> relationsByTarget;

    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid,
                                 Map<String, List<RelationInfo>> relationsByOwner,
                                 Map<String, List<ReferenceInfo>> referencesByElement) {
        this.records = Objects.requireNonNull(records, "records");
        this.handleByGuid = Objects.requireNonNull(handleByGuid, "handleByGuid");
        this.relationsByOwner = Objects.requireNonNull(relationsByOwner, "relationsByOwner");
        this.referencesByElement = Objects.requireNonNull(referencesByElement, "referencesByElement");
    }

    // Backwards-compatible 3-arg constructor
    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid,
                                 Map<String, List<RelationInfo>> relationsByOwner) {
        this(records, handleByGuid, relationsByOwner, Collections.<String, List<ReferenceInfo>>emptyMap());
    }

    // Backwards-compatible 2-arg constructor
    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid) {
        this(records, handleByGuid, Collections.<String, List<RelationInfo>>emptyMap());
    }

    public List<ElementRecord> records() { return records; }
    public Map<String, IRPModelElement> handleByGuid() { return handleByGuid; }
    public Map<String, List<RelationInfo>> relationsByOwner() { return relationsByOwner; }
    public Map<String, List<ReferenceInfo>> referencesByElement() { return referencesByElement; }

    /**
     * @deprecated Use {@link #relationsByOwner()} instead. Kept for migration visibility.
     */
    @Deprecated
    public Map<String, List<RelationInfo>> dependenciesByOwner() { return relationsByOwner; }

    /**
     * Reverse index: targetGuid -> list of incoming relations.
     * Built lazily on first access, then cached. O(1) lookup for incoming relations
     * instead of scanning the entire relationsByOwner map.
     */
    public Map<String, List<RelationInfo>> relationsByTarget() {
        Map<String, List<RelationInfo>> local = relationsByTarget;
        if (local == null) {
            synchronized (this) {
                local = relationsByTarget;
                if (local == null) {
                    local = buildRelationsByTarget();
                    relationsByTarget = local;
                }
            }
        }
        return local;
    }

    /**
     * @deprecated Use {@link #relationsByTarget()} instead. Kept for migration visibility.
     */
    @Deprecated
    public Map<String, List<RelationInfo>> dependenciesByTarget() { return relationsByTarget(); }

    private Map<String, List<RelationInfo>> buildRelationsByTarget() {
        Map<String, List<RelationInfo>> result = new HashMap<String, List<RelationInfo>>();
        for (Map.Entry<String, List<RelationInfo>> entry : relationsByOwner.entrySet()) {
            String ownerGuid = entry.getKey();
            for (RelationInfo rel : entry.getValue()) {
                String target = rel.otherEndGuid();
                if (target != null && !target.isEmpty()) {
                    List<RelationInfo> list = result.get(target);
                    if (list == null) {
                        list = new ArrayList<RelationInfo>();
                        result.put(target, list);
                    }
                    // Store with ownerGuid as the otherEndGuid (the source of the incoming relation)
                    list.add(new RelationInfo(rel.guid(), rel.metaClass(), rel.stereotypes(), ownerGuid));
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Lightweight pre-indexed relation info collected during model loading.
     * Covers all relation metaclasses: Dependency, Generalization, Association, etc.
     */
    public static final class RelationInfo {
        private final String guid;
        private final String metaClass;
        private final Set<String> stereotypes;
        private final String otherEndGuid; // may be null for external links

        public RelationInfo(String guid, String metaClass, Set<String> stereotypes, String otherEndGuid) {
            this.guid = guid;
            this.metaClass = metaClass != null ? metaClass : "Dependency";
            this.stereotypes = stereotypes != null ? stereotypes : Collections.<String>emptySet();
            this.otherEndGuid = otherEndGuid;
        }

        public String guid() { return guid; }
        public String metaClass() { return metaClass; }
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