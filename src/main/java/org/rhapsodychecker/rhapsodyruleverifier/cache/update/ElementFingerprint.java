// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/update/ElementFingerprint.java
package org.rhapsodychecker.rhapsodyruleverifier.cache.update;

import org.rhapsodychecker.rhapsodyruleverifier.cache.CachedElement;

import java.util.Map;
import java.util.*;

/**
 * Lightweight fingerprint of a live model element, captured during the Phase 1 scan.
 * Contains only the fields needed to detect changes against the cached version.
 *
 * <p>Does NOT include:
 * <ul>
 *   <li>ownerPath, portInfo, typeInfo, references — read only during Phase 3 full-read</li>
 * </ul>
 *
 * <p>Tag values are included in the fingerprint so that tag-only changes (e.g. after
 * an auto-fix SET_TAG_VALUE) are detected during incremental updates. If name, metaClass,
 * stereotypes, description, or tagValues changed, a full read is triggered in Phase 3.
 */
public final class ElementFingerprint {

    private final String guid;
    private final String name;
    private final String metaClass;
    private final Set<String> stereotypes;
    private final String description;
    private final Map<String, String> tagValues;

    public ElementFingerprint(String guid, String name, String metaClass,
                              Set<String> stereotypes, String description,
                              Map<String, String> tagValues) {
        this.guid = guid;
        this.name = name;
        this.metaClass = metaClass;
        this.stereotypes = stereotypes != null ? stereotypes : Collections.<String>emptySet();
        this.description = description;
        this.tagValues = tagValues != null ? tagValues : Collections.<String, String>emptyMap();
    }

    public String guid() { return guid; }
    public String name() { return name; }
    public String metaClass() { return metaClass; }
    public Set<String> stereotypes() { return stereotypes; }
    public String description() { return description; }
    public Map<String, String> tagValues() { return tagValues; }

    /**
     * Compare this live fingerprint against a cached element.
     * Returns true if the element is unchanged (all checked fields match).
     */
    public boolean matches(CachedElement cached) {
        if (cached == null) return false;

        // Always compare: name, metaClass
        if (!safeEquals(name, cached.getName())) return false;
        if (!safeEquals(metaClass, cached.getMetaClass())) return false;

        // Compare stereotypes (order-insensitive)
        Set<String> cachedStereos = cached.getStereotypes();
        if (cachedStereos == null) cachedStereos = Collections.emptySet();
        if (!setsEqualIgnoreCase(stereotypes, cachedStereos)) return false;

        // Compare description
        String cachedDesc = cached.getDescription();
        if (!safeEquals(normalizeEmpty(description), normalizeEmpty(cachedDesc))) return false;

        // Compare tag values
        Map<String, String> cachedTags = cached.getTagValues();
        if (cachedTags == null) cachedTags = Collections.emptyMap();
        if (!mapsEqual(tagValues, cachedTags)) return false;

        return true;
    }

    /**
     * Returns a human-readable summary of what changed vs the cached element.
     * Useful for debug logging.
     */
    public String diffSummary(CachedElement cached) {
        if (cached == null) return "NEW";

        List<String> diffs = new ArrayList<String>();
        if (!safeEquals(name, cached.getName())) {
            diffs.add("name: '" + cached.getName() + "' -> '" + name + "'");
        }
        if (!safeEquals(metaClass, cached.getMetaClass())) {
            diffs.add("metaClass: '" + cached.getMetaClass() + "' -> '" + metaClass + "'");
        }

        Set<String> cachedStereos = cached.getStereotypes();
        if (cachedStereos == null) cachedStereos = Collections.emptySet();
        if (!setsEqualIgnoreCase(stereotypes, cachedStereos)) {
            diffs.add("stereotypes: " + cachedStereos + " -> " + stereotypes);
        }

        String cachedDesc = cached.getDescription();
        if (!safeEquals(normalizeEmpty(description), normalizeEmpty(cachedDesc))) {
            diffs.add("description changed");
        }

        Map<String, String> cachedTags = cached.getTagValues();
        if (cachedTags == null) cachedTags = Collections.emptyMap();
        if (!mapsEqual(tagValues, cachedTags)) {
            diffs.add("tagValues changed");
        }

        return diffs.isEmpty() ? "UNCHANGED" : String.join(", ", diffs);
    }

    // ---- Helpers ----

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private static String normalizeEmpty(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean mapsEqual(Map<String, String> a, Map<String, String> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<String, String> entry : a.entrySet()) {
            String bVal = b.get(entry.getKey());
            if (!safeEquals(entry.getValue(), bVal)) return false;
        }
        return true;
    }

    private static boolean setsEqualIgnoreCase(Set<String> a, Set<String> b) {
        if (a.size() != b.size()) return false;
        // Build lowercase versions for comparison
        Set<String> aLower = new TreeSet<String>();
        for (String s : a) {
            aLower.add(s.toLowerCase());
        }
        Set<String> bLower = new TreeSet<String>();
        for (String s : b) {
            bLower.add(s.toLowerCase());
        }
        return aLower.equals(bLower);
    }
}