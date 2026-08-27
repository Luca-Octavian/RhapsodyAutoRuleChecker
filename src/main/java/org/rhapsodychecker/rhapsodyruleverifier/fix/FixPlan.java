package org.rhapsodychecker.rhapsodyruleverifier.fix;

import java.util.*;

/**
 * A plan containing all proposed fix actions for a single evaluation run.
 * Acts as the internal journal for simulation, application, and rollback.
 * Serialized to JSON by {@link FixPlanJournal}.
 */
public final class FixPlan {

    private final String modelGuid;
    private final String configPath;
    private final long timestamp;
    private final List<FixEntry> entries;

    public FixPlan(String modelGuid, String configPath) {
        this.modelGuid = Objects.requireNonNull(modelGuid, "modelGuid");
        this.configPath = configPath;
        this.timestamp = System.currentTimeMillis();
        this.entries = new ArrayList<FixEntry>();
    }

    /** Deserialization constructor. */
    public FixPlan(String modelGuid, String configPath, long timestamp, List<FixEntry> entries) {
        this.modelGuid = Objects.requireNonNull(modelGuid, "modelGuid");
        this.configPath = configPath;
        this.timestamp = timestamp;
        this.entries = entries != null ? new ArrayList<FixEntry>(entries) : new ArrayList<FixEntry>();
    }

    public String modelGuid() { return modelGuid; }
    public String configPath() { return configPath; }
    public long timestamp() { return timestamp; }

    /** Returns a live mutable list of entries. */
    public List<FixEntry> entries() { return entries; }

    public void addEntry(FixEntry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
    }

    public void addAction(FixAction action) {
        entries.add(new FixEntry(action));
    }

    /** Count entries matching the given status. */
    public int countByStatus(FixStatus status) {
        int count = 0;
        for (FixEntry e : entries) {
            if (e.status() == status) count++;
        }
        return count;
    }

    /** Returns true if any entry has been applied. */
    public boolean hasAppliedEntries() {
        return countByStatus(FixStatus.APPLIED) > 0;
    }

    /** Returns true if all entries are either SIMULATED or PENDING (safe to apply). */
    public boolean isReadyToApply() {
        for (FixEntry e : entries) {
            FixStatus s = e.status();
            if (s != FixStatus.PENDING && s != FixStatus.SIMULATED) return false;
        }
        return !entries.isEmpty();
    }

    /** Returns unmodifiable view of entries that were applied (for rollback). */
    public List<FixEntry> appliedEntries() {
        List<FixEntry> result = new ArrayList<FixEntry>();
        for (FixEntry e : entries) {
            if (e.status() == FixStatus.APPLIED) result.add(e);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String toString() {
        return "FixPlan{model=" + modelGuid
                + ", entries=" + entries.size()
                + ", applied=" + countByStatus(FixStatus.APPLIED)
                + ", failed=" + countByStatus(FixStatus.FAILED)
                + "}";
    }
}