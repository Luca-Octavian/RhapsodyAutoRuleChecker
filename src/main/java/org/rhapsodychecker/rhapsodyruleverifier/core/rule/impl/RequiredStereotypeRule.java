// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/rule/impl/RequiredStereotypeRule.java
package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Generic rule: verifies that an element has ALL of the required stereotypes.
 * Config-driven via params.requiredStereotypes list.
 */
public final class RequiredStereotypeRule implements Rule {

    private String id;
    private String title;
    private String message;
    private List<String> requiredStereotypes;

    public RequiredStereotypeRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        this.requiredStereotypes = optStringList(p, "requiredStereotypes");
        if (requiredStereotypes == null || requiredStereotypes.isEmpty()) {
            throw new IllegalArgumentException("RequiredStereotypeRule '" + id + "': params.requiredStereotypes must be a non-empty list");
        }
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            Set<String> elementStereos = element.stereotypes();
            List<String> missing = new ArrayList<>();

            for (String required : requiredStereotypes) {
                if (!element.hasStereotypeIgnoreCase(required)) {
                    missing.add(required);
                }
            }

            if (missing.isEmpty()) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            return DefaultRuleResult.fail(id, element.guid(),
                    formatMessage(element, missing));

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    private String formatMessage(ElementRecord element, List<String> missing) {
        if (message != null) {
            return message
                    .replace("{elementName}", element.name())
                    .replace("{missing}", missing.toString())
                    .replace("{required}", requiredStereotypes.toString());
        }
        return id + ": missing stereotypes " + missing + " on element " + element.name();
    }

    private static String requireString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required param: " + key);
        return v.toString();
    }

    private static String optString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static List<String> optStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object item : (List<?>) v) if (item != null) out.add(item.toString());
            return out;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }
}
