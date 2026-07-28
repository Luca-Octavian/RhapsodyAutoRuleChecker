package org.rhapsodychecker.rhapsodyruleverifier.core.index;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

final class ElementIndexBuilder {
    private final ElementRepository repo;

    ElementIndexBuilder(ElementRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    ElementIndex build() {
        int size = repo.allRecords().size();
        // Pre-sized capacity with load factor headroom to avoid rehashing
        int mapCap = Math.max(16, (int) (size * 0.75));

        Map<ElementKind, Set<String>> byKind = new EnumMap<>(ElementKind.class);
        Map<String, Set<String>> byStereotype = new HashMap<>(mapCap);
        Map<String, Set<String>> byMetaClass = new HashMap<>(64); // typically few distinct metaClasses
        Map<String, Set<String>> byOwnerGuid = new HashMap<>(mapCap);
        Map<String, Set<String>> byPackagePath = new HashMap<>(mapCap);
        Map<String, Set<String>> byName = new HashMap<>(mapCap);

        for (ElementRecord r : repo.allRecords()) {
            String guid = r.guid();

            byKind.computeIfAbsent(r.kind(), k -> new HashSet<>()).add(guid);

            for (String st : r.stereotypes()) {
                byStereotype.computeIfAbsent(st, k -> new HashSet<>()).add(guid);
                String lower = st.toLowerCase(Locale.ROOT);
                if (!lower.equals(st)) {
                    byStereotype.computeIfAbsent(lower, k -> new HashSet<>()).add(guid);
                }
            }

            String mc = r.metaClass();
            if (mc != null && !mc.isEmpty()) {
                byMetaClass.computeIfAbsent(mc, k -> new HashSet<>()).add(guid);
                String mcLower = mc.toLowerCase(Locale.ROOT);
                if (!mcLower.equals(mc)) {
                    byMetaClass.computeIfAbsent(mcLower, k -> new HashSet<>()).add(guid);
                }
            }

            r.ownerGuid().ifPresent(og -> byOwnerGuid.computeIfAbsent(og, k -> new HashSet<>()).add(guid));

            r.ownerPath().ifPresent(p -> byPackagePath.computeIfAbsent(p, k -> new HashSet<>()).add(guid));

            String name = r.name();
            if (name != null && !name.isEmpty()) {
                byName.computeIfAbsent(name, k -> new HashSet<>()).add(guid);
                String nameLower = name.toLowerCase(Locale.ROOT);
                if (!nameLower.equals(name)) {
                    byName.computeIfAbsent(nameLower, k -> new HashSet<>()).add(guid);
                }
            }
        }

        return new ElementIndex(
                repo,
                freezeEnumMap(byKind),
                freezeStringSetMap(byStereotype),
                freezeStringSetMap(byMetaClass),
                freezeStringSetMap(byOwnerGuid),
                freezeStringSetMap(byPackagePath),
                freezeStringSetMap(byName)
        );
    }

    private static Map<ElementKind, Set<String>> freezeEnumMap(Map<ElementKind, Set<String>> in) {
        EnumMap<ElementKind, Set<String>> out = new EnumMap<>(ElementKind.class);
        for (Map.Entry<ElementKind, Set<String>> e : in.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Set<String>> freezeStringSetMap(Map<String, Set<String>> in) {
        Map<String, Set<String>> out = new HashMap<>(in.size());
        for (Map.Entry<String, Set<String>> e : in.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }
}