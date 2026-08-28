package org.rhapsodychecker.rhapsodyruleverifier.config;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.*;

/**
 * Immutable specification for a single rule, parsed from YAML config.
 */
public final class RuleSpec {
    private final String id;
    private final RuleType type;
    private final boolean enabled;
    private final String title;
    private final String message;

    // Optional grouping label. Rules sharing the same group are evaluated
    // individually and their per-element results are then conjoined (ALL must
    // pass) in a pure post-processing step by RuleGrouping.
    private final String group;

    // Scope: either a set reference or inline filters
    private final String appliesToSet;
    private final List<String> appliesToTypes;
    private final List<String> appliesToStereotypes;
    private final List<String> appliesToIncludePackages;
    private final List<String> appliesToExcludePackages;

    // Conditions (optional predicates)
    private final List<Map<String, Object>> conditions;

    // Target specification for value-based rules
    private final TargetSpec target;

    // Rule-specific parameters
    private final Map<String, Object> params;

    private RuleSpec(Builder b) {
        this.id = requireNonBlank(b.id, "rule id");
        this.type = Objects.requireNonNull(b.type, "rule type must not be null for '" + b.id + "'");
        this.enabled = b.enabled;
        this.title = b.title;
        this.message = b.message;
        this.group = b.group;

        this.appliesToSet = b.appliesToSet;
        this.appliesToTypes = freezeList(b.appliesToTypes);
        this.appliesToStereotypes = freezeList(b.appliesToStereotypes);
        this.appliesToIncludePackages = freezeList(b.appliesToIncludePackages);
        this.appliesToExcludePackages = freezeList(b.appliesToExcludePackages);

        this.conditions = b.conditions != null
                ? Collections.unmodifiableList(new ArrayList<>(b.conditions))
                : Collections.emptyList();

        this.target = b.target;


        this.params = b.params != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(b.params))
                : Collections.emptyMap();
    }

    public String id() { return id; }
    public RuleType type() { return type; }
    public boolean enabled() { return enabled; }
    public Optional<String> title() { return Optional.ofNullable(title); }
    public Optional<String> message() { return Optional.ofNullable(message); }

    /**
     * Optional group label. When non-null, results from all rules sharing the
     * same group are conjoined per element: the element passes the group only
     * if every rule in the group passes for it.
     */
    public Optional<String> group() { return Optional.ofNullable(group); }

    public Optional<String> appliesToSet() { return Optional.ofNullable(appliesToSet); }
    public List<String> appliesToTypes() { return appliesToTypes; }
    public List<String> appliesToStereotypes() { return appliesToStereotypes; }
    public List<String> appliesToIncludePackages() { return appliesToIncludePackages; }
    public List<String> appliesToExcludePackages() { return appliesToExcludePackages; }

    public List<Map<String, Object>> conditions() { return conditions; }
    public Optional<TargetSpec> target() { return Optional.ofNullable(target); }
    public Map<String, Object> params() { return params; }

    @Override
    public String toString() {
        return "RuleSpec{id='" + id + "', type=" + type + ", enabled=" + enabled
                + (group != null ? ", group='" + group + "'" : "") + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private RuleType type;
        private boolean enabled = true;
        private String title;
        private String message;
        private String group;
        private String appliesToSet;
        private List<String> appliesToTypes;
        private List<String> appliesToStereotypes;
        private List<String> appliesToIncludePackages;
        private List<String> appliesToExcludePackages;
        private List<Map<String, Object>> conditions;
        private TargetSpec target;
        private Map<String, Object> params;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder type(RuleType type) { this.type = type; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder appliesToSet(String set) { this.appliesToSet = set; return this; }
        public Builder appliesToTypes(List<String> types) { this.appliesToTypes = types; return this; }
        public Builder appliesToStereotypes(List<String> stereos) { this.appliesToStereotypes = stereos; return this; }
        public Builder appliesToIncludePackages(List<String> pkgs) { this.appliesToIncludePackages = pkgs; return this; }
        public Builder appliesToExcludePackages(List<String> pkgs) { this.appliesToExcludePackages = pkgs; return this; }
        public Builder conditions(List<Map<String, Object>> conditions) { this.conditions = conditions; return this; }
        public Builder target(TargetSpec target) { this.target = target; return this; }
        public Builder params(Map<String, Object> params) { this.params = params; return this; }
        public RuleSpec build() { return new RuleSpec(this); }
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