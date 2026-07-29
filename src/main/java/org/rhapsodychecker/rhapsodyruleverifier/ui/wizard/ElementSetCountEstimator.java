// ui/wizard/ElementSetCountEstimator.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import java.util.*;

/**
 * Computes exact element counts for a combination of
 * kinds + types + stereotypes, using pre-cached data from FastDetectionResult.
 *
 * Logic mirrors ElementSelector:
 *   - OR within each category (union)
 *   - AND between categories (intersection → min of category counts)
 *
 * All counts come from pre-loaded ElementRecord data — no COM calls needed.
 */
public final class ElementSetCountEstimator {

    private ElementSetCountEstimator() {}

    /**
     * Computes the total number of elements affected by the current selection.
     * Returns -1 if fast is null (no data available).
     */
    public static int estimateTotal(
            FastDetectionResult fast,
            List<String> selectedKinds,
            List<String> selectedTypes,
            List<String> selectedStereotypes) {

        if (fast == null) return -1;

        int kindsCount  = countKinds(fast, selectedKinds);
        int typesCount  = countTypes(fast, selectedTypes);
        int stereoCount = countStereotypes(fast, selectedStereotypes);

        // If nothing selected → total elements
        if (kindsCount < 0 && typesCount < 0 && stereoCount < 0) {
            return (int) fast.totalElements();
        }

        // AND between categories → min (most restrictive filter)
        int result = Integer.MAX_VALUE;
        if (kindsCount >= 0)  result = Math.min(result, kindsCount);
        if (typesCount >= 0)  result = Math.min(result, typesCount);
        if (stereoCount >= 0) result = Math.min(result, stereoCount);

        return result == Integer.MAX_VALUE ? 0 : result;
    }

    /**
     * Exact count for Kinds section — OR union using countsByKind.
     * Returns -1 if nothing selected.
     */
    public static int countKinds(FastDetectionResult fast, List<String> kinds) {
        if (fast == null || kinds == null || kinds.isEmpty()) return -1;

        int total = 0;
        for (String kind : kinds) {
            String key = kind.toUpperCase(java.util.Locale.ROOT);
            Long count = fast.countsByKind().get(key);
            if (count != null) {
                total += count;
            }
        }
        return total;
    }

    /**
     * Exact count for Types section — OR union using countsByMetaClass.
     * Returns -1 if nothing selected.
     */
    public static int countTypes(FastDetectionResult fast, List<String> types) {
        if (fast == null || types == null || types.isEmpty()) return -1;
        int total = 0;
        for (String type : types) {
            // Case-insensitive lookup
            for (Map.Entry<String, Long> e : fast.countsByMetaClass().entrySet()) {
                if (e.getKey().equalsIgnoreCase(type)) {
                    total += e.getValue();
                    break;
                }
            }
        }
        return total;
    }

    /**
     * Exact count for Stereotypes section — OR union using countsByStereotype.
     * Returns -1 if nothing selected.
     */
    public static int countStereotypes(FastDetectionResult fast, List<String> stereotypes) {
        if (fast == null || stereotypes == null || stereotypes.isEmpty()) return -1;
        int total = 0;
        for (String stereo : stereotypes) {
            for (Map.Entry<String, Long> e : fast.countsByStereotype().entrySet()) {
                if (e.getKey().equalsIgnoreCase(stereo)) {
                    total += e.getValue();
                    break;
                }
            }
        }
        return total;
    }

    // ── Backward-compatible aliases ─────────────────────────────────────────
    // These delegate to the new exact-count methods so that any callers using
    // the old "estimate" names still compile without changes.

    /** @deprecated Use {@link #countKinds(FastDetectionResult, List)} instead. */
    public static int estimateKinds(FastDetectionResult fast, List<String> kinds) {
        return countKinds(fast, kinds);
    }

    /** @deprecated Use {@link #countTypes(FastDetectionResult, List)} instead. */
    public static int estimateTypes(FastDetectionResult fast, List<String> types) {
        return countTypes(fast, types);
    }

    /** @deprecated Use {@link #countStereotypes(FastDetectionResult, List)} instead. */
    public static int estimateStereotypes(FastDetectionResult fast, List<String> stereotypes) {
        return countStereotypes(fast, stereotypes);
    }
}