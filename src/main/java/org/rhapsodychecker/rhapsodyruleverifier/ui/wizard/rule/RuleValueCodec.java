package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical conversion between human-readable rule editor values and the
 * serialized operators used by rule configuration.
 */
public final class RuleValueCodec {

    private RuleValueCodec() {}

    public static String nullable(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public static List<String> splitValues(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();

        List<String> values = new ArrayList<String>();
        for (String item : raw.split(",")) {
            String value = item.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    public static String joinValues(Iterable<?> values) {
        if (values == null) return "";
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            if (result.length() > 0) result.append(", ");
            if (value != null) result.append(value);
        }
        return result.toString();
    }

    public static boolean isNumericOperator(Object operator) {
        if (operator == null) return false;
        String value = operator.toString();
        return "gt".equals(value) || "gte".equals(value)
                || "lt".equals(value) || "lte".equals(value)
                || "neq".equals(value) || "between".equals(value);
    }

    public static String countModeToOperator(String mode) {
        if ("Exactly".equals(mode)) return "eq";
        if ("At most".equals(mode)) return "lte";
        if ("More than".equals(mode)) return "gt";
        if ("Fewer than".equals(mode)) return "lt";
        return "gte";
    }

    public static String operatorToCountMode(String operator) {
        if ("eq".equals(operator)) return "Exactly";
        if ("lte".equals(operator)) return "At most";
        if ("gt".equals(operator)) return "More than";
        if ("lt".equals(operator)) return "Fewer than";
        return "At least";
    }

    public static String numericComparisonToOperator(String comparison) {
        if (comparison == null) return "eq";
        if (comparison.startsWith("not equals")) return "neq";
        if (comparison.startsWith("greater than")) return "gt";
        if (comparison.startsWith("at least")) return "gte";
        if (comparison.startsWith("less than")) return "lt";
        if (comparison.startsWith("at most")) return "lte";
        if ("between".equals(comparison)) return "between";
        return "eq";
    }

    public static String operatorToNumericComparison(String operator) {
        if ("neq".equals(operator)) return "not equals (\u2260)";
        if ("gt".equals(operator)) return "greater than (>)";
        if ("gte".equals(operator)) return "at least (\u2265)";
        if ("lt".equals(operator)) return "less than (<)";
        if ("lte".equals(operator)) return "at most (\u2264)";
        if ("between".equals(operator)) return "between";
        return "equals (=)";
    }
}