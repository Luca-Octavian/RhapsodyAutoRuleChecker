package org.rhapsodychecker.rhapsodyruleverifier.config;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.ConfigMode;

import java.util.*;

/**
 * Immutable top-level config object holding all parsed aliases, element sets, and rules.
 * Validates structure and cross-references at build time.
 */
public final class RuleCheckerConfig {
    private final int schemaVersion;
    private final ConfigMode mode;
    private final Map<String, AliasDefinition> aliases;
    private final Map<String, ElementSetDefinition> elementSets;
    private final List<RuleSpec> rules;

    private RuleCheckerConfig(Builder b) {
        this.schemaVersion = b.schemaVersion;
        this.mode = b.mode != null ? b.mode : ConfigMode.LENIENT;

        this.aliases = b.aliases != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(b.aliases))
                : Collections.emptyMap();
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

        // Cross-reference: rules that reference an alias must find it
        for (RuleSpec rule : rules) {
            rule.target().ifPresent(t -> {
                if (!aliases.containsKey(t)) {
                    errors.add("Rule '" + rule.id() + "' references unknown alias: '" + t + "'");
                }
            });
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
    public ConfigMode mode() { return mode; }
    public Map<String, AliasDefinition> aliases() { return aliases; }
    public Map<String, ElementSetDefinition> elementSets() { return elementSets; }
    public List<RuleSpec> rules() { return rules; }

    public Optional<AliasDefinition> alias(String id) { return Optional.ofNullable(aliases.get(id)); }
    public Optional<ElementSetDefinition> elementSet(String id) { return Optional.ofNullable(elementSets.get(id)); }
    public List<RuleSpec> enabledRules() {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec r : rules) if (r.enabled()) out.add(r);
        return Collections.unmodifiableList(out);
    }

    @Override
    public String toString() {
        return "RuleCheckerConfig{schema=" + schemaVersion + ", mode=" + mode
                + ", aliases=" + aliases.size() + ", sets=" + elementSets.size()
                + ", rules=" + rules.size() + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int schemaVersion;
        private ConfigMode mode;
        private Map<String, AliasDefinition> aliases;
        private Map<String, ElementSetDefinition> elementSets;
        private List<RuleSpec> rules;
        private List<String> parseErrors;

        private Builder() {}

        public Builder schemaVersion(int v) { this.schemaVersion = v; return this; }
        public Builder mode(ConfigMode mode) { this.mode = mode; return this; }
        public Builder aliases(Map<String, AliasDefinition> aliases) { this.aliases = aliases; return this; }
        public Builder elementSets(Map<String, ElementSetDefinition> sets) { this.elementSets = sets; return this; }
        public Builder rules(List<RuleSpec> rules) { this.rules = rules; return this; }
        public Builder parseErrors(List<String> errors) { this.parseErrors = errors; return this; }
        public RuleCheckerConfig build() { return new RuleCheckerConfig(this); }
    }
}
