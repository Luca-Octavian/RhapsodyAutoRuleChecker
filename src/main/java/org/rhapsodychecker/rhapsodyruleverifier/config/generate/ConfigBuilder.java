// config/generate/ConfigBuilder.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.*;

/**
 * Converts WizardState into a RuleCheckerConfig ready for injection into RuleEngine.
 *
 * Does not write YAML — produces the config object in-memory.
 * If the user wants to export/save the configuration, YamlPresetWriter
 * handles serialization separately.
 *
 * Cross-reference validation (set exists) is done by
 * RuleCheckerConfig.validate() at build time — so we don't duplicate logic here.
 */
public final class ConfigBuilder {

    private ConfigBuilder() {}

    public static RuleCheckerConfig build(WizardState state) {
        Map<String, ElementSetDefinition> setMap   = buildSetMap(state.sets());
        List<RuleSpec>                    specs    = buildRuleSpecs(state.rules());

        return RuleCheckerConfig.builder()
                .schemaVersion(1)
                .elementSets(setMap)
                .rules(specs)
                .build();
    }

    // ---------------------------------------------------------------

    private static Map<String, ElementSetDefinition> buildSetMap(List<ElementSetDefinition> sets) {
        Map<String, ElementSetDefinition> map = new LinkedHashMap<>();
        for (ElementSetDefinition s : sets) {
            map.put(s.id(), s);
        }
        return map;
    }

    private static List<RuleSpec> buildRuleSpecs(List<WizardState.RuleRequest> requests) {
        List<RuleSpec> specs = new ArrayList<>();
        for (WizardState.RuleRequest req : requests) {
            RuleType type = parseRuleType(req.ruleType());

            specs.add(
                    RuleSpec.builder()
                            .id(req.id())
                            .title(req.title())
                            .type(type)
                            .target(req.targetSpec())
                            .appliesToSet(req.elementSetId())
                            .params(req.params())
                            .message(req.message())
                            .group(req.group())
                            .enabled(true)
                            .build()
            );
        }
        return specs;
    }

    private static RuleType parseRuleType(String raw) {
        if (raw == null) throw new IllegalArgumentException("ruleType must not be null");
        switch (raw.toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_")) {
            case "REQUIRED_VALUE":
            case "REQUIREDVALUE":             return RuleType.REQUIRED_VALUE;
            case "REQUIRED_STEREOTYPE":
            case "REQUIREDSTEREOTYPE":        return RuleType.REQUIRED_STEREOTYPE;
            case "REQUIRED_STEREOTYPE_ONE_OF":
            case "REQUIREDSTEREOTYPEONEOF":   return RuleType.REQUIRED_STEREOTYPE_ONE_OF;
            case "RELATION_EXISTS":
            case "RELATIONEXISTS":            return RuleType.RELATION_EXISTS;
            case "NAMING_PATTERN":
            case "NAMINGPATTERN":             return RuleType.NAMING_PATTERN;
            case "OWNER_STEREOTYPE_CONSTRAINT":
            case "OWNERSTEREOTYPECONSTRAINT":  return RuleType.OWNER_STEREOTYPE_CONSTRAINT;
            case "FLOW_PROPERTY_CONSTRAINT":
            case "FLOWPROPERTYCONSTRAINT":     return RuleType.FLOW_PROPERTY_CONSTRAINT;
            default:
                throw new IllegalArgumentException("Unknown rule type from wizard: " + raw);
        }
    }
}