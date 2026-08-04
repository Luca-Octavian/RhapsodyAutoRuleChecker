// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CachedElement.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.util.Map;
import java.util.Set;

/**
 * JSON-serializable representation of an ElementRecord.
 * All fields use JavaBean conventions for Jackson.
 */
public final class CachedElement {

    private String guid;
    private String name;
    private String metaClass;
    private String kind;
    private String description;
    private String ownerGuid;
    private String ownerPath;
    private String typeGuid;
    private String typeName;
    private Set<String> stereotypes;
    private String portDirection;
    private String portMultiplicity;
    private String initialValue;
    private Map<String, String> tagValues;

    /** Jackson needs no-arg constructor. */
    public CachedElement() {}

    public String getGuid() { return guid; }
    public void setGuid(String v) { this.guid = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getMetaClass() { return metaClass; }
    public void setMetaClass(String v) { this.metaClass = v; }

    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getOwnerGuid() { return ownerGuid; }
    public void setOwnerGuid(String v) { this.ownerGuid = v; }

    public String getOwnerPath() { return ownerPath; }
    public void setOwnerPath(String v) { this.ownerPath = v; }

    public String getTypeGuid() { return typeGuid; }
    public void setTypeGuid(String v) { this.typeGuid = v; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String v) { this.typeName = v; }

    public Set<String> getStereotypes() { return stereotypes; }
    public void setStereotypes(Set<String> v) { this.stereotypes = v; }

    public String getPortDirection() { return portDirection; }
    public void setPortDirection(String v) { this.portDirection = v; }

    public String getPortMultiplicity() { return portMultiplicity; }
    public void setPortMultiplicity(String v) { this.portMultiplicity = v; }

    public String getInitialValue() { return initialValue; }
    public void setInitialValue(String v) { this.initialValue = v; }

    public Map<String, String> getTagValues() { return tagValues; }
    public void setTagValues(Map<String, String> v) { this.tagValues = v; }
}