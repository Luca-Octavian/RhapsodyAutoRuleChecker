package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixActionType;

import java.util.*;

/**
 * Generic rule: verifies that an element's name matches one simple pattern mode.
 * Exactly one of startsWith / endsWith / contains must be configured.
 * No regex — plain string matching only.
 *
 * <p>Targeting is done via appliesTo config (element sets or inline filters),
 * consistent with all other rules. The rule itself always applies to any
 * candidate element the selector provides.
 *
 * <p>Config params:
 * <ul>
 *   <li>startsWith — name must start with this value</li>
 *   <li>endsWith — name must end with this value</li>
 *   <li>contains — name must contain this value</li>
 *   <li>caseSensitive — boolean, default true</li>
 * </ul>
 */
public final class NamingPatternRule implements Rule {

    private String id;
    private String title;
    private String message;

    private String mode;      // "startsWith", "endsWith", or "contains"
    private String expected;  // the pattern value
    private boolean caseSensitive;

    public NamingPatternRule() {}

    @Override public String id() { return id; }
    @Override public String title() { return title != null ? title : id; }

    @Override
    public void configure(Map<String, Object> params) {
        this.id = requireString(params, "ruleId");
        this.title = optString(params, "ruleTitle");
        this.message = optString(params, "ruleMessage");

        Map<String, Object> p = optMap(params, "params");
        if (p == null) p = Collections.emptyMap();

        String startsWith = optString(p, "startsWith");
        String endsWith   = optString(p, "endsWith");
        String contains   = optString(p, "contains");

        // Validate: exactly one pattern mode
        int modeCount = 0;
        if (startsWith != null && !startsWith.isEmpty()) modeCount++;
        if (endsWith   != null && !endsWith.isEmpty())   modeCount++;
        if (contains   != null && !contains.isEmpty())   modeCount++;

        if (modeCount == 0) {
            throw new IllegalArgumentException("NamingPatternRule '" + id
                    + "': exactly one of startsWith, endsWith, contains must be specified");
        }
        if (modeCount > 1) {
            throw new IllegalArgumentException("NamingPatternRule '" + id
                    + "': only one of startsWith, endsWith, contains may be specified (got " + modeCount + ")");
        }

        if (startsWith != null && !startsWith.isEmpty()) {
            this.mode = "startsWith";
            this.expected = startsWith;
        } else if (endsWith != null && !endsWith.isEmpty()) {
            this.mode = "endsWith";
            this.expected = endsWith;
        } else {
            this.mode = "contains";
            this.expected = contains;
        }

        this.caseSensitive = optBool(p, "caseSensitive", true);
    }

    @Override
    public boolean appliesTo(ElementRecord element, EvaluationContext context) {
        // Selector already filtered candidates; always apply
        return true;
    }

    @Override
    public RuleResult evaluate(ElementRecord element, EvaluationContext context) {
        try {
            String actualName = element.name();
            String compareName = caseSensitive ? actualName : actualName.toLowerCase(Locale.ROOT);
            String compareExpected = caseSensitive ? expected : expected.toLowerCase(Locale.ROOT);

            boolean matches;
            switch (mode) {
                case "startsWith":
                    matches = compareName.startsWith(compareExpected);
                    break;
                case "endsWith":
                    matches = compareName.endsWith(compareExpected);
                    break;
                case "contains":
                    matches = compareName.contains(compareExpected);
                    break;
                default:
                    matches = false;
            }

            if (matches) {
                return DefaultRuleResult.pass(id, element.guid());
            }

            return DefaultRuleResult.builder()
                    .status(RuleStatus.FAIL)
                    .ruleId(id)
                    .elementGuid(element.guid())
                    .message(formatMessage(element))
                    .detail("actualName", actualName)
                    .detail("mode", mode)
                    .detail("expected", expected)
                    .build();

        } catch (Throwable t) {
            return DefaultRuleResult.skipped(id, element.guid(),
                    "Error evaluating rule: " + t.getMessage());
        }
    }

    private String formatMessage(ElementRecord element) {
        if (message != null) {
            return message
                    .replace("{elementName}", element.name())
                    .replace("{mode}", mode)
                    .replace("{expected}", expected);
        }

        String verb;
        switch (mode) {
            case "startsWith": verb = "start with"; break;
            case "endsWith":   verb = "end with";   break;
            case "contains":   verb = "contain";    break;
            default:           verb = mode;
        }
        return element.metaClass() + " name '" + element.name() + "' must " + verb
                + " '" + expected + "'"
                + (caseSensitive ? "" : " (case-insensitive)")
                + ".";
    }

    @Override
    public Optional<FixAction> suggestFix(ElementRecord element, EvaluationContext context) {
        String actualName = element.name();
        String newName;

        switch (mode) {
            case "startsWith":
                // Prepend the expected prefix if missing
                String checkName = caseSensitive ? actualName : actualName.toLowerCase(Locale.ROOT);
                String checkExpected = caseSensitive ? expected : expected.toLowerCase(Locale.ROOT);
                if (!checkName.startsWith(checkExpected)) {
                    newName = expected + actualName;
                } else {
                    return Optional.empty();
                }
                break;
            case "endsWith":
                // Append the expected suffix if missing
                checkName = caseSensitive ? actualName : actualName.toLowerCase(Locale.ROOT);
                checkExpected = caseSensitive ? expected : expected.toLowerCase(Locale.ROOT);
                if (!checkName.endsWith(checkExpected)) {
                    newName = actualName + expected;
                } else {
                    return Optional.empty();
                }
                break;
            default:
                // "contains" mode — ambiguous where to insert, skip
                return Optional.empty();
        }

        return Optional.of(FixAction.builder()
                .elementGuid(element.guid())
                .elementName(actualName)
                .actionType(FixActionType.SET_NAME)
                .field("name")
                .oldValue(actualName)
                .newValue(newName)
                .ruleId(id)
                .description("Rename '" + actualName + "' to '" + newName + "' to satisfy " + mode + " '" + expected + "'")
                .build());
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }
}