// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyAliasResolver.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.DefaultResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.ResolvedValue;

import java.util.*;

public final class RhapsodyAliasResolver implements AliasResolver {

    private final RuleCheckerConfig config;

    public RhapsodyAliasResolver(RuleCheckerConfig config, RhapsodyModelSnapshot snapshot) {
        this.config = Objects.requireNonNull(config, "config");
        // snapshot kept in signature for API compatibility but no longer used during evaluation
    }

    @Override
    public ResolvedValue resolveValue(ElementRecord element, String aliasId) {
        if ("description".equalsIgnoreCase(aliasId)) {
            return resolveDescription(element);
        }
        if ("name".equalsIgnoreCase(aliasId)) {
            return DefaultResolvedValue.of(element.name(), "name");
        }

        Optional<AliasDefinition> optAlias = config.alias(aliasId);
        if (!optAlias.isPresent()) {
            return DefaultResolvedValue.absent();
        }

        AliasDefinition alias = optAlias.get();

        switch (alias.kind()) {
            case DESCRIPTION:
                return resolveDescription(element);
            case NAME:
                return DefaultResolvedValue.of(element.name(), "name");
            case TAGGED_VALUE:
                return resolveTaggedValue(element, alias);
            case STEREOTYPE:
                return resolveStereotypePresence(element, alias);
            case STEREOTYPE_SET:
                return resolveStereotypeSet(element, alias);
            case PORT_TYPE:
                return resolvePortType(element);
            case PORT_DIRECTION:
                return resolvePortDirection(element);
            case PORT_MULTIPLICITY:
                return resolvePortMultiplicity(element);
            default:
                return DefaultResolvedValue.absent();
        }
    }

    private ResolvedValue resolveDescription(ElementRecord element) {
        String desc = element.description().orElse(null);
        if (desc != null && !desc.trim().isEmpty()) {
            return DefaultResolvedValue.of(desc.trim(), "description");
        }
        return DefaultResolvedValue.absent();
    }

    private ResolvedValue resolveTaggedValue(ElementRecord element, AliasDefinition alias) {
        String tagName = alias.tagName().orElse(null);
        if (tagName == null) return DefaultResolvedValue.absent();

        // Fast path: pre-loaded tags on ElementRecord (works for both live and cached mode)
        String preloaded = element.tagValues().get(tagName);
        if (preloaded == null) {
            // Try case-insensitive match
            for (Map.Entry<String, String> entry : element.tagValues().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(tagName)) {
                    preloaded = entry.getValue();
                    break;
                }
            }
        }
        if (preloaded != null && !preloaded.trim().isEmpty()) {
            return DefaultResolvedValue.of(preloaded.trim(), "preloaded:tag:" + tagName);
        }

        // Stereotype fallback for ASIL-style tags (stored as stereotypes like ASIL_A)
        if (!alias.values().isEmpty()) {
            for (String allowed : alias.values()) {
                if (element.hasStereotypeIgnoreCase(allowed)
                        || element.hasStereotypeIgnoreCase(tagName + "_" + allowed)) {
                    return DefaultResolvedValue.of(allowed, "stereotype-fallback:" + allowed);
                }
            }
        }

        return DefaultResolvedValue.absent();
    }

    private ResolvedValue resolveStereotypePresence(ElementRecord element, AliasDefinition alias) {
        String stereoName = alias.stereotypeName().orElse(null);
        if (stereoName == null) return DefaultResolvedValue.absent();

        boolean has = element.hasStereotypeIgnoreCase(stereoName);
        return DefaultResolvedValue.of(has ? "true" : "false", "stereotype:" + stereoName);
    }

    private ResolvedValue resolveStereotypeSet(ElementRecord element, AliasDefinition alias) {
        List<String> matched = new ArrayList<>();
        for (String candidate : alias.stereotypeNames()) {
            if (element.hasStereotypeIgnoreCase(candidate)) {
                matched.add(candidate);
            }
        }
        if (!matched.isEmpty()) {
            return DefaultResolvedValue.of(matched.get(0), "stereotypeSet:" + matched.get(0));
        }
        return DefaultResolvedValue.absent();
    }

    private ResolvedValue resolvePortType(ElementRecord element) {
        if (!element.kind().isPortKind()) {
            return DefaultResolvedValue.absent();
        }
        // All port type info is pre-loaded on ElementRecord during model loading
        String typeName = element.typeName().orElse(null);
        if (typeName != null && !typeName.trim().isEmpty()) {
            return DefaultResolvedValue.of(typeName.trim(), "portType:elementRecord");
        }

        return DefaultResolvedValue.absent();
    }


    private ResolvedValue resolvePortDirection(ElementRecord element) {
        if (!element.kind().isPortKind()) return DefaultResolvedValue.absent();

        // All port direction info is pre-loaded on ElementRecord during model loading
        String dir = element.portDirection().orElse(null);
        if (dir != null && !dir.trim().isEmpty()) {
            String mapped = mapDirectionString(dir);
            if (!"UNKNOWN".equals(mapped) && !"NONE".equals(mapped)) {
                return DefaultResolvedValue.of(mapped, "preloaded:portDirection");
            }
        }

        return DefaultResolvedValue.absent();
    }

    private ResolvedValue resolvePortMultiplicity(ElementRecord element) {
        if (!element.kind().isPortKind()) return DefaultResolvedValue.absent();

        // All port multiplicity info is pre-loaded on ElementRecord during model loading
        String mult = element.portMultiplicity().orElse(null);
        if (mult != null && !mult.trim().isEmpty()) {
            return DefaultResolvedValue.of(mult.trim(), "preloaded:portMultiplicity");
        }

        return DefaultResolvedValue.absent();
    }

    private String mapDirectionString(String raw) {
        if (raw == null) return "UNKNOWN";
        String s = raw.trim().toLowerCase();
        switch (s) {
            case "in": case "input": return "IN";
            case "out": case "output": return "OUT";
            case "inout": case "in/out": case "in-out": return "INOUT";
            case "none": return "NONE";
            default: return "UNKNOWN";
        }
    }

}
