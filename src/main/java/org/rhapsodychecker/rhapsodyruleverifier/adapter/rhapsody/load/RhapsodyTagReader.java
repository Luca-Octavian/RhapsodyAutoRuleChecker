package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPTag;
import org.rhapsodychecker.rhapsodyruleverifier.core.util.ReflectiveMethodCache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads tagged values from Rhapsody elements.
 *
 * <p>Local tags represent values created directly on an element. All tags also
 * include inherited/profile tags. Collection counts are cached to avoid
 * repeated native API calls.
 */
public final class RhapsodyTagReader {

    private RhapsodyTagReader() {}

    public static Map<String, String> readLocalTags(IRPModelElement element) {
        if (element == null) return Collections.emptyMap();
        try {
            return readCollection(element.getLocalTags());
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }

    public static Map<String, String> readAllTags(IRPModelElement element) {
        if (element == null) return Collections.emptyMap();
        try {
            return readCollection(element.getAllTags());
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }

    /**
     * Merges local and inherited/profile tags. Values from getAllTags() retain
     * the existing precedence used by the loader.
     */
    public static Map<String, String> readLocalAndAllTags(IRPModelElement element) {
        Map<String, String> local = readLocalTags(element);
        Map<String, String> all = readAllTags(element);
        if (all.isEmpty()) return local;
        if (local.isEmpty()) return all;

        Map<String, String> merged = new LinkedHashMap<String, String>(local);
        merged.putAll(all);
        return merged;
    }

    private static Map<String, String> readCollection(IRPCollection collection) {
        if (collection == null) return Collections.emptyMap();

        int count = collection.getCount();
        if (count == 0) return Collections.emptyMap();

        Map<String, String> tags = new LinkedHashMap<String, String>();
        for (int i = 1; i <= count; i++) {
            Object item = collection.getItem(i);
            TagValue tag = readTag(item);
            if (tag != null && tag.name != null && !tag.name.trim().isEmpty()) {
                tags.put(tag.name.trim(), tag.value != null ? tag.value.trim() : "");
            }
        }
        return tags.isEmpty() ? Collections.<String, String>emptyMap() : tags;
    }

    private static TagValue readTag(Object item) {
        if (item == null) return null;

        if (item instanceof IRPTag) {
            IRPTag tag = (IRPTag) item;
            try {
                String name = tag.getName();
                String value = null;
                try { value = tag.getValue(); } catch (Throwable ignored) {}
                return new TagValue(name, value);
            } catch (Throwable ignored) {
                return null;
            }
        }

        if (item instanceof IRPModelElement) {
            IRPModelElement element = (IRPModelElement) item;
            try {
                Object value = ReflectiveMethodCache.invokeOrNull(element, "getValue");
                return new TagValue(element.getName(),
                        value != null ? value.toString() : null);
            } catch (Throwable ignored) {
                return null;
            }
        }

        Object name = ReflectiveMethodCache.invokeOrNull(item, "getName");
        if (name == null) return null;
        Object value = ReflectiveMethodCache.invokeOrNull(item, "getValue");
        return new TagValue(name.toString(), value != null ? value.toString() : null);
    }

    private static final class TagValue {
        private final String name;
        private final String value;

        private TagValue(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}