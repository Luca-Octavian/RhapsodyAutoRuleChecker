package org.rhapsodychecker.rhapsodyruleverifier.core.index;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

public final class ElementIndex {
    private final ElementRepository repo;
    private final Map<ElementKind, Set<String>> byKind;
    private final Map<String, Set<String>> byStereotype;
    private final Map<String, Set<String>> byMetaClass;
    private final Map<String, Set<String>> byOwnerGuid;
    private final Map<String, Set<String>> byPackagePath; // exact paths
    private final Map<String, Set<String>> byName;

    ElementIndex(ElementRepository repo,
                 Map<ElementKind, Set<String>> byKind,
                 Map<String, Set<String>> byStereotype,
                 Map<String, Set<String>> byMetaClass,
                 Map<String, Set<String>> byOwnerGuid,
                 Map<String, Set<String>> byPackagePath,
                 Map<String, Set<String>> byName) {
        this.repo = repo;
        this.byKind = byKind;
        this.byStereotype = byStereotype;
        this.byMetaClass = byMetaClass;
        this.byOwnerGuid = byOwnerGuid;
        this.byPackagePath = byPackagePath;
        this.byName = byName;
    }

    public static ElementIndex build(Collection<ElementRecord> records) {
        return new ElementIndexBuilder(new ElementRepository(records)).build();
    }

    public ElementRepository repository() {
        return repo;
    }

    public Set<String> guidsByKind(ElementKind kind) {
        return byKind.getOrDefault(kind, Collections.emptySet());
    }

    public Set<String> guidsByStereotype(String name, boolean ignoreCase) {
        if (name == null) return Collections.emptySet();
        return ignoreCase
                ? byStereotype.getOrDefault(name.toLowerCase(Locale.ROOT), Collections.emptySet())
                : byStereotype.getOrDefault(name, Collections.emptySet());
    }

    public Set<String> guidsByMetaClass(String metaClass, boolean ignoreCase) {
        if (metaClass == null) return Collections.emptySet();
        return ignoreCase
                ? byMetaClass.getOrDefault(metaClass.toLowerCase(Locale.ROOT), Collections.emptySet())
                : byMetaClass.getOrDefault(metaClass, Collections.emptySet());
    }

    public Set<String> guidsByOwnerGuid(String ownerGuid) {
        if (ownerGuid == null) return Collections.emptySet();
        return byOwnerGuid.getOrDefault(ownerGuid, Collections.emptySet());
    }

    public Set<String> guidsByPackagePathExact(String path) {
        if (path == null) return Collections.emptySet();
        return byPackagePath.getOrDefault(path, Collections.emptySet());
    }

    public Set<String> guidsByPackagePathPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptySet();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> e : byPackagePath.entrySet()) {
            String key = e.getKey();
            if (key != null && (key.equals(prefix) || key.startsWith(prefix + "::"))) {
                out.addAll(e.getValue());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public Set<String> guidsByName(String name, boolean ignoreCase) {
        if (name == null) return Collections.emptySet();
        return ignoreCase
                ? byName.getOrDefault(name.toLowerCase(Locale.ROOT), Collections.emptySet())
                : byName.getOrDefault(name, Collections.emptySet());
    }

    public List<ElementRecord> toRecords(Set<String> guids) {
        if (guids == null || guids.isEmpty()) return Collections.emptyList();
        List<ElementRecord> out = new ArrayList<>(guids.size());
        for (String g : guids) {
            repo.get(g).ifPresent(out::add);
        }
        out.sort(Comparator
                .comparing((ElementRecord r) -> r.ownerPath().orElse(""))
                .thenComparing(ElementRecord::name));
        return Collections.unmodifiableList(out);
    }

    /**
     * Convert GUIDs to records WITHOUT sorting — faster for evaluation where
     * order doesn't matter. Use {@link #toRecords(Set)} when display order is needed.
     */
    public List<ElementRecord> toRecordsUnsorted(Set<String> guids) {
        if (guids == null || guids.isEmpty()) return Collections.emptyList();
        List<ElementRecord> out = new ArrayList<>(guids.size());
        for (String g : guids) {
            repo.get(g).ifPresent(out::add);
        }
        return Collections.unmodifiableList(out);
    }

    public List<ElementRecord> byKindAsRecords(ElementKind kind) {
        return toRecords(guidsByKind(kind));
    }
}