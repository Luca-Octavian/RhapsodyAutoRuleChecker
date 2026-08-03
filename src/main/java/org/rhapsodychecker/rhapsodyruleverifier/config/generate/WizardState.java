// config/generate/WizardState.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;

import java.util.*;

public final class WizardState {

    private String                           scopePath  = "";
    private String                           sourcePath = null;
    private final List<ElementSetDefinition> sets       = new ArrayList<>();
    private final List<RuleRequest>          rules      = new ArrayList<>();

    // ── Getters ───────────────────────────────────────────────────────────────

    public String scopePath()  { return scopePath; }

    /**
     * Path of the YAML file this state was originally loaded from via
     * "Edit in Wizard", or null for a brand-new config. Used by WizardDialog
     * to save back to the same file without re-prompting for a location.
     */
    public String sourcePath() { return sourcePath; }

    public List<ElementSetDefinition> sets()    { return Collections.unmodifiableList(sets); }
    public List<RuleRequest>          rules()   { return Collections.unmodifiableList(rules); }

    // ── Fluent setters ────────────────────────────────────────────────────────

    public WizardState scopePath(String p)  { this.scopePath = p;  return this; }
    public WizardState sourcePath(String p) { this.sourcePath = p; return this; }

    public WizardState addSet(ElementSetDefinition s) { sets.add(s);    return this; }
    public WizardState addRule(RuleRequest r)         { rules.add(r);   return this; }

    // ── UI helpers ────────────────────────────────────────────────────────────

    public WizardState putSet(ElementSetDefinition s) {
        for (int i = 0; i < sets.size(); i++) {
            if (sets.get(i).id().equals(s.id())) { sets.set(i, s); return this; }
        }
        sets.add(s);
        return this;
    }

    public WizardState removeSet(String id)   { sets.removeIf(s -> s.id().equals(id));    return this; }
    public WizardState removeRule(int index)  { rules.remove(index);                      return this; }

    public WizardState replaceRule(int index, RuleRequest r) { rules.set(index, r); return this; }

    /** Toggles enabled on the rule at the given index, returning the state. */
    public WizardState setRuleEnabled(int index, boolean enabled) {
        rules.set(index, rules.get(index).withEnabled(enabled));
        return this;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static final class RuleRequest {

        private final String              id;
        private final String              title;
        private final String              ruleType;
        private final TargetSpec          targetSpec;
        private final String              elementSetId;
        private final Map<String, Object> params;
        private final String              message;
        private final boolean             enabled;
        private final String              group;

        /** Constructor without group or enabled — both default (null / true). */
        public RuleRequest(
                String              id,
                String              title,
                String              ruleType,
                TargetSpec          targetSpec,
                String              elementSetId,
                Map<String, Object> params,
                String              message
        ) {
            this(id, title, ruleType, targetSpec, elementSetId, params, message, true, null);
        }

        /** Constructor with enabled but no group. */
        public RuleRequest(
                String              id,
                String              title,
                String              ruleType,
                TargetSpec          targetSpec,
                String              elementSetId,
                Map<String, Object> params,
                String              message,
                boolean             enabled
        ) {
            this(id, title, ruleType, targetSpec, elementSetId, params, message, enabled, null);
        }

        /** Full constructor with enabled and group. */
        public RuleRequest(
                String              id,
                String              title,
                String              ruleType,
                TargetSpec          targetSpec,
                String              elementSetId,
                Map<String, Object> params,
                String              message,
                boolean             enabled,
                String              group
        ) {
            this.id            = id;
            this.title         = title;
            this.ruleType      = ruleType;
            this.targetSpec    = targetSpec;
            this.elementSetId  = elementSetId;
            this.params        = Collections.unmodifiableMap(
                                     new LinkedHashMap<>(params != null
                                             ? params
                                             : Collections.emptyMap()));
            this.message       = message;
            this.enabled       = enabled;
            this.group         = group;
        }

        public String              id()            { return id; }
        public String              title()         { return title; }
        public String              ruleType()      { return ruleType; }
        public TargetSpec          targetSpec()    { return targetSpec; }
        public String              elementSetId()  { return elementSetId; }
        public Map<String, Object> params()        { return params; }
        public String              message()       { return message; }
        public boolean             isEnabled()     { return enabled; }
        public String              group()         { return group; }

        /** Returns a copy with enabled modified (immutability preserved). */
        public RuleRequest withEnabled(boolean enabled) {
            return new RuleRequest(id, title, ruleType, targetSpec,
                                   elementSetId, params, message, enabled, group);
        }

        /** Returns a copy with group modified (immutability preserved). */
        public RuleRequest withGroup(String group) {
            return new RuleRequest(id, title, ruleType, targetSpec,
                                   elementSetId, params, message, enabled, group);
        }

        @Override
        public String toString() {
            return "[" + ruleType + "] " + (title != null && !title.trim().isEmpty() ? title : id);
        }
    }
}