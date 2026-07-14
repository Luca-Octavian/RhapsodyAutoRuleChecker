package org.rhapsodychecker.rhapsodyruleverifier.config;

import java.util.*;

/**
 * Immutable definition of a reusable element scope (e.g., "ArchitectureElements").
 */
public final class ElementSetDefinition {
    private final String id;
    private final String title;
    private final List<String> kinds;
    private final List<String> types;
    private final List<String> stereotypes;
    private final List<String> includePackages;
    private final List<String> excludePackages;

    private ElementSetDefinition(Builder b) {
        this.id = requireNonBlank(b.id, "elementSet id");
        this.title = b.title;
        this.kinds = freezeList(b.kinds);
        this.types = freezeList(b.types);
        this.stereotypes = freezeList(b.stereotypes);
        this.includePackages = freezeList(b.includePackages);
        this.excludePackages = freezeList(b.excludePackages);
    }

    public String id() { return id; }
    public Optional<String> title() { return Optional.ofNullable(title); }
    public List<String> types() { return types; }
    public List<String> stereotypes() { return stereotypes; }
    public List<String> kinds() { return kinds; }
    public List<String> includePackages() { return includePackages; }
    public List<String> excludePackages() { return excludePackages; }
    

    @Override
    public String toString() {
        return "ElementSetDefinition{id='" + id + "', types=" + types + ", stereotypes=" + stereotypes + "}";
    }
    

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String title;
        private List<String> types;
        private List<String> stereotypes;
        private List<String> includePackages;
        private List<String> excludePackages;
        private List<String> kinds;
        

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder types(List<String> types) { this.types = types; return this; }
        public Builder kinds(List<String> kinds) { this.kinds = kinds; return this; }
        public Builder stereotypes(List<String> stereotypes) { this.stereotypes = stereotypes; return this; }
        public Builder includePackages(List<String> includePackages) { this.includePackages = includePackages; return this; }
        public Builder excludePackages(List<String> excludePackages) { this.excludePackages = excludePackages; return this; }
        public ElementSetDefinition build() { return new ElementSetDefinition(this); }
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be null/blank");
        return v;
    }

    private static List<String> freezeList(List<String> in) {
        if (in == null || in.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(in));
    }
}