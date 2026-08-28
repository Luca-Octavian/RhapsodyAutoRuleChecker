package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixActionType;

import java.util.*;

/**
 * Generic rule: validates FlowProperty field constraints (type, initialValue, direction).
 * One rule type, configurable per constraint — not a separate class for each field.
 *
 * <p>Applies only to FLOW_PROPERTY elements. All constraints are optional individually,
 * but at least one must be configured.
 *
 * <p>Config params (all optional, at least one required):
 * <pre>
 * params:
 *   type:
 *     required: true
 *     allowed: [int, boolean, float]
 *   initialValue:
 *     required: false
 *     mustBeEmpty: true
 *   direction:
 *     required: true
 *     allowed: [In, Out, Bidirectional]
 *     caseSensitive: false
 * </pre>
 */
public final class FlowPropertyConstraintRule implements Rule {

    private String id;
    private String title;

    // Constraint blocks (null = not configured)
    private FieldConstraint typeConstraint;
    private FieldConstraint initialValueConstraint;
    private FieldConstraint directionConstraint;

    public FlowPropertyConstraintRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        this.typeConstraint = parseFieldConstraint(p, "type");
        this.initialValueConstraint = parseFieldConstraint(p, "initialValue");
        this.directionConstraint = parseFieldConstraint(p, "direction");

        if (typeConstraint == null && initialValueConstraint == null && directionConstraint == null) {
            throw new IllegalArgumentException("FlowPropertyConstraintRule '" + id
                    + "': at least one of type, initialValue, direction constraints must be configured");
        }
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return element.kind() == ElementKind.FLOW_PROPERTY;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            List<String> violations = new ArrayList<>();

            String actualType = element.typeName().orElse("");
            String actualInitialValue = element.initialValue().orElse("");
            String actualDirection = findTagIgnoreCase(element.tagValues(), "direction");

            // Type constraints
            if (typeConstraint != null) {
                if (typeConstraint.required && actualType.isEmpty()) {
                    violations.add("type is required but is missing");
                } else if (!actualType.isEmpty() && typeConstraint.allowed != null && !typeConstraint.allowed.isEmpty()) {
                    if (!containsValue(typeConstraint.allowed, actualType, typeConstraint.caseSensitive)) {
                        violations.add("type '" + actualType + "' is not one of " + typeConstraint.allowed);
                    }
                }
            }

            // Initial value constraints
            if (initialValueConstraint != null) {
                if (initialValueConstraint.mustBeEmpty && !actualInitialValue.isEmpty()) {
                    violations.add("initialValue must be empty but is '" + actualInitialValue + "'");
                } else if (initialValueConstraint.required && actualInitialValue.isEmpty()) {
                    violations.add("initialValue is required but is missing");
                } else if (!actualInitialValue.isEmpty()
                        && initialValueConstraint.allowed != null && !initialValueConstraint.allowed.isEmpty()) {
                    if (!containsValue(initialValueConstraint.allowed, actualInitialValue, initialValueConstraint.caseSensitive)) {
                        violations.add("initialValue '" + actualInitialValue + "' is not one of " + initialValueConstraint.allowed);
                    }
                }
            }

            // Direction constraints
            if (directionConstraint != null) {
                if (directionConstraint.required && actualDirection.isEmpty()) {
                    violations.add("direction is required but is missing");
                } else if (!actualDirection.isEmpty()
                        && directionConstraint.allowed != null && !directionConstraint.allowed.isEmpty()) {
                    if (!containsValue(directionConstraint.allowed, actualDirection, directionConstraint.caseSensitive)) {
                        violations.add("direction '" + actualDirection + "' is not one of " + directionConstraint.allowed);
                    }
                }
            }

            if (violations.isEmpty()) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            StringBuilder msg = new StringBuilder();
            msg.append("FlowProperty '").append(element.name()).append("' violates constraints:");
            for (String v : violations) {
                msg.append("\n- ").append(v);
            }

            return DefaultRuleResult.builder()
                    .status(RuleStatus.FAIL)
                    .ruleId(id)
                    .elementGuid(element.guid())
                    .message(msg.toString())
                    .detail("flowPropertyName", element.name())
                    .detail("ownerGuid", element.ownerGuid().orElse(""))
                    .detail("type", actualType)
                    .detail("initialValue", actualInitialValue)
                    .detail("direction", actualDirection)
                    .detail("violations", violations)
                    .build();

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    @Override
    public Optional<FixAction> suggestFix(ElementRecord element, EvaluationContext context) {
        // Only handle the simple case: initialValue must be empty but isn't
        if (initialValueConstraint != null && initialValueConstraint.mustBeEmpty) {
            String actualInitialValue = element.initialValue().orElse("");
            if (!actualInitialValue.isEmpty()) {
                return Optional.of(FixAction.builder()
                        .elementGuid(element.guid())
                        .elementName(element.name())
                        .actionType(FixActionType.SET_INITIAL_VALUE)
                        .field("initialValue")
                        .oldValue(actualInitialValue)
                        .newValue("")
                        .ruleId(id)
                        .description("Clear initialValue of FlowProperty '" + element.name()
                                + "' (was '" + actualInitialValue + "')")
                        .build());
            }
        }
        return Optional.empty();
    }

    // ---- Internal data structures ----

    private static final class FieldConstraint {
        final boolean required;
        final boolean mustBeEmpty;
        final List<String> allowed;
        final boolean caseSensitive;

        FieldConstraint(boolean required, boolean mustBeEmpty, List<String> allowed, boolean caseSensitive) {
            this.required = required;
            this.mustBeEmpty = mustBeEmpty;
            this.allowed = allowed;
            this.caseSensitive = caseSensitive;
        }
    }

    // ---- Helpers ----

    /**
     * Case-insensitive tag key lookup, since profile capitalization may vary.
     */
    private static String findTagIgnoreCase(Map<String, String> tags, String wanted) {
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (wanted.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? "" : entry.getValue().trim();
            }
        }
        return "";
    }

    private static boolean containsValue(List<String> allowed, String actual, boolean caseSensitive) {
        for (String a : allowed) {
            if (caseSensitive) {
                if (a.equals(actual)) return true;
            } else {
                if (a.equalsIgnoreCase(actual)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private FieldConstraint parseFieldConstraint(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) return null;
        if (!(raw instanceof Map)) return null;

        Map<String, Object> m = (Map<String, Object>) raw;
        boolean required = optBool(m, "required", false);
        boolean mustBeEmpty = optBool(m, "mustBeEmpty", false);
        List<String> allowed = optStringList(m, "allowed");
        boolean caseSensitive = optBool(m, "caseSensitive", true);

        return new FieldConstraint(required, mustBeEmpty, allowed, caseSensitive);
    }

    // ---- Param helpers ----

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