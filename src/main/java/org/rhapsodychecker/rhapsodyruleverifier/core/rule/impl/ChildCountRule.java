package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Checks that an element has at least N children of a specific ElementKind.
 *
 * <p>Config params:
 * <ul>
 *   <li>child_kind — which ElementKind children to count (e.g. PORT_FLOW)</li>
 *   <li>min_count — minimum required (default 1)</li>
 * </ul>
 */
public final class ChildCountRule implements Rule {

    private String id;
    private String title;
    private String message;
    private ElementKind childKind;
    private int minCount;

    public ChildCountRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        String kindStr = requireString(p, "child_kind");
        try {
            this.childKind = ElementKind.valueOf(kindStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ChildCountRule '" + id
                    + "': unknown child_kind '" + kindStr + "'. Use one of: "
                    + Arrays.toString(ElementKind.values()));
        }

        this.minCount = optInt(p, "min_count", 1);
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            List<ElementRecord> children = context.findElementsByOwnerGuid(element.guid());
            int count = 0;
            for (ElementRecord child : children) {
                if (child.kind() == childKind) {
                    count++;
                }
            }

            if (count >= minCount) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            String msg = message != null
                    ? message.replace("{elementName}", element.name())
                            .replace("{count}", String.valueOf(count))
                            .replace("{minCount}", String.valueOf(minCount))
                            .replace("{childKind}", childKind.name())
                    : element.metaClass() + " '" + element.name()
                            + "' has " + count + " " + childKind.name()
                            + " children (minimum: " + minCount + ").";

            return DefaultRuleResult.builder()
                    .status(RuleStatus.FAIL)
                    .ruleId(id)
                    .elementGuid(element.guid())
                    .message(msg)
                    .detail("childKind", childKind.name())
                    .detail("actualCount", count)
                    .detail("minCount", minCount)
                    .build();

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
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