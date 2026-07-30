package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.*;

import java.util.*;
import java.util.function.Supplier;

/**
 * Creates configured Rule instances from RuleSpec (config).
 *
 * Uses a registry pattern: rule types are mapped to suppliers in a static
 * registry. New rule types can be added via {@link #register(RuleType, Supplier)}
 * without modifying this class (Open/Closed Principle).
 */
public final class RuleFactory {

    private static final Map<RuleType, Supplier<Rule>> REGISTRY = new LinkedHashMap<>();

    static {
        register(RuleType.REQUIRED_VALUE,              RequiredValueRule::new);
        register(RuleType.OWNER_STEREOTYPE_CONSTRAINT, OwnerStereotypeConstraintRule::new);
        register(RuleType.REQUIRED_STEREOTYPE,         RequiredStereotypeRule::new);
        register(RuleType.REQUIRED_STEREOTYPE_ONE_OF,  RequiredStereotypeOneOfRule::new);
        register(RuleType.RELATION_EXISTS,             RelationExistsRule::new);
    }

    private RuleFactory() {}

    /**
     * Register a rule supplier for a given type.
     * Can be called at startup to add custom/plugin rule types.
     *
     * @param type     the rule type enum value
     * @param supplier a factory that creates a fresh (unconfigured) Rule instance
     */
    public static void register(RuleType type, Supplier<Rule> supplier) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(supplier, "supplier");
        REGISTRY.put(type, supplier);
    }

    /**
     * Returns an unmodifiable view of the currently registered rule types.
     */
    public static Set<RuleType> registeredTypes() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * Create a Rule from a RuleSpec. The rule is configured with all
     * necessary params from the spec.
     *
     * @throws IllegalArgumentException if no supplier is registered for the spec's type
     */
    public static Rule createRule(RuleSpec spec) {
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