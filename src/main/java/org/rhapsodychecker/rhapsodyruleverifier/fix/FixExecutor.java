package org.rhapsodychecker.rhapsodyruleverifier.fix;

/**
 * Abstraction for applying and rolling back fix actions on a model.
 * The Rhapsody implementation uses COM; tests can use a no-op or recording stub.
 */
public interface FixExecutor {

    /**
     * Apply a single fix action to the live model.
     * Implementations must:
     * 1. Look up the element by GUID
     * 2. Read the current (live) value of the field being changed
     * 3. Apply the change
     * 4. Call entry.markApplied(liveOldValue) on success
     * 5. Call entry.markFailed(message) on failure
     */
    void apply(FixEntry entry);

    /**
     * Roll back a previously applied fix entry.
     * Uses the liveOldValue recorded during apply() to restore the original state.
     * Calls entry.markRolledBack() on success, entry.markFailed() on failure.
     */
    void rollback(FixEntry entry);
}