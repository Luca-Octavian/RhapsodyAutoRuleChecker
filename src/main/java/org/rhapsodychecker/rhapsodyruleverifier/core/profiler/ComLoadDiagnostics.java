package org.rhapsodychecker.rhapsodyruleverifier.core.profiler;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Optional timing/counter accumulator for Rhapsody COM/API call families.
 * Disabled diagnostics have near-zero behavior impact beyond a boolean check.
 */
public final class ComLoadDiagnostics {

    private final boolean enabled;
    private final Map<String, Metric> metrics = new LinkedHashMap<String, Metric>();

    public ComLoadDiagnostics(boolean enabled) {
        this.enabled = enabled;
    }

    public static ComLoadDiagnostics disabled() {
        return new ComLoadDiagnostics(false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    public void success(String family, long startedNanos) {
        record(family, startedNanos, false);
    }

    public void failure(String family, long startedNanos) {
        record(family, startedNanos, true);
    }

    public void item(String family, long durationNanos) {
        if (!enabled) return;
        metric(family).add(durationNanos, false);
    }

    private void record(String family, long startedNanos, boolean failure) {
        if (!enabled) return;
        metric(family).add(System.nanoTime() - startedNanos, failure);
    }

    private Metric metric(String family) {
        Metric value = metrics.get(family);
        if (value == null) {
            value = new Metric();
            metrics.put(family, value);
        }
        return value;
    }

    public String summary() {
        if (!enabled) return "COM diagnostics disabled";
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== COM/API Load Diagnostics ===\n");
        sb.append(String.format(Locale.ROOT,
                "%-28s %10s %12s %12s %12s %10s%n",
                "Family", "Calls", "Total ms", "Avg ms", "Max ms", "Failures"));
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            Metric m = entry.getValue();
            double totalMs = m.totalNanos / 1_000_000.0;
            double averageMs = m.calls == 0 ? 0.0 : totalMs / m.calls;
            sb.append(String.format(Locale.ROOT,
                    "%-28s %,10d %,12.1f %,12.3f %,12.3f %,10d%n",
                    entry.getKey(), m.calls, totalMs, averageMs,
                    m.maxNanos / 1_000_000.0, m.failures));
        }
        return sb.toString();
    }

    private static final class Metric {
        private long calls;
        private long totalNanos;
        private long maxNanos;
        private long failures;

        private void add(long durationNanos, boolean failure) {
            calls++;
            totalNanos += Math.max(0L, durationNanos);
            maxNanos = Math.max(maxNanos, durationNanos);
            if (failure) failures++;
        }
    }
}