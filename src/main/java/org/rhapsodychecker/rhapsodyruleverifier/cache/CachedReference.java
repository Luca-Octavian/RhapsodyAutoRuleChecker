// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CachedReference.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.util.Set;

/**
 * JSON-serializable representation of a ReferenceInfo record.
 * Represents an element that references (points to) the keyed element.
 */
public final class CachedReference {

    private String guid;
    private String metaClass;
    private Set<String> stereotypes;

    /** Jackson needs no-arg constructor. */
    public CachedReference() {}

    public CachedReference(String guid, String metaClass, Set<String> stereotypes) {
        this.guid = guid;
        this.metaClass = metaClass;
        this.stereotypes = stereotypes;
    }

    public String getGuid() { return guid; }
    public void setGuid(String v) { this.guid = v; }

    public String getMetaClass() { return metaClass; }
    public void setMetaClass(String v) { this.metaClass = v; }

    public Set<String> getStereotypes() { return stereotypes; }
    public void setStereotypes(Set<String> v) { this.stereotypes = v; }
}