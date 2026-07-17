// config/generate/ConfigBuilder.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ConfigMode;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.*;

/**
 * Convertește WizardState → RuleCheckerConfig gata de injectat în RuleEngine.
 *
 * Nu scrie YAML — produce direct obiectul de config in-memory.
 * Dacă utilizatorul vrea să exporte/salveze configurația, YamlPresetWriter
 * se ocupă separat de serializare.
 *
 * Validarea cross-reference (alias există, set există) este făcută de
 * RuleCheckerConfig.validate() la build time — deci nu duplicăm logica aici.
 */
public final class ConfigBuilder {

    private ConfigBuilder() {}

    public static RuleCheckerConfig build(WizardState state) {
        Map<String, AliasDefinition>      aliasMap = buildAliasMap(state.aliases());
        Map<String, ElementSetDefinition> setMap   = buildSetMap(state.sets());
        List<RuleSpec>                    specs    = buildRuleSpecs(state.rules());

        return RuleCheckerConfig.builder()
                .schemaVersion(1)
                .mode(parseMode(state.mode()))
                .aliases(aliasMap)
                .elementSets(setMap)
                .rules(specs)
                .build();
    }

    // ---------------------------------------------------------------

    private static Map<String, AliasDefinition> buildAliasMap(List<AliasDefinition> aliases) {
        Map<String, AliasDefinition> map = new LinkedHashMap<>();
        aliases.forEach(a -> map.put(a.id(), a));
        return map;
    }

    private static Map<String, ElementSetDefinition> buildSetMap(List<ElementSetDefinition> sets) {
        Map<String, ElementSetDefinition> map = new LinkedHashMap<>();
        sets.forEach(s -> map.put(s.id(), s));
        return map;
    }

    private static List<RuleSpec> buildRuleSpecs(List<WizardState.RuleRequest> requests) {
        List<RuleSpec> specs = new ArrayList<>();
        for (WizardState.RuleRequest req : requests) {
            specs.add(
                    RuleSpec.builder()
                            .id(req.id())
                            .title(req.title())
                            .type(parseRuleType(req.ruleType()))
                            .target(req.targetAliasId())
                            .appliesToSet(req.elementSetId())
                            .params(req.params())
                            .message(req.message())
                            .enabled(true)
                            .build()
            );
        }
        return specs;
    }

    private static ConfigMode parseMode(String raw) {
        if (raw == null) return ConfigMode.LENIENT;
        switch (raw.toUpperCase(Locale.ROOT)) {
            case "STRICT":  return ConfigMode.STRICT;
            case "LENIENT": return ConfigMode.LENIENT;
            default:
                throw new IllegalArgumentException("Unknown config mode: " + raw);
        }
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
            default:
                throw new IllegalArgumentException("Unknown rule type from wizard: " + raw);
        }
    }
}
