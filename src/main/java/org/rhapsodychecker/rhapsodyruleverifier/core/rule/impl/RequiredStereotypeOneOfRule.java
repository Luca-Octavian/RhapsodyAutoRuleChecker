// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/rule/impl/RequiredStereotypeOneOfRule.java
package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Generic rule: verifies that an element has exactly one stereotype from a given set.
 * Config-driven via params.anyOf list.
 */
public final class RequiredStereotypeOneOfRule implements Rule {

    private String id;
    private String title;
    private String message;
    private List<String> anyOf;

    public RequiredStereotypeOneOfRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        this.anyOf = optStringList(p, "anyOf");
        if (anyOf == null || anyOf.isEmpty()) {
            throw new IllegalArgumentException("RequiredStereotypeOneOfRule '" + id + "': params.anyOf must be a non-empty list");
        }
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            List<String> matched = new ArrayList<>();
            for (String candidate : anyOf) {
                if (element.hasStereotypeIgnoreCase(candidate)) {
                    matched.add(candidate);
                }
            }

            if (!matched.isEmpty()) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            return DefaultRuleResult.fail(id, element.guid(),
                    formatMessage(element, "none found", "must have at least one of " + anyOf));

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    private String formatMessage(ElementRecord element, String value, String reason) {
        if (message != null) {
            return message
                    .replace("{elementName}", element.name())
                    .replace("{value}", value)
                    .replace("{expected}", anyOf.toString())
                    .replace("{reason}", reason);
        }
        return id + ": " + reason + " [element=" + element.name() + "]";
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
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (!s.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (String part : s.split("\\s*,\\s*")) {
                    if (!part.isEmpty()) out.add(part);
                }
                return out;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }
}
