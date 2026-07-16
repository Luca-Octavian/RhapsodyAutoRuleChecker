package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

/**
 * Generic rule: if the element's OWNER has a given stereotype, then the
 * element itself must be one of the allowed kinds.
 *
 * If the owner does NOT have the given stereotype (or has no owner at all),
 * the rule simply does not apply to this element — no result is recorded.
 *
 * Config-driven via params: ownerStereotype (string), allowedKinds (list of ElementKind names).
 */
public final class OwnerStereotypeConstraintRule implements Rule {

    private String id;
    private String title;
    private String message;
    private String ownerStereotype;
    private Set<ElementKind> allowedKinds;

    public OwnerStereotypeConstraintRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        this.ownerStereotype = requireString(p, "ownerStereotype");

        List<String> kindNames = optStringList(p, "allowedKinds");
        if (kindNames == null || kindNames.isEmpty()) {
            throw new IllegalArgumentException("OwnerStereotypeConstraintRule '" + id
                    + "': params.allowedKinds must be a non-empty list");
        }
        this.allowedKinds = new HashSet<>();
        for (String k : kindNames) {
            try {
                allowedKinds.add(ElementKind.valueOf(k.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("OwnerStereotypeConstraintRule '" + id
                        + "': unknown ElementKind '" + k + "' in allowedKinds");
            }
        }
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        Optional<String> ownerGuid = element.ownerGuid();
        if (!ownerGuid.isPresent()) return false;

        Optional<ElementRecord> owner = context.findElementByGuid(ownerGuid.get());
        if (!owner.isPresent()) return false;

        return owner.get().hasStereotypeIgnoreCase(ownerStereotype);
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            if (allowedKinds.contains(element.kind())) {
                return DefaultRuleResult.pass(id, element.guid());
            }
            return DefaultRuleResult.fail(id, element.guid(),
                    formatMessage(element));
        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    private String formatMessage(ElementRecord element) {
        if (message != null) {
            return message
                    .replace("{elementName}", element.name())
                    .replace("{ownerStereotype}", ownerStereotype)
                    .replace("{allowedKinds}", allowedKinds.toString())
                    .replace("{actualKind}", element.kind().toString());
        }
        return id + ": owner has stereotype '" + ownerStereotype
                + "' but element kind is " + element.kind()
                + ", expected one of " + allowedKinds
                + " [element=" + element.name() + "]";
    }

    // ---- Param helpers (identice cu celelalte reguli) ----

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