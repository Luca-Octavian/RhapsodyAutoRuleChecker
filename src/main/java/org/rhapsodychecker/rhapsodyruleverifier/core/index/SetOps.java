package org.rhapsodychecker.rhapsodyruleverifier.core.index;

import java.util.*;

public final class SetOps {
    private SetOps() {}

    public static Set<String> intersect(Set<String> a, Set<String> b) {
        if (a == null || a.isEmpty()) return Collections.emptySet();
        if (b == null || b.isEmpty()) return Collections.emptySet();
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = a.size() <= b.size() ? b : a;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String x : small) if (large.contains(x)) out.add(x);
        return Collections.unmodifiableSet(out);
    }

    public static Set<String> intersectAll(Collection<Set<String>> sets) {
        if (sets == null || sets.isEmpty()) return Collections.emptySet();
        Iterator<Set<String>> it = sets.iterator();
        Set<String> acc = new LinkedHashSet<>(Optional.ofNullable(it.next()).orElse(Collections.emptySet()));
        while (it.hasNext() && !acc.isEmpty()) {
            Set<String> next = Optional.ofNullable(it.next()).orElse(Collections.emptySet());
            acc = new LinkedHashSet<>(intersect(acc, next));
        }
        return Collections.unmodifiableSet(acc);
    }

    public static Set<String> union(Collection<Set<String>> sets) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (sets != null) for (Set<String> s : sets) if (s != null) out.addAll(s);
        return Collections.unmodifiableSet(out);
    }
}