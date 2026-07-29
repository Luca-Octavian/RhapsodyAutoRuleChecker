// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/model/ElementRecord.java
package org.rhapsodychecker.rhapsodyruleverifier.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, tool-agnostic snapshot of a model element.
 * Stores stable identity (GUID), basic metadata, and lightweight classification.
 * No direct dependency on Rhapsody API types.
 */
public final class ElementRecord {

    private final String guid;
    private final String name;
    private final String metaClass;
    private final ElementKind kind;
    private final String description;

    // Ownership / navigation
    private final String ownerGuid;
    private final String ownerPath;

    // Typing info (useful for parts/ports)
    private final String typeGuid;
    private final String typeName;

    // Port-specific pre-loaded info (avoids COM calls during evaluation)
    private final String portDirection;
    private final String portMultiplicity;

    // Stereotypes applied to this element
    private final Set<String> stereotypes;

    // Pre-computed lowercase stereotypes for O(1) case-insensitive lookup
    private final Set<String> stereotypesLower;

    // Pre-loaded tagged values (tagName -> value)
    private final Map<String, String> tagValues;

    // Pre-cached Optional wrappers — avoids millions of Optional.ofNullable() allocations
    private final Optional<String> ownerGuidOpt;
    private final Optional<String> ownerPathOpt;
    private final Optional<String> typeGuidOpt;
    private final Optional<String> typeNameOpt;
    private final Optional<String> descriptionOpt;
    private final Optional<String> portDirectionOpt;
    private final Optional<String> portMultiplicityOpt;

    private ElementRecord(Builder b) {
        this.guid = requireNonBlank(b.guid, "guid");
        this.name = requireNonBlank(b.name, "name");
        this.metaClass = requireNonBlank(b.metaClass, "metaClass");
        this.kind = Objects.requireNonNull(b.kind, "kind must not be null");

        this.ownerGuid = emptyToNull(trimOrNull(b.ownerGuid));
        this.ownerPath = emptyToNull(trimOrNull(b.ownerPath));
        this.description = emptyToNull(trimOrNull(b.description));

        this.typeGuid = emptyToNull(trimOrNull(b.typeGuid));
        this.typeName = emptyToNull(trimOrNull(b.typeName));

        this.portDirection = emptyToNull(trimOrNull(b.portDirection));
        this.portMultiplicity = emptyToNull(trimOrNull(b.portMultiplicity));

        Set<String> st = (b.stereotypes == null) ? Collections.emptySet() : defensiveCopySet(b.stereotypes);
        this.stereotypes = st.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(st);

        // Pre-compute lowercase stereotype set for O(1) case-insensitive lookup
        if (st.isEmpty()) {
            this.stereotypesLower = Collections.emptySet();
        } else {
            Set<String> lower = new LinkedHashSet<String>(st.size());
            for (String s : st) {
                lower.add(s.toLowerCase(java.util.Locale.ROOT));
            }
            this.stereotypesLower = Collections.unmodifiableSet(lower);
        }

        this.tagValues = b.tagValues != null && !b.tagValues.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<String, String>(b.tagValues))
                : Collections.<String, String>emptyMap();

