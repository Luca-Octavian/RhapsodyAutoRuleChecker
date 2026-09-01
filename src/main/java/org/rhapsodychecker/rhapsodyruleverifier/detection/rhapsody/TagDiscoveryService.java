// detection/rhapsody/TagDiscoveryService.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.TagDiscoveryResult;
import java.lang.reflect.Method;


import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers existing tags on a subset of elements.
 *
 * Why scoped and not global:
 *   On a model with 6000+ elements, calling getTags() on each would double load time.
 *   Instead, the wizard knows "the user is configuring rules for Blocks" →
 *   we scan only the ~130 Blocks, not the entire model.
 *
 * maxPerKind = additional safety cap: even if the scope is wide,
 *   we scan no more than N elements per kind.
 */
public final class TagDiscoveryService {

    private static final Logger LOG = Logger.getLogger(TagDiscoveryService.class.getName());

    private final int maxPerKind;

    public TagDiscoveryService(int maxPerKind) {
        this.maxPerKind = maxPerKind;
    }

    /**
     * @param records      all records from the snapshot
     * @param scopeFilter  predicate that restricts what gets scanned
     *                     (e.g., r -> r.kind() == BLOCK)
     * @param handleByGuid map GUID → live native handle
     */
    public TagDiscoveryResult discover(
            List<ElementRecord>          records,
            Predicate<ElementRecord>     scopeFilter,
            Map<String, IRPModelElement> handleByGuid
    ) {
        Map<String, Set<String>> tagValues = new TreeMap<>();
        Map<ElementKind, Integer> seen     = new EnumMap<>(ElementKind.class);

        for (ElementRecord record : records) {
            if (!scopeFilter.test(record)) continue;

            // Check that we don't exceed the cap per kind
            int count = seen.merge(record.kind(), 1, Integer::sum);
            if (count > maxPerKind) continue;

            IRPModelElement handle = handleByGuid.get(record.guid());
            if (handle == null) continue;

            collectTagsFrom(handle, tagValues);
        }

        return new TagDiscoveryResult(tagValues);
    }

    private void collectTagsFrom(IRPModelElement handle, Map<String, Set<String>> accumulator) {
        try {
            Method getTags = handle.getClass().getMethod("getTags");
            IRPCollection tags = (IRPCollection) getTags.invoke(handle);
            if (tags == null) return;

            for (int i = 1; i <= tags.getCount(); i++) {
                Object tag = tags.getItem(i);
                if (tag == null) continue;

                String name  = tryGetString(tag, "getName");
                String value = tryGetString(tag, "getValue");

                if (name != null && !name.trim().isEmpty()) {
                    accumulator
                            .computeIfAbsent(name, k -> new TreeSet<>())
                            .add(value != null ? value : "");
                }
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not read tags from element, skipping", e);
        }
    }

    private static String tryGetString(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);
            return (v instanceof String) ? ((String) v).trim() : null;
        } catch (Throwable t) {
            return null;
        }
    }

}
