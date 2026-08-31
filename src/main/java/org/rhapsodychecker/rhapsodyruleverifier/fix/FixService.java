package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrates the fix lifecycle: simulate → apply → journal → rollback.
 * This is the main entry point for the UI layer.
 */
public final class FixService {

    private static final Logger LOG = Logger.getLogger(FixService.class.getName());

    private final FixExecutor executor;
    private final FixPlanJournal journal;
    private final ElementIndex index;

    /** The most recently applied plan, for rollback. */
    private FixPlan lastAppliedPlan;

    public FixService(FixExecutor executor, FixPlanJournal journal, ElementIndex index) {
        this.executor = executor;
        this.journal = journal;
        this.index = index;
    }

    /**
     * Simulate the plan against the current index.
     * @return number of conflicts found
     */
    public int simulate(FixPlan plan) {
        return new FixSimulator(index).simulate(plan);
    }

    /**
     * Apply all SIMULATED (or PENDING) entries in the plan.
     * Stops on first failure if stopOnFailure is true.
     * Writes the journal after all entries are processed.
     *
     * @return number of successfully applied entries
     */
    public int apply(FixPlan plan, boolean stopOnFailure) {
        return apply(plan, stopOnFailure, null);
    }

    /**
     * Apply all SIMULATED (or PENDING) entries in the plan with progress reporting.
     * Stops on first failure if stopOnFailure is true.
     * The listener can return {@code false} to cancel remaining entries.
     * Writes the journal after all entries are processed.
     *
     * @return number of successfully applied entries
     */
    public int apply(FixPlan plan, boolean stopOnFailure, FixProgressListener listener) {
        List<FixEntry> entries = plan.entries();

        // Count only actionable entries so the progress bar excludes skipped ones
        int actionableTotal = 0;
        for (FixEntry e : entries) {
            FixStatus s = e.status();
            if (s == FixStatus.SIMULATED || s == FixStatus.PENDING) {
                actionableTotal++;
            }
        }

        int applied = 0;
        int processed = 0;
        for (int i = 0; i < entries.size(); i++) {
            FixEntry entry = entries.get(i);
            FixStatus s = entry.status();
            if (s != FixStatus.SIMULATED && s != FixStatus.PENDING) {
                continue;
            }

            executor.apply(entry);
            processed++;

            if (entry.status() == FixStatus.APPLIED) {
                applied++;
            } else if (entry.status() == FixStatus.FAILED && stopOnFailure) {
                LOG.warning("Stopping on first failure: " + entry.errorMessage());
                if (listener != null) listener.onProgress(processed, actionableTotal, entry.action().elementName());
                break;
            }

            if (listener != null && !listener.onProgress(processed, actionableTotal, entry.action().elementName())) {
                LOG.info("Fix application cancelled by user at entry " + (i + 1));
                break;
            }
        }

        this.lastAppliedPlan = plan;

        // Write journal
        try {
            File journalFile = journal.write(plan);
            LOG.info("Fix journal written: " + journalFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.warning("Failed to write fix journal: " + e.getMessage());
        }

        return applied;
    }

    /**
     * Rollback all APPLIED entries in the plan, in reverse order.
     * @return number of successfully rolled back entries
     */
    public int rollback(FixPlan plan) {
        // appliedEntries() returns an unmodifiable list, so copy it for reversal
        List<FixEntry> applied = new ArrayList<FixEntry>(plan.appliedEntries());
        Collections.reverse(applied);

        int rolledBack = 0;
        for (FixEntry entry : applied) {
            executor.rollback(entry);
            if (entry.status() == FixStatus.ROLLED_BACK) {
                rolledBack++;
            }
        }

        // Update journal
        try {
            journal.write(plan);
        } catch (IOException e) {
            LOG.warning("Failed to update journal after rollback: " + e.getMessage());
        }

        return rolledBack;
    }

    /**
     * Rollback the last applied plan.
     * @return number of entries rolled back, or -1 if no plan to rollback
     */
    public int rollbackLast() {
        if (lastAppliedPlan == null || !lastAppliedPlan.hasAppliedEntries()) {
            return -1;
        }
        return rollback(lastAppliedPlan);
    }

    /**
     * Load a plan from a journal file for inspection or rollback.
     */
    public FixPlan loadJournal(File file) throws IOException {
        return journal.read(file);
    }

    /**
     * List available journal files.
     */
    public List<File> listJournals() {
        return journal.listJournals();
    }

    public FixPlan lastAppliedPlan() {
        return lastAppliedPlan;
    }
}