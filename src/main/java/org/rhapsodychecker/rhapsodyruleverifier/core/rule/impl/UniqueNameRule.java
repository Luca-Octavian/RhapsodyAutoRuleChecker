package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Flags elements that share the same name under the same owner (sibling duplicates).
 * No parameters required — the rule checks all siblings by ownerGuid.
 */
public final class UniqueNameRule implements Rule {

    private String id;
    private String title;
    private String message;

    public UniqueNameRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            Optional<String> ownerOpt = element.ownerGuid();
            if (!ownerOpt.isPresent()) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            List<ElementRecord> siblings = context.findElementsByOwnerGuid(ownerOpt.get());
            int count = 0;
            for (ElementRecord sibling : siblings) {
                if (sibling.name().equals(element.name())) {
                    count++;
                }
            }

            if (count <= 1) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            String msg = message != null
                    ? message.replace("{elementName}", element.name())
                            .replace("{count}", String.valueOf(count))
                    : element.metaClass() + " '" + element.name()
                            + "' has " + count + " siblings with the same name under the same owner.";

            return DefaultRuleResult.builder()
                    .status(RuleStatus.FAIL)
                    .ruleId(id)
                    .elementGuid(element.guid())
                    .message(msg)
                    .detail("duplicateCount", count)
                    .detail("elementName", element.name())
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
}