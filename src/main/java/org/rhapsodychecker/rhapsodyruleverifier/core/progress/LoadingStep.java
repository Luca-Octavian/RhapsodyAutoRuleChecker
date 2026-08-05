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
    public static final String BUILDING_PACKAGE_TREE  = "Building package tree";
    public static final String BUILDING_ELEMENT_INDEX = "Indexing elements";
    public static final String WRITING_CACHE          = "Saving cache";
    public static final String READING_REFERENCES     = "Reading references";

    // ── Incremental update steps ──────────────────────────────────────────────
    public static final String INCREMENTAL_SCANNING = "Scanning for changes";
    public static final String INCREMENTAL_DIFFING  = "Comparing with cache";
    public static final String INCREMENTAL_UPDATING = "Updating changed elements";

    // ── Rule evaluation steps ─────────────────────────────────────────────────
    public static final String LOADING_CONFIG    = "Loading config";
    public static final String SELECTING_ELEMENTS = "Selecting elements";
    public static final String EVALUATING_RULES  = "Evaluating rules";
}
