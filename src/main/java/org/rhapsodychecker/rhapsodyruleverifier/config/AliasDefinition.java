package org.rhapsodychecker.rhapsodyruleverifier.config;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ValueType;

import java.util.*;

/**
 * Immutable definition of a config alias.
 * Maps a friendly name to a model property (description, tag, stereotype, etc.).
 */
public final class AliasDefinition {
    private final String id;
    private final AliasKind kind;
    private final String title;
    private final String help;
    private final ValueType valueType;

    // For taggedValue
    private final String profileName;
    private final String tagName;
    private final String stereotypeName; // optional: stereotype that owns the tag

    // For stereotypeSet
    private final List<String> stereotypeNames;

    // Allowed/canonical values
    private final List<String> values;

    private AliasDefinition(Builder b) {
        this.id = requireNonBlank(b.id, "alias id");
        this.kind = Objects.requireNonNull(b.kind, "alias kind must not be null for '" + b.id + "'");
        this.title = b.title;
        this.help = b.help;
        this.valueType = b.valueType != null ? b.valueType : ValueType.STRING;

        this.profileName = b.profileName;
        this.tagName = b.tagName;
        this.stereotypeName = b.stereotypeName;
        this.stereotypeNames = b.stereotypeNames != null
                ? Collections.unmodifiableList(new ArrayList<>(b.stereotypeNames))
                : Collections.emptyList();
        this.values = b.values != null
                ? Collections.unmodifiableList(new ArrayList<>(b.values))
                : Collections.emptyList();

        validate();
    }

    private void validate() {
        switch (kind) {
            case TAGGED_VALUE:
                if (profileName == null || profileName.trim().isEmpty())
                    throw new IllegalArgumentException("Alias '" + id + "': taggedValue requires profileName");
                if (tagName == null || tagName.trim().isEmpty())
                    throw new IllegalArgumentException("Alias '" + id + "': taggedValue requires tagName");
                break;
            case STEREOTYPE:
                if (stereotypeName == null || stereotypeName.trim().isEmpty())
                    throw new IllegalArgumentException("Alias '" + id + "': stereotype requires stereotypeName");
                break;
            case STEREOTYPE_SET:
                if (stereotypeNames.isEmpty())
                    throw new IllegalArgumentException("Alias '" + id + "': stereotypeSet requires non-empty stereotypeNames");
                break;
            case DESCRIPTION:
            case NAME:
                // No extra fields required
                break;
        }
    }

    public String id() { return id; }
    public AliasKind kind() { return kind; }
    public Optional<String> title() { return Optional.ofNullable(title); }
    public Optional<String> help() { return Optional.ofNullable(help); }
    public ValueType valueType() { return valueType; }
    public Optional<String> profileName() { return Optional.ofNullable(profileName); }
    public Optional<String> tagName() { return Optional.ofNullable(tagName); }
    public Optional<String> stereotypeName() { return Optional.ofNullable(stereotypeName); }
    public List<String> stereotypeNames() { return stereotypeNames; }
    public List<String> values() { return values; }

    @Override
    public String toString() {
        return "AliasDefinition{id='" + id + "', kind=" + kind + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private AliasKind kind;
        private String title;
        private String help;
        private ValueType valueType;
        private String profileName;
        private String tagName;
        private String stereotypeName;
        private List<String> stereotypeNames;
        private List<String> values;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder kind(AliasKind kind) { this.kind = kind; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder help(String help) { this.help = help; return this; }
        public Builder valueType(ValueType valueType) { this.valueType = valueType; return this; }
        public Builder profileName(String profileName) { this.profileName = profileName; return this; }
        public Builder tagName(String tagName) { this.tagName = tagName; return this; }
        public Builder stereotypeName(String stereotypeName) { this.stereotypeName = stereotypeName; return this; }
        public Builder stereotypeNames(List<String> stereotypeNames) { this.stereotypeNames = stereotypeNames; return this; }
        public Builder values(List<String> values) { this.values = values; return this; }
        public AliasDefinition build() { return new AliasDefinition(this); }
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be null/blank");
        return v;
    }
}