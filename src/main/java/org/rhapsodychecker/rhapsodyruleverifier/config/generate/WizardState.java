// config/generate/WizardState.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;

import java.util.*;

/**
 * Starea acumulată a wizard-ului pe durata configurării.
 * Fiecare pas din wizard adaugă/modifică intrări.
 * ConfigBuilder.build(state) produce RuleCheckerConfig gata de execuție.
 */
public final class WizardState {

    private String                     scopePath = "";
    private String                     mode      = "lenient";
    private final List<AliasDefinition>      aliases = new ArrayList<>();
    private final List<ElementSetDefinition> sets    = new ArrayList<>();
    private final List<RuleRequest>          rules   = new ArrayList<>();

    public String scopePath() { return scopePath; }
    public String mode()      { return mode; }

    public List<AliasDefinition>      aliases() { return Collections.unmodifiableList(aliases); }
    public List<ElementSetDefinition> sets()    { return Collections.unmodifiableList(sets); }
    public List<RuleRequest>          rules()   { return Collections.unmodifiableList(rules); }

    public WizardState scopePath(String p) { this.scopePath = p; return this; }
    public WizardState mode(String m)      { this.mode = m;      return this; }

    public WizardState addAlias(AliasDefinition a)      { aliases.add(a); return this; }
    public WizardState addSet(ElementSetDefinition s)   { sets.add(s);    return this; }
    public WizardState addRule(RuleRequest r)           { rules.add(r);   return this; }

    // ---------------------------------------------------------------

    /**
     * Cerere de regulă exprimată în termeni de utilizator (fără YAML).
     * Ex: "vreau că Block-ul să aibă descriere" →
     *     RuleRequest(type=RequiredValue, targetAlias=ELEMENT_DESCRIPTION, set=ArchitectureElements)
     */
    public static final class RuleRequest {

        private final String                  id;
        private final String                  title;
        private final String                  ruleType;
        private final String                  targetAliasId;
        private final String                  elementSetId;
        private final Map<String, Object>     params;
        private final String                  message;

        public RuleRequest(
                String              id,
                String              title,
                String              ruleType,
                String              targetAliasId,
                String              elementSetId,
                Map<String, Object> params,
                String              message
        ) {
            this.id           = id;
            this.title        = title;
            this.ruleType     = ruleType;
            this.targetAliasId = targetAliasId;
            this.elementSetId = elementSetId;
            this.params       = Collections.unmodifiableMap(new LinkedHashMap<>(params));
            this.message      = message;
        }

        public String              id()            { return id; }
        public String              title()         { return title; }
        public String              ruleType()      { return ruleType; }
        public String              targetAliasId() { return targetAliasId; }
        public String              elementSetId()  { return elementSetId; }
        public Map<String, Object> params()        { return params; }
        public String              message()       { return message; }
    }
}
