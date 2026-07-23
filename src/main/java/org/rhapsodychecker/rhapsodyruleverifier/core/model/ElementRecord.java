// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/model/ElementRecord.java
package org.rhapsodychecker.rhapsodyruleverifier.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
    }

    public String guid() { return guid; }
    public String name() { return name; }
    public String metaClass() { return metaClass; }
    public ElementKind kind() { return kind; }
    public Optional<String> ownerGuid() { return Optional.ofNullable(ownerGuid); }
    public Optional<String> ownerPath() { return Optional.ofNullable(ownerPath); }
    public Optional<String> typeGuid() { return Optional.ofNullable(typeGuid); }
    public Optional<String> typeName() { return Optional.ofNullable(typeName); }
    public Optional<String> description() { return Optional.ofNullable(description); }
    public Optional<String> portDirection() { return Optional.ofNullable(portDirection); }
    public Optional<String> portMultiplicity() { return Optional.ofNullable(portMultiplicity); }

    public Set<String> stereotypes() { return stereotypes; }

    public boolean hasStereotype(String stereotype) {
        if (stereotype == null) return false;
        return stereotypes.contains(stereotype);
    }

    public boolean hasStereotypeIgnoreCase(String stereotype) {
        if (stereotype == null) return false;
        for (String s : stereotypes) {
            if (s.equalsIgnoreCase(stereotype)) return true;
        }
        return false;
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
