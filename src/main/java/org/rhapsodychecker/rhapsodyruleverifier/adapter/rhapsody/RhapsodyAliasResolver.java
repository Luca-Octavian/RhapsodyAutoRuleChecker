// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyAliasResolver.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.DefaultResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.PortInfo;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.ResolvedValue;

import java.lang.reflect.Method;
import java.util.*;

public final class RhapsodyAliasResolver implements AliasResolver {

    private final RuleCheckerConfig config;
    private final RhapsodyModelSnapshot snapshot;
    private final RhapsodyPortInfoResolver portInfoResolver;

    public RhapsodyAliasResolver(RuleCheckerConfig config, RhapsodyModelSnapshot snapshot) {
        this.config = Objects.requireNonNull(config, "config");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.portInfoResolver = new RhapsodyPortInfoResolver(snapshot);
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
        IRPModelElement handle = snapshot.handleByGuid().get(element.guid());
        if (handle == null) return DefaultResolvedValue.absent();

        String tagName = alias.tagName().orElse(null);
        if (tagName == null) return DefaultResolvedValue.absent();

        String value = tryReadTag(handle, tagName);
        if (value != null && !value.trim().isEmpty()) {
            return DefaultResolvedValue.of(value.trim(), "taggedValue:" + tagName);
        }

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
        // Check ElementRecord first
        String typeName = element.typeName().orElse(null);
        if (typeName != null && !typeName.trim().isEmpty()) {
            return DefaultResolvedValue.of(typeName.trim(), "portType:elementRecord");
        }

        // Fallback: try via Rhapsody API
        IRPModelElement handle = snapshot.handleByGuid().get(element.guid());
        if (handle == null) return DefaultResolvedValue.absent();

        // Try getType() first (works for SysMLPort / FlowPort)
        try {
            java.lang.reflect.Method getType = handle.getClass().getMethod("getType");
            Object cls = getType.invoke(handle);
            if (cls instanceof IRPModelElement) {
                String name = ((IRPModelElement) cls).getName();
                if (name != null && !name.trim().isEmpty()) {
                    return DefaultResolvedValue.of(name.trim(), "portType:getType");
                }
            }
        } catch (Throwable t) { /* ignore */ }

        // Try getOtherClass() (works for standard Port)
        if (handle instanceof IRPPort) {
            try {
                IRPClassifier cls = ((IRPPort) handle).getOtherClass();
                if (cls != null) {
                    String name = cls.getName();
                    if (name != null && !name.trim().isEmpty()) {
                        return DefaultResolvedValue.of(name.trim(), "portType:getOtherClass");
                    }
                }
            } catch (Throwable t) { /* ignore */ }
        }

        return DefaultResolvedValue.absent();
    }


    private ResolvedValue resolvePortDirection(ElementRecord element) {
        if (!element.kind().isPortKind()) return DefaultResolvedValue.absent();

        // Fast path: pre-loaded on ElementRecord during model loading
        String dir = element.portDirection().orElse(null);
        if (dir != null && !dir.trim().isEmpty()) {
            String mapped = mapDirectionString(dir);
            if (!"UNKNOWN".equals(mapped) && !"NONE".equals(mapped)) {
                return DefaultResolvedValue.of(mapped, "preloaded:portDirection");
            }
        }

        // Slow fallback: only if not pre-loaded
        PortInfo info = portInfoResolver.resolve(element);
        String dirName = info.direction().name();
        if ("UNKNOWN".equals(dirName) || "NONE".equals(dirName)) {
            return DefaultResolvedValue.absent();
        }
        return DefaultResolvedValue.of(dirName, info.directionSource());
    }

    private ResolvedValue resolvePortMultiplicity(ElementRecord element) {
        if (!element.kind().isPortKind()) return DefaultResolvedValue.absent();

        // Fast path: pre-loaded on ElementRecord during model loading
        String mult = element.portMultiplicity().orElse(null);
        if (mult != null && !mult.trim().isEmpty()) {
            return DefaultResolvedValue.of(mult.trim(), "preloaded:portMultiplicity");
        }

        // Slow fallback
        PortInfo info = portInfoResolver.resolve(element);
        String multStr = info.multiplicity().toString();
        if (multStr.contains("?")) return DefaultResolvedValue.absent();
        return DefaultResolvedValue.of(multStr, info.multiplicitySource());
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

    // ---- Rhapsody tag reading ----

    private String tryReadTag(IRPModelElement elt, String tagName) {
        try {
            IRPTag tag = elt.getTag(tagName);
            if (tag != null) {
                String val = tag.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        } catch (Throwable t) { /* ignore */ }

        try {
            Method getTags = elt.getClass().getMethod("getTags");
            Object tagsObj = getTags.invoke(elt);
            if (tagsObj instanceof IRPCollection) {
                IRPCollection tags = (IRPCollection) tagsObj;
                for (int i = 1; i <= tags.getCount(); i++) {
                    Object item = tags.getItem(i);
                    if (item instanceof IRPTag) {
                        IRPTag tag = (IRPTag) item;
                        if (tagName.equalsIgnoreCase(tag.getName())) {
                            String val = tag.getValue();
                            if (val != null && !val.trim().isEmpty()) {
                                return val.trim();
                            }
                        }
                    } else if (item instanceof IRPModelElement) {
                        IRPModelElement tagElt = (IRPModelElement) item;
                        if (tagName.equalsIgnoreCase(tagElt.getName())) {
                            try {
                                Method getVal = tagElt.getClass().getMethod("getValue");
                                Object v = getVal.invoke(tagElt);
                                if (v instanceof String && !((String) v).trim().isEmpty()) {
                                    return ((String) v).trim();
                                }
                            } catch (Throwable ignore) {}
                        }
                    }
                }
            }
        } catch (Throwable t) { /* ignore */ }

        try {
            String val = elt.getPropertyValue(tagName);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        } catch (Throwable t) { /* ignore */ }

        return null;
    }
}
