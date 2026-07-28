// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/update/ElementFingerprint.java
package org.rhapsodychecker.rhapsodyruleverifier.cache.update;

import org.rhapsodychecker.rhapsodyruleverifier.cache.CachedElement;

import java.util.*;

/**
 * Lightweight fingerprint of a live model element, captured during the Phase 1 scan.
 * Contains only the fields needed to detect changes against the cached version.
 *
 * <p>Does NOT include:
 * <ul>
 *   <li>tagValues — expensive to read (getTags() + getName()/getValue() per tag), skipped to reduce COM calls</li>
 *   <li>ownerPath, portInfo, typeInfo, references — read only during Phase 3 full-read</li>
 * </ul>
 *
 * <p>Tags are preserved from cache for unchanged elements. If name, metaClass, stereotypes,
 * or description changed, a full read (including tags) is triggered in Phase 3.
 */
public final class ElementFingerprint {

    private final String guid;
    private final String name;
    private final String metaClass;
    private final Set<String> stereotypes;
    private final String description;

    public ElementFingerprint(String guid, String name, String metaClass,
                              Set<String> stereotypes, String description) {
        this.guid = guid;
        this.name = name;
        this.metaClass = metaClass;
        this.stereotypes = stereotypes != null ? stereotypes : Collections.<String>emptySet();
        this.description = description;
    }

    public String guid() { return guid; }
    public String name() { return name; }
    public String metaClass() { return metaClass; }
    public Set<String> stereotypes() { return stereotypes; }
    public String description() { return description; }

    /**
     * Compare this live fingerprint against a cached element.
     * Returns true if the element is unchanged (all checked fields match).
     *
     * <p>Note: tagValues are NOT compared — they are only read during full-read
     * for elements where other fields changed. This saves the expensive getTags()
     * COM call chain during the scan phase.
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