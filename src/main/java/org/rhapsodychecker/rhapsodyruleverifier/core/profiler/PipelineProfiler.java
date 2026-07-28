// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/profiler/PipelineProfiler.java
package org.rhapsodychecker.rhapsodyruleverifier.core.profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects {@link PhaseTimer} results across a pipeline run and
 * produces a formatted summary table.
 *
 * Thread-safety: not thread-safe — designed for single-threaded pipeline use.
 */
public final class PipelineProfiler {

    private final String pipelineName;
    private final List<PhaseTimer> phases = new ArrayList<PhaseTimer>();
    private long wallStartNanos;
    private long wallDurationNanos;

    public PipelineProfiler(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    /** Call once before the first phase. */
    public void startPipeline() {
        wallStartNanos = System.nanoTime();
    }

    /** Call once after the last phase. */
    public void stopPipeline() {
        wallDurationNanos = System.nanoTime() - wallStartNanos;
    }

    /** Record a completed phase timer. */
    public void record(PhaseTimer timer) {
        phases.add(timer);
    }

    /**
     * Convenience: create, start, and return a new PhaseTimer.
     * Caller must call {@code stop()} then {@code record(timer)}.
     */
    public PhaseTimer startPhase(String name) {
        return new PhaseTimer(name).start();
    }

    public List<PhaseTimer> phases() {
        return Collections.unmodifiableList(phases);
    }

    public double wallTimeMs() {
        return wallDurationNanos / 1_000_000.0;
    }

    /**
     * Returns a formatted summary table string.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  Pipeline: %-64s ║%n", pipelineName));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  %-35s %8s  %6s  %-22s║%n",
                "Phase", "Time", "  %  ", "Throughput"));
        sb.append("╟──────────────────────────────────────────────────────────────────────────────╢\n");

        double totalMs = wallTimeMs();
        double accountedMs = 0;

        for (PhaseTimer p : phases) {
            double ms = p.durationMs();
            double pct = totalMs > 0 ? (ms / totalMs) * 100.0 : 0;
            accountedMs += ms;

            String throughput = "";
            if (p.itemCount() > 0) {
                throughput = String.format("%,d items @ %,.0f/s", p.itemCount(), p.throughput());
            }

            sb.append(String.format("║  %-35s %7.1f ms %5.1f%%  %-22s║%n",
                    p.name(), ms, pct, throughput));
        }

        sb.append("╟──────────────────────────────────────────────────────────────────────────────╢\n");

        double overhead = totalMs - accountedMs;
        sb.append(String.format("║  %-35s %7.1f ms %5.1f%%  %-22s║%n",
                "Overhead / unaccounted", overhead, totalMs > 0 ? (overhead / totalMs) * 100 : 0, ""));
        sb.append(String.format("║  %-35s %7.1f ms %5.1f%%  %-22s║%n",
                "TOTAL (wall clock)", totalMs, 100.0, ""));

        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return summary();
    }
}