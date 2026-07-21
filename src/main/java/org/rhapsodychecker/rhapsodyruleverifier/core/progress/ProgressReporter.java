// core/progress/ProgressReporter.java
package org.rhapsodychecker.rhapsodyruleverifier.core.progress;

/**
 * Callback interface for reporting progress from long-running operations
 * (model loading, rule evaluation) back to the UI layer.
 *
 * Implementations must be thread-safe — callbacks are fired from
 * background threads (SwingWorker.doInBackground).
 */
public interface ProgressReporter {

    /**
     * A named phase has started (indeterminate progress).
     * @param step  Human-readable step name, e.g. "Connecting to Rhapsody"
     */
    void onStepStarted(String step);

    /**
     * Progress within the current step.
     * @param current  Elements processed so far
     * @param total    Total elements to process (> 0)
     */
    void onProgress(int current, int total);

    /**
     * The current step completed successfully.
     * @param step  Same label passed to onStepStarted
     */
    void onStepCompleted(String step);

    /**
     * All work is done.
     */
    void onDone();

    /**
     * A no-op reporter — use when no progress reporting is needed
     * (e.g. tests, CLI). Avoids null checks everywhere.
     */
    ProgressReporter NOOP = new ProgressReporter() {
        @Override public void onStepStarted(String step)          {}
        @Override public void onProgress(int current, int total)  {}
        @Override public void onStepCompleted(String step)        {}
        @Override public void onDone()                            {}
    };
}
