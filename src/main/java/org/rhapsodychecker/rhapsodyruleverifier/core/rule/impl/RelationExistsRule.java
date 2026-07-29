// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/rule/impl/RelationExistsRule.java
package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.ComparisonOperator;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationDirection;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RelationKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Generic rule: verifies that an element has a certain number of relations
 * matching filters (kind, direction, stereotypes, target set).
 * The actual relation reading is delegated to EvaluationContext.
 *
 * Config-driven via params:
 *   relationKind, direction, relationStereotypes, targetSet,
 *   targetTypes, targetStereotypes, targetPackages,
 *   operator, value
 */
public final class RelationExistsRule implements Rule {

    private String id;
    private String title;
    private String message;

    // Relation filters
    private RelationKind relationKind;
    private RelationDirection direction;
    private List<String> relationStereotypes;

    // Target filters
    private String targetSet;
    private List<String> targetTypes;
    private List<String> targetStereotypes;
    private List<String> targetPackages;

    // Count check
    private ComparisonOperator operator;
    private int expectedCount;

    public RelationExistsRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        String rkStr = optString(p, "relationKind");
        this.relationKind = rkStr != null ? RelationKind.fromString(rkStr) : RelationKind.ANY;

        String dirStr = optString(p, "direction");
        this.direction = dirStr != null ? RelationDirection.fromString(dirStr) : RelationDirection.ANY;

        this.relationStereotypes = optStringList(p, "relationStereotypes");
        this.targetSet = optString(p, "targetSet");
        this.targetTypes = optStringList(p, "targetTypes");
        this.targetStereotypes = optStringList(p, "targetStereotypes");
        this.targetPackages = optStringList(p, "targetPackages");

        String opStr = optString(p, "operator");
        this.operator = opStr != null ? ComparisonOperator.fromString(opStr) : ComparisonOperator.GTE;
        this.expectedCount = optInt(p, "value", 1);
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            // Build a relation query from our config
            RelationQuery query = new RelationQuery(
                    relationKind, direction, relationStereotypes,
                    targetSet, targetTypes, targetStereotypes, targetPackages
            );

            // Delegate to context to count matching relations
            int count = context.countMatchingRelations(element, query);

            boolean pass = evaluateCount(count);

            if (pass) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            return DefaultRuleResult.fail(id, element.guid(),
                    formatMessage(element, count));

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    private boolean evaluateCount(int actual) {
        switch (operator) {
            case EQ:  return actual == expectedCount;
            case NEQ: return actual != expectedCount;
            case GT:  return actual > expectedCount;
            case GTE: return actual >= expectedCount;
            case LT:  return actual < expectedCount;
            case LTE: return actual <= expectedCount;
            default:  return actual >= expectedCount;
        }
    }

    private String formatMessage(ElementRecord element, int count) {
        if (message != null) {
            return message
                    .replace("{elementName}", element.name())
                    .replace("{count}", String.valueOf(count))
                    .replace("{expected}", String.valueOf(expectedCount));
        }
        return id + ": expected " + operator + " " + expectedCount
                + " matching relations, got " + count + " [element=" + element.name() + "]";
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

    private static int optInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
        }
        return def;
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

    /**
     * Data holder for relation query parameters, passed to EvaluationContext.
     */
    public static final class RelationQuery {
        private final RelationKind kind;
        private final RelationDirection direction;
        private final List<String> relationStereotypes;
        private final String targetSet;
        private final List<String> targetTypes;
        private final List<String> targetStereotypes;
        private final List<String> targetPackages;

        public RelationQuery(RelationKind kind, RelationDirection direction,
                             List<String> relationStereotypes,
                             String targetSet, List<String> targetTypes,
                             List<String> targetStereotypes, List<String> targetPackages) {
            this.kind = kind;
            this.direction = direction;
            this.relationStereotypes = relationStereotypes;
            this.targetSet = targetSet;
            this.targetTypes = targetTypes;
            this.targetStereotypes = targetStereotypes;
            this.targetPackages = targetPackages;
        }

        public RelationKind kind() { return kind; }
        public RelationDirection direction() { return direction; }
        public List<String> relationStereotypes() { return relationStereotypes; }
        public String targetSet() { return targetSet; }
        public List<String> targetTypes() { return targetTypes; }
        public List<String> targetStereotypes() { return targetStereotypes; }
        public List<String> targetPackages() { return targetPackages; }
    }
}
