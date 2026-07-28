// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/profiler/PhaseTimer.java
package org.rhapsodychecker.rhapsodyruleverifier.core.profiler;

/**
 * Lightweight stopwatch for a single pipeline phase.
 * Captures name, duration in nanos, and optional item count for throughput stats.
 */
public final class PhaseTimer {

    private final String name;
    private long startNanos;
    private long durationNanos;
    private int itemCount;

    public PhaseTimer(String name) {
        this.name = name;
    }

    public PhaseTimer start() {
        this.startNanos = System.nanoTime();
        return this;
    }

    public PhaseTimer stop() {
        this.durationNanos = System.nanoTime() - startNanos;
        return this;
    }

    public PhaseTimer items(int count) {
        this.itemCount = count;
        return this;
    }

    public String name() { return name; }
    public long durationNanos() { return durationNanos; }
    public double durationMs() { return durationNanos / 1_000_000.0; }
    public int itemCount() { return itemCount; }

    /**
     * Items per second throughput, or 0 if no items recorded.
     */
    public double throughput() {
        if (itemCount <= 0 || durationNanos <= 0) return 0;
        return itemCount / (durationNanos / 1_000_000_000.0);
    }

    @Override
    public String toString() {
        String base = String.format("%-35s %8.1f ms", name, durationMs());
        if (itemCount > 0) {
            base += String.format("  (%,d items, %,.0f items/s)", itemCount, throughput());
        }
        return base;
    }
}