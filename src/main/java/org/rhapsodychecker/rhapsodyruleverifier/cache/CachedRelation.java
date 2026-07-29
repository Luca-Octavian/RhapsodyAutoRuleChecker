// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CachedRelation.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.util.Set;

/**
 * JSON-serializable representation of a RelationInfo record.
 * Covers all relation metaclasses: Dependency, Generalization, Association, etc.
 */
public final class CachedRelation {

    private String guid;
    private String metaClass;
    private Set<String> stereotypes;
    private String otherEndGuid;

    /** Jackson needs no-arg constructor. */
    public CachedRelation() {}

    public CachedRelation(String guid, String metaClass, Set<String> stereotypes, String otherEndGuid) {
        this.guid = guid;
        this.metaClass = metaClass;
        this.stereotypes = stereotypes;
        this.otherEndGuid = otherEndGuid;
    }

    public String getGuid() { return guid; }
    public void setGuid(String v) { this.guid = v; }

    public String getMetaClass() { return metaClass; }
    public void setMetaClass(String v) { this.metaClass = v; }

    public Set<String> getStereotypes() { return stereotypes; }
    public void setStereotypes(Set<String> v) { this.stereotypes = v; }

    public String getOtherEndGuid() { return otherEndGuid; }
    public void setOtherEndGuid(String v) { this.otherEndGuid = v; }
}