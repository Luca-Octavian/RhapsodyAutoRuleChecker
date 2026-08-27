package org.rhapsodychecker.rhapsodyruleverifier.fix;

import java.util.Objects;

/**
 * A {@link FixAction} paired with its execution lifecycle {@link FixStatus}.
 * Mutable status — transitions from PENDING → SIMULATED → APPLIED (or FAILED/CONFLICT).
 * The action itself is immutable.
 */
public final class FixEntry {

    private final FixAction action;
    private FixStatus status;
    private String errorMessage; // non-null only when status == FAILED or CONFLICT
    private String liveOldValue; // actual value read from Rhapsody at apply-time (for rollback)

    public FixEntry(FixAction action) {
        this.action = Objects.requireNonNull(action, "action");
        this.status = FixStatus.PENDING;
    }

    public FixEntry(FixAction action, FixStatus status) {
        this.action = Objects.requireNonNull(action, "action");
        this.status = Objects.requireNonNull(status, "status");
    }

    public FixAction action() { return action; }
    public FixStatus status() { return status; }
    public String errorMessage() { return errorMessage; }
    public String liveOldValue() { return liveOldValue; }

    public void markSimulated() {
        this.status = FixStatus.SIMULATED;
        this.errorMessage = null;
    }

    public void markApplied(String liveOldValue) {
        this.status = FixStatus.APPLIED;
        this.liveOldValue = liveOldValue;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = FixStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markConflict(String reason) {
        this.status = FixStatus.CONFLICT;
        this.errorMessage = reason;
    }

    public void markRolledBack() {
        this.status = FixStatus.ROLLED_BACK;
        this.errorMessage = null;
    }

    public void markSkipped(String reason) {
        this.status = FixStatus.SKIPPED;
        this.errorMessage = reason;
    }

    /** Reset to PENDING so the entry can be re-simulated after user edits. */
    public void resetToPending() {
        this.status = FixStatus.PENDING;
        this.errorMessage = null;
    }

    @Override
    public String toString() {
        return status + " | " + action.description()
                + (errorMessage != null ? " | error: " + errorMessage : "");
    }
}