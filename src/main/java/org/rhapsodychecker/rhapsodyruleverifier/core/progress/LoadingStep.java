// core/progress/LoadingStep.java
package org.rhapsodychecker.rhapsodyruleverifier.core.progress;

/**
 * Well-known step name constants shared between backend and UI.
 * Avoids magic strings scattered across classes.
 */
public final class LoadingStep {

    private LoadingStep() {}

    // ── Model loading steps ───────────────────────────────────────────────────
    public static final String CONNECTING        = "Connecting to Rhapsody";
    public static final String SCANNING_PACKAGES = "Scanning packages";
    public static final String LOADING_ELEMENTS  = "Loading elements";
    public static final String BUILDING_INDEX    = "Building index";
    public static final String FAST_DETECTION    = "Detecting stereotypes & types";

    // ── Rule evaluation steps ─────────────────────────────────────────────────
    public static final String LOADING_CONFIG    = "Loading config";
    public static final String SELECTING_ELEMENTS = "Selecting elements";
    public static final String EVALUATING_RULES  = "Evaluating rules";
}
