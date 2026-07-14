// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/resolve/DefaultResolvedValue.java
package org.rhapsodychecker.rhapsodyruleverifier.core.resolve;

import java.util.*;

public final class DefaultResolvedValue implements ResolvedValue {

    private final boolean present;
    private final String value;
    private final List<String> listValue;
    private final String source;

    private DefaultResolvedValue(boolean present, String value, List<String> listValue, String source) {
        this.present = present;
        this.value = value;
        this.listValue = listValue != null
                ? Collections.unmodifiableList(new ArrayList<>(listValue))
                : Collections.emptyList();
        this.source = source;
    }

    public static DefaultResolvedValue absent() {
        return new DefaultResolvedValue(false, null, null, null);
    }

    public static DefaultResolvedValue of(String value, String source) {
        return new DefaultResolvedValue(
                value != null && !value.trim().isEmpty(),
                value, null, source);
    }

    public static DefaultResolvedValue ofList(List<String> values, String source) {
        boolean present = values != null && !values.isEmpty();
        String single = present ? values.get(0) : null;
        return new DefaultResolvedValue(present, single, values, source);
    }

    @Override public boolean isPresent() { return present; }

    @Override public Optional<String> asString() {
        return Optional.ofNullable(value);
    }

    @Override public Optional<Boolean> asBoolean() {
        if (value == null) return Optional.empty();
        String v = value.trim().toLowerCase();
        if ("true".equals(v) || "yes".equals(v) || "1".equals(v)) return Optional.of(true);
        if ("false".equals(v) || "no".equals(v) || "0".equals(v)) return Optional.of(false);
        return Optional.empty();
    }

    @Override public List<String> asList() { return listValue; }

    @Override public Optional<String> source() { return Optional.ofNullable(source); }

    @Override public String toString() {
        return "ResolvedValue{present=" + present + ", value='" + value + "', source='" + source + "'}";
    }
}
