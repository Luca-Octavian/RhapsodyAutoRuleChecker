// detection/api/AliasGuess.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Sugestii de alias-uri derivate automat din model.
 * Wizard-ul le prezintă utilizatorului pre-populate — user-ul doar confirmă sau ajustează.
 *
 * descriptionSource  → de unde să citim descrierea (câmp nativ vs tag)
 * asilTagCandidates  → tag-uri care par să fie ASIL (ex: "ASIL", "SafetyLevel")
 * asilStereoCandidates → stereotipuri care par să fie ASIL (ex: "ASIL_A", "QM")
 * port*Resolvable    → dacă API-ul poate rezolva proprietatea respectivă pe porturi
 */
public final class AliasGuess {

    public enum DescriptionSource { NATIVE_DESCRIPTION, TAG, UNKNOWN }

    private final DescriptionSource descriptionSource;
    private final String            descriptionTagName;
    private final List<String>      asilTagCandidates;
    private final List<String>      asilStereoCandidates;
    private final boolean           portTypeResolvable;
    private final boolean           portDirectionResolvable;
    private final boolean           portMultiplicityResolvable;

    private AliasGuess(Builder b) {
        this.descriptionSource          = b.descriptionSource;
        this.descriptionTagName         = b.descriptionTagName;
        this.asilTagCandidates          = freeze(b.asilTagCandidates);
        this.asilStereoCandidates       = freeze(b.asilStereoCandidates);
        this.portTypeResolvable         = b.portTypeResolvable;
        this.portDirectionResolvable    = b.portDirectionResolvable;
        this.portMultiplicityResolvable = b.portMultiplicityResolvable;
    }

    public DescriptionSource descriptionSource()          { return descriptionSource; }
    public Optional<String>  descriptionTagName()         { return Optional.ofNullable(descriptionTagName); }
    public List<String>      asilTagCandidates()          { return asilTagCandidates; }
    public List<String>      asilStereoCandidates()       { return asilStereoCandidates; }
    public boolean           portTypeResolvable()         { return portTypeResolvable; }
    public boolean           portDirectionResolvable()    { return portDirectionResolvable; }
    public boolean           portMultiplicityResolvable() { return portMultiplicityResolvable; }

    private static <T> List<T> freeze(List<T> in) {
        return in == null ? Collections.emptyList() : Collections.unmodifiableList(in);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private DescriptionSource descriptionSource = DescriptionSource.UNKNOWN;
        private String            descriptionTagName;
        private List<String>      asilTagCandidates;
        private List<String>      asilStereoCandidates;
        private boolean           portTypeResolvable;
        private boolean           portDirectionResolvable;
        private boolean           portMultiplicityResolvable;

        public Builder descriptionSource(DescriptionSource s)   { this.descriptionSource = s; return this; }
        public Builder descriptionTagName(String t)             { this.descriptionTagName = t; return this; }
        public Builder asilTagCandidates(List<String> l)        { this.asilTagCandidates = l; return this; }
        public Builder asilStereoCandidates(List<String> l)     { this.asilStereoCandidates = l; return this; }
        public Builder portTypeResolvable(boolean v)            { this.portTypeResolvable = v; return this; }
        public Builder portDirectionResolvable(boolean v)       { this.portDirectionResolvable = v; return this; }
        public Builder portMultiplicityResolvable(boolean v)    { this.portMultiplicityResolvable = v; return this; }
        public AliasGuess build()                               { return new AliasGuess(this); }
    }
}
