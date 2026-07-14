// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyAliasResolver.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.DefaultResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.ResolvedValue;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Rhapsody-specific implementation of AliasResolver.
 * Reads values from the live model based on alias definitions from config.
 */
public final class RhapsodyAliasResolver implements AliasResolver {

    private final RuleCheckerConfig config;
    private final RhapsodyModelSnapshot snapshot;

    public RhapsodyAliasResolver(RuleCheckerConfig config, RhapsodyModelSnapshot snapshot) {
        this.config = Objects.requireNonNull(config, "config");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public ResolvedValue resolveValue(ElementRecord element, String aliasId) {
        // Handle built-in shortcuts (description, name) without alias definition
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

        // Try reading the tag value
        String value = tryReadTag(handle, tagName);
        if (value != null && !value.trim().isEmpty()) {
            return DefaultResolvedValue.of(value.trim(), "taggedValue:" + tagName);
        }

        // Fallback: check if ASIL-like tag is modeled as stereotypes
        if (!alias.values().isEmpty()) {
            // Check if any stereotype matches an allowed value
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
        if (has) {
            return DefaultResolvedValue.of("true", "stereotype:" + stereoName);
        }
        return DefaultResolvedValue.of("false", "stereotype:" + stereoName);
    }

    private ResolvedValue resolveStereotypeSet(ElementRecord element, AliasDefinition alias) {
        List<String> matched = new ArrayList<>();
        for (String candidate : alias.stereotypeNames()) {
            if (element.hasStereotypeIgnoreCase(candidate)) {
                matched.add(candidate);
            }
        }
        if (!matched.isEmpty()) {
            // Return the first match as the canonical value
            return DefaultResolvedValue.of(matched.get(0), "stereotypeSet:" + matched.get(0));
        }
        return DefaultResolvedValue.absent();
    }

    // ---- Rhapsody tag reading ----

    private String tryReadTag(IRPModelElement elt, String tagName) {
        // Method 1: try getTag(tagName)
        try {
            IRPTag tag = elt.getTag(tagName);
            if (tag != null) {
                String val = tag.getValue();
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        } catch (Throwable t) {
            // ignore, try next method
        }

        // Method 2: iterate getTags() collection
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
        } catch (Throwable t) {
            // ignore
        }

        // Method 3: try getPropertyValue
        try {
            String val = elt.getPropertyValue(tagName);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        } catch (Throwable t) {
            // ignore
        }

        return null;
    }
}
