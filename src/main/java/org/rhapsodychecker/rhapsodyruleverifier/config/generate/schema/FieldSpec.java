// config/generate/schema/FieldSpec.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Descriptor for a single field in the wizard form.
 *
 * paramKey          - the key in RuleRequest.params() (e.g. "nonEmpty", "anyOf", "operator")
 * label             - the label displayed in the UI
 * fieldType         - the type of input control rendered
 * required          - whether a missing value blocks rule creation
 * fixedOptions      - predefined fixed values for DROPDOWN (e.g. operators, directions)
 * suggestionsSource - where the list for MULTI_SELECT/SINGLE_SELECT comes from
 *                     (e.g. STEREOTYPES, TAG_VALUES, ELEMENT_KINDS)
 * defaultValue      - optional pre-filled value
 */
public final class FieldSpec {

    public enum SuggestionsSource {
        NONE,
        STEREOTYPES,        // from FastDetectionResult.countsByStereotype()
        TAG_VALUES,         // from TagDiscoveryResult for a specific tag
        ELEMENT_KINDS       // from the ElementKind enum
    }

    private final String            paramKey;
    private final String            label;
    private final FieldType         fieldType;
    private final boolean           required;
    private final List<String>      fixedOptions;
    private final SuggestionsSource suggestionsSource;
    private final Optional<String>  defaultValue;

    private FieldSpec(Builder b) {
        this.paramKey          = b.paramKey;
        this.label             = b.label;
        this.fieldType         = b.fieldType;
        this.required          = b.required;
        this.fixedOptions      = b.fixedOptions != null
                ? Collections.unmodifiableList(b.fixedOptions)
                : Collections.emptyList();
        this.suggestionsSource = b.suggestionsSource != null
                ? b.suggestionsSource
                : SuggestionsSource.NONE;
        this.defaultValue      = Optional.ofNullable(b.defaultValue);
    }

    public String            paramKey()          { return paramKey; }
    public String            label()             { return label; }
    public FieldType         fieldType()         { return fieldType; }
    public boolean           required()          { return required; }
    public List<String>      fixedOptions()      { return fixedOptions; }
    public SuggestionsSource suggestionsSource() { return suggestionsSource; }
    public Optional<String>  defaultValue()      { return defaultValue; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String            paramKey;
        private String            label;
        private FieldType         fieldType;
        private boolean           required;
        private List<String>      fixedOptions;
        private SuggestionsSource suggestionsSource;
        private String            defaultValue;

        public Builder paramKey(String k)                  { this.paramKey = k;           return this; }
        public Builder label(String l)                     { this.label = l;              return this; }
        public Builder fieldType(FieldType t)              { this.fieldType = t;          return this; }
        public Builder required(boolean r)                 { this.required = r;           return this; }
        public Builder fixedOptions(List<String> o)        { this.fixedOptions = o;       return this; }
        public Builder suggestionsSource(SuggestionsSource s) { this.suggestionsSource = s; return this; }
        public Builder defaultValue(String d)              { this.defaultValue = d;       return this; }
        public FieldSpec build()                           { return new FieldSpec(this);  }
    }
}
