package org.rhapsodychecker.rhapsodyruleverifier.fix;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable description of a single atomic change to apply to a Rhapsody model element.
 * Does not carry execution state — see {@link FixEntry} for that.
 */
public final class FixAction {

    private final String elementGuid;
    private final String elementName;
    private final FixActionType actionType;
    private final String field;       // e.g. tag name, stereotype name, or null for name/description
    private final String oldValue;    // current value (from snapshot), may be null
    private final String newValue;    // desired value, may be null (e.g. for REMOVE_STEREOTYPE)
    private final String ruleId;      // rule that suggested this fix
    private final String description; // human-readable summary
    private final List<String> options; // optional: allowed values for user to pick from (e.g. stereotype choices)

    private FixAction(Builder b) {
        this.elementGuid = Objects.requireNonNull(b.elementGuid, "elementGuid");
        this.elementName = Objects.requireNonNull(b.elementName, "elementName");
        this.actionType = Objects.requireNonNull(b.actionType, "actionType");
        this.field = b.field;
        this.oldValue = b.oldValue;
        this.newValue = b.newValue;
        this.ruleId = Objects.requireNonNull(b.ruleId, "ruleId");
        this.description = b.description != null ? b.description : buildDefaultDescription();
        this.options = b.options != null ? Collections.unmodifiableList(b.options) : null;
    }

    public String elementGuid() { return elementGuid; }
    public String elementName() { return elementName; }
    public FixActionType actionType() { return actionType; }
    public String field() { return field; }
    public String oldValue() { return oldValue; }
    public String newValue() { return newValue; }
    public String ruleId() { return ruleId; }
    public String description() { return description; }
    /** Optional list of allowed values the user can pick from (e.g. stereotype choices). */
    public List<String> options() { return options; }

    private String buildDefaultDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(actionType.name()).append(" on '").append(elementName).append("'");
        if (field != null) {
            sb.append(" [").append(field).append("]");
        }
        if (oldValue != null) {
            sb.append(": '").append(oldValue).append("'");
        }
        sb.append(" -> '").append(newValue != null ? newValue : "<remove>").append("'");
        return sb.toString();
    }

    @Override
    public String toString() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixAction)) return false;
        FixAction that = (FixAction) o;
        return elementGuid.equals(that.elementGuid)
                && actionType == that.actionType
                && Objects.equals(field, that.field)
                && Objects.equals(newValue, that.newValue)
                && ruleId.equals(that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementGuid, actionType, field, newValue, ruleId);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String elementGuid;
        private String elementName;
        private FixActionType actionType;
        private String field;
        private String oldValue;
        private String newValue;
        private String ruleId;
        private String description;
        private List<String> options;

        private Builder() {}

        public Builder elementGuid(String v) { this.elementGuid = v; return this; }
        public Builder elementName(String v) { this.elementName = v; return this; }
        public Builder actionType(FixActionType v) { this.actionType = v; return this; }
        public Builder field(String v) { this.field = v; return this; }
        public Builder oldValue(String v) { this.oldValue = v; return this; }
        public Builder newValue(String v) { this.newValue = v; return this; }
        public Builder ruleId(String v) { this.ruleId = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder options(List<String> v) { this.options = v; return this; }

        public FixAction build() { return new FixAction(this); }
    }
}