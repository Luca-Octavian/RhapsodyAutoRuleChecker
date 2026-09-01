package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.*;
import java.util.function.BiFunction;

public final class RuleParamPanelFactory {
    private RuleParamPanelFactory() {}

    private static final Map<RuleType, BiFunction<List<String>, Runnable, RuleParamPanel>> REGISTRY
            = new EnumMap<>(RuleType.class);

    static {
        REGISTRY.put(RuleType.REQUIRED_VALUE, RequiredValuePanel::new);
        REGISTRY.put(RuleType.REQUIRED_STEREOTYPE, RequiredStereotypePanel::new);
        REGISTRY.put(RuleType.REQUIRED_STEREOTYPE_ONE_OF, RequiredStereotypeOneOfPanel::new);
        REGISTRY.put(RuleType.NAMING_PATTERN, NamingPatternPanel::new);
        REGISTRY.put(RuleType.RELATION_EXISTS, RelationExistsPanel::new);
        REGISTRY.put(RuleType.OWNER_STEREOTYPE_CONSTRAINT, OwnerStereotypeConstraintPanel::new);
        REGISTRY.put(RuleType.FLOW_PROPERTY_CONSTRAINT, FlowPropertyConstraintPanel::new);
        REGISTRY.put(RuleType.UNIQUE_NAME, UniqueNamePanel::new);
        REGISTRY.put(RuleType.CHILD_COUNT, ChildCountPanel::new);
        REGISTRY.put(RuleType.MAX_DEPTH, MaxDepthPanel::new);

        for (RuleType t : RuleType.values()) {
            if (!REGISTRY.containsKey(t)) {
                throw new ExceptionInInitializerError("No RuleParamPanel registered for " + t);
            }
        }
    }

    public static RuleParamPanel create(RuleType type, List<String> detectedStereotypes, Runnable onChange) {
        BiFunction<List<String>, Runnable, RuleParamPanel> ctor = REGISTRY.get(type);
        if (ctor == null) throw new IllegalArgumentException("Unknown RuleType: " + type);
        return ctor.apply(detectedStereotypes, onChange);
    }
}