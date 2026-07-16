
package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.*;

import java.util.*;

/**
 * Creates configured Rule instances from RuleSpec (config).
 */
public final class RuleFactory {

    private RuleFactory() {}

    /**
     * Create a Rule from a RuleSpec. The rule is configured with all
     * necessary params from the spec.
     */
    public static Rule createRule(RuleSpec spec) {
        Rule rule;
        switch (spec.type()) {
            case REQUIRED_VALUE:
                rule = new RequiredValueRule();
                break;
            case OWNER_STEREOTYPE_CONSTRAINT:
                rule = new OwnerStereotypeConstraintRule();
                break;
            case REQUIRED_STEREOTYPE:
                rule = new RequiredStereotypeRule();
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                rule = new RequiredStereotypeOneOfRule();
                break;
            case RELATION_EXISTS:
                rule = new RelationExistsRule();
                break;
            case NAMING_PATTERN:
                throw new UnsupportedOperationException("NamingPattern not implemented");
            default:
                throw new IllegalArgumentException("Unknown rule type: " + spec.type());
        }

        // Build the configure map: merge spec-level fields + params
        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("ruleId", spec.id());
        spec.title().ifPresent(t -> configMap.put("ruleTitle", t));
        spec.message().ifPresent(m -> configMap.put("ruleMessage", m));
        spec.target().ifPresent(t -> configMap.put("target", t));
        configMap.put("params", spec.params());

        rule.configure(configMap);
        return rule;
    }

    /**
     * Create all enabled rules from config.
     */
    public static List<Rule> createRules(List<RuleSpec> specs) {
        List<Rule> rules = new ArrayList<>();
        for (RuleSpec spec : specs) {
            if (spec.enabled()) {
                rules.add(createRule(spec));
            }
        }
        return Collections.unmodifiableList(rules);
    }
}
