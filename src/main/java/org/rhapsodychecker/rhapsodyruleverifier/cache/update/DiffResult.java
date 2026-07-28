// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/update/DiffResult.java
package org.rhapsodychecker.rhapsodyruleverifier.cache.update;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of Phase 2 diffing: which elements are new, removed, changed, or unchanged.
 */
public final class DiffResult {

    private final List<String> newGuids;
    private final List<String> removedGuids;
    private final List<String> changedGuids;
    private final int unchangedCount;
    private final int totalScanned;
    private final int deferredRemovalCount;

    public DiffResult(List<String> newGuids, List<String> removedGuids,
                      List<String> changedGuids, int unchangedCount, int totalScanned) {
        this(newGuids, removedGuids, changedGuids, unchangedCount, totalScanned, 0);
    }

    public DiffResult(List<String> newGuids, List<String> removedGuids,
                      List<String> changedGuids, int unchangedCount, int totalScanned,
                      int deferredRemovalCount) {
        this.newGuids = Collections.unmodifiableList(newGuids);
        this.removedGuids = Collections.unmodifiableList(removedGuids);
        this.changedGuids = Collections.unmodifiableList(changedGuids);
        this.unchangedCount = unchangedCount;
        this.totalScanned = totalScanned;
        this.deferredRemovalCount = deferredRemovalCount;
    }

    public List<String> newGuids() { return newGuids; }
    public List<String> removedGuids() { return removedGuids; }
    public List<String> changedGuids() { return changedGuids; }
    public int unchangedCount() { return unchangedCount; }
    public int totalScanned() { return totalScanned; }
    public int deferredRemovalCount() { return deferredRemovalCount; }

    /** True if the scan was detected as incomplete (OSLC proxies missing). */
    public boolean wasIncomplete() { return deferredRemovalCount > 0; }

    /** Number of elements that need a full re-read (new + changed). */
    public int needsFullReadCount() {
        return newGuids.size() + changedGuids.size();
    }

    /** Percentage of cached elements that changed or were removed (for threshold check). */
    public double changedPercent() {
        int cachedTotal = changedGuids.size() + removedGuids.size() + unchangedCount;
        if (cachedTotal == 0) return 0.0;
        return (double) (changedGuids.size() + removedGuids.size()) / cachedTotal * 100.0;
    }

    /** True if no changes detected at all. */
    public boolean isClean() {
        return newGuids.isEmpty() && removedGuids.isEmpty() && changedGuids.isEmpty();
    }

    /** Human-readable summary for status bar and logging. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(changedGuids.size()).append(" changed, ");
        sb.append(newGuids.size()).append(" new, ");
        sb.append(removedGuids.size()).append(" removed, ");
        sb.append(unchangedCount).append(" unchanged");
        sb.append(" (scanned ").append(totalScanned).append(" total)");
        if (deferredRemovalCount > 0) {
            sb.append(" [").append(deferredRemovalCount)
              .append(" removals deferred \u2014 incomplete scan]");
        }
        return sb.toString();
    }
}