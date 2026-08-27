package org.rhapsodychecker.rhapsodyruleverifier.fix;

/**
 * Lifecycle status of a single fix entry within a fix plan.
 */
public enum FixStatus {
    /** Not yet processed. */
    PENDING,
    /** Dry-run validated — action is safe to apply. */
    SIMULATED,
    /** Successfully applied to the Rhapsody model. */
    APPLIED,
    /** Application failed (error recorded in FixEntry). */
    FAILED,
    /** Successfully rolled back to original value. */
    ROLLED_BACK,
    /** Simulation found a conflict (element missing, value changed, etc.). */
    CONFLICT,
    /** Skipped by the user (unchecked or no value provided). */
    SKIPPED
}