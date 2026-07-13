package org.rhapsodychecker.rhapsodyruleverifier.core.model;

import java.util.Optional;

public final class Multiplicity {
    private final Integer lower; // null means unknown
    private final Integer upper; // null means unbounded (*) or unknown

    private Multiplicity(Integer lower, Integer upper) {
        this.lower = lower;
        this.upper = upper;
    }

    public static Multiplicity of(Integer lower, Integer upper) {
        return new Multiplicity(lower, upper);
    }

    public static Multiplicity unknown() {
        return new Multiplicity(null, null);
    }

    public Optional<Integer> lower() { return Optional.ofNullable(lower); }
    public Optional<Integer> upper() { return Optional.ofNullable(upper); }

    @Override public String toString() {
        String l = lower == null ? "?" : String.valueOf(lower);
        String u = upper == null ? "*" : String.valueOf(upper);
        return l + ".." + u;
    }
}