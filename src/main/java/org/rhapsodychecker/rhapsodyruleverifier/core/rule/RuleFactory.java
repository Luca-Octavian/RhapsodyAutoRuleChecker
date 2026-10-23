package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.*;

import java.util.*;
import java.util.function.Supplier;

/**
 * Creates configured Rule instances from RuleSpec (config).
 *
 * Uses a registry pattern: rule types are mapped to suppliers in an immutable
 * static registry built at class-load time.
 */
public final class RuleFactory {

    private static final Map<RuleType, Supplier<Rule>> REGISTRY;

    static {
        Map<RuleType, Supplier<Rule>> m = new LinkedHashMap<>();
        m.put(RuleType.REQUIRED_VALUE,              RequiredValueRule::new);
        m.put(RuleType.OWNER_STEREOTYPE_CONSTRAINT, OwnerStereotypeConstraintRule::new);
        m.put(RuleType.REQUIRED_STEREOTYPE,         RequiredStereotypeRule::new);
        m.put(RuleType.REQUIRED_STEREOTYPE_ONE_OF,  RequiredStereotypeOneOfRule::new);
        m.put(RuleType.RELATION_EXISTS,             RelationExistsRule::new);
        m.put(RuleType.NAMING_PATTERN,              NamingPatternRule::new);
        m.put(RuleType.FLOW_PROPERTY_CONSTRAINT,    FlowPropertyConstraintRule::new);
        m.put(RuleType.UNIQUE_NAME,                 UniqueNameRule::new);
        m.put(RuleType.CHILD_COUNT,                 ChildCountRule::new);
        m.put(RuleType.MAX_DEPTH,                   MaxDepthRule::new);
        REGISTRY = Collections.unmodifiableMap(m);
    }

    private RuleFactory() {}

    /**
     * Returns an unmodifiable view of the registered rule types.
     */
    public static Set<RuleType> registeredTypes() {
        return REGISTRY.keySet();
    }

    /**
     * Create a Rule from a RuleSpec. The rule is configured with all
     * necessary params from the spec.
     *
     * @throws IllegalArgumentException if no supplier is registered for the spec's type
     */
    public static synchronized Rule createRule(RuleSpec spec) {
        Supplier<Rule> supplier = REGISTRY.get(spec.type());
        if (supplier == null) {
            throw new IllegalArgumentException(
                    "No rule registered for type: " + spec.type()
                    + ". Registered types: " + REGISTRY.keySet());
        }
        Rule rule = supplier.get();

        // Build the configure map: merge spec-level fields + params
        Map<String, Object> configMap = new LinkedHashMap<>();
        configMap.put("ruleId", spec.id());
        spec.title().ifPresent(t -> configMap.put("ruleTitle", t));
        spec.message().ifPresent(m -> configMap.put("ruleMessage", m));
        spec.target().ifPresent(t -> configMap.put("target", t));  // TargetSpec object
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