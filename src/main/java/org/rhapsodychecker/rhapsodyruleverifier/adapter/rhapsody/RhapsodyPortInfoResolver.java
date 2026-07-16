package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPort;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.Multiplicity;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.PortDirection;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.PortInfo;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.PortInfoResolver;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class RhapsodyPortInfoResolver implements PortInfoResolver {

    private final RhapsodyModelSnapshot snapshot;

    public RhapsodyPortInfoResolver(RhapsodyModelSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public PortInfo resolve(ElementRecord element) {
        IRPModelElement handle = snapshot.handleByGuid().get(element.guid());
        if (handle == null) {
            return new PortInfo(PortDirection.UNKNOWN, Multiplicity.unknown(), "not-found", "n/a");
        }

        DirectionResult dir = resolveDirection(element, handle);
        MultiplicityResult mult = resolveMultiplicity(element, handle);

        return new PortInfo(dir.direction, mult.multiplicity, dir.source, mult.source);
    }

    // ---- Direction resolution ----

    private static final class DirectionResult {
        final PortDirection direction;
        final String source;
        DirectionResult(PortDirection d, String s) { this.direction = d; this.source = s; }
    }

    private DirectionResult resolveDirection(ElementRecord element, IRPModelElement handle) {
        // Method 1: getPortDirection() — works for SysMLPort / FlowPort
        Optional<String> portDir = tryCallString(handle, "getPortDirection");
        if (portDir.isPresent()) {
            PortDirection d = mapDirection(portDir.get());
            return new DirectionResult(d, "getPortDirection:" + portDir.get());
        }

        // Method 2: getDirection()
        Optional<String> dir = tryCallString(handle, "getDirection");
        if (dir.isPresent()) {
            PortDirection d = mapDirection(dir.get());
            return new DirectionResult(d, "getDirection:" + dir.get());
        }

        // Method 3: tag-based direction for FlowPorts
        if (hasStereoIgnoreCase(element, "FlowPort") || hasStereoIgnoreCase(element, "flowPort")) {
            Optional<String> tagDir = tryReadTagValue(handle, "direction");
            if (!tagDir.isPresent()) tagDir = tryReadTagValue(handle, "flowDirection");
            if (tagDir.isPresent()) {
                PortDirection d = mapDirection(tagDir.get());
                return new DirectionResult(d, "tag:" + tagDir.get());
            }
        }

        // Method 4: infer from provided/required interfaces
        if (handle instanceof IRPPort) {
            int provided = countCollection(safeInvokeCollection(handle, "getProvidedInterfaces"));
            int required = countCollection(safeInvokeCollection(handle, "getRequiredInterfaces"));
            if (provided > 0 && required > 0) return new DirectionResult(PortDirection.INOUT, "provided/required");
            if (provided > 0) return new DirectionResult(PortDirection.OUT, "provided");
            if (required > 0) return new DirectionResult(PortDirection.IN, "required");
        }

        return new DirectionResult(PortDirection.NONE, "no-direction-found");
    }

    private boolean hasStereoIgnoreCase(ElementRecord e, String s) {
        for (String st : e.stereotypes()) {
            if (st.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private PortDirection mapDirection(String v) {
        if (v == null) return PortDirection.UNKNOWN;
        String s = v.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "in": case "input": return PortDirection.IN;
            case "out": case "output": return PortDirection.OUT;
            case "inout": case "in/out": case "in-out": return PortDirection.INOUT;
            case "none": return PortDirection.NONE;
            default: return PortDirection.UNKNOWN;
        }
    }

    // ---- Multiplicity resolution ----

    private static final class MultiplicityResult {
        final Multiplicity multiplicity;
        final String source;
        MultiplicityResult(Multiplicity m, String s) { this.multiplicity = m; this.source = s; }
    }

    private MultiplicityResult resolveMultiplicity(ElementRecord element, IRPModelElement handle) {
        Optional<String> multStr = tryCallString(handle, "getMultiplicity");
        if (!multStr.isPresent()) multStr = tryCallString(handle, "getCardinality");
        if (multStr.isPresent()) {
            Multiplicity m = parseRange(multStr.get());
            return new MultiplicityResult(m, "method:getMultiplicity");
        }

        Optional<Integer> lower = tryCallInt(handle, "getMultiplicityLower");
        Optional<Integer> upper = tryCallInt(handle, "getMultiplicityUpper");
        if (!lower.isPresent()) lower = tryCallInt(handle, "getLowerMultiplicity");
        if (!upper.isPresent()) upper = tryCallInt(handle, "getUpperMultiplicity");
        if (lower.isPresent() || upper.isPresent()) {
            Multiplicity m = Multiplicity.of(lower.orElse(null), upper.orElse(null));
            return new MultiplicityResult(m, "method:getMultiplicityLower/Upper");
        }

        Optional<String> tMult = tryReadTagValue(handle, "multiplicity");
        if (tMult.isPresent()) {
            Multiplicity m = parseRange(tMult.get());
            return new MultiplicityResult(m, "tag:multiplicity");
        }

        return new MultiplicityResult(Multiplicity.unknown(), "unknown");
    }

    private static Multiplicity parseRange(String s) {
        if (s == null || s.trim().isEmpty()) return Multiplicity.unknown();
        String v = s.trim();
        if (v.contains("..")) {
            String[] parts = v.split("\\.\\.");
            Integer lo = parseInt(parts.length > 0 ? parts[0] : null).orElse(null);
            Integer up = parseUpper(parts.length > 1 ? parts[1] : null);
            return Multiplicity.of(lo, up);
        }
        Integer lo = parseInt(v).orElse(null);
        Integer up = "*".equals(v) ? null : lo;
        return Multiplicity.of(lo, up);
    }

    private static Optional<Integer> parseInt(String s) {
        if (s == null) return Optional.empty();
        String t = s.trim();
        if (t.isEmpty() || "*".equals(t)) return Optional.empty();
        try { return Optional.of(Integer.parseInt(t)); } catch (NumberFormatException e) { return Optional.empty(); }
    }

    private static Integer parseUpper(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || "*".equals(t)) return null;
        try { return Integer.parseInt(t); } catch (NumberFormatException e) { return null; }
    }

    // ---- Low-level helpers ----

    private static Optional<String> tryCallString(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object val = m.invoke(target);
            if (val instanceof String) {
                String s = ((String) val).trim();
                return s.isEmpty() ? Optional.empty() : Optional.of(s);
            }
            if (val != null) {
                String s = val.toString().trim();
                return s.isEmpty() ? Optional.empty() : Optional.of(s);
            }
        } catch (Throwable ignore) {}
        return Optional.empty();
    }

    private static Optional<Integer> tryCallInt(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object val = m.invoke(target);
            if (val instanceof Integer) return Optional.of((Integer) val);
            if (val instanceof String) return parseInt((String) val);
        } catch (Throwable ignore) {}
        return Optional.empty();
    }

    private static Optional<String> tryReadTagValue(IRPModelElement elt, String tagName) {
        try {
            IRPCollection tags = (IRPCollection) elt.getClass().getMethod("getTags").invoke(elt);
            if (tags == null) return Optional.empty();
            int count = tags.getCount();
            for (int i = 1; i <= count; i++) {
                Object tag = tags.getItem(i);
                if (tag == null) continue;
                String name = tryGetName(tag);
                if (name != null && name.equalsIgnoreCase(tagName)) {
                    String value = tryGetValue(tag);
                    if (value != null && !value.trim().isEmpty()) {
                        return Optional.of(value.trim());
                    }
                }
            }
        } catch (Throwable ignore) {}
        return Optional.empty();
    }

    private static String tryGetName(Object tagObj) {
        try {
            Method m = tagObj.getClass().getMethod("getName");
            Object v = m.invoke(tagObj);
            return (v instanceof String) ? ((String) v).trim() : null;
        } catch (Throwable t) { return null; }
    }

    private static String tryGetValue(Object tagObj) {
        try {
            Method m = tagObj.getClass().getMethod("getValue");
            Object v = m.invoke(tagObj);
            return (v instanceof String) ? (String) v : (v != null ? v.toString() : null);
        } catch (Throwable t) { return null; }
    }

    private static IRPCollection safeInvokeCollection(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object v = m.invoke(target);
            return (v instanceof IRPCollection) ? (IRPCollection) v : null;
        } catch (Throwable t) { return null; }
    }

    private static int countCollection(IRPCollection c) {
        try { return c == null ? 0 : c.getCount(); } catch (Throwable t) { return 0; }
    }
}
