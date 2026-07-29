// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/rule/impl/RequiredValueRule.java
package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.ComparisonOperator;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.ResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Generic rule: verifies that a target alias resolves to a value
 * that satisfies configured constraints (nonEmpty, minLength, operator, allowedValues).
 * Entirely config-driven — one class serves all RequiredValue rules.
 */
public final class RequiredValueRule implements Rule {

    private String id;
    private String title;
    private String message;
    private String targetAlias;
    private boolean passIfAbsent;

    // Params
    private boolean nonEmpty;
    private int minLength;
    private int maxLength;
    private ComparisonOperator operator;
    private Object compareValue;       // single value for eq/neq/gt/gte/lt/lte
    private Number rangeMin;           // for between
    private Number rangeMax;           // for between
    private List<String> allowedValues; // for in/not_in
    private String pattern;            // for matches

    public RequiredValueRule() {}

    @Override
    public String id() { return id; }

    @Override
    public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");
        this.targetAlias = requireString(params, "target");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        this.nonEmpty = optBool(p, "nonEmpty", false);
        this.minLength = optInt(p, "minLength", 0);
        this.passIfAbsent = optBool(p, "passIfAbsent", false);
        this.maxLength = optInt(p, "maxLength", Integer.MAX_VALUE);

        String opStr = optString(p, "operator");
        if (opStr != null) {
            this.operator = ComparisonOperator.fromString(opStr);
        }

        this.compareValue = p.get("value");
        this.rangeMin = optNumber(p, "min");
        this.rangeMax = optNumber(p, "max");
        this.allowedValues = optStringList(p, "values");
        this.pattern = optString(p, "pattern");
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        // Selector already filtered candidates; always apply
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            AliasResolver resolver = context.aliases();
            ResolvedValue resolved = resolver.resolveValue(element, targetAlias);

            // Check presence
            if (!resolved.isPresent()) {
                if (passIfAbsent) {
                    return DefaultRuleResult.pass(id, element.guid());
                }
                if (nonEmpty || operator != null) {
                    return DefaultRuleResult.fail(id, element.guid(),
                            formatMessage(element, "<absent>", "value is required but absent"));
                }
                return DefaultRuleResult.skipped(id, element.guid(), "Value not present, not required");
            }
            String value = resolved.asString().orElse("");

            // NonEmpty check
            if (nonEmpty && value.trim().isEmpty()) {
                return DefaultRuleResult.fail(id, element.guid(),
                        formatMessage(element, value, "value must not be empty"));
            }

            // MinLength check
            if (minLength > 0 && value.length() < minLength) {
                return DefaultRuleResult.fail(id, element.guid(),
                        formatMessage(element, value, "value too short (min " + minLength + ")"));
            }

            // MaxLength check
            if (maxLength < Integer.MAX_VALUE && value.length() > maxLength) {
                return DefaultRuleResult.fail(id, element.guid(),
                        formatMessage(element, value, "value too long (max " + maxLength + ")"));
            }

            // Operator-based checks
            if (operator != null) {
                String opResult = evaluateOperator(value);
                if (opResult != null) {
                    return DefaultRuleResult.fail(id, element.guid(),
                            formatMessage(element, value, opResult));
                }
            }

            return DefaultRuleResult.pass(id, element.guid());

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    /**
     * Returns null if operator check passes, or an error message if it fails.
     */
    private String evaluateOperator(String value) {
        switch (operator) {
            case IN:
                if (allowedValues != null && !allowedValues.contains(value)) {
                    return "value '" + value + "' not in allowed values " + allowedValues;
                }
                return null;

            case NOT_IN:
                if (allowedValues != null && allowedValues.contains(value)) {
                    return "value '" + value + "' is in excluded values " + allowedValues;
                }
                return null;

            case EQ:
                if (compareValue != null && !compareValue.toString().equals(value)) {
                    return "expected '" + compareValue + "', got '" + value + "'";
                }
                return null;

            case NEQ:
                if (compareValue != null && compareValue.toString().equals(value)) {
                    return "value must not be '" + compareValue + "'";
                }
                return null;

            case GT:
            case GTE:
            case LT:
            case LTE:
                return evaluateNumericComparison(value);

            case BETWEEN:
                return evaluateBetween(value);

            case MATCHES:
                if (pattern != null && !value.matches(pattern)) {
                    return "value '" + value + "' does not match pattern '" + pattern + "'";
                }
                return null;

            default:
                return null;
        }
    }

    private String evaluateNumericComparison(String value) {
        Double actual = parseDouble(value);
        Double expected = compareValue != null ? parseDouble(compareValue.toString()) : null;
        if (actual == null) return "value '" + value + "' is not numeric";
        if (expected == null) return "comparison value is not numeric";

        switch (operator) {
            case GT:  if (!(actual > expected))  return "expected > " + expected + ", got " + actual; break;
            case GTE: if (!(actual >= expected)) return "expected >= " + expected + ", got " + actual; break;
            case LT:  if (!(actual < expected))  return "expected < " + expected + ", got " + actual; break;
            case LTE: if (!(actual <= expected)) return "expected <= " + expected + ", got " + actual; break;
            default: break;
        }
        return null;
    }

    private String evaluateBetween(String value) {
        Double actual = parseDouble(value);
        if (actual == null) return "value '" + value + "' is not numeric";
        if (rangeMin == null || rangeMax == null) return "between requires min and max";
        double min = rangeMin.doubleValue();
        double max = rangeMax.doubleValue();
        if (actual < min || actual > max) {
            return "expected between " + min + " and " + max + ", got " + actual;
        }
        return null;
    }

    private String formatMessage(ElementRecord element, String value, String reason) {
        if (message != null) {
            return message
                    .replace("{elementName}", element.name())
                    .replace("{value}", value != null ? value : "<null>")
                    .replace("{expected}", allowedValues != null ? allowedValues.toString() : "")
                    .replace("{reason}", reason);
        }
        return id + ": " + reason + " [element=" + element.name() + ", value=" + value + "]";
    }

    // ---- Param helpers ----

    private static Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return null; }
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

    private static boolean optBool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    private static int optInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private static Number optNumber(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return (Number) v;
        if (v != null) {
            try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static List<String> optStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object item : (List<?>) v) {
                if (item != null) out.add(item.toString());
            }
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
