// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/ModelCache.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.util.List;
import java.util.Map;

/**
 * Top-level serializable cache container for the entire model snapshot.
 * Designed for JSON serialization via Jackson.
 */
public final class ModelCache {

    private CacheMetadata metadata;
    private List<CachedElement> elements;
    private Map<String, List<CachedRelation>> relationsByOwner;
    private Map<String, List<CachedReference>> referencesByElement;

    /** Jackson needs no-arg constructor. */
    public ModelCache() {}

    public ModelCache(CacheMetadata metadata,
                      List<CachedElement> elements,
                      Map<String, List<CachedRelation>> relationsByOwner,
                      Map<String, List<CachedReference>> referencesByElement) {
        this.metadata = metadata;
        this.elements = elements;
        this.relationsByOwner = relationsByOwner;
        this.referencesByElement = referencesByElement;
    }

    public CacheMetadata getMetadata() { return metadata; }
    public void setMetadata(CacheMetadata metadata) { this.metadata = metadata; }

    public List<CachedElement> getElements() { return elements; }
    public void setElements(List<CachedElement> elements) { this.elements = elements; }

    public Map<String, List<CachedRelation>> getRelationsByOwner() { return relationsByOwner; }
    public void setRelationsByOwner(Map<String, List<CachedRelation>> r) { this.relationsByOwner = r; }

    public Map<String, List<CachedReference>> getReferencesByElement() { return referencesByElement; }
    public void setReferencesByElement(Map<String, List<CachedReference>> r) { this.referencesByElement = r; }
}