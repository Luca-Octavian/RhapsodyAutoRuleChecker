// ui/wizard/ElementSetCountEstimator.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import java.util.*;

/**
 * Aproximează numărul de elemente afectate de o combinație de
 * kinds + types + stereotypes, folosind datele din FastDetectionResult.
 *
 * Logica mirror-uiește ElementSelector:
 *   - OR within each category (union)
 *   - AND between categories (intersection → estimat cu min)
 *
 * Kinds sunt mapate la meta-class + stereotype echivalente pentru estimare.
 */
public final class ElementSetCountEstimator {

    // Mapare kind → stereotype Rhapsody (pentru estimare)
    private static final Map<String, String> KIND_TO_STEREOTYPE = new LinkedHashMap<>();
    // Mapare kind → metaClass Rhapsody (pentru estimare)
    private static final Map<String, String> KIND_TO_META = new LinkedHashMap<>();

    static {
        KIND_TO_STEREOTYPE.put("BLOCK",           "Block");
        KIND_TO_STEREOTYPE.put("INTERFACE_BLOCK",  "InterfaceBlock");
        KIND_TO_STEREOTYPE.put("PORT_FULL",        "FullPort");
        KIND_TO_STEREOTYPE.put("PORT_PROXY",       "ProxyPort");
        KIND_TO_STEREOTYPE.put("PORT_FLOW",        "FlowPort");

        KIND_TO_META.put("PART",        "Object");
        KIND_TO_META.put("PORT",        "Port");
        KIND_TO_META.put("PORT_FULL",   "Port");
        KIND_TO_META.put("PORT_PROXY",  "Port");
        KIND_TO_META.put("PORT_FLOW",   "Port");
        KIND_TO_META.put("INTERFACE",   "Interface");
        KIND_TO_META.put("PACKAGE",     "Package");
        KIND_TO_META.put("REQUIREMENT", "Requirement");
        KIND_TO_META.put("CONNECTOR",   "Connector");
    }

    private ElementSetCountEstimator() {}

    /**
     * Estimează totalul de elemente afectate de selecția curentă.
     * Returnează -1 dacă fast e null (nu avem date).
     */
    public static int estimateTotal(
            FastDetectionResult fast,
            List<String> selectedKinds,
            List<String> selectedTypes,
            List<String> selectedStereotypes) {

        if (fast == null) return -1;

        int kindsCount  = estimateKinds(fast, selectedKinds);
        int typesCount  = estimateTypes(fast, selectedTypes);
        int stereoCount = estimateStereotypes(fast, selectedStereotypes);

        // Dacă nimic selectat → totalul elementelor
        if (kindsCount < 0 && typesCount < 0 && stereoCount < 0) {
            int total = 0;
            for (long c : fast.countsByMetaClass().values()) total += c;
            return total;
        }

        // AND between categories → estimăm cu min (cel mai restrictiv filtra)
        int result = Integer.MAX_VALUE;
        if (kindsCount >= 0)  result = Math.min(result, kindsCount);
        if (typesCount >= 0)  result = Math.min(result, typesCount);
        if (stereoCount >= 0) result = Math.min(result, stereoCount);

        return result == Integer.MAX_VALUE ? 0 : result;
    }

    /**
     * Count per secțiune Kinds — OR union.
     * Returnează -1 dacă nimic selectat.
     */
    public static int estimateKinds(FastDetectionResult fast, List<String> kinds) {
        if (fast == null || kinds == null || kinds.isEmpty()) return -1;

        // Evităm double-counting: unele kinds share meta-class (ex: PORT, PORT_FULL)
        // Cel mai bun approach: sumăm stereotipurile unde avem mapare, altfel meta-class
        Set<String> countedMeta = new HashSet<>();
        int total = 0;

        for (String kind : kinds) {
            String stereo = KIND_TO_STEREOTYPE.get(kind.toUpperCase());
            String meta   = KIND_TO_META.get(kind.toUpperCase());

            if (stereo != null && fast.countsByStereotype().containsKey(stereo)) {
                // Stereotip specific → count exact
                total += fast.countsByStereotype().get(stereo);
            } else if (meta != null && !countedMeta.contains(meta)) {
                // Meta-class fallback, dar numărăm o singură dată per meta
                Long c = fast.countsByMetaClass().get(meta);
                if (c != null) total += c;
                countedMeta.add(meta);
            }
        }
        return total;
    }

    /**
     * Count per secțiune Types — OR union.
     * Returnează -1 dacă nimic selectat.
     */
    public static int estimateTypes(FastDetectionResult fast, List<String> types) {
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
     * Count per secțiune Stereotypes — OR union.
     * Returnează -1 dacă nimic selectat.
     */
    public static int estimateStereotypes(FastDetectionResult fast, List<String> stereotypes) {
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
}
