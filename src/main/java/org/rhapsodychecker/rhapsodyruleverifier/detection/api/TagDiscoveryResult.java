// detection/api/TagDiscoveryResult.java
package org.rhapsodychecker.rhapsodyruleverifier.detection.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Toate tag-urile găsite pe elementele scanate, cu valorile lor observate.
 * Ex: { "ASIL" -> ["QM","A","B"], "Description" -> ["", "text..."] }
 * Folosit de SuggestionsService pentru a ghici alias-urile potrivite.
 */
public final class TagDiscoveryResult {

    private final Map<String, Set<String>> valuesByTag;

    public TagDiscoveryResult(Map<String, Set<String>> valuesByTag) {
        Map<String, Set<String>> copy = new TreeMap<>();
        valuesByTag.forEach((tag, vals) ->
                copy.put(tag, Collections.unmodifiableSet(new TreeSet<>(vals))));
        this.valuesByTag = Collections.unmodifiableMap(copy);
    }

    public static TagDiscoveryResult empty() {
        return new TagDiscoveryResult(Collections.emptyMap());
    }

    public Map<String, Set<String>> valuesByTag()      { return valuesByTag; }
    public Set<String>              tagNames()          { return valuesByTag.keySet(); }
    public boolean                  hasTag(String name) { return valuesByTag.containsKey(name); }

    public Set<String> valuesFor(String tagName) {
        return valuesByTag.getOrDefault(tagName, Collections.emptySet());
    }
}
