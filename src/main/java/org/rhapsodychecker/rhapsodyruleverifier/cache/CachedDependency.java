// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CachedDependency.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.util.Set;

/**
 * JSON-serializable representation of a DependencyInfo record.
 */
public final class CachedDependency {

    private String guid;
    private Set<String> stereotypes;
    private String otherEndGuid;

    /** Jackson needs no-arg constructor. */
    public CachedDependency() {}

    public CachedDependency(String guid, Set<String> stereotypes, String otherEndGuid) {
        this.guid = guid;
        this.stereotypes = stereotypes;
        this.otherEndGuid = otherEndGuid;
    }

    public String getGuid() { return guid; }
    public void setGuid(String v) { this.guid = v; }

    public Set<String> getStereotypes() { return stereotypes; }
    public void setStereotypes(Set<String> v) { this.stereotypes = v; }

    public String getOtherEndGuid() { return otherEndGuid; }
    public void setOtherEndGuid(String v) { this.otherEndGuid = v; }
}