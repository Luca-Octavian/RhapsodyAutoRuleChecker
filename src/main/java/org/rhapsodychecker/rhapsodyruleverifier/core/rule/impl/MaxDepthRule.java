package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Flags elements whose package nesting depth exceeds a threshold.
 * Depth is measured by counting {@code ::} separators in the element's ownerPath.
 *
 * <p>Config params:
 * <ul>
 *   <li>max_depth — maximum allowed :: separators (default 5)</li>
 * </ul>
 */
public final class MaxDepthRule implements Rule {

    private String id;
    private String title;
    private String message;
    private int maxDepth;

    public MaxDepthRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        this.maxDepth = optInt(p, "max_depth", 5);
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            String ownerPath = element.ownerPath().orElse("");
            int depth = countSeparators(ownerPath);

            if (depth <= maxDepth) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            String msg = message != null
                    ? message.replace("{elementName}", element.name())
                            .replace("{depth}", String.valueOf(depth))
                            .replace("{maxDepth}", String.valueOf(maxDepth))
                    : element.metaClass() + " '" + element.name()
                            + "' is at depth " + depth
                            + " (maximum allowed: " + maxDepth + ")."
                            + " Path: " + ownerPath;

            return DefaultRuleResult.builder()
                    .status(RuleStatus.FAIL)
                    .ruleId(id)
                    .elementGuid(element.guid())
                    .message(msg)
                    .detail("depth", depth)
                    .detail("maxDepth", maxDepth)
                    .detail("ownerPath", ownerPath)
                    .build();

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    /**
     * Count occurrences of "::" in the path.
     */
    static int countSeparators(String path) {
        if (path == null || path.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = path.indexOf("::", idx)) != -1) {
            count++;
            idx += 2;
        }
        return count;
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

    private static int optInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(v.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }
}