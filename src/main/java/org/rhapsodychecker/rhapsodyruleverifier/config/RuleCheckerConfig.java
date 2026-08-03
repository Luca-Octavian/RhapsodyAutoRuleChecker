package org.rhapsodychecker.rhapsodyruleverifier.config;

import java.util.*;

/**
 * Immutable top-level config object holding element sets and rules.
 * Validates structure and cross-references at build time.
 */
public final class RuleCheckerConfig {
    private final int schemaVersion;
    private final Map<String, ElementSetDefinition> elementSets;
    private final List<RuleSpec> rules;

    private RuleCheckerConfig(Builder b) {
        this.schemaVersion = b.schemaVersion;

        this.elementSets = b.elementSets != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(b.elementSets))
                : Collections.emptyMap();
        this.rules = b.rules != null
                ? Collections.unmodifiableList(new ArrayList<>(b.rules))
                : Collections.emptyList();

        validate(b.parseErrors != null ? b.parseErrors : Collections.emptyList());
    }

    private void validate(List<String> parseErrors) {
        List<String> errors = new ArrayList<>(parseErrors);

        if (schemaVersion < 1) {
            errors.add("schemaVersion must be >= 1, got: " + schemaVersion);
        }

        // Cross-reference: rules that reference an elementSet must find it
        for (RuleSpec rule : rules) {
            rule.appliesToSet().ifPresent(s -> {
                if (!elementSets.containsKey(s)) {
                    errors.add("Rule '" + rule.id() + "' references unknown elementSet: '" + s + "'");
                }
            });
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Config validation errors:\n  - " + String.join("\n  - ", errors));
        }
    }

    public int schemaVersion() { return schemaVersion; }
    public Map<String, ElementSetDefinition> elementSets() { return elementSets; }
    public List<RuleSpec> rules() { return rules; }

    public Optional<ElementSetDefinition> elementSet(String id) { return Optional.ofNullable(elementSets.get(id)); }
    public List<RuleSpec> enabledRules() {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec r : rules) if (r.enabled()) out.add(r);
        return Collections.unmodifiableList(out);
    }

    @Override
    public String toString() {
        return "RuleCheckerConfig{schema=" + schemaVersion
                + ", sets=" + elementSets.size()
                + ", rules=" + rules.size() + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int schemaVersion;
        private Map<String, ElementSetDefinition> elementSets;
        private List<RuleSpec> rules;
        private List<String> parseErrors;

        private Builder() {}

        public Builder schemaVersion(int v) { this.schemaVersion = v; return this; }
        public Builder elementSets(Map<String, ElementSetDefinition> sets) { this.elementSets = sets; return this; }
        public Builder rules(List<RuleSpec> rules) { this.rules = rules; return this; }
        public Builder parseErrors(List<String> errors) { this.parseErrors = errors; return this; }
        public RuleCheckerConfig build() { return new RuleCheckerConfig(this); }
    }
}