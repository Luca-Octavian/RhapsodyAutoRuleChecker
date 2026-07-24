// config/generate/WizardState.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;

import java.util.*;

public final class WizardState {

    private String                           scopePath = "";
    private String                           mode      = "lenient";
    private final List<AliasDefinition>      aliases   = new ArrayList<>();
    private final List<ElementSetDefinition> sets      = new ArrayList<>();
    private final List<RuleRequest>          rules     = new ArrayList<>();

    // ── Getters ───────────────────────────────────────────────────────────────

    public String scopePath() { return scopePath; }
    public String mode()      { return mode; }

    public List<AliasDefinition>      aliases() { return Collections.unmodifiableList(aliases); }
    public List<ElementSetDefinition> sets()    { return Collections.unmodifiableList(sets); }
    public List<RuleRequest>          rules()   { return Collections.unmodifiableList(rules); }

    // ── Fluent setters ────────────────────────────────────────────────────────

    public WizardState scopePath(String p) { this.scopePath = p; return this; }
    public WizardState mode(String m)      { this.mode = m;      return this; }

    public WizardState addAlias(AliasDefinition a)    { aliases.add(a); return this; }
    public WizardState addSet(ElementSetDefinition s) { sets.add(s);    return this; }
    public WizardState addRule(RuleRequest r)         { rules.add(r);   return this; }

    // ── UI helpers ────────────────────────────────────────────────────────────

    public WizardState putAlias(AliasDefinition a) {
        for (int i = 0; i < aliases.size(); i++) {
            if (aliases.get(i).id().equals(a.id())) { aliases.set(i, a); return this; }
        }
        aliases.add(a);
        return this;
    }

    public WizardState putSet(ElementSetDefinition s) {
        for (int i = 0; i < sets.size(); i++) {
            if (sets.get(i).id().equals(s.id())) { sets.set(i, s); return this; }
        }
        sets.add(s);
        return this;
    }

    public WizardState removeAlias(String id) { aliases.removeIf(a -> a.id().equals(id)); return this; }
    public WizardState removeSet(String id)   { sets.removeIf(s -> s.id().equals(id));    return this; }
    public WizardState removeRule(int index)  { rules.remove(index);                      return this; }

    public WizardState replaceRule(int index, RuleRequest r) { rules.set(index, r); return this; }

    /** Togglează enabled pe regula de la index, returnând starea. */
    public WizardState setRuleEnabled(int index, boolean enabled) {
        rules.set(index, rules.get(index).withEnabled(enabled));
        return this;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static final class RuleRequest {

        private final String              id;
        private final String              title;
        private final String              ruleType;
        private final String              targetAliasId;
        private final String              elementSetId;
        private final Map<String, Object> params;
        private final String              message;
        private final boolean             enabled;          // ← NOU (default true)

        /** Constructor original — enabled = true implicit. */
        public RuleRequest(
                String              id,
                String              title,
                String              ruleType,
                String              targetAliasId,
                String              elementSetId,
                Map<String, Object> params,
                String              message
        ) {
            this(id, title, ruleType, targetAliasId, elementSetId, params, message, true);
        }

        /** Constructor complet cu enabled. */
        public RuleRequest(
                String              id,
                String              title,
                String              ruleType,
                String              targetAliasId,
                String              elementSetId,
                Map<String, Object> params,
                String              message,
                boolean             enabled
        ) {
            this.id            = id;
            this.title         = title;
            this.ruleType      = ruleType;
            this.targetAliasId = targetAliasId;
            this.elementSetId  = elementSetId;
            this.params        = Collections.unmodifiableMap(
                                     new LinkedHashMap<>(params != null
                                             ? params
                                             : Collections.emptyMap()));
            this.message       = message;
            this.enabled       = enabled;
        }

        public String              id()            { return id; }
        public String              title()         { return title; }
        public String              ruleType()      { return ruleType; }
        public String              targetAliasId() { return targetAliasId; }
        public String              elementSetId()  { return elementSetId; }
        public Map<String, Object> params()        { return params; }
        public String              message()       { return message; }
        public boolean             isEnabled()     { return enabled; }

        /** Returnează o copie cu enabled modificat (imutabilitate păstrată). */
        public RuleRequest withEnabled(boolean enabled) {
            return new RuleRequest(id, title, ruleType, targetAliasId,
                                   elementSetId, params, message, enabled);
        }

        @Override
        public String toString() {
            return "[" + ruleType + "] " + (title != null && !title.trim().isEmpty() ? title : id);
        }
    }
}
