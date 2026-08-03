package org.rhapsodychecker.rhapsodyruleverifier.config;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ValueType;

import java.util.*;

/**
 * Immutable specification of what a rule's target resolves to.
 * Replaces the old AliasDefinition (minus the id/title/help indirection).
 * Embedded directly on each RuleSpec.
 */
public final class TargetSpec {
    private final AliasKind kind;
    private final ValueType valueType;

    // For TAGGED_VALUE
    private final String profileName;
    private final String tagName;
    private final String stereotypeName; // optional: stereotype that owns the tag

    // Allowed/canonical values (for TAGGED_VALUE stereotype fallback)
    private final List<String> values;

    private TargetSpec(Builder b) {
        this.kind = Objects.requireNonNull(b.kind, "target kind must not be null");
        this.valueType = b.valueType != null ? b.valueType : ValueType.STRING;

        this.profileName = b.profileName;
        this.tagName = b.tagName;
        this.stereotypeName = b.stereotypeName;
        this.values = b.values != null
                ? Collections.unmodifiableList(new ArrayList<>(b.values))
                : Collections.emptyList();

        validate();
    }

    private void validate() {
        switch (kind) {
            case TAGGED_VALUE:
                if (profileName == null || profileName.trim().isEmpty())
                    throw new IllegalArgumentException("TargetSpec: taggedValue requires profileName");
                if (tagName == null || tagName.trim().isEmpty())
                    throw new IllegalArgumentException("TargetSpec: taggedValue requires tagName");
                break;
            case DESCRIPTION:
            case NAME:
            case PORT_TYPE:
            case PORT_DIRECTION:
            case PORT_MULTIPLICITY:
                // No extra fields required
                break;
        }
    }

    public AliasKind kind() { return kind; }
    public ValueType valueType() { return valueType; }
    public Optional<String> profileName() { return Optional.ofNullable(profileName); }
    public Optional<String> tagName() { return Optional.ofNullable(tagName); }
    public Optional<String> stereotypeName() { return Optional.ofNullable(stereotypeName); }
    public List<String> values() { return values; }

    @Override
    public String toString() {
        return "TargetSpec{kind=" + kind + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private AliasKind kind;
        private ValueType valueType;
        private String profileName;
        private String tagName;
        private String stereotypeName;
        private List<String> values;

        private Builder() {}

        public Builder kind(AliasKind kind) { this.kind = kind; return this; }
        public Builder valueType(ValueType valueType) { this.valueType = valueType; return this; }
        public Builder profileName(String profileName) { this.profileName = profileName; return this; }
        public Builder tagName(String tagName) { this.tagName = tagName; return this; }
        public Builder stereotypeName(String stereotypeName) { this.stereotypeName = stereotypeName; return this; }
        public Builder values(List<String> values) { this.values = values; return this; }
        public TargetSpec build() { return new TargetSpec(this); }
    }
}