        // Pre-cache Optional wrappers (constructed once, reused on every accessor call)
        this.ownerGuidOpt = Optional.ofNullable(this.ownerGuid);
        this.ownerPathOpt = Optional.ofNullable(this.ownerPath);
        this.typeGuidOpt = Optional.ofNullable(this.typeGuid);
        this.typeNameOpt = Optional.ofNullable(this.typeName);
        this.descriptionOpt = Optional.ofNullable(this.description);
        this.portDirectionOpt = Optional.ofNullable(this.portDirection);
        this.portMultiplicityOpt = Optional.ofNullable(this.portMultiplicity);
    }

    public String guid() { return guid; }
    public String name() { return name; }
    public String metaClass() { return metaClass; }
    public ElementKind kind() { return kind; }
    public Optional<String> ownerGuid() { return ownerGuidOpt; }
    public Optional<String> ownerPath() { return ownerPathOpt; }
    public Optional<String> typeGuid() { return typeGuidOpt; }
    public Optional<String> typeName() { return typeNameOpt; }
    public Optional<String> description() { return descriptionOpt; }
    public Optional<String> portDirection() { return portDirectionOpt; }
    public Optional<String> portMultiplicity() { return portMultiplicityOpt; }

    public Set<String> stereotypes() { return stereotypes; }

    public Map<String, String> tagValues() { return tagValues; }

    /**
     * Returns a copy of this record with a different ownerPath.
     * Used during model loading to set ownerPaths computed locally
     * after the initial COM scan (avoids COM chain-walking).
     */
    public ElementRecord withOwnerPath(String newOwnerPath) {
        return new Builder()
                .guid(this.guid)
                .name(this.name)
                .metaClass(this.metaClass)
                .kind(this.kind)
                .ownerGuid(this.ownerGuid)
                .ownerPath(newOwnerPath)
                .stereotypes(this.stereotypes)
                .typeGuid(this.typeGuid)
                .typeName(this.typeName)
                .description(this.description)
                .portDirection(this.portDirection)
                .portMultiplicity(this.portMultiplicity)
                .tagValues(this.tagValues)
                .build();
    }

    /**
     * Returns a copy of this record with a different kind and type info.
     * Used during local part classification: Objects owned by Blocks
     * are reclassified as PART with type info loaded from IRPInstance.getOtherClass().
     */
    public ElementRecord withKindAndType(ElementKind newKind, String newTypeGuid, String newTypeName) {
        return new Builder()
                .guid(this.guid)
                .name(this.name)
                .metaClass(this.metaClass)
                .kind(newKind)
                .ownerGuid(this.ownerGuid)
                .ownerPath(this.ownerPath)
                .stereotypes(this.stereotypes)
                .typeGuid(newTypeGuid)
                .typeName(newTypeName)
                .description(this.description)
                .portDirection(this.portDirection)
                .portMultiplicity(this.portMultiplicity)
                .tagValues(this.tagValues)
                .build();
    }

    public boolean hasStereotype(String stereotype) {
        if (stereotype == null) return false;
        return stereotypes.contains(stereotype);
    }

    public boolean hasStereotypeIgnoreCase(String stereotype) {
        if (stereotype == null) return false;
        return stereotypesLower.contains(stereotype.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ElementRecord)) return false;
        ElementRecord that = (ElementRecord) o;
        return guid.equals(that.guid);
    }

    @Override
    public int hashCode() { return guid.hashCode(); }

    @Override
    public String toString() {
        return "ElementRecord{" +
                "guid='" + guid + '\'' +
                ", name='" + name + '\'' +
                ", metaClass='" + metaClass + '\'' +
                ", kind=" + kind +
                ", ownerPath=" + (ownerPath == null ? "<none>" : ownerPath) +
                ", stereotypes=" + stereotypes +
                ", description=" + (description == null ? "<none>" : description.substring(0, Math.min(description.length(), 50))) +
                '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String guid;
        private String name;
        private String metaClass;
        private ElementKind kind;
        private String ownerGuid;
        private String ownerPath;
        private String typeGuid;
        private String typeName;
        private String description;
        private String portDirection;
        private String portMultiplicity;
        private Set<String> stereotypes;
        private Map<String, String> tagValues;

        private Builder() {}

        public Builder guid(String guid) { this.guid = guid; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder metaClass(String metaClass) { this.metaClass = metaClass; return this; }
        public Builder kind(ElementKind kind) { this.kind = kind; return this; }
        public Builder ownerGuid(String ownerGuid) { this.ownerGuid = ownerGuid; return this; }
        public Builder ownerPath(String ownerPath) { this.ownerPath = ownerPath; return this; }
        public Builder typeGuid(String typeGuid) { this.typeGuid = typeGuid; return this; }
        public Builder typeName(String typeName) { this.typeName = typeName; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder portDirection(String portDirection) { this.portDirection = portDirection; return this; }
        public Builder portMultiplicity(String portMultiplicity) { this.portMultiplicity = portMultiplicity; return this; }

        public Builder tagValues(Map<String, String> tagValues) {
            this.tagValues = tagValues;
            return this;
        }

        public Builder stereotypes(Collection<String> stereotypes) {
            if (stereotypes == null) {
                this.stereotypes = null;
            } else {
                this.stereotypes = new LinkedHashSet<>(stereotypes);
            }
            return this;
        }

        public ElementRecord build() { return new ElementRecord(this); }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException(field + " must not be null/blank");
        return value;
    }

    private static String trimOrNull(String s) { return s == null ? null : s.trim(); }

    private static String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    private static Set<String> defensiveCopySet(Collection<String> in) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String s : in) {
            if (s == null) continue;
            String v = s.trim();
            if (!v.isEmpty()) copy.add(v);
        }
        return copy;
    }
}
