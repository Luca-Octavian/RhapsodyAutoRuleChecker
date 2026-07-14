// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/rule/DefaultRuleResult.java
package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import java.util.*;

/**
 * Immutable implementation of RuleResult.
 * Created by rules after evaluation; consumed by engine, UI, and export.
 */
public final class DefaultRuleResult implements RuleResult {

    private final RuleStatus status;
    private final String ruleId;
    private final String elementGuid;
    private final String message;
    private final Map<String, Object> details;

    private DefaultRuleResult(Builder b) {
        this.status = Objects.requireNonNull(b.status, "status");
        this.ruleId = Objects.requireNonNull(b.ruleId, "ruleId");
        this.elementGuid = Objects.requireNonNull(b.elementGuid, "elementGuid");
        this.message = b.message != null ? b.message : "";
        this.details = b.details != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(b.details))
                : Collections.emptyMap();
    }

    @Override public RuleStatus status() { return status; }
    @Override public String ruleId() { return ruleId; }
    @Override public String elementGuid() { return elementGuid; }
    @Override public String message() { return message; }
    @Override public Optional<Map<String, Object>> details() {
        return details.isEmpty() ? Optional.empty() : Optional.of(details);
    }

    @Override
    public String toString() {
        return status + " | " + ruleId + " | " + elementGuid + " | " + message;
    }

    public static Builder builder() { return new Builder(); }

    // Convenience factory methods for common cases
    public static RuleResult pass(String ruleId, String elementGuid) {
        return builder().status(RuleStatus.PASS).ruleId(ruleId).elementGuid(elementGuid).build();
    }

    public static RuleResult fail(String ruleId, String elementGuid, String message) {
        return builder().status(RuleStatus.FAIL).ruleId(ruleId).elementGuid(elementGuid).message(message).build();
    }

    public static RuleResult skipped(String ruleId, String elementGuid, String reason) {
        return builder().status(RuleStatus.SKIPPED).ruleId(ruleId).elementGuid(elementGuid).message(reason).build();
    }

    public static final class Builder {
        private RuleStatus status;
        private String ruleId;
        private String elementGuid;
        private String message;
        private Map<String, Object> details;

        private Builder() {}

        public Builder status(RuleStatus status) { this.status = status; return this; }
        public Builder ruleId(String ruleId) { this.ruleId = ruleId; return this; }
        public Builder elementGuid(String elementGuid) { this.elementGuid = elementGuid; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder details(Map<String, Object> details) { this.details = details; return this; }
        public Builder detail(String key, Object value) {
            if (this.details == null) this.details = new LinkedHashMap<>();
            this.details.put(key, value);
            return this;
        }
        public DefaultRuleResult build() { return new DefaultRuleResult(this); }
    }
}
