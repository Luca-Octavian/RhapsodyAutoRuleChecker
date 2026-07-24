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
 * Descoperă tag-urile existente pe un subset de elemente.
 *
 * De ce scoped și nu global:
 *   Pe un model cu 6000+ elemente, getTags() pe fiecare ar dubla timpul de încărcare.
 *   În schimb, wizard-ul știe "utilizatorul configurează reguli pentru Block-uri" →
 *   scanăm doar cele ~130 de Block-uri, nu tot modelul.
 *
 * maxPerKind = cap de siguranță suplimentar: chiar dacă scope-ul e larg,
 *   nu scanăm mai mult de N elemente per tip.
 */
public final class TagDiscoveryService {

    private static final Logger LOG = Logger.getLogger(TagDiscoveryService.class.getName());

    private final int maxPerKind;

    public TagDiscoveryService(int maxPerKind) {
        this.maxPerKind = maxPerKind;
    }

    /**
     * @param records      toate record-urile din snapshot
     * @param scopeFilter  predicate care restricționează ce se scanează
     *                     (ex: r -> r.kind() == BLOCK)
     * @param handleByGuid map GUID → handle nativ live
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

            // Verificăm că nu depășim capul per kind
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
