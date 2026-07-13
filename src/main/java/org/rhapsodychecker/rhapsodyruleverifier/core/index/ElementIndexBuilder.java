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
        Map<ElementKind, Set<String>> byKind = new EnumMap<>(ElementKind.class);
        Map<String, Set<String>> byStereotype = new LinkedHashMap<>();
        Map<String, Set<String>> byMetaClass = new LinkedHashMap<>();
        Map<String, Set<String>> byOwnerGuid = new LinkedHashMap<>();
        Map<String, Set<String>> byPackagePath = new LinkedHashMap<>();
        Map<String, Set<String>> byName = new LinkedHashMap<>();

        for (ElementRecord r : repo.allRecords()) {
            String guid = r.guid();

            byKind.computeIfAbsent(r.kind(), k -> new LinkedHashSet<>()).add(guid);

            for (String st : r.stereotypes()) {
                byStereotype.computeIfAbsent(st, k -> new LinkedHashSet<>()).add(guid);
                byStereotype.computeIfAbsent(st.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>()).add(guid);
            }

            String mc = r.metaClass();
            if (mc != null && !mc.isEmpty()) {
                byMetaClass.computeIfAbsent(mc, k -> new LinkedHashSet<>()).add(guid);
                byMetaClass.computeIfAbsent(mc.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>()).add(guid);
            }

            r.ownerGuid().ifPresent(og -> byOwnerGuid.computeIfAbsent(og, k -> new LinkedHashSet<>()).add(guid));

            r.ownerPath().ifPresent(p -> byPackagePath.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(guid));

            String name = r.name();
            if (name != null && !name.isEmpty()) {
                byName.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(guid);
                byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>()).add(guid);
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
            out.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, Set<String>> freezeStringSetMap(Map<String, Set<String>> in) {
        Map<String, Set<String>> out = new LinkedHashMap<>(in.size());
        for (Map.Entry<String, Set<String>> e : in.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }
}